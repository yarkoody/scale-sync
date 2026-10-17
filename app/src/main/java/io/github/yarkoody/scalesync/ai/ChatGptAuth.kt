package io.github.yarkoody.scalesync.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.github.yarkoody.scalesync.medical.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.UUID

/**
 * "Sign in with ChatGPT" with plan usage: lets the app send requests that count against the
 * user's own ChatGPT Plus/Pro plan, with no API key. Open-source apps may use it directly
 * (developers.openai.com/siwc). It's a browser sign-in with PKCE whose redirect goes to a
 * one-shot HTTP listener on 127.0.0.1 inside the app.
 */
object ChatGptAuth {
    private const val TAG = "ChatGptAuth"
    private const val ISSUER = "https://auth.openai.com"
    private const val AUTHORIZE = "$ISSUER/api/accounts/authorize"
    private const val TOKEN = "$ISSUER/api/accounts/oauth/token"
    private const val JWKS = "$ISSUER/.well-known/jwks.json"
    const val RESOURCE = "https://api.openai.com/v1"
    private const val SCOPE = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
    const val PLAN_SCOPE = "chatgpt.tokens.use.direct"
    private const val PORTS_FROM = 1455
    private const val APP_NAME = "My Health (open source)"

    sealed interface State {
        data object SignedOut : State
        data object Waiting : State
        data class SignedIn(val email: String, val planAllowed: Boolean) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshLock = Mutex()
    private val _state = MutableStateFlow<State>(State.SignedOut)
    val state: StateFlow<State> = _state

    private data class Creds(
        val hostId: String,
        val clientId: String?,
        val accessToken: String?,
        val refreshToken: String?,
        val expiresAtMs: Long,
        val scope: String,
        val idToken: String?,
        val email: String?,
    ) {
        val signedIn get() = refreshToken != null && clientId != null
    }

    private fun file(context: Context) = File(context.noBackupFilesDir, "chatgpt.json")

    private fun load(context: Context): Creds {
        val m = runCatching { Json.parse(file(context).readText()) as Map<*, *> }.getOrNull()
        return Creds(
            hostId = m?.get("host_id") as? String ?: UUID.randomUUID().toString(),
            clientId = m?.get("client_id") as? String,
            accessToken = m?.get("access_token") as? String,
            refreshToken = m?.get("refresh_token") as? String,
            expiresAtMs = (m?.get("expires_at") as? Double)?.toLong() ?: 0L,
            scope = m?.get("scope") as? String ?: "",
            idToken = m?.get("id_token") as? String,
            email = m?.get("email") as? String,
        )
    }

    private fun save(context: Context, c: Creds) {
        val f = file(context)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(Json.write(linkedMapOf(
            "host_id" to c.hostId, "client_id" to c.clientId, "access_token" to c.accessToken,
            "refresh_token" to c.refreshToken, "expires_at" to c.expiresAtMs, "scope" to c.scope,
            "id_token" to c.idToken, "email" to c.email,
        )))
        tmp.setReadable(false, false); tmp.setReadable(true, true)
        tmp.renameTo(f)
    }

    /** Call once at start to reflect what's saved. */
    fun init(context: Context) {
        val c = load(context)
        if (_state.value !is State.Waiting) {
            _state.value = if (c.signedIn) State.SignedIn(c.email.orEmpty(), PLAN_SCOPE in c.scope.split(' ')) else State.SignedOut
        }
    }

    fun signOut(context: Context) {
        val c = load(context)
        // Keep the host and client ids: they identify this install for the next sign-in.
        save(context, c.copy(accessToken = null, refreshToken = null, expiresAtMs = 0, scope = "", idToken = null, email = null))
        _state.value = State.SignedOut
    }

    /** Opens the ChatGPT sign-in page and waits for the redirect back to the app. */
    fun signIn(context: Context) {
        val app = context.applicationContext
        if (_state.value is State.Waiting) return
        _state.value = State.Waiting
        scope.launch {
            try {
                val result = runSignIn(app, context)
                _state.value = result
            } catch (e: Exception) {
                Log.w(TAG, "sign-in failed", e)
                _state.value = State.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun runSignIn(app: Context, uiContext: Context): State {
        var creds = load(app)
        save(app, creds) // persist the host id before the first sign-in
        val verifier = b64(randomBytes(48))
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        val state = b64(randomBytes(24))
        val nonce = b64(randomBytes(24))

        val server = (PORTS_FROM until PORTS_FROM + 10).firstNotNullOfOrNull { port ->
            runCatching { ServerSocket(port, 4, InetAddress.getByName("127.0.0.1")) }.getOrNull()
        } ?: return State.Failed("Couldn't open the sign-in callback on this phone")
        val redirect = "http://127.0.0.1:${server.localPort}/auth/callback"

        server.use { srv ->
            srv.soTimeout = 5 * 60_000
            val params = buildList {
                add("client_id" to (creds.clientId ?: "dynamic_agent_client"))
                if (creds.clientId == null) add("agent_name_hint" to APP_NAME)
                add("ext_agent_host_id" to creds.hostId)
                creds.idToken?.let { add("id_token_hint" to it) }
                creds.email?.let { add("login_hint" to it) }
                add("response_type" to "code")
                add("redirect_uri" to redirect)
                add("scope" to SCOPE)
                add("resource" to RESOURCE)
                add("state" to state)
                add("nonce" to nonce)
                add("code_challenge_method" to "S256")
                add("code_challenge" to challenge)
            }
            val url = "$AUTHORIZE?" + Http.form(*params.toTypedArray())
            withContext(Dispatchers.Main) {
                uiContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }

            while (true) {
                val socket = try { srv.accept() } catch (_: SocketTimeoutException) { return State.Failed("Sign-in timed out") }
                socket.use { s ->
                    val requestLine = s.getInputStream().bufferedReader().readLine().orEmpty()
                    val path = requestLine.split(' ').getOrNull(1).orEmpty()
                    if (!path.startsWith("/auth/callback")) {
                        respond(s, 404, "Not found")
                        return@use
                    }
                    val q = path.substringAfter('?', "").split('&').filter { it.isNotEmpty() }.associate {
                        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
                    }
                    if (q["state"] != state) {
                        respond(s, 400, page("Sign-in didn't match", "Go back to My Health and try again."))
                        return State.Failed("Sign-in state didn't match")
                    }
                    q["error"]?.let {
                        respond(s, 200, page("Not connected", "You can go back to My Health."))
                        return if (it == "access_denied") State.SignedOut else State.Failed(it)
                    }
                    val issued = q["client_id"]
                    if (creds.clientId != null && issued != null && issued != creds.clientId) {
                        respond(s, 400, page("Sign-in didn't match", "Go back to My Health and try again."))
                        return State.Failed("ChatGPT returned a different app id")
                    }
                    val clientId = creds.clientId ?: issued ?: run {
                        respond(s, 400, page("Sign-in incomplete", "Go back to My Health and try again."))
                        return State.Failed("ChatGPT didn't return an app id")
                    }
                    val code = q["code"].orEmpty()
                    val result = runCatching {
                        val tok = Json.parse(Http.postForm(TOKEN, Http.form(
                            "grant_type" to "authorization_code", "client_id" to clientId, "code" to code,
                            "code_verifier" to verifier, "redirect_uri" to redirect, "resource" to RESOURCE,
                        ))) as Map<*, *>
                        val idToken = tok["id_token"] as? String
                        val claims = idToken?.let { verifyIdToken(it, clientId, nonce) }
                        creds = creds.copy(
                            clientId = clientId,
                            accessToken = tok["access_token"] as? String,
                            refreshToken = tok["refresh_token"] as? String,
                            expiresAtMs = System.currentTimeMillis() + ((tok["expires_in"] as? Double)?.toLong() ?: 3600L) * 1000,
                            scope = tok["scope"] as? String ?: "",
                            idToken = idToken,
                            email = claims?.get("email") as? String,
                        )
                        save(app, creds)
                    }
                    val planAllowed = PLAN_SCOPE in creds.scope.split(' ')
                    return if (result.isSuccess && creds.refreshToken != null) {
                        respond(s, 200, page("Connected to ChatGPT",
                            if (planAllowed) "My Health can now use your ChatGPT plan. Go back to the app."
                            else "You signed in, but didn't allow using your plan, so the app can't ask ChatGPT yet."))
                        State.SignedIn(creds.email.orEmpty(), planAllowed)
                    } else {
                        respond(s, 500, page("Couldn't finish connecting", "Go back to My Health and try again."))
                        State.Failed(result.exceptionOrNull()?.message ?: "No refresh token returned")
                    }
                }
            }
        }
        @Suppress("UNREACHABLE_CODE")
        return State.Failed("Sign-in ended unexpectedly")
    }

    /** A valid access token, refreshed when it's about to expire. Null when signed out. */
    suspend fun accessToken(context: Context): String? = refreshLock.withLock {
        val c = load(context)
        if (!c.signedIn) return@withLock null
        if (c.accessToken != null && c.expiresAtMs - System.currentTimeMillis() > 120_000) return@withLock c.accessToken
        try {
            val tok = withContext(Dispatchers.IO) {
                Json.parse(Http.postForm(TOKEN, Http.form(
                    "grant_type" to "refresh_token", "client_id" to c.clientId!!, "refresh_token" to c.refreshToken!!,
                ))) as Map<*, *>
            }
            val next = c.copy(
                accessToken = tok["access_token"] as? String,
                refreshToken = tok["refresh_token"] as? String ?: c.refreshToken,
                expiresAtMs = System.currentTimeMillis() + ((tok["expires_in"] as? Double)?.toLong() ?: 3600L) * 1000,
                scope = tok["scope"] as? String ?: c.scope,
                idToken = tok["id_token"] as? String ?: c.idToken,
            )
            save(context, next)
            next.accessToken
        } catch (e: HttpError) {
            if (e.code == 400 || e.code == 401) {
                // The refresh token was revoked or expired (30 days unused): sign in again.
                signOut(context)
                null
            } else throw e
        }
    }

    private fun respond(s: java.net.Socket, code: Int, body: String) {
        val bytes = body.toByteArray()
        val head = "HTTP/1.1 $code OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        s.getOutputStream().apply { write(head.toByteArray()); write(bytes); flush() }
    }

    private fun page(title: String, text: String) = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>$title</title></head>
<body style="font-family:sans-serif;padding:32px;text-align:center"><h2>$title</h2><p>$text</p>
<p><a href="intent:#Intent;component=io.github.yarkoody.scalesync/.ui.MainActivity;end"
style="display:inline-block;padding:12px 20px;background:#006a60;color:#fff;border-radius:20px;text-decoration:none">Open My Health</a></p>
</body></html>"""

    /** Checks the ID token's RS256 signature against OpenAI's published keys, then its claims. */
    private fun verifyIdToken(token: String, clientId: String, nonce: String): Map<*, *> {
        val parts = token.split('.')
        require(parts.size == 3) { "Malformed ID token" }
        val header = Json.parse(String(unb64(parts[0]))) as Map<*, *>
        val claims = Json.parse(String(unb64(parts[1]))) as Map<*, *>
        require(header["alg"] == "RS256") { "Unexpected ID token algorithm ${header["alg"]}" }
        val keys = (Json.parse(Http.get(JWKS)) as Map<*, *>)["keys"] as List<*>
        val jwk = keys.map { it as Map<*, *> }.firstOrNull { it["kid"] == header["kid"] } ?: error("Unknown ID token key")
        val pub = KeyFactory.getInstance("RSA").generatePublic(
            RSAPublicKeySpec(BigInteger(1, unb64(jwk["n"] as String)), BigInteger(1, unb64(jwk["e"] as String))))
        val ok = Signature.getInstance("SHA256withRSA").run {
            initVerify(pub); update("${parts[0]}.${parts[1]}".toByteArray()); verify(unb64(parts[2]))
        }
        require(ok) { "ID token signature is invalid" }
        require(claims["iss"] == ISSUER) { "Wrong ID token issuer" }
        val aud = claims["aud"]
        require(aud == clientId || (aud is List<*> && clientId in aud)) { "ID token is for another app" }
        require(claims["nonce"] == nonce) { "ID token nonce mismatch" }
        val exp = (claims["exp"] as? Double)?.toLong() ?: 0L
        require(exp * 1000 > System.currentTimeMillis() - 300_000) { "ID token expired" }
        return claims
    }

    private fun randomBytes(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }
    private fun b64(b: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    private fun unb64(s: String) = Base64.getUrlDecoder().decode(s)
}

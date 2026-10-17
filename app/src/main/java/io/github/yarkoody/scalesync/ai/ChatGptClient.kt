package io.github.yarkoody.scalesync.ai

import android.content.Context
import io.github.yarkoody.scalesync.medical.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(val fromUser: Boolean, val text: String)
data class ChatModel(val slug: String, val name: String)

class ChatGptException(message: String) : Exception(message)

/** Responses API calls billed to the signed-in user's ChatGPT plan. */
object ChatGptClient {
    const val INSTRUCTIONS =
        "You are a careful, practical health coach inside the user's personal health app. Base everything on the " +
            "data they share; say when it's too thin to conclude anything. Be concise: short paragraphs or a few " +
            "bullets, plain language, no tables. Flag clearly when something needs a doctor. You don't diagnose."

    /** Models the user's plan offers, as listed by the catalog. */
    suspend fun models(context: Context): List<ChatModel> = withContext(Dispatchers.IO) {
        val token = ChatGptAuth.accessToken(context) ?: throw ChatGptException("Not connected to ChatGPT")
        val root = Json.parse(call { Http.get("${ChatGptAuth.RESOURCE}/models", token) }) as Map<*, *>
        val list = (root["models"] as? List<*>) ?: (root["data"] as? List<*>) ?: emptyList<Any?>()
        list.mapNotNull { it as? Map<*, *> }
            .filter { (it["visibility"] as? String)?.let { v -> v == "list" } ?: true }
            .mapNotNull { m ->
                val slug = (m["slug"] ?: m["id"]) as? String ?: return@mapNotNull null
                ChatModel(slug, m["display_name"] as? String ?: slug)
            }
    }

    /** Streams the reply text as it arrives. The conversation history is resent each time. */
    fun reply(context: Context, model: String, history: List<ChatMessage>): Flow<String> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            try {
                val token = ChatGptAuth.accessToken(context) ?: throw ChatGptException("Not connected to ChatGPT")
                val body = Json.write(linkedMapOf(
                    "model" to model,
                    "instructions" to INSTRUCTIONS,
                    "input" to history.map { m ->
                        linkedMapOf(
                            "role" to if (m.fromUser) "user" else "assistant",
                            "content" to listOf(linkedMapOf(
                                "type" to if (m.fromUser) "input_text" else "output_text",
                                "text" to m.text,
                            )),
                        )
                    },
                    "store" to false,
                    "stream" to true,
                ))
                var completed = false
                call {
                    Http.postSse("${ChatGptAuth.RESOURCE}/responses", body, token) { data ->
                        val e = runCatching { Json.parse(data) as Map<*, *> }.getOrNull() ?: return@postSse
                        when (e["type"]) {
                            "response.output_text.delta" -> (e["delta"] as? String)?.let { trySend(it) }
                            "response.completed" -> completed = true
                            "response.failed", "error", "response.incomplete" -> throw ChatGptException(errorText(e))
                        }
                    }
                }
                if (!completed) throw ChatGptException("ChatGPT stopped before finishing. Try again.")
                close()
            } catch (t: Throwable) {
                close(t)
            }
        }
        awaitClose { job.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: HttpError) {
        throw ChatGptException(when (e.code) {
            401 -> "ChatGPT sign-in expired. Connect again."
            403 -> "This ChatGPT account or plan can't be used from other apps."
            429 -> "You've reached the share of your ChatGPT plan this app may use. It resets with your plan's limits; you can raise the app's share in ChatGPT's settings."
            else -> "ChatGPT returned an error (${e.code})."
        })
    }

    private fun errorText(e: Map<*, *>): String {
        val err = (e["error"] as? Map<*, *>) ?: ((e["response"] as? Map<*, *>)?.get("error") as? Map<*, *>)
        return (err?.get("message") as? String) ?: "ChatGPT couldn't answer. Try again."
    }
}

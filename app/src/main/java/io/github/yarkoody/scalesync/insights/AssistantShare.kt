package io.github.yarkoody.scalesync.insights

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** AI apps the brief and documents can be handed to. Each runs on the subscription signed in there. */
enum class Assistant(val label: String, val pkg: String, val plan: String) {
    CLAUDE("Claude", "com.anthropic.claude", "Claude Pro or Max"),
    CHATGPT("ChatGPT", "com.openai.chatgpt", "ChatGPT Plus or Pro"),
    GEMINI("Gemini", "com.google.android.apps.bard", "Google AI Pro"),
    COPILOT("Copilot", "com.microsoft.copilot", "Copilot Pro"),
    PERPLEXITY("Perplexity", "ai.perplexity.app.android", "Perplexity Pro"),
}

/**
 * Hands text or a document to the chosen AI app, so the conversation runs on your own
 * subscription there. Most AI subscriptions can't be used from other apps (that needs a separate,
 * pay-per-use API key); ChatGPT is the exception, see [io.github.yarkoody.scalesync.ai.ChatGptAuth].
 */
object AssistantShare {
    private const val PREFS = "assistant"

    fun selected(context: Context): Assistant =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("selected", null)
            ?.let { runCatching { Assistant.valueOf(it) }.getOrNull() }
            ?: Assistant.entries.firstOrNull { isInstalled(context, it) } ?: Assistant.CLAUDE

    fun select(context: Context, a: Assistant) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("selected", a.name).apply()
    }

    fun isInstalled(context: Context, a: Assistant): Boolean =
        runCatching { context.packageManager.getPackageInfo(a.pkg, 0); true }.getOrDefault(false)

    fun sendText(context: Context, a: Assistant, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        launch(context, a, send)
    }

    /** Sends a file with a message. Used for medical documents and for the health brief as a file. */
    fun sendFile(context: Context, a: Assistant, file: File, mime: String, text: String) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        launch(context, a, send)
    }

    /** Writes the brief to a Markdown file in the app's storage, for apps that prefer an attachment. */
    fun briefFile(context: Context, text: String): File {
        val dir = File(context.filesDir, "share").also { it.mkdirs() }
        return File(dir, "my-health-data.md").also { it.writeText(text) }
    }

    fun copy(context: Context, text: String) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Health data", text))
    }

    fun clipboardText(context: Context): String =
        context.getSystemService(ClipboardManager::class.java).primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()

    private fun launch(context: Context, a: Assistant, intent: Intent) {
        if (isInstalled(context, a)) {
            try {
                context.startActivity(Intent(intent).setPackage(a.pkg))
                return
            } catch (_: ActivityNotFoundException) {
                // That app doesn't take this kind of share; fall back to the chooser.
            } catch (_: SecurityException) {
            }
        }
        context.startActivity(Intent.createChooser(intent, "Send to"))
    }
}

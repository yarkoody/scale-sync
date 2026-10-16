package io.github.yarkoody.scalesync.insights

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands text or a document to the Claude app, so the conversation runs on your own Claude
 * subscription. Claude subscriptions can't be called from other apps (that needs a separate,
 * pay-per-use API key), so this app stays offline and lets the Claude app do the talking.
 */
object ClaudeShare {
    const val PACKAGE = "com.anthropic.claude"

    fun isInstalled(context: Context): Boolean =
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

    fun sendText(context: Context, text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        launch(context, send, "Send to")
    }

    /** Sends a file with a message. Used for medical documents and for the health brief as a file. */
    fun sendFile(context: Context, file: File, mime: String, text: String) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        launch(context, send, "Send to")
    }

    /** Writes the brief to a Markdown file in the app's cache, for apps that prefer an attachment. */
    fun briefFile(context: Context, text: String): File {
        val dir = File(context.filesDir, "share").also { it.mkdirs() }
        return File(dir, "my-health-data.md").also { it.writeText(text) }
    }

    fun copy(context: Context, text: String) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Health data for Claude", text))
    }

    fun clipboardText(context: Context): String =
        context.getSystemService(ClipboardManager::class.java).primaryClip
            ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()

    private fun launch(context: Context, intent: Intent, chooserTitle: String) {
        if (isInstalled(context)) {
            try {
                context.startActivity(Intent(intent).setPackage(PACKAGE))
                return
            } catch (_: ActivityNotFoundException) {
                // The Claude app doesn't take this kind of share; fall back to the chooser.
            } catch (_: SecurityException) {
            }
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}

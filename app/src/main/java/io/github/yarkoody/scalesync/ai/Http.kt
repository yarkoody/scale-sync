package io.github.yarkoody.scalesync.ai

import java.io.BufferedReader
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal class HttpError(val code: Int, val body: String) : IOException("HTTP $code: ${body.take(300)}")

/** Tiny HTTP helpers on HttpURLConnection, so the app needs no networking library. */
internal object Http {
    fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }

    fun get(url: String, bearer: String? = null): String = request("GET", url, null, null, bearer)

    fun postForm(url: String, body: String): String =
        request("POST", url, body, "application/x-www-form-urlencoded", null)

    private fun request(method: String, url: String, body: String?, type: String?, bearer: String?): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.setRequestProperty("Accept", "application/json")
            bearer?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", type)
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (code !in 200..299) throw HttpError(code, text)
            return text
        } finally {
            c.disconnect()
        }
    }

    /** POSTs JSON and hands each server-sent event's data line to [onEvent]. */
    fun postSse(url: String, json: String, bearer: String, onEvent: (String) -> Unit) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 120_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "text/event-stream")
            c.setRequestProperty("Authorization", "Bearer $bearer")
            c.outputStream.use { it.write(json.toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) {
                throw HttpError(code, c.errorStream?.bufferedReader()?.use(BufferedReader::readText).orEmpty())
            }
            c.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) if (line.startsWith("data:")) {
                    val data = line.removePrefix("data:").trim()
                    if (data.isNotEmpty() && data != "[DONE]") onEvent(data)
                }
            }
        } finally {
            c.disconnect()
        }
    }
}

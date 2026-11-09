package io.github.yarkoody.scalesync.data

import android.content.Context
import io.github.yarkoody.scalesync.core.Advertisement
import io.github.yarkoody.scalesync.core.Capture
import io.github.yarkoody.scalesync.core.toHex
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A rolling log of raw advertisements, so someone with an unsupported or Experimental scale can
 * share exactly what it sends. Stays on the phone until the user shares it; the app has no
 * network access.
 */
object Diagnostics {
    private const val FILE = "diagnostic-capture.txt"
    private const val MAX_BYTES = 256 * 1024L
    private val TIME: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /** Last payload per address, so a scale repeating the same frame doesn't flood the file. */
    private val last = HashMap<String, String>()

    fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    @Synchronized
    fun record(context: Context, ad: Advertisement) {
        val key = (ad.raw?.toHex() ?: "") + ad.serviceData.values.joinToString { it.toHex() } +
            ad.manufacturerData.values.joinToString { it.toHex() }
        if (key.isEmpty() || last[ad.address] == key) return
        last[ad.address] = key
        val f = file(context)
        runCatching {
            if (f.length() > MAX_BYTES) {
                val keep = f.readLines().takeLast(500)
                f.writeText(keep.joinToString("\n", postfix = "\n"))
            }
            f.appendText(Capture.line(LocalDateTime.now().withNano(0).format(TIME), ad) + "\n")
        }
    }

    @Synchronized
    fun clear(context: Context) {
        last.clear()
        file(context).delete()
    }
}

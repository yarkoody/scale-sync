package io.github.yarkoody.scalesync.core

import java.util.Locale

/**
 * One line of a diagnostic capture: the raw advertisement, plus what Scale Sync made of it.
 * The hex fields can be pasted straight into a driver unit test.
 */
object Capture {
    fun line(atIso: String, ad: Advertisement): String = buildString {
        append(atIso).append(' ').append(ad.address)
        ad.rssi?.let { append(" rssi=").append(it) }
        ad.name?.let { append(" name=\"").append(it.replace("\"", "'")).append('"') }
        ad.serviceData.forEach { (uuid, data) ->
            val key = Advertisement.short(uuid)?.let { String.format(Locale.ROOT, "%04x", it) } ?: uuid.toString()
            append(" svc[").append(key).append("]=").append(data.toHex())
        }
        ad.manufacturerData.forEach { (company, data) ->
            append(" mfr[").append(String.format(Locale.ROOT, "%04x", company)).append("]=").append(data.toHex())
        }
        ad.raw?.let { append(" raw=").append(it.trimTrailingZeros().toHex()) }
        val matched = Drivers.matching(ad)
        if (matched.isEmpty()) append(" driver=none")
        matched.forEach { d ->
            append(" driver=").append(d.id)
            val f = Drivers.parse(d, ad)
            if (f == null) append(":no-reading") else {
                append(String.format(Locale.ROOT, ":%.2fkg", f.weightKg))
                f.impedance?.let { append(":").append(it).append("ohm") }
                when (f.stable) {
                    true -> append(":stable")
                    false -> append(":unstable")
                    null -> Unit
                }
                if (f.removed) append(":removed")
            }
        }
    }

    /** Android pads the advertisement buffer to 62 bytes with zeros. */
    private fun ByteArray.trimTrailingZeros(): ByteArray {
        var end = size
        while (end > 0 && this[end - 1] == 0.toByte()) end--
        return copyOf(end)
    }
}

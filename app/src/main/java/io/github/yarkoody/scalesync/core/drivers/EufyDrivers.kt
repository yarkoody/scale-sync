/*
 * Scale Sync
 * Ported from BLE Scale Sync (KristianP26/ble-scale-sync, GPLv3): src/scales/eufy-c20-p3.ts
 * (frame layout, final statuses, address-echo match, weight-only decision) and the broadcast
 * part of src/scales/eufy-p2.ts. Both credit bdr99/eufylife-ble-client (MIT) for the weight
 * offsets and names; openScale's EufyC20Handler (GPLv3) reads the same C20/P3 frame.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.yarkoody.scalesync.core.drivers

import io.github.yarkoody.scalesync.core.Advertisement
import io.github.yarkoody.scalesync.core.Capabilities
import io.github.yarkoody.scalesync.core.Field
import io.github.yarkoody.scalesync.core.Frame
import io.github.yarkoody.scalesync.core.ScaleDriver
import io.github.yarkoody.scalesync.core.ScaleFrame
import io.github.yarkoody.scalesync.core.SupportLevel
import io.github.yarkoody.scalesync.core.toHex
import io.github.yarkoody.scalesync.core.u16le
import io.github.yarkoody.scalesync.core.u8
import java.util.Locale

/** The advertiser's address as 12 upper-case hex digits, or null. */
internal fun ownAddressHex(ad: Advertisement): String? =
    ad.address.replace(":", "").replace("-", "").uppercase()
        .takeIf { it.length == 12 && it.all { c -> c.isDigit() || c in 'A'..'F' } }

/**
 * Eufy Smart Scale C20 (T9130) and P3 (T9150). The weigh-in is in a connectable advertisement's
 * manufacturer data (18, 23 or 24 bytes after the company id):
 *
 *   company id high byte + [0..4]  the scale's own address
 *   [5] 0x00, [6..9] scale clock (Unix s, LE, often unset), [10] status, [11] end flags,
 *   [12..13] weight LE, hundredths of a kg.
 *
 * Status: 0x00/0x0D idle (0 kg), 0x01 live, then 0x05, 0x15, 0x25, 0x65, 0xA5 with the locked
 * weight while the scale measures impedance and heart rate. Weight only: the bytes that look
 * like impedance were never compared with the Eufy app, so they are not used.
 */
object EufyC20P3Driver : ScaleDriver {
    private val NAMES = listOf("eufy t9130", "eufy t9150")
    private val FINAL = setOf(0x05, 0x15, 0x25, 0x65, 0xA5)
    private const val LIVE = 0x01

    override val id = "eufy_c20_p3"
    override val displayName = "Eufy Smart Scale C20 / P3"
    override val manufacturer = "Eufy"
    override val model = "Smart Scale C20 / P3"
    override val capabilities = Capabilities(setOf(Field.WEIGHT, Field.STABLE_FLAG), SupportLevel.EXPERIMENTAL)

    private fun shaped(d: ByteArray) = d.size >= 18 && d[5] == 0.toByte()

    private fun frame(ad: Advertisement): ByteArray? {
        val own = ownAddressHex(ad)
        val named = NAMES.any { ad.name?.trim()?.lowercase()?.startsWith(it) == true }
        val shapedEntries = ad.manufacturerData.filterValues(::shaped)
        // Prefer the entry that spells the scale's own address.
        shapedEntries.entries.firstOrNull { (id, d) ->
            own != null && String.format(Locale.ROOT, "%02X", (id shr 8) and 0xFF) + d.copyOfRange(0, 5).toHex().uppercase() == own
        }?.let { return it.value }
        return if (named) shapedEntries.values.firstOrNull() ?: ByteArray(0) else null
    }

    override fun matches(ad: Advertisement): Boolean = frame(ad) != null

    override fun parse(ad: Advertisement): ScaleFrame? {
        val d = frame(ad)?.takeIf(::shaped) ?: return null
        val status = d.u8(10)
        val stable = when (status) {
            LIVE -> false
            in FINAL -> true
            else -> return null
        }
        val kg = d.u16le(12) / 100f
        if (kg < 2f || kg > 250f) return null
        return Frame(weightKg = kg, stable = stable)
    }
}

/**
 * Eufy Smart Scale P2 (T9148) and P2 Pro (T9149), weight from the broadcast (the scale's body
 * composition needs an authenticated connection, which Scale Sync never makes).
 * 19 bytes under company id 0xFF48:
 *   [0..5] the scale's own address, [6] 0xCF, [7] heart rate, [8] flags,
 *   [9..10] weight LE, hundredths of a kg, [15] 0x00 when final.
 */
object EufyP2Driver : ScaleDriver {
    private const val COMPANY = 0xFF48
    private val NAMES = listOf("eufy t9148", "eufy t9149")

    override val id = "eufy_p2"
    override val displayName = "Eufy Smart Scale P2 / P2 Pro"
    override val manufacturer = "Eufy"
    override val model = "Smart Scale P2 / P2 Pro"
    override val capabilities = Capabilities(setOf(Field.WEIGHT, Field.STABLE_FLAG), SupportLevel.EXPERIMENTAL)

    private fun payload(ad: Advertisement): ByteArray? =
        ad.manufacturerData[COMPANY]?.takeIf { it.size >= 19 && it.u8(6) == 0xCF }

    override fun matches(ad: Advertisement): Boolean {
        if (NAMES.any { ad.name?.trim()?.lowercase()?.startsWith(it) == true }) return true
        val d = payload(ad) ?: return false
        return d.copyOfRange(0, 6).toHex().uppercase() == ownAddressHex(ad)
    }

    override fun parse(ad: Advertisement): ScaleFrame? {
        val d = payload(ad) ?: return null
        val kg = d.u16le(9) / 100f
        if (kg < 2f || kg > 200f) return null
        return Frame(weightKg = kg, stable = d.u8(15) == 0)
    }
}

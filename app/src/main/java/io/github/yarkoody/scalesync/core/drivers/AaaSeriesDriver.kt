/*
 * Scale Sync
 * Frame layout from openScale's AAAxHandler.kt (GPLv3) and BLE Scale Sync's
 * src/scales/silvergear-108.ts (GPLv3), which decode the same broadcast; the 18-bit weight mask
 * and the 0x06 body frame are BLE Scale Sync's findings.
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
import io.github.yarkoody.scalesync.core.u8

/**
 * AAA-series broadcast scales ("AAA002", "AAA006", "AAA007", "AAA013", "AAA07A"; Silvergear
 * Smart Scale 108, Posture FG220LB). 12 bytes of manufacturer data, usually under 0xA0AC:
 *   [0..5] the scale's own address reversed,
 *   [6..11] payload; bytes 0..3 XOR the company id's high byte (0xA0):
 *     p[0] bit 7 settled, p[1..3] weight in grams in the low 18 bits,
 *     p[4] type, in clear: 0x0D weight, 0x06 post-weigh-in body frame,
 *     p[5] low 5 bits: checksum of p[0..4], high 3 bits: display unit.
 * Weight only: the body frame looks like impedance but was never checked against the app.
 */
object AaaSeriesDriver : ScaleDriver {
    private const val TYPE_WEIGHT = 0x0D
    private const val TYPE_BODY = 0x06

    override val id = "aaa_series"
    override val displayName = "AAA-series broadcast scale (Silvergear, Posture, …)"
    override val manufacturer = "AAA-series"
    override val model = "AAA broadcast scale"
    override val capabilities = Capabilities(setOf(Field.WEIGHT, Field.STABLE_FLAG), SupportLevel.EXPERIMENTAL)

    private fun valid(company: Int, d: ByteArray, own: String?): Boolean {
        if (d.size != 12 || own == null) return false
        if (d.copyOfRange(0, 6).reversedArray().toHex().uppercase() != own) return false
        val p = d.copyOfRange(6, 12)
        if ((p.take(5).sumOf { it.toInt() and 0xFF } and 0x1F) != (p.u8(5) and 0x1F)) return false
        return p.u8(4) == TYPE_WEIGHT || p.u8(4) == TYPE_BODY
    }

    private fun entry(ad: Advertisement): Pair<Int, ByteArray>? {
        val own = ownAddressHex(ad)
        return ad.manufacturerData.entries.firstOrNull { (c, d) -> valid(c, d, own) }?.toPair()
    }

    override fun matches(ad: Advertisement): Boolean =
        entry(ad) != null || ad.name?.trim()?.uppercase()?.matches(Regex("AAA0[0-9A-Z]{2}")) == true

    override fun parse(ad: Advertisement): ScaleFrame? {
        val (company, d) = entry(ad) ?: return null
        val key = (company shr 8) and 0xFF
        if (d.u8(10) != TYPE_WEIGHT) return null
        val v = IntArray(4) { d.u8(6 + it) xor key }
        val grams = ((v[1] shl 16) or (v[2] shl 8) or v[3]) and 0x3FFFF
        val kg = grams / 1000f
        if (kg < 2f || kg > 250f) return null
        return Frame(weightKg = kg, stable = (v[0] and 0x80) != 0)
    }
}

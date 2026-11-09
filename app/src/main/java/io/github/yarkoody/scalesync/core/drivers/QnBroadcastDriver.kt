/*
 * Scale Sync
 * Ported from openScale's QNHandlerBroadcast.kt (QnBroadcastAdv, GPLv3), including the FT-26R
 * bit-0 stable rule; BLE Scale Sync's src/scales/qn-scale/broadcast.ts reads the same frame.
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
import io.github.yarkoody.scalesync.core.u16le
import io.github.yarkoody.scalesync.core.u8

/**
 * QN-protocol scales in broadcast mode (Renpho ES-CS20M / Elis 1 variants that never accept a
 * connection, FITINDEX FT-26R-W and similar). Manufacturer data under 0xFFFF:
 *   [0..1] AA BB, [2..7] device address, [15] status, [17..18] weight LE, hundredths of a kg.
 * Stable is status bit 5; the FT-26R family (bytes 51 0E 03 at [19..21]) uses bit 0 instead.
 * Weight only: the broadcast carries no impedance.
 */
object QnBroadcastDriver : ScaleDriver {
    private const val COMPANY = 0xFFFF
    private val FT26R_SIGNATURE = byteArrayOf(0x51, 0x0E, 0x03)

    override val id = "qn_broadcast"
    override val displayName = "QN broadcast scale (FITINDEX, Renpho, …)"
    override val manufacturer = "QN"
    override val model = "QN broadcast scale"
    override val capabilities = Capabilities(setOf(Field.WEIGHT, Field.STABLE_FLAG), SupportLevel.EXPERIMENTAL)

    private fun payload(ad: Advertisement): ByteArray? = ad.manufacturerData[COMPANY]
        ?.takeIf { it.size >= 19 && it.u8(0) == 0xAA && it.u8(1) == 0xBB }

    private fun isFt26r(d: ByteArray) =
        d.size >= 22 && FT26R_SIGNATURE.indices.all { d[19 + it] == FT26R_SIGNATURE[it] }

    override fun matches(ad: Advertisement): Boolean = payload(ad) != null

    override fun parse(ad: Advertisement): ScaleFrame? {
        val d = payload(ad) ?: return null
        val kg = d.u16le(17) / 100f
        if (kg < 2f || kg > 300f) return null
        val mask = if (isFt26r(d)) 0x21 else 0x20
        return Frame(weightKg = kg, stable = (d.u8(15) and mask) != 0)
    }
}

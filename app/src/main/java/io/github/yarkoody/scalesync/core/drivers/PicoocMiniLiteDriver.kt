/*
 * Scale Sync
 * Ported from BLE Scale Sync's src/scales/picooc-mini-lite.ts (KristianP26/ble-scale-sync,
 * GPLv3). The layout was first published by openScale's PicoocBroadcastHandler (GPLv3).
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
import io.github.yarkoody.scalesync.core.u16be
import io.github.yarkoody.scalesync.core.u8

/**
 * PICOOC Mini Lite, name "PICOOC-L", broadcast-only. 14 bytes of manufacturer data under a
 * company id that is really the first two bytes of the scale's address:
 *   [0..3] rest of the address, [4] 0x39 (result frame), [5] 0x1F,
 *   [6..7] weight BE in 1/20 kg, [8..9] maybe impedance (not used: the PICOOC app calibrates it
 *   per person), [10..11] weight again in 0.1 lb with bit 15 set, [12] unit, [13] checksum
 *   (all 16 bytes including the company id sum to 0xFF).
 * Only finished results are broadcast (no settling frames), so every frame is stable. The two
 * weight fields must agree, which catches a wrong unit or divisor.
 */
object PicoocMiniLiteDriver : ScaleDriver {
    override val id = "picooc_mini_lite"
    override val displayName = "PICOOC Mini Lite"
    override val manufacturer = "PICOOC"
    override val model = "Mini Lite"
    override val capabilities = Capabilities(setOf(Field.WEIGHT), SupportLevel.EXPERIMENTAL)

    override fun matches(ad: Advertisement): Boolean = ad.name?.trim()?.lowercase() == "picooc-l"

    override fun parse(ad: Advertisement): ScaleFrame? {
        for ((company, d) in ad.manufacturerData) {
            if (d.size != 14 || d.u8(4) != 0x39 || d.u8(5) != 0x1F) continue
            val sum = (company and 0xFF) + ((company shr 8) and 0xFF) + d.sumOf { it.toInt() and 0xFF }
            if (sum and 0xFF != 0xFF) continue
            val kg = d.u16be(6) / 20f
            val lbKg = (d.u16be(10) and 0x7FFF) / 10f / 2.20462f
            if (kotlin.math.abs(kg - lbKg) > maxOf(0.2f, kg * 0.01f)) continue
            if (kg < 2f || kg > 250f) continue
            return Frame(weightKg = kg, stable = true)
        }
        return null
    }
}

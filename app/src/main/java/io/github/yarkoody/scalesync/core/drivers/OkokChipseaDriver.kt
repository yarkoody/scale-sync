/*
 * Scale Sync
 * Ported from BLE Scale Sync's src/scales/okok-chipsea.ts (KristianP26/ble-scale-sync, GPLv3):
 * the two dialects, the properties whitelist, the address-echo match and the finding that
 * the "r1" field is not impedance. The same frames are decoded by openScale's OkOkHandler
 * (GPLv3) and, for C0, ble_monitor's Xiaogui parser (MIT).
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
import io.github.yarkoody.scalesync.core.u16be
import io.github.yarkoody.scalesync.core.u8

/**
 * Broadcast-only Chipsea scales sold under many brands with the OKOK app (NIX Home, Xiaogui
 * TZC4, MaxxMee QJ-J, "Yoda0"/"Yoda1", BL-26L01 "ADV", EB8217). Weight only.
 *
 * C0 dialect: 13 bytes under a company id whose low byte is 0xC0 (the high byte is a counter).
 *   [0..1] weight BE, [2..3] r1, [4..5] constant, [6] properties, [7..12] the scale's own address
 *   (zeros on Yoda units).
 * 2.0 dialect: 19 bytes under company id 0x20CA, name "ADV".
 *   [6] properties, [7] counter, [8..9] weight BE, [10..11] r1, [12] 0x20 XOR [0..11],
 *   [13..18] the scale's own address.
 *
 * "r1" is published as impedance elsewhere, but it is a constant (600.0 or 500.0) on every unit
 * captured, so it is ignored: passing it on would turn a constant into a body fat figure.
 * Only the properties values seen in real captures are decoded (all kg); others are refused.
 */
object OkokChipseaDriver : ScaleDriver {
    private const val C0_ID_LOW = 0xC0
    private const val C0_LEN = 13
    private const val V20_ID = 0x20CA
    private const val V20_LEN = 19
    private const val MIN_KG = 2f
    private const val MAX_KG = 300f

    /** properties byte -> (final, divisor) */
    private val C0_PROPS = mapOf(0x20 to (false to 10f), 0x21 to (true to 10f), 0x24 to (false to 100f), 0x25 to (true to 100f))
    private val V20_PROPS = mapOf(0x01 to (true to 10f), 0x04 to (false to 100f), 0x05 to (true to 100f))

    override val id = "okok_chipsea"
    override val displayName = "OKOK / Chipsea scale"
    override val manufacturer = "Chipsea"
    override val model = "OKOK broadcast scale"
    override val capabilities = Capabilities(
        fields = setOf(Field.WEIGHT, Field.STABLE_FLAG),
        support = SupportLevel.EXPERIMENTAL,
    )

    private fun c0Shaped(d: ByteArray) = d.size == C0_LEN && (d.u8(6) and 0xE0) == 0x20

    private fun v20ChecksumOk(d: ByteArray): Boolean {
        if (d.size != V20_LEN) return false
        var x = 0x20
        for (i in 0 until 12) x = x xor d.u8(i)
        return x == d.u8(12)
    }

    private fun echoed(d: ByteArray, offset: Int) = d.copyOfRange(offset, offset + 6).toHex().uppercase()

    /** The (company id, payload) entry this driver claims, if any. */
    private fun claimed(ad: Advertisement): Pair<Int, ByteArray>? {
        val own = ownAddressHex(ad)
        val name = ad.name?.trim()?.lowercase().orEmpty()
        val yoda = name.startsWith("yoda0") || name.startsWith("yoda1")
        for ((id, d) in ad.manufacturerData) {
            if ((id and 0xFF) == C0_ID_LOW && c0Shaped(d)) {
                val echo = echoed(d, 7)
                if (own != null && echo == own) return id to d
                if (yoda && (own == null || echo == "000000000000")) return id to d
            } else if (id == V20_ID && v20ChecksumOk(d)) {
                if (if (own != null) echoed(d, 13) == own else name == "adv") return id to d
            }
        }
        return null
    }

    override fun matches(ad: Advertisement): Boolean = claimed(ad) != null

    override fun parse(ad: Advertisement): ScaleFrame? {
        val (id, d) = claimed(ad) ?: return null
        val (final, divisor) = if (id == V20_ID) V20_PROPS[d.u8(6)] ?: return null
        else C0_PROPS[d.u8(6)] ?: return null
        val raw = if (id == V20_ID) d.u16be(8) else d.u16be(0)
        val kg = raw / divisor
        if (kg < MIN_KG || kg > MAX_KG) return null
        return Frame(weightKg = kg, stable = final)
    }
}

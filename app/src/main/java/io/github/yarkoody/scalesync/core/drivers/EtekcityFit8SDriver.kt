/*
 * Scale Sync
 * Ported from BLE Scale Sync's src/scales/etekcity-fit8s.ts (KristianP26/ble-scale-sync,
 * GPLv3): layout, address-echo match and the weight-only decision. The layout was first published
 * by ronnnnnnnnnnnnn/etekcity_esf551_ble (MIT); openScale's EtekcityFit8SHandler (GPLv3) is based on it.
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
 * Etekcity Fit 8S: broadcast-only, nameless, 20 bytes under company id 0x06D0 (VeSync, shared
 * with other Etekcity scales and even air purifiers, hence the address check):
 *   [0] 0x01, [1..6] the scale's own address reversed, [10..12] weight in grams (uint24 LE),
 *   [13..14] maybe impedance (never checked against the app, so not used), [15] 0x01 stable,
 *   [16] display unit (the weight is grams whatever it shows).
 */
object EtekcityFit8SDriver : ScaleDriver {
    private const val COMPANY = 0x06D0
    private val SERVICE_FFD0 = Advertisement.uuid16(0xFFD0)

    override val id = "etekcity_fit8s"
    override val displayName = "Etekcity Fit 8S"
    override val manufacturer = "Etekcity"
    override val model = "Fit 8S"
    override val capabilities = Capabilities(setOf(Field.WEIGHT, Field.STABLE_FLAG), SupportLevel.EXPERIMENTAL)

    private fun payload(ad: Advertisement): ByteArray? {
        val d = ad.manufacturerData[COMPANY]?.takeIf { it.size == 20 } ?: return null
        if (ad.serviceUuids.isNotEmpty() && SERVICE_FFD0 !in ad.serviceUuids) return null
        val echoed = d.copyOfRange(1, 7).reversedArray().toHex().uppercase()
        return d.takeIf { echoed == ownAddressHex(ad) }
    }

    override fun matches(ad: Advertisement): Boolean = payload(ad) != null

    override fun parse(ad: Advertisement): ScaleFrame? {
        val d = payload(ad) ?: return null
        if (d.u8(0) != 0x01) return null
        val stable = when (d.u8(15)) {
            0x00 -> false
            0x01 -> true
            else -> return null
        }
        val grams = d.u8(10) or (d.u8(11) shl 8) or (d.u8(12) shl 16)
        val kg = grams / 1000f
        if (kg < 2f || kg > 250f) return null
        return Frame(weightKg = kg, stable = stable)
    }
}

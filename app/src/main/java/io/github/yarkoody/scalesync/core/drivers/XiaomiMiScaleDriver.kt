/*
 * Scale Sync
 * 10-byte layout as in openScale's MiScaleHandler.parseHistory10
 * (Copyright (C) 2025 olie.xdev, GPLv3). The same layout is documented in
 * Bluetooth-Devices/xiaomi-ble (Apache-2.0), parser.py _parse_scale_v1.
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
import io.github.yarkoody.scalesync.core.WeightUnit
import io.github.yarkoody.scalesync.core.u16le
import io.github.yarkoody.scalesync.core.u8
import java.time.LocalDateTime

/**
 * Xiaomi Mi Smart Scale (XMTZC01HM, "MI_SCALE") and Mi Smart Scale 2 (XMTZC04HM, "MI SCALE2"):
 * weight only, a 10-byte frame in service data 0x181D (Weight Scale).
 *
 * Layout: [status][weightLE(2)][yearLE(2)][mon][day][h][m][s]
 * status: bit0 lb, bit4 jin (catty), bit5 stable, bit7 removed. Weight is kg*200, or lb/jin*100.
 */
object XiaomiMiScaleDriver : ScaleDriver {
    const val SERVICE_DATA_UUID_16 = 0x181D
    private const val MI_BCS_SERVICE_16 = 0x181B
    private const val LBS_TO_KG = 0.45359237f
    private const val JIN_TO_KG = 0.5f

    override val id = "xiaomi_miscale"
    override val displayName = "Xiaomi Mi Smart Scale / Mi Smart Scale 2"
    override val manufacturer = "Xiaomi"
    override val model = "Mi Smart Scale"
    override val capabilities = Capabilities(
        fields = setOf(Field.WEIGHT, Field.STABLE_FLAG, Field.SCALE_CLOCK),
        support = SupportLevel.EXPERIMENTAL,
    )

    override fun matches(ad: Advertisement): Boolean {
        // A body composition scale advertising its 13-byte frame belongs to XiaomiMiBcsDriver,
        // whatever its name says ("MI SCALE2" is used by both families).
        if (ad.serviceData16(MI_BCS_SERVICE_16)?.size == 13) return false
        val name = ad.upperName
        return name.startsWith("MI_SCALE") || name == "MI SCALE2" ||
            ad.serviceData16(SERVICE_DATA_UUID_16)?.size == 10
    }

    override fun parse(ad: Advertisement): ScaleFrame? {
        val d = ad.serviceData16(SERVICE_DATA_UUID_16) ?: return null
        if (d.size != 10) return null
        val status = d.u8(0)
        val isLbs = (status and 0x01) != 0
        val isCatty = (status and 0x10) != 0
        val raw = d.u16le(1)
        val weightKg = when {
            isLbs -> raw / 100f * LBS_TO_KG
            isCatty -> raw / 100f * JIN_TO_KG
            else -> raw / 200f
        }
        if (weightKg <= 0f || weightKg > 250f) return null
        val time = runCatching {
            LocalDateTime.of(d.u16le(3), d.u8(5), d.u8(6), d.u8(7), d.u8(8), d.u8(9))
        }.getOrNull()?.takeIf { it.year >= 2020 }
        return Frame(
            weightKg = weightKg,
            stable = (status and 0x20) != 0,
            removed = (status and 0x80) != 0,
            scaleTime = time,
            unit = when {
                isLbs -> WeightUnit.LB
                isCatty -> WeightUnit.JIN
                else -> WeightUnit.KG
            },
        )
    }
}

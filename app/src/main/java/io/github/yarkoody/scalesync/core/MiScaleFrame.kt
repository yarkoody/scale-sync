/*
 * Scale Sync
 * Frame layout taken from openScale's MiScaleHandler.parseLive13
 * (Copyright (C) 2025 olie.xdev, GPLv3) and prototux/MIBCS-reverse-engineering.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.yarkoody.scalesync.core

import java.time.LocalDateTime

/**
 * One 13-byte frame from a Xiaomi Mi Body Composition Scale (1 or 2, BLE name MIBCS/MIBFS).
 *
 * The scale puts this frame in the service data of its advertisements under UUID 0x181B
 * while someone is standing on it, so it can be read without connecting.
 *
 * Layout: [ctrl0][ctrl1][yearLE(2)][mon][day][h][m][s][impedanceLE(2)][weightLE(2)]
 */
data class MiScaleFrame(
    val weightKg: Float,
    /** Ohms, or null when the frame carries no impedance (still measuring, or shoes/socks). */
    val impedance: Int?,
    val stable: Boolean,
    val removed: Boolean,
    /** The scale's own clock, local time. Null if it is clearly unset. */
    val scaleTime: LocalDateTime?,
) {
    companion object {
        const val SERVICE_DATA_UUID_16 = 0x181B
        private const val LBS_TO_KG = 0.45359237f
        private const val JIN_TO_KG = 0.5f

        fun parse(d: ByteArray?): MiScaleFrame? {
            if (d == null || d.size != 13) return null
            val c0 = d[0].toInt() and 0xFF
            val c1 = d[1].toInt() and 0xFF
            val isLbs = (c0 and 0x01) != 0
            val isCatty = (c1 and 0x40) != 0
            val stable = (c1 and 0x20) != 0
            val removed = (c1 and 0x80) != 0
            val hasImp = (c1 and 0x02) != 0

            val weightRaw = u16(d, 11)
            val weightKg = when {
                isLbs -> weightRaw / 100.0f * LBS_TO_KG
                isCatty -> weightRaw / 100.0f * JIN_TO_KG
                else -> weightRaw / 200.0f
            }
            if (weightKg <= 0f || weightKg > 250f) return null

            val imp = u16(d, 9)
            val impedance = if (hasImp && imp in 1..2999) imp else null

            val scaleTime = runCatching {
                LocalDateTime.of(
                    u16(d, 2), d[4].toInt() and 0xFF, d[5].toInt() and 0xFF,
                    d[6].toInt() and 0xFF, d[7].toInt() and 0xFF, d[8].toInt() and 0xFF,
                )
            }.getOrNull()?.takeIf { it.year >= 2020 }

            return MiScaleFrame(weightKg, impedance, stable, removed, scaleTime)
        }

        private fun u16(d: ByteArray, i: Int) =
            ((d[i + 1].toInt() and 0xFF) shl 8) or (d[i].toInt() and 0xFF)
    }
}

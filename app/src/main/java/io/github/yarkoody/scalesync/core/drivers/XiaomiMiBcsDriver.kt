/*
 * Scale Sync
 * Detection rules from openScale's MiScaleHandler.supportFor
 * (Copyright (C) 2025 olie.xdev, GPLv3). Frame decoding is in ../MiScaleFrame.kt.
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
import io.github.yarkoody.scalesync.core.MiScaleFrame
import io.github.yarkoody.scalesync.core.ScaleDriver
import io.github.yarkoody.scalesync.core.ScaleFrame
import io.github.yarkoody.scalesync.core.SupportLevel
import io.github.yarkoody.scalesync.core.VendorFormula

/**
 * Xiaomi Mi Body Composition Scale (XMTZC02HM) and Mi Body Composition Scale 2 (XMTZC05HM),
 * BLE name MIBCS/MIBFS. A 13-byte frame in service data 0x181B, with impedance.
 */
object XiaomiMiBcsDriver : ScaleDriver {
    override val id = "xiaomi_mibcs"
    override val displayName = "Xiaomi Mi Body Composition Scale 2"
    override val manufacturer = "Xiaomi"
    override val model = "Mi Body Composition Scale 2"
    override val capabilities = Capabilities(
        fields = setOf(Field.WEIGHT, Field.IMPEDANCE, Field.STABLE_FLAG, Field.SCALE_CLOCK),
        support = SupportLevel.VERIFIED,
        vendorFormula = VendorFormula.XIAOMI,
    )

    override fun matches(ad: Advertisement): Boolean {
        val name = ad.upperName
        return name.startsWith("MIBCS") || name.startsWith("MIBFS") ||
            ad.serviceData16(MiScaleFrame.SERVICE_DATA_UUID_16)?.size == 13
    }

    override fun parse(ad: Advertisement): ScaleFrame? =
        MiScaleFrame.parse(ad.serviceData16(MiScaleFrame.SERVICE_DATA_UUID_16))
}

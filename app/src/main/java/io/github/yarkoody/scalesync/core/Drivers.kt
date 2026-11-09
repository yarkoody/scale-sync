package io.github.yarkoody.scalesync.core

import io.github.yarkoody.scalesync.core.drivers.AaaSeriesDriver
import io.github.yarkoody.scalesync.core.drivers.EtekcityFit8SDriver
import io.github.yarkoody.scalesync.core.drivers.EufyC20P3Driver
import io.github.yarkoody.scalesync.core.drivers.EufyP2Driver
import io.github.yarkoody.scalesync.core.drivers.OkokChipseaDriver
import io.github.yarkoody.scalesync.core.drivers.PicoocMiniLiteDriver
import io.github.yarkoody.scalesync.core.drivers.QnBroadcastDriver
import io.github.yarkoody.scalesync.core.drivers.XiaomiMiBcsDriver
import io.github.yarkoody.scalesync.core.drivers.XiaomiMiScaleDriver

/** Every scale Scale Sync can read. Order matters only for display. */
object Drivers {
    val all: List<ScaleDriver> = listOf(
        XiaomiMiBcsDriver,
        XiaomiMiScaleDriver,
        OkokChipseaDriver,
        EufyC20P3Driver,
        EufyP2Driver,
        QnBroadcastDriver,
        EtekcityFit8SDriver,
        PicoocMiniLiteDriver,
        AaaSeriesDriver,
    )

    /** Used for installs paired before drivers existed: they could only pair a Mi BCS. */
    val legacyDefault: ScaleDriver = XiaomiMiBcsDriver

    fun byId(id: String?): ScaleDriver? = all.firstOrNull { it.id == id }

    /** Drivers that recognise [ad]. More than one means the user has to pick. */
    fun matching(ad: Advertisement): List<ScaleDriver> = all.filter { runCatching { it.matches(ad) }.getOrDefault(false) }

    /** [driver]'s reading from [ad], or null. Never throws: a malformed packet is just ignored. */
    fun parse(driver: ScaleDriver, ad: Advertisement): ScaleFrame? =
        runCatching { if (driver.matches(ad)) driver.parse(ad) else null }.getOrNull()
}

package io.github.yarkoody.scalesync.core

import io.github.yarkoody.scalesync.core.drivers.XiaomiMiBcsDriver

/** Every scale Scale Sync can read. Order matters only for display. */
object Drivers {
    val all: List<ScaleDriver> = listOf(
        XiaomiMiBcsDriver,
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

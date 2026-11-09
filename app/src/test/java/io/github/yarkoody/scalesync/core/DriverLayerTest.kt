package io.github.yarkoody.scalesync.core

import io.github.yarkoody.scalesync.core.drivers.XiaomiMiBcsDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DriverLayerTest {
    /** Mi BCS 2 frame: stable, impedance 480 ohm, 72.35 kg, 2026-10-14 07:30:05. */
    private val miFrame = "0226ea070a0e071e05e0018638".hexToBytes()

    private fun miAd(name: String? = "MIBFS", data: ByteArray? = miFrame) = Advertisement(
        address = "C8:47:8C:00:00:01", name = name,
        serviceData = data?.let { mapOf(Advertisement.uuid16(0x181B) to it) } ?: emptyMap(),
    )

    @Test
    fun uuid16_round_trips() {
        assertEquals("0000181b-0000-1000-8000-00805f9b34fb", Advertisement.uuid16(0x181B).toString())
        assertEquals(0x181B, Advertisement.short(Advertisement.uuid16(0x181B)))
        assertNull(Advertisement.short(java.util.UUID.fromString("00001530-0000-3512-2118-0009af100700")))
    }

    @Test
    fun mi_bcs_is_detected_and_parsed_through_the_driver() {
        val ad = miAd()
        assertEquals(listOf(XiaomiMiBcsDriver), Drivers.matching(ad))
        val f = Drivers.parse(XiaomiMiBcsDriver, ad)!!
        assertEquals(72.35f, f.weightKg, 0.001f)
        assertEquals(480, f.impedance)
        assertEquals(true, f.stable)
        assertEquals(WeightUnit.KG, f.unit)
    }

    @Test
    fun mi_bcs_matches_by_name_or_frame_but_reads_only_frames() {
        assertTrue(XiaomiMiBcsDriver.matches(miAd(name = "MIBCS", data = null)))
        assertNull(Drivers.parse(XiaomiMiBcsDriver, miAd(name = "MIBCS", data = null)))
        assertTrue(XiaomiMiBcsDriver.matches(miAd(name = null)))
        assertTrue(Drivers.matching(Advertisement("00:11:22:33:44:55", "Phone")).isEmpty())
    }

    @Test
    fun drivers_never_throw_on_garbage() {
        val junk = Advertisement(
            "00:11:22:33:44:55", "MIBFS",
            serviceData = mapOf(Advertisement.uuid16(0x181B) to byteArrayOf(1, 2, 3)),
            manufacturerData = mapOf(0xFFFF to byteArrayOf()),
        )
        Drivers.all.forEach { assertNull(Drivers.parse(it, junk)) }
    }

    @Test
    fun driver_ids_are_unique() {
        assertEquals(Drivers.all.size, Drivers.all.map { it.id }.toSet().size)
        assertEquals(XiaomiMiBcsDriver, Drivers.byId("xiaomi_mibcs"))
        assertEquals(SupportLevel.VERIFIED, XiaomiMiBcsDriver.capabilities.support)
    }

    @Test
    fun capture_line_carries_raw_hex_and_decoding() {
        val line = Capture.line("2026-10-14T07:30:06", miAd())
        assertTrue(line, line.contains("svc[181b]=0226ea070a0e071e05e0018638"))
        assertTrue(line, line.contains("driver=xiaomi_mibcs:72.35kg:480ohm:stable"))
        assertTrue(Capture.line("t", Advertisement("aa", "x")).endsWith("driver=none"))
    }

    // --- Body composition chosen by what arrived ---

    private val profile = Profile(180f, LocalDate.of(1996, 1, 1), Sex.MALE, Algorithm.XIAOMI)
    private val day = LocalDate.of(2026, 1, 2)

    @Test
    fun xiaomi_impedance_uses_the_profile_formula() {
        val m = BodyComposition.compute(80f, 500, profile, day, VendorFormula.XIAOMI)
        assertEquals(XiaomiLib(Sex.MALE, 30, 180f).bodyFat(80f, 500f), m.fatPercent!!, 1e-4f)
        // Default argument keeps the old behaviour.
        assertEquals(m, BodyComposition.compute(80f, 500, profile, day))
    }

    @Test
    fun impedance_without_vendor_formula_uses_bodymiscale() {
        val m = BodyComposition.compute(80f, 500, profile, day, vendorFormula = null)
        val s = ScienceLib(Sex.MALE, 30, 180f)
        assertEquals(s.fat(80f, s.lbm(80f, 500f)), m.fatPercent!!, 1e-4f)
    }

    @Test
    fun weight_only_gives_bmi_and_bmr_but_no_body_fat() {
        val m = BodyComposition.compute(80f, null, profile, day, vendorFormula = null)
        assertNull(m.fatPercent)
        assertNull(m.waterPercent)
        assertNull(m.leanKg)
        assertFalse(m.hasComposition)
        assertEquals(80f / (1.8f * 1.8f), m.bmi!!, 1e-3f)
        assertEquals(ScienceLib(Sex.MALE, 30, 180f).bmr(80f), m.bmrKcal!!, 1e-3f)
    }
}

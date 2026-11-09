package io.github.yarkoody.scalesync.core.drivers

import io.github.yarkoody.scalesync.core.Advertisement
import io.github.yarkoody.scalesync.core.Drivers
import io.github.yarkoody.scalesync.core.WeightUnit
import io.github.yarkoody.scalesync.core.hexToBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Captures are real advertisements from the tests of Bluetooth-Devices/xiaomi-ble
 * (tests/test_parser.py: test_Xiaomi_Scale1*, test_Xiaomi_Scale2*), with their expected values.
 */
class XiaomiDriversTest {
    private fun ad(uuid16: Int, hex: String, name: String? = null) = Advertisement(
        address = "C4:DA:6C:00:B5:DC", name = name,
        serviceData = mapOf(Advertisement.uuid16(uuid16) to hex.hexToBytes()),
        serviceUuids = listOf(Advertisement.uuid16(uuid16)),
    )

    private fun v1(hex: String, name: String? = null) = ad(0x181D, hex, name)
    private fun v2(hex: String) = ad(0x181B, hex)

    @Test
    fun v1_stable_weight() {
        // test_Xiaomi_Scale1: 86.55 kg, stabilized.
        val a = v1("229e43e507040b101301")
        assertEquals(listOf(XiaomiMiScaleDriver), Drivers.matching(a))
        val f = Drivers.parse(XiaomiMiScaleDriver, a)!!
        assertEquals(86.55f, f.weightKg, 0.001f)
        assertEquals(true, f.stable)
        assertFalse(f.removed)
        assertNull(f.impedance)
        assertEquals(WeightUnit.KG, f.unit)
        assertEquals(LocalDateTime.of(2021, 4, 11, 16, 19, 1), f.scaleTime)
    }

    @Test
    fun v1_mass_removed() {
        // test_Xiaomi_Scale1_mass_removed: 87.2 kg, stepped off.
        val f = Drivers.parse(XiaomiMiScaleDriver, v1("a22044b20701010a1a15"))!!
        assertEquals(87.2f, f.weightKg, 0.001f)
        assertTrue(f.removed)
    }

    @Test
    fun v1_non_stabilized_and_empty() {
        // test_Xiaomi_Scale1_non_stabilized: 0.1 kg; test_Xiaomi_Scale1_reset: 0 kg.
        val f = Drivers.parse(XiaomiMiScaleDriver, v1("821400e507040b101708"))!!
        assertEquals(0.1f, f.weightKg, 0.001f)
        assertEquals(false, f.stable)
        assertNull(Drivers.parse(XiaomiMiScaleDriver, v1("800000e507040b101301")))
    }

    @Test
    fun v1_units() {
        // Same frame with the lb bit, and with the jin bit (synthetic: flags changed only).
        val lb = Drivers.parse(XiaomiMiScaleDriver, v1("21" + "e04b" + "e507040b101301"))!!
        assertEquals(194.24f * 0.45359237f, lb.weightKg, 0.01f)
        assertEquals(WeightUnit.LB, lb.unit)
        val jin = Drivers.parse(XiaomiMiScaleDriver, v1("30" + "a843" + "e507040b101301"))!!
        assertEquals(173.2f * 0.5f, jin.weightKg, 0.01f)
        assertEquals(WeightUnit.JIN, jin.unit)
    }

    @Test
    fun v1_is_found_by_name_before_it_has_a_reading() {
        val bare = Advertisement("C4:DA:6C:00:B5:DC", "MI SCALE2")
        assertEquals(listOf(XiaomiMiScaleDriver), Drivers.matching(bare))
        assertNull(Drivers.parse(XiaomiMiScaleDriver, bare))
        assertEquals(listOf(XiaomiMiScaleDriver), Drivers.matching(Advertisement("x", "MI_SCALE")))
    }

    @Test
    fun v2_real_capture_with_impedance() {
        // test_Xiaomi_Scale2: 85.15 kg, impedance 428.
        val a = v2("0226b2070504 0f0201ac018642".replace(" ", ""))
        assertEquals(listOf(XiaomiMiBcsDriver), Drivers.matching(a))
        val f = Drivers.parse(XiaomiMiBcsDriver, a)!!
        assertEquals(85.15f, f.weightKg, 0.001f)
        assertEquals(428, f.impedance)
        assertEquals(true, f.stable)
    }

    @Test
    fun v2_real_captures_non_stabilized_and_reset() {
        // test_Xiaomi_Scale2_non_stabilized: 105.8 kg; test_Xiaomi_Scale2_reset: empty.
        val f = Drivers.parse(XiaomiMiBcsDriver, v2("0204b207010112101a0000a852"))!!
        assertEquals(105.8f, f.weightKg, 0.001f)
        assertEquals(false, f.stable)
        assertNull(f.impedance)
        assertNull(Drivers.parse(XiaomiMiBcsDriver, v2("0282b2070504 0f020100000000".replace(" ", ""))))
    }

    @Test
    fun families_do_not_claim_each_other() {
        assertFalse(XiaomiMiBcsDriver.matches(v1("229e43e507040b101301")))
        assertFalse(XiaomiMiScaleDriver.matches(v2("0226b20705040f0201ac018642")))
        // A body composition scale that calls itself "MI SCALE2" stays with the BCS driver.
        val bcsNamedScale2 = ad(0x181B, "0226b20705040f0201ac018642", name = "MI SCALE2")
        assertEquals(listOf(XiaomiMiBcsDriver), Drivers.matching(bcsNamedScale2))
    }
}

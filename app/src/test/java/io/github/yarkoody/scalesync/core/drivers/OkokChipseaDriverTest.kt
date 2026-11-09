package io.github.yarkoody.scalesync.core.drivers

import io.github.yarkoody.scalesync.core.Drivers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real frames, as collected in BLE Scale Sync's tests/scales/okok-chipsea.test.ts (each from a
 * public btsnoop capture: openScale issues #1191, #950, #1177, #410, #496, #930 and
 * ble_monitor #537, #824), with the weights the reporters or vendor apps gave.
 */
class OkokChipseaDriverTest {
    private val d = OkokChipseaDriver

    // C0 dialect. NIX Home, openScale #1191.
    private val nixMac = "08:B8:D0:DE:B5:3A"
    private val nixIdle = "000000000a012408b8d0deb53a"
    private val nixSettling147 = "396c00000a012408b8d0deb53a"
    private val nixStable = listOf(
        "396c17700a012508b8d0deb53a" to 147.00f,
        "3a6b17700a012508b8d0deb53a" to 149.55f,
        "3a1617700a012508b8d0deb53a" to 148.70f,
        "399e17700a012508b8d0deb53a" to 147.50f,
    )
    // openScale #950 (arnelap).
    private val arnMac = "5D:02:49:A6:3D:E8"
    private val arnStable = "1fd617700a01255d0249a63de8"
    // openScale #1177, named Yoda0, zeros instead of its address.
    private val yodaMac = "08:B8:D0:E8:5F:6D"
    private val yodaStable = "1dce1388000025000000000000"
    // ble_monitor #537, Xiaogui TZC4, one decimal.
    private val tzc4Mac = "5F:5A:29:E5:E0:94"
    private val tzc4Stable = "0276138b0002215f5a29e5e094"
    private val tzc4Settling = "027a00000002205f5a29e5e094"
    // ble_monitor #824, MaxxMee QJ-J.
    private val qjjMac = "70:14:F6:07:65:EC"
    private val qjjStable = "2c1500000a01257014f60765ec"

    // 2.0 dialect. BL-26L01 "ADV", openScale #410.
    private val v20Mac = "ED:67:37:26:69:D7"
    private val v20Idle = "0b41af2f81010400000000006eed67372669d7"
    private val v20Final = listOf(
        "0b41af2f810105051a90177087ed67372669d7" to 68.00f,
        "0b41af2f810105061bda1770cfed67372669d7" to 71.30f,
        "0b41af2f810105071ad11770c4ed67372669d7" to 68.65f,
    )
    private val v20Settling = "0b41af2f810104011ad60000a3ed67372669d7"
    // EB8217, openScale #496: logged without its address, so only the name ADV claims it.
    private val eb8217 = "0b000000000101eb03b6138aeced67371b508c"

    @Test
    fun c0_stable_weights() {
        for ((hex, kg) in nixStable) {
            val ad = mfrAd(0x10C0, hex, nixMac)
            assertEquals(listOf(OkokChipseaDriver), Drivers.matching(ad))
            val f = Drivers.parse(d, ad)!!
            assertEquals(kg, f.weightKg, 0.001f)
            assertEquals(true, f.stable)
            assertNull("r1 is not impedance", f.impedance)
        }
        assertEquals(81.5f, Drivers.parse(d, mfrAd(0xB1C0, arnStable, arnMac))!!.weightKg, 0.001f)
        assertEquals(112.85f, Drivers.parse(d, mfrAd(0x9EC0, qjjStable, qjjMac))!!.weightKg, 0.001f)
        assertEquals(63.0f, Drivers.parse(d, mfrAd(0xA3C0, tzc4Stable, tzc4Mac))!!.weightKg, 0.001f)
    }

    @Test
    fun c0_settling_and_idle() {
        val s = Drivers.parse(d, mfrAd(0x1AC0, nixSettling147, nixMac))!!
        assertEquals(147.0f, s.weightKg, 0.001f)
        assertEquals(false, s.stable)
        assertEquals(63.4f, Drivers.parse(d, mfrAd(0x8FC0, tzc4Settling, tzc4Mac))!!.weightKg, 0.001f)
        // Idle frames are still this scale (for pairing) but carry no reading.
        assertTrue(d.matches(mfrAd(0x03C0, nixIdle, nixMac)))
        assertNull(Drivers.parse(d, mfrAd(0x03C0, nixIdle, nixMac)))
    }

    @Test
    fun c0_needs_its_own_address_or_a_yoda_name() {
        assertFalse(d.matches(mfrAd(0x10C0, nixStable[0].first, arnMac)))
        assertFalse(d.matches(mfrAd(0x10C1, nixStable[0].first, nixMac)))
        assertFalse(d.matches(mfrAd(0x10C0, withByte(nixStable[0].first, 6, 0x65), nixMac)))
        assertFalse(d.matches(mfrAd(0x10C0, nixStable[0].first.dropLast(2), nixMac)))
        val yoda = mfrAd(0x3FC0, yodaStable, yodaMac, name = "Yoda0")
        assertTrue(d.matches(yoda))
        assertEquals(76.3f, Drivers.parse(d, yoda)!!.weightKg, 0.001f)
        assertFalse(d.matches(mfrAd(0x3FC0, yodaStable, yodaMac, name = "")))
        assertFalse(d.matches(mfrAd(0x3FC0, yodaStable, yodaMac, name = "Yoda")))
    }

    @Test
    fun v20_final_and_settling() {
        for ((hex, kg) in v20Final) {
            val f = Drivers.parse(d, mfrAd(0x20CA, hex, v20Mac, name = "ADV"))!!
            assertEquals(kg, f.weightKg, 0.001f)
            assertEquals(true, f.stable)
        }
        val s = Drivers.parse(d, mfrAd(0x20CA, v20Settling, v20Mac, name = "ADV"))!!
        assertEquals(68.70f, s.weightKg, 0.001f)
        assertEquals(false, s.stable)
        assertTrue(d.matches(mfrAd(0x20CA, v20Idle, v20Mac)))
        assertNull(Drivers.parse(d, mfrAd(0x20CA, v20Idle, v20Mac)))
    }

    @Test
    fun v20_checksum_address_and_id_must_agree() {
        assertFalse(d.matches(mfrAd(0x20CA, withByte(v20Final[0].first, 12, 0x86), v20Mac)))
        assertFalse(d.matches(mfrAd(0x20CA, v20Final[0].first, nixMac, name = "ADV")))
        assertFalse(d.matches(mfrAd(0x20CB, v20Final[0].first, v20Mac, name = "ADV")))
    }

    @Test
    fun v20_by_name_when_the_address_is_not_echoed() {
        // Android always knows the address; this frame's tail is another unit's, so only a
        // missing address would let the name claim it. Here the address doesn't match.
        assertFalse(d.matches(mfrAd(0x20CA, eb8217, v20Mac, name = "ADV")))
        val ownTail = "ED:67:37:1B:50:8C"
        val f = Drivers.parse(d, mfrAd(0x20CA, eb8217, ownTail, name = "ADV"))!!
        assertEquals(95.0f, f.weightKg, 0.001f)
    }

    @Test
    fun other_chipsea_dialects_are_not_claimed() {
        // V1.1 (0x11CA) and 0xF0FF, both named Chipsea-BLE (openScale #496, #930).
        assertFalse(d.matches(mfrAd(0x11CA, "0f000102f25821332e0000000000000041c8b21e156edb", "08:B2:1E:15:6E:DB", "Chipsea-BLE")))
        assertFalse(d.matches(mfrAd(0xF0FF, "0204db03000000000000000050fb19c01c6d", name = "Chipsea-BLE")))
    }
}

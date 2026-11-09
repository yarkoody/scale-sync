package io.github.yarkoody.scalesync.core.drivers

import io.github.yarkoody.scalesync.core.Advertisement
import io.github.yarkoody.scalesync.core.Drivers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C20/P3: real frames from Home Assistant debug logs owners posted in bdr99/eufylife-ble-client
 * #3 and #5, as collected in BLE Scale Sync's tests/scales/eufy-c20-p3.test.ts (comment and line
 * cited there). P2 Pro: the two frames in bdr99/eufylife-ble-client#8 (issue body); the reporter
 * confirmed weight and heart rate decode correctly but did not state them.
 */
class EufyDriversTest {
    private val c20 = EufyC20P3Driver
    private val p2 = EufyP2Driver

    // C20 (T9130), BC:0F:B7:00:9C:9D, id 0xBC64, #5 comment 2564606224: "100.1kg".
    private val woods = "BC:0F:B7:00:9C:9D"
    private val s1Idle = "0fb7009c9d00c0c970670000000000000000"
    private val s1Off0d = "0fb7009c9d00c0c970670d00000000000000"
    private val s1Live = "0fb7009c9d00c3c9706701001a2700000000"
    private val s1Lock = "0fb7009c9d00c5c9706705001a2700000000"
    private val s1Status15 = "0fb7009c9d00c7c9706715001a2700000000"
    private val s1End25 = "0fb7009c9d00c9c9706725801a27000000641104038a0000"

    // P3 (T9150), CF:E7:09:18:28:04, id 0xCF4D, #3 comment 2564412265: locked 82.80 kg.
    private val duke = "CF:E7:09:18:28:04"
    private val s3Lock = "e709182804002c4f706705005820000000000000000000"
    private val s3A5End = "e70918280400384f7067a5805820005f000b143534d900"
    // P3, CF:E8:07:12:20:9D, id 0xCF5D, #3 comment 2870205491: 68.55 kg, clock never set (2022).
    private val branda = "CF:E8:07:12:20:9D"
    private val s4Lock = "e80712209d0058d6f9610500c71a010000000000000000"
    // P3, CF:E7:0B:08:81:8A, id 0xCF45, #3 comment 2873299889: 123.00 kg final.
    private val nolifer = "CF:E7:0B:08:81:8A"
    private val s5Live = "e70b08818a008025226801002030000000000000000000"
    private val s5End25 = "e70b08818a008c25226825800c30000000eb10f1f73500"

    // P2 Pro (T9149), CF:E8:12:18:33:EE, eufylife-ble-client#8; company id 0xFF48 as in #98.
    private val p2Mac = "CF:E8:12:18:33:EE"
    private val p2a = "cfe8121833eecf51c00e29dcd69700000000e4"
    private val p2b = "cfe8121833eecf4cc00e2904032c000000004f"

    @Test
    fun c20_weigh_in() {
        val ad = { hex: String -> mfrAd(0xBC64, hex, woods) }
        assertEquals(listOf(EufyC20P3Driver), Drivers.matching(ad(s1Lock)))
        assertNull(Drivers.parse(c20, ad(s1Idle)))
        assertNull(Drivers.parse(c20, ad(s1Off0d)))
        val live = Drivers.parse(c20, ad(s1Live))!!
        assertEquals(100.10f, live.weightKg, 0.001f)
        assertEquals(false, live.stable)
        for (hex in listOf(s1Lock, s1Status15, s1End25)) {
            val f = Drivers.parse(c20, ad(hex))!!
            assertEquals(100.10f, f.weightKg, 0.001f)
            assertEquals(true, f.stable)
            assertNull(f.impedance)
        }
    }

    @Test
    fun p3_weigh_ins() {
        assertEquals(82.80f, Drivers.parse(c20, mfrAd(0xCF4D, s3Lock, duke))!!.weightKg, 0.001f)
        assertEquals(82.80f, Drivers.parse(c20, mfrAd(0xCF4D, s3A5End, duke))!!.weightKg, 0.001f)
        // A clock that was never set doesn't matter: Scale Sync dates by receipt.
        val b = Drivers.parse(c20, mfrAd(0xCF5D, s4Lock, branda))!!
        assertEquals(68.55f, b.weightKg, 0.001f)
        assertNull(b.scaleTime)
        assertEquals(false, Drivers.parse(c20, mfrAd(0xCF45, s5Live, nolifer))!!.stable)
        assertEquals(123.00f, Drivers.parse(c20, mfrAd(0xCF45, s5End25, nolifer))!!.weightKg, 0.001f)
    }

    @Test
    fun c20_p3_match_by_address_echo_or_name() {
        assertFalse(c20.matches(mfrAd(0xBC64, s1Lock, duke)))
        assertFalse(c20.matches(mfrAd(0xBD64, s1Lock, woods)))
        assertTrue(c20.matches(mfrAd(0xBC64, s1Lock, "11:22:33:44:55:66", name = "eufy T9130")))
        // The name arrives in the scan response, sometimes before any data.
        assertTrue(c20.matches(Advertisement("11:22:33:44:55:66", "eufy T9150")))
    }

    @Test
    fun p2_broadcast_weight() {
        val a = mfrAd(0xFF48, p2a, p2Mac)
        assertEquals(listOf(EufyP2Driver), Drivers.matching(a))
        val f = Drivers.parse(p2, a)!!
        assertEquals(105.10f, f.weightKg, 0.001f)
        assertEquals(true, f.stable)
        assertEquals(105.10f, Drivers.parse(p2, mfrAd(0xFF48, p2b, p2Mac))!!.weightKg, 0.001f)
        assertTrue(p2.matches(Advertisement("11:22:33:44:55:66", "eufy T9149")))
        // Not final: [15] != 0 (single byte changed).
        assertEquals(false, Drivers.parse(p2, mfrAd(0xFF48, withByte(p2a, 15, 0x01), p2Mac))!!.stable)
    }

    @Test
    fun p2_and_c20_p3_do_not_claim_each_other() {
        assertFalse(c20.matches(mfrAd(0xFF48, p2a, p2Mac)))
        assertFalse(p2.matches(mfrAd(0xBC64, s1Lock, woods)))
        // P2 frame heard from a different address is not this scale.
        assertFalse(p2.matches(mfrAd(0xFF48, p2a, woods)))
    }
}

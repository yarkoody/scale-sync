package io.github.yarkoody.scalesync.core.drivers

import io.github.yarkoody.scalesync.core.Advertisement
import io.github.yarkoody.scalesync.core.Drivers
import io.github.yarkoody.scalesync.core.hexToBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** QN broadcast, Etekcity Fit 8S, PICOOC Mini Lite and AAA-series, from real captures. */
class BroadcastDriversTest {

    // ---- QN broadcast: FITINDEX FT-26R-W, openScale QNHandlerBroadcastFT26RTest (captured
    // 2026-09-07; display read 155.0 lb = 70.31 kg; address bytes replaced with 00:00:5E:00:53:01).
    private fun qn(hex: String) = mfrAd(0xFFFF, hex, "00:00:5E:00:53:01")
    private val qnMoving1 = "aabb00005e0053016d000000ffffff1400671b510e032100"
    private val qnSettled1 = "aabb00005e00530172000000ffffff1500761b510e032300"
    private val qnSettled3 = "aabb00005e005301f0000000ffffff15007b1b510e034400"
    private val qnIdle = "aabb00005e00530100f09f6affffff15a00000510e03500f"

    @Test
    fun qn_ft26r_weigh_in() {
        assertEquals(listOf(QnBroadcastDriver), Drivers.matching(qn(qnSettled1)))
        val moving = Drivers.parse(QnBroadcastDriver, qn(qnMoving1))!!
        assertEquals(70.15f, moving.weightKg, 0.001f)
        assertEquals(false, moving.stable)
        val settled = Drivers.parse(QnBroadcastDriver, qn(qnSettled1))!!
        assertEquals(70.30f, settled.weightKg, 0.001f)
        assertEquals(true, settled.stable)
        assertEquals(70.35f, Drivers.parse(QnBroadcastDriver, qn(qnSettled3))!!.weightKg, 0.001f)
        assertNull("idle, 0 kg", Drivers.parse(QnBroadcastDriver, qn(qnIdle)))
    }

    @Test
    fun qn_bit0_counts_only_with_the_ft26r_signature() {
        // Same settled frame with the signature bytes changed (derived): bit 0 no longer counts.
        val noSig = withByte(qnSettled1, 19, 0x00)
        assertEquals(false, Drivers.parse(QnBroadcastDriver, qn(noSig))!!.stable)
        assertFalse(QnBroadcastDriver.matches(mfrAd(0xFFFF, "08140100030100000000026108020015")))
    }

    // ---- Etekcity Fit 8S: openScale#1472 (basjam) pcapng captures, A8:89:57:8C:A1:CE, and
    // ronnnnnnnnnnnnn/etekcity_esf551_ble (MIT) test frames of A9:89:5D:ED:A0:63, both as
    // collected in BLE Scale Sync's tests/scales/etekcity-fit8s.test.ts.
    private val basjam = "A8:89:57:8C:A1:CE"
    private fun fit8s(hex: String, mac: String = basjam, services: List<Int> = listOf(0xFFD0)) = Advertisement(
        address = mac,
        manufacturerData = mapOf(0x06D0 to hex.hexToBytes()),
        serviceUuids = services.map { Advertisement.uuid16(it) },
    )

    @Test
    fun fit8s_weigh_ins() {
        val idle = fit8s("01cea18c5789a8c0a80100000000000000020300")
        assertEquals(listOf(EtekcityFit8SDriver), Drivers.matching(idle))
        assertNull(Drivers.parse(EtekcityFit8SDriver, idle))
        val settling = Drivers.parse(EtekcityFit8SDriver, fit8s("01cea18c5789a8c0a801facd0000000000020300"))!!
        assertEquals(52.73f, settling.weightKg, 0.001f)
        assertEquals(false, settling.stable)
        val s1 = Drivers.parse(EtekcityFit8SDriver, fit8s("01cea18c5789a8c0a8014e1a010e020100020300"))!!
        assertEquals(72.27f, s1.weightKg, 0.001f)
        assertEquals(true, s1.stable)
        assertNull("not impedance until checked", s1.impedance)
        assertEquals(72.55f, Drivers.parse(EtekcityFit8SDriver, fit8s("01cea18c5789a8c0a801661b010c020100020300"))!!.weightKg, 0.001f)
        val flautz = fit8s("0163a0ed5d89a9c0a9017c3c0119020100020300", "A9:89:5D:ED:A0:63")
        assertEquals(81.02f, Drivers.parse(EtekcityFit8SDriver, flautz)!!.weightKg, 0.001f)
    }

    @Test
    fun fit8s_does_not_claim_other_vesync_devices() {
        // ESF-551 (BLE Scale Sync #385) and a Levoit purifier (etekcity_esf551_ble tests).
        assertFalse(EtekcityFit8SDriver.matches(fit8s("01bd4043004dd00001", "D0:4D:00:43:40:BD", emptyList())))
        assertFalse(EtekcityFit8SDriver.matches(fit8s("018e31e5519140c623020202", "40:91:51:E5:31:8E", emptyList())))
        // A Fit 8S frame heard from another address, or without its 0xFFD0 service.
        assertFalse(EtekcityFit8SDriver.matches(fit8s("01cea18c5789a8c0a8014e1a010e020100020300", "A9:89:5D:ED:A0:63")))
        assertFalse(EtekcityFit8SDriver.matches(fit8s("01cea18c5789a8c0a8014e1a010e020100020300", services = listOf(0x181D))))
    }

    // ---- PICOOC Mini Lite: nRF Connect log attached to openScale#1512 (D0:49:00:4B:4F:1E,
    // company id 0x49D0) and openScale PicoocBroadcastHandlerTest packetA, as collected in
    // BLE Scale Sync's tests/scales/picooc-mini-lite.test.ts.
    private fun picooc(hex: String, name: String? = "PICOOC-L") = mfrAd(0x49D0, hex, "D0:49:00:4B:4F:1E", name)

    @Test
    fun picooc_results() {
        assertEquals(listOf(PicoocMiniLiteDriver), Drivers.matching(picooc("004b4f1e391f066813b08710000e")))
        for ((hex, kg) in listOf(
            "004b4f1e391f066813b08710000e" to 82.00f,
            "004b4f1e391f06be13ba876e0050" to 86.30f,
            "004b4f1e391f065e13388704009c" to 81.50f,
        )) {
            val f = Drivers.parse(PicoocMiniLiteDriver, picooc(hex))!!
            assertEquals(kg, f.weightKg, 0.001f)
            assertEquals(true, f.stable)
            assertNull(f.impedance)
        }
    }

    @Test
    fun picooc_refuses_bad_checksum_and_disagreeing_weights() {
        assertNull(Drivers.parse(PicoocMiniLiteDriver, picooc(withByte("004b4f1e391f066813b08710000e", 13, 0x0f))))
        // Weight field doubled, checksum fixed up (derived): the lb field no longer agrees.
        val doubled = withByte(withByte("004b4f1e391f066813b08710000e", 6, 0x0c), 13, 0x08)
        assertNull(Drivers.parse(PicoocMiniLiteDriver, picooc(doubled)))
        assertFalse(PicoocMiniLiteDriver.matches(picooc("004b4f1e391f066813b08710000e", name = "PICOOC")))
    }

    // ---- AAA-series: two iOS PacketLogger captures attached to BLE Scale Sync #297
    // (A0:85:61:91:E9:4F, displayed 108.5 kg and 5.6 kg) and a second unit, AAA006
    // A0:91:54:A4:C9:0C (displayed 81.0 kg; the frame holds 81 010 g), from tests/scales/silvergear-108.test.ts.
    private val aaaMac = "A0:85:61:91:E9:4F"
    private fun aaa(payload: String, mac: String = aaaMac, macReversed: String = "4fe9916185a0") =
        mfrAd(0xA0AC, macReversed + payload, mac)

    @Test
    fun aaa_weigh_ins() {
        assertEquals(listOf(AaaSeriesDriver), Drivers.matching(aaa("202d07600da1")))
        val settled = Drivers.parse(AaaSeriesDriver, aaa("202d07600da1"))!!
        assertEquals(108.48f, settled.weightKg, 0.001f)
        assertEquals(true, settled.stable)
        val settling = Drivers.parse(AaaSeriesDriver, aaa("a02d07600da1"))!!
        assertEquals(108.48f, settling.weightKg, 0.001f)
        assertEquals(false, settling.stable)
        assertEquals(39.60f, Drivers.parse(AaaSeriesDriver, aaa("a02c3a100da3"))!!.weightKg, 0.001f)
        assertEquals(5.61f, Drivers.parse(AaaSeriesDriver, aaa("202cb54a0db8"))!!.weightKg, 0.001f)
        val unit2 = aaa("20c99cd20da4", "A0:91:54:A4:C9:0C", "0cc9a45491a0")
        assertEquals(81.01f, Drivers.parse(AaaSeriesDriver, unit2)!!.weightKg, 0.001f)
    }

    @Test
    fun aaa_idle_body_frames_and_refusals() {
        assertNull("idle, 0 kg", Drivers.parse(AaaSeriesDriver, aaa("a02ca0a00db9")))
        assertTrue("body frame is still this scale", AaaSeriesDriver.matches(aaa("a2b1a0a206bb")))
        assertNull(Drivers.parse(AaaSeriesDriver, aaa("a2b1a0a206bb")))
        assertFalse("bad checksum", AaaSeriesDriver.matches(aaa(withByte("202d07600da1", 3, 0x61))))
        assertFalse("other address", AaaSeriesDriver.matches(aaa("202d07600da1", "A0:91:54:A4:C9:0C")))
        assertTrue("known name", AaaSeriesDriver.matches(Advertisement("11:22:33:44:55:66", "AAA07A")))
    }

    @Test
    fun no_real_capture_is_claimed_by_two_drivers() {
        val all = listOf(
            qn(qnSettled1), fit8s("01cea18c5789a8c0a8014e1a010e020100020300"),
            picooc("004b4f1e391f066813b08710000e"), aaa("202d07600da1"),
            mfrAd(0x10C0, "396c17700a012508b8d0deb53a", "08:B8:D0:DE:B5:3A"),
            mfrAd(0xBC64, "0fb7009c9d00c5c9706705001a2700000000", "BC:0F:B7:00:9C:9D"),
            mfrAd(0xFF48, "cfe8121833eecf51c00e29dcd69700000000e4", "CF:E8:12:18:33:EE"),
        )
        all.forEach { assertEquals(Capture(it), 1, Drivers.matching(it).size) }
    }

    private fun Capture(ad: Advertisement) = io.github.yarkoody.scalesync.core.Capture.line("t", ad)
}

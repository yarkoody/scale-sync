package io.github.yarkoody.scalesync.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class MiScaleFrameTest {

    private fun frame(ctrl1: Int, weightKg: Double, impedance: Int, t: LocalDateTime = LocalDateTime.of(2026, 10, 14, 7, 30, 5)): ByteArray {
        val raw = Math.round(weightKg * 200).toInt()
        return byteArrayOf(
            0x02, ctrl1.toByte(),
            (t.year and 0xFF).toByte(), (t.year shr 8).toByte(),
            t.monthValue.toByte(), t.dayOfMonth.toByte(), t.hour.toByte(), t.minute.toByte(), t.second.toByte(),
            (impedance and 0xFF).toByte(), (impedance shr 8).toByte(),
            (raw and 0xFF).toByte(), (raw shr 8).toByte(),
        )
    }

    @Test
    fun parses_stable_frame_with_impedance() {
        val f = MiScaleFrame.parse(frame(0x26, 72.35, 480))!!
        assertEquals(72.35f, f.weightKg, 0.001f)
        assertEquals(480, f.impedance)
        assertTrue(f.stable)
        assertFalse(f.removed)
        assertEquals(LocalDateTime.of(2026, 10, 14, 7, 30, 5), f.scaleTime)
    }

    @Test
    fun impedance_ignored_without_flag() {
        val f = MiScaleFrame.parse(frame(0x24, 72.35, 480))!!
        assertNull(f.impedance)
        assertTrue(f.stable)
    }

    @Test
    fun unstable_and_removed_flags() {
        assertFalse(MiScaleFrame.parse(frame(0x04, 50.0, 0))!!.stable)
        assertTrue(MiScaleFrame.parse(frame(0xA4, 50.0, 0))!!.removed)
    }

    @Test
    fun rejects_wrong_length() {
        assertNull(MiScaleFrame.parse(ByteArray(10)))
        assertNull(MiScaleFrame.parse(null))
        assertNotNull(MiScaleFrame.parse(frame(0x24, 60.0, 0)))
    }
}

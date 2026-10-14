package io.github.yarkoody.scalesync.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** Reference values from openScale's MiScaleLibTest and BodyMiScaleLibTest. */
class BodyCompositionTest {

    @Test
    fun xiaomi_matches_openscale_regression() {
        val x = XiaomiLib(Sex.MALE, 30, 180f)
        assertEquals(23.315107f, x.bodyFat(80f, 500f), 1e-3f)
        assertEquals(3.1254203f, x.boneMass(80f, 500f), 1e-3f)
        assertEquals(58.222496f, x.lbm(80f, 500f), 1e-3f)
        assertEquals(40.977253f, x.muscle(80f, 500f), 1e-3f)
        assertEquals(52.605835f, x.water(80f, 500f), 1e-3f)
        assertEquals(13.359997f, x.visceralFat(80f), 1e-3f)
    }

    @Test
    fun science_matches_bodymiscale_reference() {
        val s = ScienceLib(Sex.MALE, 46, 168f)
        val w = 86.10f
        val lbm = s.lbm(w, 421f)
        val fat = s.fat(w, lbm)
        val bone = s.boneMass(lbm)
        assertEquals(60.0f, lbm, 0.05f)
        assertEquals(30.33f, fat, 0.05f)
        assertEquals(50.86f, s.water(fat), 0.05f)
        assertEquals(13.59f, s.protein(w, lbm), 0.05f)
        assertEquals(3.01f, bone, 0.05f)
        assertEquals(56.97f, s.muscleMass(w, fat, bone), 0.05f)
        assertEquals(1861f, s.bmr(w), 1f)
        assertEquals(15.36f, s.visceralFat(w), 0.05f)
    }
}

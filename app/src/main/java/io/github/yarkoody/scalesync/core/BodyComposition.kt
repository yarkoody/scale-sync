/*
 * Scale Sync
 * Formulas ported from openScale (Copyright (C) 2025 olie.xdev, GPLv3):
 *   MiScaleLib.kt     - reverse-engineered Mi Fit / Zepp Life algorithm
 *                       (based on prototux/MIBCS-reverse-engineering)
 *   BodyMiScaleLib.kt - bodymiscale (dckiller51, GPLv3) scientific estimator
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.yarkoody.scalesync.core

import java.time.LocalDate
import java.time.Period

enum class Sex { MALE, FEMALE }

enum class Algorithm {
    /** Same numbers as Zepp Life / Mi Fit (openScale's default). */
    XIAOMI,
    /** bodymiscale's peer-reviewed formulas. */
    SCIENCE,
}

data class Profile(
    val heightCm: Float,
    val birthDate: LocalDate,
    val sex: Sex,
    val algorithm: Algorithm = Algorithm.XIAOMI,
) {
    fun ageOn(date: LocalDate): Int = Period.between(birthDate, date).years.coerceAtLeast(1)
}

data class BodyMetrics(
    val weightKg: Float,
    val impedance: Int?,
    val bmi: Float?,
    val fatPercent: Float? = null,
    val waterPercent: Float? = null,
    val musclePercent: Float? = null,
    val boneKg: Float? = null,
    val leanKg: Float? = null,
    val visceralFat: Float? = null,
    val proteinPercent: Float? = null,
    val bmrKcal: Float? = null,
) {
    val fatKg get() = fatPercent?.let { weightKg * it / 100f }
    val waterKg get() = waterPercent?.let { weightKg * it / 100f }
    val muscleKg get() = musclePercent?.let { weightKg * it / 100f }
    val hasComposition get() = fatPercent != null
}

object BodyComposition {

    fun compute(weightKg: Float, impedance: Int?, profile: Profile?, on: LocalDate): BodyMetrics {
        if (profile == null) return BodyMetrics(weightKg, impedance, bmi = null)
        val age = profile.ageOn(on)
        val h = profile.heightCm
        val bmi = weightKg / ((h / 100f) * (h / 100f))
        val science = ScienceLib(profile.sex, age, h)
        val bmr = science.bmr(weightKg)
        if (impedance == null) return BodyMetrics(weightKg, null, bmi, bmrKcal = bmr)

        val imp = impedance.toFloat()
        return when (profile.algorithm) {
            Algorithm.XIAOMI -> {
                val x = XiaomiLib(profile.sex, age, h)
                val lean = x.lbm(weightKg, imp)
                BodyMetrics(
                    weightKg, impedance, bmi,
                    fatPercent = x.bodyFat(weightKg, imp),
                    waterPercent = x.water(weightKg, imp),
                    musclePercent = x.muscle(weightKg, imp),
                    boneKg = x.boneMass(weightKg, imp),
                    leanKg = lean,
                    visceralFat = x.visceralFat(weightKg),
                    proteinPercent = (lean * 0.195f / weightKg * 100f).coerceIn(5f, 32f),
                    bmrKcal = bmr,
                )
            }
            Algorithm.SCIENCE -> {
                val lean = science.lbm(weightKg, imp)
                val fat = science.fat(weightKg, lean)
                val bone = science.boneMass(lean)
                val muscleKg = science.muscleMass(weightKg, fat, bone)
                BodyMetrics(
                    weightKg, impedance, bmi,
                    fatPercent = fat,
                    waterPercent = science.water(fat),
                    musclePercent = muscleKg / weightKg * 100f,
                    boneKg = bone,
                    leanKg = lean,
                    visceralFat = science.visceralFat(weightKg),
                    proteinPercent = science.protein(weightKg, lean),
                    bmrKcal = bmr,
                )
            }
        }
    }
}

/** Port of openScale MiScaleLib. */
internal class XiaomiLib(sex: Sex, private val age: Int, private val height: Float) {
    private val sex = if (sex == Sex.MALE) 1 else 0

    private fun lbmCoefficient(weight: Float, impedance: Float): Float {
        var lbm = (height * 9.058f / 100.0f) * (height / 100.0f)
        lbm += weight * 0.32f + 12.226f
        lbm -= impedance * 0.0068f
        lbm -= age * 0.0542f
        return lbm
    }

    fun lbm(weight: Float, impedance: Float): Float {
        var lean = weight - ((bodyFat(weight, impedance) * 0.01f) * weight) - boneMass(weight, impedance)
        if (sex == 0 && lean >= 84.0f) lean = 120.0f
        else if (sex == 1 && lean >= 93.5f) lean = 120.0f
        return lean
    }

    fun muscle(weight: Float, impedance: Float): Float {
        if (weight <= 0f) return 0f
        // Janssen et al.: SMM(kg) = 0.401*(H^2/R) + 3.825*sex - 0.071*age + 5.102
        val smmKg = 0.401f * (height * height) / impedance + 3.825f * sex - 0.071f * age + 5.102f
        return (smmKg / weight * 100f).coerceIn(10f, 60f)
    }

    fun water(weight: Float, impedance: Float): Float {
        val water = (100.0f - bodyFat(weight, impedance)) * 0.7f
        return (if (water < 50f) 1.02f else 0.98f) * water
    }

    fun boneMass(weight: Float, impedance: Float): Float {
        val base = if (sex == 0) 0.245691014f else 0.18016894f
        var bone = (base - (lbmCoefficient(weight, impedance) * 0.05158f)) * -1.0f
        bone = if (bone > 2.2f) bone + 0.1f else bone - 0.1f
        if (sex == 0 && bone > 5.1f) bone = 8.0f
        else if (sex == 1 && bone > 5.2f) bone = 8.0f
        return bone
    }

    fun visceralFat(weight: Float): Float {
        return if (sex == 0) {
            if (weight > (13.0f - (height * 0.5f)) * -1.0f) {
                val subsubcalc = ((height * 1.45f) + (height * 0.1158f) * height) - 120.0f
                val subcalc = weight * 500.0f / subsubcalc
                (subcalc - 6.0f) + (age * 0.07f)
            } else {
                val subcalc = 0.691f + (height * -0.0024f) + (height * -0.0024f)
                (((height * 0.027f) - (subcalc * weight)) * -1.0f) + (age * 0.07f) - age
            }
        } else {
            if (height < weight * 1.6f) {
                val subcalc = ((height * 0.4f) - (height * (height * 0.0826f))) * -1.0f
                ((weight * 305.0f) / (subcalc + 48.0f)) - 2.9f + (age * 0.15f)
            } else {
                val subcalc = 0.765f + height * -0.0015f
                (((height * 0.143f) - (weight * subcalc)) * -1.0f) + (age * 0.15f) - 5.0f
            }
        }
    }

    fun bodyFat(weight: Float, impedance: Float): Float {
        var lbmSub = 0.8f
        if (sex == 0 && age <= 49) lbmSub = 9.25f
        else if (sex == 0 && age > 49) lbmSub = 7.25f

        val lbmCoeff = lbmCoefficient(weight, impedance)
        var coeff = 1.0f
        if (sex == 1 && weight < 61.0f) {
            coeff = 0.98f
        } else if (sex == 0 && weight > 60.0f) {
            coeff = 0.96f
            if (height > 160.0f) coeff *= 1.03f
        } else if (sex == 0 && weight < 50.0f) {
            coeff = 1.02f
            if (height > 160.0f) coeff *= 1.03f
        }
        var bodyFat = (1.0f - (((lbmCoeff - lbmSub) * coeff) / weight)) * 100.0f
        if (bodyFat > 63.0f) bodyFat = 75.0f
        return bodyFat
    }
}

/** Port of openScale BodyMiScaleLib (bodymiscale). */
internal class ScienceLib(sex: Sex, private val age: Int, private val heightCm: Float) {
    private val isMale = sex == Sex.MALE

    fun lbm(weightKg: Float, impedance: Float): Float {
        var lbm = (heightCm * 9.058f / 100f) * (heightCm / 100f) +
            weightKg * 0.32f + 12.226f - impedance * 0.0068f - age * 0.0542f
        if (!isMale) lbm *= 0.84f
        return minOf(lbm, weightKg * 0.98f)
    }

    fun fat(weightKg: Float, lbm: Float): Float =
        ((weightKg - lbm) / weightKg * 100f).coerceIn(if (isMale) 5f else 10f, 75f)

    fun water(fatPercent: Float): Float = ((100f - fatPercent) * 0.73f).coerceIn(35f, 73f)

    fun protein(weightKg: Float, lbm: Float): Float = (lbm * 0.195f / weightKg * 100f).coerceIn(5f, 32f)

    fun boneMass(lbm: Float): Float {
        val base = if (isMale) 0.18016894f else 0.245691014f
        var bone = (base - lbm * 0.05158f) * -1f
        bone = if (bone > 2.2f) bone + 0.1f else bone - 0.1f
        if ((isMale && bone > 5.2f) || (!isMale && bone > 5.1f)) bone = 8.0f
        return bone.coerceIn(0.5f, 8f)
    }

    fun muscleMass(weightKg: Float, fatPercent: Float, boneKg: Float): Float =
        (weightKg - (fatPercent * 0.01f * weightKg) - boneKg).coerceIn(10f, 120f)

    /** Schofield (WHO) BMR in kcal/day. */
    fun bmr(weightKg: Float): Float {
        val coeffs = if (isMale) MALE else FEMALE
        val (slope, constant) = when {
            age < 3 -> coeffs[0]
            age < 10 -> coeffs[1]
            age < 18 -> coeffs[2]
            age < 30 -> coeffs[3]
            age < 60 -> coeffs[4]
            else -> coeffs[5]
        }
        return (slope * weightKg + constant).coerceIn(500f, 5000f)
    }

    fun visceralFat(weightKg: Float): Float {
        val h = heightCm
        val w = weightKg
        val v = if (isMale) {
            if (h < w * 1.6f + 63.0f) age * 0.15f + ((w * 305.0f) / ((h * 0.0826f * h - h * 0.4f) + 48.0f) - 2.9f)
            else age * 0.15f + (w * (h * -0.0015f + 0.765f) - h * 0.143f) - 5.0f
        } else {
            if (w <= h * 0.5f - 13.0f) age * 0.07f + (w * (h * -0.0024f + 0.691f) - h * 0.027f) - 10.5f
            else age * 0.07f + ((w * 500.0f) / ((h * 1.45f + h * 0.1158f * h) - 120.0f) - 6.0f)
        }
        return v.coerceIn(1f, 50f)
    }

    private companion object {
        val MALE = arrayOf(
            59.512f to -30.4f, 22.706f to 504.3f, 17.686f to 658.2f,
            15.057f to 692.2f, 11.472f to 873.1f, 11.711f to 587.7f,
        )
        val FEMALE = arrayOf(
            58.317f to -31.1f, 20.315f to 485.9f, 13.384f to 692.6f,
            14.818f to 486.6f, 8.126f to 845.6f, 9.082f to 658.5f,
        )
    }
}

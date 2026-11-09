package io.github.yarkoody.scalesync.core

import java.time.LocalDateTime

/** The unit the scale displays. Frames always carry kilograms; this is informational. */
enum class WeightUnit { KG, LB, JIN, ST }

/**
 * One reading decoded from an advertisement. Every driver returns this; everything after the
 * driver ([WeighInDetector], [BodyComposition], Health Connect) only sees this.
 */
interface ScaleFrame {
    val weightKg: Float
    /** Ohms, or null when the frame carries no impedance (still measuring, socks, or no electrodes). */
    val impedance: Int?
    /** The scale's own "measurement is final" flag, or null if it doesn't send one. */
    val stable: Boolean?
    /** The scale says the person stepped off. */
    val removed: Boolean
    /** The scale's own clock, local time, or null if it has none or it is unset. */
    val scaleTime: LocalDateTime?
    val unit: WeightUnit
}

data class Frame(
    override val weightKg: Float,
    override val impedance: Int? = null,
    override val stable: Boolean? = null,
    override val removed: Boolean = false,
    override val scaleTime: LocalDateTime? = null,
    override val unit: WeightUnit = WeightUnit.KG,
) : ScaleFrame

enum class SupportLevel {
    /** Tested on real hardware by the maintainer. */
    VERIFIED,
    /** Written from other projects' protocol notes and captures; not tested on the real scale. */
    EXPERIMENTAL,
}

/** What a scale's advertisement can carry. */
enum class Field { WEIGHT, IMPEDANCE, STABLE_FLAG, SCALE_CLOCK }

/**
 * The vendor's own formula for turning impedance into body composition, when it is known.
 * Scales that send impedance without a known formula get bodymiscale's formulas.
 */
enum class VendorFormula {
    /** Zepp Life / Mi Fit (or bodymiscale, the user's choice in the profile). */
    XIAOMI,
}

data class Capabilities(
    val fields: Set<Field>,
    val support: SupportLevel,
    val vendorFormula: VendorFormula? = null,
) {
    val hasImpedance get() = Field.IMPEDANCE in fields
}

/**
 * Decodes one family of scales from its BLE advertisements. A driver never connects to the
 * scale: everything it needs is in the broadcast.
 */
interface ScaleDriver {
    /** Stable id, saved in settings and in the history CSV. Never change it once released. */
    val id: String
    /** Shown to the user, e.g. "Xiaomi Mi Body Composition Scale 2". */
    val displayName: String
    /** Written to Health Connect as the recording device. */
    val manufacturer: String
    val model: String
    val capabilities: Capabilities

    /** True if [ad] comes from a scale this driver understands (it may not carry a reading). */
    fun matches(ad: Advertisement): Boolean

    /** The reading in [ad], or null if it carries none. Only called when [matches] is true. */
    fun parse(ad: Advertisement): ScaleFrame?
}

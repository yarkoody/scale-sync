package io.github.yarkoody.scalesync.medical

import java.time.LocalDate

enum class Flag { LOW, HIGH, NORMAL, UNKNOWN }

data class LabResult(
    val date: LocalDate,
    val test: String,
    val group: String = "",
    val value: Double? = null,
    /** The value as written when it isn't a number, such as "Negative" or "<5". */
    val valueText: String = "",
    val unit: String = "",
    val low: Double? = null,
    val high: Double? = null,
    val note: String = "",
) {
    val flag: Flag
        get() = when {
            value == null || (low == null && high == null) -> Flag.UNKNOWN
            low != null && value < low -> Flag.LOW
            high != null && value > high -> Flag.HIGH
            else -> Flag.NORMAL
        }
    val shown: String get() = value?.let { fmt(it) } ?: valueText
    val range: String
        get() = when {
            low != null && high != null -> "${fmt(low)}–${fmt(high)}"
            high != null -> "under ${fmt(high)}"
            low != null -> "over ${fmt(low)}"
            else -> ""
        }
}

data class Medication(
    val name: String,
    val generic: String = "",
    val use: String = "",
    val dose: String = "",
    val kind: String = "",
    val lastPrescribed: LocalDate? = null,
    val daysSupply: Int? = null,
    val timesPrescribed: Int? = null,
) {
    val ongoing: Boolean get() = kind.contains("ongoing", ignoreCase = true)
    /** When the last prescription runs out if taken as prescribed. */
    val runsOut: LocalDate? get() = lastPrescribed?.let { d -> daysSupply?.let { d.plusDays(it.toLong()) } }
}

data class CareItem(
    val item: String,
    val lastDone: LocalDate? = null,
    val intervalMonths: Int? = null,
    val why: String = "",
    val how: String = "",
) {
    val nextDue: LocalDate? get() = lastDone?.let { d -> intervalMonths?.let { d.plusMonths(it.toLong()) } }
}

data class OpenItem(val item: String, val date: LocalDate? = null, val detail: String = "")

data class HistoryEvent(
    val date: LocalDate,
    val kind: String = "",
    val item: String,
    val detail: String = "",
    val approx: Boolean = false,
)

data class VitalSign(
    val date: LocalDate,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val pulse: Int? = null,
    val weightKg: Double? = null,
)

/** Something with a date that needs doing: a check-up, a renewal, or an unfinished referral. */
data class DueItem(val title: String, val due: LocalDate?, val detail: String, val kind: String)

/**
 * Everything taken from the HMO's PDFs. Israeli HMOs have no patient API, so this comes in as a
 * JSON file Claude extracts from the documents, and is merged into what the app already has.
 */
data class MedicalRecord(
    val labs: List<LabResult> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val care: List<CareItem> = emptyList(),
    val openItems: List<OpenItem> = emptyList(),
    val history: List<HistoryEvent> = emptyList(),
    val vitals: List<VitalSign> = emptyList(),
) {
    val isEmpty: Boolean
        get() = labs.isEmpty() && medications.isEmpty() && care.isEmpty() &&
            openItems.isEmpty() && history.isEmpty() && vitals.isEmpty()

    /** Newest result per test, with the one before it, ordered by group then test. */
    fun latestLabs(): List<Pair<LabResult, LabResult?>> =
        labs.groupBy { it.test.lowercase() }.values
            .map { rs -> rs.sortedByDescending { it.date }.let { it[0] to it.getOrNull(1) } }
            .sortedWith(compareBy({ it.first.group }, { it.first.test }))

    fun labHistory(test: String): List<LabResult> =
        labs.filter { it.test.equals(test, ignoreCase = true) }.sortedBy { it.date }

    fun labDates(): List<LocalDate> = labs.map { it.date }.distinct().sortedDescending()

    /**
     * Check-ups, renewals and open referrals, soonest (or most overdue) first. Renewals that ran out
     * more than two months ago are left out: you're evidently not taking that one daily.
     */
    fun dueItems(today: LocalDate = LocalDate.now()): List<DueItem> = buildList {
        care.forEach { add(DueItem(it.item, it.nextDue, it.why, "Check-up")) }
        medications.filter { m -> m.ongoing && m.runsOut?.let { it >= today.minusMonths(2) } == true }.forEach {
            add(DueItem("Renew ${it.name}", it.runsOut, "Runs out then if taken ${it.dose.lowercase()}.", "Prescription"))
        }
        openItems.forEach { add(DueItem(it.item, null, it.detail, "Open")) }
    }.sortedWith(compareBy(nullsLast()) { it.due })

    fun merge(incoming: MedicalRecord): MedicalRecord {
        fun <T, K> m(old: List<T>, new: List<T>, key: (T) -> K): List<T> {
            val map = LinkedHashMap<K, T>()
            old.forEach { map[key(it)] = it }
            new.forEach { map[key(it)] = it }
            return map.values.toList()
        }
        return MedicalRecord(
            labs = m(labs, incoming.labs) { it.date to it.test.lowercase() }.sortedWith(compareBy({ it.date }, { it.test })),
            medications = m(medications, incoming.medications) { it.name.lowercase() },
            care = m(care, incoming.care) { it.item.lowercase() },
            openItems = m(openItems, incoming.openItems) { it.item.lowercase() },
            history = m(history, incoming.history) { it.date to it.item.lowercase() }.sortedByDescending { it.date },
            vitals = m(vitals, incoming.vitals) { it.date }.sortedBy { it.date },
        )
    }

    fun toJson(): String = Json.write(
        linkedMapOf(
            "labs" to labs.map {
                linkedMapOf("date" to it.date.toString(), "test" to it.test, "group" to it.group,
                    "value" to (it.value ?: it.valueText.ifEmpty { null }), "unit" to it.unit,
                    "low" to it.low, "high" to it.high, "note" to it.note)
            },
            "medications" to medications.map {
                linkedMapOf("medication" to it.name, "generic" to it.generic, "use" to it.use, "dose" to it.dose,
                    "kind" to it.kind, "last_prescribed" to it.lastPrescribed?.toString(),
                    "days_supply" to it.daysSupply, "times_prescribed" to it.timesPrescribed)
            },
            "care" to care.map {
                linkedMapOf("item" to it.item, "last_done" to it.lastDone?.toString(),
                    "interval_months" to it.intervalMonths, "why" to it.why, "how" to it.how)
            },
            "open_items" to openItems.map {
                linkedMapOf("item" to it.item, "date" to it.date?.toString(), "detail" to it.detail)
            },
            "history" to history.map {
                linkedMapOf("date" to it.date.toString(), "kind" to it.kind, "item" to it.item,
                    "detail" to it.detail, "approx" to it.approx)
            },
            "vitals" to vitals.map {
                linkedMapOf("date" to it.date.toString(), "systolic" to it.systolic, "diastolic" to it.diastolic,
                    "pulse" to it.pulse, "weight_kg" to it.weightKg)
            },
        ),
        indent = " ",
    )

    companion object {
        /**
         * Reads either the combined file ({"labs": [...], "medications": [...], ...}) or one of the
         * separate lists (lab-results.json, medications.json, ...), recognised by its fields.
         * Accepts text around the JSON, such as a Claude reply with a ```json block.
         */
        fun parse(text: String): MedicalRecord {
            val root = Json.parse(extractJson(text))
            return when (root) {
                is Map<*, *> -> MedicalRecord(
                    labs = list(root, "labs", "lab_results", "lab-results").mapNotNull(::lab),
                    medications = list(root, "medications", "meds", "prescriptions").mapNotNull(::med),
                    care = list(root, "care", "follow_ups").mapNotNull(::care),
                    openItems = list(root, "open_items", "open-items", "open").mapNotNull(::open),
                    history = list(root, "history", "events").mapNotNull(::event),
                    vitals = list(root, "vitals").mapNotNull(::vital),
                )
                is List<*> -> fromList(root)
                else -> throw Json.JsonException("expected an object or a list")
            }
        }

        private fun fromList(items: List<*>): MedicalRecord {
            val first = items.firstOrNull() as? Map<*, *> ?: return MedicalRecord()
            return when {
                "test" in first -> MedicalRecord(labs = items.mapNotNull(::lab))
                "medication" in first -> MedicalRecord(medications = items.mapNotNull(::med))
                "interval_months" in first -> MedicalRecord(care = items.mapNotNull(::care))
                "systolic" in first || "pulse" in first -> MedicalRecord(vitals = items.mapNotNull(::vital))
                "kind" in first -> MedicalRecord(history = items.mapNotNull(::event))
                "item" in first -> MedicalRecord(openItems = items.mapNotNull(::open))
                else -> MedicalRecord()
            }
        }

        internal fun extractJson(text: String): String {
            val t = text.trim()
            if (t.startsWith("{") || t.startsWith("[")) return t
            val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(t)
            if (fence != null) return fence.groupValues[1].trim()
            val start = t.indexOfFirst { it == '{' || it == '[' }
            val end = t.indexOfLast { it == '}' || it == ']' }
            if (start < 0 || end <= start) throw Json.JsonException("no JSON found")
            return t.substring(start, end + 1)
        }

        private fun list(m: Map<*, *>, vararg keys: String): List<*> =
            keys.firstNotNullOfOrNull { m[it] as? List<*> } ?: emptyList<Any?>()

        private fun Map<*, *>.s(k: String): String = (this[k] as? String)?.trim() ?: (this[k] as? Double)?.let { fmt(it) } ?: ""
        private fun Map<*, *>.d(k: String): Double? = when (val v = this[k]) {
            is Double -> v
            is String -> v.trim().replace(",", ".").toDoubleOrNull()
            else -> null
        }
        private fun Map<*, *>.i(k: String): Int? = d(k)?.toInt()
        private fun Map<*, *>.date(k: String): LocalDate? = parseDate(this[k] as? String)

        private fun lab(x: Any?): LabResult? {
            val m = x as? Map<*, *> ?: return null
            val date = m.date("date") ?: return null
            val test = m.s("test").ifEmpty { return null }
            val raw = m["value"]
            return LabResult(date, test, m.s("group"), m.d("value"),
                if (raw is String && raw.toDoubleOrNull() == null) raw else "",
                m.s("unit"), m.d("low"), m.d("high"), m.s("note"))
        }

        private fun med(x: Any?): Medication? {
            val m = x as? Map<*, *> ?: return null
            val name = m.s("medication").ifEmpty { m.s("name") }.ifEmpty { return null }
            return Medication(name, m.s("generic"), m.s("use"), m.s("dose"), m.s("kind"),
                m.date("last_prescribed"), m.i("days_supply"), m.i("times_prescribed"))
        }

        private fun care(x: Any?): CareItem? {
            val m = x as? Map<*, *> ?: return null
            val item = m.s("item").ifEmpty { return null }
            return CareItem(item, m.date("last_done"), m.i("interval_months"), m.s("why"), m.s("how"))
        }

        private fun open(x: Any?): OpenItem? {
            val m = x as? Map<*, *> ?: return null
            val item = m.s("item").ifEmpty { return null }
            return OpenItem(item, m.date("date"), m.s("detail"))
        }

        private fun event(x: Any?): HistoryEvent? {
            val m = x as? Map<*, *> ?: return null
            val date = m.date("date") ?: return null
            val item = m.s("item").ifEmpty { return null }
            return HistoryEvent(date, m.s("kind"), item, m.s("detail"), m["approx"] == true)
        }

        private fun vital(x: Any?): VitalSign? {
            val m = x as? Map<*, *> ?: return null
            val date = m.date("date") ?: return null
            return VitalSign(date, m.i("systolic"), m.i("diastolic"), m.i("pulse"), m.d("weight_kg"))
        }

        /** ISO dates, plus the dd/mm/yyyy that Israeli documents use. */
        fun parseDate(s: String?): LocalDate? {
            val t = s?.trim().orEmpty()
            if (t.isEmpty()) return null
            runCatching { return LocalDate.parse(t.take(10)) }
            val m = Regex("^(\\d{1,2})[/.](\\d{1,2})[/.](\\d{2,4})").find(t) ?: return null
            val (d, mo, y) = m.destructured
            val year = y.toInt().let { if (it < 100) 2000 + it else it }
            return runCatching { LocalDate.of(year, mo.toInt(), d.toInt()) }.getOrNull()
        }
    }
}

internal fun fmt(v: Double): String =
    if (v == Math.rint(v) && kotlin.math.abs(v) < 1e9) v.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", v).trimEnd('0').trimEnd('.')

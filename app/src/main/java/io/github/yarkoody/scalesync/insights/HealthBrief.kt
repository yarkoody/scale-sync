package io.github.yarkoody.scalesync.insights

import io.github.yarkoody.scalesync.medical.Flag
import io.github.yarkoody.scalesync.medical.MedicalRecord
import io.github.yarkoody.scalesync.wellness.Wellness
import io.github.yarkoody.scalesync.wellness.hm
import java.time.LocalDate
import java.util.Locale

/** A focus for the conversation, in the spirit of Wellness Project's specialists. */
enum class Coach(val title: String, val blurb: String, val ask: String) {
    CHECK_IN(
        "Weekly check-in", "Everything at a glance",
        "Give me a short weekly check-in: what stands out in my sleep, recovery and activity compared with my " +
            "own baseline, anything in my medical record that needs attention soon, and the 3 most useful things " +
            "to do this week.",
    ),
    SLEEP(
        "Sleep coach", "Duration, stages, consistency",
        "Act as a sleep coach. Look at my nights: duration, deep and REM sleep, bedtime consistency, and how " +
            "resting heart rate and HRV move after short nights. Tell me what pattern you see and one or two " +
            "changes to try, and how I'd see in this data whether they work.",
    ),
    LABS(
        "Labs and longevity", "Blood tests over the years",
        "Act as a longevity-minded physician reviewing my blood tests over time. Point out results outside the " +
            "range or drifting in one direction, separate what looks like a stable personal baseline from what " +
            "deserves a question to my doctor, and suggest which tests to add at my next routine check.",
    ),
    BODY(
        "Body composition", "Weight, fat, muscle",
        "Act as a body-composition coach. Look at my weight and body-fat trend from my scale together with " +
            "steps and sleep. Tell me what's actually changing (beyond day-to-day water swings) and what to focus on.",
    ),
    VISIT(
        "Doctor visit prep", "Questions to bring",
        "I'm seeing my doctor soon. Using my record, open items and recent data, write a short list of what to " +
            "bring up, most important first, with the specific question to ask for each. Keep it to one screen.",
    ),
}

object HealthBrief {

    fun build(
        coach: Coach,
        question: String,
        wellness: Wellness,
        medical: MedicalRecord,
        today: LocalDate,
        includeWearables: Boolean = true,
        includeMedical: Boolean = true,
    ): String = buildString {
        appendLine(coach.ask)
        if (question.isNotBlank()) {
            appendLine()
            appendLine("My question: ${question.trim()}")
        }
        appendLine()
        appendLine("Below is my own health data, exported from my personal health app on $today. I live in Israel; " +
            "my HMO records were extracted from PDFs. Be specific to these numbers, say when the data is too thin " +
            "to conclude anything, and tell me plainly when something needs a doctor rather than a coach. " +
            "This is for my own understanding, not a diagnosis.")
        if (includeWearables && !wellness.isEmpty) {
            appendLine()
            append(wearables(wellness, today))
        }
        if (includeMedical && !medical.isEmpty) {
            appendLine()
            append(medicalSection(medical, today))
        }
    }

    fun wearables(w: Wellness, today: LocalDate): String = buildString {
        appendLine("## Wearable and scale data, last ${w.days.size} days (Fitbit Air and Xiaomi scale via Health Connect)")
        val sleep = w.sleepTrend(today)
        fun line(name: String, t: io.github.yarkoody.scalesync.wellness.Trend, f: (Double) -> String) {
            if (t.latest == null) return
            append("- $name: latest ${f(t.latest)}")
            t.avg7?.let { append(", 7-day avg ${f(it)}") }
            t.avg30?.let { append(", 30-day avg ${f(it)}") }
            appendLine()
        }
        line("Sleep", sleep) { hm(it.toLong()) }
        line("Resting heart rate", w.trend(today) { it.restingHr }) { "${n(it, 0)} bpm" }
        line("HRV (RMSSD)", w.trend(today) { it.hrvMs }) { "${n(it, 0)} ms" }
        line("Steps", w.trend(today) { it.steps?.toDouble() }) { n(it, 0) }
        line("SpO2", w.trend(today) { it.spo2 }) { "${n(it, 1)}%" }
        line("Breathing rate", w.trend(today) { it.breathingRate }) { "${n(it, 1)}/min" }
        line("Weight", w.trend(today) { it.weightKg }) { "${n(it, 1)} kg" }
        line("Body fat", w.trend(today) { it.bodyFatPct }) { "${n(it, 1)}%" }
        appendLine()
        appendLine("Daily detail (newest first; blank = not recorded):")
        appendLine("date | asleep | deep | REM | awake | bed-wake | RHR | HRV | steps | SpO2 | weight")
        val nights = w.nights.associateBy { it.date }
        val zone = java.time.ZoneId.systemDefault()
        w.days.sortedByDescending { it.date }.forEach { d ->
            val nt = nights[d.date]
            if (nt == null && d.steps == null && d.restingHr == null && d.hrvMs == null && d.weightKg == null) return@forEach
            val clock = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            appendLine(listOf(
                d.date.toString(),
                nt?.let { hm(it.asleepMin) }.orEmpty(),
                nt?.takeIf { it.hasStages }?.let { hm(it.deepMin) }.orEmpty(),
                nt?.takeIf { it.hasStages }?.let { hm(it.remMin) }.orEmpty(),
                nt?.takeIf { it.hasStages }?.let { "${it.awakeMin}m" }.orEmpty(),
                nt?.let { "${it.start.atZone(zone).format(clock)}-${it.end.atZone(zone).format(clock)}" }.orEmpty(),
                d.restingHr?.let { n(it, 0) }.orEmpty(),
                d.hrvMs?.let { n(it, 0) }.orEmpty(),
                d.steps?.toString().orEmpty(),
                d.spo2?.let { n(it, 1) }.orEmpty(),
                d.weightKg?.let { n(it, 1) }.orEmpty(),
            ).joinToString(" | "))
        }
    }

    fun medicalSection(m: MedicalRecord, today: LocalDate): String = buildString {
        appendLine("## Medical record (from my HMO's documents)")
        if (m.history.isNotEmpty()) {
            appendLine("Recent history (newest first):")
            m.history.sortedByDescending { it.date }.take(15).forEach {
                appendLine("- ${it.date}${if (it.approx) " (approx.)" else ""} ${it.kind}: ${it.item}. ${it.detail}".trimEnd())
            }
        }
        if (m.medications.isNotEmpty()) {
            appendLine("Medications:")
            m.medications.forEach {
                appendLine("- ${it.name}${if (it.generic.isNotEmpty()) " (${it.generic})" else ""}: ${it.use}; ${it.dose}; " +
                    "${it.kind}; last prescribed ${it.lastPrescribed ?: "?"}")
            }
        }
        val due = m.dueItems(today)
        if (due.isNotEmpty()) {
            appendLine("Follow-ups and open items:")
            due.forEach {
                val whenText = it.due?.let { d -> if (d.isBefore(today)) "overdue since $d" else "due $d" } ?: "no result on record"
                appendLine("- ${it.title} ($whenText). ${it.detail}".trimEnd())
            }
        }
        if (m.labs.isNotEmpty()) {
            appendLine("Blood tests: latest value per test, with earlier values (date: value). Flag shows outside the lab's range.")
            m.latestLabs().forEach { (latest, _) ->
                val hist = m.labHistory(latest.test)
                val flag = when (latest.flag) { Flag.HIGH -> " HIGH"; Flag.LOW -> " LOW"; else -> "" }
                val range = latest.range.takeIf { it.isNotEmpty() }?.let { " [range $it]" }.orEmpty()
                val earlier = hist.dropLast(1).takeLast(5).joinToString(", ") { "${it.date}: ${it.shown}" }
                appendLine("- ${latest.test} ${latest.shown} ${latest.unit}$range$flag (${latest.date})" +
                    if (earlier.isNotEmpty()) "; earlier $earlier" else "")
            }
        }
        if (m.vitals.isNotEmpty()) {
            appendLine("Clinic vitals:")
            m.vitals.sortedByDescending { it.date }.take(6).forEach {
                appendLine("- ${it.date}: BP ${it.systolic ?: "?"}/${it.diastolic ?: "?"}, pulse ${it.pulse ?: "?"}" +
                    (it.weightKg?.let { w -> ", weight ${n(w, 0)} kg" } ?: ""))
            }
        }
    }

    /** Sent with an uploaded document, so the AI returns it in the app's import format. */
    const val EXTRACT_PROMPT = """The attached file is one of my medical documents from my Israeli HMO (it may be in Hebrew). Extract it into JSON for my health app.

Reply with ONE ```json block holding an object with any of these lists (leave out lists with nothing in them). Use English, dates as YYYY-MM-DD, and numbers as numbers. Do not include my name, ID number, address or phone number.

{
  "labs": [{"date": "", "test": "", "group": "Blood count | Chemistry | Lipids | Thyroid | Urine | Other", "value": 0, "unit": "", "low": null, "high": null, "note": ""}],
  "medications": [{"medication": "", "generic": "", "use": "", "dose": "", "kind": "Ongoing, renewed on request | Seasonal | Short course", "last_prescribed": "", "days_supply": 30, "times_prescribed": 1}],
  "care": [{"item": "", "last_done": "", "interval_months": 12, "why": "", "how": ""}],
  "open_items": [{"item": "", "date": "", "detail": ""}],
  "history": [{"date": "", "kind": "Visit | Diagnosis | Imaging | Procedure | Hospital | Vaccine | Injury", "item": "", "detail": "", "approx": false}],
  "vitals": [{"date": "", "systolic": null, "diastolic": null, "pulse": null, "weight_kg": null}]
}

"low"/"high" are the lab's reference range. "open_items" are referrals or tests a doctor asked for with no result in this document. After the JSON block, add two or three sentences in plain language on anything that stands out."""

    private fun n(v: Double, digits: Int) = String.format(Locale.US, "%.${digits}f", v)
}

package io.github.yarkoody.scalesync.medical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MedicalRecordTest {
    private val labs = """[
        {"date": "2025-10-01", "test": "MPV", "group": "Blood count", "value": 11.9, "unit": "fL", "low": 6.5, "high": 11.5, "note": ""},
        {"date": "2026-10-13", "test": "MPV", "group": "Blood count", "value": 12.3, "unit": "fL", "low": 6.5, "high": 11.5, "note": ""},
        {"date": "2026-10-13", "test": "Hemoglobin", "group": "Blood count", "value": 14.1, "unit": "g/dL", "low": 13.5, "high": 17.5, "note": ""},
        {"date": "2026-10-13", "test": "HbA1c", "group": "Chemistry", "value": 5.2, "unit": "%", "low": null, "high": null, "note": ""}
    ]"""

    @Test
    fun reads_a_separate_list_by_its_fields() {
        val r = MedicalRecord.parse(labs)
        assertEquals(4, r.labs.size)
        val latest = r.latestLabs().associate { it.first.test to it }
        assertEquals(Flag.HIGH, latest.getValue("MPV").first.flag)
        assertEquals(11.9, latest.getValue("MPV").second!!.value!!, 0.0)
        assertEquals(Flag.NORMAL, latest.getValue("Hemoglobin").first.flag)
        assertEquals(Flag.UNKNOWN, latest.getValue("HbA1c").first.flag)
    }

    @Test
    fun reads_a_claude_reply_with_a_fenced_block_and_israeli_dates() {
        val reply = "Here is the extraction:\n```json\n{\"labs\": [{\"date\": \"13/10/2026\", \"test\": \"Ferritin\", " +
            "\"value\": \"45,5\", \"unit\": \"ng/mL\", \"low\": 30, \"high\": 400}], \"medications\": []}\n```\nAll in range."
        val r = MedicalRecord.parse(reply)
        assertEquals(LocalDate.of(2026, 10, 13), r.labs.single().date)
        assertEquals(45.5, r.labs.single().value!!, 0.0)
    }

    @Test
    fun keeps_text_values() {
        val r = MedicalRecord.parse("""[{"date": "2026-10-13", "test": "Urine protein", "value": "Negative"}]""")
        assertEquals("Negative", r.labs.single().shown)
        assertEquals(Flag.UNKNOWN, r.labs.single().flag)
    }

    @Test
    fun merge_replaces_same_entries_and_round_trips() {
        val a = MedicalRecord.parse(labs)
        val fix = MedicalRecord.parse("""[{"date": "2026-10-13", "test": "mpv", "value": 12.0, "unit": "fL", "low": 6.5, "high": 11.5}]""")
        val merged = a.merge(fix)
        assertEquals(4, merged.labs.size)
        assertEquals(12.0, merged.labHistory("MPV").last().value!!, 0.0)
        assertEquals(merged, MedicalRecord.parse(merged.toJson()))
        assertEquals(merged, merged.merge(MedicalRecord.parse(merged.toJson())))
    }

    @Test
    fun due_items_skip_long_stale_renewals() {
        val r = MedicalRecord.parse("""{
            "medications": [
                {"medication": "Daily pill", "dose": "1 a day", "kind": "Ongoing, renewed on request", "last_prescribed": "2026-09-20", "days_supply": 30},
                {"medication": "Old spray", "dose": "2 a day", "kind": "Ongoing, renewed on request", "last_prescribed": "2025-01-01", "days_supply": 30}
            ],
            "care": [{"item": "Flu vaccine", "last_done": "2025-10-01", "interval_months": 12}],
            "open_items": [{"item": "Ultrasound", "date": "2024-01-01", "detail": "No result"}]
        }""")
        val due = r.dueItems(LocalDate.of(2026, 10, 16))
        assertEquals(listOf("Flu vaccine", "Renew Daily pill", "Ultrasound"), due.map { it.title })
        assertTrue(due.last().due == null)
    }
}

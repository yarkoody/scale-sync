package io.github.yarkoody.scalesync.data

import android.content.Context
import io.github.yarkoody.scalesync.core.BodyMetrics
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WeighIn(
    val sessionId: Long,
    val version: Long,
    val metrics: BodyMetrics,
    val syncedToHealthConnect: Boolean,
) {
    val time: Instant get() = Instant.ofEpochMilli(sessionId)
}

/**
 * Every weigh-in, including the metrics Health Connect has no place for (visceral fat,
 * muscle, protein, impedance). Stored as one CSV so it can be shared/exported as-is.
 */
class History(context: Context) {
    private val file = File(context.applicationContext.filesDir, "weigh-ins.csv")

    @Synchronized
    fun all(): List<WeighIn> {
        if (!file.exists()) return emptyList()
        return file.readLines().drop(1).mapNotNull(::parse).sortedByDescending { it.sessionId }
    }

    /** Inserts or replaces the row with the same session id. */
    @Synchronized
    fun upsert(w: WeighIn) {
        val rows = all().filterNot { it.sessionId == w.sessionId } + w
        write(rows)
    }

    @Synchronized
    fun markSynced(sessionId: Long) {
        write(all().map { if (it.sessionId == sessionId) it.copy(syncedToHealthConnect = true) else it })
    }

    fun file(): File = file

    private fun write(rows: List<WeighIn>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(buildString {
            appendLine(HEADER)
            rows.sortedBy { it.sessionId }.forEach { appendLine(format(it)) }
        })
        tmp.renameTo(file)
    }

    private fun format(w: WeighIn): String {
        val m = w.metrics
        fun f(v: Float?) = v?.let { String.format(Locale.US, "%.2f", it) } ?: ""
        return listOf(
            DATE.format(w.time), w.sessionId.toString(), w.version.toString(),
            f(m.weightKg), m.impedance?.toString() ?: "", f(m.bmi), f(m.fatPercent), f(m.waterPercent),
            f(m.musclePercent), f(m.boneKg), f(m.leanKg), f(m.visceralFat), f(m.proteinPercent),
            f(m.bmrKcal), if (w.syncedToHealthConnect) "1" else "0",
        ).joinToString(",")
    }

    private fun parse(line: String): WeighIn? = runCatching {
        val c = line.split(",")
        fun f(i: Int) = c[i].takeIf { it.isNotEmpty() }?.toFloat()
        WeighIn(
            sessionId = c[1].toLong(),
            version = c[2].toLong(),
            metrics = BodyMetrics(
                weightKg = c[3].toFloat(), impedance = c[4].takeIf { it.isNotEmpty() }?.toInt(),
                bmi = f(5), fatPercent = f(6), waterPercent = f(7), musclePercent = f(8),
                boneKg = f(9), leanKg = f(10), visceralFat = f(11), proteinPercent = f(12), bmrKcal = f(13),
            ),
            syncedToHealthConnect = c[14] == "1",
        )
    }.getOrNull()

    private companion object {
        const val HEADER = "time,session_id,version,weight_kg,impedance_ohm,bmi,body_fat_pct,water_pct," +
            "muscle_pct,bone_kg,lean_kg,visceral_fat,protein_pct,bmr_kcal,synced_health_connect"
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    }
}

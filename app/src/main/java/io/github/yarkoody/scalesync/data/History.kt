package io.github.yarkoody.scalesync.data

import android.content.Context
import io.github.yarkoody.scalesync.core.BodyMetrics
import io.github.yarkoody.scalesync.core.Drivers
import io.github.yarkoody.scalesync.core.ScaleDriver
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
    /** [ScaleDriver.id] of the scale that measured it. */
    val driverId: String = Drivers.legacyDefault.id,
) {
    val time: Instant get() = Instant.ofEpochMilli(sessionId)
    val driver: ScaleDriver get() = Drivers.byId(driverId) ?: Drivers.legacyDefault
}

/**
 * Every weigh-in, including the metrics Health Connect has no place for (visceral fat,
 * muscle, protein, impedance). Stored as one CSV so it can be shared/exported as-is.
 */
class History(private val dir: File) {
    constructor(context: Context) : this(context.applicationContext.filesDir)

    private val file = File(dir, "weigh-ins.csv")

    /**
     * Weigh-ins the user deleted, one per line: `session_id,removed_from_health_connect`.
     * Kept for good: it stops a deleted weigh-in from being saved again (a late impedance reading
     * for the same session, a profile recalculation) and remembers which still have to be removed
     * from Health Connect.
     */
    private val deletedFile = File(dir, "deleted-weigh-ins.csv")

    fun all(): List<WeighIn> = synchronized(LOCK) {
        if (!file.exists()) return emptyList()
        file.readLines().drop(1).mapNotNull(::parse).sortedByDescending { it.sessionId }
    }

    /** Inserts or replaces the row with the same session id. A deleted weigh-in is never re-added. */
    fun upsert(w: WeighIn): Unit = synchronized(LOCK) {
        if (isDeleted(w.sessionId)) return
        val rows = all().filterNot { it.sessionId == w.sessionId } + w
        write(rows)
    }

    fun markSynced(sessionId: Long): Unit = synchronized(LOCK) {
        write(all().map { if (it.sessionId == sessionId) it.copy(syncedToHealthConnect = true) else it })
    }

    /** Removes the weigh-in here and queues its removal from Health Connect. */
    fun delete(sessionId: Long): Unit = synchronized(LOCK) {
        val rows = all()
        if (rows.none { it.sessionId == sessionId }) return
        write(rows.filterNot { it.sessionId == sessionId })
        writeDeleted(readDeleted() + (sessionId to false))
    }

    fun isDeleted(sessionId: Long): Boolean = synchronized(LOCK) { sessionId in readDeleted() }

    /** Deleted weigh-ins whose records may still be in Health Connect, oldest first. */
    fun pendingHealthConnectRemovals(): List<Long> = synchronized(LOCK) {
        readDeleted().filterValues { removed -> !removed }.keys.sorted()
    }

    fun markRemovedFromHealthConnect(sessionId: Long): Unit = synchronized(LOCK) {
        val d = readDeleted()
        if (sessionId in d) writeDeleted(d + (sessionId to true))
    }

    private fun readDeleted(): Map<Long, Boolean> {
        if (!deletedFile.exists()) return emptyMap()
        return deletedFile.readLines().mapNotNull { line ->
            val c = line.split(",")
            c.getOrNull(0)?.toLongOrNull()?.let { it to (c.getOrNull(1) == "1") }
        }.toMap()
    }

    private fun writeDeleted(d: Map<Long, Boolean>) {
        val tmp = File(dir, deletedFile.name + ".tmp")
        tmp.writeText(d.entries.sortedBy { it.key }.joinToString("") { "${it.key},${if (it.value) "1" else "0"}\n" })
        tmp.renameTo(deletedFile)
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
            f(m.bmrKcal), if (w.syncedToHealthConnect) "1" else "0", w.driverId,
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
            // Rows written before drivers existed have no scale column; they came from a Mi BCS.
            driverId = c.getOrNull(15)?.takeIf { it.isNotEmpty() } ?: Drivers.legacyDefault.id,
        )
    }.getOrNull()

    private companion object {
        /** One lock for every History instance: the files are shared, the instances are not. */
        val LOCK = Any()
        const val HEADER = "time,session_id,version,weight_kg,impedance_ohm,bmi,body_fat_pct,water_pct," +
            "muscle_pct,bone_kg,lean_kg,visceral_fat,protein_pct,bmr_kcal,synced_health_connect,scale"
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())
    }
}

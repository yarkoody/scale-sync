package io.github.yarkoody.scalesync.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.yarkoody.scalesync.wellness.Day
import io.github.yarkoody.scalesync.wellness.Night
import io.github.yarkoody.scalesync.wellness.Stage
import io.github.yarkoody.scalesync.wellness.StageSpan
import io.github.yarkoody.scalesync.wellness.Wellness
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import kotlin.reflect.KClass

/** Reads what the Fitbit (through Google Health), the scale and other apps put in Health Connect. */
object HealthConnectReader {
    const val DAYS = 30L

    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
    )

    suspend fun read(context: Context, zone: ZoneId = ZoneId.systemDefault()): Wellness {
        if (!HealthConnectWriter.isAvailable(context)) return Wellness()
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val today = LocalDate.now(zone)
        val since = today.minusDays(DAYS).atStartOfDay(zone).toInstant()
        val filter = TimeRangeFilter.after(since)

        suspend fun <T : Record> all(type: KClass<T>): List<T> {
            if (HealthPermission.getReadPermission(type) !in granted) return emptyList()
            return runCatching {
                val out = mutableListOf<T>()
                var token: String? = null
                do {
                    val resp = client.readRecords(ReadRecordsRequest(type, filter, pageToken = token))
                    out += resp.records
                    token = resp.pageToken
                } while (token != null)
                out
            }.onFailure { Log.w("HealthConnectReader", "reading ${type.simpleName} failed", it) }
                .getOrDefault(emptyList())
        }

        fun day(t: Instant) = t.atZone(zone).toLocalDate()
        fun <T> daily(records: List<T>, time: (T) -> Instant, value: (T) -> Double): Map<LocalDate, Double> =
            records.groupBy { day(time(it)) }.mapValues { (_, v) -> v.map(value).average() }
        fun <T> lastOfDay(records: List<T>, time: (T) -> Instant, value: (T) -> Double): Map<LocalDate, Double> =
            records.groupBy { day(time(it)) }.mapValues { (_, v) -> value(v.maxBy(time)) }

        val nights = all(SleepSessionRecord::class).map { s ->
            Night.from(s.startTime, s.endTime, s.stages.map { StageSpan(stage(it.stage), it.startTime, it.endTime) },
                zone, s.metadata.dataOrigin.packageName)
        }
        val rhr = daily(all(RestingHeartRateRecord::class), { it.time }) { it.beatsPerMinute.toDouble() }
        val hrv = daily(all(HeartRateVariabilityRmssdRecord::class), { it.time }) { it.heartRateVariabilityMillis }
        val spo2 = daily(all(OxygenSaturationRecord::class), { it.time }) { it.percentage.value }
        val breath = daily(all(RespiratoryRateRecord::class), { it.time }) { it.rate }
        val weight = lastOfDay(all(WeightRecord::class), { it.time }) { it.weight.inKilograms }
        val fat = lastOfDay(all(BodyFatRecord::class), { it.time }) { it.percentage.value }

        val steps = HashMap<LocalDate, Long>()
        if (HealthPermission.getReadPermission(StepsRecord::class) in granted) runCatching {
            client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(today.minusDays(DAYS).atStartOfDay(), LocalDateTime.now(zone)),
                    timeRangeSlicer = Period.ofDays(1),
                )
            ).forEach { g -> g.result[StepsRecord.COUNT_TOTAL]?.let { steps[g.startTime.toLocalDate()] = it } }
        }.onFailure { Log.w("HealthConnectReader", "steps failed", it) }

        val dates = (0..DAYS).map { today.minusDays(it) }
        val days = dates.map { d ->
            Day(d, steps[d], rhr[d], hrv[d], spo2[d], breath[d], weight[d], fat[d])
        }
        return Wellness(days, Night.mainPerMorning(nights))
    }

    private fun stage(type: Int): Stage = when (type) {
        SleepSessionRecord.STAGE_TYPE_AWAKE,
        SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED,
        SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> Stage.AWAKE
        SleepSessionRecord.STAGE_TYPE_LIGHT -> Stage.LIGHT
        SleepSessionRecord.STAGE_TYPE_DEEP -> Stage.DEEP
        SleepSessionRecord.STAGE_TYPE_REM -> Stage.REM
        SleepSessionRecord.STAGE_TYPE_SLEEPING -> Stage.ASLEEP_UNSPECIFIED
        else -> Stage.UNKNOWN
    }
}

package io.github.yarkoody.scalesync.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import io.github.yarkoody.scalesync.data.WeighIn
import java.time.ZoneId
import kotlin.reflect.KClass

object HealthConnectWriter {

    /** Every record type a weigh-in can produce, with the kind used in its client record id. */
    private val KINDS: List<Pair<String, KClass<out Record>>> = listOf(
        "weight" to WeightRecord::class,
        "fat" to BodyFatRecord::class,
        "water" to BodyWaterMassRecord::class,
        "bone" to BoneMassRecord::class,
        "lean" to LeanBodyMassRecord::class,
        "bmr" to BasalMetabolicRateRecord::class,
    )

    private fun clientId(kind: String, sessionId: Long) = "scalesync-$kind-$sessionId"

    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(BodyWaterMassRecord::class),
        HealthPermission.getWritePermission(BoneMassRecord::class),
        HealthPermission.getWritePermission(LeanBodyMassRecord::class),
        HealthPermission.getWritePermission(BasalMetabolicRateRecord::class),
    )

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun grantedPermissions(context: Context): Set<String> =
        if (!isAvailable(context)) emptySet()
        else HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

    /**
     * Writes every metric the weigh-in has and Health Connect has a type for (a weight-only scale
     * gives weight and BMR; body fat is only written when it was computed from impedance), skipping types whose permission was not
     * granted. Records use client ids derived from the weigh-in session, so re-writing the same
     * weigh-in (for example weight-only first, then with impedance) replaces instead of duplicating.
     * Returns true if at least the weight was written.
     */
    suspend fun write(context: Context, w: WeighIn): Boolean {
        if (!isAvailable(context)) return false
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        val time = w.time
        val zone = ZoneId.systemDefault().rules.getOffset(time)
        val m = w.metrics
        val device = Device(type = Device.TYPE_SCALE, manufacturer = w.driver.manufacturer, model = w.driver.model)
        fun meta(kind: String) = Metadata.autoRecorded(device, clientId(kind, w.sessionId), w.version)

        val records = buildList<Record> {
            add(WeightRecord(time, zone, Mass.kilograms(m.weightKg.toDouble()), meta("weight")))
            m.fatPercent?.let { add(BodyFatRecord(time, zone, Percentage(it.toDouble()), meta("fat"))) }
            m.waterKg?.let { add(BodyWaterMassRecord(time, zone, Mass.kilograms(it.toDouble()), meta("water"))) }
            m.boneKg?.let { add(BoneMassRecord(time, zone, Mass.kilograms(it.toDouble()), meta("bone"))) }
            m.leanKg?.let { add(LeanBodyMassRecord(time, zone, Mass.kilograms(it.toDouble()), meta("lean"))) }
            m.bmrKcal?.let {
                add(BasalMetabolicRateRecord(time, zone, Power.kilocaloriesPerDay(it.toDouble()), meta("bmr")))
            }
        }.filter { HealthPermission.getWritePermission(it::class) in granted }

        if (records.none { it is WeightRecord }) return false
        client.insertRecords(records)
        return true
    }

    /**
     * Removes every record Scale Sync wrote for this weigh-in, found by its client record ids.
     * Health Connect only lets an app delete its own records, so another app's copy of the same
     * weigh-in (Zepp Life, for example) is never touched. Deleting an id that isn't there is a
     * no-op. Returns false when Health Connect can't be asked or weight may not be written, so
     * the caller keeps the removal queued and retries.
     */
    suspend fun delete(context: Context, sessionId: Long): Boolean {
        if (!isAvailable(context)) return false
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        if (HealthPermission.getWritePermission(WeightRecord::class) !in granted) return false
        for ((kind, type) in KINDS) {
            // Types without write permission now can't be deleted; nothing new was written to them either.
            if (HealthPermission.getWritePermission(type) !in granted) continue
            client.deleteRecords(type, recordIdsList = emptyList(), clientRecordIdsList = listOf(clientId(kind, sessionId)))
        }
        return true
    }
}

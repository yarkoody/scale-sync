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

object HealthConnectWriter {

    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(BodyWaterMassRecord::class),
        HealthPermission.getWritePermission(BoneMassRecord::class),
        HealthPermission.getWritePermission(LeanBodyMassRecord::class),
        HealthPermission.getWritePermission(BasalMetabolicRateRecord::class),
    )

    private val SCALE = Device(type = Device.TYPE_SCALE, manufacturer = "Xiaomi", model = "Mi Body Composition Scale 2")

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun grantedPermissions(context: Context): Set<String> =
        if (!isAvailable(context)) emptySet()
        else HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

    /**
     * Writes every metric Health Connect has a type for, skipping types whose permission was not
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
        fun meta(kind: String) = Metadata.autoRecorded(SCALE, "scalesync-$kind-${w.sessionId}", w.version)

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
}

package io.github.yarkoody.scalesync.data

import android.content.Context
import android.content.SharedPreferences
import io.github.yarkoody.scalesync.core.Algorithm
import io.github.yarkoody.scalesync.core.Drivers
import io.github.yarkoody.scalesync.core.ScaleDriver
import io.github.yarkoody.scalesync.core.Profile
import io.github.yarkoody.scalesync.core.Sex
import io.github.yarkoody.scalesync.core.WeighInDetector
import java.time.LocalDate

/** Small settings + detector state, kept in SharedPreferences so a cold-started receiver sees it. */
class Store(context: Context) {
    private val p: SharedPreferences =
        context.applicationContext.getSharedPreferences("scalesync", Context.MODE_PRIVATE)

    var profile: Profile?
        get() {
            val h = p.getFloat("height", 0f)
            val birth = p.getString("birth", null) ?: return null
            if (h <= 0f) return null
            return Profile(
                heightCm = h,
                birthDate = LocalDate.parse(birth),
                sex = Sex.valueOf(p.getString("sex", Sex.MALE.name)!!),
                algorithm = runCatching { Algorithm.valueOf(p.getString("algorithm", "")!!) }
                    .getOrDefault(Algorithm.XIAOMI),
            )
        }
        set(v) {
            p.edit().apply {
                if (v == null) {
                    remove("height"); remove("birth"); remove("sex"); remove("algorithm")
                } else {
                    putFloat("height", v.heightCm)
                    putString("birth", v.birthDate.toString())
                    putString("sex", v.sex.name)
                    putString("algorithm", v.algorithm.name)
                }
            }.commit()
        }

    /** Bluetooth address of the paired scale. */
    var scaleAddress: String?
        get() = p.getString("scale_address", null)
        set(v) { p.edit().putString("scale_address", v).commit() }

    /** Driver of the paired scale. Installs paired before drivers existed had a Mi BCS. */
    var driver: ScaleDriver?
        get() {
            if (scaleAddress == null) return null
            return Drivers.byId(p.getString("scale_driver", null)) ?: Drivers.legacyDefault
        }
        set(v) { p.edit().putString("scale_driver", v?.id).commit() }

    var detector: WeighInDetector.State
        get() = WeighInDetector.State(
            sessionId = p.getLong("d_session", 0L),
            sessionVersion = p.getLong("d_version", 0L),
            lastSavedAtMs = p.getLong("d_saved_at", 0L),
            lastSavedWeight = p.getFloat("d_saved_w", 0f),
            lastSavedHadImpedance = p.getBoolean("d_saved_imp", false),
            pendingWeight = p.getFloat("d_pending_w", 0f),
            pendingAtMs = p.getLong("d_pending_at", 0L),
            rawWeight = p.getFloat("d_raw_w", 0f),
            rawAtMs = p.getLong("d_raw_at", 0L),
            steadySinceMs = p.getLong("d_steady_since", 0L),
        )
        set(s) {
            p.edit()
                .putLong("d_session", s.sessionId)
                .putLong("d_version", s.sessionVersion)
                .putLong("d_saved_at", s.lastSavedAtMs)
                .putFloat("d_saved_w", s.lastSavedWeight)
                .putBoolean("d_saved_imp", s.lastSavedHadImpedance)
                .putFloat("d_pending_w", s.pendingWeight)
                .putLong("d_pending_at", s.pendingAtMs)
                .putFloat("d_raw_w", s.rawWeight)
                .putLong("d_raw_at", s.rawAtMs)
                .putLong("d_steady_since", s.steadySinceMs)
                .commit()
        }
}

package io.github.yarkoody.scalesync.data

import android.content.Context
import android.content.SharedPreferences
import io.github.yarkoody.scalesync.core.Algorithm
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

    var detector: WeighInDetector.State
        get() = WeighInDetector.State(
            sessionId = p.getLong("d_session", 0L),
            sessionVersion = p.getLong("d_version", 0L),
            lastSavedAtMs = p.getLong("d_saved_at", 0L),
            lastSavedWeight = p.getFloat("d_saved_w", 0f),
            lastSavedHadImpedance = p.getBoolean("d_saved_imp", false),
            pendingWeight = p.getFloat("d_pending_w", 0f),
            pendingAtMs = p.getLong("d_pending_at", 0L),
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
                .commit()
        }
}

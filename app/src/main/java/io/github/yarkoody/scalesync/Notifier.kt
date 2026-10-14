package io.github.yarkoody.scalesync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.yarkoody.scalesync.data.WeighIn
import io.github.yarkoody.scalesync.ui.MainActivity
import java.util.Locale

object Notifier {
    private const val CHANNEL = "weigh-ins"

    fun weighInSaved(context: Context, w: WeighIn, synced: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Weigh-ins", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val m = w.metrics
        val title = buildString {
            append(String.format(Locale.US, "%.2f kg", m.weightKg))
            m.fatPercent?.let { append(String.format(Locale.US, " · %.1f%% fat", it)) }
        }
        val text = when {
            !synced -> "Saved in Scale Sync. Open the app to finish the Health Connect setup."
            m.hasComposition -> "Sent to Health Connect with body composition."
            m.impedance == null -> "Weight only (no impedance: weigh barefoot and stay on until it finishes)."
            else -> "Sent to Health Connect. Add your profile in the app for body composition."
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_scale)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(w.sessionId.toInt(), n) }
    }
}

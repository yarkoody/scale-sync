package io.github.yarkoody.scalesync.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.yarkoody.scalesync.ble.ScaleScanner
import io.github.yarkoody.scalesync.core.Drivers
import io.github.yarkoody.scalesync.core.ScaleDriver
import io.github.yarkoody.scalesync.core.ScaleFrame
import io.github.yarkoody.scalesync.core.SupportLevel
import io.github.yarkoody.scalesync.data.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/** A small "Experimental" tag for drivers nobody has tested on the real scale. */
@Composable
fun SupportBadge(driver: ScaleDriver) {
    if (driver.capabilities.support != SupportLevel.EXPERIMENTAL) return
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            "Experimental",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private class Candidate(
    val address: String,
    val drivers: List<ScaleDriver>,
    val frame: ScaleFrame?,
    val rssi: Int,
)

/**
 * Finds the scale by listening for any advertisement a driver recognises. One driver: "Found: X —
 * is this yours?". Several drivers claiming the same device: the user picks.
 */
@Composable
fun PairCard(onPicked: (address: String, driver: ScaleDriver) -> Unit) {
    val context = LocalContext.current
    val found = remember { mutableStateMapOf<String, Candidate>() }
    val dismissed = remember { mutableStateListOf<String>() }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val cb = ScaleScanner.startForeground(context, null) { r ->
            val ad = ScaleScanner.toAdvertisement(r)
            scope.launch(Dispatchers.IO) { Diagnostics.record(context, ad) }
            val drivers = Drivers.matching(ad)
            if (drivers.isEmpty()) return@startForeground
            val frame = drivers.firstNotNullOfOrNull { Drivers.parse(it, ad) }?.takeIf { it.weightKg > 0f }
            val prev = found[ad.address]
            found[ad.address] = Candidate(ad.address, drivers, frame ?: prev?.frame, ad.rssi ?: -127)
        }
        onDispose { ScaleScanner.stopForeground(context, cb) }
    }
    val candidates = found.values
        .filterNot { it.address in dismissed }
        .sortedWith(compareByDescending<Candidate> { it.frame != null }.thenByDescending { it.rssi })

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Pair your scale", style = MaterialTheme.typography.titleMedium)
            Text("Step on the scale. Scale Sync listens for it and recognises the model; it never connects to it.",
                style = MaterialTheme.typography.bodyMedium)
            if (candidates.isEmpty()) Text("Looking…", style = MaterialTheme.typography.bodySmall)
            candidates.forEach { c -> CandidateRow(c, onPicked) { dismissed.add(c.address) } }
            TextButton(onClick = { shareCapture(context) }) { Text("Scale not found? Share a diagnostic capture") }
        }
    }
}

@Composable
private fun CandidateRow(c: Candidate, onPicked: (String, ScaleDriver) -> Unit, onNo: () -> Unit) {
    val weight = c.frame?.let { String.format(Locale.US, " · %.2f kg", it.weightKg) } ?: ""
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (c.drivers.size == 1) {
            val d = c.drivers.single()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Found: ${d.displayName}$weight — is this yours?",
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                SupportBadge(d)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPicked(c.address, d) }) { Text("Yes, that's mine") }
                OutlinedButton(onClick = onNo) { Text("No") }
            }
        } else {
            Text("Found a scale$weight (${c.address}). Which one is it?", style = MaterialTheme.typography.bodyLarge)
            c.drivers.forEach { d ->
                OutlinedButton(onClick = { onPicked(c.address, d) }, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(d.displayName)
                        SupportBadge(d)
                    }
                }
            }
            TextButton(onClick = onNo) { Text("None of these") }
        }
    }
}

/** The paired scale, with its support level and the diagnostic/forget actions. */
@Composable
fun PairedScaleRow(driver: ScaleDriver, onForget: () -> Unit) {
    val context = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scale: ${driver.displayName}", style = MaterialTheme.typography.bodySmall)
            SupportBadge(driver)
        }
        if (driver.capabilities.support == SupportLevel.EXPERIMENTAL) {
            Text("Not yet tested on this model. If a reading looks wrong, share a diagnostic capture.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row {
            TextButton(onClick = { shareCapture(context) }) { Text("Share diagnostic capture") }
            TextButton(onClick = onForget) { Text("Forget scale") }
        }
    }
}

/**
 * Hands the raw advertisement log to the share sheet. Nothing leaves the phone unless the user
 * picks an app to send it with; Scale Sync itself has no network access.
 */
fun shareCapture(context: Context) {
    val file = Diagnostics.file(context)
    if (!file.exists() || file.length() == 0L) {
        Toast.makeText(context, "Nothing captured yet. Step on the scale first.", Toast.LENGTH_LONG).show()
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, "Scale Sync diagnostic capture")
        .putExtra(Intent.EXTRA_TEXT,
            "Raw Bluetooth advertisements Scale Sync heard, for adding or fixing scale support. " +
                "While pairing it records every nearby Bluetooth device, so it may list other devices' addresses.")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share diagnostic capture"))
}

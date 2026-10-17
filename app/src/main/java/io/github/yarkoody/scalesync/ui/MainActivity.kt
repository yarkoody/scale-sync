package io.github.yarkoody.scalesync.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.health.connect.client.PermissionController
import io.github.yarkoody.scalesync.Processor
import io.github.yarkoody.scalesync.ble.ScaleScanner
import io.github.yarkoody.scalesync.core.Algorithm
import io.github.yarkoody.scalesync.core.Profile
import io.github.yarkoody.scalesync.core.Sex
import io.github.yarkoody.scalesync.data.History
import io.github.yarkoody.scalesync.data.Store
import io.github.yarkoody.scalesync.data.WeighIn
import io.github.yarkoody.scalesync.health.HealthConnectWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = when {
                Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(this)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(this)
                dark -> darkColorScheme()
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colors) {
                HomeApp()
            }
        }
    }
}

/** The scale: live reading, pairing, profile and weigh-ins. */
@SuppressLint("BatteryLife")
@Composable
fun BodyScreen(padding: PaddingValues) {
    val context = LocalContext.current
    val store = remember { Store(context) }
    val scope = rememberCoroutineScope()

    var profile by remember { mutableStateOf(store.profile) }
    var address by remember { mutableStateOf(store.scaleAddress) }
    var btGranted by remember { mutableStateOf(ScaleScanner.hasPermissions(context)) }
    var hcGranted by remember { mutableStateOf(emptySet<String>()) }
    val hcAvailable = remember { HealthConnectWriter.isAvailable(context) }
    var history by remember { mutableStateOf(emptyList<WeighIn>()) }
    var message by remember { mutableStateOf<String?>(null) }
    val historyVersion by Processor.historyVersion.collectAsState()
    val live by Processor.live.collectAsState()

    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        btGranted = ScaleScanner.hasPermissions(context)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val hcLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        hcGranted = it
        scope.launch { withContext(Dispatchers.IO) { Processor.retryUnsynced(context) } }
    }

    LaunchedEffect(Unit) {
        hcGranted = runCatching { HealthConnectWriter.grantedPermissions(context) }.getOrDefault(emptySet())
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        withContext(Dispatchers.IO) { Processor.retryUnsynced(context) }
    }
    LaunchedEffect(historyVersion) {
        history = withContext(Dispatchers.IO) { History(context).all() }
    }
    // Keep the system background scan armed, and run a fast scan while the app is open.
    DisposableEffect(btGranted, address) {
        var cb: ScanCallback? = null
        val a = address
        if (btGranted && a != null) {
            ScaleScanner.startBackground(context, a)
            cb = ScaleScanner.startForeground(context, a) { r ->
                scope.launch(Dispatchers.IO) { Processor.onServiceData(context, ScaleScanner.serviceData(r)) }
            }
        }
        onDispose { ScaleScanner.stopForeground(context, cb) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Body", "Xiaomi scale, saved to Health Connect") }

        item {
            LiveCard(live?.frame?.let { f ->
                val age = (System.currentTimeMillis() - (live?.atMs ?: 0)) / 1000
                if (age > 60) null else f
            }, address != null)
        }

        message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }

        if (!btGranted) item {
            SetupCard("Allow Bluetooth", "Needed to hear the scale. Scale Sync only listens; it never connects to it.") {
                btLauncher.launch(ScaleScanner.requiredPermissions())
            }
        } else if (!ScaleScanner.isBluetoothOn(context)) item {
            Text("Bluetooth is off. Turn it on so the app can hear the scale.", color = MaterialTheme.colorScheme.error)
        }

        if (btGranted && address == null) item {
            PairCard { picked ->
                store.scaleAddress = picked
                address = picked
            }
        }

        if (profile == null) item {
            ProfileCard(null) { p ->
                store.profile = p; profile = p
                scope.launch(Dispatchers.IO) { Processor.recomputeAll(context) }
            }
        }

        if (!hcAvailable) item {
            SetupCard("Install Health Connect", "Health Connect isn't available on this phone yet.", "Open Play Store") {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.google.android.apps.healthdata")))
            }
        } else if (!hcGranted.containsAll(HealthConnectWriter.PERMISSIONS)) item {
            SetupCard("Connect Health Connect",
                "Lets Scale Sync write weight, body fat, water, bone, lean mass and BMR.") {
                hcLauncher.launch(HealthConnectWriter.PERMISSIONS)
            }
        }

        val pm = context.getSystemService(PowerManager::class.java)
        if (address != null && !pm.isIgnoringBatteryOptimizations(context.packageName)) item {
            SetupCard("Let it run in the background",
                "So weigh-ins are caught even when the app is closed.", "Allow") {
                context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")))
            }
        }

        if (history.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Weigh-ins", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { shareCsv(context) }) { Text("Export CSV") }
                }
            }
            item { TrendCard(history) }
            itemsIndexed(history, key = { _, w -> w.sessionId }) { i, w ->
                WeighInCard(w, previous = history.getOrNull(i + 1), startExpanded = i == 0)
            }
        }

        if (profile != null) item {
            HorizontalDivider()
            var editing by remember { mutableStateOf(false) }
            if (editing) {
                ProfileCard(profile) { p ->
                    store.profile = p; profile = p; editing = false
                    message = "Profile saved. Recalculating past weigh-ins."
                    scope.launch(Dispatchers.IO) { Processor.recomputeAll(context) }
                }
            } else {
                val p = profile!!
                Text("Profile: ${p.heightCm.toInt()} cm, born ${p.birthDate.format(BIRTH_FORMAT)}, ${p.sex.name.lowercase()}, " +
                    "${if (p.algorithm == Algorithm.XIAOMI) "Zepp Life formulas" else "bodymiscale formulas"}",
                    style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { editing = true }) { Text("Edit profile") }
                    if (address != null) TextButton(onClick = {
                        ScaleScanner.stopBackground(context)
                        store.scaleAddress = null; address = null
                    }) { Text("Forget scale") }
                }
            }
        }
    }
}

@Composable
private fun LiveCard(frame: io.github.yarkoody.scalesync.core.MiScaleFrame?, paired: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (frame == null) {
                Text(if (paired) "Step on the scale" else "Pair your scale below",
                    style = MaterialTheme.typography.titleMedium)
                if (paired) Text("Barefoot, and stay on until it finishes, for body composition.",
                    style = MaterialTheme.typography.bodySmall)
            } else {
                Text(String.format(Locale.US, "%.2f kg", frame.weightKg), fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text(when {
                    frame.removed -> "Done"
                    frame.impedance != null -> "Measured. Saving…"
                    frame.stable -> "Measuring body composition…"
                    else -> "Weighing…"
                })
            }
        }
    }
}

@Composable
internal fun SetupCard(title: String, body: String, button: String = "Allow", onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onClick) { Text(button) }
        }
    }
}

@Composable
private fun PairCard(onPicked: (String) -> Unit) {
    val context = LocalContext.current
    val found = remember { mutableStateListOf<Pair<String, String>>() }
    DisposableEffect(Unit) {
        val cb = ScaleScanner.startForeground(context, null) { r ->
            if (ScaleScanner.isMiScale(r) && found.none { it.first == r.device.address }) {
                val name = r.scanRecord?.deviceName ?: "Mi scale"
                found.add(r.device.address to name)
            }
        }
        onDispose { ScaleScanner.stopForeground(context, cb) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pair your scale", style = MaterialTheme.typography.titleMedium)
            Text("Tap the scale with your foot to wake it up. It will show up here.",
                style = MaterialTheme.typography.bodyMedium)
            if (found.isEmpty()) Text("Looking…", style = MaterialTheme.typography.bodySmall)
            found.forEach { (addr, name) ->
                OutlinedButton(onClick = { onPicked(addr) }, modifier = Modifier.fillMaxWidth()) {
                    Text("$name  ($addr)")
                }
            }
        }
    }
}

private fun shareCsv(context: android.content.Context) {
    val file = History(context).file()
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/csv")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Export weigh-ins"))
}

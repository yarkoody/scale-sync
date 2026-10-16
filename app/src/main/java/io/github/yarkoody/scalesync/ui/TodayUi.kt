package io.github.yarkoody.scalesync.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.yarkoody.scalesync.insights.Coach
import io.github.yarkoody.scalesync.medical.DueItem
import io.github.yarkoody.scalesync.wellness.hm
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val LONG_DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM")

@Composable
fun TodayScreen(
    padding: PaddingValues,
    state: HubState,
    onConnect: () -> Unit,
    onAsk: (Coach) -> Unit,
    onOpenRecords: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    val w = state.wellness
    val zone = ZoneId.systemDefault()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenTitle("Today", today.format(LONG_DAY)) {
                TextButton(onClick = { scope.launch { state.refreshWellness() } }, enabled = !state.loading) {
                    Text(if (state.loading) "Loading…" else "Refresh")
                }
            }
        }

        if (!state.hcAvailable) item {
            SetupCard("Install Health Connect", "Your Fitbit, scale and other apps share their data through it.", "Open Play Store") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")))
            }
        } else if (!state.readGranted) item {
            SetupCard(
                "Bring in your Fitbit data",
                "Allow reading sleep, heart rate, HRV, steps, oxygen and weight from Health Connect. " +
                    "Google Health writes your Fitbit Air's data there. Everything stays on this phone.",
                "Connect",
                onConnect,
            )
        }

        // Last night
        val night = w.nights.firstOrNull()
        if (night != null) item {
            val sleep = w.sleepTrend(today)
            ElevatedCard(Modifier.fillMaxWidth().clickable { onAsk(Coach.SLEEP) }) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val label = if (ChronoUnit.DAYS.between(night.date, today) == 0L) "Last night" else "Sleep, ${night.date.format(DateTimeFormatter.ofPattern("EEE d MMM"))}"
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(hm(night.asleepMin), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        Text("${night.start.atZone(zone).format(CLOCK)} – ${night.end.atZone(zone).format(CLOCK)}",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    StageBar(night)
                    if (night.hasStages) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Legend(DeepColor, "Deep ${hm(night.deepMin)}")
                        Legend(RemColor, "REM ${hm(night.remMin)}")
                        Legend(AwakeColor, "Awake ${night.awakeMin}m")
                    }
                    sleep.avg7?.let {
                        Text("7-night average ${hm(it.toLong())}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Metric tiles, two per row
        if (state.readGranted) {
            val tiles: List<@Composable (Modifier) -> Unit> = listOf(
                { m -> MetricTile("Resting heart rate", w.trend(today) { it.restingHr }, { "${num(it)} bpm" }, false, m) },
                { m -> MetricTile("HRV", w.trend(today) { it.hrvMs }, { "${num(it)} ms" }, true, m) },
                { m -> MetricTile("Steps", w.trend(today) { it.steps?.toDouble() }, { num(it) }, true, m) },
                { m -> MetricTile("Blood oxygen", w.trend(today) { it.spo2 }, { "${num(it, 1)}%" }, null, m) },
                { m -> MetricTile("Breathing rate", w.trend(today) { it.breathingRate }, { "${num(it, 1)}/min" }, null, m) },
                { m -> MetricTile("Weight", w.trend(today) { it.weightKg }, { "${num(it, 1)} kg" }, null, m) },
            )
            items(tiles.chunked(2)) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { tile -> tile(Modifier.weight(1f)) }
                }
            }
        }

        // From the medical record
        val due = state.medical.dueItems().filter { it.due != null }.take(4)
        if (due.isNotEmpty()) {
            item { SectionTitle("Coming up") { TextButton(onClick = onOpenRecords) { Text("Records") } } }
            items(due) { DueRow(it, today) }
        } else if (state.medical.isEmpty) item {
            Card(Modifier.fillMaxWidth().clickable { onOpenRecords() }) {
                Column(Modifier.padding(16.dp)) {
                    Text("Add your medical record", style = MaterialTheme.typography.titleMedium)
                    Text("Blood tests, medications and follow-ups from Meuhedet, Clalit or Maccabi PDFs.",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onAsk(Coach.CHECK_IN) }) { Text("Weekly check-in with Claude") }
                OutlinedButton(onClick = { onAsk(Coach.LABS) }) { Text("Labs") }
            }
        }
    }
}

@Composable
internal fun DueRow(d: DueItem, today: LocalDate) {
    val overdue = d.due?.isBefore(today) == true
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(d.title, style = MaterialTheme.typography.titleSmall)
                if (d.detail.isNotEmpty()) Text(d.detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.padding(4.dp))
            Text(
                when {
                    d.due == null -> d.kind
                    overdue -> "Overdue\n${d.due.format(DateTimeFormatter.ofPattern("MMM yyyy"))}"
                    else -> d.due.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (overdue) WatchColor else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

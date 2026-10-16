package io.github.yarkoody.scalesync.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.yarkoody.scalesync.insights.Coach
import io.github.yarkoody.scalesync.wellness.Night
import io.github.yarkoody.scalesync.wellness.hm
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong
import kotlin.math.sqrt

private val NIGHT_DAY = DateTimeFormatter.ofPattern("EEE d MMM")
private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun SleepScreen(padding: PaddingValues, state: HubState, onConnect: () -> Unit, onAsk: (Coach) -> Unit) {
    val nights = state.wellness.nights
    val zone = ZoneId.systemDefault()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenTitle("Sleep", "From your Fitbit, through Health Connect") }

        if (!state.readGranted) item {
            SetupCard("Bring in your sleep", "Allow reading sleep from Health Connect.", "Connect", onConnect)
        } else if (nights.isEmpty()) item {
            Card(Modifier.fillMaxWidth()) {
                Text("No nights yet. Wear the Fitbit to bed for at least 3 hours; Google Health then shares the night " +
                    "with Health Connect, usually within an hour of waking.", Modifier.padding(16.dp))
            }
        }

        if (nights.isNotEmpty()) {
            item { SleepSummary(nights.take(7), zone) }
            item { DurationChart(nights.take(14).reversed()) }
            item { Button(onClick = { onAsk(Coach.SLEEP) }) { Text("Ask the sleep coach") } }
            item { SectionTitle("Nights") }
            items(nights, key = { it.date.toString() }) { NightRow(it, zone) }
        }
    }
}

@Composable
private fun SleepSummary(week: List<Night>, zone: ZoneId) {
    // Bedtimes are averaged as minutes after 18:00, so 23:30 and 00:30 average to midnight.
    fun offset(t: Instant): Long = (t.atZone(zone).toLocalTime().toSecondOfDay() / 60 - 18 * 60 + 1440L) % 1440
    fun clock(offsetMin: Double) = LocalTime.of(18, 0).plusMinutes(offsetMin.roundToLong()).format(HHMM)
    val bed = week.map { offset(it.start).toDouble() }
    val wake = week.map { offset(it.end).toDouble() }
    val bedSd = if (bed.size < 2) null else bed.average().let { m -> sqrt(bed.sumOf { (it - m) * (it - m) } / (bed.size - 1)) }
    val staged = week.filter { it.hasStages }
    val deepPct = staged.takeIf { it.isNotEmpty() }?.let { s -> 100.0 * s.sumOf { it.deepMin } / s.sumOf { it.asleepMin }.coerceAtLeast(1) }
    val remPct = staged.takeIf { it.isNotEmpty() }?.let { s -> 100.0 * s.sumOf { it.remMin } / s.sumOf { it.asleepMin }.coerceAtLeast(1) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Last ${week.size} nights", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${hm(week.map { it.asleepMin }.average().roundToLong())} a night", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("Bedtime", clock(bed.average()))
                Stat("Wake", clock(wake.average()))
                bedSd?.let { Stat("Bedtime varies", "±${it.roundToLong()}m") }
            }
            if (deepPct != null && remPct != null) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("Deep", "${num(deepPct)}%")
                Stat("REM", "${num(remPct)}%")
                Stat("Efficiency", "${week.map { it.efficiency }.average().roundToLong()}%")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/** Hours asleep per night, with dashed lines at 7 and 9 hours. */
@Composable
private fun DurationChart(nights: List<Night>) {
    if (nights.size < 2) return
    val bar = MaterialTheme.colorScheme.primary
    val guide = MaterialTheme.colorScheme.outline
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Hours asleep, last ${nights.size} nights", style = MaterialTheme.typography.labelLarge)
            Canvas(Modifier.fillMaxWidth().height(110.dp).padding(top = 8.dp)) {
                val maxH = 10f
                val slot = size.width / nights.size
                listOf(7f, 9f).forEach { h ->
                    val y = size.height * (1 - h / maxH)
                    drawLine(guide, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                }
                nights.forEachIndexed { i, n ->
                    val h = (n.asleepMin / 60f).coerceAtMost(maxH)
                    val top = size.height * (1 - h / maxH)
                    drawRoundRect(bar, Offset(i * slot + slot * 0.2f, top), Size(slot * 0.6f, size.height - top),
                        CornerRadius(3.dp.toPx()))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(nights.first().date.format(NIGHT_DAY), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("7h and 9h guides", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NightRow(n: Night, zone: ZoneId) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n.date.format(NIGHT_DAY), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(hm(n.asleepMin), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            StageBar(n)
            Text(
                "${n.start.atZone(zone).format(HHMM)} – ${n.end.atZone(zone).format(HHMM)}" +
                    (if (n.hasStages) " · deep ${hm(n.deepMin)} · REM ${hm(n.remMin)} · awake ${n.awakeMin}m" else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

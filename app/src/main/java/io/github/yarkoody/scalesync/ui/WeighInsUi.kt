package io.github.yarkoody.scalesync.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.yarkoody.scalesync.core.BodyMetrics
import io.github.yarkoody.scalesync.data.WeighIn
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM").withZone(ZoneId.systemDefault())
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private fun num(v: Float, digits: Int = 1) = String.format(Locale.US, "%.${digits}f", v)

/** Weight over time, oldest on the left. Shown once there are two or more weigh-ins. */
@Composable
fun TrendCard(history: List<WeighIn>) {
    val points = history.sortedBy { it.sessionId }.takeLast(30)
    if (points.size < 2) return
    val first = points.first().metrics.weightKg
    val last = points.last().metrics.weightKg
    val change = last - first
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Weight trend", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${num(last, 1)} kg", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "${if (change > 0) "+" else ""}${num(change, 1)} kg over ${points.size} weigh-ins",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                val weights = points.map { it.metrics.weightKg }
                val min = weights.min()
                val max = weights.max()
                val span = (max - min).coerceAtLeast(0.5f)
                val stepX = size.width / (weights.size - 1)
                fun y(w: Float) = size.height - (w - min) / span * size.height * 0.85f - size.height * 0.075f
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                val path = Path()
                weights.forEachIndexed { i, w ->
                    val p = Offset(i * stepX, y(w))
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(line, radius = 4.dp.toPx(), center = Offset(size.width, y(weights.last())))
            }
        }
    }
}

/**
 * One weigh-in. The newest starts expanded with every metric as a tile;
 * older ones show weight and body fat and expand on tap.
 */
@Composable
fun WeighInCard(w: WeighIn, previous: WeighIn?, startExpanded: Boolean) {
    var expanded by rememberSaveable(w.sessionId) { mutableStateOf(startExpanded) }
    val m = w.metrics
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${DAY.format(w.time)} · ${CLOCK.format(w.time)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(num(m.weightKg, 2), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                        Text(" kg", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp))
                    }
                }
                previous?.let { Delta(m.weightKg - it.metrics.weightKg) }
            }

            if (!expanded) {
                m.fatPercent?.let {
                    Text("${num(it)}% body fat · tap for details", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Spacer(Modifier.height(12.dp))
                MetricGrid(m)
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    if (w.syncedToHealthConnect) "In Health Connect" else "Not in Health Connect yet",
                    ok = w.syncedToHealthConnect,
                )
                Spacer(Modifier.weight(1f))
                m.impedance?.let {
                    Text("$it Ω", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Delta(diff: Float) {
    if (abs(diff) < 0.05f) {
        Text("no change", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val up = diff > 0
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            "${if (up) "▲" else "▼"} ${num(abs(diff), 1)} kg",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun StatusPill(text: String, ok: Boolean) {
    val color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private data class Metric(val label: String, val value: String, val note: String? = null, val warn: Boolean = false)

private fun metrics(m: BodyMetrics): List<Metric> = listOfNotNull(
    m.fatPercent?.let { Metric("Body fat", "${num(it)}%", m.fatKg?.let { kg -> "${num(kg)} kg" }) },
    m.musclePercent?.let { Metric("Muscle", "${num(it)}%", m.muscleKg?.let { kg -> "${num(kg)} kg" }) },
    m.waterPercent?.let { Metric("Water", "${num(it)}%", m.waterKg?.let { kg -> "${num(kg)} kg" }) },
    m.visceralFat?.let {
        val (note, warn) = when {
            it < 10f -> "healthy" to false
            it < 15f -> "high" to true
            else -> "very high" to true
        }
        Metric("Visceral fat", num(it, 0), note, warn)
    },
    m.boneKg?.let { Metric("Bone mass", "${num(it)} kg") },
    m.proteinPercent?.let { Metric("Protein", "${num(it)}%") },
    m.leanKg?.let { Metric("Lean mass", "${num(it)} kg") },
    m.bmrKcal?.let { Metric("BMR", num(it, 0), "kcal/day") },
    m.bmi?.let {
        val (note, warn) = when {
            it < 18.5f -> "under" to true
            it < 25f -> "normal" to false
            it < 30f -> "over" to true
            else -> "obese" to true
        }
        Metric("BMI", num(it), note, warn)
    },
)

@Composable
private fun MetricGrid(m: BodyMetrics) {
    val items = metrics(m)
    if (m.impedance == null) {
        Text(
            "Weight only. For body composition, weigh in barefoot and stay on until the scale finishes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { MetricTile(it, Modifier.weight(1f)) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MetricTile(metric: Metric, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(metric.label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(metric.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            metric.note?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                    color = if (metric.warn) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

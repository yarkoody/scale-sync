package io.github.yarkoody.scalesync.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ArrowDown
import com.composables.icons.lucide.ArrowUp
import com.composables.icons.lucide.BicepsFlexed
import com.composables.icons.lucide.Bone
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CloudOff
import com.composables.icons.lucide.Droplets
import com.composables.icons.lucide.Egg
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Layers
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.PersonStanding
import com.composables.icons.lucide.Target
import com.composables.icons.lucide.Zap
import io.github.yarkoody.scalesync.core.BodyMetrics
import io.github.yarkoody.scalesync.data.WeighIn
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM").withZone(ZoneId.systemDefault())
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

private fun num(v: Float, digits: Int = 1) = String.format(Locale.US, "%.${digits}f", v)

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
                MetricGrid(m, measuresComposition = w.driver.capabilities.hasImpedance)
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    if (w.syncedToHealthConnect) "In Health Connect" else "Not in Health Connect yet",
                    ok = w.syncedToHealthConnect,
                )
                Spacer(Modifier.weight(1f))
                m.impedance?.let {
                    Icon(Lucide.Zap, contentDescription = "Impedance", Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(3.dp))
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
        Row(Modifier.padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (up) Lucide.ArrowUp else Lucide.ArrowDown, contentDescription = if (up) "Up" else "Down",
                Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(4.dp))
            Text("${num(abs(diff), 1)} kg", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun StatusPill(text: String, ok: Boolean) {
    val color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Lucide.CircleCheck else Lucide.CloudOff, contentDescription = null, Modifier.size(14.dp), tint = color)
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** How a metric's note reads: plain detail, a reassuring band, or one worth a look. */
private enum class Tone { Detail, Good, Warn }

private data class Metric(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val note: String? = null,
    val tone: Tone = Tone.Detail,
)

private fun metrics(m: BodyMetrics): List<Metric> = listOfNotNull(
    m.fatPercent?.let { Metric("Body fat", "${num(it)}%", Lucide.Layers, m.fatKg?.let { kg -> "${num(kg)} kg" }) },
    m.musclePercent?.let { Metric("Muscle", "${num(it)}%", Lucide.BicepsFlexed, m.muscleKg?.let { kg -> "${num(kg)} kg" }) },
    m.waterPercent?.let { Metric("Water", "${num(it)}%", Lucide.Droplets, m.waterKg?.let { kg -> "${num(kg)} kg" }) },
    m.visceralFat?.let {
        val (note, tone) = when {
            it < 10f -> "healthy" to Tone.Good
            it < 15f -> "high" to Tone.Warn
            else -> "very high" to Tone.Warn
        }
        Metric("Visceral fat", num(it, 0), Lucide.Target, note, tone)
    },
    m.boneKg?.let { Metric("Bone mass", "${num(it)} kg", Lucide.Bone, "${num(it / m.weightKg * 100)}%") },
    m.proteinPercent?.let { Metric("Protein", "${num(it)}%", Lucide.Egg, "${num(m.weightKg * it / 100)} kg") },
    m.leanKg?.let { Metric("Lean mass", "${num(it)} kg", Lucide.PersonStanding, "${num(it / m.weightKg * 100)}%") },
    m.bmrKcal?.let { Metric("BMR", num(it, 0), Lucide.Flame, "kcal/day") },
    m.bmi?.let {
        val (note, tone) = when {
            it < 18.5f -> "under" to Tone.Warn
            it < 25f -> "normal" to Tone.Good
            it < 30f -> "over" to Tone.Warn
            else -> "obese" to Tone.Warn
        }
        Metric("BMI", num(it), Lucide.Gauge, note, tone)
    },
)

@Composable
private fun MetricGrid(m: BodyMetrics, measuresComposition: Boolean) {
    val items = metrics(m)
    if (m.impedance == null) {
        Text(
            if (measuresComposition) "Weight only. For body composition, weigh in barefoot and stay on until the scale finishes."
            else "This scale measures weight only. BMI and BMR are worked out from your profile; body fat isn't guessed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            // Every tile in a row takes the height of the tallest, so rows line up.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { MetricTile(it, Modifier.weight(1f).fillMaxHeight()) }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MetricTile(metric: Metric, modifier: Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.padding(10.dp)) {
            Box(Modifier.size(28.dp).background(accent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(metric.icon, contentDescription = null, Modifier.size(16.dp), tint = accent)
            }
            Spacer(Modifier.height(8.dp))
            Text(metric.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(metric.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // The note sits on the tile's bottom edge, so notes line up across a row.
            Spacer(Modifier.weight(1f).height(4.dp))
            metric.note?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (metric.tone == Tone.Detail) FontWeight.Normal else FontWeight.Medium,
                    color = when (metric.tone) {
                        Tone.Detail -> MaterialTheme.colorScheme.onSurfaceVariant
                        Tone.Good -> accent
                        Tone.Warn -> MaterialTheme.colorScheme.error
                    })
            }
        }
    }
}

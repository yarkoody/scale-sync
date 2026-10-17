package io.github.yarkoody.scalesync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.yarkoody.scalesync.wellness.Night
import io.github.yarkoody.scalesync.wellness.Trend
import java.util.Locale

internal val DeepColor = Color(0xFF3949AB)
internal val LightColor = Color(0xFF64B5F6)
internal val RemColor = Color(0xFF9575CD)
internal val AwakeColor = Color(0xFFFFB74D)
internal val GoodColor = Color(0xFF2E7D32)
internal val WatchColor = Color(0xFFC62828)

internal fun num(v: Double, digits: Int = 0) = String.format(Locale.US, "%.${digits}f", v)

@Composable
internal fun ScreenTitle(title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        action?.invoke()
    }
}

@Composable
internal fun SectionTitle(text: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/**
 * A metric against your own normal. [higherIsBetter] colours the change: null means neither
 * direction is good or bad (steps per day, weight).
 */
@Composable
internal fun MetricTile(
    label: String,
    trend: Trend,
    format: (Double) -> String,
    higherIsBetter: Boolean?,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(trend.latest?.let(format) ?: "–", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            val pct = trend.weekVsBaselinePct
            when {
                trend.latest == null -> Text("No data yet", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                pct == null -> Text(trend.avg7?.let { "7-day avg ${format(it)}" } ?: "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val arrow = if (kotlin.math.abs(pct) < 3) "≈" else if (pct > 0) "▲" else "▼"
                    val color = when {
                        kotlin.math.abs(pct) < 3 || higherIsBetter == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        (pct > 0) == higherIsBetter -> GoodColor
                        else -> WatchColor
                    }
                    Text("$arrow ${num(kotlin.math.abs(pct))}% this week vs 30 days", style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
        }
    }
}

/** Deep, light, REM and awake as one proportional bar. */
@Composable
internal fun StageBar(n: Night, modifier: Modifier = Modifier) {
    val parts = if (n.hasStages) listOf(n.deepMin to DeepColor, n.lightMin to LightColor, n.remMin to RemColor, n.awakeMin to AwakeColor)
    else listOf(n.asleepMin to LightColor)
    Row(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
        parts.filter { it.first > 0 }.forEach { (min, color) ->
            Box(Modifier.weight(min.toFloat()).height(10.dp).background(color))
        }
    }
}

@Composable
internal fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}

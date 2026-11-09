package io.github.yarkoody.scalesync.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.yarkoody.scalesync.data.WeighIn
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

private val AXIS_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM").withZone(ZoneId.systemDefault())
private val AXIS_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy").withZone(ZoneId.systemDefault())
private val POINT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm").withZone(ZoneId.systemDefault())

private fun kg(v: Float, digits: Int = 1) = String.format(Locale.US, "%.${digits}f", v)
private fun signedKg(v: Float) = (if (v > 0.05f) "+" else if (v < -0.05f) "−" else "") + kg(abs(v))

private enum class TrendSpan(val label: String, val spoken: String, val days: Long?) {
    Week("1W", "Last week", 7), Month("1M", "Last month", 30), Quarter("3M", "Last 3 months", 90),
    Year("1Y", "Last year", 365), All("All", "All weigh-ins", null)
}

/**
 * Weight over time on a real time axis (gaps between weigh-ins show as gaps), with a kg scale,
 * date labels, a range picker, and tap or drag to read any single weigh-in.
 * Shown once there are two or more weigh-ins.
 */
@Composable
fun TrendCard(history: List<WeighIn>) {
    val all = remember(history) { history.sortedBy { it.sessionId } }
    if (all.size < 2) return
    var span by rememberSaveable { mutableStateOf(TrendSpan.Quarter) }
    val points = remember(all, span) {
        val end = all.last().time
        val inSpan = span.days?.let { d -> all.filter { !it.time.isBefore(end.minus(d, ChronoUnit.DAYS)) } } ?: all
        if (inSpan.size >= 2) inSpan else all.takeLast(2)
    }
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val focus = points[selected ?: points.lastIndex]
    val first = points.first()
    val weights = points.map { it.metrics.weightKg }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (selected == null) "Weight trend" else POINT_DAY.format(focus.time),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(kg(focus.metrics.weightKg), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(" kg", style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${signedKg(focus.metrics.weightKg - first.metrics.weightKg)} kg",
                        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text("since ${AXIS_DAY.format(first.time)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))
            TrendChart(points, selected) { selected = it }

            Spacer(Modifier.height(12.dp))
            // Equal-width buttons across the card, like the range pickers in Health and Fit.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TrendSpan.entries.forEach { s -> SpanChip(s.label, s.spoken, s == span, Modifier.weight(1f)) { span = s } }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(10.dp))
            Row {
                Summary("Lowest", weights.min(), Modifier.weight(1f))
                Summary("Average", weights.average().toFloat(), Modifier.weight(1f))
                Summary("Highest", weights.max(), Modifier.weight(1f))
                Summary("Weigh-ins", null, Modifier.weight(1f), count = points.size)
            }
        }
    }
}

@Composable
private fun SpanChip(label: String, spoken: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = spoken },
        shape = RoundedCornerShape(50),
        color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            label,
            modifier = Modifier.padding(vertical = 7.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Summary(label: String, value: Float?, modifier: Modifier, count: Int? = null) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.let { "${kg(it)} kg" } ?: count.toString(), style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold)
    }
}

/** Smallest "nice" kg step at least [raw]: 0.2, 0.5, 1, 2, 5, 10... */
private fun niceStep(raw: Float): Float {
    var base = 0.1f
    while (true) {
        for (m in floatArrayOf(2f, 5f, 10f)) if (base * m >= raw) return base * m
        base *= 10f
    }
}

@Composable
private fun TrendChart(points: List<WeighIn>, selected: Int?, onSelect: (Int?) -> Unit) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val dotHole = MaterialTheme.colorScheme.surfaceContainerLow
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    // Read inside the long-lived touch handlers, so they see the current selection.
    val current by rememberUpdatedState(selected)

    val times = points.map { it.time.toEpochMilli() }
    val weights = points.map { it.metrics.weightKg }
    val t0 = times.first()
    val tSpan = (times.last() - t0).coerceAtLeast(1L).toFloat()
    val lo = weights.min()
    val hi = weights.max()
    val step = niceStep(((hi - lo).coerceAtLeast(0.6f)) / 3f)
    val yMin = floor(lo / step) * step
    val yMax = (ceil(hi / step) * step).let { if (it - yMin < step) yMin + step else it }
    val longRange = tSpan > 200L * 24 * 3600 * 1000
    val avg = weights.average().toFloat()

    // Geometry shared by drawing and touch: y labels on the right, dates underneath.
    val yLabelW = 34.dp
    val xLabelH = 20.dp
    val topPad = 8.dp

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .semantics { contentDescription = "Weight from ${kg(weights.first())} to ${kg(weights.last())} kg" }
            .pointerInput(points) {
                fun nearest(x: Float): Int {
                    val plotW = size.width - yLabelW.toPx()
                    return times.indices.minBy { abs((times[it] - t0) / tSpan * plotW - x) }
                }
                detectTapGestures { p -> nearest(p.x).let { onSelect(if (current == it) null else it) } }
            }
            .pointerInput(points) {
                fun nearest(x: Float): Int {
                    val plotW = size.width - yLabelW.toPx()
                    return times.indices.minBy { abs((times[it] - t0) / tSpan * plotW - x) }
                }
                detectHorizontalDragGestures { change, _ -> onSelect(nearest(change.position.x)) }
            },
    ) {
        val plotW = size.width - yLabelW.toPx()
        val plotTop = topPad.toPx()
        val plotBottom = size.height - xLabelH.toPx()
        val plotH = plotBottom - plotTop
        fun x(i: Int) = (times[i] - t0) / tSpan * plotW
        fun y(w: Float) = plotBottom - (w - yMin) / (yMax - yMin) * plotH

        // kg gridlines and labels
        var g = yMin
        while (g <= yMax + step / 2) {
            val gy = y(g)
            drawLine(grid, Offset(0f, gy), Offset(plotW, gy), strokeWidth = 1.dp.toPx())
            val t = measurer.measure(kg(g, if (step < 1f) 1 else 0), labelStyle)
            drawText(t, topLeft = Offset(plotW + 6.dp.toPx(), gy - t.size.height / 2f))
            g += step
        }

        // average, dashed, labelled at its left end
        val avgColor = line.copy(alpha = 0.55f)
        drawLine(avgColor, Offset(0f, y(avg)), Offset(plotW, y(avg)), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
        val avgLabel = measurer.measure("avg", labelStyle.copy(color = avgColor))
        drawText(avgLabel, topLeft = Offset(2.dp.toPx(), y(avg) - avgLabel.size.height - 1.dp.toPx()))

        // date labels: start, middle, end
        val fmt = if (longRange) AXIS_MONTH else AXIS_DAY
        listOf(0f, 0.5f, 1f).forEach { f ->
            val label = measurer.measure(fmt.format(java.time.Instant.ofEpochMilli(t0 + (tSpan * f).toLong())), labelStyle)
            val lx = when (f) { 0f -> 0f; 1f -> plotW - label.size.width; else -> plotW * f - label.size.width / 2f }
            drawText(label, topLeft = Offset(lx, plotBottom + 4.dp.toPx()))
        }

        // the line
        val path = Path()
        weights.forEachIndexed { i, w -> if (i == 0) path.moveTo(x(i), y(w)) else path.lineTo(x(i), y(w)) }
        drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (points.size <= 45) weights.forEachIndexed { i, w ->
            drawCircle(line, radius = 3.dp.toPx(), center = Offset(x(i), y(w)))
            drawCircle(dotHole, radius = 1.5.dp.toPx(), center = Offset(x(i), y(w)))
        }

        // focus: the selected weigh-in, or the latest
        val f = selected ?: points.lastIndex
        if (selected != null) {
            drawLine(line.copy(alpha = 0.5f), Offset(x(f), plotTop), Offset(x(f), plotBottom), strokeWidth = 1.dp.toPx())
        }
        drawCircle(line, radius = 5.dp.toPx(), center = Offset(x(f), y(weights[f])))
        drawCircle(dotHole, radius = 2.dp.toPx(), center = Offset(x(f), y(weights[f])))
    }
}

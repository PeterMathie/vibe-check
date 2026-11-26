package com.petermathie.vibecheck.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.VibeDashboardTypography
import com.petermathie.vibecheck.ui.theme.VibeSpacing
import com.petermathie.vibecheck.ui.formatDisplayDate
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

enum class VibeGraphStyle { LINE, BARS }

sealed interface VibeGraphState {
    data object Data : VibeGraphState
    data object Loading : VibeGraphState
    data class Empty(val message: String) : VibeGraphState
    data class Error(val message: String) : VibeGraphState
}

internal data class GraphDomain(val minimum: Double, val maximum: Double) {
    val span: Double get() = (maximum - minimum).coerceAtLeast(1.0)
}

internal fun graphDomain(
    values: List<Double>,
    fixedRange: ClosedFloatingPointRange<Double>? = null,
    secondaryValues: List<Double?> = emptyList(),
): GraphDomain? {
    fixedRange?.let { return GraphDomain(it.start, it.endInclusive) }
    val finite = values.filter(Double::isFinite)
    if (finite.isEmpty()) return null
    val plotted = finite + secondaryValues.filterNotNull().filter(Double::isFinite)
    return GraphDomain(checkNotNull(plotted.minOrNull()), checkNotNull(plotted.maxOrNull()))
}

internal fun nearestGraphIndex(x: Float, width: Float, count: Int): Int {
    if (count <= 1 || width <= 0f) return 0
    return ((x / width) * (count - 1)).roundToInt().coerceIn(0, count - 1)
}

internal fun graphPoint(
    index: Int,
    value: Double,
    count: Int,
    width: Float,
    height: Float,
    domain: GraphDomain,
    padding: Float,
): Offset {
    val plotWidth = (width - padding * 2f).coerceAtLeast(1f)
    val plotHeight = (height - padding * 2f).coerceAtLeast(1f)
    return Offset(
        x = if (count <= 1) width / 2f else padding + plotWidth * index / (count - 1),
        y = height - padding - ((value - domain.minimum) / domain.span * plotHeight).toFloat(),
    )
}

@Composable
fun VibeGraph(
    values: List<Double>,
    dates: List<Long>,
    unit: String,
    modifier: Modifier = Modifier,
    secondaryValues: List<Double?> = emptyList(),
    style: VibeGraphStyle = VibeGraphStyle.LINE,
    graphState: VibeGraphState = if (values.any(Double::isFinite)) VibeGraphState.Data else VibeGraphState.Empty("Complete a valid entry to see this chart."),
    fixedRange: ClosedFloatingPointRange<Double>? = null,
    dateRange: LongRange? = null,
    readoutBelow: Boolean = false,
    onSelect: (Int) -> Unit = {},
    edgeToEdge: Boolean = false,
    plotHeight: Dp = 138.dp,
    readoutDateLabel: String? = null,
    selectedDate: Long? = null,
    onDateSelect: ((Long) -> Unit)? = null,
    compactLayout: Boolean = false,
    onValueHelp: (() -> Unit)? = null,
    valueFormatter: ((Double) -> String)? = null,
    valueHelpDescription: String = "What are points?",
) {
    if (dateRange != null) {
        require(dateRange.first <= dateRange.last && dates.size == values.size)
    }
    val palette = LocalVibePalette.current
    fun valueText(value: Double) = valueFormatter?.invoke(value) ?: formatGraphAxis(value)
    val domain = remember(values, fixedRange, secondaryValues) { graphDomain(values, fixedRange, secondaryValues) }
    val graphPaddingPx = with(LocalDensity.current) { if (edgeToEdge) 0f else 12.dp.toPx() }
    var selectedIndex by remember(values, dates, dateRange) { mutableIntStateOf(values.indexOfLast(Double::isFinite)) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnDateSelect by rememberUpdatedState(onDateSelect)
    val select: (Float, Float) -> Unit = { x, width ->
        if (values.isNotEmpty()) {
            val plotX = (x - graphPaddingPx).coerceAtLeast(0f)
            val plotWidth = (width - graphPaddingPx * 2f).coerceAtLeast(1f)
            (if (dateRange != null) {
                values.indices.filter { values[it].isFinite() }.minByOrNull {
                    kotlin.math.abs(graphDateX(dates[it], dateRange, width, graphPaddingPx) - x)
                } ?: 0
            } else if (style == VibeGraphStyle.BARS) {
                (plotX / plotWidth * values.size).toInt().coerceIn(0, values.lastIndex)
            } else {
                nearestGraphIndex(plotX, plotWidth, values.size)
            })
                .let { candidate ->
                    if (values[candidate].isFinite()) candidate
                    else values.indices.minByOrNull { kotlin.math.abs(it - candidate) + if (values[it].isFinite()) 0 else values.size } ?: candidate
                }
                .also {
                    selectedIndex = it
                    currentOnSelect(it)
                }
            dateRange?.let { currentOnDateSelect?.invoke(graphDateAtX(x, it, width, graphPaddingPx)) }
        }
    }
    val description = domain?.let {
        val minimum = if (valueFormatter != null) valueText(it.minimum) else if (fixedRange == null) it.minimum.toString() else formatGraphAxis(it.minimum)
        val maximum = if (valueFormatter != null) valueText(it.maximum) else if (fixedRange == null) it.maximum.toString() else formatGraphAxis(it.maximum)
        "Progress chart from ${formatGraphDate(dates.firstOrNull())} to ${formatGraphDate(dates.lastOrNull())}, $minimum to $maximum $unit"
    } ?: "Progress chart, no data"

    Box(modifier = modifier.fillMaxWidth()) {
        when (graphState) {
            VibeGraphState.Loading -> VibeSkeleton("Loading chart", Modifier.fillMaxWidth().height(178.dp))
            is VibeGraphState.Empty -> VibeStatePanel(graphState.message)
            is VibeGraphState.Error -> VibeStatePanel(graphState.message, isError = true)
            VibeGraphState.Data -> if (domain != null) {
                Column(Modifier.fillMaxWidth().padding(horizontal = if (edgeToEdge) 0.dp else VibeSpacing.compact, vertical = if (compactLayout) VibeSpacing.xSmall else VibeSpacing.compact)) {
                    if (!readoutBelow) selectedIndex.takeIf { it in values.indices && values[it].isFinite() }?.let { index ->
                        DateValueReadout(
                            date = dates.getOrNull(index)?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() },
                            value = "${valueText(values[index])} $unit",
                            modifier = Modifier.padding(bottom = VibeSpacing.small).semantics {
                                contentDescription = "Graph readout"
                                stateDescription = "${formatGraphDate(dates.getOrNull(index))}, ${valueText(values[index])} $unit"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            valueColor = palette.textPrimary,
                        )
                    }
                    if (!readoutBelow) Text("${valueText(domain.maximum)} $unit", style = VibeDashboardTypography.annotation)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(plotHeight)
                            .pointerInput(values, dates, style, dateRange, graphPaddingPx) {
                                detectTapGestures { select(it.x, size.width.toFloat()) }
                            }
                            .pointerInput(values, dates, style, dateRange, graphPaddingPx) {
                                detectHorizontalDragGestures { change, _ ->
                                    change.consume()
                                    select(change.position.x, size.width.toFloat())
                                }
                            }
                            .semantics {
                                contentDescription = description
                                selectedIndex.takeIf { it in values.indices }?.let {
                                    stateDescription = "${formatGraphDate(dates.getOrNull(it))}, ${valueText(values[it])} $unit"
                                }
                                customActions = listOf(
                                    CustomAccessibilityAction("Previous data point") {
                                        val start = selectedIndex.takeIf { it in values.indices } ?: values.size
                                        val previous = (start - 1 downTo 0).firstOrNull { values[it].isFinite() }
                                        if (previous == null) false else {
                                            selectedIndex = previous
                                            currentOnSelect(selectedIndex)
                                            dates.getOrNull(selectedIndex)?.let { currentOnDateSelect?.invoke(it) }
                                            true
                                        }
                                    },
                                    CustomAccessibilityAction("Next data point") {
                                        val next = ((selectedIndex + 1).coerceAtLeast(0)..values.lastIndex)
                                            .firstOrNull { values[it].isFinite() }
                                        if (next == null) false else {
                                            selectedIndex = next
                                            currentOnSelect(selectedIndex)
                                            dates.getOrNull(selectedIndex)?.let { currentOnDateSelect?.invoke(it) }
                                            true
                                        }
                                    },
                                )
                            },
                    ) {
                        val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) }
                        Canvas(
                            Modifier
                                .matchParentSize()
                                .drawWithCache {
                                    val padding = 12.dp.toPx()
                                    val horizontalPadding = if (edgeToEdge) 0f else padding
                                    fun point(index: Int, value: Double): Offset {
                                        val point = graphPoint(index, value, values.size, size.width, size.height, domain, padding)
                                        return if (dateRange == null) point else point.copy(
                                            x = graphDateX(dates[index], dateRange, size.width, horizontalPadding),
                                        )
                                    }
                                    val slot = (size.width - horizontalPadding * 2f) / values.size.coerceAtLeast(1)
                                    val barWidth = if (dateRange == null) slot * 0.6f else {
                                        val positions = dates.map { graphDateX(it, dateRange, size.width, horizontalPadding) }.distinct().sorted()
                                        val gap = positions.zipWithNext { a, b -> b - a }.minOrNull() ?: 20.dp.toPx()
                                        (gap * 0.6f).coerceIn(2.dp.toPx(), 12.dp.toPx())
                                    }
                                    val major = palette.textPrimary.copy(alpha = if (palette.isDark) 0.09f else 0.12f)
                                    val primaryPath = Path()
                                    var primaryStarted = false
                                    val primaryPoints = values.mapIndexedNotNull { index, value ->
                                        value.takeIf(Double::isFinite)?.let {
                                            point(index, it)
                                        }
                                    }
                                    values.forEachIndexed { index, value ->
                                        if (!value.isFinite()) {
                                            primaryStarted = false
                                        } else {
                                            val point = point(index, value)
                                            if (primaryStarted) primaryPath.lineTo(point.x, point.y) else primaryPath.moveTo(point.x, point.y)
                                            primaryStarted = true
                                        }
                                    }
                                    val secondarySegments = secondaryValues.mapIndexedNotNull { index, value ->
                                        val previous = secondaryValues.getOrNull(index - 1)
                                        if (value != null && value.isFinite() && previous != null && previous.isFinite()) {
                                            point(index - 1, previous) to point(index, value)
                                        } else null
                                    }
                                    onDrawBehind {
                                        repeat(5) { row ->
                                            val y = padding + (size.height - padding * 2f) * row / 4f
                                            drawLine(major, Offset(horizontalPadding, y), Offset(size.width - horizontalPadding, y), 1.dp.toPx())
                                        }
                                        repeat(4) { column ->
                                            val x = horizontalPadding + (size.width - horizontalPadding * 2f) * column / 3f
                                            drawLine(major, Offset(x, padding), Offset(x, size.height - padding), 1.dp.toPx())
                                        }
                                        if (style == VibeGraphStyle.LINE) {
                                            drawPath(primaryPath, palette.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                                            primaryPoints.forEach { drawCircle(palette.accent, 2.5.dp.toPx(), it) }
                                        } else {
                                            values.forEachIndexed { index, value ->
                                                if (value.isFinite()) {
                                                    val point = point(index, value).let {
                                                        if (dateRange == null) it.copy(x = horizontalPadding + slot * (index + 0.5f)) else it
                                                    }
                                                    drawRect(
                                                        palette.accent,
                                                        topLeft = Offset(point.x - barWidth / 2, point.y),
                                                        size = androidx.compose.ui.geometry.Size(barWidth, size.height - padding - point.y),
                                                    )
                                                }
                                            }
                                        }
                                        secondarySegments.forEach { (from, to) ->
                                            drawLine(palette.secondary, from, to, 2.5.dp.toPx(), pathEffect = dashEffect)
                                        }
                                    }
                                }
                                ,
                        ) {
                            val index = selectedIndex
                            if (index in values.indices && values[index].isFinite()) {
                                val padding = 12.dp.toPx()
                                val horizontalPadding = if (edgeToEdge) 0f else padding
                                val linePoint = graphPoint(index, values[index], values.size, size.width, size.height, domain, padding).let {
                                    if (dateRange == null) it else it.copy(x = graphDateX(dates[index], dateRange, size.width, horizontalPadding))
                                }
                                val point = if (style == VibeGraphStyle.BARS && dateRange == null) {
                                    val slot = (size.width - horizontalPadding * 2f) / values.size.coerceAtLeast(1)
                                    linePoint.copy(x = horizontalPadding + slot * (index + 0.5f))
                                } else {
                                    linePoint
                                }
                                drawCircle(palette.accent, 3.5.dp.toPx(), point)
                                drawCircle(palette.background, 1.dp.toPx(), point)
                                val markerX = selectedDate?.takeIf { dateRange != null && it in dateRange }?.let {
                                    graphDateX(it, checkNotNull(dateRange), size.width, horizontalPadding)
                                } ?: point.x
                                drawLine(
                                    palette.textSecondary.copy(alpha = 0.6f),
                                    Offset(markerX, padding),
                                    Offset(markerX, size.height - padding),
                                    1.dp.toPx(),
                                    pathEffect = dashEffect,
                                )
                            }
                        }
                    }
                    if (dateRange != null) {
                        GraphDateAxis(dateRange, graphPaddingPx)
                    } else Row(Modifier.fillMaxWidth()) {
                        Text(formatGraphDate(dates.firstOrNull()), style = VibeDashboardTypography.annotation, modifier = Modifier.weight(1f))
                        Text(formatGraphDate(dates.lastOrNull()), style = VibeDashboardTypography.annotation, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                    if (readoutBelow) selectedIndex.takeIf { it in values.indices && values[it].isFinite() }?.let { index ->
                        DateValueReadout(
                            date = dates.getOrNull(index)?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() },
                            value = "${valueText(values[index])} $unit",
                            modifier = Modifier.padding(top = if (compactLayout) VibeSpacing.xSmall else VibeSpacing.small).semantics {
                                contentDescription = "Graph readout"
                                stateDescription = "${formatGraphDate(dates.getOrNull(index))}, ${valueText(values[index])} $unit"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            valueColor = palette.textPrimary,
                            dateLabel = readoutDateLabel,
                            onValueHelp = onValueHelp,
                            valueHelpDescription = valueHelpDescription,
                            compactValue = if (compactLayout) values.filter(Double::isFinite)
                                .map { "${valueText(it)} $unit" }.maxByOrNull { it.length } else null,
                        )
                    }
                    if (!readoutBelow) Text("${valueText(domain.minimum)} $unit", style = VibeDashboardTypography.annotation)
                }
            }
        }
    }
}

internal fun formatGraphDate(value: Long?): String = value?.let {
    formatDisplayDate(it)
}.orEmpty()

internal fun formatGraphAxis(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)

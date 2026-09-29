package com.petermathie.vibecheck.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.LocalVibeDashboardTypography
import com.petermathie.vibecheck.ui.theme.LocalVibeStyleTokens
import com.petermathie.vibecheck.ui.theme.LocalVibeVisualStyle
import com.petermathie.vibecheck.ui.theme.VibeBackdropMode
import com.petermathie.vibecheck.ui.theme.VibeSpacing
import com.petermathie.vibecheck.ui.theme.VibeShapes
import com.petermathie.vibecheck.ui.theme.VibeSurfaceLevel
import com.petermathie.vibecheck.ui.theme.VibeVisualStyle
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

internal fun graphDomain(values: List<Double>, fixedRange: ClosedFloatingPointRange<Double>? = null): GraphDomain? {
    fixedRange?.let { return GraphDomain(it.start, it.endInclusive) }
    val finite = values.filter(Double::isFinite)
    if (finite.isEmpty()) return null
    return GraphDomain(finite.minOrNull() ?: 0.0, finite.maxOrNull() ?: 1.0)
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

internal data class GraphGridLine(
    val start: Offset,
    val end: Offset,
    val major: Boolean,
)

internal fun graphGridLines(
    width: Float,
    height: Float,
    padding: Float,
    visualStyle: VibeVisualStyle,
): List<GraphGridLine> {
    val plotWidth = (width - padding * 2f).coerceAtLeast(1f)
    val plotHeight = (height - padding * 2f).coerceAtLeast(1f)
    if (visualStyle == VibeVisualStyle.STANDARD) {
        return buildList {
            repeat(5) { row ->
                val y = padding + plotHeight * row / 4f
                add(GraphGridLine(Offset(padding, y), Offset(width - padding, y), true))
            }
            repeat(4) { column ->
                val x = padding + plotWidth * column / 3f
                add(GraphGridLine(Offset(x, padding), Offset(x, height - padding), true))
            }
        }
    }
    return buildList {
        repeat(17) { row ->
            val y = padding + plotHeight * row / 16f
            add(GraphGridLine(Offset(padding, y), Offset(width - padding, y), row % 4 == 0))
        }
        repeat(17) { column ->
            val x = padding + plotWidth * column / 16f
            add(GraphGridLine(Offset(x, padding), Offset(x, height - padding), column % 4 == 0))
        }
    }
}

internal fun graphBarPoint(
    index: Int,
    value: Double,
    count: Int,
    width: Float,
    height: Float,
    domain: GraphDomain,
    padding: Float,
): Offset {
    val linePoint = graphPoint(index, value, count, width, height, domain, padding)
    val slot = (width - padding * 2f) / count.coerceAtLeast(1)
    return linePoint.copy(x = padding + slot * (index + 0.5f))
}

internal data class GraphTooltipPlacement(
    val offset: IntOffset,
    val abovePoint: Boolean,
)

internal fun graphTooltipPlacement(
    point: Offset,
    viewport: IntSize,
    tooltip: IntSize,
    gapPx: Int,
): GraphTooltipPlacement {
    val maxX = (viewport.width - tooltip.width).coerceAtLeast(0)
    val maxY = (viewport.height - tooltip.height).coerceAtLeast(0)
    val x = (point.x - tooltip.width / 2f).roundToInt().coerceIn(0, maxX)
    val aboveY = point.y.roundToInt() - gapPx - tooltip.height
    val belowY = point.y.roundToInt() + gapPx
    val above = aboveY >= 0 || belowY + tooltip.height > viewport.height
    return GraphTooltipPlacement(
        offset = IntOffset(x, (if (above) aboveY else belowY).coerceIn(0, maxY)),
        abovePoint = above,
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
    onSelect: (Int) -> Unit = {},
) {
    val palette = LocalVibePalette.current
    val visualStyle = LocalVibeVisualStyle.current
    val styleTokens = LocalVibeStyleTokens.current
    val typography = LocalVibeDashboardTypography.current
    val domain = remember(values, fixedRange) { graphDomain(values, fixedRange) }
    val graphPaddingPx = with(LocalDensity.current) { 12.dp.toPx() }
    val tooltipGapPx = with(LocalDensity.current) { 6.dp.roundToPx() }
    var selectedIndex by remember(values) { mutableIntStateOf(-1) }
    var plotSize by remember { mutableStateOf(IntSize.Zero) }
    var tooltipSize by remember { mutableStateOf(IntSize.Zero) }
    val select: (Float, Float) -> Unit = { x, width ->
        if (values.isNotEmpty()) {
            val plotX = (x - graphPaddingPx).coerceAtLeast(0f)
            val plotWidth = (width - graphPaddingPx * 2f).coerceAtLeast(1f)
            (if (style == VibeGraphStyle.BARS) {
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
                    onSelect(it)
                }
        }
    }
    val description = domain?.let {
        val minimum = if (fixedRange == null) it.minimum.toString() else formatGraphAxis(it.minimum)
        val maximum = if (fixedRange == null) it.maximum.toString() else formatGraphAxis(it.maximum)
        "Progress chart from ${formatGraphDate(dates.firstOrNull())} to ${formatGraphDate(dates.lastOrNull())}, $minimum to $maximum $unit"
    } ?: "Progress chart, no data"

    VibeSurface(VibeSurfaceLevel.INSET, modifier = modifier.fillMaxWidth()) {
        when (graphState) {
            VibeGraphState.Loading -> VibeSkeleton("Loading chart", Modifier.fillMaxWidth().height(178.dp))
            is VibeGraphState.Empty -> VibeStatePanel(graphState.message)
            is VibeGraphState.Error -> VibeStatePanel(graphState.message, isError = true)
            VibeGraphState.Data -> if (domain != null) {
                Column(Modifier.fillMaxWidth().padding(VibeSpacing.compact)) {
                    Text("${formatGraphAxis(domain.maximum)} $unit", style = typography.annotation)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(138.dp)
                            .onSizeChanged { plotSize = it }
                            .pointerInput(values) {
                                detectTapGestures { select(it.x, size.width.toFloat()) }
                            }
                            .pointerInput(values) {
                                detectDragGestures { change, _ ->
                                    select(change.position.x, size.width.toFloat())
                                }
                            }
                            .semantics {
                                contentDescription = description
                                selectedIndex.takeIf { it in values.indices }?.let {
                                    stateDescription = "${formatGraphDate(dates.getOrNull(it))}, ${formatGraphAxis(values[it])} $unit"
                                }
                                customActions = listOf(
                                    CustomAccessibilityAction("Previous data point") {
                                        val start = selectedIndex.takeIf { it in values.indices } ?: values.size
                                        val previous = (start - 1 downTo 0).firstOrNull { values[it].isFinite() }
                                        if (previous == null) false else {
                                            selectedIndex = previous
                                            onSelect(selectedIndex)
                                            true
                                        }
                                    },
                                    CustomAccessibilityAction("Next data point") {
                                        val next = ((selectedIndex + 1).coerceAtLeast(0)..values.lastIndex)
                                            .firstOrNull { values[it].isFinite() }
                                        if (next == null) false else {
                                            selectedIndex = next
                                            onSelect(selectedIndex)
                                            true
                                        }
                                    },
                                )
                            },
                    ) {
                        val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) }
                        TechnicalBackdrop(
                            Modifier.matchParentSize(),
                            mode = VibeBackdropMode.PERSPECTIVE,
                            enabled = visualStyle == VibeVisualStyle.RETRO_FUTURE,
                        ) {
                            Canvas(
                                Modifier
                                    .matchParentSize()
                                    .drawWithCache {
                                        val padding = 12.dp.toPx()
                                        val gridLines = graphGridLines(size.width, size.height, padding, visualStyle)
                                        val standardGrid = palette.textPrimary.copy(alpha = if (palette.isDark) 0.09f else 0.12f)
                                        val majorGrid = styleTokens.instrumentSignal.copy(alpha = if (palette.isDark) 0.34f else 0.28f)
                                        val minorGrid = styleTokens.instrumentSignal.copy(alpha = if (palette.isDark) 0.13f else 0.10f)
                                        val primaryPath = Path()
                                        var primaryStarted = false
                                        val primaryPoints = values.mapIndexedNotNull { index, value ->
                                            value.takeIf(Double::isFinite)?.let {
                                                if (style == VibeGraphStyle.BARS) {
                                                    graphBarPoint(index, it, values.size, size.width, size.height, domain, padding)
                                                } else {
                                                    graphPoint(index, it, values.size, size.width, size.height, domain, padding)
                                                }
                                            }
                                        }
                                        values.forEachIndexed { index, value ->
                                            if (!value.isFinite()) {
                                                primaryStarted = false
                                            } else {
                                                val point = graphPoint(index, value, values.size, size.width, size.height, domain, padding)
                                                if (primaryStarted) primaryPath.lineTo(point.x, point.y) else primaryPath.moveTo(point.x, point.y)
                                                primaryStarted = true
                                            }
                                        }
                                        val secondarySegments = secondaryValues.mapIndexedNotNull { index, value ->
                                            val previous = secondaryValues.getOrNull(index - 1)
                                            if (value != null && value.isFinite() && previous != null && previous.isFinite()) {
                                                graphPoint(index - 1, previous, values.size, size.width, size.height, domain, padding) to
                                                    graphPoint(index, value, values.size, size.width, size.height, domain, padding)
                                            } else null
                                        }
                                        onDrawBehind {
                                            gridLines.forEach { line ->
                                                val color = if (visualStyle == VibeVisualStyle.STANDARD) {
                                                    standardGrid
                                                } else if (line.major) {
                                                    majorGrid
                                                } else {
                                                    minorGrid
                                                }
                                                drawLine(color, line.start, line.end, if (line.major) 1.dp.toPx() else 0.5.dp.toPx())
                                            }
                                            if (visualStyle == VibeVisualStyle.RETRO_FUTURE) {
                                                val horizontal = gridLines.filter { it.major && it.start.x == padding }
                                                val vertical = gridLines.filter { it.major && it.start.y == padding }
                                                val hatch = 2.dp.toPx()
                                                horizontal.forEach { h ->
                                                    vertical.forEach { v ->
                                                        val cross = Offset(v.start.x, h.start.y)
                                                        drawLine(majorGrid, cross.copy(x = cross.x - hatch), cross.copy(x = cross.x + hatch), 1.dp.toPx())
                                                        drawLine(majorGrid, cross.copy(y = cross.y - hatch), cross.copy(y = cross.y + hatch), 1.dp.toPx())
                                                    }
                                                }
                                            }
                                            if (style == VibeGraphStyle.LINE) {
                                                if (visualStyle == VibeVisualStyle.RETRO_FUTURE) {
                                                    drawPath(
                                                        primaryPath,
                                                        palette.accent.copy(alpha = styleTokens.graphGlowAlpha),
                                                        style = Stroke(8.dp.toPx(), cap = StrokeCap.Round),
                                                    )
                                                }
                                                drawPath(primaryPath, palette.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                                                primaryPoints.forEach { point ->
                                                    if (visualStyle == VibeVisualStyle.RETRO_FUTURE) {
                                                        val radius = 3.dp.toPx()
                                                        drawLine(palette.accent, point.copy(x = point.x - radius), point.copy(x = point.x + radius), 1.5.dp.toPx())
                                                        drawLine(palette.accent, point.copy(y = point.y - radius), point.copy(y = point.y + radius), 1.5.dp.toPx())
                                                    } else {
                                                        drawCircle(palette.accent, 2.5.dp.toPx(), point)
                                                    }
                                                }
                                            } else {
                                                val slot = (size.width - padding * 2f) / values.size.coerceAtLeast(1)
                                                values.forEachIndexed { index, value ->
                                                    if (value.isFinite()) {
                                                        val point = graphBarPoint(index, value, values.size, size.width, size.height, domain, padding)
                                                        val barSize = Size(slot * 0.6f, size.height - padding - point.y)
                                                        if (visualStyle == VibeVisualStyle.RETRO_FUTURE) {
                                                            drawRect(
                                                                palette.accent.copy(alpha = styleTokens.graphGlowAlpha),
                                                                topLeft = Offset(point.x - slot * 0.36f, point.y - 2.dp.toPx()),
                                                                size = Size(slot * 0.72f, barSize.height + 2.dp.toPx()),
                                                            )
                                                        }
                                                        drawRect(palette.accent, Offset(point.x - slot * 0.3f, point.y), barSize)
                                                        if (visualStyle == VibeVisualStyle.RETRO_FUTURE) {
                                                            drawLine(
                                                                palette.onAccent,
                                                                Offset(point.x - slot * 0.18f, point.y),
                                                                Offset(point.x + slot * 0.18f, point.y),
                                                                1.dp.toPx(),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            secondarySegments.forEach { (from, to) ->
                                                drawLine(palette.secondary, from, to, 2.5.dp.toPx(), pathEffect = dashEffect)
                                            }
                                        }
                                    },
                            ) {
                                val index = selectedIndex
                                if (index in values.indices && values[index].isFinite()) {
                                    val padding = 12.dp.toPx()
                                    val point = if (style == VibeGraphStyle.BARS) {
                                        graphBarPoint(index, values[index], values.size, size.width, size.height, domain, padding)
                                    } else {
                                        graphPoint(index, values[index], values.size, size.width, size.height, domain, padding)
                                    }
                                    if (visualStyle == VibeVisualStyle.RETRO_FUTURE) {
                                        val reticle = styleTokens.instrumentSignal.copy(alpha = 0.74f)
                                        drawLine(reticle, Offset(point.x, padding), Offset(point.x, size.height - padding), 1.dp.toPx(), pathEffect = dashEffect)
                                        drawLine(reticle, Offset(padding, point.y), Offset(size.width - padding, point.y), 1.dp.toPx(), pathEffect = dashEffect)
                                        drawCircle(palette.accent.copy(alpha = styleTokens.graphGlowAlpha), 9.dp.toPx(), point)
                                        drawCircle(palette.accent, 6.dp.toPx(), point, style = Stroke(1.5.dp.toPx()))
                                        drawCircle(palette.surfaceInset, 1.5.dp.toPx(), point)
                                    } else {
                                        drawCircle(palette.accent, 3.5.dp.toPx(), point)
                                        drawCircle(palette.surfaceInset, 1.dp.toPx(), point)
                                        drawLine(
                                            palette.textSecondary.copy(alpha = 0.6f),
                                            Offset(point.x, padding),
                                            Offset(point.x, size.height - padding),
                                            1.dp.toPx(),
                                            pathEffect = dashEffect,
                                        )
                                    }
                                }
                            }
                        }
                        selectedIndex.takeIf { it in values.indices && values[it].isFinite() }?.let { index ->
                            val linePoint = graphPoint(
                                index,
                                values[index],
                                values.size,
                                plotSize.width.toFloat(),
                                plotSize.height.toFloat(),
                                domain,
                                graphPaddingPx,
                            )
                            val selectedPoint = if (style == VibeGraphStyle.BARS) {
                                graphBarPoint(
                                    index,
                                    values[index],
                                    values.size,
                                    plotSize.width.toFloat(),
                                    plotSize.height.toFloat(),
                                    domain,
                                    graphPaddingPx,
                                )
                            } else {
                                linePoint
                            }
                            val placement = graphTooltipPlacement(
                                point = selectedPoint,
                                viewport = plotSize,
                                tooltip = tooltipSize,
                                gapPx = tooltipGapPx,
                            )
                            VibeGraphTooltip(
                                value = "${formatGraphAxis(values[index])} $unit",
                                date = formatGraphDate(dates.getOrNull(index)),
                                modifier = Modifier
                                    .offset { placement.offset }
                                    .onSizeChanged { tooltipSize = it },
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatGraphDate(dates.firstOrNull()), style = typography.annotation, modifier = Modifier.weight(1f))
                        Text(formatGraphDate(dates.lastOrNull()), style = typography.annotation, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                    }
                    Text("${formatGraphAxis(domain.minimum)} $unit", style = typography.annotation)
                }
            }
        }
    }
}

@Composable
fun VibeGraphTooltip(value: String, date: String, modifier: Modifier = Modifier) {
    val visualStyle = LocalVibeVisualStyle.current
    val typography = LocalVibeDashboardTypography.current
    VibeSurface(
        level = VibeSurfaceLevel.FLOATING,
        modifier = modifier
            .widthIn(max = 148.dp)
            .semantics { contentDescription = "Graph tooltip" },
        shape = RoundedCornerShape(VibeShapes.tooltip),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (visualStyle == VibeVisualStyle.RETRO_FUTURE) {
                Text(
                    "DATUM // LOCK",
                    color = LocalVibeStyleTokens.current.instrumentSignal,
                    style = typography.microLabel,
                )
            }
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(date, color = LocalVibePalette.current.textSecondary, style = typography.annotation)
        }
    }
}

internal fun formatGraphDate(value: Long?): String = value?.let {
    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
}.orEmpty()

internal fun formatGraphAxis(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else "%.1f".format(value)

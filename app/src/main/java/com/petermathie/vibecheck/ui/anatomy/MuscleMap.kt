package com.petermathie.vibecheck.ui.anatomy

import android.graphics.Region
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.petermathie.vibecheck.domain.model.AnatomySex
import com.petermathie.vibecheck.domain.model.MuscleRecencyBand
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.LocalVibeReducedMotion
import com.petermathie.vibecheck.ui.theme.LocalVibeStyleTokens
import com.petermathie.vibecheck.ui.theme.LocalVibeVisualStyle
import com.petermathie.vibecheck.ui.theme.VibeVisualStyle
import com.petermathie.vibecheck.ui.theme.freshnessColors
import com.petermathie.vibecheck.ui.theme.interpolateFreshnessBandColor
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.min

enum class AnatomyView { FRONT, BACK }

internal const val MUSCLE_COLOR_TRANSITION_MILLIS = 35
internal const val MUSCLE_MAP_HORIZONTAL_SCALE = 1.12f
internal const val MUSCLE_MAP_VERTICAL_STRETCH = 1.10f
internal const val MUSCLE_MAP_GLOW_OVERFLOW = 8f

private data class ParsedOutline(val def: OutlinePathDef, val path: Path)
private data class ParsedMuscle(val def: MusclePathDef, val path: Path, val region: Region)
internal data class MuscleMapTransform(
    val scaleX: Float,
    val scaleY: Float,
    val offsetX: Float,
    val offsetY: Float,
)

internal fun muscleMapTransform(
    canvasWidth: Float,
    canvasHeight: Float,
    contentLeft: Float,
    contentRight: Float,
    contentTop: Float,
    contentBottom: Float,
): MuscleMapTransform {
    val targetYScale = MUSCLE_MAP_HORIZONTAL_SCALE * MUSCLE_MAP_VERTICAL_STRETCH
    val fit = min(
        canvasWidth / ((contentRight - contentLeft) * MUSCLE_MAP_HORIZONTAL_SCALE),
        canvasHeight / ((contentBottom - contentTop) * targetYScale),
    )
    val scaleX = fit * MUSCLE_MAP_HORIZONTAL_SCALE
    val scaleY = fit * targetYScale
    return MuscleMapTransform(
        scaleX = scaleX,
        scaleY = scaleY,
        offsetX = canvasWidth / 2f - (contentLeft + contentRight) / 2f * scaleX,
        offsetY = canvasHeight / 2f - (contentTop + contentBottom) / 2f * scaleY,
    )
}

@Composable
fun MuscleMap(
    sex: AnatomySex,
    view: AnatomyView,
    states: Map<String, MuscleRecencyBand>,
    onMuscleTap: (String) -> Unit,
    modifier: Modifier = Modifier,
    selectedMuscleId: String? = null,
    nextStates: Map<String, MuscleRecencyBand>? = null,
    interpolationFraction: Float = 0f,
    directInterpolation: Boolean = false,
    celebratedMuscleIds: Set<String> = emptySet(),
    alignToLegendBounds: Boolean = false,
) {
    val palette = LocalVibePalette.current
    val freshnessColors = palette.freshnessColors()
    val reducedMotion = LocalVibeReducedMotion.current
    val visualStyle = LocalVibeVisualStyle.current
    val styleTokens = LocalVibeStyleTokens.current
    val diagram = when (sex to view) {
        AnatomySex.MALE to AnatomyView.FRONT -> MuscleDiagrams.MaleFront
        AnatomySex.MALE to AnatomyView.BACK -> MuscleDiagrams.MaleBack
        AnatomySex.FEMALE to AnatomyView.FRONT -> MuscleDiagrams.FemaleFront
        AnatomySex.FEMALE to AnatomyView.BACK -> MuscleDiagrams.FemaleBack
        else -> MuscleDiagrams.MaleFront
    }
    val outlines = remember(diagram.id) {
        diagram.outline.map { ParsedOutline(it, PathParser().parsePathString(it.pathData).toPath()) }
    }
    val outlineBounds = remember(outlines, diagram.centerX) {
        val pathBounds = outlines.map { it.def to it.path.getBounds() }
        val left = pathBounds.minOf { (definition, bounds) ->
            if (definition.side == BodySide.LEFT) minOf(bounds.left, 2f * diagram.centerX - bounds.right) else bounds.left
        }
        val right = pathBounds.maxOf { (definition, bounds) ->
            if (definition.side == BodySide.LEFT) maxOf(bounds.right, 2f * diagram.centerX - bounds.left) else bounds.right
        }
        androidx.compose.ui.geometry.Rect(
            left - MUSCLE_MAP_GLOW_OVERFLOW,
            pathBounds.minOf { it.second.top } - MUSCLE_MAP_GLOW_OVERFLOW,
            right + MUSCLE_MAP_GLOW_OVERFLOW,
            pathBounds.maxOf { it.second.bottom } + MUSCLE_MAP_GLOW_OVERFLOW,
        )
    }
    val muscles = remember(diagram.id) {
        diagram.muscles.map { definition ->
            val path = PathParser().parsePathString(definition.pathData).toPath()
            val clip = Region(0, 0, diagram.viewBoxWidth.toInt() + 1, diagram.viewBoxHeight.toInt() + 1)
            ParsedMuscle(definition, path, Region().apply { setPath(path.asAndroidPath(), clip) })
        }
    }
    val groups = muscles.map { it.def.group }.distinct()
    val selectedGroup = selectedMuscleId?.takeIf(groups::contains)
    val animatedColors = groups.associateWith { group ->
        val fromBand = states[group] ?: MuscleRecencyBand.NEVER
        val from = freshnessColors.forBand(fromBand)
        val targetColor = nextStates?.let {
            interpolateFreshnessBandColor(
                fromBand,
                it[group] ?: MuscleRecencyBand.NEVER,
                interpolationFraction,
                freshnessColors,
            )
        } ?: from
        val color by animateColorAsState(
            targetValue = targetColor,
            animationSpec = if (reducedMotion || directInterpolation) snap() else tween(durationMillis = MUSCLE_COLOR_TRANSITION_MILLIS),
            label = "freshness $group",
        )
        color
    }

    val canvasModifier = if (alignToLegendBounds) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxWidth()
            .aspectRatio(outlineBounds.width / (outlineBounds.height * MUSCLE_MAP_VERTICAL_STRETCH))
    }
    Canvas(
        modifier = canvasModifier
            .semantics {
                contentDescription = "${sex.name.lowercase()} ${view.name.lowercase()} freshness map"
                stateDescription = selectedGroup?.let { "Selected ${it.replace('_', ' ').lowercase()}" }
                    ?: "No muscle selected"
                customActions = groups.map { group ->
                    CustomAccessibilityAction("Inspect ${group.replace('_', ' ')}: ${(states[group] ?: MuscleRecencyBand.NEVER).name.lowercase().replace('_', ' ')}") {
                        onMuscleTap(group)
                        true
                    }
                }
            }
            .pointerInput(diagram.id, muscles) {
                detectTapGestures { tap ->
                    val transform = muscleMapTransform(
                        size.width.toFloat(),
                        size.height.toFloat(),
                        outlineBounds.left,
                        outlineBounds.right,
                        outlineBounds.top,
                        outlineBounds.bottom,
                    )
                    val vectorX = (tap.x - transform.offsetX) / transform.scaleX
                    val vectorY = (tap.y - transform.offsetY) / transform.scaleY
                    val hit = muscles.lastOrNull { item ->
                        val direct = item.region.contains(vectorX.toInt(), vectorY.toInt())
                        val mirroredX = 2f * diagram.centerX - vectorX
                        direct || (item.def.side == BodySide.LEFT && item.region.contains(mirroredX.toInt(), vectorY.toInt()))
                    }
                    hit?.def?.group?.let(onMuscleTap)
                }
            },
    ) {
        if (visualStyle == VibeVisualStyle.RETRO_FUTURE) {
            val signal = styleTokens.instrumentSignal
            val minor = signal.copy(alpha = if (palette.isDark) 0.08f else 0.06f)
            val major = signal.copy(alpha = if (palette.isDark) 0.2f else 0.15f)
            val spacing = (size.minDimension / 12f).coerceAtLeast(12f)
            var x = 0f
            var index = 0
            while (x <= size.width) {
                drawLine(if (index % 4 == 0) major else minor, Offset(x, 0f), Offset(x, size.height), 1f)
                x += spacing
                index++
            }
            var y = 0f
            index = 0
            while (y <= size.height) {
                drawLine(if (index % 4 == 0) major else minor, Offset(0f, y), Offset(size.width, y), 1f)
                y += spacing
                index++
            }
            val tick = (size.minDimension * 0.025f).coerceIn(4f, 10f)
            repeat(9) { marker ->
                val markerY = size.height * marker / 8f
                drawLine(major, Offset(0f, markerY), Offset(tick, markerY), 1f)
                drawLine(major, Offset(size.width - tick, markerY), Offset(size.width, markerY), 1f)
            }
            val reticleCentre = Offset(size.width / 2f, size.height * 0.44f)
            val reticleRadius = size.minDimension * 0.22f
            drawCircle(major, reticleRadius, reticleCentre, style = Stroke(1f))
            drawLine(major, reticleCentre.copy(x = reticleCentre.x - reticleRadius - tick), reticleCentre.copy(x = reticleCentre.x - reticleRadius + tick), 1f)
            drawLine(major, reticleCentre.copy(x = reticleCentre.x + reticleRadius - tick), reticleCentre.copy(x = reticleCentre.x + reticleRadius + tick), 1f)
            if (styleTokens.scannerSweepEnabled) {
                val sweepY = size.height * 0.36f
                drawLine(signal.copy(alpha = if (palette.isDark) 0.18f else 0.12f), Offset(0f, sweepY), Offset(size.width, sweepY), 1f)
            }
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(
                    (0.72f * 255).toInt(),
                    (signal.red * 255).toInt(),
                    (signal.green * 255).toInt(),
                    (signal.blue * 255).toInt(),
                )
                textSize = (size.minDimension * 0.075f).coerceIn(10f, 22f)
                typeface = android.graphics.Typeface.MONOSPACE
                isAntiAlias = true
            }
            drawContext.canvas.nativeCanvas.drawText(
                if (view == AnatomyView.FRONT) "F" else "B",
                tick,
                paint.textSize + tick,
                paint,
            )
        }
        val transform = muscleMapTransform(
            size.width,
            size.height,
            outlineBounds.left,
            outlineBounds.right,
            outlineBounds.top,
            outlineBounds.bottom,
        )
        withTransform({
            translate(transform.offsetX, transform.offsetY)
            scale(transform.scaleX, transform.scaleY, Offset.Zero)
        }) {
            if (visualStyle == VibeVisualStyle.RETRO_FUTURE) {
                outlines.forEach { item ->
                    drawPathWithMirror(
                        item.path,
                        item.def.side,
                        diagram.centerX,
                        Color.Transparent,
                        styleTokens.instrumentSignal.copy(alpha = if (palette.isDark) 0.13f else 0.09f),
                        strokeWidth = 7f,
                    )
                    drawPathWithMirror(
                        item.path,
                        item.def.side,
                        diagram.centerX,
                        Color.Transparent,
                        styleTokens.instrumentSignal.copy(alpha = if (palette.isDark) 0.72f else 0.58f),
                        strokeWidth = 1.2f,
                    )
                }
            }
            outlines.forEach { item ->
                drawPathWithMirror(item.path, item.def.side, diagram.centerX, Color.Transparent, palette.diagramLine)
            }
            muscles.forEach { item ->
                val color = animatedColors.getValue(item.def.group)
                val selected = item.def.group == selectedMuscleId
                if (item.def.group in celebratedMuscleIds) {
                    drawPathWithMirror(
                        path = item.path,
                        side = item.def.side,
                        centerX = diagram.centerX,
                        color = Color.Transparent,
                        strokeColor = color.copy(alpha = if (palette.isDark) 0.24f else 0.14f),
                        strokeWidth = 12f,
                    )
                    drawPathWithMirror(
                        path = item.path,
                        side = item.def.side,
                        centerX = diagram.centerX,
                        color = Color.Transparent,
                        strokeColor = color,
                        strokeWidth = 1f,
                    )
                }
                drawPathWithMirror(
                    path = item.path,
                    side = item.def.side,
                    centerX = diagram.centerX,
                    color = color,
                    strokeColor = if (selected) freshnessColors.selection else color,
                    strokeWidth = if (selected) 4f else 2.5f,
                )
                if (selected && visualStyle == VibeVisualStyle.RETRO_FUTURE) {
                    drawSelectionReticle(
                        bounds = item.path.getBounds(),
                        side = item.def.side,
                        centerX = diagram.centerX,
                        color = styleTokens.instrumentSignal,
                    )
                }
            }
        }
    }

}

private fun DrawScope.drawSelectionReticle(
    bounds: androidx.compose.ui.geometry.Rect,
    side: BodySide,
    centerX: Float,
    color: Color,
) {
    fun drawCurrent() {
        val gap = 3f
        val tick = 7f
        val left = bounds.left - gap
        val right = bounds.right + gap
        val top = bounds.top - gap
        val bottom = bounds.bottom + gap
        drawLine(color, Offset(left, top), Offset(left + tick, top), 1.5f)
        drawLine(color, Offset(left, top), Offset(left, top + tick), 1.5f)
        drawLine(color, Offset(right, top), Offset(right - tick, top), 1.5f)
        drawLine(color, Offset(right, top), Offset(right, top + tick), 1.5f)
        drawLine(color, Offset(left, bottom), Offset(left + tick, bottom), 1.5f)
        drawLine(color, Offset(left, bottom), Offset(left, bottom - tick), 1.5f)
        drawLine(color, Offset(right, bottom), Offset(right - tick, bottom), 1.5f)
        drawLine(color, Offset(right, bottom), Offset(right, bottom - tick), 1.5f)
    }
    drawCurrent()
    if (side == BodySide.LEFT) {
        withTransform({ scale(-1f, 1f, Offset(centerX, 0f)) }) { drawCurrent() }
    }
}

private fun DrawScope.drawPathWithMirror(
    path: Path,
    side: BodySide,
    centerX: Float,
    color: Color,
    strokeColor: Color,
    strokeWidth: Float = 1f,
) {
    fun drawCurrent() {
        drawPath(path, color)
        drawPath(path, strokeColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
    drawCurrent()
    if (side == BodySide.LEFT) {
        withTransform({ scale(-1f, 1f, Offset(centerX, 0f)) }) { drawCurrent() }
    }
}

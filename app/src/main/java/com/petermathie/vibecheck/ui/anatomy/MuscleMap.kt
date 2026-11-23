package com.petermathie.vibecheck.ui.anatomy

import android.graphics.Region
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import com.petermathie.vibecheck.domain.model.AnatomySex
import com.petermathie.vibecheck.domain.model.MuscleRecencyBand
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.LocalVibeReducedMotion
import com.petermathie.vibecheck.ui.theme.freshnessColors
import com.petermathie.vibecheck.ui.theme.interpolateFreshnessBandColor
import kotlin.math.ceil
import kotlin.math.min

enum class AnatomyView { FRONT, BACK }

internal const val MUSCLE_COLOR_TRANSITION_MILLIS = 35
internal const val MUSCLE_MAP_HORIZONTAL_SCALE = 1.12f
internal const val MUSCLE_MAP_VERTICAL_STRETCH = 1.10f
internal const val MUSCLE_MAP_GLOW_OVERFLOW = 8f
internal const val MUSCLE_MAP_VERTICAL_FILL = 0.9f

private data class ParsedOutline(val def: OutlinePathDef, val path: Path)
private data class ParsedMuscle(val def: MusclePathDef, val path: Path, val region: Region)
internal data class MuscleMapBounds(val outline: Rect, val muscles: Rect)
private data class ParsedDiagram(
    val definition: MuscleDiagramDef,
    val outlines: List<ParsedOutline>,
    val muscles: List<ParsedMuscle>,
    val bounds: MuscleMapBounds,
)

private fun pathBounds(paths: List<Pair<BodySide, Path>>, centerX: Float): Rect {
    val bounds = paths.map { (side, path) -> side to path.getBounds() }
    return Rect(
        bounds.minOf { (side, rect) -> if (side == BodySide.LEFT) minOf(rect.left, 2f * centerX - rect.right) else rect.left } - MUSCLE_MAP_GLOW_OVERFLOW,
        bounds.minOf { it.second.top } - MUSCLE_MAP_GLOW_OVERFLOW,
        bounds.maxOf { (side, rect) -> if (side == BodySide.LEFT) maxOf(rect.right, 2f * centerX - rect.left) else rect.right } + MUSCLE_MAP_GLOW_OVERFLOW,
        bounds.maxOf { it.second.bottom } + MUSCLE_MAP_GLOW_OVERFLOW,
    )
}

@Composable
private fun rememberParsedDiagram(sex: AnatomySex, view: AnatomyView): ParsedDiagram = remember(sex, view) {
    val diagram = when (sex to view) {
        AnatomySex.MALE to AnatomyView.FRONT -> MuscleDiagrams.MaleFront
        AnatomySex.MALE to AnatomyView.BACK -> MuscleDiagrams.MaleBack
        AnatomySex.FEMALE to AnatomyView.FRONT -> MuscleDiagrams.FemaleFront
        AnatomySex.FEMALE to AnatomyView.BACK -> MuscleDiagrams.FemaleBack
        else -> MuscleDiagrams.MaleFront
    }
    val outlines = diagram.outline.map { ParsedOutline(it, PathParser().parsePathString(it.pathData).toPath()) }
    val muscles = diagram.muscles.map { definition ->
        val path = PathParser().parsePathString(definition.pathData).toPath()
        val clip = Region(0, 0, diagram.viewBoxWidth.toInt() + 1, diagram.viewBoxHeight.toInt() + 1)
        ParsedMuscle(definition, path, Region().apply { setPath(path.asAndroidPath(), clip) })
    }
    ParsedDiagram(
        diagram,
        outlines,
        muscles,
        MuscleMapBounds(
            pathBounds(outlines.map { it.def.side to it.path }, diagram.centerX),
            pathBounds(muscles.map { it.def.side to it.path }, diagram.centerX),
        ),
    )
}

@Composable
internal fun rememberMuscleMapBounds(sex: AnatomySex, view: AnatomyView): MuscleMapBounds =
    rememberParsedDiagram(sex, view).bounds

internal fun croppedMuscleMapHeight(bounds: MuscleMapBounds, width: Int, referenceHeight: Float): Int {
    val transform = muscleMapTransform(
        width.toFloat(), referenceHeight,
        bounds.outline.left, bounds.outline.right, bounds.outline.top, bounds.outline.bottom,
    )
    return ceil(bounds.muscles.height * transform.scaleY).toInt()
}
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
    visibleTop: Float = contentTop,
    visibleBottom: Float = contentBottom,
    referenceHeight: Float = canvasHeight,
    stretchVertically: Boolean = false,
): MuscleMapTransform {
    val targetYScale = MUSCLE_MAP_HORIZONTAL_SCALE * MUSCLE_MAP_VERTICAL_STRETCH
    val fit = min(
        canvasWidth / ((contentRight - contentLeft) * MUSCLE_MAP_HORIZONTAL_SCALE),
        referenceHeight / ((contentBottom - contentTop) * targetYScale),
    )
    val scaleX = fit * MUSCLE_MAP_HORIZONTAL_SCALE
    val scaleY = if (stretchVertically) {
        canvasHeight * MUSCLE_MAP_VERTICAL_FILL / (visibleBottom - visibleTop)
    } else {
        fit * targetYScale
    }
    return MuscleMapTransform(
        scaleX = scaleX,
        scaleY = scaleY,
        offsetX = canvasWidth / 2f - (contentLeft + contentRight) / 2f * scaleX,
        offsetY = canvasHeight / 2f - (visibleTop + visibleBottom) / 2f * scaleY,
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
    musclesOnly: Boolean = false,
    referenceCanvasHeight: Dp = Dp.Infinity,
    onMuscleDoubleTap: ((String) -> Unit)? = null,
) {
    val selectMuscle by rememberUpdatedState(onMuscleTap)
    val inspectMuscle by rememberUpdatedState(onMuscleDoubleTap)
    val palette = LocalVibePalette.current
    val freshnessColors = palette.freshnessColors()
    val reducedMotion = LocalVibeReducedMotion.current
    val parsed = rememberParsedDiagram(sex, view)
    val diagram = parsed.definition
    val outlines = parsed.outlines
    val muscles = parsed.muscles
    val outlineBounds = parsed.bounds.outline
    val visibleBounds = if (musclesOnly) parsed.bounds.muscles else outlineBounds
    val referenceHeightPx = with(LocalDensity.current) { referenceCanvasHeight.toPx() }
    fun transform(width: Float, height: Float) = muscleMapTransform(
        width, height,
        outlineBounds.left, outlineBounds.right, outlineBounds.top, outlineBounds.bottom,
        visibleTop = visibleBounds.top,
        visibleBottom = visibleBounds.bottom,
        referenceHeight = if (musclesOnly) referenceHeightPx else height,
        stretchVertically = musclesOnly,
    )
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
            .aspectRatio(outlineBounds.width / (visibleBounds.height * MUSCLE_MAP_VERTICAL_STRETCH))
    }
    Canvas(
        modifier = canvasModifier
            .semantics {
                contentDescription = "${sex.name.lowercase()} ${view.name.lowercase()} freshness map"
                stateDescription = selectedGroup?.let { "Selected ${it.replace('_', ' ').lowercase()}" }
                    ?: "No muscle selected"
                customActions = groups.map { group ->
                    CustomAccessibilityAction("Inspect ${group.replace('_', ' ')}: ${(states[group] ?: MuscleRecencyBand.NEVER).name.lowercase().replace('_', ' ')}") {
                        (inspectMuscle ?: selectMuscle)(group)
                        true
                    }
                }
            }
            .pointerInput(diagram.id, muscles, musclesOnly, referenceHeightPx, onMuscleDoubleTap != null) {
                fun muscleAt(tap: Offset): String? {
                    val transform = transform(size.width.toFloat(), size.height.toFloat())
                    val vectorX = (tap.x - transform.offsetX) / transform.scaleX
                    val vectorY = (tap.y - transform.offsetY) / transform.scaleY
                    val hit = muscles.lastOrNull { item ->
                        val direct = item.region.contains(vectorX.toInt(), vectorY.toInt())
                        val mirroredX = 2f * diagram.centerX - vectorX
                        direct || (item.def.side == BodySide.LEFT && item.region.contains(mirroredX.toInt(), vectorY.toInt()))
                    }
                    return hit?.def?.group
                }
                var firstPress: Offset? = null
                detectTapGestures(
                    onPress = { tap ->
                        if (inspectMuscle != null) {
                            if (firstPress == null) firstPress = tap
                            muscleAt(tap)?.let(selectMuscle)
                            if (!tryAwaitRelease()) firstPress = null
                        }
                    },
                    onTap = { tap ->
                        if (inspectMuscle == null) muscleAt(tap)?.let(selectMuscle)
                        firstPress = null
                    },
                    onDoubleTap = if (onMuscleDoubleTap == null) null else { tap ->
                        val muscle = muscleAt(tap)
                        if (muscle != null && firstPress?.let(::muscleAt) == muscle) inspectMuscle?.invoke(muscle)
                        firstPress = null
                    },
                )
            },
    ) {
        val transform = transform(size.width, size.height)
        withTransform({
            translate(transform.offsetX, transform.offsetY)
            scale(transform.scaleX, transform.scaleY, Offset.Zero)
        }) {
            if (!musclesOnly) outlines.forEach { item ->
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
            }
        }
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

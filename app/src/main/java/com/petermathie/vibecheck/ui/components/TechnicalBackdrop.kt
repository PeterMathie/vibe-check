package com.petermathie.vibecheck.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.LocalVibeStyleTokens
import com.petermathie.vibecheck.ui.theme.LocalVibeVisualStyle
import com.petermathie.vibecheck.ui.theme.VibeBackdropMode
import com.petermathie.vibecheck.ui.theme.VibeVisualStyle
import kotlin.math.max

@Composable
fun TechnicalBackdrop(
    modifier: Modifier = Modifier,
    mode: VibeBackdropMode = VibeBackdropMode.ORTHOGRAPHIC,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalVibePalette.current
    val visualStyle = LocalVibeVisualStyle.current
    val tokens = LocalVibeStyleTokens.current
    Box(
        modifier.drawWithCache {
            val standard = visualStyle == VibeVisualStyle.STANDARD
            val majorSpacing = if (standard) 32.dp.toPx() else max(tokens.backdropMajorSpacing.toPx(), 28.dp.toPx())
            val minorSpacing = majorSpacing / tokens.backdropMinorDivisions.coerceAtLeast(1)
            val stroke = 1f
            val major = if (standard) {
                palette.textPrimary.copy(alpha = if (palette.isDark) 0.025f else 0.02f)
            } else {
                tokens.instrumentSignal.copy(alpha = tokens.backdropAlpha)
            }
            val minor = tokens.instrumentSignal.copy(alpha = tokens.backdropAlpha * 0.36f)
            onDrawBehind {
                if (!enabled) return@onDrawBehind
                if (standard || mode == VibeBackdropMode.ORTHOGRAPHIC) {
                    var x = 0f
                    var index = 0
                    while (x <= size.width) {
                        drawLine(if (index % tokens.backdropMinorDivisions == 0) major else minor, Offset(x, 0f), Offset(x, size.height), stroke)
                        x += minorSpacing
                        index++
                    }
                    var y = 0f
                    index = 0
                    while (y <= size.height) {
                        drawLine(if (index % tokens.backdropMinorDivisions == 0) major else minor, Offset(0f, y), Offset(size.width, y), stroke)
                        y += minorSpacing
                        index++
                    }
                } else {
                    val horizon = size.height * 0.18f
                    val centre = size.width / 2f
                    val edgeStep = majorSpacing * 0.75f
                    var bottomX = -edgeStep
                    while (bottomX <= size.width + edgeStep) {
                        drawLine(minor, Offset(centre, horizon), Offset(bottomX, size.height), stroke)
                        bottomX += edgeStep
                    }
                    var fraction = 0f
                    repeat(8) { row ->
                        fraction += (1f - fraction) * 0.27f
                        val y = horizon + (size.height - horizon) * fraction
                        drawLine(if (row % 2 == 1) major else minor, Offset(0f, y), Offset(size.width, y), stroke)
                    }
                }
            }
        },
        content = content,
    )
}

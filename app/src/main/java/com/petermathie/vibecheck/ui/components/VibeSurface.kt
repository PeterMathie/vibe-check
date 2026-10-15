package com.petermathie.vibecheck.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.ui.theme.LocalVibeMotion
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.LocalVibeReducedMotion
import com.petermathie.vibecheck.ui.theme.LocalVibeSurfaceLevel
import com.petermathie.vibecheck.ui.theme.VibeElevations
import com.petermathie.vibecheck.ui.theme.VibeShapes
import com.petermathie.vibecheck.ui.theme.VibeSurfaceLevel
import com.petermathie.vibecheck.ui.theme.VibeSurfaceState

@Composable
fun VibeSurface(
    level: VibeSurfaceLevel,
    modifier: Modifier = Modifier,
    state: VibeSurfaceState = VibeSurfaceState.RESTING,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    shape: Shape? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = LocalVibePalette.current
    val reducedMotion = LocalVibeReducedMotion.current
    val motion = LocalVibeMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val pressed by interactionSource.collectIsPressedAsState()
    val resolvedState = when {
        !enabled -> VibeSurfaceState.DISABLED
        pressed -> VibeSurfaceState.PRESSED
        else -> state
    }
    val elevation = VibeElevations.resolve(level, resolvedState, palette.isDark)
    val shadow by animateDpAsState(
        elevation.shadow,
        if (reducedMotion) snap() else tween(if (pressed) motion.pressInMillis else motion.pressOutMillis),
        label = "surface shadow",
    )
    val resolvedShape = shape ?: RoundedCornerShape(
        when (level) {
            VibeSurfaceLevel.PAGE, VibeSurfaceLevel.INSET -> VibeShapes.control
            VibeSurfaceLevel.FLOATING, VibeSurfaceLevel.MODAL -> VibeShapes.panel
            else -> VibeShapes.card
        },
    )
    val fill = when (level) {
        VibeSurfaceLevel.PAGE -> palette.background
        VibeSurfaceLevel.INSET -> palette.surfaceInset
        VibeSurfaceLevel.CARD -> palette.surface
        VibeSurfaceLevel.RAISED -> palette.surfaceRaised
        VibeSurfaceLevel.SELECTED -> palette.surfaceSelected
        VibeSurfaceLevel.FLOATING -> palette.surfaceFloating
        VibeSurfaceLevel.MODAL -> palette.surfaceModal
    }
    val borderColor = if (level == VibeSurfaceLevel.SELECTED || state == VibeSurfaceState.SELECTED) {
        palette.accent.copy(alpha = elevation.borderAlpha)
    } else {
        palette.textPrimary.copy(alpha = elevation.borderAlpha)
    }
    var focused by remember { mutableStateOf(false) }
    val interactive = if (onClick == null) Modifier else Modifier
        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
        .clickable(
            interactionSource = interactionSource,
            indication = indication,
            enabled = enabled,
            onClick = onClick,
        )
        .onFocusChanged { focused = it.isFocused }
    Box(
        modifier
            .graphicsLayer {
                shadowElevation = shadow.toPx()
                this.shape = resolvedShape
                clip = true
                ambientShadowColor = Color.Black.copy(alpha = if (palette.isDark) 0.24f else 0.18f)
                spotShadowColor = Color.Black.copy(alpha = if (palette.isDark) 0.38f else 0.28f)
                translationY = if (pressed && !reducedMotion) 1.dp.toPx() else 0f
            }
            .background(fill)
            .border(
                BorderStroke(if (focused) 2.dp else 1.dp, if (focused) palette.focusRing else borderColor),
                resolvedShape,
            )
            .then(interactive)
            .semantics {
                if (!enabled) disabled()
                if (state == VibeSurfaceState.SELECTED) selected = true
            },
    ) {
        CompositionLocalProvider(
            LocalVibeSurfaceLevel provides level,
            LocalContentColor provides if (enabled) palette.textPrimary else palette.textSecondary,
        ) {
            content()
        }
    }
}

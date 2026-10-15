package com.petermathie.vibecheck.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class VibeSurfaceLevel { PAGE, INSET, CARD, RAISED, SELECTED, FLOATING, MODAL }

enum class VibeSurfaceState { RESTING, PRESSED, DRAGGED, SELECTED, DISABLED }

@Immutable
data class VibeElevation(
    val shadow: Dp,
    val borderAlpha: Float,
)

object VibeElevations {
    fun resolve(level: VibeSurfaceLevel, state: VibeSurfaceState, dark: Boolean): VibeElevation {
        if (state == VibeSurfaceState.DISABLED) return VibeElevation(0.dp, 0.06f)
        if (state == VibeSurfaceState.PRESSED) return VibeElevation(0.dp, 0.14f)
        if (state == VibeSurfaceState.DRAGGED) return VibeElevation(8.dp, 0.55f)
        return when (level) {
            VibeSurfaceLevel.PAGE -> VibeElevation(0.dp, 0f)
            VibeSurfaceLevel.INSET -> VibeElevation(0.dp, if (dark) 0.05f else 0.08f)
            VibeSurfaceLevel.CARD -> VibeElevation(0.dp, if (dark) 0.09f else 0.11f)
            VibeSurfaceLevel.RAISED -> VibeElevation(1.dp, 0.12f)
            VibeSurfaceLevel.SELECTED -> VibeElevation(2.dp, if (dark) 0.55f else 0.65f)
            VibeSurfaceLevel.FLOATING -> VibeElevation(10.dp, 0.14f)
            VibeSurfaceLevel.MODAL -> VibeElevation(16.dp, 0.16f)
        }
    }
}

val LocalVibeSurfaceLevel = staticCompositionLocalOf { VibeSurfaceLevel.PAGE }

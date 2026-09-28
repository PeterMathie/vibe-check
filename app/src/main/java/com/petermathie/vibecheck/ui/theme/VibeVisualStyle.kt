package com.petermathie.vibecheck.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class VibeVisualStyle(val id: String, val displayName: String) {
    STANDARD("standard", "Standard"),
    RETRO_FUTURE("retro_future", "Retro Futuristic");

    companion object {
        fun fromPreference(value: String?): VibeVisualStyle =
            entries.firstOrNull { it.id == value } ?: STANDARD
    }
}

enum class VibeBackdropMode {
    ORTHOGRAPHIC,
    PERSPECTIVE,
}

@Immutable
data class VibeStyleTokens(
    val clippedCorners: Boolean,
    val cornerCut: Dp,
    val borderWidth: Dp,
    val instrumentSignal: Color,
    val backdropMajorSpacing: Dp,
    val backdropMinorDivisions: Int,
    val backdropAlpha: Float,
    val graphMajorDivisionsX: Int,
    val graphMajorDivisionsY: Int,
    val graphMinorDivisions: Int,
    val graphGlowAlpha: Float,
    val showInstrumentDetails: Boolean,
    val scannerSweepEnabled: Boolean,
)

object VibeVisualStyles {
    private val RetroSignalDark = Color(0xFF78F5A2)
    private val RetroSignalLight = Color(0xFF0A6B39)

    fun resolve(
        style: VibeVisualStyle,
        palette: VibePalette,
        reducedMotion: Boolean,
    ): VibeStyleTokens = when (style) {
        VibeVisualStyle.STANDARD -> VibeStyleTokens(
            clippedCorners = false,
            cornerCut = 0.dp,
            borderWidth = 1.dp,
            instrumentSignal = palette.textPrimary,
            backdropMajorSpacing = 32.dp,
            backdropMinorDivisions = 1,
            backdropAlpha = if (palette.isDark) 0.025f else 0.02f,
            graphMajorDivisionsX = 3,
            graphMajorDivisionsY = 4,
            graphMinorDivisions = 1,
            graphGlowAlpha = 0f,
            showInstrumentDetails = false,
            scannerSweepEnabled = false,
        )

        VibeVisualStyle.RETRO_FUTURE -> VibeStyleTokens(
            clippedCorners = true,
            cornerCut = 8.dp,
            borderWidth = 1.dp,
            instrumentSignal = if (palette.isDark) RetroSignalDark else RetroSignalLight,
            backdropMajorSpacing = 40.dp,
            backdropMinorDivisions = 4,
            backdropAlpha = if (palette.isDark) 0.075f else 0.055f,
            graphMajorDivisionsX = 4,
            graphMajorDivisionsY = 4,
            graphMinorDivisions = 4,
            graphGlowAlpha = if (palette.isDark) 0.16f else 0.09f,
            showInstrumentDetails = true,
            scannerSweepEnabled = !reducedMotion,
        )
    }

    fun dashboardTypography(style: VibeVisualStyle): DashboardTypography =
        if (style == VibeVisualStyle.STANDARD) {
            VibeDashboardTypography
        } else {
            DashboardTypography(
                metricXL = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 40.sp,
                    lineHeight = 44.sp,
                    letterSpacing = 0.4.sp,
                    fontFeatureSettings = "tnum",
                ),
                metric = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    letterSpacing = 0.3.sp,
                    fontFeatureSettings = "tnum",
                ),
                metricCompact = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    letterSpacing = 0.2.sp,
                    fontFeatureSettings = "tnum",
                ),
                label = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.8.sp,
                ),
                microLabel = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    letterSpacing = 1.sp,
                ),
                body = VibeDashboardTypography.body,
                annotation = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.35.sp,
                    fontFeatureSettings = "tnum",
                ),
            )
        }
}

val LocalVibeVisualStyle = staticCompositionLocalOf { VibeVisualStyle.STANDARD }
val LocalVibeStyleTokens = staticCompositionLocalOf {
    VibeVisualStyles.resolve(
        style = VibeVisualStyle.STANDARD,
        palette = VibePalettes.Ocean.dark,
        reducedMotion = false,
    )
}
val LocalVibeDashboardTypography = staticCompositionLocalOf { VibeDashboardTypography }

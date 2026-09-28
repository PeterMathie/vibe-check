package com.petermathie.vibecheck.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VibeVisualStyleTest {
    @Test
    fun preferenceParserDefaultsUnknownAndMissingValuesToStandard() {
        assertEquals(VibeVisualStyle.STANDARD, VibeVisualStyle.fromPreference(null))
        assertEquals(VibeVisualStyle.STANDARD, VibeVisualStyle.fromPreference(""))
        assertEquals(VibeVisualStyle.STANDARD, VibeVisualStyle.fromPreference("future"))
        assertEquals(VibeVisualStyle.STANDARD, VibeVisualStyle.fromPreference("standard"))
        assertEquals(VibeVisualStyle.RETRO_FUTURE, VibeVisualStyle.fromPreference("retro_future"))
    }

    @Test
    fun standardTokensRetainExistingGeometryAndEffects() {
        val tokens = VibeVisualStyles.resolve(
            VibeVisualStyle.STANDARD,
            VibePalettes.Ocean.dark,
            reducedMotion = false,
        )
        assertFalse(tokens.clippedCorners)
        assertEquals(1.dp, tokens.borderWidth)
        assertEquals(32.dp, tokens.backdropMajorSpacing)
        assertEquals(0f, tokens.graphGlowAlpha)
        assertFalse(tokens.showInstrumentDetails)
        assertFalse(tokens.scannerSweepEnabled)
        assertEquals(VibeDashboardTypography, VibeVisualStyles.dashboardTypography(VibeVisualStyle.STANDARD))
    }

    @Test
    fun retroTokensStayIndependentAcrossEveryPaletteAndAppearance() {
        VibePalettes.presets.flatMap { listOf(it.light, it.dark) }.forEach { palette ->
            val normal = VibeVisualStyles.resolve(VibeVisualStyle.RETRO_FUTURE, palette, reducedMotion = false)
            val reduced = VibeVisualStyles.resolve(VibeVisualStyle.RETRO_FUTURE, palette, reducedMotion = true)
            assertTrue(normal.clippedCorners)
            assertTrue(normal.showInstrumentDetails)
            assertTrue(normal.graphGlowAlpha in 0.01f..0.2f)
            assertTrue(normal.scannerSweepEnabled)
            assertFalse(reduced.scannerSweepEnabled)
            assertEquals(normal.instrumentSignal, reduced.instrumentSignal)
            assertContrast(normal.instrumentSignal, palette.surfaceInset, 3.0)
            assertContrast(normal.instrumentSignal, palette.background, 3.0)
        }
        assertEquals(
            FontFamily.Monospace,
            VibeVisualStyles.dashboardTypography(VibeVisualStyle.RETRO_FUTURE).metric.fontFamily,
        )
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Double) {
        val lighter = maxOf(foreground.luminance(), background.luminance()).toDouble()
        val darker = minOf(foreground.luminance(), background.luminance()).toDouble()
        assertTrue(
            "Contrast ${(lighter + 0.05) / (darker + 0.05)} is below $minimum",
            (lighter + 0.05) / (darker + 0.05) >= minimum,
        )
    }
}

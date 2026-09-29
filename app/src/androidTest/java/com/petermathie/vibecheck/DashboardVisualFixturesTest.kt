package com.petermathie.vibecheck

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.ui.DashboardTokenPreview
import com.petermathie.vibecheck.ui.DashboardVisualFixtures
import com.petermathie.vibecheck.ui.components.VibeGraph
import com.petermathie.vibecheck.ui.components.VibeSurface
import com.petermathie.vibecheck.ui.Destination
import com.petermathie.vibecheck.ui.PrimaryNavigationBar
import com.petermathie.vibecheck.ui.anatomy.AnatomyView
import com.petermathie.vibecheck.ui.anatomy.MuscleMap
import com.petermathie.vibecheck.domain.model.AnatomySex
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import com.petermathie.vibecheck.ui.theme.VibePalettes
import com.petermathie.vibecheck.ui.theme.VibeSurfaceLevel
import com.petermathie.vibecheck.ui.theme.VibeSurfaceState
import com.petermathie.vibecheck.ui.theme.VibeVisualStyle
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DashboardVisualFixturesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyPaletteModeHasADeterministicTokenPreview() {
        val activePalette = mutableStateOf(DashboardVisualFixtures.palettes.first())
        compose.setContent { DashboardTokenPreview(activePalette.value) }
        DashboardVisualFixtures.palettes.forEach { palette ->
            compose.runOnIdle { activePalette.value = palette }
            compose.onNodeWithContentDescription(
                "${palette.displayName} ${if (palette.isDark) "dark" else "light"} dashboard tokens",
            ).assertIsDisplayed()
        }
    }

    @Test
    fun sharedSurfaceExposesSelectedAndDisabledSemantics() {
        compose.setContent {
            VibeCheckTheme {
                VibeSurface(
                    level = VibeSurfaceLevel.SELECTED,
                    state = VibeSurfaceState.SELECTED,
                    enabled = false,
                    onClick = {},
                ) {
                    Text("Selected fixture")
                }
            }
        }
        compose.onNodeWithText("Selected fixture")
            .assertIsSelected()
            .assertIsNotEnabled()
    }

    @Test
    fun sharedSurfaceClipsTopHighlightToItsRoundedShape() {
        val palette = VibePalettes.Mono.dark
        var insetPx = 0
        compose.setContent {
            val density = LocalDensity.current
            insetPx = with(density) { 10.dp.roundToPx() }
            VibeCheckTheme(palette) {
                Box(
                    Modifier
                        .size(140.dp, 100.dp)
                        .background(palette.background)
                        .semantics { contentDescription = "Clipped surface fixture" },
                ) {
                    VibeSurface(
                        level = VibeSurfaceLevel.CARD,
                        modifier = Modifier.size(120.dp, 80.dp).align(Alignment.Center),
                    ) {}
                }
            }
        }

        val pixels = compose.onNodeWithContentDescription("Clipped surface fixture").captureToImage().toPixelMap()
        assertEquals(palette.background.toArgb(), pixels[insetPx, insetPx].toArgb())
        assertEquals(palette.background.toArgb(), pixels[pixels.width - insetPx - 1, insetPx].toArgb())
    }

    @Test
    fun monoPreviewRemainsVisibleAt320DpAndTwoHundredPercentFontScale() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(320.dp)) {
                    DashboardTokenPreview(VibePalettes.Mono.dark)
                }
            }
        }

        val preview = compose.onNodeWithContentDescription("Mono dark dashboard tokens")
        preview.assertIsDisplayed()
        val bitmap = preview.captureToImage()
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }

    @Test
    fun graphHasMinimumAccessibleRegionAndDatumActions() {
        compose.setContent {
            VibeCheckTheme {
                VibeGraph(
                    values = listOf(1.0, 2.0, 1.5),
                    dates = listOf(1_700_000_000_000, 1_700_086_400_000, 1_700_172_800_000),
                    unit = "kg",
                )
            }
        }

        compose.onNodeWithContentDescription("Progress chart", substring = true)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun graphTooltipTracksEdgePointsAndStaysInsideAtTwoHundredPercentFontScale() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(320.dp)) {
                    VibeCheckTheme {
                        VibeGraph(
                            values = listOf(2.0, 8.0, 4.0),
                            secondaryValues = listOf(3.0, 6.0, 5.0),
                            dates = listOf(1_700_000_000_000, 1_700_086_400_000, 1_700_172_800_000),
                            unit = "kg",
                        )
                    }
                }
            }
        }
        val graph = compose.onNodeWithContentDescription("Progress chart", substring = true)
        fun selectNext() {
            val action = graph.fetchSemanticsNode().config[SemanticsActions.CustomActions]
                .first { it.label == "Next data point" }
            compose.runOnIdle { action.action() }
            compose.waitForIdle()
        }

        selectNext()
        val graphBounds = graph.fetchSemanticsNode().boundsInRoot
        val first = compose.onNodeWithContentDescription("Graph tooltip").fetchSemanticsNode().boundsInRoot
        assertTrue(first.left >= graphBounds.left && first.right <= graphBounds.right)
        assertTrue(first.top >= graphBounds.top && first.bottom <= graphBounds.bottom)

        selectNext()
        selectNext()
        val last = compose.onNodeWithContentDescription("Graph tooltip").fetchSemanticsNode().boundsInRoot
        assertTrue(last.left >= graphBounds.left && last.right <= graphBounds.right)
        assertTrue(last.top >= graphBounds.top && last.bottom <= graphBounds.bottom)
        assertTrue(first.center.x < graphBounds.center.x)
        assertTrue(last.center.x > graphBounds.center.x)
    }

    @Test
    fun bothVisualStylesRenderAcrossEveryPaletteAndAppearance() {
        val palettes = DashboardVisualFixtures.palettes
        val activePalette = mutableStateOf(palettes.first())
        val activeStyle = mutableStateOf(VibeVisualStyle.STANDARD)
        compose.setContent {
            VibeCheckTheme(activePalette.value, activeStyle.value) {
                Column {
                    VibeGraph(
                        values = listOf(1.0, 3.0, 2.0),
                        dates = listOf(1_700_000_000_000, 1_700_086_400_000, 1_700_172_800_000),
                        unit = "kg",
                    )
                    MuscleMap(
                        sex = AnatomySex.MALE,
                        view = AnatomyView.FRONT,
                        states = emptyMap(),
                        onMuscleTap = {},
                        modifier = Modifier.width(120.dp),
                    )
                }
            }
        }

        palettes.forEach { palette ->
            VibeVisualStyle.entries.forEach { style ->
                compose.runOnIdle {
                    activePalette.value = palette
                    activeStyle.value = style
                }
                compose.waitForIdle()
                compose.onNodeWithContentDescription("Progress chart", substring = true).assertIsDisplayed()
                compose.onNodeWithContentDescription("male front freshness map").assertIsDisplayed()
            }
        }
    }

    @Test
    fun retroTooltipUsesHudLabelWhileStandardDoesNot() {
        val style = mutableStateOf(VibeVisualStyle.STANDARD)
        compose.setContent {
            VibeCheckTheme(VibePalettes.Mono.dark, style.value) {
                VibeGraph(
                    values = listOf(1.0, 2.0),
                    dates = listOf(1_700_000_000_000, 1_700_086_400_000),
                    unit = "kg",
                )
            }
        }
        val graph = compose.onNodeWithContentDescription("Progress chart", substring = true)
        fun selectFirst() {
            val action = graph.fetchSemanticsNode().config[SemanticsActions.CustomActions]
                .first { it.label == "Next data point" }
            compose.runOnIdle { action.action() }
            compose.waitForIdle()
        }
        selectFirst()
        compose.onNodeWithText("DATUM // LOCK").assertDoesNotExist()
        compose.runOnIdle { style.value = VibeVisualStyle.RETRO_FUTURE }
        compose.waitForIdle()
        compose.onNodeWithText("DATUM // LOCK").assertIsDisplayed()
    }

    @Test
    fun narrowTwoHundredPercentNavigationUsesAccessibleIconsWithoutClippedLabels() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(320.dp)) {
                    VibeCheckTheme(VibePalettes.Mono.dark, VibeVisualStyle.RETRO_FUTURE) {
                        PrimaryNavigationBar(Destination.PROGRESS) {}
                    }
                }
            }
        }
        listOf("Home", "Plans", "Progress", "Habits", "Body", "More").forEach { label ->
            compose.onNodeWithContentDescription(label).assertIsDisplayed()
            compose.onNodeWithText(label).assertDoesNotExist()
        }
    }

    @Test
    fun retroDefaultNavigationKeepsProgressLabelOnOneLine() {
        var maximumLabelHeight = 0f
        compose.setContent {
            maximumLabelHeight = with(LocalDensity.current) { 24.dp.toPx() }
            Box(Modifier.width(411.dp)) {
                VibeCheckTheme(VibePalettes.Mono.dark, VibeVisualStyle.RETRO_FUTURE) {
                    PrimaryNavigationBar(Destination.PROGRESS) {}
                }
            }
        }
        val progress = compose.onNodeWithText("Progress", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(progress.fetchSemanticsNode().boundsInRoot.height <= maximumLabelHeight)
    }
}

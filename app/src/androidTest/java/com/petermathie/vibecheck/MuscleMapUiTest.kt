package com.petermathie.vibecheck

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.domain.model.AnatomySex
import com.petermathie.vibecheck.domain.model.MuscleRecencyBand
import com.petermathie.vibecheck.ui.anatomy.AnatomyView
import com.petermathie.vibecheck.ui.anatomy.FreshnessLegend
import com.petermathie.vibecheck.ui.anatomy.MuscleMap
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import com.petermathie.vibecheck.ui.theme.VibePalettes
import com.petermathie.vibecheck.ui.theme.VibeVisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class MuscleMapUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun allMapVariantsExposeRegionsAndSelectionToAccessibility() {
        var selected = mutableStateOf<String?>(null)
        compose.setContent {
            selected = remember { mutableStateOf(null) }
            VibeCheckTheme {
                Column {
                    AnatomySex.entries.forEach { sex ->
                        AnatomyView.entries.forEach { view ->
                            MuscleMap(
                                sex = sex,
                                view = view,
                                states = emptyMap(),
                                onMuscleTap = { selected.value = it },
                                modifier = androidx.compose.ui.Modifier.width(100.dp),
                                selectedMuscleId = selected.value,
                            )
                        }
                    }
                }
            }
        }

        val expectedFront = setOf(
            "TRAPEZIUS", "CHEST", "SHOULDERS FRONT", "SHOULDERS SIDE", "BICEPS", "TRICEPS",
            "FOREARMS", "OBLIQUES", "CORE", "ABDUCTORS", "QUADS", "CALVES", "ADDUCTORS",
        )
        val expectedBack = setOf(
            "TRAPEZIUS", "SHOULDERS REAR", "SHOULDERS SIDE", "TRICEPS", "FOREARMS", "RHOMBOIDS",
            "LATS", "BACK LOWER", "OBLIQUES", "GLUTES", "ABDUCTORS", "ADDUCTORS", "HAMSTRINGS", "CALVES",
        )

        AnatomySex.entries.forEach { sex ->
            AnatomyView.entries.forEach { view ->
                val map = compose.onNodeWithContentDescription(
                    "${sex.name.lowercase()} ${view.name.lowercase()} freshness map",
                )
                map.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "No muscle selected"))
                val labels = map.fetchSemanticsNode().config[SemanticsActions.CustomActions]
                    .map { it.label.removePrefix("Inspect ").substringBefore(':').uppercase() }
                    .toSet()
                assertEquals(if (view == AnatomyView.FRONT) expectedFront else expectedBack, labels)
            }
        }

        val front = compose.onNodeWithContentDescription("male front freshness map")
        val inspectChest = front.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label.startsWith("Inspect CHEST") }
        compose.runOnIdle { inspectChest.action() }
        front.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected chest"))

        val back = compose.onNodeWithContentDescription("male back freshness map")
        val inspectLats = back.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label.startsWith("Inspect LATS") }
        compose.runOnIdle { inspectLats.action() }
        back.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected lats"))
        front.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "No muscle selected"))
    }

    @Test
    fun neutralOutlineLayerRendersTheCompleteSilhouette() {
        compose.setContent {
            VibeCheckTheme {
                MuscleMap(
                    sex = AnatomySex.MALE,
                    view = AnatomyView.FRONT,
                    states = emptyMap(),
                    onMuscleTap = {},
                    modifier = androidx.compose.ui.Modifier.width(240.dp),
                )
            }
        }

        val pixels = compose.onNodeWithContentDescription("male front freshness map").captureToImage().toPixelMap()
        val background = pixels[0, 0]
        var headPixels = 0
        var feetPixels = 0
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val pixel = pixels[x, y]
                val differsFromBackground =
                    abs(pixel.red - background.red) > 0.04f ||
                        abs(pixel.green - background.green) > 0.04f ||
                        abs(pixel.blue - background.blue) > 0.04f ||
                        abs(pixel.alpha - background.alpha) > 0.04f
                if (differsFromBackground) {
                    if (y < pixels.height * 0.15f) headPixels++
                    if (y > pixels.height * 0.87f) feetPixels++
                }
            }
        }
        assertTrue("Expected rendered head outline, found $headPixels pixels", headPixels > 20)
        assertTrue("Expected rendered feet outline, found $feetPixels pixels", feetPixels > 20)
    }

    @Test
    fun allPresetModesRenderTheSharedFreshnessScaleAndAccessibleLegend() {
        val palettes = VibePalettes.presets.flatMap { listOf(it.light, it.dark) }
        val activePalette = mutableStateOf(palettes.first())
        compose.setContent {
            VibeCheckTheme(activePalette.value) {
                Column {
                    MuscleMap(
                        sex = AnatomySex.MALE,
                        view = AnatomyView.FRONT,
                        states = mapOf(
                            "CHEST" to MuscleRecencyBand.UNDER_24_HOURS,
                            "CORE" to MuscleRecencyBand.HOURS_48_TO_72,
                            "QUADS" to MuscleRecencyBand.OVER_7_DAYS,
                        ),
                        onMuscleTap = {},
                        modifier = androidx.compose.ui.Modifier.width(240.dp),
                    )
                    FreshnessLegend(androidx.compose.ui.Modifier.width(74.dp))
                }
            }
        }

        palettes.forEach { palette ->
            compose.runOnIdle { activePalette.value = palette }
            compose.waitForIdle()
            compose.onNodeWithContentDescription("male front freshness map").assertExists()
            compose.onNodeWithContentDescription(
                "Freshness colour scale. Most recent under 24 hours at the top; " +
                    "24 to 48 hours; 48 to 72 hours; 3 to 7 days; least recent over 7 days at the bottom. " +
                    "No data is separate.",
            ).assertExists()
        }
    }

    @Test
    fun retroScannerPreservesMapBoundsHitActionsAndSelectionSemantics() {
        val style = mutableStateOf(VibeVisualStyle.STANDARD)
        var selected = mutableStateOf<String?>(null)
        compose.setContent {
            selected = remember { mutableStateOf(null) }
            VibeCheckTheme(VibePalettes.Mono.dark, style.value) {
                MuscleMap(
                    sex = AnatomySex.FEMALE,
                    view = AnatomyView.BACK,
                    states = mapOf("LATS" to MuscleRecencyBand.HOURS_24_TO_48),
                    onMuscleTap = { selected.value = it },
                    modifier = androidx.compose.ui.Modifier.width(240.dp),
                    selectedMuscleId = selected.value,
                )
            }
        }
        val map = compose.onNodeWithContentDescription("female back freshness map")
        val standardBounds = map.fetchSemanticsNode().boundsInRoot
        val standardActions = map.fetchSemanticsNode().config[SemanticsActions.CustomActions].map { it.label }

        compose.runOnIdle { style.value = VibeVisualStyle.RETRO_FUTURE }
        compose.waitForIdle()
        val retroBounds = map.fetchSemanticsNode().boundsInRoot
        val retroActions = map.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(standardBounds, retroBounds)
        assertEquals(standardActions, retroActions.map { it.label })

        compose.runOnIdle { retroActions.first { it.label.startsWith("Inspect LATS") }.action() }
        map.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected lats"))
        assertEquals(retroBounds, map.fetchSemanticsNode().boundsInRoot)
    }
}

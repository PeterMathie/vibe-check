package com.petermathie.vibecheck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.domain.model.AnatomySex
import com.petermathie.vibecheck.domain.model.MuscleRecencyBand
import com.petermathie.vibecheck.ui.anatomy.AnatomyView
import com.petermathie.vibecheck.ui.anatomy.FreshnessLegend
import com.petermathie.vibecheck.ui.anatomy.MuscleMap
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import com.petermathie.vibecheck.ui.theme.VibePalettes
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
    fun musclesOnlyMapKeepsWidthsAndBalancedVerticalPadding() {
        val crop = mutableStateOf(false)
        var tappedMuscle: String? = null
        val palette = VibePalettes.presets.first().light.copy(
            diagramLine = Color.Magenta,
            recencyNever = Color.Green,
            surface = Color.White,
        )
        compose.setContent {
            VibeCheckTheme(palette) {
                Row {
                    AnatomyView.entries.forEach { view ->
                        MuscleMap(
                            sex = AnatomySex.MALE,
                            view = view,
                            states = emptyMap(),
                            onMuscleTap = { tappedMuscle = it },
                            modifier = androidx.compose.ui.Modifier.width(140.dp)
                                .height(if (crop.value) 320.dp else 400.dp)
                                .background(palette.surface),
                            musclesOnly = crop.value,
                            alignToLegendBounds = true,
                            referenceCanvasHeight = 400.dp,
                        )
                    }
                }
            }
        }
        val before = AnatomyView.entries.associateWith { view ->
            compose.onNodeWithContentDescription("male ${view.name.lowercase()} freshness map")
                .captureToImage().toPixelMap()
        }
        fun outlinePixels(pixels: PixelMap) = (0 until pixels.width).sumOf { x ->
            (0 until pixels.height).count { y ->
                val color = pixels[x, y]
                color.red > 0.95f && color.blue > 0.95f && color.green < 0.95f
            }
        }
        fun muscleBounds(pixels: PixelMap): List<Int> {
            var left = pixels.width
            var top = pixels.height
            var right = 0
            var bottom = 0
            for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
                val color = pixels[x, y]
                if (color.green > 0.8f && color.red < 0.2f && color.blue < 0.2f) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
            assertTrue(right > left && bottom > top)
            return listOf(left, top, right, bottom)
        }
        compose.runOnIdle { crop.value = true }
        AnatomyView.entries.forEach { view ->
            val pixels = compose.onNodeWithContentDescription("male ${view.name.lowercase()} freshness map")
                .captureToImage().toPixelMap()
            val original = before.getValue(view)
            assertEquals(original.width, pixels.width)
            assertTrue("$view: crop must shorten the map", pixels.height < original.height)
            assertTrue("$view: default History outlines remain", outlinePixels(original) > 20)
            assertEquals("$view: Home has no silhouette outlines", 0, outlinePixels(pixels))
            val oldBounds = muscleBounds(original)
            val newBounds = muscleBounds(pixels)
            assertEquals((oldBounds[2] - oldBounds[0]).toFloat(), (newBounds[2] - newBounds[0]).toFloat(), 1f)
            val topPadding = newBounds[1].toFloat() / pixels.height
            val bottomPadding = (pixels.height - newBounds[3]).toFloat() / pixels.height
            assertTrue("$view: modest top breathing room: $topPadding", topPadding in 0.04f..0.08f)
            assertTrue("$view: modest bottom breathing room: $bottomPadding", bottomPadding in 0.04f..0.08f)
            assertEquals(topPadding, bottomPadding, 0.01f)
        }
        compose.onNodeWithContentDescription("male front freshness map").performTouchInput {
            down(Offset(width * 0.4f, height * 0.15f))
            up()
        }
        compose.runOnIdle { assertEquals("CHEST", tappedMuscle) }
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
}

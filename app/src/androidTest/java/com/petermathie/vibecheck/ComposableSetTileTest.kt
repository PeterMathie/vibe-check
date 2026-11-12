package com.petermathie.vibecheck

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.data.local.BandEntity
import com.petermathie.vibecheck.domain.programme.*
import com.petermathie.vibecheck.domain.workout.encodeTimingInputs
import com.petermathie.vibecheck.domain.workout.encodeTimingMillis
import com.petermathie.vibecheck.ui.*
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ComposableSetTileTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allTwelveCombinationsFitAtOrdinarySize() = exerciseMatrix(411, 1f)
    @Test fun allTwelveCombinationsFitAtNarrowSize() = exerciseMatrix(320, 1f)
    @Test fun allTwelveCombinationsRemainUsableAtDoubleFontScale() = exerciseMatrix(320, 2f)

    private fun exerciseMatrix(width: Int, fontScale: Float) {
        var resistance by mutableStateOf(ResistanceMetric.BODYWEIGHT)
        var measurement by mutableStateOf(MeasurementMetric.REPETITIONS)
        var attemptCount by mutableIntStateOf(8)
        var draft by mutableStateOf(emptyEntryDraft("fixture", 1).copy(
            performance = "7", rpe = "8", romValue = "45", timeUnderTension = "45",
            tutEntries = encodeTimingInputs(List(8) { (it + 1).toString() }),
        ))
        var bands by mutableStateOf(emptyList<String>())
        val definitions = List(8) { BandEntity("custom-$it", "Band $it", it + 0.5, 0xFF5085D6L) }
        val previous = emptySet("previous", 1).copy(
            reps = 6.0, weightKg = 20.0, romValue = 40.0, romUnit = "degrees",
            holdMillis = 36_000, tutEntriesMillis = encodeTimingMillis(List(8) { (it + 1) * 1_000L }),
            timeUnderTensionMillis = 42_000, rpe = 7.5,
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VibeCheckTheme {
                    Column(
                        Modifier.width(width.dp).verticalScroll(rememberScrollState())
                            .semantics { contentDescription = "Tile fixture" },
                    ) {
                        val config = ExerciseInputConfig().withResistance(resistance).withMeasurement(measurement)
                        WorkoutSetTile(
                            resistanceWidth = when (resistance) {
                                ResistanceMetric.BODYWEIGHT -> 0
                                ResistanceMetric.KILOGRAMS -> 56
                                ResistanceMetric.BANDS -> 96
                            },
                            measurementWidth = if (measurement == MeasurementMetric.SKILL_HOLD) 150 else 56,
                            header = { Text("Variant") },
                            resistance = {
                                ResistanceRenderer(
                                    resistance, config, draft, "20", "kg", definitions, bands,
                                    previous, listOf("Band 0"), false, true, "Fixture set 1",
                                    onWeightChange = {}, onBandsChange = { bands = it },
                                )
                            },
                            measurement = {
                                MeasurementRenderer(
                                    measurement, config, draft, draft.performance,
                                    if (measurement == MeasurementMetric.SKILL_HOLD) List(attemptCount) { (it + 1).toString() } else listOf("7"),
                                    previous, false, true, "Fixture set 1", Modifier,
                                    onRepsChange = { draft = draft.copy(performance = it) },
                                    onDraftChange = { draft = it },
                                    onStopwatch = {},
                                )
                            },
                            rpe = {
                                MetricValue(
                                    draft.rpe, "RPE", "RPE for Fixture set 1", "7.5", false, true, width = 44, secondary = true,
                                    onChange = { draft = draft.copy(rpe = it) },
                                )
                            },
                            completion = {
                                IconButton(onClick = {}, modifier = Modifier.semantics { contentDescription = "Complete fixture" }) {
                                    Text("✓")
                                }
                            },
                            feedback = {},
                        )
                    }
                }
            }
        }
        ResistanceMetric.entries.forEach { load ->
            MeasurementMetric.entries.forEach { result ->
                compose.runOnIdle { resistance = load; measurement = result; bands = emptyList(); attemptCount = 8 }
                compose.waitForIdle()
                compose.onNodeWithText("Variant").performScrollTo()
                val fixture = compose.onNodeWithContentDescription("Tile fixture").getUnclippedBoundsInRoot()
                val inputs = compose.onAllNodes(hasSetTextAction())
                val rectangles = (0 until inputs.fetchSemanticsNodes().size).map { inputs[it].getUnclippedBoundsInRoot() }
                rectangles.forEach { bounds ->
                    assertTrue("$load/$result: input overflows left", bounds.left >= fixture.left)
                    assertTrue("$load/$result: input overflows right", bounds.right <= fixture.right + 0.01.dp)
                    assertTrue("$load/$result: touch height", bounds.bottom - bounds.top >= 48.dp - 0.01.dp)
                }
                rectangles.forEachIndexed { index, a ->
                    rectangles.drop(index + 1).forEach { b ->
                        assertTrue("$load/$result: overlapping inputs", a.right <= b.left + 0.01.dp ||
                            b.right <= a.left + 0.01.dp || a.bottom <= b.top + 0.01.dp || b.bottom <= a.top + 0.01.dp)
                    }
                }
                val rpe = compose.onNodeWithContentDescription("RPE for Fixture set 1").getUnclippedBoundsInRoot()
                val tick = compose.onNodeWithContentDescription("Complete fixture").getUnclippedBoundsInRoot()
                val previousRpe = compose.onNodeWithContentDescription("Previous RPE for Fixture set 1").getUnclippedBoundsInRoot()
                assertTrue("$load/$result: decimal RPE comparison stays on one line",
                    (previousRpe.bottom - previousRpe.top).value <= 18f * fontScale)
                assertTrue(rpe.right <= tick.left)
                assertTrue("$load/$result: completion stays beside RPE",
                    kotlin.math.abs(rpe.top.value - tick.top.value) <= 8f)
                if (load == ResistanceMetric.BODYWEIGHT) {
                    compose.onNodeWithContentDescription("Resistance for Fixture set 1").assertDoesNotExist()
                    compose.onNodeWithText("Bodyweight").assertDoesNotExist()
                }
                if (load == ResistanceMetric.BANDS) {
                    val dots = definitions.indices.map {
                        compose.onNodeWithContentDescription("Band $it band for Fixture set 1").getUnclippedBoundsInRoot()
                    }
                    assertEquals(2, dots.map { it.top }.distinct().size)
                    assertEquals(4, dots.count { it.top == dots.first().top })
                    assertEquals(dots.first().left, dots[4].left)
                    compose.onNodeWithContentDescription("Band 0 band for Fixture set 1").performScrollTo().performClick()
                    compose.onNodeWithContentDescription("Band 0 band for Fixture set 1").assertIsSelected()
                    compose.onNodeWithContentDescription("Band 7 band for Fixture set 1").performScrollTo().performClick()
                    compose.onNodeWithContentDescription("Band 7 band for Fixture set 1").assertIsSelected()
                }
                if (result == MeasurementMetric.SKILL_HOLD) {
                    compose.onNodeWithContentDescription("TUT attempt 8 for Fixture set 1").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithContentDescription("Total Time for Fixture set 1").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithContentDescription("Add TUT entry for Fixture set 1").assertDoesNotExist()
                    compose.onNodeWithContentDescription("Previous Total Time for Fixture set 1").assertTextEquals("Prev 42")
                    compose.runOnIdle { attemptCount = 1 }
                    val first = compose.onNodeWithContentDescription("Time Under Tension for Fixture set 1").getUnclippedBoundsInRoot()
                    val add = compose.onNodeWithContentDescription("Add TUT entry for Fixture set 1").getUnclippedBoundsInRoot()
                    assertTrue("The add icon occupies the next attempt slot", add.left >= first.right)
                    assertTrue("The next slot shares the attempt row", add.top < first.bottom && add.bottom > first.top)
                    compose.onNodeWithContentDescription("Previous remaining TUT attempts for Fixture set 1")
                        .assertTextContains("2: 2 s", substring = true).assertTextContains("8: 8 s", substring = true)
                }
            }
        }
    }
}

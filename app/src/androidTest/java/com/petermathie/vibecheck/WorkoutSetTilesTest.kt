package com.petermathie.vibecheck

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.petermathie.vibecheck.data.local.BandEntity
import com.petermathie.vibecheck.data.DataTransfer
import com.petermathie.vibecheck.data.TrainingRepository
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.ExerciseMeasurement
import com.petermathie.vibecheck.domain.programme.MeasurementMetric
import com.petermathie.vibecheck.domain.programme.ResistanceMetric
import com.petermathie.vibecheck.domain.workout.encodeTimingMillis
import com.petermathie.vibecheck.domain.workout.timingMillis
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.WorkoutEditor
import com.petermathie.vibecheck.ui.HistoryScreen
import com.petermathie.vibecheck.ui.ExerciseSettingsDialog
import com.petermathie.vibecheck.ui.RestTimer
import com.petermathie.vibecheck.ui.emptySet
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutSetTilesTest {
    private lateinit var db: VibeDatabase
    @get:Rule val lifecycle = ComposeRoomLifecycleRule { if (::db.isInitialized) db else null }
    private val compose get() = lifecycle.compose

    private fun start(day: String = "demo-day-push"): String = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        DatabaseSeeder(context, db).seedIfNeeded()
        TrainingRepository(db, db.programmeDao(), db.workoutDao(), db.trackerDao(), context)
            .startWorkout(day)
    }

    private fun show(workout: String) {
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Time Under Tension for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun expandSet(exercise: String, ordinal: Int) {
        compose.onNodeWithContentDescription("Set $ordinal for $exercise")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
    }

    private fun variantOption(name: String) = compose.onNode(
        hasText(name) and hasClickAction() and
            SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected),
    )

    private fun showAtSize(workout: String, width: Dp, fontScale: Float) {
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VibeCheckTheme { Box(Modifier.width(width)) { WorkoutEditor(vm, workout, {}, {}) } }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun tickStartsConfiguredRestAndParentContainsIndependentlyTappableSets() {
        val workout = start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        RestTimer.cancel(context)
        try {
            show(workout)
            val tick = compose.onNodeWithContentDescription("Complete set 1 for Handstand and open next empty set")
            tick.assertIsNotEnabled()
            compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("5")
            compose.runOnIdle {
                assertEquals(0, context.getSharedPreferences("settings", 0).getLong("restEnd", 0))
            }
            val parent = compose.onNodeWithContentDescription("Exercise Handstand").getUnclippedBoundsInRoot()
            val last = compose.onNodeWithContentDescription("Set 3 for Handstand").getUnclippedBoundsInRoot()
            assertTrue(parent.bottom >= last.bottom && parent.left < last.left && parent.right > last.right)
            tick.performScrollTo().performClick()
            compose.onNodeWithContentDescription("Time Under Tension for Handstand set 2").assertExists()
            compose.onNodeWithContentDescription("Exercise Handstand")
                .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Expanded"))
            val rest = runBlocking {
                db.editorDao().workoutExercises().first().first { it.workoutId == workout && it.actualExerciseId == "core:handstand" }.restSeconds
            }
            compose.runOnIdle {
                val remaining = context.getSharedPreferences("settings", 0).getLong("restEnd", 0) - System.currentTimeMillis()
                assertTrue(remaining in (rest * 1_000L - 10_000)..(rest * 1_000L))
            }
            compose.onNodeWithContentDescription("Start 120 second rest for Handstand").assertDoesNotExist()
        } finally {
            RestTimer.cancel(context)
        }
    }

    @Test
    fun tickLocksOnlyFilledValuesAcrossEditorRecreationAndUnlockRestoresOriginalFields() {
        val workout = start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = runBlocking {
            val variant = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(variant.copy(inputConfig = ExerciseInputConfig(
                timeHeld = true, manyTuts = true, timeUnderTension = true, bandResistance = true,
            ).encode()))
            val row = db.editorDao().workoutExercises().first().first {
                it.workoutId == workout && it.actualExerciseId == "core:handstand"
            }
            val set = emptySet(row.id, 1).copy(
                variationId = variant.id, holdMillis = 10_000L,
                tutEntriesMillis = encodeTimingMillis(listOf(4_000L, 6_000L)),
                timeUnderTensionMillis = 30_000L,
            )
            db.editorDao().saveSetWithSnapshots(set, listOf("band-yellow", "band-black"), false)
            db.editorDao().setById(set.id)!!
        }
        val generation = mutableIntStateOf(0)
        val firstVm = lifecycle.own(EditorViewModel(db))
        val reopenedVm = lifecycle.own(EditorViewModel(db))
        compose.setContent {
            key(generation.intValue) {
                VibeCheckTheme {
                    WorkoutEditor(if (generation.intValue == 0) firstVm else reopenedVm, workout, {}, {})
                }
            }
        }
        RestTimer.cancel(context)
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("TUT attempt 2 for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").assertDoesNotExist()
            compose.onNodeWithContentDescription("Complete set 1 for Handstand and open next empty set").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("Edit completed set 1 for Handstand").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasContentDescription("Set 1 for Handstand")))
                .assertCountEquals(0)
            compose.onNodeWithContentDescription("TUT attempt 1 for Handstand set 1").assertTextEquals("4 s")
            compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").assertTextEquals("6 s")
            compose.onNodeWithText("30.0 Total s").assertExists()
            compose.onNodeWithContentDescription("RPE for Handstand set 1").assertDoesNotExist()
            compose.onNodeWithContentDescription("Yellow band for Handstand set 1").assertIsNotEnabled().assertIsSelected()
            compose.onNodeWithContentDescription("Red band for Handstand set 1").assertDoesNotExist()
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").assertIsSelected()
            compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertIsDisplayed().assertIsEnabled()
            compose.runOnIdle {
                assertTrue(context.getSharedPreferences("workout_logging", 0).getStringSet("locked_sets", emptySet())!!.contains(original.id))
                generation.intValue++
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("Yellow band for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").assertExists()
            compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithContentDescription("TUT attempt 1 for Handstand set 1").assertTextEquals("4 s")
            val saved = runBlocking { db.editorDao().setById(original.id)!! }
            assertEquals(original, saved)
            RestTimer.cancel(context)
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").performClick()
            compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertIsDisplayed().assertIsEnabled()
            expandSet("Handstand", 1)
            compose.onNodeWithContentDescription("TUT attempt 1 for Handstand set 1").assertTextContains("4")
            compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").assertTextContains("6")
            compose.onNodeWithContentDescription("Total Time for Handstand set 1").assertTextContains("30.0")
            compose.onNodeWithContentDescription("Yellow band for Handstand set 1").assertIsEnabled().assertIsSelected()
            compose.onNodeWithContentDescription("Black band for Handstand set 1").assertIsSelected()
            compose.runOnIdle {
                assertEquals(0, context.getSharedPreferences("settings", 0).getLong("restEnd", 0))
                assertFalse(context.getSharedPreferences("workout_logging", 0).getStringSet("locked_sets", emptySet())!!.contains(original.id))
            }
        } finally {
            RestTimer.cancel(context)
        }
    }

    @Test
    fun largeFontTileKeepsVariantHeaderAndDirectlyAccessibleBandDots() {
        val workout = start()
        runBlocking {
            val variant = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(variant.copy(inputConfig = ExerciseInputConfig(
                bodyweight = true, bandResistance = true, timeHeld = true, manyTuts = true,
            ).encode()))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                VibeCheckTheme { Box(Modifier.width(320.dp)) { WorkoutEditor(vm, workout, {}, {}) } }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Yellow band for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Bodyweight: tracked from workout").assertDoesNotExist()
        compose.onNodeWithContentDescription("Reps for Handstand set 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Wall handstand")
        val firstRow = listOf("Time Under Tension for Handstand set 1",
            "Complete set 1 for Handstand and open next empty set", "Change variant for Handstand set 1")
        val collapsed = firstRow.map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot }
        expandSet("Handstand", 1)
        val bandBounds = listOf("Yellow", "Red", "Black", "Purple").map { name ->
            compose.onNodeWithContentDescription("$name band for Handstand set 1")
                .getUnclippedBoundsInRoot()
        }
        val swap = compose.onNodeWithContentDescription("Change variant for Handstand set 1").getUnclippedBoundsInRoot()
        val tick = compose.onNodeWithContentDescription("Complete set 1 for Handstand and open next empty set").getUnclippedBoundsInRoot()
        assertEquals(collapsed, firstRow.map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot })
        assertTrue("Bands must wrap in order without overlap: $bandBounds", bandBounds.zipWithNext().all { (left, right) ->
            if (kotlin.math.abs((left.top + left.bottom).value - (right.top + right.bottom).value) < 2f) {
                left.right <= right.left
            } else left.bottom <= right.top
        })
        val rpe = compose.onNodeWithContentDescription("RPE for Handstand set 1").getUnclippedBoundsInRoot()
        assertTrue("RPE and completion remain narrow trailing controls", rpe.right <= tick.left)
        assertTrue("Variant selector is a header, not a separate swap", swap.top < tick.top)
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Wall handstand")
        val heading = compose.onNodeWithContentDescription("Variant for Handstand set 1").getUnclippedBoundsInRoot()
        val tile = compose.onNodeWithContentDescription("Set 1 for Handstand").getUnclippedBoundsInRoot()
        assertTrue("Variant heading must be inside the tile", heading.top >= tile.top && heading.bottom < tile.bottom)
        compose.onNodeWithContentDescription("Yellow band for Handstand set 1").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Black band for Handstand set 1").performClick().assertIsSelected()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun stagedDeletionRemovesEmptyAndSavedTilesWithoutResurrectionOnReopening() {
        val workout = start()
        show(workout)
        val row = runBlocking { db.editorDao().workoutExercises().first().first { it.workoutId == workout && it.actualExerciseId == "core:handstand" } }
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("5")
        val savedId = runBlocking { db.editorDao().sets().first().single { it.workoutExerciseId == row.id }.id }
        compose.onNodeWithContentDescription("Set 3 for Handstand").performScrollTo()
        val empty = compose.onNodeWithContentDescription("Swipe actions for set 3 for Handstand")
        empty.performCustomAccessibilityActionWithLabel("Reveal delete")
        empty.performCustomAccessibilityActionWithLabel("Delete set 3 for Handstand")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().workoutExerciseById(row.id)?.targetSets == 2 }
        }
        compose.onNodeWithContentDescription("Set 3 for Handstand").assertDoesNotExist()
        val saved = compose.onNodeWithContentDescription("Swipe actions for set 1 for Handstand")
        saved.performCustomAccessibilityActionWithLabel("Reveal delete")
        saved.performCustomAccessibilityActionWithLabel("Delete set 1 for Handstand")
        compose.waitUntil(15_000) { runBlocking { db.editorDao().setById(savedId) == null && db.editorDao().workoutExerciseById(row.id)?.targetSets == 1 } }
        compose.onNodeWithContentDescription("Exercise heading for Handstand").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Exercise heading for Handstand").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Set 2 for Handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1")
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun pinnedHeadingAndExerciseBodyShareOneDeletionStage() {
        val workout = start()
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent {
            VibeCheckTheme { Box(Modifier.height(320.dp)) { WorkoutEditor(vm, workout, {}, {}) } }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("RPE for Handstand set 3").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add set for Handstand").performScrollTo()
        compose.onNodeWithContentDescription("Pinned exercise heading for Handstand").assertIsDisplayed()
        val wrappers = compose.onAllNodesWithContentDescription("Swipe actions for exercise Handstand")
        wrappers.assertCountEquals(2)
        wrappers.onFirst().performCustomAccessibilityActionWithLabel("Reveal delete")
        wrappers.onLast().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Delete revealed"))
        wrappers.onLast().performCustomAccessibilityActionWithLabel("Close delete")
        wrappers.onFirst().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Closed"))
        wrappers.onLast().performCustomAccessibilityActionWithLabel("Reveal delete")
        wrappers.onLast().performCustomAccessibilityActionWithLabel("Delete exercise Handstand")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().workoutExercises().first().none { it.workoutId == workout && it.actualExerciseId == "core:handstand" } }
        }
        assertTrue(runBlocking { db.editorDao().workoutExercises().first().any { it.workoutId == workout && it.actualExerciseId == "core:planche" } })
    }

    @Test
    fun manyTimingHasExactlyEightSlotsAndPersistsEveryAttemptAndTotal() {
        val workout = start()
        show(workout)
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("1")
        for (index in 2..8) {
            compose.onNodeWithContentDescription("Add TUT entry for Handstand set 1").performScrollTo().performClick()
            compose.onNodeWithContentDescription("TUT attempt $index for Handstand set 1").performScrollTo().performTextInput(index.toString())
        }
        assertTimingFlex()
        compose.onNodeWithContentDescription("Add TUT entry for Handstand set 1").assertDoesNotExist()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("TUT total for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("TUT total for Handstand set 1").assertTextContains("36 s", substring = true)
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { timingMillis(it.tutEntriesMillis) == List(8) { i -> (i + 1) * 1000L } && it.holdMillis == 36_000L } }
        }
        val backup = runBlocking { DataTransfer.export(db) }
        runBlocking { DataTransfer.import(db, backup) }
        assertTrue(runBlocking { db.editorDao().sets().first().any { timingMillis(it.tutEntriesMillis).size == 8 && it.holdMillis == 36_000L } })
    }

    @Test
    fun eightAttemptsStayAccessibleWithoutExpansionAt320DpAndDoubleFontScale() {
        val workout = start()
        runBlocking {
            val row = db.editorDao().workoutExercises().first().first {
                it.workoutId == workout && it.actualExerciseId == "core:handstand"
            }
            db.editorDao().set(emptySet(row.id, 1).copy(
                variationId = "handstand-wall",
                variationInputConfigSnapshot = ExerciseInputConfig(timeHeld = true, manyTuts = true).encode(),
                holdMillis = 36_000L,
                tutEntriesMillis = encodeTimingMillis(List(8) { (it + 1) * 1_000L }),
            ))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                VibeCheckTheme {
                    Box(Modifier.width(320.dp)) { WorkoutEditor(vm, workout, {}, {}) }
                }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty()
        }
        fun tileHeight() = compose.onNodeWithContentDescription("Set 1 for Handstand")
            .getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val collapsedHeight = tileHeight()
        expandSet("Handstand", 1)
        assertEquals(collapsedHeight, tileHeight())
        assertTimingFlex(2f)
        compose.onNodeWithContentDescription("TUT attempt 8 for Handstand set 1").performScrollTo().performTextReplacement("9")
        compose.waitUntil(15_000) {
            runBlocking {
                db.editorDao().sets().first().any {
                    timingMillis(it.tutEntriesMillis) == listOf(1_000L, 2_000L, 3_000L, 4_000L, 5_000L, 6_000L, 7_000L, 9_000L)
                }
            }
        }
    }

    private fun assertTimingFlex(fontScale: Float = 1f) {
        (1..8).forEach {
            compose.onNodeWithContentDescription("TUT attempt $it for Handstand set 1")
                .performScrollTo().assertIsDisplayed().assertTextContains(it.toString())
        }
        val bounds = (1..8).map {
            compose.onNodeWithContentDescription("TUT attempt $it for Handstand set 1")
                .getUnclippedBoundsInRoot()
        }
        val cell = compose.onNodeWithContentDescription("TUT attempt 1 for Handstand set 1").getUnclippedBoundsInRoot()
        assertTrue("TUT cells must remain content-sized", cell.right - cell.left <= 56.dp * fontScale + 0.5.dp)
        assertTrue("TUT attempts must wrap in order without overlap: $bounds", bounds.zipWithNext().all { (left, right) ->
            if (kotlin.math.abs((left.top + left.bottom).value - (right.top + right.bottom).value) < 2f) left.right <= right.left else left.bottom <= right.top
        })
        assertTrue(bounds.groupBy { it.top }.values.all { it.size <= 4 })
    }

    @Test
    fun variantsAreIndependentAndSwitchingToOneRetainsExistingAttempts() {
        val workout = start()
        runBlocking {
            val variation = db.editorDao().variationById("handstand-free")!!
            db.editorDao().variation(variation.copy(inputConfig = ExerciseInputConfig(timeHeld = true, manyTuts = false).encode()))
        }
        show(workout)
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("4")
        compose.onNodeWithContentDescription("Add TUT entry for Handstand set 1").performScrollTo().performClick()
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").performTextInput("6")
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performScrollTo().performClick()
        compose.onNodeWithText("Freestanding handstand").performClick()
        compose.onNodeWithContentDescription("Add TUT entry for Handstand set 1").assertDoesNotExist()
        compose.onNodeWithText("Recorded attempts are retained. Choose Skill hold to edit them.").assertExists()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").assertTextEquals("10 s")
        expandSet("Handstand", 2)
        compose.onNodeWithContentDescription("Change variant for Handstand set 2").performScrollTo().performClick()
        variantOption("Wall handstand").assertIsSelected().performClick()
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.variationId == "handstand-free" && it.holdMillis == 10_000L && timingMillis(it.tutEntriesMillis) == listOf(4_000L, 6_000L) } }
        }
    }

    @Test
    fun completingTileSelectsNextEmptyAndExercisesCollapseWithoutLosingValues() {
        val workout = start()
        show(workout)
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("5")
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("RPE for Handstand set 1").performTextInput("8")
        compose.onNodeWithContentDescription("RPE for Handstand set 1").performImeAction()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 2").assertExists()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").assertTextContains("5")
        compose.onNodeWithContentDescription("Set 2 for Handstand").assertIsSelected()
        compose.onNodeWithContentDescription("Exercise heading for Handstand").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Set 2 for Handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("Exercise heading for Handstand").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { db.editorDao().sets().first().any { it.holdMillis == 5_000L && it.rpe == 8.0 } })
        compose.onNodeWithContentDescription("More actions for Handstand").assertDoesNotExist()
        compose.onNodeWithText("Substitute exercise").assertDoesNotExist()
    }

    @Test
    fun timingFieldsRemainDirectlyEditableWithOneSumAndSeparateTotalTime() {
        val workout = start()
        show(workout)
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("4")
        compose.onNodeWithContentDescription("Add TUT entry for Handstand set 1").performScrollTo().performClick()
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").performTextInput("6")
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").performTextInput("30")
        compose.waitUntil(15_000) {
            runBlocking {
                db.editorDao().sets().first().any {
                    timingMillis(it.tutEntriesMillis) == listOf(4_000L, 6_000L) && it.timeUnderTensionMillis == 30_000L
                }
            }
        }

        compose.onNodeWithContentDescription("TUT total for Handstand set 1").assertTextContains("10 s", substring = true)
        compose.onNodeWithText("4s + 6s = 10s · Total 30s").assertDoesNotExist()
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Wall handstand")
        compose.onNodeWithContentDescription("TUT attempt 1 for Handstand set 1").assertTextContains("4")
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").assertTextContains("6")
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").assertTextContains("30")
    }

    @Test
    fun previousResultMatchesExactVariantAndOrdinalInline() = assertPreviousResultPlacement(1f)

    @Test
    fun previousResultStaysInlineAt320DpAndDoubleFontScale() = assertPreviousResultPlacement(2f)

    private fun assertPreviousResultPlacement(fontScale: Float) {
        val workout = start()
        runBlocking {
            val row = db.editorDao().workoutExercises().first().first {
                it.workoutId == workout && it.actualExerciseId == "core:handstand"
            }
            val session = db.editorDao().workouts().first().first { it.id == workout }
            listOf(
                Triple("previous-wall", 4_000L, "handstand-wall"),
                Triple("previous-free", 2_000L, "handstand-free"),
                Triple("unfinished-wall", 1_000L, "handstand-wall"),
            ).forEach { (id, offset, variant) ->
                val finished = id != "unfinished-wall"
                db.editorDao().workout(session.copy(
                    id = id,
                    status = if (finished) "FINISHED" else "DRAFT",
                    startedAt = session.startedAt - offset - 60_000L,
                    finishedAt = if (finished) session.startedAt - offset else null,
                ))
                db.editorDao().workoutExercise(row.copy(id = "$id-exercise", workoutId = id))
                val durations = when (id) {
                    "previous-wall" -> listOf(7_000L, 9_000L)
                    "previous-free" -> listOf(11_000L)
                    else -> listOf(99_000L)
                }
                durations.forEachIndexed { index, duration ->
                    db.editorDao().set(emptySet("$id-exercise", index + 1).copy(
                        variationId = variant,
                        holdMillis = duration,
                    ))
                }
            }
        }
        showAtSize(workout, if (fontScale == 1f) 411.dp else 320.dp, fontScale)
        compose.waitUntil(15_000) {
            compose.onAllNodes(
                hasContentDescription("Previous Time Under Tension for Handstand set 1") and hasText("Prev 7"),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        fun assertInline(ordinal: Int) {
            val note = compose.onNodeWithContentDescription("Previous Time Under Tension for Handstand set $ordinal")
                .getUnclippedBoundsInRoot()
            val field = compose.onNodeWithContentDescription("Time Under Tension for Handstand set $ordinal")
                .getUnclippedBoundsInRoot()
            assertEquals(field.left.value, note.left.value, 0.5f)
            assertTrue("Previous result belongs immediately under its current field", note.top >= field.bottom)
        }
        compose.onNodeWithContentDescription("Previous Time Under Tension for Handstand set 1").assertIsDisplayed()
        assertInline(1)
        compose.onNodeWithContentDescription("Previous Time Under Tension for Handstand set 2").assertTextEquals("Prev 9")
        compose.onNodeWithContentDescription("Previous Time Under Tension for Handstand set 3").assertDoesNotExist()
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Previous Time Under Tension for Handstand set 1").assertTextEquals("Prev 7")
        assertInline(1)

        compose.onNodeWithContentDescription("Set 2 for Handstand")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithContentDescription("Previous Time Under Tension for Handstand set 1").assertTextEquals("Prev 7")
        compose.onNodeWithContentDescription("Previous Time Under Tension for Handstand set 2").assertTextEquals("Prev 9")
        assertInline(2)
        compose.onNodeWithContentDescription("Change variant for Handstand set 2").performScrollTo().performClick()
        variantOption("Freestanding handstand").performClick()
        compose.onNodeWithContentDescription("Previous Time Under Tension for Handstand set 2").assertDoesNotExist()
        expandSet("Handstand", 2)
        compose.onNodeWithContentDescription("Previous Time Under Tension for Handstand set 2").assertDoesNotExist()
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performScrollTo().performClick()
        variantOption("Freestanding handstand").performClick()
        compose.onNodeWithContentDescription("Previous Time Under Tension for Handstand set 1").assertTextEquals("Prev 11")
        compose.onNodeWithText("Prev 99").assertDoesNotExist()
    }

    @Test
    fun emptyTilesProvideFieldsAndLabelledFooterActionsShareARow() {
        val workout = start()
        show(workout)
        compose.onNodeWithText("3 sets").assertDoesNotExist()
        compose.onNodeWithContentDescription("Workout exercise list")
            .performScrollToNode(hasContentDescription("Set 1 for Dip"))
        compose.onNodeWithText("Ready to log").assertDoesNotExist()
        compose.onNodeWithContentDescription("Reps for Dip set 2").assertIsEnabled()
        compose.onNodeWithContentDescription("Add set for Dip").performScrollTo().assertTextContains("Add set")
        compose.onNodeWithContentDescription("Show notes for Dip").performScrollTo().assertTextContains("Notes")
        val addBounds = compose.onNodeWithContentDescription("Add set for Dip").fetchSemanticsNode().boundsInRoot
        val notesBounds = compose.onNodeWithContentDescription("Show notes for Dip").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(addBounds.center.y - notesBounds.center.y) < 2f)
        assertTrue(addBounds.right <= notesBounds.left)

        compose.onNodeWithContentDescription("Add set for Dip").performClick()
        compose.onNodeWithContentDescription("Reps for Dip set 4").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Show notes for Dip").performScrollTo().performClick()
        compose.onAllNodes(hasText("Exercise notes") and hasSetTextAction())[0].performTextInput("Keep elbows close")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().workoutExercises().first().any { it.notes == "Keep elbows close" } }
        }
    }

    @Test
    fun everySetIdentifiesItsVariantAndMarksTheCurrentPickerChoice() {
        val workout = start()
        show(workout)
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Wall handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 2").assertTextEquals("Wall handstand")
        compose.onNodeWithText("Freestanding handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performClick()
        variantOption("Wall handstand").assertIsSelected()
        variantOption("Freestanding handstand").assertIsNotSelected().performClick()
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Freestanding handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 2").assertTextEquals("Wall handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 3").assertTextEquals("Wall handstand")
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Freestanding handstand")
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performClick()
        variantOption("Freestanding handstand").assertIsSelected().performClick()
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Freestanding handstand")
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun variantHeadersUseIdentityAndSnapshotsAndRecomputeAfterEditsAndDeletion() {
        val workout = start()
        val row = runBlocking {
            val row = db.editorDao().workoutExercises().first().first {
                it.workoutId == workout && it.actualExerciseId == "core:handstand"
            }
            db.editorDao().workoutExercise(row.copy(targetSets = 5, targets = "5 sets · 4s hold"))
            listOf("handstand-wall", "handstand-wall", "handstand-free", "handstand-free", "handstand-wall")
                .forEachIndexed { index, variant ->
                    db.editorDao().set(emptySet(row.id, index + 1).copy(
                        variationId = variant,
                        variationNameSnapshot = "Recorded handstand",
                        variationInputConfigSnapshot = ExerciseInputConfig(timeHeld = true).encode(),
                        holdMillis = (index + 1) * 1_000L,
                    ))
                }
            row
        }
        show(workout)
        compose.onNodeWithText("5 sets · 4s hold").assertExists()
        for (ordinal in 1..5) {
            val heading = compose.onNodeWithContentDescription("Variant for Handstand set $ordinal")
            heading.assertTextEquals("Recorded handstand")
        }
        compose.onNodeWithContentDescription("Change variant for Handstand set 2").performScrollTo().performClick()
        variantOption("Freestanding handstand").performClick()
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any {
                it.workoutExerciseId == row.id && it.ordinal == 2 && it.variationId == "handstand-free"
            } }
        }
        compose.onNodeWithContentDescription("Variant for Handstand set 2").assertTextEquals("Freestanding handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 3").assertTextEquals("Recorded handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 4").assertTextEquals("Recorded handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 5").assertTextEquals("Recorded handstand")

        compose.onNodeWithContentDescription("Set 2 for Handstand").performScrollTo()
        val swipe = compose.onNodeWithContentDescription("Swipe actions for set 2 for Handstand")
        swipe.performCustomAccessibilityActionWithLabel("Reveal delete")
        swipe.performCustomAccessibilityActionWithLabel("Delete set 2 for Handstand")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().workoutExerciseById(row.id)?.targetSets == 4 }
        }
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Recorded handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 2").assertTextEquals("Recorded handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 3").assertTextEquals("Recorded handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 4").assertTextEquals("Recorded handstand")
        compose.onNodeWithContentDescription("Set 5 for Handstand").assertDoesNotExist()
    }

    @Test
    fun iconActionsRemainAccessibleAndTimingSummaryIsNotRepeated() {
        val workout = start()
        show(workout)
        compose.onNodeWithText("Next set").assertDoesNotExist()
        compose.onNodeWithText("Add time", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Complete set 1 for Handstand and open next empty set")
            .assertHasClickAction().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("4")
        val addTime = compose.onNodeWithContentDescription("Add TUT entry for Handstand set 1")
            .assertHasClickAction().performScrollTo()
        val tileBounds = compose.onNodeWithContentDescription("Set 1 for Handstand").fetchSemanticsNode().boundsInRoot
        assertTrue(addTime.fetchSemanticsNode().boundsInRoot.right < tileBounds.center.x)
        addTime.performClick()
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").performTextInput("6")
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("TUT total for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription("TUT total for Handstand set 1").assertCountEquals(1)
        compose.onNodeWithText("TUT total:", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Complete set 1 for Handstand and open next empty set")
            .performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 2").assertExists()
        compose.onNodeWithContentDescription("TUT attempt 1 for Handstand set 1").assertTextEquals("4 s")
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").assertTextEquals("6 s")
    }

    @Test
    fun plancheShowsDirectBandGridAndCompactHoldAtOrdinaryReviewSize() = assertPlancheCompactRow(1f)

    @Test
    fun plancheCompactInputsWrapWithoutClippingAt320DpAndDoubleFontScale() = assertPlancheCompactRow(2f)

    private fun assertPlancheCompactRow(fontScale: Float) {
        val workout = start()
        showAtSize(workout, if (fontScale == 1f) 411.dp else 320.dp, fontScale)
        compose.onNodeWithContentDescription("Workout exercise list")
            .performScrollToNode(hasContentDescription("Set 1 for Planche"))
        if (fontScale > 1f) expandSet("Planche", 1)
        compose.onNodeWithContentDescription("Set 1 for Planche").performScrollTo()
        val descriptions = listOf("Time Under Tension", "Yellow band", "Red band", "Black band", "Purple band", "RPE")
        val fields = descriptions.map {
            compose.onNodeWithContentDescription("$it for Planche set 1").assertIsDisplayed().getUnclippedBoundsInRoot()
        }
        val header = compose.onNodeWithContentDescription("Change variant for Planche set 1")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val tick = compose.onNodeWithContentDescription("Complete set 1 for Planche and open next empty set")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Variant selector remains above the body", header.top < tick.top)
        fields.forEachIndexed { index, a ->
            fields.drop(index + 1).forEach { b ->
                assertTrue("Measurements must not overlap", a.right <= b.left || b.right <= a.left ||
                    a.bottom <= b.top || b.bottom <= a.top)
            }
        }
        val bands = fields.subList(1, 5)
        assertTrue("Four band dots stay left aligned on one row", bands.all { it.top == bands.first().top })
        assertTrue("RPE remains beside completion", fields.last().right <= tick.left)
        compose.onNodeWithContentDescription("Time Under Tension for Planche set 1")
            .assertTextContains("s").performTextInput("12")
        compose.onNodeWithContentDescription("RPE for Planche set 1").performScrollTo().performTextInput("8")
        compose.onNodeWithContentDescription("Yellow band for Planche set 1")
            .performScrollTo().performTouchInput { click() }
        compose.onNodeWithContentDescription("Yellow band for Planche set 1").assertIsSelected()
        compose.onNodeWithContentDescription("Red band for Planche set 1").assertIsNotSelected()
        compose.waitUntil(15_000) {
            runBlocking {
                val row = db.editorDao().workoutExercises().first().single {
                    it.workoutId == workout && it.actualExerciseId == "core:planche"
                }
                db.editorDao().sets().first().any { it.workoutExerciseId == row.id && it.holdMillis == 12_000L && it.rpe == 8.0 }
            }
        }
    }

    @Test
    fun simpleExerciseKeepsInputsInOneRowWithoutASeparateCompletionRow() {
        val workout = start("demo-day-pull")
        show(workout)
        compose.onNodeWithContentDescription("Workout exercise list")
            .performScrollToNode(hasContentDescription("Set 1 for Back extension"))
        compose.onNodeWithContentDescription("Change variant for Back extension set 1").assertExists()
        compose.onNodeWithContentDescription("Variant for Back extension set 1").assertTextEquals("Back extension")
        val fields = listOf("Resistance" to 64.dp, "Reps" to 56.dp, "RPE" to 48.dp).map { (label, maximum) ->
            val field = compose.onNodeWithContentDescription("$label for Back extension set 1")
            val width = field.getUnclippedBoundsInRoot().let { bounds -> bounds.right - bounds.left }
            assertTrue("Inputs must be narrower: $label=$width", width <= maximum)
            val height = field.getUnclippedBoundsInRoot().let { bounds -> bounds.bottom - bounds.top }
            assertTrue("Compact entry fields must be no taller than 48dp: $label=$height", height.value <= 48f + 0.001f)
            field
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertTrue(fields.zipWithNext().all { (left, right) ->
            kotlin.math.abs(left.center.y - right.center.y) < 2f && left.right <= right.left
        })
        val completion = compose.onNodeWithContentDescription("Complete set 1 for Back extension and open next empty set")
            .assertHasClickAction().fetchSemanticsNode().boundsInRoot
        assertTrue("Tick remains beside the compact primary row", kotlin.math.abs(completion.center.y - fields.first().center.y) < 8f)
        assertTrue(completion.left >= fields.last().right)
        val bounds = compose.onNodeWithContentDescription("Set 1 for Back extension").getUnclippedBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue("Simple tile stays compact with its variant header and inline previous fields, was $height", height <= 140.dp)
        for (ordinal in 2..3) {
            val empty = compose.onNodeWithContentDescription("Set $ordinal for Back extension")
                .performScrollTo().getUnclippedBoundsInRoot()
            assertTrue("Every simple tile remains compact with editable fields", empty.bottom - empty.top <= 160.dp)
            compose.onNodeWithContentDescription("Resistance for Back extension set $ordinal").assertIsEnabled()
            compose.onNodeWithContentDescription("Reps for Back extension set $ordinal").assertIsEnabled()
            compose.onNodeWithContentDescription("Complete set $ordinal for Back extension and open next empty set").assertIsNotEnabled()
        }
    }

    @Test
    fun activeAndInactiveTilesKeepAContrastingBoundary() {
        val workout = start()
        show(workout)
        fun assertBoundary() {
            val tile = compose.onNodeWithContentDescription("Set 1 for Handstand")
                .performScrollTo().captureToImage().toPixelMap()
            val border = tile[0, tile.height / 2].luminance()
            val fill = tile[4, tile.height / 2].luminance()
            val contrast = (maxOf(border, fill) + 0.05f) / (minOf(border, fill) + 0.05f)
            assertTrue("Set outline must remain visible: contrast=$contrast", contrast >= 3f)
        }
        assertBoundary()
        compose.onNodeWithContentDescription("Set 2 for Handstand")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        assertBoundary()
    }

    @Test
    fun everyTileKeepsDirectFieldsAndVariantHeaderWithoutExpansion() {
        val workout = start()
        show(workout)
        val tile = compose.onNodeWithContentDescription("Set 1 for Handstand")
        for (ordinal in 1..3) {
            compose.onNodeWithContentDescription("Time Under Tension for Handstand set $ordinal").assertIsEnabled()
            compose.onNodeWithContentDescription("Complete set $ordinal for Handstand and open next empty set").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Change variant for Handstand set $ordinal").assertExists()
        }
        val bounds = tile.getUnclippedBoundsInRoot()
        val swap = compose.onNodeWithContentDescription("Change variant for Handstand set 1").getUnclippedBoundsInRoot()
        val tick = compose.onNodeWithContentDescription("Complete set 1 for Handstand and open next empty set").getUnclippedBoundsInRoot()
        assertTrue("Variant selector starts at the tile's leading edge", swap.left < bounds.left + (bounds.right - bounds.left) / 2)
        assertTrue("Variant selector is above completion", swap.top < tick.top)
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 2")
            .performScrollTo().performTextInput("4")
        compose.onNodeWithContentDescription("Complete set 2 for Handstand and open next empty set").assertIsEnabled()
        compose.onNodeWithContentDescription("Complete set 1 for Handstand and open next empty set").assertIsNotEnabled()
        tile.performScrollTo().performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) }
        compose.onNodeWithContentDescription("Open set 1 for Handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close set 1 for Handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 2").assertTextContains("4")
    }

    @Test
    fun configuredOrderRendersCompactFieldsAndKeepsTimingTogetherAtLargeFont() {
        val workout = start()
        runBlocking {
            val variant = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(variant.copy(inputConfig = ExerciseInputConfig(
                timeHeld = true, manyTuts = true, timeUnderTension = true,
                measurementOrder = listOf(ExerciseMeasurement.RPE, ExerciseMeasurement.TOTAL_TIME, ExerciseMeasurement.TUT, ExerciseMeasurement.REPS),
            ).encode()))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                VibeCheckTheme { Box(Modifier.width(320.dp)) { WorkoutEditor(vm, workout, {}, {}) } }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Time Under Tension for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("RPE for Handstand set 1").performScrollTo()
        val ordered = listOf("Time Under Tension", "Total Time").map {
            compose.onNodeWithContentDescription("$it for Handstand set 1").getUnclippedBoundsInRoot()
        }
        assertTrue("Configured fields must wrap in order: $ordered", ordered.zipWithNext().all { (a, b) ->
            a.top < b.top || a.right <= b.left
        })
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").performScrollTo().performTextInput("30")
        compose.onNodeWithContentDescription("Open Total Time stopwatch for Handstand set 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performScrollTo().performTextInput("4")
        compose.onNodeWithContentDescription("Add TUT entry for Handstand set 1").performScrollTo().performClick()
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").performScrollTo().performTextInput("6")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.holdMillis == 10_000L && it.timeUnderTensionMillis == 30_000L } }
        }
    }

    @Test
    fun changingRecordedSetVariantPreservesAllMeasurements() {
        val workout = start()
        val recorded = runBlocking {
            val config = ExerciseInputConfig(
                timeHeld = true, manyTuts = true, timeUnderTension = true, bandResistance = true).encode()
            listOf("handstand-wall", "handstand-free").forEach { id ->
                db.editorDao().variation(db.editorDao().variationById(id)!!.copy(inputConfig = config))
            }
            val row = db.editorDao().workoutExercises().first().first { it.workoutId == workout && it.actualExerciseId == "core:handstand" }
            val set = emptySet(row.id, 1).copy(
                variationId = "handstand-wall", reps = 5.0, weightKg = 20.0, addedWeightKg = 3.0,
                holdMillis = 10_000L, tutEntriesMillis = encodeTimingMillis(listOf(4_000L, 6_000L)),
                timeUnderTensionMillis = 30_000L, rpe = 8.0,
            )
            db.editorDao().saveSetWithSnapshots(set, listOf("band-yellow", "band-black"), false)
            set
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performScrollTo().performClick()
        compose.onNodeWithText("Freestanding handstand").performClick()
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.id == recorded.id && it.variationId == "handstand-free" } }
        }
        val updated = runBlocking { db.editorDao().sets().first().single { it.id == recorded.id } }
        assertEquals(recorded.reps, updated.reps)
        assertEquals(recorded.weightKg, updated.weightKg)
        assertEquals(recorded.addedWeightKg, updated.addedWeightKg)
        assertEquals(recorded.tutEntriesMillis, updated.tutEntriesMillis)
        assertEquals(recorded.holdMillis, updated.holdMillis)
        assertEquals(recorded.timeUnderTensionMillis, updated.timeUnderTensionMillis)
        assertEquals(recorded.rpe, updated.rpe)
        assertEquals(listOf("band-yellow", "band-black"), runBlocking { db.editorDao().setBands().first().filter { it.setId == recorded.id }.map { it.bandId } })
        compose.onNodeWithContentDescription("Set 1 for Handstand").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Editing, collapsed"),
        )
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").assertTextContains("6")
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performScrollTo().performClick()
        variantOption("Freestanding handstand").assertIsSelected()
    }

    @Test
    fun inheritedMeasurementOrderIsFrozenInRecordedVariationSnapshots() {
        start()
        runBlocking {
            val parent = db.editorDao().exerciseById("core:handstand")!!
            val inherited = listOf(ExerciseMeasurement.RPE, ExerciseMeasurement.TUT)
            db.editorDao().exercise(parent.copy(inputConfig = ExerciseInputConfig(
                timeHeld = true, measurementOrder = inherited,
            ).encode()))
            val variant = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(variant.copy(inputConfig = ExerciseInputConfig(timeHeld = true).encode()))
            val row = db.editorDao().workoutExercises().first().first { it.actualExerciseId == parent.id }
            db.editorDao().workoutExercise(row.copy(inputConfig = ExerciseInputConfig(
                timeHeld = true, measurementOrder = inherited,
            ).encode()))
            val set = emptySet(row.id, 1).copy(variationId = variant.id, holdMillis = 4_000L)
            db.editorDao().saveSetWithSnapshots(set, emptyList(), false)
            val saved = db.editorDao().sets().first().single { it.id == set.id }
            assertEquals(inherited, ExerciseInputConfig.decode(saved.variationInputConfigSnapshot, variant.trackingType).measurementOrder)
            db.editorDao().exercise(parent.copy(inputConfig = ExerciseInputConfig(
                timeHeld = true, measurementOrder = inherited.reversed(),
            ).encode()))
            db.editorDao().saveSetWithSnapshots(saved.copy(holdMillis = 5_000L), emptyList(), false)
            assertEquals(saved.variationInputConfigSnapshot, db.editorDao().sets().first().single { it.id == set.id }.variationInputConfigSnapshot)
        }
    }

    @Test
    fun editingTimingPreservesRepetitionsAndLoad() {
        val workout = start()
        runBlocking {
            val row = db.editorDao().workoutExercises().first().first { it.workoutId == workout && it.actualExerciseId == "core:handstand" }
            val variant = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(variant.copy(inputConfig = ExerciseInputConfig(weightUnit = "kg", timeHeld = true, manyTuts = true).encode()))
            db.editorDao().set(emptySet(row.id, 1).copy(variationId = variant.id, weightKg = 20.0, reps = 5.0, holdMillis = 4_000L))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextReplacement("9")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.holdMillis == 9_000L && it.reps == 5.0 && it.weightKg == 20.0 } }
        }
    }

    @Test
    fun bandSelectionIsExplicitAndSurvivesCollapsingTheExercise() {
        val workout = start()
        runBlocking {
            val dip = db.editorDao().workoutExercises().first().first { it.workoutId == workout && it.actualExerciseId == "core:dip" }
            db.editorDao().workoutExercise(dip.copy(inputConfig = ExerciseInputConfig(reps = true, bodyweight = true, bandResistance = true).encode()))
        }
        show(workout)
        compose.onNodeWithContentDescription("Workout exercise list")
            .performScrollToNode(hasContentDescription("Set 1 for Dip"))
        compose.onNodeWithContentDescription("Yellow band for Dip set 1").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Yellow band for Dip set 1").assertIsSelected()
        compose.onNodeWithContentDescription("Black band for Dip set 1").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Not selected"),
        )
        compose.onNodeWithContentDescription("Reps for Dip set 1").performScrollTo().performTextInput("5")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().setBands().first().any { it.bandId == "band-yellow" } }
        }
        compose.onNodeWithContentDescription("Reps for Dip set 1").assertTextContains("5")
        compose.onNodeWithContentDescription("Exercise heading for Dip").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Exercise heading for Dip").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Dip").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Yellow band for Dip set 1").performScrollTo().assertIsSelected()
    }

    @Test
    fun historyShowsBothRecordedTimingMeasurementsWithoutOpeningEdit() {
        val workout = start()
        runBlocking {
            val row = db.editorDao().workoutExercises().first().first { it.workoutId == workout && it.actualExerciseId == "core:handstand" }
            val session = db.editorDao().workouts().first().first { it.id == workout }
            db.editorDao().workout(session.copy(status = "FINISHED", finishedAt = System.currentTimeMillis()))
            db.editorDao().set(emptySet(row.id, 1).copy(holdMillis = 3_000, timeUnderTensionMillis = 12_000))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { HistoryScreen(vm, {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("3s · Total 12s").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("3s · Total 12s").assertExists()
    }

    @Test
    fun oneManyTogglePersistsAndRestoresIntoFreshSeededDatabase() {
        start()
        val vm = lifecycle.own(EditorViewModel(db))
        val planche = runBlocking { db.editorDao().exerciseById("core:planche")!! }
        compose.setContent {
            VibeCheckTheme {
                ExerciseSettingsDialog(planche, {}) { vm.saveExerciseSettings(it) }
            }
        }
        compose.onNodeWithContentDescription("Hold duration").assertIsSelected()
        compose.onNodeWithContentDescription("Skill hold").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(15_000) {
            runBlocking { ExerciseInputConfig.decode(db.editorDao().exerciseById(planche.id)!!.inputConfig, planche.trackingType).manyTuts }
        }
        runBlocking {
            val backup = DataTransfer.export(db)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val restored = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
            try {
                DatabaseSeeder(context, restored).seedIfNeeded()
                DataTransfer.import(restored, backup)
                assertTrue(ExerciseInputConfig.decode(restored.editorDao().exerciseById(planche.id)!!.inputConfig, planche.trackingType).manyTuts)
            } finally {
                restored.close()
            }
        }
    }

    @Test
    fun changingVariantDoesNotReinterpretRepsAsWeight() {
        val workout = start()
        runBlocking {
            val variation = db.editorDao().variationById("handstand-free")!!
            db.editorDao().variation(variation.copy(inputConfig = ExerciseInputConfig(weightUnit = "kg", reps = true).encode()))
            val wall = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(wall.copy(inputConfig = ExerciseInputConfig(reps = true).encode()))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Reps for Handstand set 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Reps for Handstand set 1").performTextInput("5")
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performClick()
        compose.onNodeWithText("Freestanding handstand").performClick()
        compose.onNodeWithContentDescription("Reps for Handstand set 1").assertTextContains("5")
        compose.onNodeWithContentDescription("Resistance for Handstand set 1").assertTextEquals("kg", "")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.variationId == "handstand-free" && it.reps == 5.0 && it.weightKg == null } }
        }
    }

    @Test
    fun romOnlySetPersistsAndCanBeCompletedWithoutOtherMeasurements() {
        val workout = start()
        runBlocking {
            val wall = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(wall.copy(inputConfig = ExerciseInputConfig()
                .withResistance(ResistanceMetric.BODYWEIGHT)
                .withMeasurement(MeasurementMetric.RANGE_OF_MOTION).encode()))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Range of motion for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Range of motion for Handstand set 1").performTextInput("45")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.romValue == 45.0 && it.romUnit == "degrees" && it.reps == null && it.holdMillis == null } }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            compose.onNodeWithContentDescription("Complete set 1 for Handstand and open next empty set").performClick()
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").assertExists()
        } finally {
            RestTimer.cancel(context)
        }
    }

    @Test
    fun explicitMetricKeysControlLoggingDespiteLegacyBackingFlags() {
        val workout = start()
        runBlocking {
            val wall = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(wall.copy(
                inputConfig = "resistanceMetric=KILOGRAMS;measurementMetric=REPETITIONS;reps=false",
            ))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Resistance for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Resistance for Handstand set 1").performTextInput("20")
        compose.onNodeWithContentDescription("Reps for Handstand set 1").performTextInput("6")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.weightKg == 20.0 && it.reps == 6.0 && it.holdMillis == null } }
        }
        compose.onNodeWithContentDescription("Complete set 1 for Handstand and open next empty set").assertIsEnabled()
    }

    @Test
    fun worstCaseAndSimpleLoggerRenderInTheRealExerciseContainer() {
        val workout = start()
        runBlocking {
            repeat(4) { index ->
                db.editorDao().band(BandEntity("review-band-$index", "Custom $index", 4.0 + index, 0xFF5085D6L))
            }
            val config = ExerciseInputConfig(
                bandResistance = true, timeHeld = true, timeUnderTension = true, manyTuts = true,
            ).encode()
            val exercise = db.editorDao().exerciseById("core:handstand")!!.copy(
                id = "review-skill-exercise", canonicalName = "Skill hold demo", isCustom = true, inputConfig = config,
            )
            db.editorDao().exercise(exercise)
            val variant = db.editorDao().variationById("handstand-wall")!!.copy(
                id = "review-skill-variant", exerciseId = exercise.id, name = "Test variant",
                isSeeded = false, inputConfig = config,
            )
            db.editorDao().variation(variant)
            val rows = db.editorDao().workoutExercises().first().filter { it.workoutId == workout }
            rows.forEach { db.editorDao().workoutExercise(it.copy(position = it.position + 1)) }
            val row = db.editorDao().workoutExercises().first().first {
                it.workoutId == workout && it.actualExerciseId == "core:handstand"
            }.copy(id = "review-skill-row", plannedExerciseId = exercise.id, actualExerciseId = exercise.id,
                exerciseName = exercise.canonicalName, inputConfig = config, position = 0)
            db.editorDao().workoutExercise(row)
            db.editorDao().saveSetWithSnapshots(emptySet(row.id, 1).copy(
                variationId = variant.id, holdMillis = 36_000L,
                tutEntriesMillis = encodeTimingMillis(List(8) { (it + 1) * 1_000L }),
                timeUnderTensionMillis = 45_000L, rpe = 8.0,
            ), listOf("band-yellow", "band-black"), false)
        }
        val vm = lifecycle.own(EditorViewModel(db))
        val font = androidx.compose.runtime.mutableFloatStateOf(1f)
        val width = androidx.compose.runtime.mutableStateOf(411.dp)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, font.floatValue)) {
                VibeCheckTheme { Box(Modifier.width(width.value)) { WorkoutEditor(vm, workout, {}, {}) } }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("TUT attempt 8 for Skill hold demo set 1").fetchSemanticsNodes().isNotEmpty()
        }
        fun capture(name: String) {
            val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
            file.outputStream().use {
                assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        }
        compose.onNodeWithContentDescription("Complete set 1 for Skill hold demo and open next empty set").assertIsEnabled()
        val groups = listOf("Yellow band", "TUT attempt 1", "RPE").map {
            compose.onNodeWithContentDescription("$it for Skill hold demo set 1").getUnclippedBoundsInRoot()
        } + compose.onNodeWithContentDescription("Complete set 1 for Skill hold demo and open next empty set").getUnclippedBoundsInRoot()
        assertTrue("Worst-case groups must share one body row", (groups.maxOf { it.top } - groups.minOf { it.top }).value <= 8f)
        assertTrue("Worst-case groups must not overlap", groups.zipWithNext().all { (a, b) -> a.right <= b.left })
        capture("composable-worst-case.png")
        compose.onNodeWithContentDescription("Workout exercise list").performScrollToNode(hasContentDescription("Exercise heading for Dip"))
        capture("composable-simple.png")
        compose.runOnIdle { font.floatValue = 2f; width.value = 320.dp }
        compose.onNodeWithContentDescription("Workout exercise list").performScrollToNode(hasContentDescription("Set 1 for Skill hold demo"))
        compose.onNodeWithContentDescription("TUT attempt 8 for Skill hold demo set 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Total Time for Skill hold demo set 1").performScrollTo().assertIsDisplayed()
        capture("composable-large-font.png")
    }

    @Test
    fun variantRoundTripRestoresRawValuesUnitsAndSnapshotsAcrossRecreationAndCompletedChanges() {
        val workout = start()
        val original = runBlocking {
            val wall = db.editorDao().variationById("handstand-wall")!!
            val free = db.editorDao().variationById("handstand-free")!!
            val reps = ExerciseInputConfig().withResistance(ResistanceMetric.BODYWEIGHT)
                .withMeasurement(MeasurementMetric.REPETITIONS).encode()
            db.editorDao().variation(wall.copy(inputConfig = reps))
            db.editorDao().variation(free.copy(inputConfig = ExerciseInputConfig()
                .withResistance(ResistanceMetric.BODYWEIGHT)
                .withMeasurement(MeasurementMetric.RANGE_OF_MOTION).encode()))
            val row = db.editorDao().workoutExercises().first().first {
                it.workoutId == workout && it.actualExerciseId == "core:handstand"
            }
            emptySet(row.id, 1).copy(
                variationId = wall.id, reps = 7.0, romValue = 12.0, romUnit = "cm",
                variationNameSnapshot = "Recorded wall", variationRankSnapshot = 42,
                variationTrackingTypeSnapshot = "BODYWEIGHT_REPS", variationInputConfigSnapshot = reps,
            ).also { db.editorDao().set(it) }
        }
        val vm = lifecycle.own(EditorViewModel(db))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Reps for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        fun select(name: String) {
            compose.onNodeWithContentDescription("Change variant for Handstand set 1").performScrollTo().performClick()
            variantOption(name).performClick()
        }
        compose.onNodeWithContentDescription("Reps for Handstand set 1").performTextReplacement("7.")
        select("Freestanding handstand")
        compose.onNodeWithContentDescription("Range of motion for Handstand set 1").assertTextContains("cm", substring = true)
            .performTextReplacement("45")
        compose.waitUntil(15_000) { runBlocking { db.editorDao().setById(original.id)?.romValue == 45.0 } }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Range of motion for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        select("Wall handstand")
        compose.onNodeWithContentDescription("Reps for Handstand set 1").assertTextContains("7.")
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Recorded wall")
        compose.onNodeWithContentDescription("Complete set 1 for Handstand and open next empty set").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Reps for Handstand set 1").performTextReplacement("8")
        compose.waitUntil(15_000) { runBlocking { db.editorDao().setById(original.id)?.reps == 8.0 } }
        val saved = runBlocking { db.editorDao().setById(original.id)!! }
        assertEquals(original.variationNameSnapshot, saved.variationNameSnapshot)
        assertEquals(original.variationRankSnapshot, saved.variationRankSnapshot)
        assertEquals(original.variationTrackingTypeSnapshot, saved.variationTrackingTypeSnapshot)
        assertEquals("cm", saved.romUnit)
        assertEquals(45.0, saved.romValue!!, 0.0)
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            compose.onNodeWithContentDescription("Complete set 1 for Handstand and open next empty set").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("Edit completed set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() &&
                    context.getSharedPreferences("settings", 0).getLong("restEnd", 0) > System.currentTimeMillis()
            }
            val rest = context.getSharedPreferences("settings", 0).getLong("restEnd", 0)
            assertTrue(rest > System.currentTimeMillis())
            select("Freestanding handstand")
            compose.onNodeWithContentDescription("Range of motion for Handstand set 1").assertTextContains("cm", substring = true)
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").assertExists()
            assertEquals(rest, context.getSharedPreferences("settings", 0).getLong("restEnd", 0))
        } finally {
            RestTimer.cancel(context)
        }
    }
}

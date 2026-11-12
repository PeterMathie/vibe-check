package com.petermathie.vibecheck

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.data.DataTransfer
import com.petermathie.vibecheck.data.TrainingRepository
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.ExerciseMeasurement
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
            compose.onNodeWithText("10 s TUT").assertExists()
            compose.onNodeWithText("30.0 s Total").assertExists()
            compose.onNodeWithContentDescription("RPE for Handstand set 1").assertDoesNotExist()
            compose.onNodeWithContentDescription("Yellow band for Handstand set 1").assertIsNotEnabled().assertIsSelected()
            compose.onNodeWithContentDescription("Red band for Handstand set 1").assertDoesNotExist()
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").assertIsSelected()
            compose.runOnIdle {
                assertTrue(context.getSharedPreferences("workout_logging", 0).getStringSet("locked_sets", emptySet())!!.contains(original.id))
                generation.intValue++
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("Yellow band for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").assertExists()
            compose.onNodeWithContentDescription("TUT attempt 1 for Handstand set 1").assertDoesNotExist()
            val saved = runBlocking { db.editorDao().setById(original.id)!! }
            assertEquals(original, saved)
            RestTimer.cancel(context)
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").performClick()
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
    fun abbreviatedBandsShareExpandedActionRowAt320DpAndDoubleFontScale() {
        val workout = start()
        runBlocking {
            val variant = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(variant.copy(inputConfig = ExerciseInputConfig(
                bodyweight = true, reps = true, bandResistance = true, timeHeld = true,
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
        compose.onNodeWithContentDescription("Reps for Handstand set 1").assertIsEnabled()
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertDoesNotExist()
        expandSet("Handstand", 1)
        val labels = listOf("Yellow" to "Yw", "Red" to "Rd", "Black" to "Bl", "Purple" to "Pr")
        val bandBounds = labels.map { (name, abbreviation) ->
            compose.onNodeWithContentDescription("$name band for Handstand set 1")
                .assertTextContains(abbreviation).getUnclippedBoundsInRoot()
        }
        val swap = compose.onNodeWithContentDescription("Change variant for Handstand set 1").getUnclippedBoundsInRoot()
        val tick = compose.onNodeWithContentDescription("Complete set 1 for Handstand and open next empty set").getUnclippedBoundsInRoot()
        val bounds = listOf(swap) + bandBounds + tick
        assertTrue("Swap, four bands and tick must fit one row: $bounds", bounds.zipWithNext().all { (left, right) ->
            kotlin.math.abs((left.top + left.bottom).value - (right.top + right.bottom).value) < 2f && left.right <= right.left
        })
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
            VibeCheckTheme { Box(Modifier.height(480.dp)) { WorkoutEditor(vm, workout, {}, {}) } }
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
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("1")
        for (index in 2..8) {
            compose.onNodeWithContentDescription("Add TUT entry for Handstand set 1").performScrollTo().performClick()
            compose.onNodeWithContentDescription("TUT attempt $index for Handstand set 1").performScrollTo().performTextInput(index.toString())
        }
        assertFourByTwoTimingGrid()
        compose.onNodeWithContentDescription("Add TUT entry for Handstand set 1").assertIsNotEnabled()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("TUT total for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("TUT total for Handstand set 1").assertTextContains("TUT 36s", substring = true)
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { timingMillis(it.tutEntriesMillis) == List(8) { i -> (i + 1) * 1000L } && it.holdMillis == 36_000L } }
        }
        val backup = runBlocking { DataTransfer.export(db) }
        runBlocking { DataTransfer.import(db, backup) }
        assertTrue(runBlocking { db.editorDao().sets().first().any { timingMillis(it.tutEntriesMillis).size == 8 && it.holdMillis == 36_000L } })
    }

    @Test
    fun fourByTwoTimingGridRemainsUsableAt320DpAndDoubleFontScale() {
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
        assertFourByTwoTimingGrid()
        compose.onNode(hasText("TUT (s)") and hasAnyAncestor(hasContentDescription("Set 1 for Handstand"))).assertExists()
        compose.onNodeWithContentDescription("TUT attempt 8 for Handstand set 1").performTextReplacement("9")
        compose.waitUntil(15_000) {
            runBlocking {
                db.editorDao().sets().first().any {
                    timingMillis(it.tutEntriesMillis) == listOf(1_000L, 2_000L, 3_000L, 4_000L, 5_000L, 6_000L, 7_000L, 9_000L)
                }
            }
        }
    }

    private fun assertFourByTwoTimingGrid() {
        compose.onNodeWithContentDescription("TUT attempt 1 for Handstand set 1").performScrollTo()
        val bounds = (1..8).map {
            compose.onNodeWithContentDescription("TUT attempt $it for Handstand set 1")
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        bounds.chunked(4).forEach { row ->
            assertTrue(row.zipWithNext().all { (left, right) ->
                kotlin.math.abs(left.center.y - right.center.y) < 2f && left.right <= right.left
            })
        }
        repeat(4) { column ->
            assertTrue(kotlin.math.abs(bounds[column].left - bounds[column + 4].left) < 2f)
            assertTrue(kotlin.math.abs(bounds[column].width - bounds[column + 4].width) < 2f)
            assertTrue(bounds[column].bottom <= bounds[column + 4].top)
        }
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
        compose.onNodeWithText("One-entry mode. Previously recorded attempts are retained.").assertExists()
        expandSet("Handstand", 2)
        compose.onNodeWithContentDescription("Change variant for Handstand set 2").performScrollTo().performClick()
        compose.onNodeWithText("Wall handstand").assertIsSelected().performClick()
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.variationId == "handstand-free" && it.holdMillis == 10_000L && timingMillis(it.tutEntriesMillis) == listOf(4_000L, 6_000L) } }
        }
    }

    @Test
    fun completingTileSelectsNextEmptyAndExercisesCollapseWithoutLosingValues() {
        val workout = start()
        show(workout)
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("5")
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
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").performTextInput("30")
        compose.waitUntil(15_000) {
            runBlocking {
                db.editorDao().sets().first().any {
                    timingMillis(it.tutEntriesMillis) == listOf(4_000L, 6_000L) && it.timeUnderTensionMillis == 30_000L
                }
            }
        }

        compose.onNodeWithContentDescription("TUT total for Handstand set 1").assertTextContains("TUT 10s", substring = true)
        compose.onNodeWithText("4s + 6s = 10s · Total 30s").assertDoesNotExist()
        compose.onNodeWithText("Wall handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("TUT attempt 1 for Handstand set 1").assertTextContains("4")
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").assertTextContains("6")
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").assertTextContains("30")
    }

    @Test
    fun previousResultFollowsTheActiveOrdinalAndVariantWithoutUsingDraftHistory() {
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
        show(workout)
        compose.onNodeWithContentDescription("Previous performance for Handstand set 1").assertDoesNotExist()
        expandSet("Handstand", 1)
        compose.waitUntil(15_000) {
            compose.onAllNodes(
                hasContentDescription("Previous performance for Handstand set 1") and hasText("Previous: 7s"),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Previous performance for Handstand set 1").assertTextEquals("Previous: 7s")
        compose.onNodeWithContentDescription("Previous performance for Handstand set 2").assertDoesNotExist()

        compose.onNodeWithContentDescription("Set 2 for Handstand")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithContentDescription("Previous performance for Handstand set 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Previous performance for Handstand set 2").assertTextEquals("Previous: 9s")
        compose.onNodeWithContentDescription("Change variant for Handstand set 2").performScrollTo().performClick()
        compose.onNodeWithText("Freestanding handstand").performClick()
        compose.onNodeWithContentDescription("Previous performance for Handstand set 2").assertTextEquals("Previous: 11s")
        compose.onNodeWithText("Previous: 99s").assertDoesNotExist()
    }

    @Test
    fun emptyTilesProvideFieldsAndLabelledFooterActionsShareARow() {
        val workout = start()
        show(workout)
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
    fun variantNamesAppearOnlyInThePickerWithTheCurrentChoiceMarked() {
        val workout = start()
        show(workout)
        compose.onNodeWithText("Wall handstand").assertDoesNotExist()
        compose.onNodeWithText("Freestanding handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertDoesNotExist()
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performClick()
        compose.onNodeWithText("Wall handstand").assertIsSelected()
        compose.onNodeWithText("Freestanding handstand").assertIsNotSelected().performClick()
        compose.onNodeWithText("Freestanding handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performClick()
        compose.onNodeWithText("Freestanding handstand").assertIsSelected()
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
        compose.onNodeWithText("10 s TUT").assertExists()
    }

    @Test
    fun simpleExerciseKeepsInputsInOneRowWithoutASeparateCompletionRow() {
        val workout = start("demo-day-pull")
        show(workout)
        compose.onNodeWithContentDescription("Workout exercise list")
            .performScrollToNode(hasContentDescription("Set 1 for Back extension"))
        val fields = listOf("Resistance", "Reps", "RPE").map {
            val field = compose.onNodeWithContentDescription("$it for Back extension set 1")
            val width = field.getUnclippedBoundsInRoot().let { bounds -> bounds.right - bounds.left }
            assertTrue("Inputs should not stretch across unused row space: $it=$width", width <= 96.dp)
            val height = field.getUnclippedBoundsInRoot().let { bounds -> bounds.bottom - bounds.top }
            assertTrue("Compact entry fields must be no taller than 48dp: $it=$height", height <= 48.dp)
            field
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertTrue(fields.zipWithNext().all { (left, right) ->
            kotlin.math.abs(left.center.y - right.center.y) < 2f && left.right <= right.left
        })
        val completion = compose.onNodeWithContentDescription("Complete set 1 for Back extension and open next empty set")
            .assertHasClickAction().fetchSemanticsNode().boundsInRoot
        assertTrue(completion.bottom <= fields.first().top)
        val bounds = compose.onNodeWithContentDescription("Set 1 for Back extension").getUnclippedBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue("Simple set tile should be at most 160dp, was $height", height <= 160.dp)
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
    fun everyTileHasDirectFieldsAndTickButOnlyExpandedTilesShowVariantControl() {
        val workout = start()
        show(workout)
        val tile = compose.onNodeWithContentDescription("Set 1 for Handstand")
        for (ordinal in 1..3) {
            compose.onNodeWithContentDescription("Time Under Tension for Handstand set $ordinal").assertIsEnabled()
            compose.onNodeWithContentDescription("Complete set $ordinal for Handstand and open next empty set").assertIsNotEnabled()
        }
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertDoesNotExist()
        expandSet("Handstand", 1)
        val bounds = tile.getUnclippedBoundsInRoot()
        val swap = compose.onNodeWithContentDescription("Change variant for Handstand set 1").getUnclippedBoundsInRoot()
        val tick = compose.onNodeWithContentDescription("Complete set 1 for Handstand and open next empty set").getUnclippedBoundsInRoot()
        assertTrue(swap.left < bounds.left + (bounds.right - bounds.left) / 2)
        assertTrue(tick.left > swap.right)
        assertEquals(swap.top.value, tick.top.value, 0.5f)
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
                reps = true, timeHeld = true, manyTuts = true, timeUnderTension = true,
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
        compose.onNodeWithContentDescription("RPE for Handstand set 1").performScrollTo()
        val ordered = listOf("Total Time", "Time Under Tension", "Reps", "RPE").map {
            compose.onNodeWithContentDescription("$it for Handstand set 1").getUnclippedBoundsInRoot()
        }
        assertTrue("Configured fields must wrap in order: $ordered", ordered.zipWithNext().all { (a, b) ->
            a.top < b.top || a.right <= b.left
        })
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").performScrollTo().performTextInput("30")
        compose.onNodeWithContentDescription("Open Total Time stopwatch for set 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performScrollTo().performTextInput("4")
        compose.onNodeWithContentDescription("Add TUT entry for Handstand set 1").performScrollTo().performClick()
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").performScrollTo().performTextInput("6")
        compose.onNodeWithContentDescription("Reps for Handstand set 1").performScrollTo().performTextInput("5")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.reps == 5.0 && it.holdMillis == 10_000L && it.timeUnderTensionMillis == 30_000L } }
        }
    }

    @Test
    fun changingRecordedSetVariantPreservesAllMeasurements() {
        val workout = start()
        val recorded = runBlocking {
            val config = ExerciseInputConfig(weightUnit = "kg", reps = true, addedWeight = true,
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
        expandSet("Handstand", 1)
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
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Editing, expanded"),
        )
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").assertTextContains("6")
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performScrollTo().performClick()
        compose.onNodeWithText("Freestanding handstand").assertIsSelected()
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
            db.editorDao().variation(variant.copy(inputConfig = ExerciseInputConfig(weightUnit = "kg", reps = true, timeHeld = true, manyTuts = true).encode()))
            db.editorDao().set(emptySet(row.id, 1).copy(variationId = variant.id, weightKg = 20.0, reps = 5.0, holdMillis = 4_000L))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Set 1 for Handstand").performClick()
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
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Not selected, raised"),
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
        compose.onNodeWithContentDescription("Multiple TUT entries per set").assertIsOff().performClick()
        compose.onNodeWithText("Many · up to 8").assertExists()
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
}

package com.petermathie.vibecheck

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.data.TrainingRepository
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.AppFabHostState
import com.petermathie.vibecheck.ui.AppFloatingAction
import com.petermathie.vibecheck.ui.Destination
import com.petermathie.vibecheck.ui.LocalAppFabClearance
import com.petermathie.vibecheck.ui.LocalAppFabHost
import com.petermathie.vibecheck.ui.WorkoutEditor
import com.petermathie.vibecheck.ui.emptySet
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutLoggingUiTest {
    private lateinit var database: VibeDatabase

    @get:Rule
    val lifecycle = ComposeRoomLifecycleRule {
        if (::database.isInitialized) database else null
    }
    private val compose get() = lifecycle.compose

    @Test
    fun rpeRejectsValuesAboveTenAndExplainsRepsInReserve() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val workoutId = startPushWorkout(context)
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { WorkoutEditor(viewModel, workoutId, {}, {}) } }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("RPE for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        val rpe = compose.onNodeWithContentDescription("RPE for Handstand set 1").performScrollTo()
        rpe.performTextInput("9")
        rpe.performTextInput("9")
        rpe.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("9")))
        rpe.performTextReplacement("10")
        rpe.performTextInput("1")
        rpe.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("10")))
        compose.onNodeWithText("Value is too large").assertDoesNotExist()
        rpe.performTextReplacement("8.5")
        rpe.assertTextContains("8.5")
        rpe.performTextClearance()
        compose.onNodeWithContentDescription("About RPE for Handstand set 1").performScrollTo().performTouchInput { click() }
        compose.onNodeWithText("RIR").assertIsDisplayed()
        compose.onNodeWithText("~2").assertIsDisplayed()
        compose.onNodeWithText("0-4").assertIsDisplayed()
        compose.onNodeWithText("Many").assertIsDisplayed()
        compose.onNodeWithText("Got it").performClick()
        compose.onNodeWithText("RIR").assertDoesNotExist()
        rpe.performScrollTo().assertExists()
    }

    @Test
    fun workoutLoggerHidesSetConfigurationAndPersistsExerciseNotes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val workoutId = startPushWorkout(context)
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                val host = remember { AppFabHostState(Destination.ACTIVE_WORKOUT) }
                CompositionLocalProvider(
                    LocalAppFabHost provides host,
                    LocalAppFabClearance provides 88.dp,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        WorkoutEditor(viewModel, workoutId, {}, {})
                        host.action?.takeIf { it.visible }?.let {
                            AppFloatingAction(
                                it,
                                host,
                                Modifier.align(Alignment.BottomStart).padding(16.dp),
                            )
                        }
                    }
                }
            }
        }

        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Bodyweight:", substring = true).assertDoesNotExist()
        val list = compose.onNodeWithContentDescription("Workout exercise list")
        list.performScrollToNode(hasContentDescription("Add exercise to workout"))
        compose.onNodeWithContentDescription("Add exercise to workout").assertIsDisplayed().performClick()
        compose.onNodeWithText("Choose exercise").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add exercise to workout").assertIsNotEnabled()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Add exercise to workout").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Workout notes").assertDoesNotExist()
        compose.onNodeWithText("Start hold timer").assertDoesNotExist()
        compose.onNodeWithContentDescription("Save set 1 for Handstand").assertDoesNotExist()
        list.performScrollToNode(hasContentDescription("Show notes for Handstand"))
        compose.onNodeWithContentDescription("Show notes for Handstand").performClick()
        compose.onAllNodes(hasText("Exercise notes") and hasSetTextAction())[0].performTextInput("Shoulders stable")
        compose.waitUntil(15_000) {
            runBlocking { database.editorDao().workoutExercises().first().any { it.notes == "Shoulders stable" } }
        }

        list.performScrollToNode(hasContentDescription("Set 1 for Bench press"))
        compose.onNodeWithContentDescription("Set details for Bench press set 1").assertDoesNotExist()
        compose.onNodeWithText("Set details").assertDoesNotExist()
        assertTrue(compose.onAllNodesWithText("kg").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Resistance kg").assertDoesNotExist()
    }

    @Test
    fun holdResultCanBeCorrectedThroughTheSameWorkoutRow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val workoutId = startPushWorkout(context)
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { WorkoutEditor(viewModel, workoutId, {}, {}) } }

        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Time Under Tension for Handstand set 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("8")
        val handstandId = runBlocking { database.editorDao().workoutExercises().first().first { it.workoutId == workoutId && it.actualExerciseId == "core:handstand" }.id }
        compose.waitUntil(15_000) { runBlocking { database.editorDao().sets().first().any { it.workoutExerciseId == handstandId && it.holdMillis == 8_000L } } }

        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextClearance()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("10")

        compose.waitUntil(15_000) { runBlocking { database.editorDao().sets().first().any { it.workoutExerciseId == handstandId && it.holdMillis == 10_000L } } }
        assertEquals(0, runBlocking { database.editorDao().sets().first().count { it.workoutExerciseId == handstandId && it.holdMillis == 8_000L } })
    }

    @Test
    fun handstandTimingRatioRetainsEditableDurationWithoutStopwatch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking {
            DatabaseSeeder(context, database).seedIfNeeded()
            val handstand = database.editorDao().programmeEntries("demo-day-push")
                .first { it.exerciseId == "core:handstand" }
            database.editorDao().entry(
                handstand.copy(
                    inputConfig = ExerciseInputConfig(
                        timeHeld = true,
                        timeUnderTension = true,
                    ).encode(),
                ),
            )
        }
        val workoutId = runBlocking {
            TrainingRepository(database, database.programmeDao(), database.workoutDao(), database.trackerDao(), context)
                .startWorkout("demo-day-push")
        }
        runBlocking {
            val row = database.editorDao().workoutExercises().first().first {
                it.workoutId == workoutId && it.actualExerciseId == "core:handstand"
            }
            database.editorDao().set(emptySet(row.id, 1).copy(
                variationId = "handstand-wall", timeUnderTensionMillis = 30_000L,
            ))
        }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { WorkoutEditor(viewModel, workoutId, {}, {}) } }

        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Set 1 for Handstand")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        assertHandstandQuantitativeFieldsRemainOrdered()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("12")
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").assertTextContains("30")
        compose.onNodeWithContentDescription("Open Total Time stopwatch for Handstand set 1").assertDoesNotExist()
        compose.onNodeWithText("Total Time stopwatch").assertDoesNotExist()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").assertTextContains("12")
        compose.onNodeWithContentDescription("TUT total for Handstand set 1").assertTextContains("12 s", substring = true)
        compose.onNodeWithText("Freestanding sec").assertDoesNotExist()
        compose.onNodeWithText("Total wall sec").assertDoesNotExist()
        compose.onNodeWithContentDescription("RPE for Handstand set 1").performTextInput("99")
        compose.onNodeWithContentDescription("RPE for Handstand set 1").performTextClearance()
        compose.onNodeWithContentDescription("RPE for Handstand set 1").performTextInput("8")
        awaitHandstandQuantitativeFieldBounds(requireSameRow = false)

        val handstandId = runBlocking {
            database.editorDao().workoutExercises().first()
                .first { it.workoutId == workoutId && it.actualExerciseId == "core:handstand" }.id
        }
        compose.waitUntil(15_000) {
            runBlocking {
                database.editorDao().sets().first().any {
                    it.workoutExerciseId == handstandId &&
                        it.holdMillis == 12_000L &&
                        it.timeUnderTensionMillis == 30_000L &&
                        it.rpe == 8.0
                }
            }
        }
    }

    @Test
    fun handstandVariationSwitchesInputsAndSnapshotsItsConfiguration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking {
            DatabaseSeeder(context, database).seedIfNeeded()
            database.editorDao().entries().first()
                .filter { it.exerciseId == "core:handstand" }
                .forEach { database.editorDao().entry(it.copy(targetSets = 1)) }
        }
        val workoutId = startPushWorkout(context)
        val viewModel = lifecycle.own(EditorViewModel(database))
        runBlocking { viewModel.variations.first { rows -> rows.any { it.id == "handstand-wall" } } }
        compose.setContent { VibeCheckTheme { WorkoutEditor(viewModel, workoutId, {}, {}) } }

        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Variation").assertDoesNotExist()
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Wall handstand")
        compose.onNodeWithContentDescription("Set 1 for Handstand")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performClick()
        compose.onNodeWithText("Freestanding handstand", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").assertExists()
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").assertExists()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("15")

        compose.waitUntil(15_000) {
            runBlocking {
                database.editorDao().sets().first().singleOrNull {
                    it.variationId == "handstand-free" &&
                        it.holdMillis == 15_000L &&
                        it.variationNameSnapshot == "Freestanding handstand" &&
                        it.variationInputConfigSnapshot.contains("timeHeld=false")
                } != null
            }
        }
        assertEquals(
            1,
            runBlocking { database.editorDao().sets().first().count { it.variationId == "handstand-free" } },
        )
    }

    @Test
    fun removedSubstitutionControlsPreserveOriginalHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val workoutId = startPushWorkout(context)
        val bench = runBlocking { database.editorDao().workoutExercises().first().first { it.workoutId == workoutId && it.actualExerciseId == "core:bench-press" } }
        runBlocking { database.editorDao().set(emptySet(bench.id, 1).copy(weightKg = 50.0, reps = 5.0)) }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { WorkoutEditor(viewModel, workoutId, {}, {}) } }

        compose.waitUntil("Workout and Bench row were not ready", 15_000) {
            viewModel.workouts.value.any { it.id == workoutId } &&
                viewModel.workoutLoggingData.value?.exercises?.any { it.id == bench.id } == true &&
                compose.onAllNodesWithContentDescription("Workout exercise list").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Workout exercise list")
            .performScrollToNode(hasContentDescription("Set 1 for Bench press"))
        compose.onNodeWithContentDescription("More actions for Bench press").assertDoesNotExist()
        compose.onNodeWithText("Substitute exercise").assertDoesNotExist()
        assertEquals(6, runBlocking { database.editorDao().workoutExercises().first().count { it.workoutId == workoutId } })
        assertEquals(1, runBlocking { database.editorDao().sets().first().count { it.workoutExerciseId == bench.id && it.reps == 5.0 } })
    }

    @Test
    fun compactLoggerRemainsReachableAt320DpAndDefaultFont() {
        assertCompactLogger(1f)
    }

    @Test
    fun compactLoggerRemainsReachableAt320DpAndMediumFont() {
        assertCompactLogger(1.3f)
    }

    @Test
    fun compactLoggerRemainsReachableAt320DpAndLargeFont() {
        assertCompactLogger(2f)
    }

    private fun assertCompactLogger(fontScale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val workoutId = startPushWorkout(context)
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VibeCheckTheme {
                    Box(androidx.compose.ui.Modifier.width(320.dp)) {
                        WorkoutEditor(viewModel, workoutId, {}, {})
                    }
                }
            }
        }

        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Set 1 for Handstand")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Change variant for Handstand set 1")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("s")
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").assertExists()
        compose.onNodeWithContentDescription("RPE for Handstand set 1")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("RPE")
        assertHandstandQuantitativeFieldsRemainOrdered()
        compose.onNodeWithContentDescription("Add set for Handstand").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Show notes for Handstand").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Finish workout"))
        compose.onNodeWithText("Finish workout").assertIsDisplayed()
    }

    private fun assertHandstandQuantitativeFieldsRemainOrdered() {
        assertOrderedAndNonOverlapping(awaitHandstandQuantitativeFieldBounds(requireSameRow = false))
    }

    private fun awaitHandstandQuantitativeFieldBounds(requireSameRow: Boolean): List<Rect> {
        var previousBounds: List<Rect>? = null
        var stableSamples = 0
        compose.waitUntil("Handstand quantitative fields did not settle into their expected layout", 15_000) {
            val bounds = handstandQuantitativeFieldBounds() ?: return@waitUntil false
            val aligned = bounds.drop(1).all {
                kotlin.math.abs(bounds.first().center.y - it.center.y) < 2f
            }
            val expectedLayout = !requireSameRow || aligned
            stableSamples = if (expectedLayout && bounds == previousBounds) stableSamples + 1 else 0
            previousBounds = bounds
            stableSamples >= 2
        }
        compose.waitForIdle()
        return checkNotNull(handstandQuantitativeFieldBounds())
    }

    private fun assertOrderedAndNonOverlapping(bounds: List<Rect>) {
        val (heldBounds, rpeBounds) = bounds
        assertTrue(
            "Timing and RPE remain ordered without overlap: TUT=$heldBounds RPE=$rpeBounds",
            bounds.all { it.width > 0f && it.height > 0f } &&
                listOf(heldBounds).all {
                    it.right <= rpeBounds.left || rpeBounds.right <= it.left ||
                        it.bottom <= rpeBounds.top || rpeBounds.bottom <= it.top
                },
        )
    }

    private fun handstandQuantitativeFieldBounds(): List<Rect>? {
        return listOf(
            "Time Under Tension for Handstand set 1",
            "RPE for Handstand set 1",
        ).map { description ->
            compose.onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes()
                .singleOrNull()
                ?.boundsInRoot
                ?: return null
        }
    }

    @Test
    fun programmedRowsUseSeparateFieldsAndPlusAddsAnExtraSet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val workoutId = startPushWorkout(context)
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { WorkoutEditor(viewModel, workoutId, {}, {}) } }

        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasContentDescription("Set 1 for Bench press"))
        compose.onNodeWithContentDescription("Set 1 for Bench press").assertExists()
        compose.onNodeWithContentDescription("Set 2 for Bench press").assertExists()
        compose.onNodeWithContentDescription("Set 3 for Bench press").assertExists()
        compose.onNodeWithContentDescription("Set 4 for Bench press").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add set for Bench press").assertTextContains("Add set")
        compose.onNodeWithContentDescription("Start 120 second rest for Bench press").assertDoesNotExist()

        compose.onNodeWithContentDescription("Add set for Bench press").performScrollTo().performClick()
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasContentDescription("Set 4 for Bench press"))
        compose.onNodeWithContentDescription("Set 4 for Bench press").assertExists()
        compose.onNodeWithContentDescription("Set 1 for Bench press").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Resistance for Bench press set 1").performTextInput("60")
        compose.onNodeWithContentDescription("Reps for Bench press set 1").performTextInput("5")
        compose.onNodeWithContentDescription("RPE for Bench press set 1").performTextInput("8")
        compose.onNodeWithContentDescription("Save set 1 for Bench press").assertDoesNotExist()

        val benchId = runBlocking { database.editorDao().workoutExercises().first().first { it.workoutId == workoutId && it.actualExerciseId == "core:bench-press" }.id }
        compose.waitUntil(15_000) {
            runBlocking {
                database.editorDao().sets().first().any {
                    it.workoutExerciseId == benchId && it.weightKg == 60.0 && it.reps == 5.0 && it.rpe == 8.0
                }
            }
        }
    }

    @Test
    fun malformedNumericInputShowsAccessibleErrorAndCannotCompleteASet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val workoutId = startPushWorkout(context)
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { WorkoutEditor(viewModel, workoutId, {}, {}) } }

        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasContentDescription("Set 1 for Bench press"))
        compose.onNodeWithContentDescription("Reps for Bench press set 1").performTextInput("6 reps")
        compose.onNodeWithContentDescription("Reps for Bench press set 1")
            .assert(
                androidx.compose.ui.test.SemanticsMatcher.expectValue(
                    androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
                    "Enter a non-negative decimal",
                ),
            )
        compose.onNodeWithText("Complete or correct the highlighted number").assertExists()
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Finish workout"))
        compose.onNodeWithText("Finish workout").assertIsNotEnabled()
    }

    @Test
    fun finishedWorkoutDeletionRequiresExplicitConfirmation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val workoutId = startPushWorkout(context)
        runBlocking {
            val workout = database.editorDao().workouts().first().first { it.id == workoutId }
            database.editorDao().workout(
                workout.copy(status = "FINISHED", finishedAt = System.currentTimeMillis()),
            )
        }
        var deleted = false
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                WorkoutEditor(
                    viewModel,
                    workoutId,
                    onChoose = {},
                    onFinish = {},
                    onDeleted = { deleted = true },
                )
            }
        }

        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Delete workout"))
        compose.onNodeWithText("Delete workout").performClick()
        compose.onNodeWithText("Delete workout?").assertIsDisplayed()
        compose.onNodeWithText("Are you sure you want to delete", substring = true).assertIsDisplayed()
        compose.onNodeWithText("No").performClick()
        assertTrue(runBlocking { database.editorDao().workouts().first().any { it.id == workoutId } })

        compose.onNodeWithText("Delete workout").performClick()
        compose.onNodeWithText("Yes, delete").performClick()
        compose.waitUntil(15_000) {
            deleted && runBlocking {
                database.editorDao().workouts().first().none { it.id == workoutId }
            }
        }
    }

    private fun startPushWorkout(context: Context) = runBlocking {
        DatabaseSeeder(context, database).seedIfNeeded()
        val repository = TrainingRepository(database, database.programmeDao(), database.workoutDao(), database.trackerDao(), context)
        repository.startWorkout(repository.observeProgrammeDays().first().first { it.id == "demo-day-push" }.id)
    }

}

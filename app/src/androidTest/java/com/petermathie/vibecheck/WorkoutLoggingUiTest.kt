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
        compose.onNodeWithContentDescription("Add exercise to workout").assertIsDisplayed().performClick()
        compose.onNodeWithText("Choose exercise").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add exercise to workout").assertDoesNotExist()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithContentDescription("Add exercise to workout").assertIsDisplayed()
        compose.onNodeWithText("Workout notes").assertDoesNotExist()
        compose.onNodeWithText("Start hold timer").assertDoesNotExist()
        compose.onNodeWithContentDescription("Save set 1 for Handstand").assertDoesNotExist()
        compose.onAllNodes(hasText("Exercise notes") and hasSetTextAction())[0].performTextInput("Shoulders stable")
        compose.waitUntil(15_000) {
            runBlocking { database.editorDao().workoutExercises().first().any { it.notes == "Shoulders stable" } }
        }

        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasContentDescription("Set 1 for Bench press"))
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
    fun handstandCanTrackFreestandingAndTotalTensionTime() {
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
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { WorkoutEditor(viewModel, workoutId, {}, {}) } }

        assertHandstandQuantitativeFieldsShareRow()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("12")
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").performTextInput("30")
        compose.onNodeWithContentDescription("Open Total Time stopwatch for set 1").performClick()
        compose.onNodeWithText("Total Time stopwatch").assertExists()
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithText("Start").performClick()
        compose.waitUntil("Stopwatch did not reach 100 ms", 5_000) {
            compose.onAllNodes(stopwatchElapsedAtLeast(100)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Stop").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").assertTextContains("TUT (s)")
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").assertTextContains("Total")
        compose.onNodeWithText("Freestanding sec").assertDoesNotExist()
        compose.onNodeWithText("Total wall sec").assertDoesNotExist()
        compose.onNodeWithContentDescription("RPE for Handstand set 1").performTextInput("99")
        compose.onNodeWithContentDescription("RPE for Handstand set 1").performTextClearance()
        compose.onNodeWithContentDescription("RPE for Handstand set 1").performTextInput("8")
        val (heldBounds, tensionBounds, rpeBounds) = assertHandstandQuantitativeFieldsShareRow()
        val stopwatchBounds = compose.onNodeWithContentDescription("Open Total Time stopwatch for set 1").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Stopwatch should occupy the right side of Total Time: Total=$tensionBounds stopwatch=$stopwatchBounds",
            stopwatchBounds.left >= tensionBounds.center.x && stopwatchBounds.right <= tensionBounds.right,
        )

        val handstandId = runBlocking {
            database.editorDao().workoutExercises().first()
                .first { it.workoutId == workoutId && it.actualExerciseId == "core:handstand" }.id
        }
        compose.waitUntil(15_000) {
            runBlocking {
                database.editorDao().sets().first().any {
                    it.workoutExerciseId == handstandId &&
                        it.holdMillis == 12_000L &&
                        it.timeUnderTensionMillis != null &&
                        it.timeUnderTensionMillis!! >= 100L &&
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
        compose.onNodeWithContentDescription("Variation for Handstand set 1: Wall handstand").assertExists()
        compose.onNodeWithText("Wall handstand").performClick()
        compose.onNodeWithText("Freestanding handstand", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").performTextInput("15")

        compose.waitUntil(15_000) {
            runBlocking {
                database.editorDao().sets().first().singleOrNull {
                    it.variationId == "handstand-free" &&
                        it.timeUnderTensionMillis == 15_000L &&
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
    fun substitutionAfterARecordedSetPreservesOriginalHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val workoutId = startPushWorkout(context)
        val bench = runBlocking { database.editorDao().workoutExercises().first().first { it.workoutId == workoutId && it.actualExerciseId == "core:bench-press" } }
        runBlocking { database.editorDao().set(emptySet(bench.id, 1).copy(weightKg = 50.0, reps = 5.0)) }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { WorkoutEditor(viewModel, workoutId, {}, {}) } }

        compose.waitUntil("Workout and Bench row were not ready", 15_000) {
            viewModel.workouts.value.any { it.id == workoutId } &&
                viewModel.workoutExercises.value.any { it.id == bench.id } &&
                compose.onAllNodesWithContentDescription("Workout exercise list").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Workout exercise list")
            .performScrollToNode(hasContentDescription("More actions for Bench press"))
        compose.onNodeWithContentDescription("More actions for Bench press").performClick()
        compose.onNodeWithText("Substitute exercise").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Choose exercise").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Name, alias or muscle") and hasSetTextAction()).performTextInput("Dip")
        compose.onNode(hasTextExactly("Dip") and hasClickAction()).performClick()

        compose.waitUntil(15_000) {
            runBlocking {
                database.editorDao().workoutExercises().first().count { it.workoutId == workoutId } == 7
            }
        }
        assertEquals(2, runBlocking { database.editorDao().workoutExercises().first().count { it.workoutId == workoutId && it.actualExerciseId == "core:dip" } })
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
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Variation for Handstand set 1: Wall handstand")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Variation for Handstand set 1: Wall handstand").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("TUT (s)")
        compose.onNodeWithContentDescription("Total Time for Handstand set 1")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Total")
        compose.onNodeWithContentDescription("RPE for Handstand set 1")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("RPE")
        assertHandstandQuantitativeFieldsRemainOrdered()
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("Finish workout"))
        compose.onNodeWithText("Finish workout").assertIsDisplayed()
    }

    private fun assertHandstandQuantitativeFieldsShareRow(): List<Rect> {
        val bounds = awaitHandstandQuantitativeFieldBounds(requireSameRow = true)
        val (heldBounds, tensionBounds, rpeBounds) = bounds
        assertTrue(
            "TUT, Total, and RPE should share a row: TUT=$heldBounds Total=$tensionBounds RPE=$rpeBounds",
            kotlin.math.abs(heldBounds.center.y - tensionBounds.center.y) < 2f,
        )
        assertTrue(
            "TUT, Total, and RPE should share a row: TUT=$heldBounds Total=$tensionBounds RPE=$rpeBounds",
            kotlin.math.abs(heldBounds.center.y - rpeBounds.center.y) < 2f,
        )
        assertOrderedAndNonOverlapping(bounds)
        return bounds
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
        val (heldBounds, tensionBounds, rpeBounds) = bounds
        assertTrue(
            "TUT, Total, and RPE should retain readable, ordered bounds: TUT=$heldBounds Total=$tensionBounds RPE=$rpeBounds",
            bounds.all { it.width > 0f && it.height > 0f } &&
                bounds.zipWithNext().all { (first, second) ->
                    val sameRow = kotlin.math.abs(first.center.y - second.center.y) < 2f
                    if (sameRow) first.right <= second.left else first.bottom <= second.top
                },
        )
    }

    private fun handstandQuantitativeFieldBounds(): List<Rect>? {
        return listOf(
            "Time Under Tension for Handstand set 1",
            "Total Time for Handstand set 1",
            "RPE for Handstand set 1",
        ).map { description ->
            compose.onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes()
                .singleOrNull()
                ?.boundsInRoot
                ?: return null
        }
    }

    private fun stopwatchElapsedAtLeast(minimumMillis: Long) =
        SemanticsMatcher("stopwatch elapsed at least $minimumMillis ms") { node ->
            if (!node.config.contains(SemanticsProperties.Text)) {
                false
            } else {
                node.config[SemanticsProperties.Text]
                    .asSequence()
                    .mapNotNull { text ->
                        val match = STOPWATCH_TIME.matchEntire(text.text) ?: return@mapNotNull null
                        match.groupValues[1].toLong() * 60_000L +
                            (match.groupValues[2].toDouble() * 1_000L).toLong()
                    }
                    .any { it >= minimumMillis }
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
        compose.onNodeWithText("Add set").assertDoesNotExist()
        compose.onNodeWithContentDescription("Start 120 second rest for Bench press").assertExists()

        compose.onNodeWithContentDescription("Add set for Bench press").performScrollTo().performClick()
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasContentDescription("Set 4 for Bench press"))
        compose.onNodeWithContentDescription("Set 4 for Bench press").assertExists()
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

    private companion object {
        private val STOPWATCH_TIME = Regex("""(\d{2}):(\d{2}\.\d{2})""")
    }
}

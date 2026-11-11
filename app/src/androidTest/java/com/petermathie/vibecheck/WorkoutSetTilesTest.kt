package com.petermathie.vibecheck

import android.content.Context
import androidx.compose.ui.test.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.data.DataTransfer
import com.petermathie.vibecheck.data.TrainingRepository
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.workout.timingMillis
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.WorkoutEditor
import com.petermathie.vibecheck.ui.HistoryScreen
import com.petermathie.vibecheck.ui.ExerciseSettingsDialog
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

    private fun start(): String = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        DatabaseSeeder(context, db).seedIfNeeded()
        TrainingRepository(db, db.programmeDao(), db.workoutDao(), db.trackerDao(), context)
            .startWorkout("demo-day-push")
    }

    private fun show(workout: String) {
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Time Under Tension for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
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
        compose.onNodeWithContentDescription("Add TUT entry for Handstand set 1").assertIsNotEnabled()
        compose.onNodeWithText("TUT total: 36 s").assertExists()
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { timingMillis(it.tutEntriesMillis) == List(8) { i -> (i + 1) * 1000L } && it.holdMillis == 36_000L } }
        }
        val backup = runBlocking { DataTransfer.export(db) }
        runBlocking { DataTransfer.import(db, backup) }
        assertTrue(runBlocking { db.editorDao().sets().first().any { timingMillis(it.tutEntriesMillis).size == 8 && it.holdMillis == 36_000L } })
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
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performScrollTo().performClick()
        compose.onNodeWithText("Freestanding handstand").performClick()
        compose.onNodeWithContentDescription("Add TUT entry for Handstand set 1").assertDoesNotExist()
        compose.onNodeWithText("One-entry mode. Previously recorded attempts are retained.").assertExists()
        compose.onNodeWithContentDescription("Variation for Handstand set 2: Wall handstand").assertExists()
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.variationId == "handstand-free" && it.holdMillis == 10_000L && timingMillis(it.tutEntriesMillis) == listOf(4_000L, 6_000L) } }
        }
    }

    @Test
    fun completingTileOpensNextEmptyAndExercisesCollapseWithoutLosingValues() {
        val workout = start()
        show(workout)
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("5")
        compose.onNodeWithContentDescription("RPE for Handstand set 1").performTextInput("8")
        compose.onNodeWithContentDescription("RPE for Handstand set 1").performImeAction()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 2").assertExists()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Collapse Handstand").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Set 2 for Handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("Expand Handstand").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { db.editorDao().sets().first().any { it.holdMillis == 5_000L && it.rpe == 8.0 } })
        compose.onNodeWithContentDescription("More actions for Handstand").assertDoesNotExist()
        compose.onNodeWithText("Substitute exercise").assertDoesNotExist()
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
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Open set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Open set 1 for Handstand").performClick()
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
        compose.onNodeWithContentDescription("Collapse Dip").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Expand Dip").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Open set 1 for Dip").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Open set 1 for Dip").performClick()
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
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performClick()
        compose.onNodeWithText("Freestanding handstand").performClick()
        compose.onNodeWithContentDescription("Reps for Handstand set 1").assertTextContains("5")
        compose.onNodeWithContentDescription("Resistance for Handstand set 1").assertTextEquals("kg", "")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.variationId == "handstand-free" && it.reps == 5.0 && it.weightKg == null } }
        }
    }
}

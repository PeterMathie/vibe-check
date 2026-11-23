package com.petermathie.vibecheck

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.local.ExerciseEntity
import com.petermathie.vibecheck.data.local.WorkoutEntity
import com.petermathie.vibecheck.data.local.WorkoutExerciseEntity
import com.petermathie.vibecheck.data.local.WorkoutSetEntity
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.ProgressScreen
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ProgressUiTest {
    private lateinit var database: VibeDatabase
    private var preservedPhotoNames: Set<String>? = null
    private var fixturePreferenceName: String? = null

    @get:Rule
    val lifecycle = ComposeRoomLifecycleRule {
        if (::database.isInitialized) database else null
    }
    private val compose get() = lifecycle.compose

    @After
    fun cleanUpFixturePreferences() {
        fixturePreferenceName?.let {
            ApplicationProvider.getApplicationContext<Context>().deleteSharedPreferences(it)
        }
    }

    @Test
    fun habitCalendarsAreAbsentWhileOtherCardsRetainTheirSavedOrder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferenceName = "habits-progress-fixture-${java.util.UUID.randomUUID()}"
        fixturePreferenceName = preferenceName
        val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        preferences.edit().putString("card-order", "training|habit:tracker|bodyweight")
            .putBoolean("card-order-customized", true).commit()
        val isolatedContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = preferences
            override fun getFilesDir() = File(context.cacheDir, preferenceName)
        }
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking {
            database.editorDao().tracker(com.petermathie.vibecheck.data.local.TrackerEntity("tracker", "Not on Progress", false))
            database.editorDao().measurement(
                com.petermathie.vibecheck.data.local.BodyMeasurementEntity("weight", 1, "Bodyweight", 75.0, "kg", "", false),
            )
        }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                CompositionLocalProvider(LocalContext provides isolatedContext) { ProgressScreen(viewModel) }
            }
        }
        compose.waitUntil(15_000) {
            preferences.getString("card-order", "") == "training|bodyweight"
        }
        compose.onNodeWithText("Not on Progress").assertDoesNotExist()
        compose.onNodeWithText("Training progress").assertExists()
        compose.onNodeWithText("Bodyweight").assertExists()
        val training = compose.onNodeWithText("Training progress").fetchSemanticsNode().boundsInRoot
        val bodyweight = compose.onNodeWithText("Bodyweight").fetchSemanticsNode().boundsInRoot
        assertTrue("Training should remain before bodyweight", training.top < bodyweight.top)
    }

    @After
    fun cleanUpPhotos() {
        preservedPhotoNames?.let { preserved ->
            File(ApplicationProvider.getApplicationContext<Context>().filesDir, "progress-photos")
                .listFiles()
                .orEmpty()
                .filterNot { it.name in preserved }
                .forEach(File::delete)
        }
    }

    @Test
    fun chartExposesAxesSkillExplanationAndExplicitRecords() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preservedPhotoNames = File(context.filesDir, "progress-photos")
            .listFiles()
            .orEmpty()
            .map(File::getName)
            .toSet()
        context.getSharedPreferences("progress-layout", Context.MODE_PRIVATE).edit().clear().commit()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking {
            DatabaseSeeder(context, database).seedIfNeeded()
            database.editorDao().exercise(
                ExerciseEntity("no-history", "No history exercise", "STRENGTH", "WEIGHT_REPS", null, null, "custom", true),
            )
            assertEquals(156, database.editorDao().workouts().first().count { it.id.startsWith("demo-progress-") })
            val rows = database.editorDao().workoutExercises().first()
            val sets = database.editorDao().sets().first()
            fun weeklyWeights(exerciseId: String) = rows
                .filter { it.actualExerciseId == exerciseId && it.workoutId.startsWith("demo-progress-") }
                .sortedBy { row -> sets.filter { it.workoutExerciseId == row.id }.minOf { it.loggedAt } }
                .map { row -> sets.filter { it.workoutExerciseId == row.id }.maxOf { it.weightKg ?: 0.0 } }
            val bench = weeklyWeights("core:bench-press")
            val lunge = weeklyWeights("core:lunge")
            val overhead = weeklyWeights("core:overhead-press")
            assertTrue(bench.last() > bench.first() + 15.0)
            assertTrue((lunge.maxOrNull() ?: 0.0) - (lunge.minOrNull() ?: 0.0) < 2.0)
            assertTrue(overhead.last() < overhead.first() - 5.0)
            assertEquals(52, database.editorDao().measurements().first().count { it.isDemo && it.metric == "Bodyweight" })
            assertTrue(database.editorDao().values().first().size > 500)
            val photoTime = database.editorDao().measurements().first().filter { it.isDemo }.minOf { it.recordedAt }
            val directory = File(context.filesDir, "progress-photos").apply { mkdirs() }
            val bitmap = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
            File(directory, "$photoTime.jpg").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { ProgressScreen(viewModel) } }

        compose.onNodeWithText("Overall training trend").assertExists()
        compose.onNodeWithText("Bodyweight").assertExists()
        compose.onNodeWithContentDescription("Reorder Overall training trend").assertExists()
        compose.onNodeWithContentDescription("Reorder Bodyweight").assertExists()
        compose.onNodeWithContentDescription("Reorder Training progress").assertExists()
        listOf("Piano", "Meditation", "Protein", "Mood", "Journal", "Reading").forEach { habit ->
            compose.onNodeWithText(habit).assertDoesNotExist()
            compose.onNodeWithContentDescription("Reorder $habit").assertDoesNotExist()
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("kg", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        repeat(1) {
            val actions: List<androidx.compose.ui.semantics.CustomAccessibilityAction> =
                compose.onNodeWithContentDescription("Reorder Bodyweight")
                .fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
            assertTrue(actions.first { action -> action.label == "Move earlier" }.action())
            compose.waitForIdle()
        }
        compose.waitUntil(15_000) {
            context.getSharedPreferences("progress-layout", Context.MODE_PRIVATE)
                .getString("card-order", "")
                ?.substringBefore('|') == "bodyweight"
        }
        compose.onNodeWithText("Bodyweight").performScrollTo()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("kg", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("kg", substring = true).performTouchInput { click(androidx.compose.ui.geometry.Offset(16f, center.y)) }
        compose.onNodeWithContentDescription("Progress photo for selected bodyweight day").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Choose exercise").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Choose exercise").performScrollTo().performClick()
        compose.onNode(hasText("Name, alias or muscle") and hasSetTextAction()).performTextInput("No history exercise")
        compose.onNode(hasText("No history exercise") and !hasSetTextAction()).assertDoesNotExist()
        compose.onNode(hasText("Name, alias or muscle") and hasSetTextAction()).performTextClearance()
        compose.onNode(hasText("Name, alias or muscle") and hasSetTextAction()).performTextInput("Handstand")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Handstand").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Handstand") and !hasSetTextAction()).performClick()

        compose.onNodeWithText("Variations").performClick()
        compose.onNodeWithText("Wall handstand").performClick()
        compose.onNodeWithText("Wall handstand").assertIsDisplayed()
        compose.onNodeWithText("Wall handstand").performClick()
        compose.onNodeWithText("All variations").performClick()
        compose.onNodeWithText("Variations").assertIsDisplayed()
        compose.onAllNodes(hasContentDescription("Progress chart", substring = true)).assertCountEquals(4)
        compose.onNodeWithContentDescription("0 to 10 RPE", substring = true).assertExists()
        compose.onNodeWithText("Personal records").assertExists()
        compose.onNodeWithText("Best performance").assertExists()
        compose.onNodeWithText("Longest hold").assertExists()
        compose.onNodeWithText("Heaviest weight").assertDoesNotExist()
        compose.onNodeWithText("Estimated 1RM").assertDoesNotExist()
        compose.onNodeWithText("Repetition PR", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Calculated performance PR", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Skill index is a heuristic", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("How progress works").performScrollTo().performClick()
        compose.onNodeWithText("Skill index is a heuristic", substring = true).assertExists()
        compose.onNodeWithText("Close").performClick()

        compose.onNodeWithText("Handstand").performScrollTo().performClick()
        compose.onNode(hasText("Name, alias or muscle") and hasSetTextAction()).performTextInput("Bench press")
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Bench press").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("Bench press") and !hasSetTextAction()).performClick()
        compose.onNodeWithText("Heaviest weight").assertExists()
        compose.onNodeWithText("Estimated 1RM").assertExists()
        compose.onNodeWithText("Longest hold").assertDoesNotExist()
    }

    @Test
    fun eligiblePickerScrollsPastOneHundredExercises() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking {
            val dao = database.editorDao()
            dao.workout(WorkoutEntity("workout", null, "Progress", "STRENGTH", "FINISHED", 1, 2, "", null, false))
            repeat(105) { index ->
                val suffix = index.toString().padStart(3, '0')
                val exerciseId = "exercise-$suffix"
                val rowId = "row-$suffix"
                dao.exercise(ExerciseEntity(exerciseId, "Eligible $suffix", "STRENGTH", "WEIGHT_REPS", null, null, "custom", true))
                dao.workoutExercise(WorkoutExerciseEntity(rowId, "workout", exerciseId, exerciseId, index, "", 60, null))
                dao.set(
                    WorkoutSetEntity(
                        id = "set-$suffix",
                        workoutExerciseId = rowId,
                        ordinal = 1,
                        setType = "WORKING",
                        result = "COMPLETED",
                        variationId = null,
                        weightKg = 10.0,
                        reps = 5.0,
                        holdMillis = null,
                        leftReps = null,
                        rightReps = null,
                        leftHoldMillis = null,
                        rightHoldMillis = null,
                        addedWeightKg = null,
                        assistanceKg = null,
                        rpe = null,
                        romValue = null,
                        romUnit = null,
                        notes = "",
                        loggedAt = 1,
                        updatedAt = 1,
                    ),
                )
            }
        }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { ProgressScreen(viewModel) } }
        compose.waitUntil(15_000) {
            runCatching {
                compose.onNodeWithText("Choose exercise").assertIsEnabled()
                true
            }.getOrDefault(false)
        }

        compose.onNodeWithText("Choose exercise").performClick()
        compose.onNodeWithContentDescription("Eligible progress exercises")
            .performScrollToNode(hasText("Eligible 104"))
        compose.onNodeWithText("Eligible 104").assertIsDisplayed()
    }
}

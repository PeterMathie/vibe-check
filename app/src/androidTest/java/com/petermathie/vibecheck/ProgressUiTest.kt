package com.petermathie.vibecheck

import android.content.Context
import androidx.compose.ui.test.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.local.ExerciseEntity
import com.petermathie.vibecheck.data.local.WorkoutEntity
import com.petermathie.vibecheck.data.local.WorkoutExerciseEntity
import com.petermathie.vibecheck.data.local.WorkoutSetEntity
import com.petermathie.vibecheck.data.local.BodyMeasurementEntity
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
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ProgressUiTest {
    private lateinit var database: VibeDatabase
    private var preservedPhotoNames: Set<String>? = null

    @get:Rule
    val lifecycle = ComposeRoomLifecycleRule {
        if (::database.isInitialized) database else null
    }
    private val compose get() = lifecycle.compose

    @After
    fun cleanUpPhotos() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("progress-date-range", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
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
        context.getSharedPreferences("progress-date-range", Context.MODE_PRIVATE).edit().clear().commit()
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
        compose.onNodeWithText("Piano").assertExists()
        compose.onNodeWithText("Meditation").assertExists()
        compose.onNodeWithText("Protein").assertExists()
        compose.onNodeWithContentDescription("Reorder Overall training trend").assertExists()
        compose.onNodeWithContentDescription("Reorder Bodyweight").assertExists()
        compose.onNodeWithContentDescription("Reorder Piano").assertExists()
        compose.onNodeWithContentDescription("Reorder Meditation").assertExists()
        compose.onNodeWithContentDescription("Reorder Protein").assertExists()
        compose.onNodeWithContentDescription("Reorder Training progress").assertExists()
        compose.onNodeWithContentDescription("Piano icon").assertExists()
        compose.onNodeWithContentDescription("Meditation icon").assertExists()
        compose.onNodeWithContentDescription("Protein icon").assertExists()
        listOf("Piano", "Meditation", "Protein", "Mood", "Journal", "Reading").forEach { habit ->
            compose
                .onNodeWithContentDescription("${habit.lowercase()} intensity date navigation")
                .performScrollTo()
            val headerBounds = compose.onNodeWithText(habit).fetchSemanticsNode().boundsInRoot
            val navigationBounds = compose
                .onNodeWithContentDescription("${habit.lowercase()} intensity date navigation")
                .fetchSemanticsNode()
                .boundsInRoot
            assertTrue(
                "$habit heat map must start below its header: header=$headerBounds navigation=$navigationBounds",
                navigationBounds.top >= headerBounds.bottom,
            )
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("kg", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(compose.onAllNodesWithContentDescription("piano intensity", substring = true).fetchSemanticsNodes().isNotEmpty())
        repeat(3) {
            val actions: List<androidx.compose.ui.semantics.CustomAccessibilityAction> =
                compose.onNodeWithContentDescription("Reorder Meditation")
                .fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
            assertTrue(actions.first { action -> action.label == "Move earlier" }.action())
            compose.waitForIdle()
        }
        compose.waitUntil(15_000) {
            context.getSharedPreferences("progress-layout", Context.MODE_PRIVATE)
                .getString("card-order", "")
                ?.substringBefore('|') == "habit:demo-meditation"
        }
        compose.onNodeWithText("Bodyweight").performScrollTo()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("kg", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        val bodyweightGraph = compose.onNodeWithContentDescription("kg", substring = true)
        val graphActions: List<androidx.compose.ui.semantics.CustomAccessibilityAction> =
            bodyweightGraph.fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        assertTrue(graphActions.first { it.label == "Next data point" }.action())
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Progress photo for selected bodyweight day").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Mood").performScrollTo()
        assertTrue(compose.onAllNodesWithContentDescription("1 mood intensity", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithContentDescription("2 mood intensity", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithContentDescription("3 mood intensity", substring = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Journal").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Reading").performScrollTo().assertIsDisplayed()

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

    @Test
    fun persistedInclusiveDateRangeFiltersGraphsAndResetRestoresAllTime() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val selectedDate = LocalDate.now().minusDays(5)
        val earlierDate = selectedDate.minusDays(10)
        context.getSharedPreferences("progress-date-range", Context.MODE_PRIVATE).edit()
            .putLong("start", selectedDate.toEpochDay())
            .putLong("end", selectedDate.toEpochDay())
            .commit()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking {
            database.editorDao().measurement(
                BodyMeasurementEntity(
                    "earlier",
                    earlierDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    "Bodyweight",
                    80.0,
                    "kg",
                    "",
                    false,
                ),
            )
            database.editorDao().measurement(
                BodyMeasurementEntity(
                    "selected",
                    selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    "Bodyweight",
                    79.0,
                    "kg",
                    "",
                    false,
                ),
            )
        }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { ProgressScreen(viewModel) } }

        val formatted = selectedDate.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))
        compose.onNodeWithText("$formatted – $formatted").assertIsDisplayed()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription(
                "Progress chart from $selectedDate to $selectedDate",
                substring = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription(
            "Progress chart from $selectedDate to $selectedDate",
            substring = true,
        ).assertExists()

        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithText("Date range: All time").assertIsDisplayed()
        compose.onNodeWithContentDescription(
            "Progress chart from $earlierDate to $selectedDate",
            substring = true,
        ).assertExists()
        assertTrue(!context.getSharedPreferences("progress-date-range", Context.MODE_PRIVATE).contains("start"))
    }
}

package com.petermathie.vibecheck

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
import com.petermathie.vibecheck.data.local.ExerciseVariationEntity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import com.petermathie.vibecheck.ui.formatDisplayDate
import java.time.LocalDate
import java.time.ZoneId
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.MeasurementsScreen
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
    private var fixturePreferenceName: String? = null
    private var fixtureDirectory: File? = null

    @get:Rule
    val lifecycle = ComposeRoomLifecycleRule {
        if (::database.isInitialized) database else null
    }
    private val compose get() = lifecycle.compose

    @After
    fun cleanUpFixturePreferences() {
        fixturePreferenceName?.let {
            assertTrue("Could not delete owned preferences", ApplicationProvider.getApplicationContext<Context>().deleteSharedPreferences(it))
        }
        fixtureDirectory?.let {
            assertTrue("Could not remove owned fixture $it", !it.exists() || it.deleteRecursively())
        }
    }

    private fun isolatedContext(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runId = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("bodyFixtureRunId")
            ?: java.util.UUID.randomUUID().toString()
        require(runId.matches(Regex("[A-Za-z0-9-]{1,64}"))) { "Invalid presentation fixture run ID" }
        val preferenceName = "progress-presentation-fixture-$runId"
        fixturePreferenceName = preferenceName
        val preferences = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        val directory = File(context.cacheDir, preferenceName)
        fixtureDirectory = directory
        return object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) = preferences
            override fun getFilesDir() = directory
        }
    }

    @Test
    fun progressOmitsBodyweightWithoutRemovingBodyRecords() {
        val context = isolatedContext()
        val preferences = context.getSharedPreferences("progress-layout", Context.MODE_PRIVATE)
        preferences.edit().putString("card-order", "training|habit:tracker|bodyweight")
            .putBoolean("card-order-customized", true).commit()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking {
            database.editorDao().tracker(com.petermathie.vibecheck.data.local.TrackerEntity("tracker", "Not on Progress", false))
            database.editorDao().measurement(
                com.petermathie.vibecheck.data.local.BodyMeasurementEntity("weight", 1, "Bodyweight", 75.0, "kg", "", false),
            )
        }
        val viewModel = lifecycle.own(EditorViewModel(database))
        val showBody = mutableStateOf(false)
        compose.setContent {
            VibeCheckTheme {
                val registryOwner = checkNotNull(LocalActivityResultRegistryOwner.current)
                CompositionLocalProvider(
                    LocalContext provides context,
                    LocalActivityResultRegistryOwner provides registryOwner,
                ) {
                    if (showBody.value) MeasurementsScreen(viewModel) else ProgressScreen(viewModel)
                }
            }
        }
        compose.onNodeWithText("Not on Progress").assertDoesNotExist()
        compose.onNodeWithText("Overall training trend").assertExists()
        assertEquals("training|habit:tracker|bodyweight", preferences.getString("card-order", ""))
        compose.onNodeWithText("Bodyweight").assertDoesNotExist()
        compose.onNodeWithContentDescription("Reorder Bodyweight").assertDoesNotExist()
        val retained = runBlocking { database.editorDao().measurements().first().single() }
        assertEquals("weight", retained.id)
        assertEquals(75.0, retained.value, 0.0)

        compose.runOnIdle { showBody.value = true }
        compose.onNodeWithText("Bodyweight trend").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Progress chart", substring = true).assertIsDisplayed()
        compose.onNode(hasText("75.00 kg") and hasAnyAncestor(hasContentDescription("Graph readout"))).assertIsDisplayed()
        assertEquals(retained, runBlocking { database.editorDao().measurements().first().single() })
    }

    @Test
    fun estimatedOneRepMaxRangesKeepDailyEstimatesAndAllTimeRecordsWithoutSessionClutter() {
        val context = isolatedContext()
        val preferences = context.getSharedPreferences("progress-layout", Context.MODE_PRIVATE)
        preferences.edit().putString("card-order", "bodyweight|training|overall")
            .putBoolean("card-order-customized", true).commit()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val today = LocalDate.now()
        runBlocking {
            val dao = database.editorDao()
            dao.exercise(ExerciseEntity("bench", "Bench press", "STRENGTH", "WEIGHT_REPS", null, null, "custom", true))
            dao.exercise(ExerciseEntity("no-history", "No history exercise", "STRENGTH", "WEIGHT_REPS", null, null, "custom", true))
            dao.variation(ExerciseVariationEntity("paused", "bench", "Paused", 1, false))
            listOf(250L to 80.0, 240L to 70.0, 230L to 60.0, 5L to 50.0, 1L to 55.0).forEachIndexed { index, (days, weight) ->
                val date = today.minusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                dao.workout(WorkoutEntity("workout-$index", null, "Recorded session", "STRENGTH", "FINISHED", date, date, "", null, false))
                dao.workoutExercise(WorkoutExerciseEntity("row-$index", "workout-$index", "bench", "bench", 0, "Do not show exercise notes", 60, null))
                dao.set(workingSet("set-$index", "row-$index", date, weight, 5.0))
            }
        }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                CompositionLocalProvider(LocalContext provides context) { ProgressScreen(viewModel) }
            }
        }

        compose.waitUntil(15_000) {
            viewModel.exercises.value.size == 2 && viewModel.workouts.value.size == 5 && viewModel.sets.value.size == 5
        }
        compose.onNodeWithText("Overall training trend").assertExists()
        assertRange("1 year")
        compose.onAllNodes(hasContentDescription("Progress chart", substring = true)).assertCountEquals(1)
        compose.onNodeWithText("Personal records").assertDoesNotExist()
        compose.onNodeWithText("Bodyweight").assertDoesNotExist()
        compose.onAllNodes(hasContentDescription("Reorder", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("Progress").assertDoesNotExist()
        compose.onNodeWithText("Training progress").assertDoesNotExist()
        compose.onNodeWithContentDescription("Choose exercise").performClick()
        compose.onNodeWithText("No history exercise").assertExists()
        compose.onNodeWithText("Bench press").performClick()
        compose.onNodeWithText("Track progress of Bench press").assertExists()
        compose.onNodeWithText("Overall training trend").assertDoesNotExist()
        assertRange("1 year")
        val inExercise = hasAnyAncestor(hasContentDescription("Exercise progress"))
        compose.onNodeWithText("Estimated 1RM trend").assertExists()
        val e1rmChart = hasContentDescription("kg", substring = true) and hasContentDescription("Progress chart", substring = true) and inExercise
        val latestReadout = hasContentDescription("Graph readout") and hasAnyDescendant(hasText("64.17 kg")) and inExercise
        compose.onNode(e1rmChart).assert(hasContentDescription("58.33 to 93.33 kg", substring = true))
        scrollToNode(latestReadout)
        compose.onNode(latestReadout).assertIsDisplayed()
        scrollToNode(hasText("Heaviest weight"))
        compose.onNodeWithText("80 kg").assertIsDisplayed()
        listOf("1 month", "3 months", "6 months", "1 year").forEach { range ->
            chooseRange(range)
            assertRange(range)
            scrollToNode(latestReadout)
            compose.onNode(latestReadout).assertIsDisplayed()
            scrollToNode(hasText("Heaviest weight"))
            compose.onNodeWithText("80 kg").assertIsDisplayed()
        }
        chooseRange("Custom")
        val beginning = today.minusYears(2).withDayOfMonth(15)
        val ending = today.minusYears(2).withDayOfMonth(16)
        chooseDate("Beginning", beginning)
        chooseDate("End", ending)
        compose.onNode(hasContentDescription("Choose Beginning date") and hasText(formatDisplayDate(beginning))).assertExists()
        compose.onNode(hasContentDescription("Choose End date") and hasText(formatDisplayDate(ending))).assertExists()
        compose.onNodeWithText("Apply").performClick()
        assertRange("Custom")
        listOf("No estimated 1RM recorded in this date range.", "No effort recorded in this date range.", "80 kg").forEach { text ->
            scrollToNode(hasText(text))
            compose.onNodeWithText(text).assertIsDisplayed()
        }
        chooseRange("Custom")
        chooseDate("Beginning", beginning.plusDays(1))
        compose.onNodeWithText("Cancel").performClick()
        assertRange("Custom")
        chooseRange("Custom")
        compose.onNode(hasContentDescription("Choose Beginning date") and hasText(formatDisplayDate(beginning))).assertExists()
        compose.onNodeWithText("Cancel").performClick()
        chooseRange("1 year")
        scrollToNode(latestReadout)
        val graph = compose.onNode(e1rmChart)
        val previous = graph.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == "Previous data point" }
        compose.runOnIdle { assertTrue(previous.action()) }
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Graph readout") and hasAnyDescendant(hasText("58.33 kg")) and inExercise)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "${formatDisplayDate(today.minusDays(5))}, 58.33 kg"))
        compose.onNodeWithText("Do not show exercise notes").assertDoesNotExist()
        compose.onAllNodes(hasClickAction() and hasText(" · ", substring = true)).assertCountEquals(0)
        listOf("Falling", "Rising", "Flat", "Gradually improving").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        scrollToNode(hasContentDescription("Variations: All variations"))
        compose.onNodeWithContentDescription("Variations: All variations").performClick()
        compose.onNodeWithText("Paused").performClick()
        compose.onNodeWithText("Record weight and repetitions in a completed working set to see estimated 1RM.").assertExists()
        scrollToNode(hasContentDescription("Variations: Paused"))
        compose.onNodeWithContentDescription("Variations: Paused").performClick()
        compose.onNodeWithText("All variations").performClick()
        val effortChart = hasContentDescription("Progress chart", substring = true) and hasContentDescription("0 to 10 RPE", substring = true)
        val sections = listOf(
            e1rmChart,
            effortChart,
            hasContentDescription("exercise sessions calendar"),
            hasText("Personal records"),
        )
        val scrollPositions = sections.map { section ->
            scrollToNode(section)
            compose.onNode(section).assertIsDisplayed()
            compose.onNode(hasScrollToIndexAction()).fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].value()
        }
        assertTrue("Estimated 1RM, RPE, calendar and records follow the approved order",
            scrollPositions.zipWithNext().all { (before, after) -> before <= after })
        compose.onNodeWithText("Best performance").assertExists()
        compose.onNodeWithText("Heaviest weight").assertExists()
        compose.onNodeWithText("Estimated 1RM").assertExists()
        scrollToNode(latestReadout)
        compose.onNodeWithContentDescription("About estimated 1RM").performClick()
        compose.onNodeWithText("How estimated 1RM is calculated").assertExists()
        compose.onAllNodes(hasText("index", substring = true)).assertCountEquals(0)
        compose.onNodeWithContentDescription("Close").performClick()
        scrollToNode(hasContentDescription("Choose exercise"))
        compose.onNodeWithContentDescription("Choose exercise").performClick()
        compose.onNode(hasText("Overall training trend") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("Overall progress").assertExists()
        compose.onNodeWithText("Track progress of Bench press").assertDoesNotExist()
        compose.onNodeWithText("Personal records").assertDoesNotExist()
        assertEquals(5, runBlocking { database.editorDao().workouts().first().size })
    }

    @Test
    fun allExercisePickerUsesSharedModalAndDraggableScrollbar() {
        val context = isolatedContext()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking {
            val dao = database.editorDao()
            repeat(105) { index ->
                val suffix = index.toString().padStart(3, '0')
                val exerciseId = "exercise-$suffix"
                dao.exercise(ExerciseEntity(exerciseId, "Eligible $suffix", "STRENGTH", "WEIGHT_REPS", null, null, "custom", true))
            }
        }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                CompositionLocalProvider(LocalContext provides context) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { ProgressScreen(viewModel) }
                        Box(Modifier.fillMaxWidth().height(80.dp).semantics { contentDescription = "Fixture navigation" })
                    }
                }
            }
        }
        compose.waitUntil(15_000) { viewModel.exercises.value.size == 105 }

        compose.onNodeWithContentDescription("Choose exercise").performClick()
        val popupBounds = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Choose exercise"))
            .fetchSemanticsNode().boundsInWindow
        val navigationBounds = compose.onNodeWithContentDescription("Fixture navigation").fetchSemanticsNode().boundsInWindow
        assertTrue("Popup overlaps navigation", popupBounds.bottom <= navigationBounds.top)
        val scrollbar = compose.onNodeWithContentDescription("Scroll dialog")
        val before = scrollbar.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        scrollbar.performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, 80f), delayMillis = 300)
            up()
        }
        val after = scrollbar.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertTrue(after.current > before)
        scrollbar.performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(after.range.endInclusive)) }
        compose.onNodeWithText("Eligible 104").assertIsDisplayed()
        compose.onNodeWithText("Eligible 104").performClick()
        compose.onNodeWithText("Track progress of Eligible 104").assertExists()
        compose.onNodeWithText("Record weight and repetitions in a completed working set to see estimated 1RM.").assertExists()
        compose.onNodeWithContentDescription("Scroll dialog").assertDoesNotExist()
    }

    @Test
    fun exactDayGraphSelectionShowsReadOnlyWorkoutSnapshots() {
        val context = isolatedContext()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val day = today.minusDays(1)
        runBlocking {
            val dao = database.editorDao()
            dao.exercise(ExerciseEntity("bench", "Renamed bench", "STRENGTH", "WEIGHT_REPS", null, null, "custom", true))
            listOf(20L, 19L, 18L, 1L, 1L).forEachIndexed { index, ago ->
                val date = today.minusDays(ago).atTime(if (index == 4) 18 else 9, 0).atZone(zone).toInstant().toEpochMilli()
                val name = when (index) { 3 -> "Morning training"; 4 -> "Evening training"; else -> "Baseline $index" }
                dao.workout(WorkoutEntity("w-$index", null, name, "STRENGTH", "FINISHED", date, date, "Workout note $index", null, false))
                dao.workoutExercise(WorkoutExerciseEntity("r-$index", "w-$index", "bench", "bench", 0, "Exercise note $index", 60, null, exerciseName = "Saved bench"))
                dao.set(workingSet("s-$index", "r-$index", date, 60.0 + index, 7.0))
                if (index == 3) dao.set(workingSet("failed", "r-$index", date + 1, 20.0, 9.0).copy(
                    ordinal = 2, setType = "WARM_UP", result = "FAILED", notes = "Failed set note",
                    variationNameSnapshot = "Saved variation",
                ))
            }
        }
        val before = runBlocking { database.editorDao().sets().first() }
        val workoutsBefore = runBlocking { database.editorDao().workouts().first() }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { CompositionLocalProvider(LocalContext provides context) { ProgressScreen(viewModel) } } }
        compose.waitUntil(15_000) { viewModel.sets.value.size == 6 && viewModel.workouts.value.size == 5 }
        compose.onNodeWithContentDescription("Choose exercise").assertIsDisplayed()
        scrollToNode(hasContentDescription("Progress chart", substring = true))
        compose.onNodeWithText("Weekly average points").assertIsDisplayed()
        compose.onNodeWithText("Week ending").assertDoesNotExist()
        val graph = compose.onNodeWithContentDescription("Progress chart", substring = true)
        val range = com.petermathie.vibecheck.ui.components.progressDateRange(1, today, zone)
        val x = com.petermathie.vibecheck.ui.components.graphDateX(day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), range, graph.fetchSemanticsNode().size.width.toFloat(), 0f)
        graph.performTouchInput { click(androidx.compose.ui.geometry.Offset(x, center.y)) }
        scrollToNode(hasContentDescription("Selected day workouts"))
        compose.onNodeWithContentDescription("Selected day workouts")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, formatDisplayDate(day)))
        compose.onNodeWithText("Workouts").assertIsDisplayed()
        scrollToNode(hasText("Morning training"))
        compose.onNodeWithText("Morning training").assertIsDisplayed().performClick()
        compose.waitUntil(15_000) { viewModel.workoutLoggingData.value != null }
        compose.onNodeWithContentDescription("Workout details").assertExists()
        compose.onNodeWithText("Saved bench").assertExists()
        compose.onNodeWithText("Renamed bench").assertDoesNotExist()
        compose.onNodeWithText("Notes: Workout note 3").assertExists()
        compose.onNodeWithText("Notes: Exercise note 3").assertExists()
        compose.onNodeWithText("Saved variation").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("20 kg").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("9 RPE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Notes: Failed set note").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithContentDescription("Close").performClick()
        scrollToNode(hasText("Evening training"))
        compose.onNodeWithText("Evening training").assertIsDisplayed()
        scrollToNode(hasContentDescription("Progress chart", substring = true))
        val emptyDay = day.minusDays(1)
        val emptyX = com.petermathie.vibecheck.ui.components.graphDateX(emptyDay.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), range, graph.fetchSemanticsNode().size.width.toFloat(), 0f)
        graph.performTouchInput { click(androidx.compose.ui.geometry.Offset(emptyX, center.y)) }
        scrollToNode(hasContentDescription("Selected day workouts"))
        compose.onNodeWithContentDescription("Selected day workouts")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, formatDisplayDate(emptyDay)))
        compose.onNodeWithText("Workouts").assertIsDisplayed()
        compose.onNodeWithText("Morning training").assertDoesNotExist()
        compose.onNodeWithText("Evening training").assertDoesNotExist()
        compose.onNodeWithText("No workouts recorded on this day.").assertDoesNotExist()
        assertEquals(before, runBlocking { database.editorDao().sets().first() })
        assertEquals(workoutsBefore, runBlocking { database.editorDao().workouts().first() })
        scrollToNode(hasContentDescription("Choose exercise"))
        compose.onNodeWithContentDescription("Choose exercise").performClick()
        compose.onNodeWithText("Renamed bench").performClick()
        val dailyBest = hasContentDescription("Graph readout") and hasAnyDescendant(hasText("74.67 kg"))
        scrollToNode(dailyBest)
        compose.onNode(dailyBest)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "${formatDisplayDate(day)}, 74.67 kg"))
        scrollToNode(hasContentDescription("exercise sessions calendar"))
        compose.onNodeWithContentDescription("${formatDisplayDate(day)}: 2 exercise sessions").performClick().assertIsSelected()
        compose.onNodeWithText("2 exercise sessions").assertExists()
        assertEquals(before, runBlocking { database.editorDao().sets().first() })
    }

    @Test
    fun exerciseScrollsFreelyAndRpeScrubbingMovesCalendarBothWays() {
        val context = isolatedContext()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val days = listOf(27L, 20L, 10L, 1L).map(today::minusDays)
        runBlocking {
            val dao = database.editorDao()
            dao.exercise(ExerciseEntity("bench", "Bench press", "STRENGTH", "WEIGHT_REPS", null, null, "custom", true))
            repeat(4) { index ->
                val date = days[index].atStartOfDay(zone).toInstant().toEpochMilli()
                dao.workout(WorkoutEntity("w$index", null, "Training", "STRENGTH", "FINISHED", date, date, "", null, false))
                dao.workoutExercise(WorkoutExerciseEntity("r$index", "w$index", "bench", "bench", 0, "", 60, null))
                dao.set(workingSet("s$index", "r$index", date, 70.0 + index, 7.0))
            }
        }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                CompositionLocalProvider(LocalContext provides context) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { ProgressScreen(viewModel) }
                        Box(Modifier.fillMaxWidth().height(80.dp).semantics { contentDescription = "Fixture navigation" })
                    }
                }
            }
        }
        compose.waitUntil(15_000) { viewModel.workouts.value.size == 4 && viewModel.sets.value.size == 4 }
        compose.onNodeWithContentDescription("Choose exercise").performClick()
        compose.onNodeWithText("Bench press").performClick()
        val header = compose.onNodeWithText("Track progress of Bench press")
        val before = header.fetchSemanticsNode().boundsInRoot.top
        compose.onNode(hasScrollToIndexAction()).performTouchInput {
            val distance = with(compose.density) { 24.dp.toPx() }
            val x = with(compose.density) { 8.dp.toPx() }
            swipe(
                start = androidx.compose.ui.geometry.Offset(x, height * 0.5f),
                end = androidx.compose.ui.geometry.Offset(x, height * 0.5f - distance),
                durationMillis = 1000,
            )
        }
        compose.waitForIdle()
        val moved = before - header.fetchSemanticsNode().boundsInRoot.top
        assertTrue("Small scroll must not jump to a section", moved > 0f && moved < with(compose.density) { 40.dp.toPx() })
        val range = com.petermathie.vibecheck.ui.components.progressDateRange(1, today, zone)
        fun scrub(from: LocalDate, to: LocalDate) {
            scrollToNode(hasContentDescription("Progress chart", substring = true) and hasContentDescription("RPE", substring = true))
            val graph = compose.onNode(hasContentDescription("Progress chart", substring = true) and hasContentDescription("RPE", substring = true))
            val width = graph.fetchSemanticsNode().size.width.toFloat()
            fun x(day: LocalDate) = com.petermathie.vibecheck.ui.components.graphDateX(day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), range, width, 0f)
            graph.performTouchInput {
                swipe(
                    start = androidx.compose.ui.geometry.Offset(x(from), center.y),
                    end = androidx.compose.ui.geometry.Offset(x(to), center.y),
                    durationMillis = 500,
                )
            }
            compose.onNodeWithContentDescription("${formatDisplayDate(to)}: 1 exercise session").assertIsSelected()
        }
        scrub(days.last(), days.first())
        scrub(days.first(), days.last())
    }

    @Test
    fun exerciseCalendarReservesReadoutAndOpensOnlySelectedDayExerciseSets() {
        val context = isolatedContext()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val today = LocalDate.now().minusMonths(1).withDayOfMonth(15)
        val zone = ZoneId.systemDefault()
        val date = today.atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
        runBlocking {
            val dao = database.editorDao()
            dao.exercise(ExerciseEntity("bench", "Bench press", "STRENGTH", "WEIGHT_REPS", null, null, "custom", true))
            dao.exercise(ExerciseEntity("squat", "Squat", "STRENGTH", "WEIGHT_REPS", null, null, "custom", true))
            dao.variation(ExerciseVariationEntity("paused", "bench", "Paused", 1, false))
            listOf("morning", "evening", "yesterday").forEachIndexed { index, id ->
                val time = if (id == "yesterday") today.minusDays(1).atTime(10, 0).atZone(zone).toInstant().toEpochMilli() else date + index * 60_000
                dao.workout(WorkoutEntity(id, null, "$id workout", "STRENGTH", "FINISHED", time, time, "", null, false))
                dao.workoutExercise(WorkoutExerciseEntity("row-$id", id, "bench", "bench", 0, "$id bench notes", 60, null))
                dao.set(workingSet("set-$id", "row-$id", time, 70.0, 7.0))
            }
            dao.set(workingSet("warm-up", "row-morning", date, 20.0, 5.0).copy(setType = "WARM_UP", notes = "Warm-up note", ordinal = 2))
            dao.set(workingSet("failed", "row-morning", date, 50.0, 9.0).copy(result = "FAILED", notes = "Failed note", ordinal = 3))
            dao.set(workingSet("paused-set", "row-morning", date, 60.0, 8.0).copy(variationId = "paused", variationNameSnapshot = "Paused", notes = "Paused note", ordinal = 4))
            dao.workoutExercise(WorkoutExerciseEntity("squat-row", "morning", "squat", "squat", 1, "Unrelated squat notes", 60, null))
            dao.set(workingSet("squat-set", "squat-row", date, 100.0, 8.0))
        }
        val before = runBlocking { database.editorDao().sets().first() }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { CompositionLocalProvider(LocalContext provides context) { ProgressScreen(viewModel) } } }
        compose.waitUntil(15_000) { viewModel.workouts.value.size == 3 && viewModel.sets.value.size == 7 }
        compose.onNodeWithContentDescription("Choose exercise").performClick()
        compose.onNodeWithText("Bench press").performClick()
        val calendar = compose.onNodeWithContentDescription("exercise sessions calendar")
        calendar.performScrollTo()
        compose.onNodeWithContentDescription("Previous month for exercise sessions").performClick()
        val beforeHeight = calendar.fetchSemanticsNode().size.height
        val readout = compose.onNodeWithContentDescription("exercise sessions entry")
        val beforeReadout = readout.fetchSemanticsNode().boundsInRoot
        fun choose(day: LocalDate, count: Int) {
            val label = if (count == 1) "exercise session" else "exercise sessions"
            compose.onNodeWithContentDescription("${formatDisplayDate(day)}: $count $label").performClick()
            assertEquals(beforeHeight, calendar.fetchSemanticsNode().size.height)
            assertEquals(beforeReadout, readout.fetchSemanticsNode().boundsInRoot)
        }
        choose(today, 2)
        compose.onNode(hasText("2 exercise sessions") and hasClickAction()).performClick()
        compose.waitUntil(15_000) { viewModel.workoutLoggingData.value != null }
        compose.onNodeWithContentDescription("Exercise day details").assertExists()
        compose.onNodeWithText("morning workout").assertExists()
        compose.onNodeWithText("Notes: Warm-up note").assertExists()
        compose.onNodeWithText("Notes: Failed note").assertExists()
        compose.onNodeWithText("evening workout").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("yesterday workout").assertDoesNotExist()
        compose.onNodeWithText("Notes: Unrelated squat notes").assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithContentDescription("Close").performClick()
        choose(today.minusDays(1), 1)
        compose.onNode(hasText("1 exercise session") and hasClickAction()).assertExists()
        choose(today.minusDays(2), 0)
        compose.onNode(hasText("0 exercise sessions") and hasClickAction()).assertDoesNotExist()
        scrollToNode(hasContentDescription("Choose exercise"))
        compose.onNodeWithContentDescription("Variations: All variations").performClick()
        compose.onNodeWithText("Paused").performClick()
        calendar.performScrollTo()
        compose.onNodeWithContentDescription("Previous month for exercise sessions").performClick()
        compose.onNodeWithContentDescription("${formatDisplayDate(today)}: 1 exercise session").performClick()
        compose.onNode(hasText("1 exercise session") and hasClickAction()).performClick()
        compose.onNodeWithText("Notes: Paused note").assertExists()
        compose.onNodeWithText("evening workout").assertDoesNotExist()
        compose.onNodeWithText("Notes: Warm-up note").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(before, runBlocking { database.editorDao().sets().first() })
    }

    private fun workingSet(id: String, row: String, date: Long, weight: Double, rpe: Double) = WorkoutSetEntity(
        id = id, workoutExerciseId = row, ordinal = 1, setType = "WORKING", result = "COMPLETED",
        variationId = null, weightKg = weight, reps = 5.0, holdMillis = null, leftReps = null,
        rightReps = null, leftHoldMillis = null, rightHoldMillis = null, addedWeightKg = null,
        assistanceKg = null, rpe = rpe, romValue = null, romUnit = null, notes = "",
        loggedAt = date, updatedAt = date,
    )

    private fun scrollToNode(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(matcher)
        compose.waitForIdle()
    }

    private fun chooseRange(label: String) {
        scrollToNode(hasContentDescription("Progress date range"))
        compose.onNodeWithContentDescription("Progress date range").performClick()
        compose.onNodeWithText(label).performClick()
        if (label != "Custom") assertRange(label)
    }

    private fun assertRange(label: String) {
        compose.onNodeWithContentDescription("Progress date range")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label))
    }

    private fun chooseDate(field: String, date: LocalDate) {
        compose.onNodeWithContentDescription("Choose $field date").performClick()
        val inCalendar = hasAnyAncestor(hasContentDescription("$field calendar"))
        compose.onNode(hasContentDescription("year", substring = true) and hasClickAction() and inCalendar).performClick()
        compose.onNode((hasText(date.year.toString(), substring = true) or hasContentDescription(date.year.toString(), substring = true)) and hasClickAction() and inCalendar).performClick()
        compose.onNodeWithText("Choose month").performClick()
        compose.onNodeWithText(date.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())).performClick()
        val bounds = compose.onNodeWithContentDescription("$field calendar").fetchSemanticsNode().boundsInWindow
        compose.onAllNodes(hasText(date.dayOfMonth.toString(), substring = true) and hasClickAction() and inCalendar)
            .filterToOne(SemanticsMatcher("Visible date") {
                it.boundsInWindow.width > 0 && it.boundsInWindow.height > 0 && bounds.contains(it.boundsInWindow.center)
            }).performClick()
        compose.onNodeWithText("Selected: ${formatDisplayDate(date)}").assertExists()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Custom date range").assertExists()
    }
}

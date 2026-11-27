package com.petermathie.vibecheck

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.data.TrainingRepository
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.local.TrackerEntity
import com.petermathie.vibecheck.data.local.TrackerFieldEntity
import com.petermathie.vibecheck.data.local.TrackerDailyValueEntity
import com.petermathie.vibecheck.data.local.WorkoutEntity
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.ui.HistoryDayScreen
import com.petermathie.vibecheck.ui.HistoryScreen
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.HomeScreen
import com.petermathie.vibecheck.ui.MainViewModel
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import java.time.LocalDate
import com.petermathie.vibecheck.ui.formatDisplayDate
import com.petermathie.vibecheck.ui.formatHistoryWorkoutDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryUiTest {
    private lateinit var database: VibeDatabase

    @get:Rule
    val lifecycle = ComposeRoomLifecycleRule {
        if (::database.isInitialized) database else null
    }
    private val compose get() = lifecycle.compose

    @Test
    fun workoutHistoryShowsCapturedTitleAndGuardedEditorAndStatusOnlyRemoval() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val time = LocalDate.now().atTime(10, 1).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val saved = WorkoutEntity(
            "history-summary", null, "Saved day", "STRENGTH", "FINISHED",
            time - 60_000, time, "", null, false, programmeNameSnapshot = "Captured programme",
        )
        runBlocking { database.workoutDao().insertWorkouts(listOf(saved)) }
        val vm = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { HistoryScreen(vm) } }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Captured programme").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Body map").assertDoesNotExist()
        compose.onNodeWithText(formatDisplayDate(time)).assertIsDisplayed()
        val titleBounds = compose.onNodeWithText("Captured programme").fetchSemanticsNode().boundsInRoot
        val dateBounds = compose.onNodeWithText(formatDisplayDate(time)).fetchSemanticsNode().boundsInRoot
        val removeBounds = compose.onNodeWithContentDescription("Remove Captured programme").fetchSemanticsNode().boundsInRoot
        assertEquals(titleBounds.center.y, dateBounds.center.y, 1f)
        assertEquals(dateBounds.center.y, removeBounds.center.y, 1f)
        assertTrue(removeBounds.left - dateBounds.right >= with(compose.density) { 12.dp.toPx() } - 1f)
        compose.onNodeWithText("Keyword search").performTextInput("Saved day")
        compose.onNodeWithText("Captured programme").assertIsDisplayed()
        compose.onNodeWithText("Captured programme").performClick()
        compose.onNodeWithText("Saved day").assertIsDisplayed()
        compose.onNodeWithText("${formatHistoryWorkoutDate(time)},").assertIsDisplayed()
        val editorTitle = compose.onNodeWithText("Saved day").fetchSemanticsNode().boundsInRoot
        val editorDate = compose.onNodeWithContentDescription("Change workout date").fetchSemanticsNode().boundsInRoot
        val editorStart = compose.onNodeWithContentDescription("Change start time").fetchSemanticsNode().boundsInRoot
        assertTrue("Date/time range is below title", editorDate.top >= editorTitle.bottom)
        assertEquals(editorDate.center.y, editorStart.center.y, 1f)
        compose.onNodeWithText("Started").assertDoesNotExist()
        compose.onNodeWithText("Finished").assertDoesNotExist()
        compose.onNodeWithText("Logged duration: 00:01:00").assertIsDisplayed()
        compose.onNodeWithContentDescription("Change start time").assertIsDisplayed()
        compose.onNodeWithContentDescription("Change finish time").assertIsDisplayed()
        compose.onNodeWithContentDescription("Change start time").performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("11")
        compose.onNodeWithText("Apply").performClick()
        compose.waitUntil(15_000) {
            vm.error.value?.let { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() } == true
        }
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(saved, vm.workouts.value.single())
        compose.onNodeWithContentDescription("Back to history").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Captured programme").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Remove Captured programme").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals("FINISHED", vm.workouts.value.single().status)
        compose.onNodeWithContentDescription("Remove Captured programme").performClick()
        compose.onNodeWithText("Remove", useUnmergedTree = true).performClick()
        compose.waitUntil(15_000) { vm.workouts.value.single().status == "DISCARDED" }
        assertEquals(saved.copy(status = "DISCARDED"), vm.workouts.value.single())
    }

    @Test
    fun dateFilterCrossesClearOnlyTheirOwnedSelectionWithoutHelperButtons() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val vm = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { HistoryScreen(vm) } }
        val today = LocalDate.now()
        compose.onNodeWithText("Choose Start before End.").assertDoesNotExist()
        compose.onNodeWithText("Start").performClick()
        compose.onNode(hasText(today.dayOfMonth.toString()) and hasClickAction()).performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Without End, only Start day is shown.").assertDoesNotExist()
        compose.onNodeWithText("End").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Both dates are included.").assertDoesNotExist()
        compose.onNodeWithText("Clear End").assertDoesNotExist()
        compose.onNodeWithText("Clear dates").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear End date").performClick()
        compose.onNodeWithText("Start: ${formatDisplayDate(today)}").assertExists()
        compose.onNodeWithText("End").assertIsEnabled()
        compose.onNodeWithContentDescription("Clear End date").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear Start date").performClick()
        compose.onNodeWithText("Start").assertExists()
        compose.onNodeWithText("End").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Clear Start date").assertDoesNotExist()
    }

    @Test
    fun homeBreakdownListsRecordedValuesRatherThanOnlyTargetMetCounts() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val date = LocalDate.now().minusDays(1)
        val day = date.toEpochDay()
        val timestamp = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        runBlocking {
            database.trackerDao().insertTrackers(listOf(
                TrackerEntity("meditation", "Meditation", false),
                TrackerEntity("mood", "Mood", false),
                TrackerEntity("protein", "Protein", false),
            ))
            database.trackerDao().insertFields(listOf(
                TrackerFieldEntity("minutes", "meditation", "Duration", "NUMBER", "min", "AT_LEAST", 10.0, 0),
                TrackerFieldEntity("feeling", "mood", "Feeling", "TEXT", null, null, null, 0),
                TrackerFieldEntity("grams", "protein", "Protein", "NUMBER", "g", "AT_LEAST", 120.0, 0),
            ))
            database.trackerDao().upsertValue(TrackerDailyValueEntity("minutes", day, 12.0, null, null, "", timestamp))
            database.trackerDao().upsertValue(TrackerDailyValueEntity("feeling", day, null, null, "Calm", "", timestamp))
            database.trackerDao().upsertValue(TrackerDailyValueEntity("grams", day, 50.0, null, null, "", timestamp))
            database.workoutDao().insertWorkouts(listOf(
                WorkoutEntity("finished", null, "Finished training", "STRENGTH", "FINISHED", timestamp, timestamp + 1_000, "Recorded workout note", null, false),
                WorkoutEntity("draft", null, "Draft training", "STRENGTH", "DRAFT", timestamp, null, "", null, false),
                WorkoutEntity("other", null, "Another day", "STRENGTH", "FINISHED", timestamp - 86_400_000, timestamp - 86_400_000, "", null, false),
            ))
        }
        val viewModel = lifecycle.own(MainViewModel(
            TrainingRepository(database, database.programmeDao(), database.workoutDao(), database.trackerDao(), context),
            database.catalogueDao(),
        ))
        viewModel.selectHomeRecencyDay(day)
        compose.setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            VibeCheckTheme {
                HomeScreen(
                    state, viewModel::selectHistoryDay, viewModel::selectHomeRecencyDay, viewModel::setMode,
                    onDayDismiss = { viewModel.selectHistoryDay(null) },
                )
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("${formatDisplayDate(date)}: 3 activities, selected freshness date").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("${formatDisplayDate(date)}: 3 activities, selected freshness date").performTouchInput { doubleClick() }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Protein. Protein: 50.0 g").fetchSemanticsNodes().isNotEmpty()
        }
        for (activity in listOf(
            "Finished training. Strength workout. Recorded workout note",
            "Meditation. Duration: 12.0 min",
            "Mood. Feeling: Calm",
            "Protein. Protein: 50.0 g",
        )) compose.onNodeWithContentDescription(activity).assertIsDisplayed()
        compose.onNodeWithContentDescription("Draft training", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Another day", substring = true).assertDoesNotExist()
        assertEquals(4, viewModel.uiState.value.historyDay?.activities?.size)
        assertEquals("Draft training", viewModel.uiState.value.activeWorkout?.name)
        compose.onNodeWithContentDescription("Close day activity").performClick()
        compose.onNodeWithContentDescription("Home content").assertIsDisplayed()
        compose.onNodeWithContentDescription("Historical day page").assertDoesNotExist()
    }

    @Test
    fun navigatesHistoricalDaysAndSwitchesMapModesWithSelectedSemantics() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking { DatabaseSeeder(context, database).seedIfNeeded() }
        val repository = TrainingRepository(
            database,
            database.programmeDao(),
            database.workoutDao(),
            database.trackerDao(),
            context,
        )
        val viewModel = lifecycle.own(MainViewModel(repository, database.catalogueDao()))
        val firstDay = LocalDate.now().minusDays(1)
        viewModel.selectHistoryDay(firstDay.toEpochDay())
        runBlocking {
            val historicalState = withTimeout(15_000) {
                viewModel.uiState.first {
                    it.selectedHistoryDay == firstDay.toEpochDay() &&
                        it.historyDay?.activities?.isNotEmpty() == true &&
                        it.recency.isNotEmpty()
                }
            }
            assertTrue(historicalState.recency.any { it.band.name != "NEVER" })
        }

        compose.setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            VibeCheckTheme {
                state.selectedHistoryDay?.let {
                    HistoryDayScreen(
                        state = state,
                        onBack = { viewModel.selectHistoryDay(null) },
                        onDayChange = viewModel::selectHistoryDay,
                    )
                }

            }
        }

        compose.onNodeWithText(formatDisplayDate(firstDay), useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Reconstructed from records up to the end of this day").assertDoesNotExist()
        compose.onNodeWithContentDescription("Historical day controls").assertExists()
        compose.onNodeWithContentDescription("Freshness date").assertDoesNotExist()
        compose.onNodeWithText("Strength").assertDoesNotExist()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Planche + Push").fetchSemanticsNodes().isNotEmpty()
        }

        val frontMap = compose.onNodeWithContentDescription("male front freshness map")
        val mapBoundsBefore = frontMap.fetchSemanticsNode().boundsInRoot
        val inspectChest = frontMap.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label.startsWith("Inspect CHEST") }
        compose.runOnIdle { inspectChest.action() }
        compose.onNodeWithText("Chest Freshness details").assertIsDisplayed()
        val mapBoundsDuring = frontMap.fetchSemanticsNode().boundsInRoot
        assertEquals(mapBoundsBefore.left, mapBoundsDuring.left, 0.5f)
        assertEquals(mapBoundsBefore.top, mapBoundsDuring.top, 0.5f)
        assertEquals(mapBoundsBefore.right, mapBoundsDuring.right, 0.5f)
        assertEquals(mapBoundsBefore.bottom, mapBoundsDuring.bottom, 0.5f)
        compose.onNodeWithContentDescription("Close muscle details").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Chest Freshness details").assertDoesNotExist()
        frontMap.assertIsFocused()
        val mapBoundsAfter = frontMap.fetchSemanticsNode().boundsInRoot
        assertEquals(mapBoundsBefore.left, mapBoundsAfter.left, 0.5f)
        assertEquals(mapBoundsBefore.top, mapBoundsAfter.top, 0.5f)
        assertEquals(mapBoundsBefore.right, mapBoundsAfter.right, 0.5f)
        assertEquals(mapBoundsBefore.bottom, mapBoundsAfter.bottom, 0.5f)

        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(
                formatDisplayDate(firstDay.minusDays(1)),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Historical day controls").assertDoesNotExist()
    }

    @Test
    fun homeRecencyPreviewDoesNotOpenHistoricalDay() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        DatabaseSeeder(context, database).seedIfNeeded()
        val viewModel = lifecycle.own(MainViewModel(
            TrainingRepository(database, database.programmeDao(), database.workoutDao(), database.trackerDao(), context),
            database.catalogueDao(),
        ))
        val previewDay = LocalDate.now().minusDays(10).toEpochDay()

        viewModel.selectHomeRecencyDay(previewDay)
        val state = withTimeout(15_000) {
            viewModel.uiState.first { it.homeRecencyDay == previewDay }
        }

        assertEquals(previewDay, state.homeRecencyDay)
        assertNull(state.selectedHistoryDay)
    }

}

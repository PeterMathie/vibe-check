package com.petermathie.vibecheck

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.data.TrainingRepository
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.ui.HistoryDayScreen
import com.petermathie.vibecheck.ui.HistoryScreen
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.MainViewModel
import com.petermathie.vibecheck.ui.emptySet
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

        compose.onNodeWithText(firstDay.format(DATE_FORMAT), useUnmergedTree = true).assertExists()
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
                firstDay.minusDays(1).format(DATE_FORMAT),
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

    @Test
    fun workoutHistoryShowsRecordedTimingWithoutOpeningTheWorkout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val workoutId = runBlocking {
            DatabaseSeeder(context, database).seedIfNeeded()
            val repository = TrainingRepository(
                database,
                database.programmeDao(),
                database.workoutDao(),
                database.trackerDao(),
                context,
            )
            val id = repository.startWorkout("demo-day-push")
            val handstand = database.editorDao().workoutExercises().first()
                .first { it.workoutId == id && it.actualExerciseId == "core:handstand" }
            database.editorDao().set(
                emptySet(handstand.id, 1).copy(
                    holdMillis = 12_500,
                    timeUnderTensionMillis = 30_000,
                ),
            )
            val workout = database.editorDao().workouts().first().first { it.id == id }
            database.editorDao().workout(
                workout.copy(status = "FINISHED", finishedAt = System.currentTimeMillis()),
            )
            id
        }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                HistoryScreen(viewModel, onMap = {})
            }
        }

        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Handstand · TUT 12.5s · Total 30s")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Handstand · TUT 12.5s · Total 30s").assertIsDisplayed()
        compose.onNodeWithText("Back to history").assertDoesNotExist()
        assertTrue(runBlocking { database.editorDao().workouts().first().any { it.id == workoutId } })
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")
    }
}

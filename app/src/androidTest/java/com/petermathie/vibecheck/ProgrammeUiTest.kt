package com.petermathie.vibecheck

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.data.local.ExerciseEntity
import com.petermathie.vibecheck.data.local.ProgrammeDayEntity
import com.petermathie.vibecheck.data.local.ProgrammeEntity
import com.petermathie.vibecheck.data.local.ProgrammeExerciseEntity
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.local.WorkoutEntity
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.AppFabHostState
import com.petermathie.vibecheck.ui.AppFloatingAction
import com.petermathie.vibecheck.ui.LocalAppFabClearance
import com.petermathie.vibecheck.ui.LocalAppFabHost
import com.petermathie.vibecheck.ui.ProgrammeEditor
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgrammeUiTest {
    private lateinit var database: VibeDatabase

    @get:Rule
    val lifecycle = ComposeRoomLifecycleRule {
        if (::database.isInitialized) database else null
    }
    private val compose get() = lifecycle.compose

    @Test
    fun visibleEditOpensCoherentProgrammeEditorAndStartStillWorks() {
        seedProgramme()
        var startedDay: String? = null
        setProgrammeContent { day, _ -> startedDay = day }

        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithText("Strength").assertDoesNotExist()
        compose.onNodeWithText("Stretch").assertDoesNotExist()
        compose.onNodeWithText("Rename programme").assertDoesNotExist()
        compose.onNodeWithText("Duplicate").assertDoesNotExist()
        compose.onNodeWithText("Archive").assertDoesNotExist()
        compose.onNodeWithContentDescription("Reorder Alpha").assertDoesNotExist()
        compose.onNodeWithContentDescription("Start Alpha").performClick()
        assertEquals("day-a", startedDay)
        compose.onNodeWithText("Bench press").assertDoesNotExist()
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithContentDescription("Read-only exercises for Alpha").assertIsDisplayed()
        compose.onNodeWithText("Bench press").assertIsDisplayed()
        assertTrue(
            compose.onNodeWithText("Bench press").fetchSemanticsNode().boundsInRoot.top >
                compose.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.bottom,
        )
        compose.onNodeWithText("3 sets · 5–8 reps · 120s rest").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit targets for Bench press").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit prescription for Bench press").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit programme Alpha").performClick()

        compose.onNodeWithContentDescription("Back").assertIsDisplayed()
        compose.onNodeWithText("Back").assertDoesNotExist()
        compose.onNodeWithText("Strength").assertDoesNotExist()
        compose.onNodeWithText("Add workout").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("Alpha")).assertIsDisplayed()
        compose.onNodeWithText("Morning").assertDoesNotExist()
        compose.onNodeWithContentDescription("Reorder Morning").assertDoesNotExist()
        compose.onNodeWithText("Bench press").assertIsDisplayed()
        compose.onNodeWithText("3 sets · 5–8 reps · 120s rest").assertIsDisplayed()
        compose.onNodeWithText("Start workout").performClick()
        assertEquals("day-a", startedDay)

        compose.onNodeWithContentDescription("Edit workout Morning").assertDoesNotExist()
        compose.onNodeWithText("Rename workout").assertDoesNotExist()
        compose.onNodeWithText("Delete workout").assertDoesNotExist()
        compose.onNodeWithText("Add exercise").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add exercise or stretch").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit targets for Bench press").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit prescription for Bench press").assertIsDisplayed()
        compose.onNodeWithContentDescription("Reorder Bench press").assertDoesNotExist()
        compose.onNodeWithText("Exercise settings").assertDoesNotExist()

        compose.onNodeWithText("Duplicate").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsDisplayed()
        compose.onNodeWithText("Archive").assertIsDisplayed()
    }

    @Test
    fun expandedExercisesRemainBelowLongHeaderAtLargeFontScales() {
        seedProgramme()
        val longName = "Long programme name that wraps without covering exercises"
        runBlocking {
            database.editorDao().programme(
                database.editorDao().programmes().first().single().copy(name = longName),
            )
        }
        val fontScale = mutableStateOf(1f)
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                VibeCheckTheme {
                    ProgrammeEditor(viewModel, TrainingMode.STRENGTH, { _, _ -> })
                }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(longName).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(longName).performClick()

        listOf(1f, 1.3f, 2f).forEach { scale ->
            compose.runOnIdle { fontScale.value = scale }
            compose.waitForIdle()
            val title = compose.onNodeWithText(longName).fetchSemanticsNode().boundsInRoot
            val edit = compose.onNodeWithContentDescription("Edit programme $longName").fetchSemanticsNode().boundsInRoot
            val start = compose.onNodeWithContentDescription("Start $longName").fetchSemanticsNode().boundsInRoot
            val exercise = compose.onNodeWithText("Bench press").fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Expanded exercise overlaps header at ${scale}x: exercise=$exercise title=$title edit=$edit start=$start",
                exercise.top > maxOf(title.bottom, edit.bottom, start.bottom),
            )
        }
    }

    @Test
    fun draggingProgrammesDaysAndExercisesPersistsTheirOrder() {
        seedProgramme(includeSecondRows = true)
        setProgrammeContent { _, _ -> }

        val handleLeft = compose.onNodeWithContentDescription("Reorder Alpha").fetchSemanticsNode().boundsInRoot.left
        val titleLeft = compose.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.left
        assertTrue(handleLeft < titleLeft)
        dragDown("Reorder Alpha")
        compose.waitUntil(15_000) {
            runBlocking { database.editorDao().programmes().first().sortedBy { it.position }.map { it.id } } ==
                listOf("programme-b", "programme-a")
        }

        compose.onNodeWithContentDescription("Edit programme Alpha").performClick()
        val dayHandleLeft = compose.onNodeWithContentDescription("Reorder Morning").fetchSemanticsNode().boundsInRoot.left
        val dayTitleLeft = compose.onNodeWithText("Morning").fetchSemanticsNode().boundsInRoot.left
        assertTrue(dayHandleLeft < dayTitleLeft)
        dragDown("Reorder Morning")
        compose.waitUntil(15_000) {
            runBlocking {
                database.editorDao().days().first()
                    .filter { it.programmeId == "programme-a" }
                    .sortedBy { it.position }
                    .map { it.id }
            } == listOf("day-b", "day-a")
        }

        val entryHandleLeft = compose.onNodeWithContentDescription("Reorder Bench press").fetchSemanticsNode().boundsInRoot.left
        val entryTitleLeft = compose.onNodeWithText("Bench press").fetchSemanticsNode().boundsInRoot.left
        assertTrue(entryHandleLeft < entryTitleLeft)
        dragDown("Reorder Bench press")
        compose.waitUntil(15_000) {
            runBlocking {
                database.editorDao().entries().first()
                    .filter { it.programmeDayId == "day-a" }
                    .sortedBy { it.position }
                    .map { it.id }
            } == listOf("entry-b", "entry-a")
        }
    }

    @Test
    fun stretchingIsOneWorkoutWithIndividuallyReorderableExercises() {
        seedStretchProgramme()
        setProgrammeContent(mode = TrainingMode.STRETCHING) { _, _ -> }

        compose.onNodeWithText("Stretching").performClick()
        compose.onNodeWithContentDescription("Read-only exercises for Stretching").assertIsDisplayed()
        compose.onNodeWithText("Front split").assertIsDisplayed()
        compose.onNodeWithText("Forward fold").assertIsDisplayed()

        compose.onNodeWithContentDescription("Edit programme Stretching").performClick()
        compose.onNodeWithText("Stretching").assertIsDisplayed()
        compose.onNodeWithText("Forward Fold").assertDoesNotExist()
        compose.onNodeWithContentDescription("Reorder Front split").assertIsDisplayed()
        dragDown("Reorder Front split")
        compose.waitUntil(15_000) {
            runBlocking {
                database.editorDao().entries().first()
                    .filter { it.programmeDayId == "day-stretch" }
                    .sortedBy { it.position }
                    .map { it.id }
            } == listOf("entry-fold", "entry-split")
        }
    }

    @Test
    fun programmePrescriptionEditorPersistsAssignmentTargets() {
        seedProgramme()
        setProgrammeContent { _, _ -> }
        compose.onNodeWithContentDescription("Edit programme Alpha").performClick()
        compose.onNodeWithContentDescription("Edit prescription for Bench press").performClick()

        compose.onNodeWithContentDescription("Sets").performTextClearance()
        compose.onNodeWithContentDescription("Sets").performTextInput("4")
        compose.onNodeWithContentDescription("Minimum reps").performTextClearance()
        compose.onNodeWithContentDescription("Minimum reps").performTextInput("6")
        compose.onNodeWithContentDescription("Maximum reps").performTextClearance()
        compose.onNodeWithContentDescription("Maximum reps").performTextInput("10")
        compose.onNodeWithContentDescription("Target RPE").performTextInput("8")
        compose.onNodeWithContentDescription("Rest seconds").performTextClearance()
        compose.onNodeWithContentDescription("Rest seconds").performTextInput("90")
        compose.onNodeWithText("Save").performClick()

        compose.waitUntil(15_000) {
            runBlocking {
                database.editorDao().entries().first().single { it.id == "entry-a" }.let {
                    it.targetSets == 4 &&
                        it.targetRepsMin == 6 &&
                        it.targetRepsMax == 10 &&
                        it.targetRpe == 8.0 &&
                        it.restSeconds == 90
                }
            }
        }
    }

    @Test
    fun strengthAndStretchProgrammesExpandIndependentlyWithoutChangingTheirData() {
        seedProgramme(includeSecondRows = true)
        runBlocking {
            val dao = database.editorDao()
            dao.programme(activeProgrammes().single { it.id == "programme-b" }.copy(mode = "STRETCHING"))
            dao.exercise(ExerciseEntity("stretch", "Forward fold", "STRETCHING", "ROM_MEASUREMENT", null, null, "custom", true))
            dao.day(ProgrammeDayEntity("stretch-day", "programme-b", "Mobility", 0))
            dao.entry(ProgrammeExerciseEntity("stretch-entry", "stretch-day", "stretch", 0, 3, null, null, null, 60, null, "", null))
        }
        val before = runBlocking {
            val dao = database.editorDao()
            Triple(dao.programmes().first(), dao.days().first(), dao.entries().first())
        }
        setProgrammeContent { _, _ -> }

        compose.onNodeWithText("Alpha").assertIsDisplayed().performClick()
        compose.onNodeWithText("Beta").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Read-only exercises for Alpha").assertIsDisplayed()
        compose.onNodeWithContentDescription("Read-only exercises for Beta").assertIsDisplayed()
        compose.onNodeWithText("Bench press").assertIsDisplayed()
        compose.onNodeWithText("Forward fold").assertIsDisplayed()
        compose.onNodeWithText("Alpha").performClick()
        compose.onNodeWithContentDescription("Read-only exercises for Alpha").assertDoesNotExist()
        compose.onNodeWithContentDescription("Read-only exercises for Beta").assertIsDisplayed()
        assertEquals(before, runBlocking {
            val dao = database.editorDao()
            Triple(dao.programmes().first(), dao.days().first(), dao.entries().first())
        })
    }

    @Test
    fun newProgrammeActionIsOnlyAtTheScrollingEndOfTheList() {
        createDatabase()
        runBlocking {
            repeat(12) { index ->
                database.editorDao().programme(
                    ProgrammeEntity("programme-$index", "Plan $index", "STRENGTH", false, position = index),
                )
            }
        }
        setProgrammeContent { _, _ -> }

        compose.onNodeWithContentDescription("Create new programme").assertDoesNotExist()
        compose.onNodeWithText("New programme").assertIsNotDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("New programme"))
        compose.onNodeWithText("New programme").assertIsDisplayed()
        val finalCard = compose.onNodeWithText("Plan 11").fetchSemanticsNode().boundsInRoot
        val add = compose.onNodeWithText("New programme").fetchSemanticsNode().boundsInRoot
        assertTrue(add.top > finalCard.bottom)
        compose.onNode(hasScrollAction()).performScrollToIndex(0)
        compose.onNodeWithText("New programme").assertIsNotDisplayed()
    }

    @Test
    fun createRenameDuplicateArchiveAndDeleteRemainInEditContext() {
        createDatabase()
        runBlocking {
            database.editorDao().exercise(
                ExerciseEntity("new-exercise", "New exercise", "STRENGTH", "WEIGHT_REPS", null, null, "custom", true),
            )
        }
        setProgrammeContent { _, _ -> }

        compose.onNodeWithContentDescription("Create new programme").assertDoesNotExist()
        compose.onNodeWithText("New programme").assertIsDisplayed().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("My gym plan")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("No exercises yet").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add exercise").performScrollTo().performClick()
        compose.onNodeWithText("New exercise").performClick()
        compose.waitUntil(15_000) { activeProgrammes().any { it.name == "My gym plan" } }

        val programmeId = activeProgrammes().single().id
        val dayId = runBlocking { database.editorDao().days().first().single { it.programmeId == programmeId }.id }
        compose.waitUntil(30_000) {
            runBlocking {
                database.editorDao().entries().first().any {
                    it.programmeDayId == dayId && it.exerciseId == "new-exercise"
                }
            }
        }
        runBlocking {
            database.editorDao().workout(
                WorkoutEntity("historical", dayId, "Workout", "STRENGTH", "FINISHED", 1, 2, "", null, false),
            )
        }

        compose.onNodeWithText("Add workout").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("Renamed plan")
        compose.onNodeWithContentDescription("Save programme name").performClick()
        compose.waitUntil(15_000) { activeProgrammes().any { it.name == "Renamed plan" } }

        compose.onNodeWithText("Duplicate").performScrollTo().performClick()
        compose.waitUntil(15_000) { activeProgrammes().any { it.name == "Renamed plan (copy)" } }
        compose.onNodeWithText("Archive").performClick()
        compose.waitUntil(15_000) { activeProgrammes().none { it.name == "Renamed plan" } }
        compose.onNodeWithContentDescription("Edit programme Renamed plan (copy)").performClick()
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.waitUntil(15_000) { activeProgrammes().isEmpty() }
        assertEquals(listOf("historical"), runBlocking { database.editorDao().workouts().first().map { it.id } })
    }

    @Test
    fun emptyProgrammeCreatesDayThenAddsExerciseAndStretchInline() {
        createDatabase()
        runBlocking {
            val dao = database.editorDao()
            dao.exercise(ExerciseEntity("strength", "Bench press", "STRENGTH", "WEIGHT_REPS", null, null, "custom", true))
            dao.exercise(ExerciseEntity("stretch", "Forward fold", "STRETCHING", "ROM_MEASUREMENT", null, null, "custom", true))
            dao.programme(ProgrammeEntity("empty", "Empty plan", "STRENGTH", false))
        }
        setProgrammeContent { _, _ -> }

        compose.onNodeWithContentDescription("Edit programme Empty plan").performClick()
        compose.onNodeWithText("No exercises yet").assertDoesNotExist()
        compose.onNodeWithText("Add exercise").assertIsDisplayed().performClick()
        compose.onNode(hasText("Add exercise") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithText("Add stretch").assertIsDisplayed()
        compose.onNode(hasText("Add exercise") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("Bench press").performClick()
        compose.waitUntil(15_000) {
            runBlocking {
                database.editorDao().entries().first().singleOrNull()?.exerciseId == "strength"
            }
        }

        val dayId = runBlocking { database.editorDao().days().first().single().id }
        compose.onNodeWithText("Add exercise").performClick()
        compose.onNodeWithText("Add stretch").performClick()
        compose.onNodeWithText("Forward fold").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Forward fold").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(15_000) {
            runBlocking {
                database.editorDao().entries().first()
                    .filter { it.programmeDayId == dayId }
                    .map { it.exerciseId }
                    .toSet()
            } == setOf("strength", "stretch")
        }
    }

    private fun dragDown(description: String) {
        compose.onNodeWithContentDescription(description).performScrollTo().performTouchInput {
            down(center)
            repeat(5) {
                moveBy(Offset(0f, 60f))
                advanceEventTime(50)
            }
            up()
        }
    }

    private fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            VibeDatabase::class.java,
        ).build()
    }

    private fun seedProgramme(includeSecondRows: Boolean = false) {
        createDatabase()
        runBlocking {
            val dao = database.editorDao()
            dao.exercise(
                ExerciseEntity(
                    "exercise-a", "Bench press", "STRENGTH", "WEIGHT_REPS", null, null, "custom", true,
                    targetSets = 3, targetRepsMin = 5, targetRepsMax = 8,
                ),
            )
            dao.exercise(ExerciseEntity("exercise-b", "Row", "STRENGTH", "WEIGHT_REPS", null, null, "custom", true))
            dao.programme(ProgrammeEntity("programme-a", "Alpha", "STRENGTH", false, position = 0))
            dao.day(ProgrammeDayEntity("day-a", "programme-a", "Morning", 0))
            dao.entry(ProgrammeExerciseEntity("entry-a", "day-a", "exercise-a", 0, 3, 5, 8, null, 120, null, "", null))
            if (includeSecondRows) {
                dao.programme(ProgrammeEntity("programme-b", "Beta", "STRENGTH", false, position = 1))
                dao.day(ProgrammeDayEntity("day-b", "programme-a", "Evening", 1))
                dao.entry(ProgrammeExerciseEntity("entry-b", "day-a", "exercise-b", 1, 3, 8, 10, null, 90, null, "", null))
            }
        }
    }

    private fun seedStretchProgramme() {
        createDatabase()
        runBlocking {
            val dao = database.editorDao()
            dao.exercise(ExerciseEntity("stretch-split", "Front split", "STRETCHING", "ROM_MEASUREMENT", null, null, "custom", true))
            dao.exercise(ExerciseEntity("stretch-fold", "Forward fold", "STRETCHING", "ROM_MEASUREMENT", null, null, "custom", true))
            dao.programme(ProgrammeEntity("programme-stretch", "Stretching", "STRETCHING", false, position = 0))
            dao.day(ProgrammeDayEntity("day-stretch", "programme-stretch", "Stretching", 0))
            dao.entry(ProgrammeExerciseEntity("entry-split", "day-stretch", "stretch-split", 0, 3, null, null, null, 60, null, "", null))
            dao.entry(ProgrammeExerciseEntity("entry-fold", "day-stretch", "stretch-fold", 1, 3, null, null, null, 60, null, "", null))
        }
    }

    private fun setProgrammeContent(
        mode: TrainingMode = TrainingMode.STRENGTH,
        onStart: (String, Boolean) -> Unit,
    ) {
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                val host = remember { AppFabHostState() }
                CompositionLocalProvider(
                    LocalAppFabHost provides host,
                    LocalAppFabClearance provides 0.dp,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        ProgrammeEditor(viewModel, mode, onStart)
                        host.action?.takeIf { it.visible }?.let {
                            AppFloatingAction(
                                action = it,
                                host = host,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
        compose.waitUntil(15_000) {
            val active = activeProgrammes()
            active.isEmpty() || active.any { programme ->
                compose.onAllNodesWithText(programme.name).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    private fun activeProgrammes() = runBlocking {
        database.editorDao().programmes().first().filterNot { it.isArchived }
    }
}

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
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
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
import com.petermathie.vibecheck.data.TrainingRepository
import com.petermathie.vibecheck.domain.model.SetDraft
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.AppFabHostState
import com.petermathie.vibecheck.ui.AppFloatingAction
import com.petermathie.vibecheck.ui.LocalAppFabClearance
import com.petermathie.vibecheck.ui.LocalAppFabHost
import com.petermathie.vibecheck.ui.ProgrammeEditor
import com.petermathie.vibecheck.ui.HabitGlassDialogHost
import com.petermathie.vibecheck.ui.ProgrammeExercisePreview
import com.petermathie.vibecheck.ui.ProgrammeEntryPrescriptionUpdate
import com.petermathie.vibecheck.ui.MainViewModel
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import com.petermathie.vibecheck.ui.theme.VibePalettes
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
    fun singleTapExpandsAndDoubleTapOpensProgrammeModalWithoutEditButton() {
        seedProgramme()
        var startedDay: String? = null
        setProgrammeContent { day, _, _ -> startedDay = day }

        compose.onNodeWithText("Alpha").assertIsDisplayed()
        compose.onNodeWithText("Strength").assertDoesNotExist()
        compose.onNodeWithText("Stretch").assertDoesNotExist()
        compose.onNodeWithText("Rename programme").assertDoesNotExist()
        compose.onNodeWithText("Duplicate").assertDoesNotExist()
        compose.onNodeWithText("Archive").assertDoesNotExist()
        compose.onNodeWithContentDescription("Reorder Alpha").assertIsDisplayed()
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
        compose.onNodeWithText("3 sets").assertIsDisplayed()
        compose.onNodeWithText("120s rest").assertIsDisplayed()
        compose.onAllNodesWithText("|").assertCountEquals(0)
        compose.onNodeWithTag("Programme preview divider 0 entry-a").assertIsDisplayed()
        compose.onNodeWithTag("Programme preview divider 1 entry-a").assertIsDisplayed()
        compose.onNodeWithContentDescription("Edit targets for Bench press").assertDoesNotExist()
        compose.onNodeWithContentDescription("Programme exercise Bench press").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit programme Alpha").assertDoesNotExist()
        openProgramme("Alpha")

        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithText("Back").assertDoesNotExist()
        compose.onNodeWithText("Strength").assertDoesNotExist()
        compose.onNodeWithText("Add workout").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("Alpha")).assertIsDisplayed()
        compose.onNodeWithText("Morning").assertDoesNotExist()
        compose.onNodeWithContentDescription("Reorder Morning").assertDoesNotExist()
        modalText("Bench press").assertIsDisplayed()
        compose.onNodeWithText("3 sets · 5–8 reps · 120s rest").assertDoesNotExist()
        compose.onNodeWithContentDescription("Sets for Bench press").assertIsDisplayed()
        compose.onNodeWithContentDescription("Rest seconds for Bench press").assertIsDisplayed()
        compose.onNodeWithText("Start workout").assertDoesNotExist()
        assertEquals("day-a", startedDay)

        compose.onNodeWithContentDescription("Edit workout Morning").assertDoesNotExist()
        compose.onNodeWithText("Rename workout").assertDoesNotExist()
        compose.onNodeWithText("Delete workout").assertDoesNotExist()
        compose.onNodeWithText("Add exercise").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add exercise or stretch").assertDoesNotExist()
        compose.onNodeWithContentDescription("Edit targets for Bench press").assertDoesNotExist()
        compose.onNodeWithContentDescription("Programme exercise Bench press").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithContentDescription("Reorder Bench press").assertIsDisplayed()
        compose.onNodeWithText("Exercise settings").assertDoesNotExist()

        compose.onNodeWithText("Duplicate").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Delete").assertIsDisplayed()
        compose.onNodeWithText("Archive").assertIsDisplayed()
        val footer = listOf("Save", "Archive", "Duplicate", "Delete").map { label ->
            compose.onNode(hasText(label) and hasClickAction()).getUnclippedBoundsInRoot()
        }
        assertTrue(footer.zipWithNext().all { (left, right) ->
            left.right <= right.left && kotlin.math.abs(left.top.value - right.top.value) < 1f
        })
        val gaps = footer.zipWithNext().map { (left, right) -> right.left.value - left.right.value }
        assertTrue("Footer label gaps are equal", gaps.max() - gaps.min() <= 1f)
        val actions = compose.onNodeWithTag("Programme editor actions").getUnclippedBoundsInRoot()
        assertEquals(actions.left.value, footer.first().left.value, 1f)
        assertEquals(actions.right.value, footer.last().right.value, 1f)
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
                    ProgrammeEditor(viewModel, TrainingMode.STRENGTH, onResume = {}) { _, _, _ -> }
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
            val start = compose.onNodeWithContentDescription("Start $longName").fetchSemanticsNode().boundsInRoot
            val exercise = compose.onNodeWithText("Bench press").fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Expanded exercise overlaps header at ${scale}x: exercise=$exercise title=$title start=$start",
                exercise.top > maxOf(title.bottom, start.bottom),
            )
        }
    }

    @Test
    fun previewUsesOneAlignedThinDividedRowAndKeepsMissingColumnsBlank() {
        val missing = mutableStateOf(false)
        val fontScale = mutableStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                VibeCheckTheme {
                    Box(Modifier.padding(8.dp)) {
                        ProgrammeExercisePreview("fixture", "Handstands", if (missing.value) null else 3, if (missing.value) null else 120)
                    }
                }
            }
        }
        listOf(1f, 2f).forEach { scale ->
            compose.runOnIdle { fontScale.value = scale; missing.value = false }
            val fields = listOf("name", "sets", "rest").map { column ->
                compose.onNodeWithTag("Programme preview $column fixture")
            }
            val bounds = fields.map { it.fetchSemanticsNode().boundsInRoot }
            assertTrue(bounds.zipWithNext().all { (left, right) -> left.right < right.left && kotlin.math.abs(left.center.y - right.center.y) < 2f })
            assertEquals("Sets column stays centred", (bounds.first().left + bounds.last().right) / 2, bounds[1].center.x, 1f)
            fields.forEachIndexed { index, field ->
                val results = mutableListOf<TextLayoutResult>()
                field.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
                val layout = results.single()
                assertEquals("No wrapping at ${scale}x", 1, layout.lineCount)
                when (index) {
                    0 -> assertEquals(0f, layout.getLineLeft(0), 2f)
                    1 -> assertEquals(layout.size.width / 2f, (layout.getLineLeft(0) + layout.getLineRight(0)) / 2, 2f)
                    2 -> assertEquals(layout.size.width.toFloat(), layout.getLineRight(0), 2f)
                }
            }
            compose.onAllNodesWithText("|").assertCountEquals(0)
            (0..1).forEach { index ->
                val divider = compose.onNodeWithTag("Programme preview divider $index fixture")
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(divider.width > 0 && divider.height > 0)
                assertTrue(divider.center.x > bounds[index].right && divider.center.x < bounds[index + 1].left)
            }
            compose.runOnIdle { missing.value = true }
            fields[1].assertTextEquals("")
            fields[2].assertTextEquals("")
            fields.zip(bounds).forEach { (field, before) ->
                val after = field.fetchSemanticsNode().boundsInRoot
                assertEquals("Missing values retain column positions", before.left, after.left, 1f)
                assertEquals("Missing values retain column widths", before.right, after.right, 1f)
            }
        }
    }

    @Test
    fun programmeDeletionNeedsConfirmationAndCancelOrClosePreservesTemplateAndHistory() {
        seedProgramme()
        val history = WorkoutEntity("history", "day-a", "Recorded workout", "STRENGTH", "FINISHED", 1, 2, "Keep notes", 80.0, false)
        runBlocking { database.editorDao().workout(history) }
        val before = runBlocking {
            val dao = database.editorDao()
            Triple(dao.programmes().first(), dao.days().first(), dao.entries().first())
        }
        setProgrammeContent { _, _, _ -> }
        openProgramme("Alpha")
        listOf("Cancel", "Close").forEach { dismiss ->
            modalText("Delete").performClick()
            compose.onAllNodes(isDialog()).assertCountEquals(1)
            compose.onNodeWithText("Delete programme?").assertIsDisplayed()
            if (dismiss == "Close") compose.onNodeWithContentDescription("Close").performClick()
            else compose.onNodeWithText("Cancel").performClick()
            assertEquals(before, runBlocking {
                val dao = database.editorDao()
                Triple(dao.programmes().first(), dao.days().first(), dao.entries().first())
            })
            assertEquals(history, runBlocking { database.editorDao().workouts().first().single() })
        }
        modalText("Delete").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(15_000) { activeProgrammes().isEmpty() }
        assertEquals(history.copy(programmeDayId = null), runBlocking { database.editorDao().workouts().first().single() })
        assertTrue(runBlocking { database.editorDao().entries().first().isEmpty() })
    }

    @Test
    fun onlyActiveProgrammePlayIsBlueAndResumesItsActualDay() {
        seedProgramme(includeSecondRows = true)
        runBlocking {
            val dao = database.editorDao()
            dao.day(ProgrammeDayEntity("day-c", "programme-b", "Other workout", 0))
            val assignment = dao.entries().first().first()
            dao.entry(assignment.copy(id = "entry-evening", programmeDayId = "day-b"))
            dao.entry(assignment.copy(id = "entry-other", programmeDayId = "day-c"))
        }
        var resumes = 0
        var starts = 0
        setProgrammeContent(onResume = { resumes++ }) { _, _, _ -> starts++ }
        fun hasBluePlay(name: String): Boolean {
            val pixels = compose.onNodeWithContentDescription("Start $name").captureToImage().toPixelMap()
            val blue = VibePalettes.Ocean.dark.accent
            var matches = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val pixel = pixels[x, y]
                if (kotlin.math.abs(pixel.red - blue.red) < 2f / 255 &&
                    kotlin.math.abs(pixel.green - blue.green) < 2f / 255 &&
                    kotlin.math.abs(pixel.blue - blue.blue) < 2f / 255) matches++
            }
            return matches >= 4
        }
        assertTrue(!hasBluePlay("Alpha") && !hasBluePlay("Beta"))
        val activeId = runBlocking { trainingRepository().startWorkout("day-b") }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Start Alpha").fetchSemanticsNodes().single().config[SemanticsProperties.StateDescription] ==
                "Workout in progress"
        }
        assertTrue(hasBluePlay("Alpha") && !hasBluePlay("Beta"))
        compose.onNodeWithContentDescription("Start Alpha").performClick()
        assertEquals(1, resumes)
        assertEquals(0, starts)
        assertEquals(activeId, runBlocking { database.workoutDao().draft()?.id })
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }

    @Test
    fun draggingProgrammesDaysAndExercisesPersistsTheirOrder() {
        seedProgramme(includeSecondRows = true)
        setProgrammeContent { _, _, _ -> }

        val handleLeft = compose.onNodeWithContentDescription("Reorder Alpha").fetchSemanticsNode().boundsInRoot.left
        val titleLeft = compose.onNodeWithText("Alpha").fetchSemanticsNode().boundsInRoot.left
        assertTrue(handleLeft <= titleLeft)
        dragDown("Reorder Alpha")
        compose.waitUntil(15_000) {
            runBlocking { database.editorDao().programmes().first().sortedBy { it.position }.map { it.id } } ==
                listOf("programme-b", "programme-a")
        }

        openProgramme("Alpha")
        val dayHandleLeft = compose.onNodeWithContentDescription("Reorder Morning").fetchSemanticsNode().boundsInRoot.left
        val dayTitleLeft = compose.onNodeWithText("Morning").fetchSemanticsNode().boundsInRoot.left
        assertTrue(dayHandleLeft <= dayTitleLeft)
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
        assertTrue(entryHandleLeft <= entryTitleLeft)
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
        setProgrammeContent(mode = TrainingMode.STRETCHING) { _, _, _ -> }

        compose.onNodeWithText("Stretching").performClick()
        compose.onNodeWithContentDescription("Read-only exercises for Stretching").assertIsDisplayed()
        compose.onNodeWithText("Front split").assertIsDisplayed()
        compose.onNodeWithText("Forward fold").assertIsDisplayed()

        openProgramme("Stretching")
        compose.onNode(hasSetTextAction() and hasText("Stretching")).assertIsDisplayed()
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
    fun inlineOnlyPrescriptionEditingPreservesHiddenTargetsWithoutOpeningAChildModal() {
        seedProgramme()
        runBlocking {
            val entry = database.editorDao().entries().first().single()
            database.editorDao().entry(entry.copy(targetHoldSeconds = 42, targetRpe = 8.0))
        }
        setProgrammeContent { _, _, _ -> }
        openProgramme("Alpha")
        compose.onNodeWithContentDescription("Programme exercise Bench press").performScrollTo().assertHasNoClickAction()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithText("Programme prescription").assertDoesNotExist()
        compose.onNodeWithContentDescription("Target seconds").assertDoesNotExist()
        compose.onNodeWithContentDescription("Target RPE").assertDoesNotExist()
        compose.onNodeWithContentDescription("Minimum reps").assertDoesNotExist()
        compose.onNodeWithContentDescription("Maximum reps").assertDoesNotExist()
        compose.onNodeWithContentDescription("Sets for Bench press").performTextReplacement("4")
        compose.onNodeWithContentDescription("Rest seconds for Bench press").performTextReplacement("90")

        compose.waitUntil(15_000) {
            runBlocking {
                database.editorDao().entries().first().single { it.id == "entry-a" }.let {
                    it.targetSets == 4 &&
                        it.targetRepsMin == 5 &&
                        it.targetRepsMax == 8 &&
                        it.targetRpe == 8.0 &&
                        it.targetHoldSeconds == 42 &&
                        it.restSeconds == 90
                }
            }
        }
        compose.onAllNodes(isDialog()).assertCountEquals(1)
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
        setProgrammeContent { _, _, _ -> }

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
        setProgrammeContent { _, _, _ -> }

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
        setProgrammeContent { _, _, _ -> }

        compose.onNodeWithContentDescription("Create new programme").assertDoesNotExist()
        compose.onNodeWithText("New programme").assertIsDisplayed().performClick()
        awaitProgrammeModal()
        compose.onNode(hasSetTextAction()).performTextReplacement("My gym plan")
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
        compose.waitUntil(15_000) { activeProgrammes().any { it.name == "Renamed plan" } }

        compose.onNodeWithText("Duplicate").performScrollTo().performClick()
        compose.waitUntil(15_000) { activeProgrammes().any { it.name == "Renamed plan (copy)" } }
        compose.onNodeWithText("Archive").performClick()
        compose.waitUntil(15_000) { activeProgrammes().none { it.name == "Renamed plan" } }
        openProgramme("Renamed plan (copy)")
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Delete programme?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(15_000) { activeProgrammes().isEmpty() }
        assertEquals(listOf("historical"), runBlocking { database.editorDao().workouts().first().map { it.id } })
    }

    @Test
    fun numberedCreationAutosavesNamesAndKeepsColourThroughDuplicate() {
        seedProgramme(includeSecondRows = true)
        setProgrammeContent { _, _, _ -> }
        compose.onNodeWithText("New programme").performClick()
        awaitProgrammeModal()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(
                androidx.compose.ui.semantics.SemanticsProperties.EditableText,
                androidx.compose.ui.text.AnnotatedString("Program 3"),
            ),
        )
        compose.onNodeWithContentDescription("Save programme name").assertDoesNotExist()
        compose.onNodeWithText("Add exercise").assertIsEnabled()
        val saved = activeProgrammes().single { it.name == "Program 3" }
        assertEquals(1, runBlocking { database.editorDao().days().first().count { it.programmeId == saved.id } })
        compose.onNodeWithContentDescription("Change programme colour").performClick()
        compose.onNodeWithContentDescription("Set programme colour Blue").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("   ")
        modalText("Save").assertIsNotEnabled()
        assertEquals("Program 3", activeProgrammes().single { it.id == saved.id }.name)
        compose.onNode(hasSetTextAction()).performTextReplacement("Coloured plan")
        compose.waitUntil(15_000) { activeProgrammes().single { it.id == saved.id }.let { it.name == "Coloured plan" && it.colourArgb == 0xFF42A5F5L } }
        compose.onNodeWithContentDescription("Change programme colour").performClick()
        compose.onNodeWithContentDescription("Set programme colour Orange").performClick()
        compose.waitUntil(15_000) { activeProgrammes().single { it.id == saved.id }.colourArgb == 0xFFFFA726L }
        compose.onNode(hasSetTextAction()).performTextReplacement("Renamed")
        compose.onNodeWithText("Duplicate").performScrollTo().performClick()
        compose.waitUntil(15_000) { activeProgrammes().any { it.name == "Renamed (copy)" } }
        assertTrue(activeProgrammes().filter { it.id == saved.id || it.name == "Renamed (copy)" }.all { it.colourArgb == 0xFFFFA726L })
        compose.onNodeWithContentDescription("Close").performClick()
        openProgramme("Renamed")
        compose.onNode(hasSetTextAction()).assertTextContains("Renamed")
        modalText("Save").performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun programmeExerciseHasSmallXBeforeNameAndRequiresRemovalConfirmation() {
        seedProgramme()
        setProgrammeContent { _, _, _ -> }
        openProgramme("Alpha")
        compose.onNodeWithContentDescription("Delete programme exercise Bench press").assertDoesNotExist()
        compose.onNodeWithContentDescription("Swipe actions for programme exercise Bench press").assertDoesNotExist()
        val remove = compose.onNodeWithContentDescription("Remove Bench press from programme").performScrollTo()
        val name = compose.onNodeWithContentDescription("Programme exercise Bench press")
        assertTrue(remove.getUnclippedBoundsInRoot().right <= name.getUnclippedBoundsInRoot().left)
        remove.performClick()
        compose.onNodeWithText("Remove Bench press from programme?").assertIsDisplayed()
        assertEquals(1, runBlocking { database.editorDao().entries().first().size })
        modalText("Cancel").performClick()
        assertEquals(1, runBlocking { database.editorDao().entries().first().size })
        remove.performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(1, runBlocking { database.editorDao().entries().first().size })
        remove.performClick()
        modalText("Remove").performClick()
        compose.waitUntil(15_000) { runBlocking { database.editorDao().entries().first().isEmpty() } }
        assertEquals(2, runBlocking { database.editorDao().exercises().first().size })
        assertEquals(1, activeProgrammes().size)
    }

    @Test
    fun normalPhoneDialogKeepsXNameAndReadablePrescriptionsOnOneRow() {
        seedProgramme()
        runBlocking {
            val dao = database.editorDao()
            dao.entry(requireNotNull(dao.programmeEntryById("entry-a")).copy(targetSets = 4, restSeconds = 120))
        }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                VibeCheckTheme {
                    HabitGlassDialogHost(constrainToContent = true) { backdrop, _ ->
                        Box(Modifier.fillMaxSize().then(backdrop)) {
                            ProgrammeEditor(viewModel, TrainingMode.STRENGTH, onResume = {}) { _, _, _ -> }
                        }
                    }
                }
            }
        }
        openProgramme("Alpha")
        compose.waitForIdle()
        val name = compose.onNodeWithContentDescription("Programme exercise Bench press").fetchSemanticsNode().boundsInRoot
        val remove = compose.onNodeWithContentDescription("Remove Bench press from programme").fetchSemanticsNode().boundsInRoot
        val fields = listOf(
            compose.onNodeWithContentDescription("Sets for Bench press"),
            compose.onNodeWithContentDescription("Rest seconds for Bench press"),
        )
        assertTrue(remove.right <= name.left)
        val bounds = fields.map { it.fetchSemanticsNode().boundsInRoot }
        assertTrue(name.right <= bounds[0].left && bounds[0].right <= bounds[1].left)
        for (field in fields) {
            assertEquals(name.center.y, field.fetchSemanticsNode().boundsInRoot.center.y, 1f)
            val layouts = mutableListOf<TextLayoutResult>()
            field.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
        }
        for (suffix in listOf("sets", "s")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNode(hasText(suffix) and hasAnyAncestor(isDialog()), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty() && layouts.none { it.hasVisualOverflow })
        }
    }

    @Test
    fun inlinePrescriptionsRetainRapidEditsRejectInvalidValuesAndPreserveOtherData() {
        seedProgramme(includeSecondRows = true)
        val original = runBlocking {
            val dao = database.editorDao()
            val entry = requireNotNull(dao.programmeEntryById("entry-a")).copy(
                targetHoldSeconds = 42, targetRpe = 8.0, notes = "Legacy note",
                supersetGroup = "pair", inputConfig = """{"legacy":true}""",
            )
            dao.entry(entry)
            entry
        }
        val other = runBlocking { database.editorDao().programmeEntryById("entry-b") }
        var inset = Color.Unspecified
        setProgrammeContent(onInset = { inset = it }) { _, _, _ -> }
        openProgramme("Alpha")
        val sets = compose.onNodeWithContentDescription("Sets for Bench press").performScrollTo()
        val rest = compose.onNodeWithContentDescription("Rest seconds for Bench press")
        sets.assertTextContains("sets")
        rest.assertTextContains("s")
        for (field in listOf(sets, rest)) {
            val pixels = field.captureToImage().toPixelMap()
            val background = pixels[2, pixels.height / 2]
            assertEquals(inset.red, background.red, 1f / 255)
            assertEquals(inset.green, background.green, 1f / 255)
            assertEquals(inset.blue, background.blue, 1f / 255)
        }
        val nameBounds = compose.onNodeWithContentDescription("Programme exercise Bench press").fetchSemanticsNode().boundsInRoot
        val setsBounds = sets.fetchSemanticsNode().boundsInRoot
        val restBounds = rest.fetchSemanticsNode().boundsInRoot
        assertTrue(nameBounds.right <= setsBounds.left)
        assertTrue(setsBounds.right <= restBounds.left)
        assertEquals(setsBounds.center.y, restBounds.center.y, 1f)
        val setText = requireNotNull(sets.fetchSemanticsNode().config[SemanticsActions.SetText].action)
        val restText = requireNotNull(rest.fetchSemanticsNode().config[SemanticsActions.SetText].action)
        compose.runOnIdle {
            setText(AnnotatedString("4"))
            restText(AnnotatedString("90"))
        }
        compose.waitUntil(15_000) {
            runBlocking { database.editorDao().programmeEntryById("entry-a") } == original.copy(targetSets = 4, restSeconds = 90)
        }
        assertEquals("4", sets.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertEquals("90", rest.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        sets.performTextReplacement("0")
        rest.performTextReplacement("-1")
        compose.onNode(hasText("Save") and hasClickAction()).assertIsNotEnabled()
        compose.waitForIdle()
        assertEquals(original.copy(targetSets = 4, restSeconds = 90), runBlocking {
            database.editorDao().programmeEntryById("entry-a")
        })
        sets.performTextReplacement("")
        rest.performTextReplacement("0")
        compose.onNode(hasText("Save") and hasClickAction()).assertIsEnabled().performClick()
        compose.waitUntil(15_000) { compose.onAllNodes(isDialog()).fetchSemanticsNodes().isEmpty() }
        assertEquals(original.copy(targetSets = null, restSeconds = 0), runBlocking {
            database.editorDao().programmeEntryById("entry-a")
        })
        assertEquals(other, runBlocking { database.editorDao().programmeEntryById("entry-b") })
    }

    @Test
    fun queuedPrescriptionsUseLatestRowsAndFailedSavesLeaveDataAndCallbacksIntact() {
        seedProgramme(includeSecondRows = true)
        val original = runBlocking { requireNotNull(database.editorDao().programmeEntryById("entry-a")) }
        val viewModel = lifecycle.own(EditorViewModel(database))
        var prepared: ProgrammeExerciseEntity? = null
        var preparationFailed = false
        compose.runOnIdle {
            viewModel.setProgrammeSetCount(original.id, 4)
            viewModel.moveEntry(original.id, 1)
            viewModel.setProgrammeRestSeconds(original.id, 90)
            viewModel.prepareProgrammePrescription(original.id, { prepared = it }, { preparationFailed = true })
        }
        val updated = original.copy(position = 1, targetSets = 4, restSeconds = 90)
        compose.waitUntil(15_000) { prepared != null || preparationFailed }
        assertTrue(!preparationFailed)
        assertEquals(updated, prepared)
        var childSucceeded = false
        compose.runOnIdle {
            viewModel.moveEntry(original.id, -1)
            viewModel.saveProgrammePrescription(
                updated.copy(targetSets = 5, restSeconds = 30),
                onSaved = { childSucceeded = true }, onFailure = { preparationFailed = true },
            )
        }
        compose.waitUntil(15_000) { childSucceeded || preparationFailed }
        assertTrue(childSucceeded && !preparationFailed)
        val latest = original.copy(targetSets = 5, restSeconds = 30)
        assertEquals(latest, runBlocking { database.editorDao().programmeEntryById(original.id) })
        var childFailed = false
        var childSaved = false
        compose.runOnIdle {
            viewModel.saveProgrammePrescription(
                updated.copy(id = "missing-entry"),
                onSaved = { childSaved = true }, onFailure = { childFailed = true },
            )
        }
        compose.waitUntil(15_000) { childFailed }
        assertTrue(!childSaved && viewModel.error.value != null)
        assertEquals(latest, runBlocking { database.editorDao().programmeEntryById(original.id) })
        runBlocking {
            val dao = database.editorDao()
            dao.day(ProgrammeDayEntity("foreign-day", "programme-b", "Foreign", 0))
            dao.entry(original.copy(id = "foreign-entry", programmeDayId = "foreign-day"))
        }
        var footerFailed = false
        var footerSaved = false
        compose.runOnIdle {
            viewModel.saveProgrammeEditor(
                "programme-a", "Must roll back", listOf(ProgrammeEntryPrescriptionUpdate("foreign-entry", 9, 30)),
                onSaved = { footerSaved = true }, onFailure = { footerFailed = true },
            )
        }
        compose.waitUntil(15_000) { footerFailed || footerSaved }
        assertTrue(footerFailed && !footerSaved)
        assertEquals("Alpha", activeProgrammes().first { it.id == "programme-a" }.name)
        assertEquals(original.copy(id = "foreign-entry", programmeDayId = "foreign-day"), runBlocking {
            database.editorDao().programmeEntryById("foreign-entry")
        })
    }

    @Test
    fun queuedProgrammeColourNameOrderAndArchiveChangesDoNotOverwriteEachOther() {
        seedProgramme(includeSecondRows = true)
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.runOnIdle {
            viewModel.setProgrammeColour("programme-a", 0xFFFFA726L)
            viewModel.renameProgramme("programme-a", "Renamed")
            viewModel.moveProgramme("programme-a", 1)
            viewModel.archiveProgramme("programme-a")
        }
        compose.waitUntil(15_000) {
            runBlocking { database.editorDao().archivedProgrammes().first() }.singleOrNull()?.let {
                it.id == "programme-a" && it.name == "Renamed" && it.colourArgb == 0xFFFFA726L && it.position == 1
            } == true
        }
    }

    @Test
    fun queuedRenameDuplicateAndImmediateAddReusePersistedProgrammeAndDay() {
        seedProgramme()
        val programme = activeProgrammes().single()
        val viewModel = lifecycle.own(EditorViewModel(database))
        val preparedDays = mutableListOf<String>()
        compose.runOnIdle {
            viewModel.renameProgramme(programme.id, "Latest name")
            viewModel.duplicate(programme)
            viewModel.ensureProgrammeDay(programme.id) { preparedDays.add(it) }
            viewModel.ensureProgrammeDay(programme.id) { preparedDays.add(it) }
        }
        compose.waitUntil(15_000) { preparedDays.size == 2 && activeProgrammes().size == 2 }
        assertEquals(listOf("day-a", "day-a"), preparedDays)
        assertTrue(activeProgrammes().any { it.name == "Latest name (copy)" })
        assertEquals(1, runBlocking { database.editorDao().days().first().count { it.programmeId == programme.id } })
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
        setProgrammeContent { _, _, _ -> }

        openProgramme("Empty plan")
        compose.onNodeWithText("No exercises yet").assertDoesNotExist()
        compose.onNodeWithText("Add exercise").assertIsDisplayed().performClick()
        compose.onNode(hasText("Add exercise") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithText("Add stretch").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").assertIsDisplayed()
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

    @Test
    fun resumeOnlyAppearsWhileActiveAndSameDayPlayDoesNotCreateAnotherWorkout() {
        seedProgramme()
        val repository = trainingRepository()
        var resumes = 0
        var starts = 0
        setProgrammeContent(onResume = { resumes++ }) { _, _, _ -> starts++ }
        compose.onNodeWithContentDescription("Resume workout").assertDoesNotExist()

        val id = runBlocking { repository.startWorkout("day-a") }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Resume workout").fetchSemanticsNodes().isNotEmpty()
        }
        val before = runBlocking { database.editorDao().workouts().first() }
        compose.onNodeWithContentDescription("Resume workout").performClick()
        compose.onNodeWithContentDescription("Start Alpha").performClick()
        assertEquals(2, resumes)
        assertEquals(0, starts)
        assertEquals(before, runBlocking { database.editorDao().workouts().first() })
        compose.onNodeWithText("New programme").assertIsDisplayed()
        openProgramme("Alpha")
        compose.onNodeWithContentDescription("Resume workout").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithContentDescription("Resume workout").assertIsDisplayed()

        runBlocking {
            database.workoutDao().updateWorkout(before.single { it.id == id }.copy(status = "DISCARDED"))
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Resume workout").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun overviewRetainsGuardedSwitchingWhileMultidayEditorHasNoStartActions() {
        seedProgramme(includeSecondRows = true)
        runBlocking {
            database.editorDao().day(ProgrammeDayEntity("day-c", "programme-b", "Other session", 0))
            trainingRepository().startWorkout("day-a")
        }
        val choices = mutableListOf<Triple<String, String?, Boolean>>()
        setProgrammeContent { day, activeId, finish -> choices.add(Triple(day, activeId, finish)) }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Resume workout").fetchSemanticsNodes().isNotEmpty()
        }
        val before = runBlocking { database.editorDao().workouts().first() }
        val activeId = before.single().id
        compose.onNodeWithContentDescription("Start Beta").performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onNodeWithContentDescription("Resume workout").assertDoesNotExist()
        val actions = listOf("Save", "Discard", "Cancel").map {
            compose.onNode(hasClickAction() and hasText(it, substring = true))
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        actions.zipWithNext().forEach { (left, right) ->
            assertEquals("Switch options must share one row", left.center.y, right.center.y, 1f)
            assertTrue("Switch options must not overlap", left.right <= right.left)
        }
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(choices.isEmpty())
        compose.onNodeWithText("Programmes").assertIsDisplayed()
        assertEquals(before, runBlocking { database.editorDao().workouts().first() })

        compose.onNodeWithContentDescription("Start Beta").performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(Triple("day-c", activeId, true), choices.single())
        compose.onNodeWithContentDescription("Start Beta").performClick()
        compose.onNodeWithText("Discard").performClick()
        assertEquals(Triple("day-c", activeId, false), choices.last())

        openProgramme("Alpha")
        compose.onAllNodesWithText("Start workout").assertCountEquals(0)
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithContentDescription("Start Beta").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(2, choices.size)
        assertEquals(before, runBlocking { database.editorDao().workouts().first() })
    }

    @Test
    fun saveAndStartOnlyNavigatesAndEmitsCompletionAfterSuccessfulFinish() {
        seedProgramme(includeSecondRows = true)
        val repository = trainingRepository()
        val activeId = runBlocking {
            database.editorDao().day(ProgrammeDayEntity("day-c", "programme-b", "Other session", 0))
            repository.startWorkout("day-a")
        }
        val main = lifecycle.own(MainViewModel(repository, database.catalogueDao()))
        var navigations = 0
        var rejection: String? = null
        setProgrammeContent { day, currentId, finish ->
            main.startWorkout(
                day, currentId, finish,
                onStarted = { navigations++ },
                onRejected = { rejection = it },
            )
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Resume workout").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Start Beta").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(15_000) { rejection != null && !main.isStartingWorkout.value }
        assertTrue(rejection.orEmpty().contains("completed working set"))
        assertEquals(0, navigations)
        assertEquals(null, main.completionEvents.value)
        assertEquals(activeId, runBlocking { database.workoutDao().draft()?.id })

        val row = runBlocking { database.workoutDao().exercises(activeId).first() }
        runBlocking { repository.addSet(row.workoutExerciseId, SetDraft(reps = 5.0)) }
        val saved = runBlocking { database.workoutDao().sets(row.workoutExerciseId) }
        compose.onNodeWithContentDescription("Start Beta").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(15_000) { navigations == 1 && !main.isStartingWorkout.value }
        assertEquals(TrainingMode.STRENGTH, main.completionEvents.value?.mode)
        assertEquals("FINISHED", runBlocking { database.workoutDao().workout(activeId)?.status })
        assertEquals(saved, runBlocking { database.workoutDao().sets(row.workoutExerciseId) })
        assertEquals("day-c", runBlocking { database.workoutDao().draft()?.programmeDayId })
        assertEquals(1, runBlocking { database.workoutDao().observeFinishedWorkouts().first().size })
    }

    private fun trainingRepository() = TrainingRepository(
        database, database.programmeDao(), database.workoutDao(), database.trackerDao(),
        ApplicationProvider.getApplicationContext(),
    )

    private fun modalText(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()))

    private fun awaitProgrammeModal() {
        compose.waitUntil(15_000) {
            compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Edit programme"))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openProgramme(name: String) {
        compose.onNodeWithText(name).performScrollTo().performTouchInput { doubleClick() }
        awaitProgrammeModal()
    }

    private fun dragDown(description: String) {
        compose.onNodeWithContentDescription(description).performScrollTo().performTouchInput {
            down(center)
            advanceEventTime(700)
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
        onResume: () -> Unit = {},
        onInset: (Color) -> Unit = {},
        onStart: (String, String?, Boolean) -> Unit,
    ) {
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                onInset(LocalVibePalette.current.surfaceInset)
                val host = remember { AppFabHostState() }
                CompositionLocalProvider(
                    LocalAppFabHost provides host,
                    LocalAppFabClearance provides 0.dp,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        ProgrammeEditor(viewModel, mode, onResume = onResume, onStart = onStart)
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

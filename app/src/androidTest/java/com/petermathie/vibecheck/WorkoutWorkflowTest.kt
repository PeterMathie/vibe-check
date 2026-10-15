package com.petermathie.vibecheck

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.data.TrainingRepository
import com.petermathie.vibecheck.data.local.ProgrammeExerciseEntity
import com.petermathie.vibecheck.data.local.ExerciseEntity
import com.petermathie.vibecheck.data.local.ProgrammeDayEntity
import com.petermathie.vibecheck.data.local.ProgrammeEntity
import com.petermathie.vibecheck.data.local.WorkoutEntryDraftEntity
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import com.petermathie.vibecheck.domain.model.SetDraft
import com.petermathie.vibecheck.ui.MainViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutWorkflowTest {
    private lateinit var database: VibeDatabase
    private lateinit var repository: TrainingRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        DatabaseSeeder(context, database).seedIfNeeded()
        repository = TrainingRepository(
            database,
            database.programmeDao(),
            database.workoutDao(),
            database.trackerDao(),
            context,
        )
    }

    @After
    fun tearDown() = database.close()

    @Test(timeout = 120_000)
    fun programmeToFinishedWorkoutUpdatesDerivedRecency() = runBlocking {
        val day = repository.observeProgrammeDays().first { it.isNotEmpty() }.first()
        val workoutId = repository.startWorkout(day.id)
        val draft = repository.observeDraft().first { it != null }
        assertNotNull(draft)

        val firstExercise = requireNotNull(draft).exercises.first()
        repository.addSet(firstExercise.id, SetDraft(reps = 5.0, notes = "Emulator workflow"))

        val autosaved = repository.observeDraft().first { active ->
            active?.exercises?.firstOrNull()?.sets?.isNotEmpty() == true
        }
        assertTrue(requireNotNull(autosaved).exercises.first().sets.isNotEmpty())

        val completion = repository.finishWorkout(workoutId)
        assertEquals(day.mode, completion?.mode)
        assertTrue(requireNotNull(completion).affectedMuscleIds.isNotEmpty())
        assertNull(repository.observeDraft().first())
        assertTrue(repository.observeMuscleRecency(day.mode).first { it.isNotEmpty() }.isNotEmpty())
    }

    @Test(timeout = 120_000)
    fun strengthAndStretchCompletionDeriveMappedMusclesButEmptyWorkoutIsRejected() = runBlocking {
        val days = repository.observeProgrammeDays().first { rows ->
            rows.any { it.mode == com.petermathie.vibecheck.domain.model.TrainingMode.STRENGTH } &&
                rows.any { it.mode == com.petermathie.vibecheck.domain.model.TrainingMode.STRETCHING }
        }
        for (mode in com.petermathie.vibecheck.domain.model.TrainingMode.entries) {
            val day = days.first { it.mode == mode }
            val workoutId = repository.startWorkout(day.id, replaceExisting = true)
            val draft = requireNotNull(repository.observeDraft().first { it?.id == workoutId })
            repository.addSet(draft.exercises.first().id, SetDraft(reps = 1.0, holdMillis = 1_000))

            val completion = requireNotNull(repository.finishWorkout(workoutId))
            assertEquals(mode, completion.mode)
            assertEquals(completion.affectedMuscleIds.distinct().sorted(), completion.affectedMuscleIds)
            assertTrue(completion.affectedMuscleIds.isNotEmpty())
        }

        val emptyDay = days.first()
        val emptyWorkoutId = repository.startWorkout(emptyDay.id, replaceExisting = true)
        assertNull(repository.finishWorkout(emptyWorkoutId))
        assertEquals(
            "DRAFT",
            database.editorDao().workouts().first().single { it.id == emptyWorkoutId }.status,
        )
    }

    @Test(timeout = 120_000)
    fun completionEventPrecedesNavigationAndIsAbsentForInvalidFinish() = runBlocking {
        val day = repository.observeProgrammeDays().first { it.isNotEmpty() }.first()
        val workoutId = repository.startWorkout(day.id)
        val viewModel = MainViewModel(repository, database.catalogueDao())
        val rejected = CompletableDeferred<String>()
        var navigated = false

        viewModel.finishWorkout(
            workoutId,
            onFinished = { navigated = true },
            onRejected = { rejected.complete(it) },
        )
        assertTrue(rejected.await().contains("completed working set"))
        assertTrue(!navigated)
        assertNull(viewModel.completionEvents.value)

        val draft = requireNotNull(repository.observeDraft().first { it?.id == workoutId })
        repository.addSet(draft.exercises.first().id, SetDraft(reps = 5.0))
        val finished = CompletableDeferred<Unit>()
        viewModel.finishWorkout(
            workoutId,
            onFinished = {
                assertNotNull(viewModel.completionEvents.value)
                navigated = true
                finished.complete(Unit)
            },
            onRejected = { error(it) },
        )
        finished.await()
        val event = requireNotNull(viewModel.completionEvents.value)
        assertEquals(day.mode, event.mode)
        assertEquals(day.mode, viewModel.uiState.value.mode)
        assertTrue(event.affectedMuscleIds.isNotEmpty())
        viewModel.consumeCompletionEvent(event.id)
        assertNull(viewModel.completionEvents.value)
        assertNull(repository.finishWorkout(workoutId))
    }

    @Test(timeout = 120_000)
    fun startingAnotherProgrammeRequiresExplicitReplacementAndUsesItsName() = runBlocking {
        val days = repository.observeProgrammeDays().first { it.size >= 3 }
        assertTrue(days.none { Regex("^(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\b").containsMatchIn(it.name) })
        val push = days.first { it.id == "demo-day-push" }
        val legs = days.first { it.id == "demo-day-legs" }

        val pushId = repository.startWorkout(push.id)
        assertEquals(pushId, repository.startWorkout(legs.id))

        val legsId = repository.startWorkout(legs.id, replaceExisting = true)
        val active = requireNotNull(repository.observeDraft().first { it?.id == legsId })
        assertEquals("Legs + Mobility", active.name)
        assertTrue(active.exercises.any { it.exerciseName == "Back squat" })
        assertTrue(active.exercises.none { it.exerciseName == "Planche" })
    }

    @Test(timeout = 120_000)
    fun genericDayNameUsesProgrammeNameAndSanitizesSnapshotNotes() = runBlocking {
        val dao = database.editorDao()
        dao.exercise(ExerciseEntity("custom", "Custom", "STRENGTH", "REPS", null, null, "user", true))
        dao.programme(ProgrammeEntity("custom-programme", "Actual workout name", "STRENGTH", false))
        dao.day(ProgrammeDayEntity("custom-day", "custom-programme", "Workout", 0))
        val leaked =
            "Private programme source 2f55a8f5-5878-4295-aa5d-22db384a9338-2. LEGS + MOBILITY.md, line 20. " +
                "Explicit rest: 2 minutes; achieved added load/reps were not promoted."
        dao.entry(
            ProgrammeExerciseEntity(
                "custom-entry", "custom-day", "custom", 0, 1, 5, 5, null, 120, null,
                "$leaked\nKeep hips square", null,
            ),
        )

        val workoutId = repository.startWorkout("custom-day")
        val active = requireNotNull(repository.observeDraft().first { it?.id == workoutId })
        assertEquals("Actual workout name", active.name)
        assertEquals("Keep hips square", active.exercises.single().notes)
    }

    @Test(timeout = 120_000)
    fun frontSplitUsesTimeBasedMeasurementInFreshAndStartedWorkouts() = runBlocking {
        val exercise = database.editorDao().exercises().first().single { it.id == "core:front-split" }
        assertEquals("HOLD", exercise.trackingType)

        val workoutId = repository.startWorkout("demo-day-front-splits")
        val active = requireNotNull(repository.observeDraft().first { it?.id == workoutId })
        val frontSplit = active.exercises.single { it.actualExerciseId == "core:front-split" }
        assertEquals(com.petermathie.vibecheck.domain.model.TrackingType.HOLD, frontSplit.trackingType)
    }

    @Test(timeout = 120_000)
    fun saveCurrentAndStartFinishesOnlyAQualifyingWorkout() = runBlocking {
        val currentId = repository.startWorkout("demo-day-push")
        val viewModel = MainViewModel(repository, database.catalogueDao())
        val rejected = CompletableDeferred<String>()
        viewModel.saveCurrentAndStartWorkout(
            currentId,
            "demo-day-legs",
            onRejected = { rejected.complete(it) },
        )
        assertTrue(rejected.await().contains("completed working set"))
        assertEquals(currentId, requireNotNull(repository.observeDraft().first()).id)

        val active = requireNotNull(repository.observeDraft().first())
        repository.addSet(active.exercises.first().id, SetDraft(reps = 5.0))
        database.editorDao().entryDraft(
            WorkoutEntryDraftEntity(
                workoutExerciseId = active.exercises.last().id,
                setId = "partial-set",
                ordinal = 1,
                performance = "12.",
                rpe = "",
                detailsOpen = false,
                warmUp = false,
                failed = false,
                bandIds = "[]",
                variationId = null,
                leftValue = "",
                rightValue = "",
                addedWeight = "",
                assistance = "",
                romValue = "",
                romUnit = "cm",
                updatedAt = 1,
            ),
        )
        val started = CompletableDeferred<Unit>()
        viewModel.saveCurrentAndStartWorkout(
            currentId,
            "demo-day-legs",
            onStarted = { started.complete(Unit) },
            onRejected = { error(it) },
        )
        started.await()
        assertEquals("FINISHED", database.editorDao().workouts().first().single { it.id == currentId }.status)
        assertEquals(
            "12.",
            database.editorDao().entryDrafts(active.exercises.last().id).single().performance,
        )
        assertEquals("Legs + Mobility", requireNotNull(repository.observeDraft().first()).name)
    }

    @Test(timeout = 120_000)
    fun programmeAssignmentsSnapshotIndependentPrescriptions() = runBlocking {
        val dao = database.editorDao()
        val handstand = dao.exercises().first().first { it.id == "core:handstand" }
        val canonicalConfig = "weightUnit=;bandResistance=false;timeHeld=true;timeUnderTension=true;reps=false"
        dao.exercise(
            handstand.copy(
                inputConfig = canonicalConfig,
                targetSets = 9,
                targetRpe = 10.0,
                restSeconds = 15,
            ),
        )
        val pushEntry = dao.entries().first().first { it.exerciseId == handstand.id }
        val legsEntry = dao.entries().first().first {
            it.programmeDayId == "demo-day-legs" && it.exerciseId == handstand.id
        }
        dao.entry(
            pushEntry.copy(
                targetSets = 2,
                targetHoldSeconds = 20,
                targetRpe = 7.0,
                restSeconds = 60,
                inputConfig = "legacy-programme-config",
            ),
        )
        dao.entry(
            legsEntry.copy(
                targetSets = 5,
                targetHoldSeconds = 45,
                targetRpe = 9.0,
                restSeconds = 150,
                inputConfig = "another-legacy-config",
            ),
        )

        val pushWorkoutId = repository.startWorkout("demo-day-push")
        val pushSnapshot = dao.workoutExercises().first()
            .first { it.workoutId == pushWorkoutId && it.plannedExerciseId == handstand.id }
        assertEquals(canonicalConfig, pushSnapshot.inputConfig)
        assertEquals(60, pushSnapshot.restSeconds)
        assertEquals(2, pushSnapshot.targetSets)
        assertEquals(20, pushSnapshot.targetHoldSeconds)
        assertEquals(7.0, pushSnapshot.targetRpe)
        assertEquals("2 sets · 20 seconds · RPE 7", pushSnapshot.targets)

        val legsWorkoutId = repository.startWorkout("demo-day-legs", replaceExisting = true)
        val legsSnapshot = dao.workoutExercises().first()
            .first { it.workoutId == legsWorkoutId && it.plannedExerciseId == handstand.id }
        assertEquals(canonicalConfig, legsSnapshot.inputConfig)
        assertEquals(150, legsSnapshot.restSeconds)
        assertEquals(5, legsSnapshot.targetSets)
        assertEquals(45, legsSnapshot.targetHoldSeconds)
        assertEquals(9.0, legsSnapshot.targetRpe)
        assertEquals("5 sets · 45 seconds · RPE 9", legsSnapshot.targets)
    }

    @Test(timeout = 120_000)
    fun freshCatalogueContainsOnlyMaintainedExercises() = runBlocking {
        val exercises = database.editorDao().exercises().first()
        assertEquals(17, exercises.size)
        assertTrue(exercises.none { it.source == "free-exercise-db" })
        assertTrue(exercises.all { it.source == "vibe-trainer" })
        val usedIds = database.editorDao().entries().first().map { it.exerciseId }.toSet()
        assertEquals(exercises.map { it.id }.toSet(), usedIds)
        val variations = database.editorDao().variations().first()
        val wall = variations.single { it.id == "handstand-wall" }
        val freestanding = variations.single { it.id == "handstand-free" }
        assertTrue(wall.inputConfig.contains("timeHeld=true"))
        assertTrue(wall.inputConfig.contains("timeUnderTension=true"))
        assertTrue(freestanding.inputConfig.contains("timeHeld=false"))
        assertTrue(freestanding.inputConfig.contains("timeUnderTension=true"))
        assertTrue(variations.single { it.id == "pull-up-assisted" }.inputConfig.contains("bandResistance=true"))
        assertTrue(variations.single { it.id == "pull-up-bodyweight" }.inputConfig.contains("bodyweight=true"))
        assertTrue(variations.single { it.id == "pull-up-weighted" }.inputConfig.contains("addedWeight=true"))
    }
}

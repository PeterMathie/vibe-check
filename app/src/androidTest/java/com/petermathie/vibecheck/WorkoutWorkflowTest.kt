package com.petermathie.vibecheck

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.data.TrainingRepository
import com.petermathie.vibecheck.data.local.ProgrammeExerciseEntity
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import com.petermathie.vibecheck.domain.model.SetDraft
import com.petermathie.vibecheck.ui.MainViewModel
import com.petermathie.vibecheck.ui.emptySet
import com.petermathie.vibecheck.ui.emptyEntryDraft
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
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
    fun newWorkoutCapturesProgrammeNameAndEffectiveExerciseWithoutChangingResume() = runBlocking {
        val dao = database.editorDao()
        val day = requireNotNull(database.programmeDao().day("demo-day-push"))
        dao.renameProgramme(day.programmeId, "Programme at start")
        dao.exerciseOverride(com.petermathie.vibecheck.data.local.ExerciseOverrideEntity("core:handstand", "Local handstand"))
        val id = repository.startWorkout(day.id)
        assertEquals(day.name, database.workoutDao().workout(id)!!.name)
        assertEquals("Programme at start", database.workoutDao().workout(id)!!.programmeNameSnapshot)
        assertEquals("Local handstand", dao.workoutExercisesForWorkout(id).first().exerciseName)
        dao.renameProgramme(day.programmeId, "Renamed later")
        assertEquals(id, repository.startWorkout(day.id))
        assertEquals("Programme at start", database.workoutDao().workout(id)!!.programmeNameSnapshot)
    }

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
    fun plancheBandCorrectionUpdatesDefaultsAndDraftsOnceWithoutRewritingHistory() = runBlocking {
        val dao = database.editorDao()
        val workout = repository.startWorkout("demo-day-push")
        val row = dao.workoutExercises().first().single { it.workoutId == workout && it.actualExerciseId == "core:planche" }
        val config = "weightUnit=lb;timeHeld=true;manyTuts=false;bandResistance=false;measurementOrder=tut,bands,rpe"
        val exercise = requireNotNull(dao.exerciseById("core:planche"))
        dao.exercise(exercise.copy(inputConfig = config))
        dao.variations().first().filter { it.exerciseId == exercise.id }.forEach { dao.variation(it.copy(inputConfig = config)) }
        val variant = dao.variations().first().first { it.exerciseId == exercise.id }
        dao.variation(variant.copy(id = "custom-planche", name = "Custom Planche", inputConfig = config, isSeeded = false))
        val assignments = dao.entries().first().filter { it.exerciseId == exercise.id }
        assignments.forEach { dao.entry(it.copy(inputConfig = config)) }
        dao.workoutExercise(row.copy(inputConfig = config))
        val saved = emptySet(row.id, 1).copy(variationId = variant.id, holdMillis = 12_000, variationInputConfigSnapshot = config)
        dao.set(saved)
        val historicalRow = row.copy(id = "historical-planche", workoutId = "demo-workout-push-1", inputConfig = config)
        dao.workoutExercise(historicalRow)
        val historicalSet = saved.copy(id = "historical-set", workoutExerciseId = historicalRow.id)
        dao.set(historicalSet)
        val beforeBands = dao.setBands().first()
        database.openHelper.writableDatabase.execSQL("DELETE FROM seed_metadata WHERE `key`='planche-band-measurement'")
        val context = ApplicationProvider.getApplicationContext<Context>()
        DatabaseSeeder(context, database).seedIfNeeded()
        val expected = config.replace("bandResistance=false", "bandResistance=true")
        assertEquals(expected.split(";").sorted(), dao.exerciseById(exercise.id)!!.inputConfig.split(";").sorted())
        dao.variations().first().filter { it.exerciseId == exercise.id }.forEach {
            assertTrue(ExerciseInputConfig.decode(it.inputConfig, it.trackingType).bandResistance)
        }
        assertEquals(
            config.split(";").filterNot { it.startsWith("bandResistance=") },
            dao.workoutExerciseById(row.id)!!.inputConfig.split(";").filterNot { it.startsWith("bandResistance=") },
        )
        assertTrue(ExerciseInputConfig.decode(dao.setById(saved.id)!!.variationInputConfigSnapshot, "SKILL_HOLD").bandResistance)
        assertEquals(historicalRow, dao.workoutExerciseById(historicalRow.id))
        assertEquals(historicalSet, dao.setById(historicalSet.id))
        assertEquals(beforeBands, dao.setBands().first())
        assignments.forEach { assignment ->
            assertTrue(ExerciseInputConfig.decode(dao.entries().first().single { it.id == assignment.id }.inputConfig, "SKILL_HOLD").bandResistance)
        }
        dao.exercise(exercise.copy(inputConfig = config))
        DatabaseSeeder(context, database).seedIfNeeded()
        assertEquals(config, dao.exerciseById(exercise.id)?.inputConfig)
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
    fun programmeSwitchDiscardsTheDraftWithoutAddingHistory() = runBlocking {
        val id = repository.startWorkout("demo-day-push")
        val row = database.workoutDao().exercises(id).first()
        repository.addSet(row.workoutExerciseId, SetDraft(reps = 5.0))
        database.editorDao().entryDraft(emptyEntryDraft(row.workoutExerciseId, 2).copy(rpe = "7"))
        val history = database.workoutDao().observeFinishedWorkouts().first()

        assertNull(repository.startProgrammeWorkout("demo-day-legs", id, finishCurrent = false))

        assertEquals("DISCARDED", database.workoutDao().workout(id)?.status)
        assertEquals("demo-day-legs", database.workoutDao().draft()?.programmeDayId)
        assertEquals(history, database.workoutDao().observeFinishedWorkouts().first())
        assertTrue(database.editorDao().entryDrafts(row.workoutExerciseId).isEmpty())
        assertEquals(1, database.editorDao().workouts().first().count { it.status == "DRAFT" })
    }

    @Test(timeout = 120_000)
    fun failedProgrammeStartRollsBackFinishingAndDiscardingWithAllDraftData() = runBlocking {
        val id = repository.startWorkout("demo-day-push")
        val row = database.workoutDao().exercises(id).first()
        repository.addSet(row.workoutExerciseId, SetDraft(reps = 5.0, notes = "Keep this snapshot"))
        database.editorDao().entryDraft(emptyEntryDraft(row.workoutExerciseId, 2).copy(rpe = "7"))
        val before = database.workoutDao().workout(id)
        val sets = database.workoutDao().sets(row.workoutExerciseId)
        val drafts = database.editorDao().entryDrafts(row.workoutExerciseId)
        val history = database.workoutDao().observeFinishedWorkouts().first()

        listOf(false, true).forEach { finish ->
            val result = runCatching { repository.startProgrammeWorkout("missing-day", id, finish) }
            assertTrue(result.isFailure)
            assertEquals(before, database.workoutDao().workout(id))
            assertEquals(id, database.workoutDao().draft()?.id)
            assertEquals(sets, database.workoutDao().sets(row.workoutExerciseId))
            assertEquals(drafts, database.editorDao().entryDrafts(row.workoutExerciseId))
            assertEquals(history, database.workoutDao().observeFinishedWorkouts().first())
        }
    }

    @Test(timeout = 120_000)
    fun programmeSwitchRejectsEmptyInvalidAndStaleActiveState() = runBlocking {
        val id = repository.startWorkout("demo-day-push")
        val row = database.workoutDao().exercises(id).first()
        val empty = runCatching {
            repository.startProgrammeWorkout("demo-day-legs", id, finishCurrent = true)
        }
        assertTrue(empty.exceptionOrNull()?.message.orEmpty().contains("completed working set"))
        assertEquals(id, database.workoutDao().draft()?.id)
        repository.addSet(row.workoutExerciseId, SetDraft(reps = 5.0))
        val draft = emptyEntryDraft(row.workoutExerciseId, 2).copy(timeHeld = ".")
        database.editorDao().entryDraft(draft)
        val invalid = runCatching {
            repository.startProgrammeWorkout("demo-day-legs", id, finishCurrent = true)
        }
        assertTrue(invalid.exceptionOrNull()?.message.orEmpty().contains("highlighted numbers"))
        assertEquals(draft, database.editorDao().entryDrafts(row.workoutExerciseId).single())
        listOf(null, "stale-id").forEach { expected ->
            listOf(false, true).forEach { finish ->
                val stale = runCatching {
                    repository.startProgrammeWorkout("demo-day-legs", expected, finish)
                }
                assertTrue(stale.exceptionOrNull()?.message.orEmpty().contains("active workout changed"))
                assertEquals(id, database.workoutDao().draft()?.id)
            }
        }
        assertEquals("DRAFT", database.workoutDao().workout(id)?.status)
    }

    @Test(timeout = 120_000)
    fun persistedClearedHoldCannotHideInHistoryThroughFinishOrProgrammeSwitch() = runBlocking {
        val id = repository.startWorkout("demo-day-push")
        val dao = database.editorDao()
        val row = dao.workoutExercisesForWorkout(id).first { it.actualExerciseId == "core:handstand" }
        val config = ExerciseInputConfig(timeHeld = true, reps = false).encode()
        val saved = emptySet(row.id, 1).copy(
            variationId = "handstand-wall", holdMillis = 30_000L,
            variationInputConfigSnapshot = config, variationTrackingTypeSnapshot = "HOLD",
        )
        dao.set(saved)
        dao.set(emptySet(row.id, 2).copy(holdMillis = 5_000L))
        val cleared = emptyEntryDraft(row.id, 1).copy(
            setId = saved.id, variationId = saved.variationId, tutEntries = "[\"\"]",
        )
        dao.entryDraft(cleared)
        val workouts = dao.workouts().first()
        val history = database.workoutDao().observeFinishedWorkouts().first()

        listOf(false, true).forEach { viaProgramme ->
            val result = runCatching {
                if (viaProgramme) repository.startProgrammeWorkout("demo-day-legs", id, true)
                else repository.finishWorkout(id)
            }
            assertTrue(
                "A pending cleared saved measurement must not be discarded into History",
                result.exceptionOrNull()?.message.orEmpty().contains("cleared measurement"),
            )
            assertEquals(workouts, dao.workouts().first())
            assertEquals(saved, dao.setById(saved.id))
            assertEquals(listOf(cleared), dao.entryDrafts(row.id))
            assertEquals(history, database.workoutDao().observeFinishedWorkouts().first())
        }
    }

    @Test(timeout = 120_000)
    fun programmeSwitchPreservesMetricGuardsAndRecordedConfigurationPrecedence() = runBlocking {
        val id = repository.startWorkout("demo-day-push")
        val dao = database.editorDao()
        val row = dao.workoutExercisesForWorkout(id).first { it.actualExerciseId == "core:planche" }
        val variation = dao.variations().first().first { it.exerciseId == row.actualExerciseId }
        val ambiguous = "bandResistance=true;timeHeld=true;reps=true"
        dao.workoutExercise(row.copy(inputConfig = ambiguous, targetSets = 1))
        dao.variation(variation.copy(inputConfig = ambiguous))
        val saved = emptySet(row.id, 1).copy(variationId = variation.id, holdMillis = 5_000)
        dao.set(saved)
        val before = dao.workouts().first()

        val rejected = runCatching {
            repository.startProgrammeWorkout("demo-day-legs", id, finishCurrent = true)
        }
        assertTrue(rejected.exceptionOrNull()?.message.orEmpty().contains("Choose logging metrics"))
        assertEquals(before, dao.workouts().first())
        assertEquals(saved, dao.setById(saved.id))

        val recorded = saved.copy(
            variationInputConfigSnapshot = "bandResistance=true;timeHeld=true;reps=false",
            variationTrackingTypeSnapshot = "HOLD",
            variationNameSnapshot = "Recorded hold",
        )
        dao.set(recorded)
        assertNotNull(repository.startProgrammeWorkout("demo-day-legs", id, finishCurrent = true))
        assertEquals("FINISHED", database.workoutDao().workout(id)?.status)
        assertEquals(recorded, dao.setById(saved.id))
        assertEquals("demo-day-legs", database.workoutDao().draft()?.programmeDayId)
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

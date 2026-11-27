package com.petermathie.vibecheck

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.data.TrainingRepository
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.emptyEntryDraft
import com.petermathie.vibecheck.ui.emptySet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CataloguePersistenceTest {
    private lateinit var db: VibeDatabase
    @get:Rule val lifecycle = ComposeRoomLifecycleRule { if (::db.isInitialized) db else null }

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        DatabaseSeeder(context, db).seedIfNeeded()
    }

    private fun repository() = TrainingRepository(
        db, db.programmeDao(), db.workoutDao(), db.trackerDao(), ApplicationProvider.getApplicationContext(),
    )

    @Test fun uniformEditsPreserveOriginalsAndNewVariantCopiesSelectedIndependentDefaults() = runBlocking {
        val dao = db.editorDao()
        val vm = lifecycle.own(EditorViewModel(db))
        val original = requireNotNull(dao.exerciseById("core:handstand"))
        val variant = dao.variations().first().first { it.exerciseId == original.id }
        val variantOriginal = requireNotNull(dao.variationOriginalById(variant.id))
        dao.exercise(original.copy(id = "custom", canonicalName = "Custom", isCustom = true))
        for (id in listOf(original.id, "custom")) {
            vm.renameExerciseItem(id, name = "Edited $id").join()
            vm.saveExerciseItemMuscles(id, muscleRoles = emptyMap()).join()
            assertEquals("Edited $id", dao.effectiveExerciseById(id)!!.canonicalName)
            assertTrue(dao.muscleMappings(id).isEmpty())
        }
        assertEquals(original, dao.exerciseById(original.id))
        assertEquals("Custom", dao.exerciseById("custom")!!.canonicalName)
        vm.renameExerciseItem(original.id, variant.id, "Edited variant").join()
        vm.saveExerciseItemMuscles(original.id, variant.id, mapOf("LATS" to "PRIMARY")).join()
        vm.saveExerciseItemConfig(original.id, variant.id, "measurementMetric=DISTANCE;reps=false;resistanceMetric=BODYWEIGHT").join()
        var defaults: com.petermathie.vibecheck.ui.ExerciseVariantDefaults? = null
        vm.prepareExerciseVariant(original.id, variant.id, onReady = { defaults = it }).join()
        vm.saveExerciseItemMuscles(original.id, variant.id, emptyMap()).join()
        var created: String? = null
        vm.createExerciseVariant(original.id, variant.id, "Sibling", defaults, onCreated = { created = it }).join()
        val sibling = requireNotNull(dao.variationById(requireNotNull(created)))
        assertEquals(original.id, sibling.exerciseId)
        assertTrue(sibling.inputConfig.contains("measurementMetric=DISTANCE"))
        assertEquals(listOf("LATS"), dao.variationMuscleMappings(sibling.id).map { it.muscleId })
        assertTrue(dao.variationMuscleMappings(variant.id).isEmpty())
        assertTrue(dao.referenceVideos().first().none { it.variationId == sibling.id })
        assertEquals(variantOriginal.copy(inputConfig = dao.variationOriginalById(variant.id)!!.inputConfig), dao.variationOriginalById(variant.id))
        assertEquals(listOf(original.id), db.catalogueDao().searchExercises("Edited variant").first().map { it.id })
        val before = dao.variations().first()
        var failed = false
        vm.createExerciseVariant(original.id, variant.id, "", defaults, onCreated = { fail() }, onFailure = { failed = true }).join()
        assertTrue(failed)
        assertEquals(before, dao.variations().first())
    }

    @Test fun setSnapshotsFreezeVariantMusclesAndExplicitEmptyDoesNotFallBack() = runBlocking {
        val dao = db.editorDao()
        val vm = lifecycle.own(EditorViewModel(db))
        val workoutId = repository().startWorkout("demo-day-push")
        val row = dao.workoutExercisesForWorkout(workoutId).first()
        val variant = dao.variations().first().first { it.exerciseId == row.actualExerciseId }
        vm.saveExerciseItemMuscles(row.actualExerciseId, variant.id, mapOf("CHEST" to "PRIMARY")).join()
        val set = emptySet(row.id, 1).copy(variationId = variant.id, reps = 5.0)
        dao.saveSetWithSnapshots(set, emptyList(), consumeDraft = false)
        val frozen = dao.setMuscles(set.id)
        vm.saveExerciseItemMuscles(row.actualExerciseId, variant.id, emptyMap()).join()
        dao.saveSetWithSnapshots(dao.setById(set.id)!!.copy(rpe = 7.0), emptyList(), consumeDraft = false)
        assertEquals(frozen, dao.setMuscles(set.id))
        dao.saveSetWithSnapshots(set.copy(id = "explicit-empty", ordinal = 2), emptyList(), consumeDraft = false)
        assertTrue(dao.setById("explicit-empty")!!.musclesSnapshotPresent)
        assertTrue(dao.setMuscles("explicit-empty").isEmpty())
        val legacy = set.copy(id = "legacy", ordinal = 3, musclesSnapshotPresent = false)
        dao.set(legacy)
        val expectedLegacy = dao.muscleMappings(row.actualExerciseId).map { it.muscleId }.toSet()
        assertEquals(expectedLegacy + "CHEST", db.workoutDao().affectedMuscleIds(workoutId).toSet())
        dao.saveSetWithSnapshots(dao.setById(set.id)!!.copy(variationId = null), emptyList(), false)
        assertEquals(expectedLegacy, dao.setMuscles(set.id).map { it.muscleId }.toSet())
        dao.saveSetWithSnapshots(dao.setById(set.id)!!.copy(variationId = variant.id), emptyList(), false)
        assertTrue(dao.setById(set.id)!!.musclesSnapshotPresent)
        assertTrue(dao.setMuscles(set.id).isEmpty())
        dao.deleteSet(set.id)
        dao.deleteSet(legacy.id)
        assertTrue(db.workoutDao().affectedMuscleIds(workoutId).isEmpty())
    }

    @Test fun distanceQualificationIgnoresInactiveResultsAndRetainsIndependentRawDrafts() = runBlocking {
        val dao = db.editorDao()
        val repository = repository()
        val workoutId = repository.startWorkout("demo-day-push")
        val row = dao.workoutExercisesForWorkout(workoutId).first()
        val config = "measurementMetric=DISTANCE;reps=false;resistanceMetric=BODYWEIGHT"
        val set = emptySet(row.id, 1).copy(reps = 9.0, romValue = 42.125, romUnit = "cm", distanceValue = 0.0, distanceUnit = "cm")
        val draft = emptyEntryDraft(row.id, 1).copy(
            setId = set.id, distanceValue = "18.7500", romValue = ".", performance = "invalid inactive",
        )
        dao.saveSetWithSnapshots(set, emptyList(), true, config, draft)
        assertEquals(0, db.workoutDao().qualifyingSetCount(workoutId))
        dao.saveSetWithSnapshots(set.copy(distanceValue = 18.75), emptyList(), true, config, draft)
        assertEquals(draft, dao.entryDrafts(row.id).single())
        assertEquals(1, db.workoutDao().qualifyingSetCount(workoutId))
        assertNotNull(repository.finishWorkout(workoutId))
        assertEquals(draft, dao.entryDrafts(row.id).single())
        assertTrue(db.workoutDao().observeCompletedMuscleSets().first().none { it.setId == set.id })
        assertEquals(42.125, dao.setById(set.id)!!.romValue!!, 0.0)
    }

    @Test fun historyWritesReadFreshFieldsAndFlushReportsFailuresBeforeExit() = runBlocking {
        val dao = db.editorDao()
        val vm = lifecycle.own(EditorViewModel(db))
        val zone = ZoneId.systemDefault()
        val end = LocalDate.of(2026, 11, 27).atTime(12, 30, 34, 567_000_000).atZone(zone).toInstant().toEpochMilli()
        val history = WorkoutEntity("history", null, "Saved day", "STRENGTH", "FINISHED", end - 60_000, end, "Original", null, false, "Frozen programme")
        dao.workout(history)
        val otherHistory = history.copy(id = "other-history")
        dao.workout(otherHistory)
        val definition = dao.exercises().first().first()
        dao.workoutExercise(WorkoutExerciseEntity("history-exercise", history.id, definition.id, definition.id, 0, "", 120, null))
        val set = emptySet("history-exercise", 1).copy(loggedAt = end + 100_000)
        dao.set(set)
        vm.save(history.copy(name = "Fresh title", notes = "Fresh notes"))
        vm.changeHistoryWorkoutDate(history.id, LocalDate.of(2026, 12, 1)).join()
        val changed = dao.workoutById(history.id)!!
        assertEquals("Fresh title", changed.name)
        assertEquals("Fresh notes", changed.notes)
        assertEquals(history.programmeNameSnapshot, changed.programmeNameSnapshot)
        assertEquals(set.loggedAt + changed.finishedAt!! - end, dao.setById(set.id)!!.loggedAt)
        val logged = dao.setById(set.id)!!.loggedAt
        vm.changeHistoryWorkoutTime(history.id, false, LocalTime.of(12, 45)).join()
        assertEquals(logged, dao.setById(set.id)!!.loggedAt)
        val precise = Instant.ofEpochMilli(dao.workoutById(history.id)!!.finishedAt!!).atZone(zone)
        assertEquals(34, precise.second)
        assertEquals(567_000_000, precise.nano)
        val beforeRejectedTiming = dao.workoutById(history.id)!!
        var timingFailures = 0
        vm.changeHistoryWorkoutDate(
            history.id, LocalDate.of(1960, 1, 1),
            onSaved = { fail("Negative date must be rejected") },
            onFailure = { assertTrue(it.isNotBlank()); timingFailures++ },
        ).join()
        vm.changeHistoryWorkoutTime(
            history.id, true, LocalTime.of(13, 0),
            onSaved = { fail("Start after finish must be rejected") },
            onFailure = { assertTrue(it.isNotBlank()); timingFailures++ },
        ).join()
        vm.changeHistoryWorkoutTime(
            history.id, false, LocalTime.of(12, 0),
            onSaved = { fail("Finish before start must be rejected") },
            onFailure = { assertTrue(it.isNotBlank()); timingFailures++ },
        ).join()
        assertEquals(3, timingFailures)
        assertEquals(beforeRejectedTiming, dao.workoutById(history.id))
        assertEquals(logged, dao.setById(set.id)!!.loggedAt)
        var otherCanExit = false
        vm.flushWorkoutWrites(
            { otherCanExit = true }, { fail("Another workout's rejected timing must not block exit: $it") },
            workoutId = otherHistory.id,
        ).join()
        assertTrue(otherCanExit)
        vm.cancelHistoryWorkoutDate(history.id).join()
        vm.flushWorkoutWrites(
            { fail("Date cancellation must not clear rejected clocks") }, { },
            workoutId = history.id,
        ).join()
        vm.cancelHistoryWorkoutTime(history.id, true).join()
        vm.flushWorkoutWrites(
            { fail("Start cancellation must not clear rejected finish") }, { },
            workoutId = history.id,
        ).join()
        vm.cancelHistoryWorkoutTime(history.id, false).join()
        var cancelledCanExit = false
        vm.flushWorkoutWrites(
            { cancelledCanExit = true }, { fail("Cancelled timing must allow unchanged exit: $it") },
            workoutId = history.id,
        ).join()
        assertTrue(cancelledCanExit)
        assertEquals(beforeRejectedTiming, dao.workoutById(history.id))
        assertEquals(logged, dao.setById(set.id)!!.loggedAt)
        val beforeFailedSave = dao.workoutById(history.id)!!
        db.openHelper.writableDatabase.execSQL(
            "CREATE TEMP TRIGGER reject_history_autosave BEFORE UPDATE OF notes ON workouts " +
                "WHEN NEW.id='history' AND NEW.notes='Retry these notes' " +
                "BEGIN SELECT RAISE(ABORT,'Synthetic autosave failure'); END",
        )
        vm.save(beforeFailedSave.copy(notes = "Retry these notes")).join()
        assertEquals(beforeFailedSave, dao.workoutById(history.id))
        val beforeFailedSet = dao.setById(set.id)!!
        val retryDraft = emptyEntryDraft("history-exercise", 1).copy(setId = set.id, performance = "Retry raw")
        db.openHelper.writableDatabase.execSQL(
            "CREATE TEMP TRIGGER reject_history_set_autosave BEFORE UPDATE OF rpe ON workout_sets " +
                "WHEN NEW.id='${set.id}' AND NEW.rpe=9 " +
                "BEGIN SELECT RAISE(ABORT,'Synthetic set autosave failure'); END",
        )
        db.openHelper.writableDatabase.execSQL(
            "CREATE TEMP TRIGGER reject_history_draft_autosave BEFORE INSERT ON workout_entry_drafts " +
                "WHEN NEW.workoutExerciseId='history-exercise' AND NEW.performance='Retry raw' " +
                "BEGIN SELECT RAISE(ABORT,'Synthetic draft autosave failure'); END",
        )
        vm.saveSet(beforeFailedSet.copy(rpe = 9.0), emptyList()).join()
        vm.saveEntryDraft(retryDraft).join()
        assertEquals(beforeFailedSet, dao.setById(set.id))
        assertNull(dao.entryDraft("history-exercise"))
        vm.changeHistoryWorkoutDate(history.id, LocalDate.of(1960, 1, 1)).join()
        vm.changeHistoryWorkoutTime(history.id, true, LocalTime.of(13, 0)).join()
        vm.cancelHistoryWorkoutDate(history.id).join()
        vm.cancelHistoryWorkoutTime(history.id, true).join()
        vm.save(otherHistory.copy(notes = "Unrelated workout saved")).join()
        otherCanExit = false
        vm.flushWorkoutWrites(
            { otherCanExit = true }, { fail("Another workout's autosaves must not block exit: $it") },
            workoutId = otherHistory.id,
        ).join()
        assertTrue(otherCanExit)
        val legacyFlush = vm.flushWorkoutWrites({ fail("Unscoped legacy flush must still report unresolved writes") }) { }
        legacyFlush.join()
        var failures = 0
        repeat(2) {
            vm.flushWorkoutWrites(
                { fail("Failed autosave must keep blocking exit") }, { failures++ }, workoutId = history.id,
            ).join()
        }
        assertEquals(2, failures)
        vm.changeHistoryWorkoutTime(history.id, true, LocalTime.of(12, 28)).join()
        val afterUnrelatedWrite = dao.workoutById(history.id)!!
        assertEquals(beforeFailedSave.notes, afterUnrelatedWrite.notes)
        vm.flushWorkoutWrites(
            { fail("Unrelated success must not clear the failed autosave") }, { failures++ }, workoutId = history.id,
        ).join()
        assertEquals(3, failures)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_history_autosave")
        vm.save(afterUnrelatedWrite.copy(notes = "Retry these notes")).join()
        assertEquals(afterUnrelatedWrite.copy(notes = "Retry these notes"), dao.workoutById(history.id))
        vm.flushWorkoutWrites(
            { fail("Timing cancellation and notes recovery must not clear failed set/draft autosaves") },
            { failures++ }, workoutId = history.id,
        ).join()
        assertEquals(4, failures)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_history_set_autosave")
        vm.saveSet(dao.setById(set.id)!!.copy(rpe = 9.0), emptyList()).join()
        assertEquals(9.0, dao.setById(set.id)!!.rpe!!, 0.0)
        vm.flushWorkoutWrites(
            { fail("Set recovery must not clear the failed draft autosave") }, { failures++ }, workoutId = history.id,
        ).join()
        assertEquals(5, failures)
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_history_draft_autosave")
        vm.submitEntryDraft(
            dao.setById(set.id)!!, emptyList(), entryDraft = retryDraft, onSaved = {},
        ).join()
        assertEquals(retryDraft, dao.entryDraft("history-exercise"))
        var recovered = false
        vm.flushWorkoutWrites(
            { recovered = true }, { fail("Matching successful retry must resolve the failure: $it") },
            workoutId = history.id,
        ).join()
        assertTrue(recovered)
        val beforeOwnerLookupFailure = dao.setById(set.id)!!
        val ownerRetrySet = beforeOwnerLookupFailure.copy(rpe = 8.0)
        val ownerRetryDraft = retryDraft.copy(rpe = "8.000")
        db.openHelper.writableDatabase.execSQL(
            "CREATE TEMP VIEW workout_exercises AS SELECT 1 AS owner_lookup_fault",
        )
        try {
            vm.saveSet(ownerRetrySet, emptyList()).join()
            vm.saveEntryDraft(ownerRetryDraft).join()
            assertEquals(beforeOwnerLookupFailure, dao.setById(set.id))
            assertEquals(retryDraft, dao.entryDraft("history-exercise"))
            var unknownOwnerFailures = 0
            for (workoutId in listOf(history.id, otherHistory.id)) {
                vm.flushWorkoutWrites(
                    { fail("Unresolved ownership must conservatively block scoped exit") },
                    { assertTrue(it.contains("no such column: id")); unknownOwnerFailures++ },
                    workoutId = workoutId,
                ).join()
            }
            assertEquals(2, unknownOwnerFailures)
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP VIEW temp.workout_exercises")
        }
        vm.saveSet(ownerRetrySet, emptyList()).join()
        assertEquals(8.0, dao.setById(set.id)!!.rpe!!, 0.0)
        var draftOwnerStillBlocked = false
        vm.flushWorkoutWrites(
            { fail("Set recovery must not clear an unknown-owner draft failure") },
            { draftOwnerStillBlocked = true }, workoutId = history.id,
        ).join()
        assertTrue(draftOwnerStillBlocked)
        vm.saveEntryDraft(ownerRetryDraft).join()
        assertEquals(ownerRetryDraft, dao.entryDraft("history-exercise"))
        for (workoutId in listOf(history.id, otherHistory.id)) {
            var ownerRecovered = false
            vm.flushWorkoutWrites(
                { ownerRecovered = true }, { fail("Matching ownership/write recovery must unblock exit: $it") },
                workoutId = workoutId,
            ).join()
            assertTrue(ownerRecovered)
        }
        vm.discardHistoryWorkout(history.id).join()
        assertEquals("DISCARDED", dao.workoutById(history.id)!!.status)
        assertEquals("Retry these notes", dao.workoutById(history.id)!!.notes)
    }
}

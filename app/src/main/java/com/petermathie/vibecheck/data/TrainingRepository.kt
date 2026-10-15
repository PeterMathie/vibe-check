package com.petermathie.vibecheck.data

import android.content.Context
import androidx.room.withTransaction
import com.petermathie.vibecheck.data.local.ProgrammeDao
import com.petermathie.vibecheck.data.local.TrackerDao
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.local.WorkoutDao
import com.petermathie.vibecheck.data.local.CompletedMuscleSetRow
import com.petermathie.vibecheck.data.local.WorkoutEntity
import com.petermathie.vibecheck.data.local.WorkoutExerciseEntity
import com.petermathie.vibecheck.data.local.WorkoutSetEntity
import com.petermathie.vibecheck.domain.model.ActiveWorkout
import com.petermathie.vibecheck.domain.model.ActivityDay
import com.petermathie.vibecheck.domain.model.MuscleRecency
import com.petermathie.vibecheck.domain.model.HistoryActivity
import com.petermathie.vibecheck.domain.model.HistoryDayDetail
import com.petermathie.vibecheck.domain.model.MuscleRole
import com.petermathie.vibecheck.domain.model.ProgrammeDaySummary
import com.petermathie.vibecheck.domain.model.SetDraft
import com.petermathie.vibecheck.domain.model.TrackingType
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.domain.model.WorkoutExerciseLog
import com.petermathie.vibecheck.domain.programme.prescriptionSummary
import com.petermathie.vibecheck.domain.model.WorkoutSetLog
import com.petermathie.vibecheck.domain.model.WorkoutStatus
import com.petermathie.vibecheck.domain.recency.RecencyCalculator
import com.petermathie.vibecheck.data.seed.DemoProgressPhotos
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

data class WorkoutCompletion(
    val mode: TrainingMode,
    val affectedMuscleIds: List<String>,
)

private fun List<CompletedMuscleSetRow>.recencyAt(
    mode: TrainingMode,
    atMillis: Long,
): List<MuscleRecency> =
    filter { it.mode == mode.name && it.finishedAt <= atMillis }
        .groupBy { it.muscleId }
        .map { (muscleId, muscleRows) ->
            val last = muscleRows.maxOfOrNull { it.finishedAt }
            val recentWindowStart = atMillis - 7 * 86_400_000L
            MuscleRecency(
                muscleId = muscleId,
                lastTrainedAt = last,
                band = RecencyCalculator.band(last, atMillis),
                contributingExerciseNames = muscleRows.filter { it.finishedAt == last }
                    .map { it.exerciseName }
                    .distinct(),
                setEquivalents = muscleRows.filter { it.finishedAt >= recentWindowStart }
                    .sumOf { MuscleRole.valueOf(it.role).setEquivalent },
            )
        }

@Singleton
class TrainingRepository @Inject constructor(
    private val database: VibeDatabase,
    private val programmeDao: ProgrammeDao,
    private val workoutDao: WorkoutDao,
    private val trackerDao: TrackerDao,
    @ApplicationContext private val context: Context,
) {
    fun observeProgrammeDays(): Flow<List<ProgrammeDaySummary>> = programmeDao.observeDays().map { rows ->
        rows.map { ProgrammeDaySummary(it.id, it.name, TrainingMode.valueOf(it.mode), it.exerciseCount) }
    }

    fun observeDraft(): Flow<ActiveWorkout?> =
        combine(
            workoutDao.observeDraft(),
            workoutDao.observeSetCount(),
            workoutDao.observeWorkoutExerciseChanges(),
        ) { draft, _, _ -> draft }
            .flatMapLatest { draft -> flow { emit(draft?.let { loadWorkout(it) }) } }

    suspend fun startWorkout(dayId: String, replaceExisting: Boolean = false): String = database.withTransaction {
        val existing = workoutDao.draft()
        if (existing?.programmeDayId == dayId) return@withTransaction existing.id
        if (existing != null && !replaceExisting) return@withTransaction existing.id
        if (existing != null) {
            database.editorDao().deleteEntryDraftsForWorkout(existing.id)
            workoutDao.updateWorkout(existing.copy(status = WorkoutStatus.DISCARDED.name))
        }
        requireNotNull(programmeDao.day(dayId))
        val mode = requireNotNull(programmeDao.modeForDay(dayId))
        val workoutName = requireNotNull(programmeDao.workoutNameForDay(dayId))
        val exercises = programmeDao.exercisesForDay(dayId)
        val workoutId = UUID.randomUUID().toString()
        workoutDao.insertWorkout(
            WorkoutEntity(
                id = workoutId,
                programmeDayId = dayId,
                name = workoutName,
                mode = mode,
                status = WorkoutStatus.DRAFT.name,
                startedAt = System.currentTimeMillis(),
                finishedAt = null,
                notes = "",
                bodyweightKg = null,
                isDemo = false,
            ),
        )
        exercises.forEach { row ->
            val snapshot=WorkoutExerciseEntity(
                id = UUID.randomUUID().toString(),
                workoutId = workoutId,
                plannedExerciseId = row.exerciseId,
                actualExerciseId = row.exerciseId,
                position = row.position,
                notes = sanitizeExerciseNotes(row.notes),
                restSeconds = row.restSeconds,
                supersetGroup = row.supersetGroup,
                exerciseName = row.canonicalName,
                trackingType = row.trackingType,
                targets = prescriptionSummary(
                    row.targetSets,
                    row.targetRepsMin,
                    row.targetRepsMax,
                    row.targetHoldSeconds,
                    row.targetRpe,
                ),
                inputConfig = row.inputConfig,
                targetSets = row.targetSets,
                targetRepsMin = row.targetRepsMin,
                targetRepsMax = row.targetRepsMax,
                targetHoldSeconds = row.targetHoldSeconds,
                targetRpe = row.targetRpe,
            )
            workoutDao.insertWorkoutExercises(listOf(snapshot))
            database.editorDao().workoutMuscles(database.editorDao().muscleMappings(row.exerciseId).map { com.petermathie.vibecheck.data.local.WorkoutMuscleEntity(snapshot.id,it.muscleId,it.role) })
        }
        workoutId
    }

    suspend fun addSet(workoutExerciseId: String, draft: SetDraft) {
        val now = System.currentTimeMillis()
        workoutDao.insertSet(
            WorkoutSetEntity(
                id = UUID.randomUUID().toString(),
                workoutExerciseId = workoutExerciseId,
                ordinal = workoutDao.nextSetOrdinal(workoutExerciseId),
                setType = draft.type.name,
                result = draft.result.name,
                variationId = null,
                weightKg = draft.weightKg,
                reps = draft.reps,
                holdMillis = draft.holdMillis,
                leftReps = null,
                rightReps = null,
                leftHoldMillis = null,
                rightHoldMillis = null,
                addedWeightKg = null,
                assistanceKg = null,
                rpe = draft.rpe,
                romValue = null,
                romUnit = null,
                notes = draft.notes,
                loggedAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun updateExerciseNotes(workoutExerciseId: String, notes: String) =
        workoutDao.updateExerciseNotes(workoutExerciseId, notes)

    suspend fun finishWorkout(
        workoutId: String,
        preserveEntryDrafts: Boolean = false,
    ): WorkoutCompletion? = database.withTransaction {
        if (workoutDao.qualifyingSetCount(workoutId) == 0) return@withTransaction null
        val mode = workoutDao.workoutMode(workoutId)?.let(TrainingMode::valueOf)
            ?: error("Workout $workoutId does not exist")
        val affectedMuscleIds = workoutDao.affectedMuscleIds(workoutId)
        if (workoutDao.finish(workoutId, System.currentTimeMillis()) != 1) return@withTransaction null
        if (!preserveEntryDrafts) database.editorDao().deleteEntryDraftsForWorkout(workoutId)
        WorkoutCompletion(mode, affectedMuscleIds)
    }

    fun observeMuscleRecency(mode: TrainingMode, atMillis: Long = System.currentTimeMillis()): Flow<List<MuscleRecency>> =
        workoutDao.observeCompletedMuscleSets().map { rows -> rows.recencyAt(mode, atMillis) }

    fun observeMuscleRecencyRange(
        mode: TrainingMode,
        firstEpochDay: Long,
        lastEpochDay: Long,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Flow<Map<Long, List<MuscleRecency>>> =
        workoutDao.observeCompletedMuscleSets().map { rows ->
            (firstEpochDay..lastEpochDay).associateWith { epochDay ->
                val endOfDay = LocalDate.ofEpochDay(epochDay).plusDays(1)
                    .atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
                rows.recencyAt(mode, minOf(endOfDay, now))
            }
        }

    fun observeActivityHeatmap(zoneId: ZoneId = ZoneId.systemDefault()): Flow<List<ActivityDay>> =
        combine(workoutDao.observeFinishedWorkoutTimes(), trackerDao.observeActivityValues()) { workouts, trackerRows ->
            val counts = mutableMapOf<Long, Int>()
            workouts.forEach { row ->
                val epochDay = Instant.ofEpochMilli(row.epochMillis).atZone(zoneId).toLocalDate().toEpochDay()
                counts[epochDay] = counts.getOrDefault(epochDay, 0) + 1
            }
            trackerRows.groupBy { it.epochDay to it.trackerId }.filterValues { rows -> rows.all { it.targetMet } }.keys.forEach { (day, _) ->
                counts[day] = counts.getOrDefault(day, 0) + 1
            }
            counts.map { ActivityDay(it.key, it.value) }.sortedBy { it.epochDay }
        }

    fun observeHistoryDay(epochDay: Long, zoneId: ZoneId = ZoneId.systemDefault()): Flow<HistoryDayDetail> =
        combine(workoutDao.observeFinishedWorkouts(), trackerDao.observeHistoryValues()) { workouts, values ->
            val workoutActivities = workouts.filter { row ->
                row.finishedAt?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate().toEpochDay() == epochDay } == true
            }.map { row ->
                HistoryActivity(
                    title = row.name,
                    detail = if (row.mode == TrainingMode.STRETCHING.name) "Stretching session" else "Strength workout",
                    notes = row.notes,
                )
            }
            val trackerActivities = values.filter { it.epochDay == epochDay }.groupBy { it.trackerName }.map { (name, rows) ->
                val detail = rows.joinToString(" · ") { value ->
                    when {
                        value.numericValue != null -> "${value.fieldName}: ${value.numericValue}${value.unit?.let { " $it" } ?: ""}"
                        value.booleanValue != null -> "${value.fieldName}: ${if (value.booleanValue) "done" else "not done"}"
                        else -> "${value.fieldName}: ${value.textValue.orEmpty()}"
                    }
                }
                HistoryActivity(name, detail, rows.map { it.notes }.firstOrNull { it.isNotBlank() }.orEmpty())
            }
            HistoryDayDetail(epochDay, workoutActivities + trackerActivities)
        }

    suspend fun removeDemoData(): DemoRemovalSummary {
        val photos = DemoProgressPhotos.deleteOwned(context)
        return database.withTransaction {
            DemoRemovalSummary(
                workouts = workoutDao.deleteDemoWorkouts(),
                programmes = programmeDao.deleteDemoProgrammes(),
                trackers = trackerDao.deleteDemoTrackers(),
                measurements = database.editorDao().deleteDemoMeasurements(),
                photos = photos,
            )
        }
    }

    private suspend fun loadWorkout(row: WorkoutEntity): ActiveWorkout {
        val exercises = workoutDao.exercises(row.id).map { exercise ->
            val sets = workoutDao.sets(exercise.workoutExerciseId).map { set ->
                WorkoutSetLog(
                    id = set.id,
                    ordinal = set.ordinal,
                    weightKg = set.weightKg,
                    reps = set.reps,
                    holdMillis = set.holdMillis,
                    rpe = set.rpe,
                    notes = set.notes,
                    type = com.petermathie.vibecheck.domain.model.SetType.valueOf(set.setType),
                    result = com.petermathie.vibecheck.domain.model.SetResult.valueOf(set.result),
                )
            }

            WorkoutExerciseLog(
                id = exercise.workoutExerciseId,
                plannedExerciseId = exercise.plannedExerciseId,
                actualExerciseId = exercise.actualExerciseId,
                exerciseName = exercise.canonicalName,
                trackingType = TrackingType.valueOf(exercise.trackingType),
                notes = sanitizeExerciseNotes(exercise.notes),
                restSeconds = exercise.restSeconds,
                sets = sets,
            )
        }
        return ActiveWorkout(row.id, row.name, TrainingMode.valueOf(row.mode), row.startedAt, row.notes, exercises)
    }
}

data class DemoRemovalSummary(
    val workouts: Int,
    val programmes: Int,
    val trackers: Int,
    val measurements: Int,
    val photos: Int,
)

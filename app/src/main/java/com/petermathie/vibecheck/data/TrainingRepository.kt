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
import com.petermathie.vibecheck.domain.model.MuscleContribution
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
import com.petermathie.vibecheck.domain.workout.quantitativeInput
import com.petermathie.vibecheck.domain.workout.timingInputs
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.MeasurementMetric
import com.petermathie.vibecheck.domain.programme.ResistanceMetric
import com.petermathie.vibecheck.domain.workout.clearedRecordedMeasurement
import com.petermathie.vibecheck.domain.workout.configuredSetCount
import com.petermathie.vibecheck.domain.workout.loggingMetricsError
import com.petermathie.vibecheck.domain.workout.resolveSetInputConfig
import com.petermathie.vibecheck.domain.workout.restoredWorkoutVariationIds
import com.petermathie.vibecheck.domain.workout.VariantDraftMetadata
import com.petermathie.vibecheck.data.local.WorkoutEntryDraftEntity
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
import org.json.JSONArray
import org.json.JSONObject

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
            val recentSets = muscleRows.filter { it.finishedAt >= recentWindowStart }.distinctBy { it.setId }
            val ratings = recentSets.mapNotNull { it.rpe }
            MuscleRecency(
                muscleId = muscleId,
                lastTrainedAt = last,
                band = RecencyCalculator.band(last, atMillis),
                contributingExerciseNames = muscleRows.filter { it.finishedAt == last }
                    .map { it.exerciseName }
                    .distinct(),
                setEquivalents = muscleRows.filter { it.finishedAt >= recentWindowStart }
                    .sumOf { MuscleRole.valueOf(it.role).setEquivalent },
                weeklyWorkingSets = recentSets.size,
                weeklyAverageRpe = ratings.takeIf { it.isNotEmpty() }?.average(),
                weeklyContributions = recentSets.groupBy { it.exerciseName to MuscleRole.valueOf(it.role) }
                    .map { (exercise, sets) ->
                        MuscleContribution(exercise.first, exercise.second, sets.size)
                    },
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
        val day = requireNotNull(programmeDao.day(dayId))
        val mode = requireNotNull(programmeDao.modeForDay(dayId))
        val exercises = programmeDao.exercisesForDay(dayId)
        val workoutId = UUID.randomUUID().toString()
        workoutDao.insertWorkout(
            WorkoutEntity(
                id = workoutId,
                programmeDayId = dayId,
                name = day.name,
                mode = mode,
                status = WorkoutStatus.DRAFT.name,
                startedAt = System.currentTimeMillis(),
                finishedAt = null,
                notes = "",
                bodyweightKg = null,
                isDemo = false,
                programmeNameSnapshot = requireNotNull(database.editorDao().programmeById(day.programmeId)).name,
            ),
        )
        exercises.forEach { row ->
            val snapshot=WorkoutExerciseEntity(
                id = UUID.randomUUID().toString(),
                workoutId = workoutId,
                plannedExerciseId = row.exerciseId,
                actualExerciseId = row.exerciseId,
                position = row.position,
                notes = row.notes,
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

    suspend fun startProgrammeWorkout(
        dayId: String,
        activeWorkoutId: String?,
        finishCurrent: Boolean,
    ): WorkoutCompletion? = database.withTransaction {
        val existing = workoutDao.draft()
        check(existing?.id == activeWorkoutId) {
            "The active workout changed. Choose the workout again."
        }
        if (existing != null && existing.programmeDayId == dayId) return@withTransaction null
        val completion = if (existing != null && finishCurrent) {
            validateWorkoutInputs(existing.id)
            requireNotNull(finishWorkout(existing.id)) {
                "Log at least one completed working set before finishing."
            }
        } else null
        // A failed new snapshot must also roll back finishing or discarding the current workout.
        startWorkout(dayId, replaceExisting = existing != null && !finishCurrent)
        completion
    }

    private suspend fun validateWorkoutInputs(workoutId: String) {
        val dao = database.editorDao()
        val variations = dao.variations().first()
        dao.workoutExercisesForWorkout(workoutId).forEach { row ->
            val drafts = dao.entryDrafts(row.id)
            val sets = workoutDao.sets(row.id)
            val exerciseVariations = variations.filter { it.exerciseId == row.actualExerciseId }
            val restoredVariations = restoredWorkoutVariationIds(sets, drafts, exerciseVariations)
            val trackingType = dao.exerciseById(row.actualExerciseId)?.trackingType.orEmpty()
            val count = maxOf(
                row.configuredSetCount(),
                sets.maxOfOrNull { it.ordinal } ?: 0,
                drafts.maxOfOrNull { it.ordinal } ?: 0,
            )
            var previousVariationId: String? = null
            for (ordinal in 1..count) {
                val set = sets.find { it.ordinal == ordinal }
                val entryDraft = drafts.find { it.ordinal == ordinal }
                val variationId = if (ordinal in restoredVariations) restoredVariations[ordinal]
                    else previousVariationId ?: exerciseVariations.firstOrNull()?.id
                val config = resolveSetInputConfig(
                    row, trackingType,
                    exerciseVariations.find { it.id == variationId },
                    set?.takeIf { it.variationId == variationId },
                    cached = entryDraft?.let(::draftMetadata), configOverride = null,
                )
                val error = config.loggingMetricsError()
                require(error == null) { "Current workout: $error" }
                entryDraft?.let { validateActiveDraft(it, config) }
                previousVariationId = variationId
            }
        }
    }

    private fun validateActiveDraft(
        draft: WorkoutEntryDraftEntity, config: ExerciseInputConfig,
    ) {
        val performance = if (config.loggingWeightUnit == null) listOf("", draft.performance)
            else draft.performance.split(Regex("\\s*[x×]\\s*"), limit = 2)
        val values = buildList {
            if (config.loggingResistance() == ResistanceMetric.KILOGRAMS) {
                add(if (config.addedWeight) draft.addedWeight else performance.getOrElse(0) { "" })
            }
            when (config.loggingMeasurement()) {
                MeasurementMetric.REPETITIONS -> add(performance.getOrElse(1) { "" })
                MeasurementMetric.RANGE_OF_MOTION -> add(draft.romValue)
                MeasurementMetric.DISTANCE -> add(draft.distanceValue)
                MeasurementMetric.HOLD -> if (!config.timeHeld && config.timeUnderTension) add(draft.timeUnderTension)
                    else addAll(timingInputs(draft.tutEntries).ifEmpty { listOf(draft.timeHeld) })
                MeasurementMetric.SKILL_HOLD -> {
                    addAll(timingInputs(draft.tutEntries).ifEmpty { listOf(draft.timeHeld) })
                    add(draft.timeUnderTension)
                }
                null -> Unit
            }
        }
        require(values.none { it.isNotEmpty() && !quantitativeInput(it).isValid } &&
            (draft.rpe.isEmpty() || quantitativeInput(draft.rpe, 10.0).isValid)) {
            "Complete or correct the current workout's highlighted numbers before finishing."
        }
    }

    private fun draftMetadata(draft: WorkoutEntryDraftEntity): VariantDraftMetadata? {
        val cache = JSONObject(draft.variantDraftCache.ifBlank { "{}" })
        val key = JSONArray().put(draft.setId).put(draft.variationId).toString()
        if (!cache.has(key)) return null
        val raw = cache.get(key)
        val values = if (raw is JSONObject) raw else JSONObject(raw as String)
        return object : VariantDraftMetadata {
            override val inputConfig = values.getString("inputConfig")
            override val variantName = values.getString("variantName")
            override val trackingType = values.getString("trackingType")
            override val rank = if (values.isNull("rank")) null else values.getInt("rank")
        }
    }

    suspend fun addSet(workoutExerciseId: String, draft: SetDraft) = database.withTransaction {
        val now = System.currentTimeMillis()
        database.editorDao().saveSetWithSnapshots(
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
                distanceValue = draft.distanceValue,
                distanceUnit = draft.distanceUnit,
            ),
            emptyList(), consumeDraft = false,
        )
    }

    suspend fun updateExerciseNotes(workoutExerciseId: String, notes: String) =
        workoutDao.updateExerciseNotes(workoutExerciseId, notes)

    suspend fun finishWorkout(workoutId: String): WorkoutCompletion? = database.withTransaction {
        val dao = database.editorDao()
        dao.workoutExercisesForWorkout(workoutId).forEach { row ->
            dao.entryDrafts(row.id).forEach draft@ { draft ->
                val recorded = dao.setById(draft.setId)
                val config = resolveSetInputConfig(
                    row, dao.exerciseById(row.actualExerciseId)?.trackingType.orEmpty(),
                    draft.variationId?.let { dao.variationById(it) },
                    recorded?.takeIf { it.variationId == draft.variationId },
                    cached = draftMetadata(draft), configOverride = null,
                )
                validateActiveDraft(draft, config)
                if (recorded == null) return@draft
                require(config.clearedRecordedMeasurement(draft, recorded) == null) {
                    "Save or correct the cleared measurement before finishing."
                }
            }
        }
        if (workoutDao.qualifyingSetCount(workoutId) == 0) return@withTransaction null
        val mode = workoutDao.workoutMode(workoutId)?.let(TrainingMode::valueOf)
            ?: error("Workout $workoutId does not exist")
        val affectedMuscleIds = workoutDao.affectedMuscleIds(workoutId)
        if (workoutDao.finish(workoutId, System.currentTimeMillis()) != 1) return@withTransaction null
        database.editorDao().deleteUnrecordedEntryDraftsForWorkout(workoutId)
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
                    distanceValue = set.distanceValue,
                    distanceUnit = set.distanceUnit,
                )
            }

            WorkoutExerciseLog(
                id = exercise.workoutExerciseId,
                plannedExerciseId = exercise.plannedExerciseId,
                actualExerciseId = exercise.actualExerciseId,
                exerciseName = exercise.canonicalName,
                trackingType = TrackingType.valueOf(exercise.trackingType),
                notes = exercise.notes,
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

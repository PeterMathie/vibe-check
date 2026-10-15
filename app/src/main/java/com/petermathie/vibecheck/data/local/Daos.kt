package com.petermathie.vibecheck.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class ProgrammeDayRow(
    val id: String,
    val name: String,
    val mode: String,
    val exerciseCount: Int,
)

data class ProgrammeExerciseRow(
    val id: String,
    val exerciseId: String,
    val canonicalName: String,
    val trackingType: String,
    val inputConfig: String,
    val targetSets: Int?,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetHoldSeconds: Int?,
    val targetRpe: Double?,
    val position: Int,
    val restSeconds: Int,
    val notes: String,
    val supersetGroup: String?,
)

data class WorkoutExerciseRow(
    val workoutExerciseId: String,
    val plannedExerciseId: String,
    val actualExerciseId: String,
    val canonicalName: String,
    val trackingType: String,
    val position: Int,
    val notes: String,
    val restSeconds: Int,
)

data class CompletedMuscleSetRow(
    val workoutId: String,
    val finishedAt: Long,
    val mode: String,
    val exerciseName: String,
    val muscleId: String,
    val role: String,
)

data class ActivityWorkoutRow(val epochMillis: Long)
data class ActivityTrackerRow(val epochDay: Long, val trackerId: String, val targetMet: Boolean)
data class HistoryTrackerRow(
    val epochDay: Long,
    val trackerName: String,
    val fieldName: String,
    val numericValue: Double?,
    val booleanValue: Boolean?,
    val textValue: String?,
    val unit: String?,
    val notes: String,
)

@Dao
interface CatalogueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMuscles(rows: List<MuscleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(rows: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAliases(rows: List<ExerciseAliasEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExerciseMuscles(rows: List<ExerciseMuscleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariations(rows: List<ExerciseVariationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBands(rows: List<BandEntity>)

    @Query("SELECT COUNT(*) FROM exercises WHERE isArchived = 0")
    fun observeExerciseCount(): Flow<Int>

    @Query(
        """
        SELECT DISTINCT e.* FROM exercises e
        LEFT JOIN exercise_aliases a ON a.exerciseId = e.id
        LEFT JOIN exercise_muscles em ON em.exerciseId = e.id
        LEFT JOIN muscles m ON m.id = em.muscleId
        WHERE e.isArchived = 0 AND (
            :query = '' OR
            lower(e.canonicalName) LIKE '%' || lower(:query) || '%' OR
            a.normalizedAlias LIKE '%' || lower(:query) || '%' OR
            lower(m.displayName) LIKE '%' || lower(:query) || '%'
        )
        ORDER BY e.canonicalName
        LIMIT 100
        """,
    )
    fun searchExercises(query: String): Flow<List<ExerciseEntity>>
}

@Dao
interface ProgrammeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgrammes(rows: List<ProgrammeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDays(rows: List<ProgrammeDayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgrammeExercises(rows: List<ProgrammeExerciseEntity>)

    @Query(
        """
        SELECT d.id, d.name, p.mode,
            (SELECT COUNT(*) FROM programme_exercises pe WHERE pe.programmeDayId = d.id) AS exerciseCount
        FROM programme_days d
        JOIN programmes p ON p.id = d.programmeId
        WHERE p.isArchived = 0
        ORDER BY p.name, d.position
        """,
    )
    fun observeDays(): Flow<List<ProgrammeDayRow>>

    @Query(
        """
        SELECT pe.id, pe.exerciseId, e.canonicalName, e.trackingType, e.inputConfig,
               pe.targetSets, pe.targetRepsMin, pe.targetRepsMax, pe.targetHoldSeconds, pe.targetRpe,
               pe.position, pe.restSeconds, pe.notes, pe.supersetGroup
        FROM programme_exercises pe
        JOIN exercises e ON e.id = pe.exerciseId
        WHERE pe.programmeDayId = :dayId
        ORDER BY pe.position
        """,
    )
    suspend fun exercisesForDay(dayId: String): List<ProgrammeExerciseRow>

    @Query("SELECT * FROM programme_days WHERE id = :dayId")
    suspend fun day(dayId: String): ProgrammeDayEntity?

    @Query("SELECT p.mode FROM programmes p JOIN programme_days d ON d.programmeId = p.id WHERE d.id = :dayId")
    suspend fun modeForDay(dayId: String): String?

    @Query(
        """
        SELECT CASE
            WHEN d.name IN ('Workout', 'Stretching') THEN p.name
            ELSE d.name
        END
        FROM programme_days d
        JOIN programmes p ON p.id = d.programmeId
        WHERE d.id = :dayId
        """,
    )
    suspend fun workoutNameForDay(dayId: String): String?

    @Query("DELETE FROM programmes WHERE isDemo = 1")
    suspend fun deleteDemoProgrammes(): Int
}

@Dao
interface WorkoutDao {
    @Query("SELECT COUNT(*) FROM workouts WHERE isDemo = 1")
    suspend fun demoCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(row: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkouts(rows: List<WorkoutEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercises(rows: List<WorkoutExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(row: WorkoutSetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(rows: List<WorkoutSetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetBands(rows: List<WorkoutSetBandEntity>)

    @Update
    suspend fun updateWorkout(row: WorkoutEntity)

    @Query("SELECT * FROM workouts WHERE status = 'DRAFT' ORDER BY startedAt DESC LIMIT 1")
    fun observeDraft(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE status = 'DRAFT' ORDER BY startedAt DESC LIMIT 1")
    suspend fun draft(): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    suspend fun workout(workoutId: String): WorkoutEntity?

    @Query(
        """
        SELECT we.id AS workoutExerciseId, we.plannedExerciseId, we.actualExerciseId,
               COALESCE(NULLIF(we.exerciseName,''),e.canonicalName) AS canonicalName,
               COALESCE(NULLIF(we.trackingType,''),e.trackingType) AS trackingType, we.position, we.notes, we.restSeconds
        FROM workout_exercises we
        JOIN exercises e ON e.id = we.actualExerciseId
        WHERE we.workoutId = :workoutId
        ORDER BY we.position
        """,
    )
    suspend fun exercises(workoutId: String): List<WorkoutExerciseRow>

    @Query("SELECT * FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId ORDER BY ordinal")
    suspend fun sets(workoutExerciseId: String): List<WorkoutSetEntity>

    @Query("SELECT COALESCE(MAX(ordinal), 0) + 1 FROM workout_sets WHERE workoutExerciseId = :workoutExerciseId")
    suspend fun nextSetOrdinal(workoutExerciseId: String): Int

    @Query("SELECT COUNT(*) FROM workout_sets")
    fun observeSetCount(): Flow<Int>

    /**
     * Observing this query invalidates the active-workout stream whenever an exercise
     * snapshot changes, including an autosaved exercise note.
     */
    @Query("SELECT COUNT(*) FROM workout_exercises")
    fun observeWorkoutExerciseChanges(): Flow<Int>

    @Query("UPDATE workout_exercises SET notes = :notes WHERE id = :workoutExerciseId")
    suspend fun updateExerciseNotes(workoutExerciseId: String, notes: String)

    @Query("SELECT * FROM workouts WHERE status = 'FINISHED' AND finishedAt IS NOT NULL ORDER BY finishedAt DESC")
    fun observeFinishedWorkouts(): Flow<List<WorkoutEntity>>

    @Query(
        "UPDATE workouts SET status = 'FINISHED', finishedAt = :finishedAt " +
            "WHERE id = :workoutId AND status = 'DRAFT'",
    )
    suspend fun finish(workoutId: String, finishedAt: Long): Int

    @Query("SELECT mode FROM workouts WHERE id = :workoutId")
    suspend fun workoutMode(workoutId: String): String?

    @Query(
        """
        SELECT COUNT(*) FROM workout_sets ws
        JOIN workout_exercises we ON we.id = ws.workoutExerciseId
        WHERE we.workoutId = :workoutId
          AND ws.setType = 'WORKING'
          AND ws.result = 'COMPLETED'
          AND (COALESCE(ws.reps, 0) > 0 OR COALESCE(ws.holdMillis, 0) > 0
            OR COALESCE(ws.leftReps, 0) > 0 OR COALESCE(ws.rightReps, 0) > 0
            OR COALESCE(ws.leftHoldMillis, 0) > 0 OR COALESCE(ws.rightHoldMillis, 0) > 0
            OR COALESCE(ws.romValue, 0) > 0)
        """,
    )
    suspend fun qualifyingSetCount(workoutId: String): Int

    @Query(
        """
        SELECT DISTINCT wm.muscleId FROM workout_muscles wm
        JOIN workout_exercises we ON we.id = wm.workoutExerciseId
        JOIN workout_sets ws ON ws.workoutExerciseId = we.id
        WHERE we.workoutId = :workoutId
          AND ws.setType = 'WORKING'
          AND ws.result = 'COMPLETED'
          AND (COALESCE(ws.reps, 0) > 0 OR COALESCE(ws.holdMillis, 0) > 0
            OR COALESCE(ws.leftReps, 0) > 0 OR COALESCE(ws.rightReps, 0) > 0
            OR COALESCE(ws.leftHoldMillis, 0) > 0 OR COALESCE(ws.rightHoldMillis, 0) > 0)
        ORDER BY wm.muscleId
        """,
    )
    suspend fun affectedMuscleIds(workoutId: String): List<String>

    @Query("DELETE FROM workouts WHERE isDemo = 1")
    suspend fun deleteDemoWorkouts(): Int

    @Query(
        """
        WITH mappings AS (
          SELECT workoutExerciseId,muscleId,role FROM workout_muscles
          UNION ALL
          SELECT we.id AS workoutExerciseId,em.muscleId,em.role FROM workout_exercises we
          JOIN exercise_muscles em ON em.exerciseId=we.actualExerciseId
          WHERE NOT EXISTS (SELECT 1 FROM workout_muscles wm WHERE wm.workoutExerciseId=we.id)
        )
        SELECT w.id AS workoutId, w.finishedAt AS finishedAt, w.mode AS mode,
               COALESCE(NULLIF(we.exerciseName,''),e.canonicalName) AS exerciseName,
               em.muscleId AS muscleId, em.role AS role
        FROM workouts w
        JOIN workout_exercises we ON we.workoutId = w.id
        JOIN workout_sets ws ON ws.workoutExerciseId = we.id
        JOIN exercises e ON e.id = we.actualExerciseId
        JOIN mappings em ON em.workoutExerciseId = we.id
        WHERE w.status = 'FINISHED' AND w.finishedAt IS NOT NULL
          AND ws.setType = 'WORKING'
          AND ws.result = 'COMPLETED'
          AND ws.romValue IS NULL
          AND (COALESCE(ws.reps, 0) > 0 OR COALESCE(ws.holdMillis, 0) > 0
            OR COALESCE(ws.leftReps, 0) > 0 OR COALESCE(ws.rightReps, 0) > 0
            OR COALESCE(ws.leftHoldMillis, 0) > 0 OR COALESCE(ws.rightHoldMillis, 0) > 0)
        ORDER BY w.finishedAt DESC
        """,
    )
    fun observeCompletedMuscleSets(): Flow<List<CompletedMuscleSetRow>>

    @Query("SELECT finishedAt AS epochMillis FROM workouts WHERE status = 'FINISHED' AND finishedAt IS NOT NULL")
    fun observeFinishedWorkoutTimes(): Flow<List<ActivityWorkoutRow>>
}

@Dao
interface TrackerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackers(rows: List<TrackerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFields(rows: List<TrackerFieldEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun persistValue(row: TrackerDailyValueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun persistOutcome(row: TrackerDayOutcomeEntity)

    @Query("DELETE FROM tracker_day_outcomes WHERE trackerId=:trackerId AND epochDay=:epochDay")
    suspend fun deleteOutcome(trackerId: String, epochDay: Long)

    @Query("DELETE FROM tracker_daily_values WHERE fieldId=:fieldId AND epochDay=:epochDay")
    suspend fun deleteValue(fieldId: String, epochDay: Long)

    @Query("SELECT * FROM tracker_fields WHERE trackerId=:trackerId AND isArchived=0")
    suspend fun activeFields(trackerId: String): List<TrackerFieldEntity>

    @Query("SELECT * FROM tracker_daily_values WHERE epochDay=:epochDay AND fieldId IN (SELECT id FROM tracker_fields WHERE trackerId=:trackerId)")
    suspend fun valuesForDay(trackerId: String, epochDay: Long): List<TrackerDailyValueEntity>

    @Transaction
    suspend fun upsertValue(row: TrackerDailyValueEntity) {
        persistValue(row)
        updateOutcome(activeFieldsForValue(row.fieldId), row.epochDay)
    }

    @Transaction
    suspend fun clearValue(fieldId: String, epochDay: Long) {
        val trackerId = activeFieldsForValue(fieldId)
        deleteValue(fieldId, epochDay)
        updateOutcome(trackerId, epochDay)
    }

    suspend fun updateOutcome(trackerId: String, epochDay: Long) {
        val fields = activeFields(trackerId)
        val values = valuesForDay(fields.firstOrNull()?.trackerId ?: return, epochDay)
        if (values.isEmpty()) {
            deleteOutcome(trackerId, epochDay)
            return
        }
        val targets = fields.filter { it.targetComparison != null }
        val targetMet = if (targets.isEmpty()) values.any { it.numericValue != null || it.booleanValue == true || it.textValue != null }
        else targets.all { field ->
            val value = values.find { it.fieldId == field.id }?.numericValue ?: return@all false
            when (field.targetComparison) {
                "AT_LEAST" -> value >= field.targetValue!!
                "AT_MOST" -> value <= field.targetValue!!
                "EXACTLY" -> value == field.targetValue!!
                "RANGE" -> value >= field.targetValue!! && value <= field.targetMaxValue!!
                else -> false
            }
        }
        persistOutcome(TrackerDayOutcomeEntity(trackerId, epochDay, targetMet))
    }

    @Query("SELECT trackerId FROM tracker_fields WHERE id=:fieldId")
    suspend fun activeFieldsForValue(fieldId: String): String

    @Query(
        """
        SELECT o.epochDay,o.trackerId,o.targetMet
        FROM tracker_day_outcomes o
        JOIN trackers t ON t.id=o.trackerId WHERE t.isArchived=0
        """,
    )
    fun observeActivityValues(): Flow<List<ActivityTrackerRow>>

    @Query(
        """
        SELECT v.epochDay, t.name AS trackerName, f.name AS fieldName,
               v.numericValue, v.booleanValue, v.textValue, f.unit, v.notes
        FROM tracker_daily_values v
        JOIN tracker_fields f ON f.id = v.fieldId
        JOIN trackers t ON t.id = f.trackerId
        WHERE t.isArchived = 0
        ORDER BY v.epochDay DESC, t.name, f.position
        """,
    )
    fun observeHistoryValues(): Flow<List<HistoryTrackerRow>>

    @Query("DELETE FROM trackers WHERE isDemo = 1")
    suspend fun deleteDemoTrackers(): Int
}

@Dao
interface MetadataDao {
    @Query("SELECT version FROM seed_metadata WHERE key = :key")
    suspend fun version(key: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: SeedMetadataEntity)
}

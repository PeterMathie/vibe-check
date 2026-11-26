package com.petermathie.vibecheck.data.local

import androidx.room.*
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface EditorDao {
    @Query("SELECT * FROM exercises WHERE id=:id") suspend fun exerciseById(id:String):ExerciseEntity?
    @Query("SELECT * FROM exercise_muscles WHERE exerciseId=:id") suspend fun muscleMappings(id:String):List<ExerciseMuscleEntity>
    @Query("SELECT * FROM programme_exercises WHERE programmeDayId=:day ORDER BY position") suspend fun programmeEntries(day:String):List<ProgrammeExerciseEntity>
    @Query("DELETE FROM workout_muscles WHERE workoutExerciseId=:id") suspend fun clearWorkoutMuscles(id:String)
    @Upsert suspend fun workoutMuscles(rows:List<WorkoutMuscleEntity>)
    @Query("SELECT * FROM programmes WHERE isArchived = 0 ORDER BY position,name") fun programmes(): Flow<List<ProgrammeEntity>>
    @Query("SELECT * FROM programmes WHERE isArchived = 1 ORDER BY name") fun archivedProgrammes(): Flow<List<ProgrammeEntity>>
    @Query("SELECT * FROM programme_days ORDER BY position") fun days(): Flow<List<ProgrammeDayEntity>>
    @Query("SELECT * FROM programme_exercises ORDER BY position") fun entries(): Flow<List<ProgrammeExerciseEntity>>
    @Query("SELECT * FROM exercises ORDER BY canonicalName") fun exercises(): Flow<List<ExerciseEntity>>
    @Query("SELECT * FROM muscles ORDER BY displayName") fun muscles(): Flow<List<MuscleEntity>>
    @Query("SELECT * FROM exercise_muscles") fun mappings(): Flow<List<ExerciseMuscleEntity>>
    @Query("SELECT * FROM exercise_aliases") fun aliases(): Flow<List<ExerciseAliasEntity>>
    @Query("SELECT * FROM exercise_variations ORDER BY progressionRank") fun variations(): Flow<List<ExerciseVariationEntity>>
    @Query("SELECT * FROM exercise_reference_videos ORDER BY createdAt") fun referenceVideos(): Flow<List<ExerciseReferenceVideoEntity>>
    @Query("SELECT * FROM exercise_variations WHERE id=:id") suspend fun variationById(id: String): ExerciseVariationEntity?
    @Upsert suspend fun programme(row: ProgrammeEntity)
    @Upsert suspend fun day(row: ProgrammeDayEntity)
    @Upsert suspend fun entry(row: ProgrammeExerciseEntity)
    @Upsert suspend fun exercise(row: ExerciseEntity)
    @Upsert suspend fun variation(row: ExerciseVariationEntity)
    @Upsert suspend fun referenceVideo(row: ExerciseReferenceVideoEntity)
    @Query("DELETE FROM exercise_reference_videos WHERE id = :id") suspend fun deleteReferenceVideo(id: String)
    @Query("DELETE FROM programmes WHERE id = :id") suspend fun deleteProgramme(id: String)
    @Query("DELETE FROM programme_days WHERE id = :id") suspend fun deleteDay(id: String)
    @Query("DELETE FROM programme_exercises WHERE id = :id") suspend fun deleteEntry(id: String)
    @Query("DELETE FROM exercise_aliases WHERE exerciseId = :id") suspend fun clearAliases(id: String)
    @Query("DELETE FROM exercise_muscles WHERE exerciseId = :id") suspend fun clearMappings(id: String)
    @Query("SELECT * FROM workouts ORDER BY startedAt DESC") fun workouts(): Flow<List<WorkoutEntity>>
    @Query("SELECT * FROM workout_exercises ORDER BY position") fun workoutExercises(): Flow<List<WorkoutExerciseEntity>>
    @Query("SELECT * FROM workout_exercises WHERE id=:id") suspend fun workoutExerciseById(id: String): WorkoutExerciseEntity?
    @Query("SELECT * FROM workout_exercises WHERE workoutId=:workoutId ORDER BY position")
    suspend fun workoutExercisesForWorkout(workoutId: String): List<WorkoutExerciseEntity>
    @Query("UPDATE workout_exercises SET position=:position WHERE id=:id")
    suspend fun updateWorkoutExercisePosition(id: String, position: Int)
    @Query("SELECT * FROM workout_sets ORDER BY ordinal") fun sets(): Flow<List<WorkoutSetEntity>>
    @Query("SELECT * FROM workout_sets WHERE id=:id") suspend fun setById(id: String): WorkoutSetEntity?
    @Query("SELECT * FROM workout_set_bands ORDER BY ordinal") fun setBands(): Flow<List<WorkoutSetBandEntity>>
    @Query("SELECT b.* FROM workout_set_bands b JOIN workout_sets s ON s.id=b.setId WHERE s.workoutExerciseId=:workoutExerciseId ORDER BY b.ordinal")
    suspend fun bandsForExercise(workoutExerciseId: String): List<WorkoutSetBandEntity>
    @Query("SELECT * FROM bands ORDER BY widthCentimetres,id") fun bands(): Flow<List<BandEntity>>
    @Query("SELECT COUNT(*) FROM bands") suspend fun bandCount(): Int
    @Upsert suspend fun band(row: BandEntity)
    @Query("SELECT * FROM bands WHERE id=:id") suspend fun bandById(id: String): BandEntity?
    @Upsert suspend fun workout(row: WorkoutEntity)
    @Upsert suspend fun workoutExercise(row: WorkoutExerciseEntity)
    @Upsert suspend fun set(row: WorkoutSetEntity)
    @Upsert suspend fun setBands(rows: List<WorkoutSetBandEntity>)
    @Query("DELETE FROM workout_sets WHERE id = :id") suspend fun deleteSet(id: String)
    @Query("DELETE FROM workout_exercises WHERE id = :id") suspend fun deleteWorkoutExercise(id: String)
    @Query("DELETE FROM workout_sets WHERE workoutExerciseId=:id AND ordinal=:ordinal")
    suspend fun deleteSetAt(id: String, ordinal: Int)
    @Query("UPDATE workout_sets SET ordinal=ordinal-1 WHERE workoutExerciseId=:id AND ordinal>:ordinal")
    suspend fun shiftSetsAfterDeletion(id: String, ordinal: Int)
    @Query("UPDATE workout_exercises SET targetSets=:count WHERE id=:id")
    suspend fun setWorkoutSetCount(id: String, count: Int)
    @Transaction
    suspend fun deleteWorkoutSet(id: String, ordinal: Int, remaining: Int) {
        require(ordinal > 0 && remaining >= 0) { "Invalid set deletion" }
        val drafts = entryDrafts(id)
        deleteSetAt(id, ordinal)
        shiftSetsAfterDeletion(id, ordinal)
        deleteEntryDrafts(id)
        drafts.filter { it.ordinal != ordinal }.forEach {
            entryDraft(if (it.ordinal > ordinal) it.copy(ordinal = it.ordinal - 1) else it)
        }
        setWorkoutSetCount(id, remaining)
    }
    @Query("DELETE FROM workout_set_bands WHERE setId = :id") suspend fun clearBands(id: String)
    @Query("SELECT * FROM workout_entry_drafts WHERE workoutExerciseId = :workoutExerciseId ORDER BY ordinal LIMIT 1")
    suspend fun entryDraft(workoutExerciseId: String): WorkoutEntryDraftEntity?
    @Query("SELECT * FROM workout_entry_drafts WHERE workoutExerciseId = :workoutExerciseId ORDER BY ordinal")
    suspend fun entryDrafts(workoutExerciseId: String): List<WorkoutEntryDraftEntity>
    @Query("SELECT * FROM workout_entry_drafts ORDER BY workoutExerciseId, ordinal")
    fun observeEntryDrafts(): Flow<List<WorkoutEntryDraftEntity>>
    @Upsert suspend fun entryDraft(row: WorkoutEntryDraftEntity)
    @Query("SELECT EXISTS(SELECT 1 FROM workout_sets WHERE id = :setId)")
    suspend fun hasSet(setId: String): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM workout_exercises we JOIN workouts w ON w.id = we.workoutId WHERE we.id = :workoutExerciseId AND w.status = 'DRAFT')")
    suspend fun belongsToDraftWorkout(workoutExerciseId: String): Boolean
    @Transaction
    suspend fun persistEntryDraft(row: WorkoutEntryDraftEntity) {
        if (!hasSet(row.setId) && belongsToDraftWorkout(row.workoutExerciseId)) entryDraft(row)
    }
    @Query("DELETE FROM workout_entry_drafts WHERE workoutExerciseId = :workoutExerciseId AND ordinal = :ordinal")
    suspend fun deleteEntryDraft(workoutExerciseId: String, ordinal: Int)
    @Query("DELETE FROM workout_entry_drafts WHERE workoutExerciseId = :workoutExerciseId")
    suspend fun deleteEntryDrafts(workoutExerciseId: String)
    @Query("DELETE FROM workout_entry_drafts WHERE workoutExerciseId IN (SELECT id FROM workout_exercises WHERE workoutId = :workoutId)")
    suspend fun deleteEntryDraftsForWorkout(workoutId: String)
    @Transaction
    suspend fun consumeEntryDraft(row: WorkoutSetEntity, bands: List<WorkoutSetBandEntity>) {
        set(row)
        clearBands(row.id)
        setBands(bands)
        deleteEntryDraft(row.workoutExerciseId, row.ordinal)
    }
    @Transaction
    suspend fun saveSetWithSnapshots(
        row: WorkoutSetEntity,
        bandIds: List<String>,
        consumeDraft: Boolean,
        inputConfigOverride: String? = null,
    ) {
        val variation = row.variationId?.let { variationById(it) }
        val workoutExercise = requireNotNull(workoutExerciseById(row.workoutExerciseId)) {
            "Unknown workout exercise: ${row.workoutExerciseId}"
        }
        val exerciseConfig = ExerciseInputConfig.decode(
            workoutExercise.inputConfig, workoutExercise.trackingType, workoutExercise.actualExerciseId,
        )
        val original = setById(row.id)?.takeIf { it.variationId == row.variationId }
            ?: row.takeIf { inputConfigOverride != null }
        val saved = row.copy(
            variationRankSnapshot = original?.variationRankSnapshot ?: variation?.progressionRank,
            variationNameSnapshot = original?.variationNameSnapshot?.takeIf { it.isNotBlank() } ?: variation?.name.orEmpty(),
            variationTrackingTypeSnapshot = original?.variationTrackingTypeSnapshot?.takeIf { it.isNotBlank() } ?: variation?.trackingType.orEmpty(),
            variationInputConfigSnapshot = inputConfigOverride
                ?: original?.variationInputConfigSnapshot?.takeIf { it.isNotBlank() } ?: variation?.let {
                ExerciseInputConfig.decode(
                    it.inputConfig.ifBlank { workoutExercise.inputConfig }, it.trackingType, it.exerciseId,
                    defaultManyTuts = exerciseConfig.manyTuts,
                    defaultMeasurementOrder = exerciseConfig.measurementOrder,
                ).encode()
            }.orEmpty(),
        )
        val originalBands = bandsForExercise(saved.workoutExerciseId)
            .filter { it.setId == saved.id }.associateBy { it.bandId }
        set(saved)
        clearBands(saved.id)
        setBands(bandIds.mapIndexedNotNull { index, id ->
            originalBands[id]?.copy(ordinal = index)
                ?: bandById(id)?.let { WorkoutSetBandEntity(saved.id, id, index, it.name, it.widthCentimetres) }
        })
        if (consumeDraft) deleteEntryDraft(saved.workoutExerciseId, saved.ordinal)
    }
    @Query("DELETE FROM workouts WHERE id = :id") suspend fun deleteWorkout(id: String)
    @Query("SELECT * FROM trackers ORDER BY position, name") fun trackers(): Flow<List<TrackerEntity>>
    @Query("SELECT * FROM tracker_fields ORDER BY position") fun fields(): Flow<List<TrackerFieldEntity>>
    @Query("SELECT * FROM trackers ORDER BY position, name")
    suspend fun trackersSnapshot(): List<TrackerEntity>
    @Query("SELECT * FROM tracker_fields WHERE trackerId = :trackerId ORDER BY position")
    suspend fun fieldsForTracker(trackerId: String): List<TrackerFieldEntity>
    @Query("SELECT * FROM tracker_daily_values") fun values(): Flow<List<TrackerDailyValueEntity>>
    @Upsert suspend fun tracker(row: TrackerEntity)
    @Query("DELETE FROM trackers WHERE id = :id") suspend fun deleteTracker(id: String)
    @Query("SELECT COUNT(*) FROM tracker_daily_values WHERE fieldId IN (SELECT id FROM tracker_fields WHERE trackerId = :id)")
    suspend fun trackerValueCount(id: String): Int
    @Upsert suspend fun field(row: TrackerFieldEntity)
    @Query("SELECT * FROM tracker_fields WHERE id = :id") suspend fun fieldById(id: String): TrackerFieldEntity?
    @Query("DELETE FROM tracker_daily_values WHERE fieldId = :id AND epochDay = :day") suspend fun clearValue(id: String, day: Long)
    @Query("SELECT * FROM body_measurements WHERE isWeightCleared = 0 OR LOWER(metric) != 'bodyweight' ORDER BY recordedAt DESC, id DESC")
    fun measurements(): Flow<List<BodyMeasurementEntity>>
    @Query("SELECT * FROM body_measurements WHERE LOWER(metric) = 'bodyweight' AND recordedAt >= :start AND recordedAt < :end ORDER BY isWeightCleared, recordedAt DESC, id DESC")
    suspend fun bodyweightsForDay(start: Long, end: Long): List<BodyMeasurementEntity>
    @Query("UPDATE body_measurements SET isWeightCleared = 1 WHERE LOWER(metric) = 'bodyweight' AND recordedAt >= :start AND recordedAt < :end")
    suspend fun clearBodyweightsForDay(start: Long, end: Long)
    @Upsert suspend fun measurement(row: BodyMeasurementEntity)
    @Query("DELETE FROM body_measurements WHERE id = :id") suspend fun deleteMeasurement(id: String)
    @Query("DELETE FROM body_measurements WHERE isDemo = 1") suspend fun deleteDemoMeasurements(): Int
}

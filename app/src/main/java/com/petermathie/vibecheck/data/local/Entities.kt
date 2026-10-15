package com.petermathie.vibecheck.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "seed_metadata", primaryKeys = ["key"])
data class SeedMetadataEntity(val key: String, val version: Int)

@Entity(tableName = "muscles", primaryKeys = ["id"])
data class MuscleEntity(
    val id: String,
    val displayName: String,
    val svgGroupId: String,
)

@Entity(tableName = "exercises", primaryKeys = ["id"], indices = [Index("canonicalName")])
data class ExerciseEntity(
    val id: String,
    val canonicalName: String,
    val tag: String,
    val trackingType: String = "",
    val equipment: String?,
    val instructions: String?,
    val source: String,
    val isCustom: Boolean,
    val isArchived: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "''") val inputConfig: String = "",
    val targetSets: Int? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRpe: Double? = null,
    @androidx.room.ColumnInfo(defaultValue = "120") val restSeconds: Int = 120,
)

@Entity(
    tableName = "exercise_aliases",
    primaryKeys = ["exerciseId", "normalizedAlias"],
    foreignKeys = [ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("exerciseId"), Index("normalizedAlias")],
)
data class ExerciseAliasEntity(
    val exerciseId: String,
    val alias: String,
    val normalizedAlias: String,
)

@Entity(
    tableName = "exercise_muscles",
    primaryKeys = ["exerciseId", "muscleId"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MuscleEntity::class,
            parentColumns = ["id"],
            childColumns = ["muscleId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("exerciseId"), Index("muscleId")],
)
data class ExerciseMuscleEntity(
    val exerciseId: String,
    val muscleId: String,
    val role: String,
)

@Entity(
    tableName = "exercise_variations",
    primaryKeys = ["id"],
    foreignKeys = [ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("exerciseId")],
)
data class ExerciseVariationEntity(
    val id: String,
    val exerciseId: String,
    val name: String,
    val progressionRank: Int,
    val isSeeded: Boolean,
    val trackingType: String = "",
    @androidx.room.ColumnInfo(defaultValue = "''") val inputConfig: String = "",
    val targetSets: Int? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetRpe: Double? = null,
    @androidx.room.ColumnInfo(defaultValue = "120") val restSeconds: Int = 120,
)

@Entity(
    tableName = "exercise_reference_videos",
    primaryKeys = ["id"],
    foreignKeys = [ForeignKey(
        entity = ExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["exerciseId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("exerciseId")],
)
data class ExerciseReferenceVideoEntity(
    val id: String,
    val exerciseId: String,
    val displayName: String,
    val fileName: String,
    val createdAt: Long,
)

@Entity(tableName = "bands", primaryKeys = ["id"])
data class BandEntity(
    val id: String,
    val name: String,
    val widthCentimetres: Double,
    val colourArgb: Long,
)

@Entity(tableName = "programmes", primaryKeys = ["id"])
data class ProgrammeEntity(
    val id: String,
    val name: String,
    val mode: String,
    val isDemo: Boolean,
    val isArchived: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0") val position: Int = 0,
)

@Entity(
    tableName = "programme_days",
    primaryKeys = ["id"],
    foreignKeys = [ForeignKey(
        entity = ProgrammeEntity::class,
        parentColumns = ["id"],
        childColumns = ["programmeId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("programmeId")],
)
data class ProgrammeDayEntity(
    val id: String,
    val programmeId: String,
    val name: String,
    val position: Int,
)

@Entity(
    tableName = "programme_exercises",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = ProgrammeDayEntity::class,
            parentColumns = ["id"],
            childColumns = ["programmeDayId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("programmeDayId"), Index("exerciseId")],
)
data class ProgrammeExerciseEntity(
    val id: String,
    val programmeDayId: String,
    val exerciseId: String,
    val position: Int,
    val targetSets: Int?,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetHoldSeconds: Int?,
    val restSeconds: Int,
    val targetRpe: Double?,
    val notes: String,
    val supersetGroup: String?,
    @androidx.room.ColumnInfo(defaultValue = "''") val inputConfig: String = "",
)

@Entity(
    tableName = "workouts",
    primaryKeys = ["id"],
    indices = [Index("status"), Index("finishedAt"), Index("programmeDayId")],
)
data class WorkoutEntity(
    val id: String,
    val programmeDayId: String?,
    val name: String,
    val mode: String,
    val status: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val notes: String,
    val bodyweightKg: Double?,
    val isDemo: Boolean,
)

@Entity(
    tableName = "workout_exercises",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["plannedExerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["actualExerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("workoutId"), Index("plannedExerciseId"), Index("actualExerciseId")],
)
data class WorkoutExerciseEntity(
    val id: String,
    val workoutId: String,
    val plannedExerciseId: String,
    val actualExerciseId: String,
    val position: Int,
    val notes: String,
    val restSeconds: Int,
    val supersetGroup: String?,
    @androidx.room.ColumnInfo(defaultValue = "''") val exerciseName: String = "",
    @androidx.room.ColumnInfo(defaultValue = "''") val trackingType: String = "",
    @androidx.room.ColumnInfo(defaultValue = "''") val targets: String = "",
    @androidx.room.ColumnInfo(defaultValue = "''") val inputConfig: String = "",
    val targetSets: Int? = null,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetHoldSeconds: Int? = null,
    val targetRpe: Double? = null,
)

@Entity(tableName="workout_muscles",primaryKeys=["workoutExerciseId","muscleId"],foreignKeys=[ForeignKey(entity=WorkoutExerciseEntity::class,parentColumns=["id"],childColumns=["workoutExerciseId"],onDelete=ForeignKey.CASCADE)],indices=[Index("workoutExerciseId")])
data class WorkoutMuscleEntity(val workoutExerciseId:String,val muscleId:String,val role:String)

@Entity(
    tableName = "workout_sets",
    primaryKeys = ["id"],
    foreignKeys = [ForeignKey(
        entity = WorkoutExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutExerciseId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("workoutExerciseId"), Index("loggedAt")],
)
data class WorkoutSetEntity(
    val id: String,
    val workoutExerciseId: String,
    val ordinal: Int,
    val setType: String,
    val result: String,
    val variationId: String?,
    val weightKg: Double?,
    val reps: Double?,
    val holdMillis: Long?,
    val leftReps: Double?,
    val rightReps: Double?,
    val leftHoldMillis: Long?,
    val rightHoldMillis: Long?,
    val addedWeightKg: Double?,
    val assistanceKg: Double?,
    val rpe: Double?,
    val romValue: Double?,
    val romUnit: String?,
    val notes: String,
    val loggedAt: Long,
    val updatedAt: Long,
    val variationRankSnapshot: Int? = null,
    val timeUnderTensionMillis: Long? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val bandResistance: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "''") val variationNameSnapshot: String = "",
    @androidx.room.ColumnInfo(defaultValue = "''") val variationTrackingTypeSnapshot: String = "",
    @androidx.room.ColumnInfo(defaultValue = "''") val variationInputConfigSnapshot: String = "",
    val legacyReps: Int? = null,
    val legacyLeftReps: Int? = null,
    val legacyRightReps: Int? = null,
    @androidx.room.ColumnInfo(defaultValue = "'[]'") val timeMeasurementsMillis: String = "[]",
)

@Entity(
    tableName = "workout_set_bands",
    primaryKeys = ["setId", "bandId", "ordinal"],
    foreignKeys = [
        ForeignKey(
            entity = WorkoutSetEntity::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = BandEntity::class,
            parentColumns = ["id"],
            childColumns = ["bandId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("setId"), Index("bandId")],
)
data class WorkoutSetBandEntity(
    val setId: String,
    val bandId: String,
    val ordinal: Int,
    @androidx.room.ColumnInfo(defaultValue = "''") val nameSnapshot: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val widthCentimetresSnapshot: Double = 0.0,
)

@Entity(
    tableName = "workout_entry_drafts",
    primaryKeys = ["workoutExerciseId", "ordinal"],
    foreignKeys = [ForeignKey(
        entity = WorkoutExerciseEntity::class,
        parentColumns = ["id"],
        childColumns = ["workoutExerciseId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("workoutExerciseId")],
)
data class WorkoutEntryDraftEntity(
    val workoutExerciseId: String,
    val setId: String,
    val ordinal: Int,
    val performance: String,
    val rpe: String,
    val detailsOpen: Boolean,
    val warmUp: Boolean,
    val failed: Boolean,
    val bandIds: String,
    val variationId: String?,
    val leftValue: String,
    val rightValue: String,
    val addedWeight: String,
    val assistance: String,
    val romValue: String,
    val romUnit: String,
    val updatedAt: Long,
    @androidx.room.ColumnInfo(defaultValue = "''") val timeHeld: String = "",
    @androidx.room.ColumnInfo(defaultValue = "''") val timeUnderTension: String = "",
    @androidx.room.ColumnInfo(defaultValue = "'[]'") val timeMeasurements: String = "[]",
)

@Entity(tableName = "trackers", primaryKeys = ["id"])
data class TrackerEntity(
    val id: String,
    val name: String,
    val isDemo: Boolean,
    val isArchived: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "4283215696") val colourArgb: Long = 0xFF4CAF50L,
    @androidx.room.ColumnInfo(defaultValue = "0") val position: Int = 0,
    @androidx.room.ColumnInfo(defaultValue = "7") val heatmapLightBelow: Double = 7.0,
    @androidx.room.ColumnInfo(defaultValue = "15") val heatmapMediumBelow: Double = 15.0,
    @androidx.room.ColumnInfo(defaultValue = "'habit'") val iconName: String = "habit",
)

@Entity(
    tableName = "tracker_fields",
    primaryKeys = ["id"],
    foreignKeys = [ForeignKey(
        entity = TrackerEntity::class,
        parentColumns = ["id"],
        childColumns = ["trackerId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("trackerId")],
)
data class TrackerFieldEntity(
    val id: String,
    val trackerId: String,
    val name: String,
    val valueType: String,
    val unit: String?,
    val targetComparison: String?,
    val targetValue: Double?,
    val position: Int,
    @androidx.room.ColumnInfo(defaultValue = "''") val choiceOptions: String = "",
    val targetMaxValue: Double? = null,
    @androidx.room.ColumnInfo(defaultValue = "0") val isArchived: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "-1") val choiceLightThrough: Int = -1,
    @androidx.room.ColumnInfo(defaultValue = "-1") val choiceDarkFrom: Int = -1,
    @androidx.room.ColumnInfo(defaultValue = "''") val choiceOptionsJson: String = "",
)

@Entity(
    tableName = "tracker_daily_values",
    primaryKeys = ["fieldId", "epochDay"],
    foreignKeys = [ForeignKey(
        entity = TrackerFieldEntity::class,
        parentColumns = ["id"],
        childColumns = ["fieldId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("fieldId"), Index("epochDay")],
)
data class TrackerDailyValueEntity(
    val fieldId: String,
    val epochDay: Long,
    val numericValue: Double?,
    val booleanValue: Boolean?,
    val textValue: String?,
    val notes: String,
    val updatedAt: Long,
    val choiceOptionId: String? = null,
    val choiceIntensity: String? = null,
)

@Entity(
    tableName = "tracker_day_outcomes",
    primaryKeys = ["trackerId", "epochDay"],
    foreignKeys = [ForeignKey(
        entity = TrackerEntity::class,
        parentColumns = ["id"],
        childColumns = ["trackerId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("trackerId"), Index("epochDay")],
)
data class TrackerDayOutcomeEntity(
    val trackerId: String,
    val epochDay: Long,
    val targetMet: Boolean,
)

@Entity(tableName = "body_measurements", primaryKeys = ["id"], indices = [Index("recordedAt")])
data class BodyMeasurementEntity(
    val id: String,
    val recordedAt: Long,
    val metric: String,
    val value: Double,
    val unit: String,
    val notes: String,
    val isDemo: Boolean,
)

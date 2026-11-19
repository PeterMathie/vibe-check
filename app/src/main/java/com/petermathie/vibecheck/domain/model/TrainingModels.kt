package com.petermathie.vibecheck.domain.model

enum class TrainingMode { STRENGTH, STRETCHING }
enum class AnatomySex { MALE, FEMALE }
enum class ExerciseTag { STRENGTH, STRETCHING, BOTH }
enum class TrackingType {
    WEIGHT_REPS,
    BODYWEIGHT_REPS,
    ASSISTED_REPS,
    HOLD,
    SKILL_HOLD,
    REPS,
    ROM_MEASUREMENT,
}

enum class MuscleRole(val setEquivalent: Double) {
    PRIMARY(1.0),
    SECONDARY(0.5),
}

enum class WorkoutStatus { DRAFT, FINISHED, DISCARDED }
enum class SetType { WARM_UP, WORKING }
enum class SetResult { COMPLETED, FAILED }

data class ExerciseSummary(
    val id: String,
    val name: String,
    val tag: ExerciseTag,
    val trackingType: TrackingType,
    val aliases: List<String> = emptyList(),
    val muscles: List<String> = emptyList(),
)

data class ProgrammeDaySummary(
    val id: String,
    val name: String,
    val mode: TrainingMode,
    val exerciseCount: Int,
)

data class SetDraft(
    val weightKg: Double? = null,
    val reps: Double? = null,
    val holdMillis: Long? = null,
    val rpe: Double? = null,
    val notes: String = "",
    val type: SetType = SetType.WORKING,
    val result: SetResult = SetResult.COMPLETED,
)

data class WorkoutSetLog(
    val id: String,
    val ordinal: Int,
    val weightKg: Double?,
    val reps: Double?,
    val holdMillis: Long?,
    val rpe: Double?,
    val notes: String,
    val type: SetType,
    val result: SetResult,
)

data class WorkoutExerciseLog(
    val id: String,
    val plannedExerciseId: String,
    val actualExerciseId: String,
    val exerciseName: String,
    val trackingType: TrackingType,
    val notes: String,
    val restSeconds: Int,
    val sets: List<WorkoutSetLog>,
)

data class ActiveWorkout(
    val id: String,
    val name: String,
    val mode: TrainingMode,
    val startedAt: Long,
    val notes: String,
    val exercises: List<WorkoutExerciseLog>,
)

enum class MuscleRecencyBand {
    NEVER,
    UNDER_24_HOURS,
    HOURS_24_TO_48,
    HOURS_48_TO_72,
    DAYS_3_TO_7,
    OVER_7_DAYS,
}

data class MuscleRecency(
    val muscleId: String,
    val lastTrainedAt: Long?,
    val band: MuscleRecencyBand,
    val contributingExerciseNames: List<String>,
    val setEquivalents: Double,
    val weeklyWorkingSets: Int = 0,
    val weeklyAverageRpe: Double? = null,
    val weeklyContributions: List<MuscleContribution> = emptyList(),
)

data class MuscleContribution(
    val exerciseName: String,
    val role: MuscleRole,
    val setCount: Int,
)

data class ActivityDay(
    val epochDay: Long,
    val activityCount: Int,
)

data class HistoryActivity(
    val title: String,
    val detail: String,
    val notes: String = "",
)

data class HistoryDayDetail(
    val epochDay: Long,
    val activities: List<HistoryActivity>,
)

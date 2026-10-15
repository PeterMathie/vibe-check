package com.petermathie.vibecheck

import com.petermathie.vibecheck.data.local.ExerciseEntity
import com.petermathie.vibecheck.data.local.WorkoutExerciseEntity
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.ui.emptySet
import com.petermathie.vibecheck.ui.workoutTimingSummaries
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutHistoryTimingSummaryTest {
    @Test
    fun mixedMeasurementsShowOnlyRecordedTimingFields() {
        val exercise = ExerciseEntity(
            id = "handstand",
            canonicalName = "Handstand",
            tag = "STRENGTH",
            trackingType = "SKILL_HOLD",
            equipment = null,
            instructions = null,
            source = "test",
            isCustom = false,
        )
        val row = WorkoutExerciseEntity(
            id = "row",
            workoutId = "workout",
            plannedExerciseId = exercise.id,
            actualExerciseId = exercise.id,
            position = 0,
            notes = "",
            restSeconds = 60,
            supersetGroup = null,
            exerciseName = exercise.canonicalName,
            trackingType = exercise.trackingType,
            inputConfig = ExerciseInputConfig(timeHeld = true, timeUnderTension = true).encode(),
        )
        val timed = emptySet(row.id, 1).copy(
            holdMillis = 12_500,
            timeUnderTensionMillis = 30_000,
        )
        val repsOnly = emptySet(row.id, 2).copy(reps = 8.0)

        assertEquals(
            listOf("Handstand · TUT 12.5s · Total 30s"),
            workoutTimingSummaries("workout", listOf(row), listOf(exercise), listOf(timed, repsOnly)),
        )
        assertEquals(
            emptyList<String>(),
            workoutTimingSummaries("other", listOf(row), listOf(exercise), listOf(timed)),
        )
    }

    @Test
    fun bilateralHoldsRemainDistinctWithoutClutteringOtherEntries() {
        val exercise = ExerciseEntity(
            id = "split",
            canonicalName = "Front split",
            tag = "STRETCHING",
            trackingType = "HOLD",
            equipment = null,
            instructions = null,
            source = "test",
            isCustom = false,
        )
        val row = WorkoutExerciseEntity(
            id = "row",
            workoutId = "workout",
            plannedExerciseId = exercise.id,
            actualExerciseId = exercise.id,
            position = 0,
            notes = "",
            restSeconds = 60,
            supersetGroup = null,
        )
        val set = emptySet(row.id, 1).copy(leftHoldMillis = 15_000, rightHoldMillis = 18_250)

        assertEquals(
            listOf("Front split · Left hold 15s · Right hold 18.25s"),
            workoutTimingSummaries("workout", listOf(row), listOf(exercise), listOf(set)),
        )
    }
}

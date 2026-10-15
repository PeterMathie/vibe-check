package com.petermathie.vibecheck

import com.petermathie.vibecheck.data.local.ExerciseEntity
import com.petermathie.vibecheck.data.local.WorkoutExerciseEntity
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.ui.emptySet
import com.petermathie.vibecheck.ui.workoutTimingSummaries
import com.petermathie.vibecheck.domain.workout.encodeTimeMeasurementsMillis
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
    fun repeatedHandstandTimesAndWeightedBodyweightStayExplicit() {
        val handstand = ExerciseEntity(
            "handstand", "Handstand", "STRENGTH", "SKILL_HOLD", null, null, "test", false,
        )
        val pullUp = ExerciseEntity(
            "pull-up", "Pull-up", "STRENGTH", "BODYWEIGHT_REPS", null, null, "test", false,
        )
        val handstandRow = WorkoutExerciseEntity(
            "handstand-row", "workout", handstand.id, handstand.id, 0, "", 60, null,
            handstand.canonicalName, handstand.trackingType, "",
            ExerciseInputConfig(
                timeUnderTension = true,
                repeatedTimeMeasurements = true,
            ).encode(),
        )
        val pullUpRow = WorkoutExerciseEntity(
            "pull-up-row", "workout", pullUp.id, pullUp.id, 1, "", 60, null,
            pullUp.canonicalName, pullUp.trackingType, "",
            ExerciseInputConfig(reps = true, bodyweight = true, addedWeight = true).encode(),
        )
        val attempts = emptySet(handstandRow.id, 1).copy(
            timeUnderTensionMillis = 18_000,
            timeMeasurementsMillis = encodeTimeMeasurementsMillis(listOf(8_000, 10_000)),
        )
        val weighted = emptySet(pullUpRow.id, 1).copy(reps = 5.0, addedWeightKg = 10.0)

        assertEquals(
            listOf(
                "Handstand · Times 8s / 10s · Total 18s",
                "Pull-up · Bodyweight 80kg + 10kg added × 5 reps",
            ),
            workoutTimingSummaries(
                "workout",
                listOf(handstandRow, pullUpRow),
                listOf(handstand, pullUp),
                listOf(attempts, weighted),
                bodyweightKg = 80.0,
            ),
        )
        assertEquals(
            listOf(
                "Handstand · Times 8s / 10s · Total 18s",
                "Pull-up · Bodyweight 176.37lb + 22.05lb added × 5 reps",
            ),
            workoutTimingSummaries(
                "workout",
                listOf(handstandRow, pullUpRow),
                listOf(handstand, pullUp),
                listOf(attempts, weighted),
                bodyweightKg = 80.0,
                weightUnit = "lb",
            ),
        )
    }

    @Test
    fun plancheUsesTotalTimeWithoutRenamingOtherHolds() {
        val planche = ExerciseEntity(
            "core:planche", "Planche", "STRENGTH", "SKILL_HOLD", null, null, "test", false,
        )
        val row = WorkoutExerciseEntity(
            "row", "workout", planche.id, planche.id, 0, "", 60, null,
            planche.canonicalName, planche.trackingType, "",
            ExerciseInputConfig(timeHeld = true).encode(),
        )

        assertEquals(
            listOf("Planche · Total Time 12s"),
            workoutTimingSummaries(
                "workout",
                listOf(row),
                listOf(planche),
                listOf(emptySet(row.id, 1).copy(holdMillis = 12_000)),
            ),
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

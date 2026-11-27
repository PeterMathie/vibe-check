package com.petermathie.vibecheck

import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.MeasurementMetric
import com.petermathie.vibecheck.ui.emptySet
import com.petermathie.vibecheck.ui.setDescription
import com.petermathie.vibecheck.ui.workoutDurationText
import com.petermathie.vibecheck.domain.workout.*
import org.junit.Assert.*
import org.junit.Test

class TimingEntriesTest {
    @Test
    fun runningWorkoutUsesWholeElapsedSecondsIncludingTimeBeforeAnySet() {
        val start = 1_700_000_000_321L
        assertEquals("00:00:00", workoutDurationText(start, null, true, start + 999))
        assertEquals("00:00:01", workoutDurationText(start, null, true, start + 1000))
        assertEquals("01:01:01", workoutDurationText(start, null, true, start + 3_661_987))
        assertEquals("23:59:59", workoutDurationText(start, null, true, start + 86_399_999))
        assertEquals("24:00:00", workoutDurationText(start, null, true, start + 86_400_000))
        assertEquals("100:00:00", workoutDurationText(start, null, true, start + 360_000_000))
    }

    @Test
    fun finishedWorkoutStopsAtSavedFinishAndDoesNotInventMissingFinish() {
        val start = 1_700_000_000_321L
        val finish = start + 61_999
        assertEquals("00:01:01", workoutDurationText(start, finish, false, finish))
        assertEquals("00:01:01", workoutDurationText(start, finish, false, finish + 86_400_000))
        assertEquals("—", workoutDurationText(start, null, false, finish))
    }

    @Test
    fun reversedWorkoutTimestampsAreReportedWithoutClampingOrChangingThem() {
        assertEquals("Invalid workout times", workoutDurationText(1000, 999, false, 2000))
        assertEquals("Invalid workout times", workoutDurationText(1000, null, true, 999))
    }

    @Test
    fun timingInputsRetainBlanksAndDecimalText() {
        val values = listOf("1.5", "", "2,25")
        assertEquals(values, timingInputs(encodeTimingInputs(values)))
        assertEquals(emptyList<String>(), timingInputs(""))
    }

    @Test
    fun eightDurationsRoundTripExactly() {
        val values = List(MAX_TUT_ENTRIES) { (it + 1) * 125L }
        assertEquals(values, timingMillis(encodeTimingMillis(values)))
        assertEquals(4_500L, timingMillis(encodeTimingMillis(values)).sum())
    }

    @Test
    fun invalidDurationsAndNinthEntryAreRejected() {
        listOf("[-1]", "[1.5]", "[\"100\"]", "[null]", "[1000000000001]", List(9) { 1 }.joinToString(",", "[", "]")).forEach {
            assertThrows(IllegalArgumentException::class.java) { timingMillis(it) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            encodeTimingMillis(List(9) { 1L })
        }
    }

    @Test
    fun handstandDefaultsToManyAndPlancheToOneWithoutChangingLegacyEncoding() {
        assertTrue(ExerciseInputConfig.decode("", "SKILL_HOLD", "core:handstand").manyTuts)
        assertFalse(ExerciseInputConfig.decode("", "SKILL_HOLD", "core:planche").manyTuts)
        assertFalse(ExerciseInputConfig.decode("manyTuts=false", "SKILL_HOLD", "core:handstand").manyTuts)
        val config = ExerciseInputConfig(timeHeld = true, manyTuts = true, reps = true, weightUnit = "kg")
        assertEquals(config, ExerciseInputConfig.decode(config.encode(), "WEIGHT_REPS"))
    }

    @Test
    fun explicitlySelectedMetricDoesNotDescribeInactiveHistoricalValues() {
        val recorded = emptySet("fixture", 1).copy(
            reps = 8.0, romValue = 45.0, romUnit = "cm", holdMillis = 6_000,
            variationInputConfigSnapshot = ExerciseInputConfig().withMeasurement(MeasurementMetric.REPETITIONS).encode(),
        )
        assertEquals("8 reps", setDescription(recorded))
        assertEquals("45 cm", setDescription(recorded.copy(
            variationInputConfigSnapshot = ExerciseInputConfig().withMeasurement(MeasurementMetric.RANGE_OF_MOTION).encode(),
        )))
        assertEquals("ROM 45.0 cm", setDescription(recorded.copy(variationInputConfigSnapshot = "")))
    }
}

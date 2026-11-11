package com.petermathie.vibecheck

import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.workout.*
import org.junit.Assert.*
import org.junit.Test

class TimingEntriesTest {
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
}

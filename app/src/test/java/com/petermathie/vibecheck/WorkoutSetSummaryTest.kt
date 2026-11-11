package com.petermathie.vibecheck

import com.petermathie.vibecheck.domain.workout.encodeTimingMillis
import com.petermathie.vibecheck.ui.emptySet
import com.petermathie.vibecheck.ui.setDescription
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSetSummaryTest {
    @Test
    fun compactTimingRetainsBothTotalsWithoutListingAttempts() {
        val set = emptySet("exercise", 1).copy(
            tutEntriesMillis = encodeTimingMillis(listOf(4_000L, 6_000L)),
            holdMillis = 10_000L,
            timeUnderTensionMillis = 30_000L,
        )
        assertEquals("10s · Total 30s", setDescription(set, includeTimingAttempts = false))
        assertEquals("TUT 10s · Total 30s", setDescription(set, includeTimingAttempts = false, labelTimingTotal = true))
        assertEquals("4s + 6s = 10s · Total 30s", setDescription(set))
    }

    @Test
    fun compactTimingPreservesLegacyScalarMeasurements() {
        val set = emptySet("exercise", 1).copy(holdMillis = 12_500L, timeUnderTensionMillis = 40_000L)
        assertEquals("12.5s · Total 40s", setDescription(set, includeTimingAttempts = false))
        assertEquals("Total 40s", setDescription(set.copy(holdMillis = null), includeTimingAttempts = false))
        assertEquals("TUT 12.5s · Total 40s", setDescription(set, includeTimingAttempts = false, labelTimingTotal = true))
    }

    @Test
    fun compactSummaryPreservesLoadRepsAndSetType() {
        val set = emptySet("exercise", 1).copy(weightKg = 20.0, reps = 5.0, setType = "WARM_UP")
        assertEquals("20 kg × 5 reps · warm-up", setDescription(set, includeTimingAttempts = false))
    }
}

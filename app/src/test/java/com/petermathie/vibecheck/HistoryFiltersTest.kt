package com.petermathie.vibecheck

import com.petermathie.vibecheck.data.local.WorkoutEntity
import com.petermathie.vibecheck.ui.historyDateFilterError
import com.petermathie.vibecheck.ui.historyDateMatches
import com.petermathie.vibecheck.ui.historyWorkoutTitle
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HistoryFiltersTest {
    private val workout = WorkoutEntity(
        "w", null, "Saved day", "STRENGTH", "FINISHED",
        Instant.parse("2026-11-01T03:00:00Z").toEpochMilli(),
        Instant.parse("2026-11-01T04:30:00Z").toEpochMilli(), "", null, false,
    )

    @Test fun filtersUseViewingZoneFinishDateAndInclusiveEndpointsWithoutGuessingRanges() {
        val zone = ZoneId.of("America/New_York")
        val day = LocalDate.of(2026, 11, 1)
        assertTrue(historyDateMatches(workout, null, null, zone))
        assertTrue(historyDateMatches(workout, day, null, zone))
        assertTrue(historyDateMatches(workout, day.minusDays(1), day, zone))
        assertFalse(historyDateMatches(workout, day.minusDays(1), null, zone))
        assertFalse(historyDateMatches(workout.copy(finishedAt = null), day, null, zone))
        assertNotNull(historyDateFilterError(null, day))
        assertNotNull(historyDateFilterError(day, day.minusDays(1)))
        assertFalse(historyDateMatches(workout, day, day.minusDays(1), zone))
    }

    @Test fun titlesPreferCapturedProgrammeAndNeverGuessFromCurrentCatalogue() {
        assertEquals("Saved day", historyWorkoutTitle(workout))
        assertEquals("Captured programme", historyWorkoutTitle(workout.copy(programmeNameSnapshot = "Captured programme")))
        assertEquals("Recorded workout", historyWorkoutTitle(workout.copy(name = " ", programmeNameSnapshot = "")))
    }
}

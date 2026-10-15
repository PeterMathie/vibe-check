package com.petermathie.vibecheck.ui

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingDateRangesTest {
    @Test
    fun homeHeatmapContainsExactlyTodayAndPreviousThirtyFourDaysAcrossYearBoundary() {
        val today = LocalDate.of(2026, 1, 12)
        val dates = homeHeatmapDates(today)

        assertEquals(35, dates.size)
        assertEquals(LocalDate.of(2025, 12, 9), dates.first())
        assertEquals(today, dates.last())
        assertEquals(dates.sorted(), dates)
    }

    @Test
    fun progressRangeIsInclusiveAndRejectsReversedDates() {
        val range = validProgressDateRange(
            LocalDate.of(2026, 2, 28),
            LocalDate.of(2026, 3, 1),
        )!!

        assertTrue(range.containsEpochDay(LocalDate.of(2026, 2, 28).toEpochDay()))
        assertTrue(range.containsEpochDay(LocalDate.of(2026, 3, 1).toEpochDay()))
        assertFalse(range.containsEpochDay(LocalDate.of(2026, 3, 2).toEpochDay()))
        assertTrue(
            range.containsTimestamp(
                LocalDate.of(2026, 3, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            ),
        )
        assertNull(validProgressDateRange(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun historicalHabitDateNeverMovesIntoFuture() {
        val today = LocalDate.of(2026, 10, 15)
        assertEquals(today, historicalHabitDate(today.plusDays(3), today))
        assertEquals(today.minusDays(8), historicalHabitDate(today.minusDays(8), today))
    }
}

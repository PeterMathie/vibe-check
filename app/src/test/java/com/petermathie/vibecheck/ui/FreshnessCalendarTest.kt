package com.petermathie.vibecheck.ui

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class FreshnessCalendarTest {
    @Test
    fun activityIntensityIsRelativeToOnlyEligibleDaysInTheDisplayedMonth() {
        val month = YearMonth.of(2026, 9)
        val today = month.atDay(15)
        val counts = mapOf(
            month.atDay(1).minusDays(1).toEpochDay() to 1000,
            month.atDay(1).toEpochDay() to 1,
            month.atDay(2).toEpochDay() to 4,
            month.atDay(3).toEpochDay() to 10,
            month.atDay(4).toEpochDay() to 0,
            month.atDay(16).toEpochDay() to 1000,
            month.plusMonths(1).atDay(1).toEpochDay() to 1000,
        )
        val expected = mapOf(
            month.atDay(1).toEpochDay() to 0.1f,
            month.atDay(2).toEpochDay() to 0.4f,
            month.atDay(3).toEpochDay() to 1f,
            month.atDay(4).toEpochDay() to 0f,
        )
        assertEquals(expected, homeCalendarIntensities(counts, month, today))
        assertEquals(expected, homeCalendarIntensities(counts.mapValues { it.value * 7 }, month, today))
    }

    @Test
    fun emptyMonthsStayNeutralAndEqualPositiveCountsShareFullIntensity() {
        val month = YearMonth.of(2026, 8)
        val today = LocalDate.of(2026, 9, 15)
        val firstDay = month.atDay(1).toEpochDay()
        val lastDay = month.atEndOfMonth().toEpochDay()
        assertEquals(emptyMap<Long, Float>(), homeCalendarIntensities(emptyMap(), month, today))
        assertEquals(mapOf(firstDay to 0f), homeCalendarIntensities(mapOf(firstDay to 0), month, today))
        assertEquals(
            mapOf(firstDay to 1f, lastDay to 1f),
            homeCalendarIntensities(mapOf(firstDay to 3, lastDay to 3), month, today),
        )
    }

    @Test
    fun monthGridIsMondayFirstAndAlwaysReservesSixWeeks() {
        listOf(
            YearMonth.of(2021, 2),
            YearMonth.of(2024, 2),
            YearMonth.of(2026, 6),
            YearMonth.of(2026, 8),
            YearMonth.of(2026, 11),
        ).forEach { month ->
            val cells = homeCalendarCells(month)
            assertEquals(42, cells.size)
            assertEquals(
                (1..month.lengthOfMonth()).map(month::atDay),
                cells.filterNotNull(),
            )
            assertEquals(month.atDay(1).dayOfWeek.value - 1, cells.indexOf(month.atDay(1)))
            assertEquals(
                month.atEndOfMonth().dayOfWeek.value - 1,
                cells.indexOf(month.atEndOfMonth()) % 7,
            )
        }
    }

    @Test
    fun shortMonthsLeaveWholeUnusedWeeksBlankAndNeverShowNeighbouringDates() {
        val february = homeCalendarCells(YearMonth.of(2021, 2))
        assertEquals(LocalDate.of(2021, 2, 1), february.first())
        assertEquals(List<LocalDate?>(14) { null }, february.takeLast(14))

        val sundayStart = homeCalendarCells(YearMonth.of(2026, 11))
        assertEquals(List<LocalDate?>(6) { null }, sundayStart.take(6))
        assertEquals(LocalDate.of(2026, 11, 1), sundayStart[6])
        assertEquals(List<LocalDate?>(6) { null }, sundayStart.takeLast(6))
    }

    @Test
    fun pastMonthsExposeEveryCalendarDayIncludingLeapFebruary() {
        val today = LocalDate.of(2026, 9, 25)

        assertEquals(29, freshnessMonthRange(YearMonth.of(2024, 2), today).dayCount)
        assertEquals(28, freshnessMonthRange(YearMonth.of(2025, 2), today).dayCount)
        assertEquals(30, freshnessMonthRange(YearMonth.of(2026, 4), today).dayCount)
        assertEquals(31, freshnessMonthRange(YearMonth.of(2025, 12), today).dayCount)
    }

    @Test
    fun currentMonthStopsAtTodayAndFutureMonthsCannotBeSelected() {
        val today = LocalDate.of(2026, 9, 25)

        assertEquals(today, freshnessMonthRange(YearMonth.of(2026, 9), today).lastDate)
        assertEquals(
            FreshnessMonthRange(YearMonth.of(2026, 9), LocalDate.of(2026, 9, 1), today),
            freshnessMonthRange(YearMonth.of(2027, 1), today),
        )
    }

    @Test
    fun monthSwitchKeepsDayWhenValidAndClampsAcrossBoundaries() {
        val today = LocalDate.of(2026, 9, 25)

        assertEquals(
            LocalDate.of(2026, 8, 25),
            freshnessDateInMonth(today, YearMonth.of(2026, 8), today),
        )
        assertEquals(
            LocalDate.of(2024, 2, 29),
            freshnessDateInMonth(LocalDate.of(2024, 1, 31), YearMonth.of(2024, 2), today),
        )
        assertEquals(
            LocalDate.of(2025, 12, 29),
            freshnessDateInMonth(LocalDate.of(2024, 2, 29), YearMonth.of(2025, 12), today),
        )
        assertEquals(
            today,
            freshnessDateInMonth(LocalDate.of(2025, 12, 31), YearMonth.of(2027, 1), today),
        )
    }
}

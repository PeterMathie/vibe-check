package com.petermathie.vibecheck.ui

import com.petermathie.vibecheck.ui.components.GraphDomain
import com.petermathie.vibecheck.ui.components.graphDomain
import com.petermathie.vibecheck.ui.components.graphPoint
import com.petermathie.vibecheck.ui.components.nearestGraphIndex
import com.petermathie.vibecheck.ui.components.graphDateTicks
import com.petermathie.vibecheck.ui.components.graphDateX
import com.petermathie.vibecheck.ui.components.graphDateAtX
import com.petermathie.vibecheck.ui.components.progressDateRange
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VibeGraphTest {
    @Test
    fun domainIgnoresGapsAndRejectsNoFiniteData() {
        assertEquals(GraphDomain(2.0, 8.0), graphDomain(listOf(Double.NaN, 8.0, 2.0)))
        assertNull(graphDomain(listOf(Double.NaN, Double.POSITIVE_INFINITY)))
    }

    @Test
    fun onePointIsCenteredAndNearestSelectionIsClamped() {
        val point = graphPoint(0, 5.0, 1, 200f, 100f, GraphDomain(0.0, 10.0), 12f)
        assertEquals(100f, point.x)
        assertTrue(point.y in 12f..88f)
        assertEquals(0, nearestGraphIndex(-20f, 200f, 3))
        assertEquals(2, nearestGraphIndex(300f, 200f, 3))
    }

    @Test
    fun fixedDomainPreservesBarChartScale() {
        assertEquals(GraphDomain(0.0, 10.0), graphDomain(listOf(4.0), 0.0..10.0, listOf(20.0)))
    }

    @Test
    fun filteredHistoryKeepsHigherAndLowerRollingTrendsInsideThePlot() {
        listOf(
            listOf(60.0, 60.6) to listOf(100.0, 80.2),
            listOf(110.0, 111.0) to listOf(90.0, 100.0),
        ).forEach { (values, trends) ->
            val domain = checkNotNull(graphDomain(values, secondaryValues = trends + listOf(null, Double.NaN)))
            assertEquals((values + trends).minOrNull()!!, domain.minimum, 0.0)
            assertEquals((values + trends).maxOrNull()!!, domain.maximum, 0.0)
            (values + trends).forEachIndexed { index, value ->
                assertTrue(graphPoint(index, value, 4, 300f, 200f, domain, 12f).y in 12f..188f)
            }
        }
    }

    @Test
    fun dateRangesUseCalendarMonthsAndLocalDayBoundaries() {
        val today = LocalDate.of(2024, 3, 31)
        val zone = ZoneId.of("America/New_York")
        val range = progressDateRange(1, today, zone)
        assertEquals(LocalDate.of(2024, 2, 29).atStartOfDay(zone).toInstant().toEpochMilli(), range.first)
        assertEquals(today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1, range.last)
        assertEquals(today.minusYears(1).atStartOfDay(zone).toInstant().toEpochMilli(), progressDateRange(12, today, zone).first)
    }

    @Test
    fun customRangeIncludesBothDatesAcrossDaylightSaving() {
        val start = LocalDate.of(2026, 3, 8)
        val zone = ZoneId.of("America/New_York")
        val day = progressDateRange(start, start, zone)
        assertEquals(23 * 60 * 60 * 1000L, day.last - day.first + 1)
        val end = start.plusDays(2)
        val range = progressDateRange(start, end, zone)
        assertEquals(day.first, range.first)
        assertTrue(end.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli() in range)
        assertTrue(end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() !in range)
    }

    @Test(expected = IllegalArgumentException::class)
    fun customRangeRejectsEndBeforeBeginning() {
        val end = LocalDate.of(2026, 11, 16)
        progressDateRange(end.plusDays(1), end, ZoneId.of("UTC"))
    }

    @Test
    fun axisBucketsAreCalendarAlignedAndRespectLabelCapacity() {
        val start = LocalDate.of(2026, 10, 16)
        val month = graphDateTicks(start, start.plusMonths(1), 6)
        assertEquals(5, month.size)
        assertTrue(month.all { it.dayOfWeek.value == 1 && it in start..start.plusMonths(1) })
        val year = graphDateTicks(start.minusYears(1), start, 5)
        assertEquals(listOf(1, 4, 7, 10), year.map { it.monthValue })
        assertTrue(year.all { it.dayOfMonth == 1 })
        val narrow = graphDateTicks(start, start.plusMonths(6), 3)
        assertTrue(narrow.size in 2..3 && narrow.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun irregularSessionDatesUseElapsedTimeRatherThanEqualIndexSpacing() {
        val range = 0L..30L
        assertEquals(10f, graphDateX(1, range, 300f, 0f), 0.001f)
        assertEquals(300f, graphDateX(30, range, 300f, 0f), 0.001f)
        assertEquals(12f, graphDateX(-1, range, 300f, 12f), 0.001f)
        assertEquals(288f, graphDateX(50, range, 300f, 12f), 0.001f)
    }

    @Test
    fun exactDaySelectionDoesNotSnapToWeeklyDataAndIncludesRangeEdges() {
        val zone = ZoneId.of("America/New_York")
        val start = LocalDate.of(2026, 3, 7)
        val range = progressDateRange(start, start.plusDays(3), zone)
        val day = start.plusDays(1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val position = graphDateX(day, range, 400f, 0f)
        val selected = graphDateAtX(position, range, 400f, 0f)
        assertEquals(start.plusDays(1), java.time.Instant.ofEpochMilli(selected).atZone(zone).toLocalDate())
        assertEquals(range.first, graphDateAtX(-100f, range, 400f, 0f))
        assertEquals(range.last, graphDateAtX(500f, range, 400f, 0f))
    }
}

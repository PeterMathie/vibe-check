package com.petermathie.vibecheck.domain.tracker

import com.petermathie.vibecheck.data.local.TrackerDailyValueEntity
import com.petermathie.vibecheck.data.local.TrackerEntity
import com.petermathie.vibecheck.data.local.TrackerFieldEntity
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class HabitHeatmapTest {
    private val tracker = TrackerEntity("t", "Practice", false)
    private val field = TrackerFieldEntity("f", "t", "Minutes", "NUMBER", "min", null, null, 0)
    private val month = YearMonth.of(2026, 11)
    private val today = month.atDay(19)
    private fun value(day: Int, number: Double) =
        TrackerDailyValueEntity("f", month.atDay(day).toEpochDay(), number, null, null, "", 1)
    private fun heat(values: List<TrackerDailyValueEntity>, goal: TrackerFieldEntity = field, mode: String = "RELATIVE") =
        habitMonthHeatmap(tracker.copy(heatmapMode = mode), goal, values, month, today)

    @Test fun relativeScaleIgnoresOtherFieldsMonthsAndFutureAndKeepsRealZero() {
        val result = heat(listOf(value(1, 0.0), value(2, 10.0), value(3, 20.0), value(4, 40.0),
            value(20, 999.0), value(1, 999.0).copy(fieldId = "other"),
            value(1, 999.0).copy(epochDay = month.minusMonths(1).atDay(1).toEpochDay())))
        assertEquals(4, result.size)
        assertEquals(0f, result.getValue(value(1, 0.0).epochDay).intensity)
        assertEquals(0.5f, result.getValue(value(3, 0.0).epochDay).intensity)
        assertEquals(1f, result.getValue(value(4, 0.0).epochDay).intensity)
        assertFalse(result.containsKey(month.atDay(5).toEpochDay()))
    }

    @Test fun equalPositiveValuesAreStrongButEqualZerosAreLow() {
        listOf(listOf(value(1, 8.0)), listOf(value(1, 8.0), value(2, 8.0))).forEach {
            assertTrue(heat(it).values.all { day -> day.intensity == 1f })
        }
        assertTrue(heat(listOf(value(1, 0.0), value(2, 0.0))).values.all { it.intensity == 0f })
        assertTrue(heat(emptyList()).isEmpty())
    }

    @Test fun newNumericInputRejectsFractionalCountsAndAcceptsFiniteDecimals() {
        assertNotNull(habitNumericError("COUNT", 1.5))
        assertNull(habitNumericError("COUNT", 2.0))
        assertNull(habitNumericError("NUMBER", 1.5))
        assertNull(habitNumericError("NUMBER", Double.MAX_VALUE))
        assertNotNull(habitNumericError("NUMBER", Double.NaN))
        assertNotNull(habitNumericError("NUMBER", -1.0))
    }

    @Test fun goalsUseCurrentComparisonIncludingZeroAndFallBackWithoutGoal() {
        val values = listOf(value(1, 0.0), value(2, 20.0))
        val atMost = field.copy(targetComparison = "AT_MOST", targetValue = 10.0)
        assertEquals(true, heat(values, atMost, "GOAL").getValue(values[0].epochDay).goalMet)
        assertEquals(0f, heat(values, atMost, "GOAL").getValue(values[1].epochDay).intensity)
        assertEquals(true, heat(values, atMost.copy(targetValue = 30.0), "GOAL").getValue(values[1].epochDay).goalMet)
        assertEquals(heat(values), heat(values, mode = "GOAL"))
        listOf(
            field.copy(targetComparison = "AT_LEAST", targetValue = 20.0),
            field.copy(targetComparison = "EXACTLY", targetValue = 20.0),
            field.copy(targetComparison = "RANGE", targetValue = 10.0, targetMaxValue = 20.0),
        ).forEach { assertEquals(true, heat(values, it, "GOAL").getValue(values[1].epochDay).goalMet) }
    }

    @Test fun choiceSnapshotSurvivesEditsAndNoIsNeutral() {
        val row = value(1, 0.0).copy(numericValue = null, textValue = "Old name", choiceIntensity = "DARK")
        val choice = field.copy(valueType = "CHOICE", choiceOptions = "Other\nChoices")
        assertEquals(3, heat(listOf(row), choice).getValue(row.epochDay).level)
        val no = row.copy(textValue = null, choiceIntensity = null, booleanValue = false)
        assertEquals(0, heat(listOf(no), field.copy(valueType = "BOOLEAN")).getValue(no.epochDay).level)
    }

    @Test fun onlyFirstActiveMeasurementIsUsedAndCalendarIsMondayFirst() {
        val other = field.copy(id = "other", position = 1)
        val archived = field.copy(id = "archived", position = -1, isArchived = true)
        assertEquals(field, habitMeasurement(listOf(other, archived, field)))
        assertNull(habitMeasurement(listOf(archived)))
        val cells = habitMonthCells(YearMonth.of(2024, 2))
        assertEquals(42, cells.size)
        assertNull(cells[0])
        assertEquals(LocalDate.of(2024, 2, 1), cells[3])
        assertEquals(29, cells.count { it != null })
    }

    @Test fun currentWeekIncludesAdjacentMonthDatesButExpandedMonthDoesNot() {
        val thursday = LocalDate.of(2026, 10, 1)
        val week = habitWeekCells(thursday)
        assertEquals((0L..6L).map { LocalDate.of(2026, 9, 28).plusDays(it) }, week)
        assertEquals(week, habitWeekCells(week.first()))
        assertEquals(week, habitWeekCells(week.last()))
        assertTrue(habitMonthCells(YearMonth.from(thursday)).filterNotNull().all { it.month == thursday.month })
    }

    @Test fun weekIntensityIncludesPreviousMonthAndExcludesOtherWeeksAndFuture() {
        val thursday = LocalDate.of(2026, 10, 1)
        val monday = thursday.minusDays(3)
        fun entry(date: LocalDate, amount: Double) = value(1, amount).copy(epochDay = date.toEpochDay())
        val rows = listOf(
            entry(monday, 5.0), entry(thursday, 10.0),
            entry(monday.minusDays(1), 500.0), entry(thursday.plusDays(1), 999.0),
            entry(monday.plusDays(1), 999.0).copy(fieldId = "other"),
        )
        val result = habitWeekHeatmap(tracker, field, rows, thursday)
        assertEquals(setOf(monday.toEpochDay(), thursday.toEpochDay()), result.keys)
        assertEquals(0f, result.getValue(monday.toEpochDay()).intensity)
        assertEquals(1f, result.getValue(thursday.toEpochDay()).intensity)
    }

    @Test fun calendarStartsAtFirstRecordedMonthOfItsOwnMeasurement() {
        val first = month.minusMonths(3)
        val rows = listOf(
            value(1, 0.0).copy(epochDay = first.atDay(8).toEpochDay()),
            value(1, 10.0).copy(fieldId = "another", epochDay = first.minusMonths(1).atDay(1).toEpochDay()),
            value(20, 100.0),
        )
        assertEquals(first, firstHabitMonth(field.id, rows, today))
        assertEquals(month, firstHabitMonth(field.id, listOf(value(20, 100.0)), today))
        assertEquals(month, firstHabitMonth(field.id, emptyList(), today))
        val no = rows.first().copy(numericValue = null, booleanValue = false)
        assertEquals(first, firstHabitMonth(field.id, listOf(no), today))
    }
}

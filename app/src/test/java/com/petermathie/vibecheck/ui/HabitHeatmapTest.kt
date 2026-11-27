package com.petermathie.vibecheck.ui

import com.petermathie.vibecheck.data.local.TrackerDailyValueEntity
import com.petermathie.vibecheck.data.local.TrackerEntity
import com.petermathie.vibecheck.data.local.TrackerFieldEntity
import com.petermathie.vibecheck.domain.tracker.habitMonthHeatmap
import java.time.LocalDate
import java.time.YearMonth
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitHeatmapTest {
    private val tracker = TrackerEntity("tracker", "Mood", false)

    @Test
    fun browsingAnyPastMonthRetainsTheEditingDayAndFutureSelectionIsIgnored() {
        val today = LocalDate.of(2026, 11, 20)
        val state = HabitCalendarState("2026-11", today.toEpochDay())
        state.show(YearMonth.of(1926, 1))
        assertEquals("1926-01", state.monthText)
        assertEquals(today.toEpochDay(), state.selectedDay)
        val selected = LocalDate.of(1926, 1, 8)
        state.select(selected, today)
        state.show(YearMonth.from(today))
        assertEquals(selected.toEpochDay(), state.selectedDay)
        state.select(today.plusDays(1), today)
        assertEquals(selected.toEpochDay(), state.selectedDay)
        assertEquals("2026-11", state.monthText)
    }

    @Test
    fun weekSelectionUpdatesTheSameTargetAndMonthIndependentlyForEachHabit() {
        val today = LocalDate.of(2026, 10, 1)
        val first = HabitCalendarState("2026-10", today.toEpochDay())
        val second = HabitCalendarState("2026-10", today.toEpochDay())
        first.select(LocalDate.of(2026, 9, 28), today)
        assertEquals("2026-09", first.monthText)
        assertEquals(LocalDate.of(2026, 9, 28).toEpochDay(), first.selectedDay)
        assertEquals(today.toEpochDay(), second.selectedDay)
        assertEquals("2026-10", second.monthText)
    }

    @Test
    fun restoredCalendarAcceptsTheLegacyUnselectedAndWeekSelectionState() {
        val today = LocalDate.now().toEpochDay()
        val unselected = requireNotNull(HabitCalendarState.Saver.restore(listOf("2026-11", "", "")))
        assertEquals(today, unselected.selectedDay)
        val historical = LocalDate.of(2026, 10, 1).toEpochDay()
        val selected = requireNotNull(HabitCalendarState.Saver.restore(listOf("2026-11", "", historical.toString())))
        assertEquals(historical, selected.selectedDay)
        val current = requireNotNull(HabitCalendarState.Saver.restore(listOf("2026-10", historical.toString())))
        assertEquals(historical, current.selectedDay)
    }

    @Test
    fun displayDatesUseOrdinalsWithMonthAndYear() {
        mapOf(
            1 to "1st of November 2026",
            2 to "2nd of November 2026",
            3 to "3rd of November 2026",
            11 to "11th of November 2026",
            12 to "12th of November 2026",
            13 to "13th of November 2026",
            19 to "19th of November 2026",
            21 to "21st of November 2026",
            22 to "22nd of November 2026",
            23 to "23rd of November 2026",
        ).forEach { (day, expected) ->
            assertEquals(expected, habitCalendarDate(LocalDate.of(2026, 11, day), Locale.UK))
        }
        assertEquals("31st of October 2026", habitCalendarDate(LocalDate.of(2026, 10, 31), Locale.UK))
        assertEquals("19 novembre 2026", habitCalendarDate(LocalDate.of(2026, 11, 19), Locale.FRENCH))
    }

    @Test
    fun timestampDisplayRespectsTheViewingTimezone() {
        val time = Instant.parse("2026-10-16T00:30:00Z").toEpochMilli()
        assertEquals("15th of October 2026", formatDisplayDate(time, ZoneId.of("America/Los_Angeles"), Locale.UK))
        assertEquals("16th of October 2026", formatDisplayDate(time, ZoneId.of("UTC"), Locale.UK))
    }

    @Test
    fun historyWorkoutDateUsesShortMonthAndViewingDayWithoutChangingOtherDates() {
        val time = Instant.parse("2026-11-03T00:30:00Z").toEpochMilli()
        assertEquals("3rd Nov 2026", formatHistoryWorkoutDate(time, ZoneId.of("UTC"), Locale.UK))
        assertEquals("2nd Nov 2026", formatHistoryWorkoutDate(time, ZoneId.of("America/Los_Angeles"), Locale.UK))
        assertEquals("3rd of November 2026", formatDisplayDate(time, ZoneId.of("UTC"), Locale.UK))
        assertEquals("3 nov. 2026", formatHistoryWorkoutDate(time, ZoneId.of("UTC"), Locale.FRENCH))
    }

    @Test
    fun orderedChoicesMapFromLightToDark() {
        val field = field(
            "CHOICE",
            "Sad\nTired\nIrritated\nTense\nNeutral\nCalm\nAlert\nHappy\nExcited",
        ).copy(
            choiceLightThrough = 2,
            choiceDarkFrom = 6,
        )

        assertEquals(1, habitHeatmapLevel(tracker, listOf(field), listOf(value(text = "Sad"))))
        assertEquals(1, habitHeatmapLevel(tracker, listOf(field), listOf(value(text = "Irritated"))))
        assertEquals(2, habitHeatmapLevel(tracker, listOf(field), listOf(value(text = "Tense"))))
        assertEquals(2, habitHeatmapLevel(tracker, listOf(field), listOf(value(text = "Calm"))))
        assertEquals(3, habitHeatmapLevel(tracker, listOf(field), listOf(value(text = "Alert"))))
        assertEquals(3, habitHeatmapLevel(tracker, listOf(field), listOf(value(text = "Happy"))))
        assertEquals(3, habitHeatmapLevel(tracker, listOf(field), listOf(value(text = "Excited"))))
    }

    @Test
    fun yesNoAndWrittenEntriesHaveUsefulShades() {
        val boolean = field("BOOLEAN")
        val text = field("TEXT")

        assertEquals(0, habitHeatmapLevel(tracker, listOf(boolean), listOf(value(boolean = false))))
        assertEquals(3, habitHeatmapLevel(tracker, listOf(boolean), listOf(value(boolean = true))))
        assertEquals(2, habitHeatmapLevel(tracker, listOf(text), listOf(value(text = "Journal entry"))))
    }

    @Test
    fun loggedChoiceIntensitySurvivesOptionMoveOrDeletion() {
        val fieldAfterEdit = field("CHOICE", "Different")
        val historical = value(text = "Deleted option").copy(
            choiceOptionId = "field:choice:old",
            choiceIntensity = "DARK",
        )

        assertEquals(3, habitHeatmapLevel(tracker, listOf(fieldAfterEdit), listOf(historical)))
    }

    private fun field(type: String, options: String = "") = TrackerFieldEntity(
        id = "field",
        trackerId = tracker.id,
        name = "Value",
        valueType = type,
        unit = null,
        targetComparison = null,
        targetValue = null,
        position = 0,
        choiceOptions = options,
    )

    private fun value(
        text: String? = null,
        boolean: Boolean? = null,
    ) = TrackerDailyValueEntity("field", 1, null, boolean, text, "", 1)

    private fun habitHeatmapLevel(
        tracker: TrackerEntity,
        fields: List<TrackerFieldEntity>,
        values: List<TrackerDailyValueEntity>,
    ) = habitMonthHeatmap(tracker, fields.single(), values, YearMonth.of(1970, 1), LocalDate.of(1970, 1, 31))
        .getValue(1).level
}

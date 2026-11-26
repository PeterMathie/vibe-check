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

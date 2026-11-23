package com.petermathie.vibecheck.ui

import com.petermathie.vibecheck.data.local.TrackerDailyValueEntity
import com.petermathie.vibecheck.data.local.TrackerEntity
import com.petermathie.vibecheck.data.local.TrackerFieldEntity
import com.petermathie.vibecheck.domain.tracker.habitMonthHeatmap
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class HabitHeatmapTest {
    private val tracker = TrackerEntity("tracker", "Mood", false)

    @Test
    fun calendarDatesUseWeekdaysAndOrdinalsWithoutRepeatingTheYear() {
        mapOf(
            1 to "Sunday, 1st November",
            2 to "Monday, 2nd November",
            3 to "Tuesday, 3rd November",
            11 to "Wednesday, 11th November",
            12 to "Thursday, 12th November",
            13 to "Friday, 13th November",
            19 to "Thursday, 19th November",
            21 to "Saturday, 21st November",
            22 to "Sunday, 22nd November",
            23 to "Monday, 23rd November",
        ).forEach { (day, expected) ->
            assertEquals(expected, habitCalendarDate(LocalDate.of(2026, 11, day), Locale.UK))
        }
        assertEquals("Saturday, 31st October", habitCalendarDate(LocalDate.of(2026, 10, 31), Locale.UK))
        assertEquals("jeudi, 19 novembre", habitCalendarDate(LocalDate.of(2026, 11, 19), Locale.FRENCH))
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

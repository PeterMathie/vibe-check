package com.petermathie.vibecheck.ui

import com.petermathie.vibecheck.data.local.WorkoutEntity
import com.petermathie.vibecheck.data.local.WorkoutSetEntity
import com.petermathie.vibecheck.domain.progress.AggregateProgressPoint
import com.petermathie.vibecheck.ui.components.progressDateRange
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class ProgressDisplayTest {
    @Test
    fun incompleteWeeklyBucketIsVisibleWithoutChangingItsAverage() {
        val today = LocalDate.of(2026, 11, 26)
        val bucketEnd = today.plusDays(6).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val current = AggregateProgressPoint(bucketEnd, 113.79, 2)
        val range = progressDateRange(1, today, ZoneOffset.UTC)
        assertEquals(listOf(current.copy(date = range.last)), visibleAggregateProgress(listOf(current), range))
        assertEquals(bucketEnd, current.date)
    }

    @Test
    fun weeklyVisibilityIncludesLocalRangeEdgesButExcludesNonOverlappingWeeks() {
        val day = LocalDate.of(2026, 12, 2)
        val end = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val week = AggregateProgressPoint(end, 105.0, 3)
        val range = progressDateRange(day, day, ZoneId.of("America/New_York"))
        val previous = week.copy(date = day.minusWeeks(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val next = week.copy(date = day.plusWeeks(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        val outside = week.copy(date = day.plusWeeks(2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        assertEquals(listOf(week.copy(date = range.first), next.copy(date = range.last)),
            visibleAggregateProgress(listOf(previous, week, next, outside), range))
        assertTrue(visibleAggregateProgress(listOf(week), progressDateRange(day.plusDays(1), day.plusDays(1), ZoneOffset.UTC)).isEmpty())
    }

    @Test
    fun dayWorkoutsUseLocalFinishDateAndRetainSeparateSessions() {
        val zone = ZoneId.of("America/New_York")
        val day = LocalDate.of(2026, 11, 25)
        val start = day.minusDays(1).atTime(23, 30).atZone(zone).toInstant().toEpochMilli()
        val end = day.atTime(0, 30).atZone(zone).toInstant().toEpochMilli()
        val workout = WorkoutEntity("first", null, "Same name", "STRENGTH", "FINISHED", start, end, "", null, false)
        val later = workout.copy(id = "second", finishedAt = end + 1)
        assertEquals(listOf(workout, later), workoutsOnDay(listOf(later, workout, workout.copy(id = "draft", status = "DRAFT")), day.toEpochDay(), zone))
        assertTrue(workoutsOnDay(listOf(workout), day.minusDays(1).toEpochDay(), zone).isEmpty())
    }

    @Test
    fun snapshotDisplayRetainsBothSidesAndTimingAttemptsWithoutMutation() {
        val set = WorkoutSetEntity("set", "row", 1, "WARM_UP", "FAILED", null, 20.5, null, null,
            3.5, 4.0, 1500, 2000, 5.0, 10.0, 7.5, 90.0, "degrees", "Note", 0, 0,
            timeUnderTensionMillis = 5000, tutEntriesMillis = "[1500,2000]")
        val before = set.copy()
        val values = recordedSetValues(set).toMap()
        assertEquals("3.5", values["Left reps"])
        assertEquals("4", values["Right reps"])
        assertEquals("1.5 s", values["Left hold"])
        assertEquals("2 s", values["Right hold"])
        assertEquals("1.5 s + 2 s", values["Timing attempts"])
        assertEquals("7.5", values["RPE"])
        assertEquals("10 kg", values["Assistance"])
        assertEquals(before, set)
    }

}

package com.petermathie.vibecheck.domain.workout

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class HistoryTimingTest {
    private fun millis(value: String) = Instant.parse(value).toEpochMilli()

    @Test fun dateUsesFinishedDayAndPreservesOvernightDurationAndPrecision() {
        val start = millis("2026-11-26T23:50:12.345Z")
        val end = millis("2026-11-27T00:15:34.567Z")
        val result = historyWorkoutDateChange(start, end, LocalDate.of(2026, 12, 5), ZoneId.of("UTC"))
        assertEquals(millis("2026-12-04T23:50:12.345Z"), result.startedAt)
        assertEquals(millis("2026-12-05T00:15:34.567Z"), result.finishedAt)
        assertEquals(end - start, result.finishedAt!! - result.startedAt)
        assertEquals(result.startedAt - start, result.setDelta)
    }

    @Test fun clockChangesOnlyBoundaryAndRetainsSecondsAndMilliseconds() {
        val start = millis("2026-11-26T23:50:12.345Z")
        val end = millis("2026-11-27T00:15:34.567Z")
        val result = historyWorkoutTimeChange(start, end, false, LocalTime.of(0, 45), ZoneId.of("UTC"))
        assertEquals(start, result.startedAt)
        assertEquals(millis("2026-11-27T00:45:34.567Z"), result.finishedAt)
        assertEquals(0L, result.setDelta)
        assertTrue(runCatching {
            historyWorkoutTimeChange(start, end, true, LocalTime.of(23, 59), ZoneId.of("UTC"))
        }.isSuccess)
        assertTrue(runCatching {
            historyWorkoutTimeChange(start, end, false, LocalTime.of(23, 49), ZoneId.of("UTC"))
        }.isSuccess) // Boundary keeps its saved day; no inferred previous/next day.
        assertTrue(runCatching {
            historyWorkoutTimeChange(start, millis("2026-11-26T23:55:34.567Z"), false, LocalTime.of(23, 49), ZoneId.of("UTC"))
        }.isFailure)
    }

    @Test fun dateRejectsDstGapAndClockKeepsOriginalOverlapOffset() {
        val zone = ZoneId.of("America/New_York")
        assertTrue(runCatching {
            historyWorkoutDateChange(
                millis("2026-03-07T06:00:00Z"), millis("2026-03-07T07:30:12.345Z"),
                LocalDate.of(2026, 3, 8), zone,
            )
        }.exceptionOrNull()?.message?.contains("does not exist") == true)
        val start = millis("2026-11-01T06:30:12.345Z")
        val result = historyWorkoutTimeChange(start, millis("2026-11-01T08:00:00Z"), true, LocalTime.of(1, 45), zone)
        assertEquals(millis("2026-11-01T06:45:12.345Z"), result.startedAt)
    }

    @Test fun overlapWithNoOriginalValidOffsetRequiresUnambiguousChoice() {
        val zone = ZoneId.of("Pacific/Apia")
        val start = millis("2022-04-02T13:00:00Z")
        val end = millis("2022-04-02T14:30:12.345Z") // 03:30 at UTC+13.
        val failure = runCatching {
            historyWorkoutDateChange(start, end, LocalDate.of(2011, 4, 2), zone)
        }.exceptionOrNull()
        assertTrue(failure?.message?.contains("ambiguous") == true)
    }

    @Test fun missingFinishIsNotSynthesizedAndNegativeShiftIsRejected() {
        val result = historyWorkoutDateChange(1234L, null, LocalDate.of(1970, 1, 2), ZoneId.of("UTC"))
        assertNull(result.finishedAt)
        assertEquals(86_401_234L, result.startedAt)
        assertTrue(runCatching {
            historyWorkoutTimeChange(1234L, null, false, LocalTime.NOON, ZoneId.of("UTC"))
        }.isFailure)
        assertTrue(runCatching {
            historyWorkoutDateChange(1234L, 2000L, LocalDate.of(1969, 12, 31), ZoneId.of("UTC"))
        }.isFailure)
    }
}

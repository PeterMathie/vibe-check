package com.petermathie.vibecheck.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal data class ProgressDateRange(
    val start: LocalDate,
    val endInclusive: LocalDate,
) {
    init {
        require(!start.isAfter(endInclusive)) { "Start date must be on or before end date" }
    }

    fun containsEpochDay(epochDay: Long): Boolean =
        epochDay in start.toEpochDay()..endInclusive.toEpochDay()

    fun containsTimestamp(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): Boolean =
        containsEpochDay(Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDate().toEpochDay())
}

internal fun homeHeatmapDates(today: LocalDate): List<LocalDate> =
    (34L downTo 0L).map(today::minusDays)

internal fun validProgressDateRange(start: LocalDate, endInclusive: LocalDate): ProgressDateRange? =
    if (start.isAfter(endInclusive)) null else ProgressDateRange(start, endInclusive)

internal fun historicalHabitDate(requested: LocalDate, today: LocalDate): LocalDate =
    requested.coerceAtMost(today)

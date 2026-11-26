package com.petermathie.vibecheck.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun formatDisplayDate(date: LocalDate, locale: Locale = Locale.getDefault()): String {
    val day = date.dayOfMonth
    val pattern = if (locale.language == Locale.ENGLISH.language) {
        val suffix = dayOrdinalSuffix(day)
        "d'$suffix of' MMMM yyyy"
    } else {
        "d MMMM yyyy"
    }
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

internal fun dayOrdinalSuffix(day: Int): String = when {
    day in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}

internal fun formatDisplayDate(
    timestamp: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String = formatDisplayDate(Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate(), locale)

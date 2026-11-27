package com.petermathie.vibecheck.domain.workout

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

data class HistoryTimingChange(val startedAt: Long, val finishedAt: Long?, val setDelta: Long = 0)

private fun resolveHistoryTime(local: LocalDateTime, zone: ZoneId, originalOffset: ZoneOffset): Long {
    val offsets = zone.rules.getValidOffsets(local)
    require(offsets.isNotEmpty()) { "That local time does not exist because of daylight saving time" }
    val offset = if (offsets.size == 1) offsets.single() else {
        require(originalOffset in offsets) { "That local time is ambiguous; choose an unambiguous time" }
        originalOffset
    }
    return local.toInstant(offset).toEpochMilli()
}

private fun HistoryTimingChange.validated(): HistoryTimingChange = apply {
    require(startedAt >= 0 && (finishedAt == null || finishedAt >= 0)) { "Workout time cannot be negative" }
    require(finishedAt == null || finishedAt >= startedAt) { "Workout finish precedes start" }
}

fun historyWorkoutDateChange(
    startedAt: Long, finishedAt: Long?, date: LocalDate, zone: ZoneId,
): HistoryTimingChange {
    val anchor = finishedAt ?: startedAt
    val original = Instant.ofEpochMilli(anchor).atZone(zone)
    val changed = resolveHistoryTime(date.atTime(original.toLocalTime()), zone, original.offset)
    val delta = Math.subtractExact(changed, anchor)
    return HistoryTimingChange(
        Math.addExact(startedAt, delta), finishedAt?.let { Math.addExact(it, delta) }, delta,
    ).validated()
}

fun historyWorkoutTimeChange(
    startedAt: Long, finishedAt: Long?, start: Boolean, time: LocalTime, zone: ZoneId,
): HistoryTimingChange {
    val timestamp = if (start) startedAt else requireNotNull(finishedAt) { "Workout has no saved finish time" }
    val original = Instant.ofEpochMilli(timestamp).atZone(zone)
    val preciseTime = time.withSecond(original.second).withNano(original.nano)
    val changed = resolveHistoryTime(original.toLocalDate().atTime(preciseTime), zone, original.offset)
    return HistoryTimingChange(
        if (start) changed else startedAt, if (start) finishedAt else changed,
    ).validated()
}

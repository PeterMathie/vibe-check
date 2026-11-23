package com.petermathie.vibecheck.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth

internal fun homeCalendarCells(month: YearMonth): List<LocalDate?> {
    val leadingCells = month.atDay(1).dayOfWeek.value - 1
    return List(42) { index ->
        val day = index - leadingCells + 1
        if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
    }
}

internal fun Modifier.homeCalendarGestures(
    cells: List<LocalDate?>,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onOpen: (LocalDate) -> Unit,
): Modifier = composed {
    val select by rememberUpdatedState(onSelect)
    val open by rememberUpdatedState(onOpen)
    val configuration = LocalViewConfiguration.current
    val direction = LocalLayoutDirection.current
    pointerInput(cells, today, direction, configuration) {
        val gap = 4.dp.toPx()
        fun dayAt(position: Offset): LocalDate? {
            val cellWidth = (size.width - gap * 6) / 7f
            val cellHeight = (size.height - gap * 5) / 6f
            if (position.x < 0 || position.y < 0 || position.x >= size.width || position.y >= size.height) return null
            val column = (position.x / (cellWidth + gap)).toInt()
            val row = (position.y / (cellHeight + gap)).toInt()
            if (position.x - column * (cellWidth + gap) > cellWidth ||
                position.y - row * (cellHeight + gap) > cellHeight) return null
            val weekday = if (direction == LayoutDirection.Rtl) 6 - column else column
            return cells.getOrNull(row * 7 + weekday)?.takeUnless { it > today }
        }
        var previousTap: Pair<LocalDate, Long>? = null
        awaitEachGesture {
            // Preview on press; leave native cell activation available to keyboards and accessibility.
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            val previous = previousTap
            previousTap = null
            val first = dayAt(down.position) ?: return@awaitEachGesture
            down.consume()
            select(first)
            var selected = first
            var dragged = false
            var up: PointerInputChange? = null
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) break
                dragged = dragged || (change.position - down.position).getDistance() > configuration.touchSlop ||
                    event.changes.count { it.pressed } > 1
                if (dragged) {
                    dayAt(change.position)?.takeUnless { it == selected }?.let {
                        selected = it
                        select(it)
                    }
                }
                if (change.changedToUpIgnoreConsumed()) up = change
                change.consume()
            } while (up == null)
            if (up != null && !dragged && up.uptimeMillis - down.uptimeMillis < configuration.longPressTimeoutMillis) {
                if (previous?.first == first &&
                    down.uptimeMillis - previous.second in configuration.doubleTapMinTimeMillis..configuration.doubleTapTimeoutMillis) {
                    open(first)
                } else {
                    previousTap = first to up.uptimeMillis
                }
            }
        }
    }
}

internal fun homeCalendarIntensities(
    counts: Map<Long, Int>,
    month: YearMonth,
    today: LocalDate,
): Map<Long, Float> {
    val firstDay = month.atDay(1).toEpochDay()
    val lastDay = minOf(month.atEndOfMonth(), today).toEpochDay()
    val visibleCounts = counts.filterKeys { it in firstDay..lastDay }
    val maximum = visibleCounts.values.maxOrNull() ?: 0
    return visibleCounts.mapValues { (_, count) ->
        if (maximum == 0) 0f else count.toFloat() / maximum
    }
}

internal data class FreshnessMonthRange(
    val month: YearMonth,
    val firstDate: LocalDate,
    val lastDate: LocalDate,
) {
    val dayCount: Int = lastDate.dayOfMonth
}

internal fun freshnessMonthRange(
    requestedMonth: YearMonth,
    today: LocalDate,
): FreshnessMonthRange {
    val currentMonth = YearMonth.from(today)
    val month = requestedMonth.coerceAtMost(currentMonth)
    return FreshnessMonthRange(
        month = month,
        firstDate = month.atDay(1),
        lastDate = if (month == currentMonth) today else month.atEndOfMonth(),
    )
}

internal fun freshnessDateInMonth(
    selectedDate: LocalDate,
    requestedMonth: YearMonth,
    today: LocalDate,
): LocalDate {
    val range = freshnessMonthRange(requestedMonth, today)
    return range.month.atDay(selectedDate.dayOfMonth.coerceAtMost(range.dayCount))
}

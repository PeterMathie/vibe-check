package com.petermathie.vibecheck.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.domain.tracker.*
import com.petermathie.vibecheck.domain.workout.formatQuantitative
import com.petermathie.vibecheck.ui.theme.*
import com.petermathie.vibecheck.ui.components.DateValueReadout
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

internal class HabitCalendarState(initialMonth: String, initialDay: Long? = null, initialWeekDay: Long? = null) {
    var monthText by mutableStateOf(initialMonth)
        private set
    var selectedDay by mutableStateOf(initialDay)
    var selectedWeekDay by mutableStateOf(initialWeekDay)

    fun show(month: YearMonth) {
        if (monthText != month.toString()) {
            monthText = month.toString()
            selectedDay = null
        }
    }

    companion object {
        val Saver = listSaver<HabitCalendarState, String>(
            save = { listOf(it.monthText, it.selectedDay?.toString().orEmpty(), it.selectedWeekDay?.toString().orEmpty()) },
            restore = { HabitCalendarState(
                it[0], it[1].takeIf(String::isNotEmpty)?.toLong(), it[2].takeIf(String::isNotEmpty)?.toLong(),
            ) },
        )
    }
}

@Composable
internal fun HabitMonthHeatmap(
    tracker: TrackerEntity,
    field: TrackerFieldEntity?,
    values: List<TrackerDailyValueEntity>,
    today: LocalDate,
    state: HabitCalendarState,
    expanded: Boolean,
    onChooseMeasurement: () -> Unit,
) {
    val currentMonth = YearMonth.from(today)
    val firstMonth = remember(field?.id, values, today) { firstHabitMonth(field?.id, values, today) }
    val month = YearMonth.parse(state.monthText).coerceIn(firstMonth, currentMonth)
    LaunchedEffect(firstMonth, currentMonth) { state.show(month) }
    fun moveMonth(delta: Int) {
        state.show(when {
            delta < 0 && month > firstMonth -> month.minusMonths(1)
            delta > 0 && month < currentMonth -> month.plusMonths(1)
            else -> month
        })
    }
    val days = remember(tracker, field, values, month, today, expanded) {
        if (expanded) habitMonthHeatmap(tracker, field, values, month, today)
        else habitWeekHeatmap(tracker, field, values, today)
    }
    val palette = LocalVibePalette.current
    val colour = Color(tracker.colourArgb.toInt())
    val shades = palette.habitHeatmapColors(colour)
    val cells = if (expanded) habitMonthCells(month) else habitWeekCells(today)
    val selectedDay = if (expanded) state.selectedDay else state.selectedWeekDay
    Column(
        Modifier.fillMaxWidth().testTag("Habit calendar ${tracker.id}")
            .animateContentSize(if (LocalVibeReducedMotion.current) snap() else tween(LocalVibeMotion.current.expandMillis))
            .pointerInput(month, firstMonth, currentMonth, expanded) {
                var drag = 0f
                if (expanded) detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
                    onDragEnd = { if (drag != 0f) moveMonth(if (drag < 0f) -1 else 1) },
                )
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (expanded) CalendarMonthHeader(month, firstMonth, currentMonth, tracker.name, ::moveMonth)
        CalendarHeatmapGrid(
            cells = cells,
            today = today,
            selectedDay = selectedDay,
            colorForDay = { date ->
                val day = days[date.toEpochDay()]
                day?.intensity?.let { interpolateFreshnessColor(shades.low, shades.strong, it) }
                    ?: shades.forLevel(day?.level ?: 0)
            },
            descriptionForDay = { date -> habitDayDescription(date, days[date.toEpochDay()], field) },
            onDayClick = { date ->
                if (expanded) state.selectedDay = date.toEpochDay() else state.selectedWeekDay = date.toEpochDay()
            },
        )
        if (field == null) {
            TextButton(onClick = onChooseMeasurement) { Text("Configure a measurement") }
        }
        key(expanded, month, selectedDay) {
            DateValueReadout(
                date = selectedDay?.takeIf { epoch -> cells.any { it?.toEpochDay() == epoch } }?.let(LocalDate::ofEpochDay),
                value = if (selectedDay == null) "" else habitDayEntry(days[selectedDay], field),
                modifier = Modifier.testTag("Habit entry ${tracker.id}"),
                dateModifier = Modifier.testTag("Habit entry date ${tracker.id}"),
                valueModifier = Modifier.testTag("Habit entry value ${tracker.id}"),
            )
        }
    }
}

internal fun habitCalendarDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
    formatDisplayDate(date, locale)

private fun habitDayDescription(date: LocalDate, day: HabitHeatmapDay?, field: TrackerFieldEntity?): String {
    val entry = habitDayEntry(day, field)
    val goal = day?.goalMet?.let { if (it) ", goal met" else ", goal not met" }.orEmpty()
    return "${formatDisplayDate(date)}: $entry$goal"
}

private fun habitDayEntry(day: HabitHeatmapDay?, field: TrackerFieldEntity?): String {
    val value = day?.value
    return when {
        value == null -> "No entry"
        value.numericValue != null -> "${formatQuantitative(value.numericValue)}${field?.unit?.let { " $it" }.orEmpty()}"
        value.booleanValue != null -> if (value.booleanValue) "Yes" else "No"
        else -> value.textValue.orEmpty()
    }
}

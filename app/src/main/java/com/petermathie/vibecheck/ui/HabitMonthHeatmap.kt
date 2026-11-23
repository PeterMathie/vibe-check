package com.petermathie.vibecheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.domain.tracker.*
import com.petermathie.vibecheck.domain.workout.formatQuantitative
import com.petermathie.vibecheck.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
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
    val monthLabel = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    val cellHeight = 22.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val cells = if (expanded) habitMonthCells(month) else habitWeekCells(today)
    val selectedDay = if (expanded) state.selectedDay else state.selectedWeekDay
    val readoutStyle = MaterialTheme.typography.bodySmall
    val readoutHeight = with(LocalDensity.current) { readoutStyle.lineHeight.toDp() * 2 }
    val locale = Locale.getDefault()
    val dateStyle = readoutStyle.copy(fontFamily = FontFamily.Monospace)
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
        if (expanded) Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { moveMonth(-1) },
                enabled = month > firstMonth,
                modifier = Modifier.semantics { contentDescription = "Previous month for ${tracker.name}" },
            ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null) }
            Text(
                monthLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            IconButton(
                onClick = { moveMonth(1) },
                enabled = month < currentMonth,
                modifier = Modifier.semantics { contentDescription = "Next month for ${tracker.name}" },
            ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Mon", "Tue", "Wed", "Thur", "Fri", "Sat", "Sun").forEach { label ->
                Text(
                    label, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = palette.textSecondary,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f).height(cellHeight))
                    } else {
                        val epoch = date.toEpochDay()
                        val day = days[epoch]
                        val future = date > today
                        val fill = day?.intensity?.let { interpolateFreshnessColor(shades.low, shades.strong, it) }
                            ?: shades.forLevel(day?.level ?: 0)
                        val description = habitDayDescription(date, day, field)
                        val selected = (selectedDay ?: today.toEpochDay()) == epoch
                        val outline = if (selected || date == today) Modifier.border(
                            if (selected) 3.dp else 1.dp,
                            if (selected) palette.background else palette.heatmapOutlineColor(fill),
                            RoundedCornerShape(6.dp),
                        ) else Modifier
                        Box(
                            Modifier.weight(1f).height(cellHeight)
                                .background(fill.copy(alpha = (if (future) 0.32f else fill.alpha) * 0.8f), RoundedCornerShape(6.dp))
                                .then(outline)
                                .clickable(enabled = !future, onClickLabel = "View entry") {
                                    if (expanded) state.selectedDay = epoch else state.selectedWeekDay = epoch
                                }
                                .semantics { contentDescription = description; this.selected = selected },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium,
                                color = palette.heatmapOutlineColor(fill).copy(alpha = if (future) 0.35f else 1f),
                            )
                        }
                    }
                }
            }
        }
        if (field == null) {
            TextButton(onClick = onChooseMeasurement) { Text("Configure a measurement") }
        }
        Box(Modifier.fillMaxWidth().height(readoutHeight).testTag("Habit entry ${tracker.id}")) {
            selectedDay?.takeIf { epoch -> cells.any { it?.toEpochDay() == epoch } }?.let { epoch ->
                key(expanded, month, epoch) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val maximumValueWidth = maxWidth / 2
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                habitCalendarDate(LocalDate.ofEpochDay(epoch), locale),
                                Modifier.weight(1f).fillMaxHeight().testTag("Habit entry date ${tracker.id}"),
                                style = dateStyle, color = palette.textSecondary, textAlign = TextAlign.Start,
                            )
                            Text(
                                habitDayEntry(days[epoch], field),
                                Modifier.widthIn(max = maximumValueWidth).fillMaxHeight()
                                    .verticalScroll(rememberScrollState()).testTag("Habit entry value ${tracker.id}"),
                                style = readoutStyle, color = palette.textSecondary, textAlign = TextAlign.End,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun habitCalendarDate(date: LocalDate, locale: Locale = Locale.getDefault()): String {
    val day = date.dayOfMonth
    val suffix = if (locale.language == Locale.ENGLISH.language) {
        when {
            day in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
    } else ""
    val pattern = if (suffix.isEmpty()) "EEEE, d MMMM" else "EEEE, d'$suffix' MMMM"
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

private fun habitDayDescription(date: LocalDate, day: HabitHeatmapDay?, field: TrackerFieldEntity?): String {
    val entry = habitDayEntry(day, field)
    val goal = day?.goalMet?.let { if (it) ", goal met" else ", goal not met" }.orEmpty()
    return "${date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault()))}: $entry$goal"
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

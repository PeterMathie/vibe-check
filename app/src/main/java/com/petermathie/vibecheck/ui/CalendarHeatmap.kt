package com.petermathie.vibecheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.domain.model.ActivityDay
import com.petermathie.vibecheck.domain.tracker.habitMonthCells
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.habitHeatmapColors
import com.petermathie.vibecheck.ui.theme.heatmapOutlineColor
import com.petermathie.vibecheck.ui.components.DateValueReadout
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ActivityHeatmap(
    days: List<ActivityDay>,
    onDayClick: (Long) -> Unit,
    activityColor: Color? = null,
    itemLabel: String = "activities",
    compact: Boolean = false,
    today: LocalDate = LocalDate.now(),
    modifier: Modifier = Modifier,
    selection: MutableState<Long?>? = null,
    reserveEmptyReadoutSpace: Boolean = true,
    opaqueCells: Boolean = false,
    singularItemLabel: String? = null,
    onEntryClick: ((Long) -> Unit)? = null,
) {
    val palette = LocalVibePalette.current
    val shades = palette.habitHeatmapColors(activityColor)
    val counts = days.associate { it.epochDay to it.activityCount }
    fun countLabel(count: Int) = "$count ${if (count == 1) singularItemLabel ?: itemLabel else itemLabel}"
    val currentMonth = YearMonth.from(today)
    val firstDay = days.filter { it.epochDay <= today.toEpochDay() && it.activityCount > 0 }
        .minOfOrNull { it.epochDay } ?: today.toEpochDay()
    val firstMonth = YearMonth.from(LocalDate.ofEpochDay(firstDay))
    var monthText by rememberSaveable { mutableStateOf(currentMonth.toString()) }
    val ownSelection = rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedDay by (selection ?: ownSelection)
    val month = YearMonth.parse(monthText).coerceIn(firstMonth, currentMonth)
    val cells = remember(month) { habitMonthCells(month) }
    LaunchedEffect(firstMonth, currentMonth) {
        if (monthText != month.toString()) {
            monthText = month.toString()
            selectedDay = null
        }
    }
    LaunchedEffect(selection?.value) {
        selection?.value?.let { day ->
            val selectedMonth = YearMonth.from(LocalDate.ofEpochDay(day))
            if (selectedMonth in firstMonth..currentMonth) monthText = selectedMonth.toString()
        }
    }
    fun moveMonth(delta: Int) {
        val next = month.plusMonths(delta.toLong()).coerceIn(firstMonth, currentMonth)
        if (next != month) {
            monthText = next.toString()
            selectedDay = null
        }
    }
    Column(
        modifier.fillMaxWidth().semantics { contentDescription = "$itemLabel calendar" }
            .pointerInput(month, firstMonth, currentMonth) {
                var drag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
                    onDragEnd = { if (drag != 0f) moveMonth(if (drag < 0f) -1 else 1) },
                )
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CalendarMonthHeader(month, firstMonth, currentMonth, itemLabel, ::moveMonth)
        CalendarHeatmapGrid(
            cells = cells,
            today = today,
            selectedDay = selectedDay,
            compact = compact,
            opaqueCells = opaqueCells,
            colorForDay = { date -> shades.forLevel((counts[date.toEpochDay()] ?: 0).coerceAtMost(3)) },
            descriptionForDay = { date ->
                "${formatDisplayDate(date)}: ${countLabel(counts[date.toEpochDay()] ?: 0)}" + if (date > today) ", future date" else ""
            },
            onDayClick = { date ->
                selectedDay = date.toEpochDay()
                onDayClick(date.toEpochDay())
            },
        )
        if (reserveEmptyReadoutSpace || selectedDay != null) key(month, selectedDay) {
            val entryDay = selectedDay?.takeIf { epoch -> cells.any { it?.toEpochDay() == epoch } }
            val entryCount = counts[entryDay] ?: 0
            DateValueReadout(
                date = entryDay?.let(LocalDate::ofEpochDay),
                value = if (entryDay == null) "" else countLabel(entryCount),
                modifier = Modifier.semantics { contentDescription = "$itemLabel entry" },
                valueModifier = if (entryDay != null && entryCount > 0 && onEntryClick != null) {
                    Modifier.clickable(onClickLabel = "View $itemLabel") { onEntryClick(entryDay) }
                } else Modifier,
                valueColor = if (entryDay != null && entryCount > 0 && onEntryClick != null) palette.accent else palette.textSecondary,
            )
        }
    }
}

@Composable
internal fun CalendarMonthHeader(
    month: YearMonth,
    firstMonth: YearMonth?,
    currentMonth: YearMonth?,
    label: String,
    onMoveMonth: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onMoveMonth(-1) },
            enabled = firstMonth == null || month > firstMonth,
            modifier = Modifier.semantics { contentDescription = "Previous month for $label" },
        ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, null) }
        Text(
            month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
            Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
        )
        IconButton(
            onClick = { onMoveMonth(1) },
            enabled = currentMonth == null || month < currentMonth,
            modifier = Modifier.semantics { contentDescription = "Next month for $label" },
        ) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) }
    }
}

@Composable
internal fun CalendarHeatmapGrid(
    cells: List<LocalDate?>,
    today: LocalDate,
    selectedDay: Long?,
    colorForDay: (LocalDate) -> Color,
    descriptionForDay: (LocalDate) -> String,
    onDayClick: (LocalDate) -> Unit,
    compact: Boolean = false,
    opaqueCells: Boolean = false,
    allowFutureDates: Boolean = false,
) {
    val palette = LocalVibePalette.current
    val cellHeight = (if (compact) 14.dp else 22.dp) * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        val future = date > today && !allowFutureDates
                        val fill = colorForDay(date)
                        val selected = (selectedDay ?: today.toEpochDay()) == date.toEpochDay()
                        val outline = if (selected || date == today) Modifier.border(
                            if (selected) 3.dp else 1.dp,
                            if (selected) palette.background else palette.heatmapOutlineColor(fill),
                            RoundedCornerShape(6.dp),
                        ) else Modifier
                        Box(
                            Modifier.weight(1f).height(cellHeight)
                                .background(fill.copy(alpha = (if (future) 0.32f else fill.alpha) * if (opaqueCells) 1f else 0.8f), RoundedCornerShape(6.dp))
                                .then(outline)
                                .clickable(enabled = !future, onClickLabel = "View entry") { onDayClick(date) }
                                .semantics { contentDescription = descriptionForDay(date); this.selected = selected },
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
    }
}

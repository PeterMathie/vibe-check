package com.petermathie.vibecheck.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProgressCustomRangeDialog(
    start: LocalDate,
    end: LocalDate,
    onDismiss: () -> Unit,
    onApply: (LocalDate, LocalDate) -> Unit,
) {
    var beginning by remember(start, end) { mutableStateOf(start) }
    var ending by remember(start, end) { mutableStateOf(end) }
    var field by remember { mutableStateOf<String?>(null) }
    var choosingMonth by remember(field) { mutableStateOf(false) }
    val state = field?.let { target ->
        key(target) {
            rememberDatePickerState(
                initialSelectedDateMillis = (if (target == "Beginning") beginning else ending)
                    .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
        }
    }
    val selected = state?.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    fun returnToRange() { field = null }
    HabitGlassDialog(
        title = field ?: "Custom date range",
        onDismiss = onDismiss,
        onBack = if (field == null) null else ::returnToRange,
        actions = {
            TextButton(onClick = { if (field == null) onDismiss() else returnToRange() }) { Text("Cancel") }
            TextButton(
                enabled = if (field == null) beginning <= ending else selected != null,
                onClick = {
                    if (field == null) onApply(beginning, ending)
                    else {
                        if (field == "Beginning") beginning = checkNotNull(selected) else ending = checkNotNull(selected)
                        returnToRange()
                    }
                },
            ) { Text("Apply") }
        },
    ) {
        if (state == null) {
            listOf("Beginning" to beginning, "End" to ending).forEach { (label, date) ->
                Row(
                    Modifier.fillMaxWidth().clickable(onClickLabel = "Choose $label date") { field = label }
                        .padding(vertical = 8.dp).semantics { contentDescription = "Choose $label date" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.labelLarge)
                        Text(formatDisplayDate(date), style = MaterialTheme.typography.bodyLarge)
                    }
                    Icon(Icons.Outlined.CalendarMonth, null)
                }
            }
            if (beginning > ending) Text("End must be on or after Beginning.", color = MaterialTheme.colorScheme.error)
        } else {
            Text("Selected: ${selected?.let(::formatDisplayDate) ?: "Choose a date"}")
            TextButton(onClick = { choosingMonth = !choosingMonth }) {
                Text(if (choosingMonth) "Back to days" else "Choose month")
            }
            if (choosingMonth) {
                val displayed = Instant.ofEpochMilli(state.displayedMonthMillis).atZone(ZoneOffset.UTC).toLocalDate()
                Text(displayed.year.toString(), style = MaterialTheme.typography.titleMedium)
                (1..12).toList().chunked(3).forEach { months ->
                    Row(Modifier.fillMaxWidth()) {
                        months.forEach { month ->
                            TextButton(
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    state.displayedMonthMillis = displayed.withDayOfMonth(1).withMonth(month)
                                        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                                    choosingMonth = false
                                },
                            ) {
                                Text(displayed.withMonth(month).format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())))
                            }
                        }
                    }
                }
            } else {
                DatePicker(
                    state = state,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "$field calendar" },
                    title = null,
                    headline = null,
                    showModeToggle = false,
                )
            }
        }
    }
}

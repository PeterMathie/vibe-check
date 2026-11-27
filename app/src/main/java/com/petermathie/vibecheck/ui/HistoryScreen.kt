package com.petermathie.vibecheck.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.WorkoutEntity
import com.petermathie.vibecheck.ui.components.VibeStatePanel
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

internal fun historyWorkoutTitle(workout: WorkoutEntity): String =
    workout.programmeNameSnapshot?.takeIf(String::isNotBlank)
        ?: workout.name.takeIf(String::isNotBlank) ?: "Recorded workout"

internal fun historyDateFilterError(start: LocalDate?, end: LocalDate?): String? = when {
    end != null && start == null -> "Choose Start before End."
    start != null && end != null && end < start -> "End must be on or after Start."
    else -> null
}

internal fun historyDateMatches(
    workout: WorkoutEntity,
    start: LocalDate?,
    end: LocalDate?,
    zone: ZoneId = ZoneId.systemDefault(),
): Boolean {
    if (historyDateFilterError(start, end) != null) return false
    if (start == null) return true
    val date = Instant.ofEpochMilli(workout.finishedAt ?: workout.startedAt).atZone(zone).toLocalDate()
    return date >= start && date <= (end ?: start)
}

@Composable
fun HistoryScreen(vm: EditorViewModel, onBack: () -> Unit = {}) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val exerciseRows by vm.workoutExercises.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var startDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var endDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var calendar by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<WorkoutEntity?>(null) }
    var removingBusy by remember { mutableStateOf(false) }
    var removalError by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun openWorkout(id: String) {
        focusManager.clearFocus()
        keyboard?.hide()
        selected = id
    }
    val listState = rememberLazyListState()
    val start = startDay?.let(LocalDate::ofEpochDay)
    val end = endDay?.let(LocalDate::ofEpochDay)
    val filterError = historyDateFilterError(start, end)
    val finished = workouts.filter { it.status == "FINISHED" }
    val matches = finished.filter { workout ->
        historyDateMatches(workout, start, end) &&
            (workout.name.contains(query, true) || historyWorkoutTitle(workout).contains(query, true) ||
                exerciseRows.any { row ->
                    row.workoutId == workout.id &&
                        (row.notes.contains(query, true) || row.exerciseName.contains(query, true) ||
                            exercises.any { it.id == row.actualExerciseId && it.canonicalName.contains(query, true) })
                })
    }
    if (selected != null) {
        WorkoutEditor(
            vm, selected, { selected = null }, {},
            onDoneEditing = { selected = null },
            onDeleted = { selected = null },
            historyContext = true,
        )
        return
    }
    val palette = LocalVibePalette.current
    HabitGlassDialogHost(constrainToContent = true) { backdrop, _ ->
        ScreenList(modifier = backdrop, listState = listState) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    VibeBackChevron(onBack)
                    Text("History", style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditField("Keyword search", query) { query = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HistoryDateFilter(
                            "Start", start, onOpen = { calendar = "Start" },
                            onClear = { startDay = null; endDay = null },
                            modifier = Modifier.weight(1f),
                        )
                        HistoryDateFilter(
                            "End", end, enabled = start != null, onOpen = { calendar = "End" },
                            onClear = { endDay = null }, modifier = Modifier.weight(1f),
                        )
                    }
                    filterError?.let { Text(
                        it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    ) }
                }
            }
            if (matches.isEmpty()) item {
                VibeStatePanel(if (finished.isEmpty()) "Finished workouts will appear here."
                    else "No workouts match these filters.")
            }
            itemsIndexed(matches, key = { _, it -> it.id }) { index, workout ->
                val title = historyWorkoutTitle(workout)
                Column(Modifier.fillMaxWidth().habitGlow(palette.accent, palette.isDark)) {
                    if (index > 0) HorizontalDivider(color = palette.border.copy(alpha = 0.3f))
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title, style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                                .pointerInput(workout.id) { detectTapGestures(onDoubleTap = { openWorkout(workout.id) }) }
                                .semantics {
                                    role = Role.Button
                                    onClick("Edit $title") { openWorkout(workout.id); true }
                                }
                                .onKeyEvent {
                                    if (it.type == KeyEventType.KeyUp && it.key in listOf(Key.Enter, Key.Spacebar)) {
                                        openWorkout(workout.id)
                                        true
                                    } else false
                                }.focusable(),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            formatDisplayDate(workout.finishedAt ?: workout.startedAt),
                            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End,
                            maxLines = 1,
                        )
                        Spacer(Modifier.width(12.dp))
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            IconButton(
                                onClick = { removalError = null; removing = workout },
                                modifier = Modifier.size(28.dp),
                            ) { Icon(Icons.Outlined.Close, "Remove $title", Modifier.size(16.dp)) }
                        }
                    }
                }
            }
        }
        calendar?.let { field ->
            CalendarDateDialog(field, if (field == "Start") start else end ?: start, { calendar = null }) { date ->
                if (field == "Start") startDay = date.toEpochDay() else endDay = date.toEpochDay()
                calendar = null
            }
        }
        removing?.let { workout ->
            HabitGlassDialog(
                title = "Remove workout?",
                onDismiss = { if (!removingBusy) removing = null },
                actions = {
                    TextButton(onClick = { removing = null }, enabled = !removingBusy) { Text("Cancel") }
                    TextButton(
                        enabled = !removingBusy,
                        onClick = {
                            removingBusy = true
                            vm.discardHistoryWorkout(
                                workout.id,
                                onSaved = { removingBusy = false; removing = null },
                                onFailure = { removingBusy = false; removalError = it },
                            )
                        },
                    ) { Text("Remove") }
                },
            ) {
                Text("Remove “${historyWorkoutTitle(workout)}” from History?")
                removalError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun HistoryDateFilter(
    title: String,
    date: LocalDate?,
    onOpen: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(modifier) {
        TextButton(
            onClick = onOpen, enabled = enabled, modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 0.dp, end = if (date == null) 0.dp else 28.dp),
        ) {
            Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(date?.let { "$title: ${formatDisplayDate(it)}" } ?: title)
        }
        if (date != null) CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            IconButton(
                onClick = onClear, modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
            ) { Icon(Icons.Outlined.Close, "Clear $title date", Modifier.size(12.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CalendarDateDialog(
    title: String,
    initialDate: LocalDate?,
    onDismiss: () -> Unit,
    busy: Boolean = false,
    error: String? = null,
    onApply: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    val date = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    HabitGlassDialog(
        title = title, onDismiss = { if (!busy) onDismiss() },
        actions = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
            TextButton(onClick = { date?.let(onApply) }, enabled = date != null && !busy) { Text("Apply") }
        },
    ) {
        DatePicker(state, title = null, headline = null, showModeToggle = false)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

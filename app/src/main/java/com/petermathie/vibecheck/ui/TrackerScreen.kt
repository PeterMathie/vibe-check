package com.petermathie.vibecheck.ui

import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.domain.tracker.habitMeasurement
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

internal const val COLLAPSED_HABITS = "collapsedHabits"
private const val HABIT_TITLE_CHARACTERS_PER_LINE = 18

internal fun habitHeadingLabel(name: String): String = buildString {
    var remaining = name
    // One extra line lets Text ellipsize without formatting the entire saved name.
    repeat(3) {
        if (remaining.isNotEmpty()) {
            val end = remaining.offsetByCodePoints(
                0, minOf(HABIT_TITLE_CHARACTERS_PER_LINE, remaining.codePointCount(0, remaining.length)),
            )
            val split = if (end < remaining.length) {
                remaining.lastIndexOf(' ', end).takeIf { it > 0 } ?: end
            } else end
            val splitWord = split == end && end < remaining.length &&
                !remaining[end].isWhitespace() && remaining[end - 1] != '-'
            val boundary = if (splitWord) {
                remaining.offsetByCodePoints(0, HABIT_TITLE_CHARACTERS_PER_LINE - 1)
            } else split
            if (isNotEmpty()) append('\n')
            append(remaining.substring(0, boundary))
            if (splitWord) append('-')
            remaining = remaining.substring(boundary).trimStart()
        }
    }
}

internal fun Modifier.habitGlow(
    colour: Color,
    dark: Boolean,
    origin: Offset = Offset(0.5f, 0.5f),
): Modifier = drawWithCache {
    require(origin.x > 0f && origin.x < 1f && origin.y > 0f && origin.y < 1f)
    val halo = Brush.radialGradient(
        listOf(colour.copy(alpha = if (dark) 0.09f else 0.05f), colour.copy(alpha = 0f)),
        center = Offset.Zero, radius = 1f,
    )
    val centre = Offset(size.width * origin.x, size.height * origin.y)
    val quadrants = listOf(
        Rect(0f, 0f, centre.x, centre.y),
        Rect(centre.x, 0f, size.width, centre.y),
        Rect(0f, centre.y, centre.x, size.height),
        Rect(centre.x, centre.y, size.width, size.height),
    )
    onDrawBehind {
        quadrants.forEach { quadrant ->
            clipRect(quadrant.left, quadrant.top, quadrant.right, quadrant.bottom) {
                withTransform({
                    translate(centre.x, centre.y)
                    scale(quadrant.width, quadrant.height, pivot = Offset.Zero)
                }) {
                    drawCircle(halo, radius = 1f, center = Offset.Zero)
                }
            }
        }
    }
}

@Composable
internal fun HabitHeadingAndEntry(
    heading: @Composable () -> Unit,
    entry: @Composable () -> Unit,
) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            Box { heading() }
            Box(propagateMinConstraints = true) { entry() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = 12.dp.roundToPx().coerceAtMost(constraints.maxWidth)
        val minimumEntryWidth = minOf(
            (128.dp.toPx() * fontScale).toInt(),
            ((constraints.maxWidth - gap) * 0.4f).toInt(),
        )
        val title = measurables[0].measure(
            loose.copy(maxWidth = constraints.maxWidth - gap - minimumEntryWidth),
        )
        val entryWidth = constraints.maxWidth - title.width - gap
        val input = measurables[1].measure(loose.copy(minWidth = entryWidth, maxWidth = entryWidth))
        val height = maxOf(title.height, input.height)
        layout(constraints.maxWidth, height) {
            title.placeRelative(0, (height - title.height) / 2)
            input.placeRelative(constraints.maxWidth - input.width, (height - input.height) / 2)
        }
    }
}

@Composable
fun TrackerScreen(
    vm: EditorViewModel,
    preferences: SharedPreferences = LocalContext.current.getSharedPreferences("settings", 0),
) {
    val data by vm.habitEditorData.collectAsStateWithLifecycle()
    val haptics = rememberVibeHaptics()
    val palette = LocalVibePalette.current
    var collapsedIds by remember(preferences) {
        mutableStateOf(preferences.getStringSet(COLLAPSED_HABITS, emptySet()).orEmpty().toSet())
    }
    val today = LocalDate.now()
    val epoch = today.toEpochDay()
    var editor by remember { mutableStateOf<HabitEditorSeed?>(null) }
    var deleteCandidate by remember { mutableStateOf<TrackerEntity?>(null) }
    val trackers = data?.trackers.orEmpty()
    val fields = data?.fields.orEmpty()
    val values = data?.values.orEmpty()
    val active = trackers.filterNot { it.isArchived }.sortedBy { it.position }
    val archived = trackers.filter { it.isArchived }.sortedBy { it.name }
    fun edit(tracker: TrackerEntity) {
        haptics.perform(VibeHapticEvent.EDIT)
        editor = HabitEditorSeed(tracker, fields.filter { it.trackerId == tracker.id }, creating = false)
    }
    val order = rememberReorderState(active.map { it.id }) { key, from, to -> vm.moveTracker(key as String, to - from) }
    fun create() {
        haptics.perform(VibeHapticEvent.SELECTION)
        val tracker = TrackerEntity(newId(), "", false)
        editor = HabitEditorSeed(
            tracker,
            listOf(TrackerFieldEntity(newId(), tracker.id, "", "BOOLEAN", null, null, null, 0)),
            creating = true,
        )
    }
    HabitGlassDialogHost { backdrop, overlay ->
        Box(Modifier.fillMaxSize().then(backdrop)) {
            ScreenList {
                item { Text("Habits", style = MaterialTheme.typography.headlineSmall) }
                if (data == null) {
                    item { CircularProgressIndicator(Modifier.size(24.dp)) }
                } else if (active.isEmpty()) {
                    item { Text("Create a habit to record today's entry.", color = palette.textSecondary) }
                }
                itemsIndexed(order.ordered(active) { it.id }, key = { _, tracker -> tracker.id }) { index, tracker ->
                    val trackerFields = fields.filter { it.trackerId == tracker.id }
                    val calendarState = rememberSaveable(tracker.id, saver = HabitCalendarState.Saver) {
                        HabitCalendarState(YearMonth.from(today).toString())
                    }
                    val expanded = tracker.id !in collapsedIds
                    val glowOrigin = remember(tracker.id) {
                        val random = Random(tracker.id.hashCode())
                        Offset(0.2f + random.nextFloat() * 0.6f, 0.2f + random.nextFloat() * 0.6f)
                    }
                    ReorderItem(order, tracker.id, index, Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.fillMaxWidth()
                                .padding(top = if (index > 0) 8.dp else 0.dp)
                                .habitGlow(Color(tracker.colourArgb.toInt()), palette.isDark, glowOrigin),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (index > 0) HorizontalDivider(color = palette.border.copy(alpha = 0.3f))
                            val field = habitMeasurement(trackerFields)
                            HabitHeadingAndEntry(
                                heading = {
                                    ReorderHandle(
                                        order, tracker.id, tracker.name, enabled = active.size > 1, longPress = true,
                                    ) {
                                        Row(
                                            Modifier.heightIn(min = 48.dp)
                                                .clickable(onClickLabel = if (expanded) "Collapse to this week" else "Expand to full month") {
                                                    val updated = if (expanded) collapsedIds + tracker.id else collapsedIds - tracker.id
                                                    preferences.edit().putStringSet(COLLAPSED_HABITS, updated).apply()
                                                    collapsedIds = updated
                                                }
                                                .semantics { stateDescription = if (expanded) "Month shown" else "This week shown" },
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            IconButton(onClick = { edit(tracker) }) {
                                                Icon(
                                                    HabitIconCatalog.icon(tracker.iconName), "Edit ${tracker.name} settings",
                                                    tint = Color(tracker.colourArgb.toInt()), modifier = Modifier.size(28.dp),
                                                )
                                            }
                                            Text(
                                                remember(tracker.name) { habitHeadingLabel(tracker.name) },
                                                Modifier.weight(1f, fill = false)
                                                    .semantics { contentDescription = tracker.name },
                                                style = MaterialTheme.typography.titleLarge.copy(
                                                    lineBreak = LineBreak.Heading,
                                                    hyphens = Hyphens.Auto,
                                                ),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                },
                            ) {
                                if (field != null) {
                                    HabitDailyInput(
                                        vm, field, values.find { it.fieldId == field.id && it.epochDay == epoch },
                                        epoch, Color(tracker.colourArgb.toInt()),
                                    )
                                }
                            }
                            HabitMonthHeatmap(
                                tracker, field, values, today, calendarState, expanded,
                                onChooseMeasurement = { edit(tracker) },
                            )
                        }

                    }
                }
                if (archived.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HorizontalDivider(color = palette.border.copy(alpha = 0.3f))
                        Text("Archived habits", style = MaterialTheme.typography.titleMedium, color = palette.textSecondary)
                        archived.forEach { tracker ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(tracker.name, Modifier.weight(1f))
                                TextButton(onClick = { vm.save(tracker.copy(isArchived = false)) }) { Text("Restore") }
                                val ids = fields.filter { it.trackerId == tracker.id }.map { it.id }.toSet()
                                if (values.none { it.fieldId in ids }) {
                                    TextButton(onClick = { deleteCandidate = tracker }) { Text("Delete") }
                                }
                            }
                        }
                    }
                }
                if (data != null) item(key = "add-habit") {
                    TextButton(
                        onClick = ::create,
                        enabled = !overlay && editor == null && deleteCandidate == null,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Create new habit" },
                    ) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add habit")
                    }
                }
            }
        }
        editor?.let { seed ->
            HabitEditorDialog(
                seed, onDismiss = { editor = null },
                onSave = { tracker, updatedFields, saved, failed ->
                    vm.saveHabitEditor(tracker, updatedFields, seed.creating, saved) {
                        failed(vm.error.value ?: "Could not save habit")
                    }
                },
            )
        }
        deleteCandidate?.let { tracker ->
            var deleting by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf<String?>(null) }
            HabitGlassDialog(
                title = "Delete ${tracker.name}?",
                onDismiss = { if (!deleting) deleteCandidate = null },
                actions = {
                    TextButton(onClick = { deleteCandidate = null }, enabled = !deleting) { Text("Cancel") }
                    TextButton(onClick = {
                        deleting = true
                        vm.deleteTrackerIfEmpty(
                            tracker.id, onSaved = { deleteCandidate = null },
                            onFailure = { deleting = false; error = vm.error.value },
                        )
                    }, enabled = !deleting) { Text("Delete permanently") }
                },
            ) {
                Text("This empty habit and its settings will be permanently deleted.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

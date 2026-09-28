package com.petermathie.vibecheck.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.domain.tracker.HabitFieldForm
import java.time.LocalDate

@Composable
fun TrackerScreen(vm: EditorViewModel) {
    val haptics = rememberVibeHaptics()
    val trackers by vm.trackers.collectAsStateWithLifecycle()
    val fields by vm.fields.collectAsStateWithLifecycle()
    val values by vm.values.collectAsStateWithLifecycle()
    val epoch = LocalDate.now().toEpochDay()
    var newHabit by remember { mutableStateOf<TrackerEntity?>(null) }
    var settings by remember { mutableStateOf<TrackerEntity?>(null) }
    var field by remember { mutableStateOf<TrackerFieldEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<TrackerEntity?>(null) }
    val activeTrackers = trackers.filterNot { it.isArchived }.sortedBy { it.position }
    val archivedTrackers = trackers.filter { it.isArchived }.sortedBy { it.name }
    fun hasData(tracker: TrackerEntity): Boolean {
        val fieldIds = fields.filter { it.trackerId == tracker.id }.map { it.id }.toSet()
        return values.any { it.fieldId in fieldIds }
    }
    val trackerOrder = rememberReorderState(activeTrackers.map { it.id }) { key, from, to ->
        vm.moveTracker(key as String, to - from)
    }
    RegisterAppFabAction(
        owner = Destination.HABITS,
        destination = AppFabDestination.NewHabit,
        visible = newHabit == null && settings == null && field == null && deleteCandidate == null,
    ) {
        newHabit = TrackerEntity(newId(), "", false)
    }
    ScreenList {
        item {
            Text("Habits", style = MaterialTheme.typography.headlineSmall)
        }
        if (activeTrackers.isEmpty()) {
            item { com.petermathie.vibecheck.ui.components.VibeStatePanel("Create a habit to record today's entry.") }
        }
        itemsIndexed(
            trackerOrder.ordered(activeTrackers) { it.id },
            key = { _, tracker -> tracker.id },
        ) { trackerIndex, tracker ->
            val trackerFields = fields.filter { it.trackerId == tracker.id }
            val activeFields = trackerFields.filterNot { it.isArchived }.sortedBy { it.position }
            ReorderItem(trackerOrder, tracker.id, trackerIndex, Modifier.fillMaxWidth()) {
                VibeCard {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(
                        HabitIconCatalog.icon(tracker.iconName),
                        contentDescription = null,
                        tint = Color(tracker.colourArgb.toInt()),
                        modifier = Modifier.size(32.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(tracker.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            haptics.perform(VibeHapticEvent.EDIT)
                            settings = tracker
                        },
                        modifier = Modifier.semantics {
                            contentDescription = "Edit ${tracker.name} settings"
                        },
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null)
                    }
                    if (activeTrackers.size > 1) {
                        ReorderHandle(trackerOrder, tracker.id, tracker.name)
                    }
                }
                activeFields.forEach { habitField ->
                    HabitDailyInput(
                        vm,
                        habitField,
                        values.find { it.fieldId == habitField.id && it.epochDay == epoch },
                        epoch,
                        Color(tracker.colourArgb.toInt()),
                    )
                }
            }
            }
        }
        if (archivedTrackers.isNotEmpty()) {
            item {
                VibeCard {
                    Text("Archived habits", style = MaterialTheme.typography.titleLarge)
                    archivedTrackers.forEach { tracker ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Icon(
                                HabitIconCatalog.icon(tracker.iconName),
                                contentDescription = null,
                                tint = Color(tracker.colourArgb.toInt()),
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .background(Color(tracker.colourArgb.toInt()), CircleShape),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(tracker.name, Modifier.weight(1f))
                            TextButton(onClick = { vm.save(tracker.copy(isArchived = false)) }) {
                                Text("Restore")
                            }
                            if (!hasData(tracker)) {
                                TextButton(onClick = { deleteCandidate = tracker }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }
    }
    newHabit?.let { tracker ->
        NameDialog("Habit name", tracker.name, { newHabit = null }) {
            vm.createTracker(tracker.copy(name = it))
            newHabit = null
        }
    }
    settings?.let { tracker ->
        HabitSettingsDialog(
            tracker = tracker,
            fields = fields.filter { it.trackerId == tracker.id },
            onDismiss = { settings = null },
            onSave = { updatedTracker, updatedFields ->
                vm.save(updatedTracker)
                updatedFields.forEach(vm::save)
                settings = null
            },
            onAddMeasurement = { activeCount ->
                field = TrackerFieldEntity(
                    newId(),
                    tracker.id,
                    "",
                    "NUMBER",
                    null,
                    null,
                    null,
                    activeCount,
                )
                settings = null
            },
            onEditMeasurement = {
                field = it
                settings = null
            },
            onRestoreMeasurement = { restored, position ->
                vm.save(restored.copy(isArchived = false, position = position))
            },
            onMoveMeasurement = vm::moveTrackerField,
            onArchiveHabit = {
                vm.save(tracker.copy(isArchived = true))
                settings = null
            },
        )
    }
    field?.let { habitField ->
        HabitFieldDialog(
            vm,
            habitField,
            Color(trackers.find { it.id == habitField.trackerId }?.colourArgb?.toInt() ?: 0xFF4CAF50.toInt()),
        ) { field = null }
    }
    deleteCandidate?.let { tracker ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete ${tracker.name}?") },
            text = { Text("This empty habit and its settings will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.deleteTrackerIfEmpty(tracker.id)
                        deleteCandidate = null
                    },
                ) { Text("Delete permanently") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HabitSettingsDialog(
    tracker: TrackerEntity,
    fields: List<TrackerFieldEntity>,
    onDismiss: () -> Unit,
    onSave: (TrackerEntity, List<TrackerFieldEntity>) -> Unit,
    onAddMeasurement: (Int) -> Unit,
    onEditMeasurement: (TrackerFieldEntity) -> Unit,
    onRestoreMeasurement: (TrackerFieldEntity, Int) -> Unit,
    onMoveMeasurement: (String, Int) -> Unit,
    onArchiveHabit: () -> Unit,
) {
    val haptics = rememberVibeHaptics()
    var name by remember(tracker.id) { mutableStateOf(tracker.name) }
    var colour by remember(tracker.id) { mutableLongStateOf(tracker.colourArgb) }
    var iconName by remember(tracker.id) { mutableStateOf(tracker.iconName) }
    var iconsOpen by remember(tracker.id) { mutableStateOf(false) }
    var coloursOpen by remember(tracker.id) { mutableStateOf(false) }
    var lightBelow by remember(tracker.id, tracker.heatmapLightBelow) {
        mutableStateOf(formatThreshold(tracker.heatmapLightBelow))
    }
    var mediumBelow by remember(tracker.id, tracker.heatmapMediumBelow) {
        mutableStateOf(formatThreshold(tracker.heatmapMediumBelow))
    }
    val light = lightBelow.toDoubleOrNull()
    val medium = mediumBelow.toDoubleOrNull()
    val valid = light != null && medium != null && light >= 0.0 && medium > light
    val activeFields = fields.filterNot { it.isArchived }.sortedBy { it.position }
    val choiceForms = remember(tracker.id) {
        mutableStateMapOf<String, HabitFieldForm>().apply {
            activeFields.filter { it.valueType == HabitFieldForm.CHOICE }.forEach {
                put(it.id, resolvedHabitFieldForm(it))
            }
        }
    }
    val hasNumericField = activeFields.any { it.valueType == "NUMBER" }
    val archivedFields = fields.filter { it.isArchived }
    val activeFieldKeys = remember(activeFields.map { it.id }) { activeFields.map { it.id } }
    val fieldOrder = rememberReorderState(activeFieldKeys) { key, from, to ->
        onMoveMeasurement(key as String, to - from)
    }
    val unit = activeFields.firstOrNull { it.valueType == "NUMBER" }?.unit
    val suffix = unit?.let { " ($it)" }.orEmpty()
    val settingsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val reorderContext = rememberReorderScrollContext(settingsListState)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${tracker.name} settings") },
        text = {
            androidx.compose.runtime.CompositionLocalProvider(LocalReorderScrollContext provides reorderContext) {
                ReorderOverlayHost {
                    LazyColumn(
                        state = settingsListState,
                        modifier = Modifier.reorderScrollViewport(reorderContext),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                    item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        EditField("Habit name", name) { name = it }
                        OutlinedButton(
                            onClick = { coloursOpen = !coloursOpen },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Box(Modifier.size(20.dp).background(Color(colour.toInt()), CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(if (coloursOpen) "Hide colours" else "Change colour")
                        }
                        if (coloursOpen) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                HABIT_COLOURS.forEachIndexed { index, option ->
                                    Box(
                                        Modifier
                                            .size(if (colour == option) 38.dp else 34.dp)
                                            .background(Color(option.toInt()), CircleShape)
                                            .semantics {
                                                contentDescription = "Set ${tracker.name} colour ${index + 1}"
                                            }
                                            .clickable { colour = option },
                                    )
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { iconsOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(
                                HabitIconCatalog.icon(iconName),
                                contentDescription = null,
                                tint = Color(colour.toInt()),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Choose icon")
                        }
                        if (hasNumericField) {
                            Text("Shade thresholds", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = lightBelow,
                                    onValueChange = { lightBelow = it },
                                    label = { Text("Light below$suffix") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = mediumBelow,
                                    onValueChange = { mediumBelow = it },
                                    label = { Text("Dark from$suffix") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Text(
                                if (valid) {
                                    "Values from ${formatThreshold(light!!)} to under ${formatThreshold(medium!!)}${unit?.let { " $it" }.orEmpty()} use the medium shade."
                                } else {
                                    "Dark from must be greater than Light below."
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Text("Measurements", style = MaterialTheme.typography.titleMedium)
                        fieldOrder.ordered(activeFields) { it.id }.forEachIndexed { fieldIndex, habitField ->
                            ReorderItem(fieldOrder, habitField.id, fieldIndex, Modifier.fillMaxWidth()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        if (activeFields.size > 1) {
                                            ReorderHandle(fieldOrder, habitField.id, habitField.name)
                                        }
                                        Text(habitTypeLabel(habitField.valueType), modifier = Modifier.weight(1f))
                                        if (habitField.valueType != HabitFieldForm.CHOICE) {
                                            TextButton(onClick = {
                                                haptics.perform(VibeHapticEvent.EDIT)
                                                onEditMeasurement(habitField)
                                            }) { Text("Edit") }
                                        }
                                    }
                                    choiceForms[habitField.id]?.let { form ->
                                        ChoiceScaleEditor(
                                            fieldKey = habitField.id,
                                            form = form,
                                            habitColour = Color(colour.toInt()),
                                        ) {
                                            choiceForms[habitField.id] = it
                                        }
                                    }
                                }
                            }
                        }
                        VibeActionButton(
                            "Add measurement",
                            { onAddMeasurement(activeFields.size) },
                            modifier = Modifier.fillMaxWidth(),
                            importance = ActionImportance.SECONDARY,
                        )
                        if (archivedFields.isNotEmpty()) {
                            Text("Archived measurements", style = MaterialTheme.typography.labelLarge)
                            archivedFields.forEach { archived ->
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text(archived.name, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { onRestoreMeasurement(archived, activeFields.size) }) {
                                        Text("Restore")
                                    }
                                }
                            }
                        }
                    }
                    }
                }
                }
            }
        },
        confirmButton = {
            Box(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onArchiveHabit,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterStart),
                ) { Text("Archive") }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                ) { Text("Cancel") }
                TextButton(
                    enabled = name.isNotBlank() &&
                        (!hasNumericField || valid) &&
                        choiceForms.values.all { it.canSave },
                    modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd),
                    onClick = {
                        onSave(
                            tracker.copy(
                                name = name,
                                colourArgb = colour,
                                iconName = iconName,
                                heatmapLightBelow = light ?: tracker.heatmapLightBelow,
                                heatmapMediumBelow = medium ?: tracker.heatmapMediumBelow,
                            ),
                            choiceForms.mapNotNull { (id, form) ->
                                activeFields.find { it.id == id }?.let(form::applyTo)
                            },
                        )
                    },
                ) { Text("Save") }
            }
        },
    )
    if (iconsOpen) {
        AlertDialog(
            onDismissRequest = { iconsOpen = false },
            title = { Text("Choose icon") },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier.heightIn(max = 520.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(HabitIconCatalog.options, key = { it.key }) { option ->
                        Surface(
                            onClick = {
                                iconName = option.key
                                iconsOpen = false
                            },
                            color = if (option.key == iconName) {
                                Color(colour.toInt()).copy(alpha = 0.24f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .semantics {
                                    contentDescription = "Set ${tracker.name} icon ${option.label}"
                                },
                        ) {
                            Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                                Icon(
                                    option.icon,
                                    contentDescription = null,
                                    tint = Color(colour.toInt()),
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { iconsOpen = false }) { Text("Close") }
            },
        )
    }
}

private fun formatThreshold(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

private val HABIT_COLOURS = listOf(
    0xFF26A69AL,
    0xFF42A5F5L,
    0xFF7E57C2L,
    0xFFEC407AL,
    0xFFEF5350L,
    0xFFFFA726L,
    0xFF66BB6AL,
)

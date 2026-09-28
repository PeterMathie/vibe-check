package com.petermathie.vibecheck.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import com.petermathie.vibecheck.ui.components.VibeDivider
import com.petermathie.vibecheck.ui.components.VibeSurface
import com.petermathie.vibecheck.ui.theme.VibeSurfaceLevel
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.prescriptionSummary
import com.petermathie.vibecheck.ui.theme.LocalVibeReducedMotion

@Composable
fun ProgrammeEditor(
    vm: EditorViewModel,
    mode: TrainingMode,
    onModeChange: (TrainingMode) -> Unit,
    onStart: (String, Boolean) -> Unit,
) {
    val programmes by vm.programmes.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedProgrammeId by rememberSaveable { mutableStateOf<String?>(null) }
    var rename by remember { mutableStateOf<ProgrammeEntity?>(null) }
    var addExerciseDayId by rememberSaveable { mutableStateOf<String?>(null) }
    var addExerciseActivityTag by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAddDayChoice by rememberSaveable { mutableStateOf(false) }
    var pendingAddTypeDayId by rememberSaveable { mutableStateOf<String?>(null) }
    var editPrescription by remember { mutableStateOf<ProgrammeExerciseEntity?>(null) }
    var pendingStartDayId by rememberSaveable { mutableStateOf<String?>(null) }
    val programmeRows = programmes.filter { it.mode == mode.name }.sortedBy { it.position }
    val selectedProgramme = programmes.find { it.id == selected }
    var generatedEmptyDayId by rememberSaveable(selectedProgramme?.id) { mutableStateOf<String?>(null) }
    var programmeName by rememberSaveable(selectedProgramme?.id, selectedProgramme?.name) {
        mutableStateOf(selectedProgramme?.name.orEmpty())
    }
    val dayRows = days.filter { it.programmeId == selected }.sortedBy { it.position }
    val programmeOrder = rememberReorderState(programmeRows.map { it.id }) { key, from, to ->
        vm.moveProgramme(key as String, to - from)
    }
    val dayOrder = rememberReorderState(dayRows.map { it.id }) { key, from, to ->
        vm.moveDay(key as String, to - from)
    }
    val reducedMotion = LocalVibeReducedMotion.current
    val haptics = rememberVibeHaptics()
    val activeWorkout = vm.workouts.collectAsStateWithLifecycle().value.find { it.status == "DRAFT" }
    fun requestStart(dayId: String) {
        if (activeWorkout != null && activeWorkout.programmeDayId != dayId) pendingStartDayId = dayId
        else onStart(dayId, false)
    }
    val availableAddTypes = exercises
        .filterNot { it.isArchived }
        .map { it.tag }
        .filter { it in setOf(TrainingMode.STRENGTH.name, TrainingMode.STRETCHING.name) }
        .toSet()
    fun chooseAddType(dayId: String) {
        if (availableAddTypes.size > 1) {
            pendingAddTypeDayId = dayId
        } else {
            addExerciseDayId = dayId
            addExerciseActivityTag = availableAddTypes.singleOrNull()
        }
    }
    fun requestAddProgrammeItem() {
        when (dayRows.size) {
            0 -> generatedEmptyDayId?.let(::chooseAddType) ?: selectedProgramme?.let { programme ->
                val dayId = newId()
                generatedEmptyDayId = dayId
                val day = ProgrammeDayEntity(
                    id = dayId,
                    programmeId = programme.id,
                    name = if (programme.mode == TrainingMode.STRETCHING.name) "Stretching" else "Workout",
                    position = 0,
                )
                vm.save(day) { chooseAddType(day.id) }
            }
            1 -> chooseAddType(dayRows.single().id)
            else -> pendingAddDayChoice = true
        }
    }
    val programmeModalOpen = rename != null ||
        addExerciseDayId != null ||
        pendingAddDayChoice ||
        pendingAddTypeDayId != null ||
        editPrescription != null ||
        pendingStartDayId != null
    if (selected == null) {
        RegisterAppFabAction(
            owner = Destination.PROGRAMMES,
            destination = AppFabDestination.NewProgramme,
            visible = !programmeModalOpen,
        ) {
            rename = ProgrammeEntity(newId(), "", mode.name, false)
        }
    } else {
        RegisterAppFabAction(
            owner = Destination.PROGRAMMES,
            destination = AppFabDestination.AddProgrammeItem,
            enabled = availableAddTypes.isNotEmpty(),
            visible = !programmeModalOpen && availableAddTypes.isNotEmpty(),
            onClick = ::requestAddProgrammeItem,
        )
    }
    BackHandler(selected != null) { selected = null }
    ScreenList {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                if (selected != null) {
                    IconButton(onClick = { selected = null }) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "Back")
                    }
                }
                Text(
                    if (selected == null) "Programmes" else "Edit programme",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (selected == null) ModeSelector(mode, onModeChange, Modifier.weight(1.45f))
            }
        }
        if (selected == null) {
            if (programmeRows.isEmpty()) {
                item { com.petermathie.vibecheck.ui.components.VibeStatePanel("Create a programme to organize strength or stretch sessions.") }
            }
            itemsIndexed(programmeOrder.ordered(programmeRows) { it.id }, key = { _, row -> row.id }) { index, p ->
                val expanded = expandedProgrammeId == p.id
                val previewDays = days.filter { it.programmeId == p.id }.sortedBy { it.position }
                ReorderItem(
                    state = programmeOrder,
                    itemKey = p.id,
                    index = index,
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                ) {
                    VibeSurface(VibeSurfaceLevel.CARD, Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            if (programmeRows.size > 1) ReorderHandle(programmeOrder, p.id, p.name)
                            Text(
                                p.name,
                                Modifier
                                    .weight(1f)
                                    .clickable {
                                        expandedProgrammeId = if (expanded) null else p.id
                                    }
                                    .semantics {
                                        stateDescription = if (expanded) "Exercises shown" else "Exercises hidden"
                                    },
                                style = MaterialTheme.typography.titleLarge,
                            )
                            IconButton(onClick = {
                                haptics.perform(VibeHapticEvent.EDIT)
                                selected = p.id
                            }) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Edit programme ${p.name}")
                            }
                            IconButton(
                                onClick = { previewDays.firstOrNull()?.let { requestStart(it.id) } },
                                enabled = previewDays.isNotEmpty(),
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = "Start ${p.name}")
                            }
                        }
                        AnimatedVisibility(
                            visible = expanded,
                            enter = if (reducedMotion) EnterTransition.None else expandVertically() + fadeIn(),
                            exit = if (reducedMotion) ExitTransition.None else shrinkVertically() + fadeOut(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                VibeDivider()
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 60.dp, top = 12.dp, end = 16.dp, bottom = 16.dp)
                                        .semantics { contentDescription = "Read-only exercises for ${p.name}" },
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    previewDays.forEach { day ->
                                        if (previewDays.size > 1) {
                                            Text(day.name, style = MaterialTheme.typography.titleMedium)
                                        }
                                        val previewEntries = entries
                                            .filter { it.programmeDayId == day.id }
                                            .sortedBy { it.position }
                                        if (previewEntries.isEmpty()) {
                                            Text("No exercises yet", style = MaterialTheme.typography.bodyMedium)
                                        } else {
                                            previewEntries.forEach { entry ->
                                                val exerciseName = exercises
                                                    .find { it.id == entry.exerciseId }
                                                    ?.canonicalName
                                                    .orEmpty()
                                                Column {
                                                    Text(exerciseName, style = MaterialTheme.typography.bodyLarge)
                                                    Text(prescriptionSummary(entry), style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            item {
                OutlinedTextField(
                    value = programmeName,
                    onValueChange = { programmeName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Programme name") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = { selectedProgramme?.let { vm.save(it.copy(name = programmeName.trim())) } },
                            enabled = programmeName.isNotBlank() && programmeName.trim() != selectedProgramme?.name,
                        ) { Icon(Icons.Outlined.Check, "Save programme name") }
                    },
                )
            }
            itemsIndexed(dayOrder.ordered(dayRows) { it.id }, key = { _, row -> row.id }) { dayIndex, d ->
                val dayEntries = entries.filter { it.programmeDayId == d.id }.sortedBy { it.position }
                val entryOrder = rememberReorderState(dayEntries.map { it.id }) { key, from, to ->
                    vm.moveEntry(key as String, to - from)
                }
                ReorderItem(dayOrder, d.id, dayIndex, Modifier.fillMaxWidth()) {
                    VibeSurface(VibeSurfaceLevel.CARD, Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dayRows.size > 1) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                ReorderHandle(dayOrder, d.id, d.name)
                                Text(d.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        VibeActionButton(
                            "Start workout",
                            { requestStart(d.id) },
                            modifier = Modifier.fillMaxWidth(),
                            importance = ActionImportance.PRIMARY,
                            icon = Icons.Outlined.PlayArrow,
                        )
                        if (dayEntries.isEmpty()) {
                            Text("No exercises yet", style = MaterialTheme.typography.bodyMedium)
                        }
                        pendingStartDayId?.let { dayId ->
                            val target = days.find { it.id == dayId }
                            AlertDialog(
                                onDismissRequest = { pendingStartDayId = null },
                                title = { Text("Workout in progress") },
                                text = { Text("${activeWorkout?.name.orEmpty()} is still in progress. Resume it, or discard it and start ${target?.name.orEmpty()}.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        pendingStartDayId = null
                                        onStart(dayId, true)
                                    }) { Text("Discard and start") }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        pendingStartDayId = null
                                        activeWorkout?.programmeDayId?.let { onStart(it, false) }
                                    }) { Text("Resume workout") }
                                },
                            )
                        }
                        entryOrder.ordered(dayEntries) { it.id }.forEachIndexed { index, entry ->
                            key(entry.id) {
                                val exerciseName = exercises.find { it.id == entry.exerciseId }?.canonicalName.orEmpty()
                                ReorderItem(
                                    entryOrder,
                                    entry.id,
                                    index,
                                    Modifier.fillMaxWidth().animateContentSize(),
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                        if (dayEntries.size > 1) {
                                            ReorderHandle(entryOrder, entry.id, exerciseName)
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(exerciseName, style = MaterialTheme.typography.titleMedium)
                                            Text(prescriptionSummary(entry), style = MaterialTheme.typography.bodySmall)
                                            entry.supersetGroup?.let { Text("Circuit: $it", style = MaterialTheme.typography.bodySmall) }
                                        }
                                        IconButton(onClick = {
                                            haptics.perform(VibeHapticEvent.EDIT)
                                            editPrescription = entry
                                        }) {
                                            Icon(Icons.Outlined.Edit, contentDescription = "Edit prescription for $exerciseName")
                                        }
                                        IconButton(onClick = { vm.removeEntry(entry.id) }) {
                                            Icon(Icons.Outlined.Delete, contentDescription = "Remove $exerciseName")
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
            }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VibeActionButton("Duplicate", { selectedProgramme?.let(vm::duplicate) }, modifier = Modifier.weight(1f), importance = ActionImportance.COMPACT)
                        VibeActionButton(
                            "Delete",
                            { selectedProgramme?.let { vm.removeProgramme(it.id); selected = null } },
                            modifier = Modifier.weight(1f),
                            importance = ActionImportance.COMPACT,
                        )
                        VibeActionButton(
                            "Archive",
                            { selectedProgramme?.let { vm.save(it.copy(isArchived = true)); selected = null } },
                            modifier = Modifier.weight(1f),
                            importance = ActionImportance.COMPACT,
                        )
                    }
                }
            }
        }
    rename?.let { p ->
        NameDialog("Programme name", p.name, { rename = null }) { name ->
            val programme = p.copy(name = name)
            val day = ProgrammeDayEntity(
                id = newId(),
                programmeId = programme.id,
                name = if (mode == TrainingMode.STRENGTH) "Workout" else "Stretching",
                position = 0,
            )
            vm.createProgramme(programme, day) {
                haptics.perform(VibeHapticEvent.SUCCESS)
                selected = programme.id
            }
            rename = null
        }
    }
    if (pendingAddDayChoice) {
        AlertDialog(
            onDismissRequest = { pendingAddDayChoice = false },
            title = { Text("Choose programme day") },
            text = {
                Column {
                    dayRows.forEach { day ->
                        TextButton(
                            onClick = {
                                pendingAddDayChoice = false
                                chooseAddType(day.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(day.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingAddDayChoice = false }) { Text("Cancel") }
            },
        )
    }
    pendingAddTypeDayId?.let { targetDayId ->
        AlertDialog(
            onDismissRequest = { pendingAddTypeDayId = null },
            title = { Text("Add to programme") },
            text = {
                Column {
                    if (TrainingMode.STRENGTH.name in availableAddTypes) {
                        TextButton(
                            onClick = {
                                pendingAddTypeDayId = null
                                addExerciseDayId = targetDayId
                                addExerciseActivityTag = TrainingMode.STRENGTH.name
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Add exercise") }
                    }
                    if (TrainingMode.STRETCHING.name in availableAddTypes) {
                        TextButton(
                            onClick = {
                                pendingAddTypeDayId = null
                                addExerciseDayId = targetDayId
                                addExerciseActivityTag = TrainingMode.STRETCHING.name
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Add stretch") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingAddTypeDayId = null }) { Text("Cancel") }
            },
        )
    }
    addExerciseDayId?.let { targetDayId ->
        ExercisePicker(
            vm = vm,
            onDismiss = {
                addExerciseDayId = null
                addExerciseActivityTag = null
            },
            activityTag = addExerciseActivityTag,
        ) { exercise ->
            vm.save(
                ProgrammeExerciseEntity(
                    newId(),
                    targetDayId,
                    exercise.id,
                    entries.count { it.programmeDayId == targetDayId },
                    3,
                    null,
                    null,
                    null,
                    120,
                    null,
                    "",
                    null,
                ),
            ) { haptics.perform(VibeHapticEvent.SUCCESS) }
            addExerciseDayId = null
            addExerciseActivityTag = null
        }
    }
    editPrescription?.let { assignment ->
        ProgrammePrescriptionDialog(
            assignment = assignment,
            exercise = exercises.find { it.id == assignment.exerciseId },
            onDismiss = { editPrescription = null },
            onSave = {
                vm.save(it)
                editPrescription = null
            },
        )
    }
}

private fun prescriptionSummary(assignment: ProgrammeExerciseEntity): String =
    prescriptionSummary(
        assignment.targetSets,
        assignment.targetRepsMin,
        assignment.targetRepsMax,
        assignment.targetHoldSeconds,
        assignment.targetRpe,
    ) + " · ${assignment.restSeconds}s rest"

@Composable
internal fun ProgrammePrescriptionDialog(
    assignment: ProgrammeExerciseEntity,
    exercise: ExerciseEntity?,
    onDismiss: () -> Unit,
    onSave: (ProgrammeExerciseEntity) -> Unit,
) {
    val config = ExerciseInputConfig.decode(exercise?.inputConfig.orEmpty(), exercise?.trackingType.orEmpty())
    val tracksReps = config.reps
    val tracksDuration = config.timeHeld || config.timeUnderTension ||
        exercise?.trackingType in setOf("HOLD", "SKILL_HOLD")
    var sets by remember(assignment.id) { mutableStateOf(assignment.targetSets?.toString().orEmpty()) }
    var minimum by remember(assignment.id) { mutableStateOf(assignment.targetRepsMin?.toString().orEmpty()) }
    var maximum by remember(assignment.id) { mutableStateOf(assignment.targetRepsMax?.toString().orEmpty()) }
    var holdSeconds by remember(assignment.id) { mutableStateOf(assignment.targetHoldSeconds?.toString().orEmpty()) }
    var rpe by remember(assignment.id) { mutableStateOf(assignment.targetRpe?.toString().orEmpty()) }
    var rest by remember(assignment.id) { mutableStateOf(assignment.restSeconds.toString()) }
    val parsedSets = sets.toIntOrNull()
    val parsedMinimum = minimum.toIntOrNull()
    val parsedMaximum = maximum.toIntOrNull()
    val parsedHold = holdSeconds.toIntOrNull()
    val parsedRpe = rpe.toDoubleOrNull()
    val parsedRest = rest.toIntOrNull()
    val valid = (sets.isBlank() || parsedSets?.let { it > 0 } == true) &&
        (minimum.isBlank() || parsedMinimum?.let { it >= 0 } == true) &&
        (maximum.isBlank() || parsedMaximum?.let { it >= 0 } == true) &&
        (parsedMinimum == null || parsedMaximum == null || parsedMaximum >= parsedMinimum) &&
        (holdSeconds.isBlank() || parsedHold?.let { it >= 0 } == true) &&
        (rpe.isBlank() || parsedRpe?.let { it in 0.0..10.0 } == true) &&
        parsedRest?.let { it >= 0 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Programme prescription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(exercise?.canonicalName.orEmpty(), style = MaterialTheme.typography.titleMedium)
                EditField("Sets", sets) { sets = it }
                if (tracksReps) {
                    EditField("Minimum reps", minimum) { minimum = it }
                    EditField("Maximum reps", maximum) { maximum = it }
                }
                if (tracksDuration) {
                    EditField("Target seconds", holdSeconds) { holdSeconds = it }
                }
                EditField("Target RPE", rpe) { rpe = it }
                EditField("Rest seconds", rest) { rest = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        assignment.copy(
                            targetSets = parsedSets,
                            targetRepsMin = if (tracksReps) parsedMinimum else assignment.targetRepsMin,
                            targetRepsMax = if (tracksReps) parsedMaximum else assignment.targetRepsMax,
                            targetHoldSeconds = if (tracksDuration) parsedHold else assignment.targetHoldSeconds,
                            targetRpe = parsedRpe,
                            restSeconds = requireNotNull(parsedRest),
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, allowEmpty: Boolean = false, onSave: (String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(name, { name = it }, singleLine = !allowEmpty) }, confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = allowEmpty || name.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun ExercisePicker(
    vm: EditorViewModel,
    onDismiss: () -> Unit,
    activityTag: String? = null,
    onChoose: (ExerciseEntity) -> Unit,
) {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val aliases by vm.aliases.collectAsStateWithLifecycle()
    val mappings by vm.mappings.collectAsStateWithLifecycle()
    val muscles by vm.muscles.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val aliasIds = aliases.filter { it.alias.contains(query, true) }.map { it.exerciseId }.toSet()
    val muscleIds = muscles.filter { it.displayName.contains(query, true) }.map { it.id }.toSet()
    val mappedIds = mappings.filter { it.muscleId in muscleIds }.map { it.exerciseId }.toSet()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose exercise") }, text = {
        Column { OutlinedTextField(query, { query = it }, label = { Text("Name, alias or muscle") })
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(
                    exercises.filter {
                        !it.isArchived &&
                            (activityTag == null || it.tag == activityTag) &&
                            (it.canonicalName.contains(query, true) || it.id in aliasIds || it.id in mappedIds)
                    },
                ) { e ->
                Text(e.canonicalName, Modifier.fillMaxWidth().clickable { onChoose(e) }.padding(vertical = 14.dp))
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
fun EditField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value,
        onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
        singleLine = true,
    )
}

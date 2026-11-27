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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.domain.model.programmeColour
import com.petermathie.vibecheck.domain.model.recordColours
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.prescriptionSummary
import com.petermathie.vibecheck.ui.theme.LocalVibeReducedMotion
import com.petermathie.vibecheck.ui.theme.LocalVibePalette

@Composable
fun ProgrammeEditor(
    vm: EditorViewModel,
    defaultMode: TrainingMode,
    onResume: () -> Unit,
    isStartingWorkout: Boolean = false,
    onStart: (dayId: String, activeWorkoutId: String?, finishCurrent: Boolean) -> Unit,
) {
    val programmes by vm.programmes.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedProgrammeIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var creatingId by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingColour by rememberSaveable { mutableLongStateOf(0L) }
    var savingProgramme by remember { mutableStateOf(false) }
    var chooseColour by rememberSaveable { mutableStateOf(false) }
    var addExerciseDayId by rememberSaveable { mutableStateOf<String?>(null) }
    var addExerciseActivityTag by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAddDayChoice by rememberSaveable { mutableStateOf(false) }
    var pendingAddTypeDayId by rememberSaveable { mutableStateOf<String?>(null) }
    var editPrescription by remember { mutableStateOf<ProgrammeExerciseEntity?>(null) }
    var pendingStartDayId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingStartWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
    val programmeRows = programmes.sortedBy { it.position }
    val selectedProgramme = programmes.find { it.id == selected }
    val editing = selected != null || creatingId != null
    val colourArgb = selectedProgramme?.colourArgb ?: creatingColour
    var generatedEmptyDayId by rememberSaveable(selectedProgramme?.id) { mutableStateOf<String?>(null) }
    var programmeName by rememberSaveable(selectedProgramme?.id, selectedProgramme?.name, creatingId) {
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
    val palette = LocalVibePalette.current
    val haptics = rememberVibeHaptics()
    val listState = rememberLazyListState()
    LaunchedEffect(selected, creatingId) { listState.scrollToItem(0) }
    val activeWorkout = vm.workouts.collectAsStateWithLifecycle().value.find { it.status == "DRAFT" }
    fun requestStart(dayId: String) {
        when {
            isStartingWorkout -> Unit
            activeWorkout == null -> onStart(dayId, null, false)
            activeWorkout.programmeDayId == dayId -> onResume()
            else -> {
                pendingStartWorkoutId = activeWorkout.id
                pendingStartDayId = dayId
            }
        }
    }
    RegisterAppFabAction(
        owner = Destination.PROGRAMMES,
        destination = AppFabDestination.ResumeWorkout,
        visible = activeWorkout != null && !isStartingWorkout && !editing &&
            addExerciseDayId == null && !pendingAddDayChoice && pendingAddTypeDayId == null &&
            editPrescription == null && pendingStartDayId == null,
        onClick = onResume,
    )
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
    fun closeEditor() {
        selected = null
        creatingId = null
        chooseColour = false
    }
    fun saveName() {
        val name = programmeName.trim()
        if (name.isBlank() || savingProgramme) return
        val id = creatingId
        if (id == null) {
            selectedProgramme?.let { vm.renameProgramme(it.id, name) }
        } else {
            savingProgramme = true
            vm.createProgramme(
                ProgrammeEntity(id, name, defaultMode.name, false, position = programmeRows.size, colourArgb = creatingColour),
                ProgrammeDayEntity(
                    newId(), id,
                    if (defaultMode == TrainingMode.STRENGTH) "Workout" else "Stretching", 0,
                ),
                onFailure = { savingProgramme = false },
            ) {
                selected = id
                creatingId = null
                savingProgramme = false
                haptics.perform(VibeHapticEvent.SUCCESS)
            }
        }
    }
    BackHandler(editing) { if (!savingProgramme) closeEditor() }
    HabitGlassDialogHost { backdrop, _ ->
    ScreenList(modifier = backdrop, listState = listState) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                if (editing) {
                    IconButton(onClick = ::closeEditor, enabled = !savingProgramme) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "Back")
                    }
                }
                Text(
                    if (!editing) "Programmes" else if (creatingId != null) "New programme" else "Edit programme",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        if (!editing) {
            if (programmeRows.isEmpty()) {
                item { com.petermathie.vibecheck.ui.components.VibeStatePanel("Create a programme to organize your sessions.") }
            }
            itemsIndexed(programmeOrder.ordered(programmeRows) { it.id }, key = { _, row -> row.id }) { index, p ->
                val expanded = p.id in expandedProgrammeIds
                val previewDays = days.filter { it.programmeId == p.id }.sortedBy { it.position }
                ReorderItem(
                    state = programmeOrder,
                    itemKey = p.id,
                    index = index,
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                ) {
                    Box(
                        Modifier.fillMaxWidth().habitGlow(Color(p.colourArgb.toInt()), palette.isDark),
                    ) {
                    Column(Modifier.fillMaxWidth()) {
                        if (index > 0) HorizontalDivider(color = palette.border.copy(alpha = 0.3f))
                        ReorderHandle(
                            programmeOrder, p.id, p.name, enabled = programmeRows.size > 1, longPress = true,
                        ) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                p.name,
                                Modifier
                                    .weight(1f)
                                    .clickable {
                                        expandedProgrammeIds = if (expanded) {
                                            expandedProgrammeIds - p.id
                                        } else {
                                            expandedProgrammeIds + p.id
                                        }
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
                                enabled = previewDays.isNotEmpty() && !isStartingWorkout,
                            ) {
                                Icon(Icons.Outlined.PlayArrow, contentDescription = "Start ${p.name}")
                            }
                        }
                        }
                        AnimatedVisibility(
                            visible = expanded,
                            enter = if (reducedMotion) EnterTransition.None else expandVertically() + fadeIn(),
                            exit = if (reducedMotion) ExitTransition.None else shrinkVertically() + fadeOut(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp)
                                        .semantics { contentDescription = "Read-only exercises for ${p.name}" },
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    if (previewDays.isEmpty()) {
                                        Text("No exercises yet", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                                    }
                                    previewDays.forEach { day ->
                                        if (previewDays.size > 1) {
                                            Text(day.name, style = MaterialTheme.typography.titleMedium)
                                        }
                                        val previewEntries = entries
                                            .filter { it.programmeDayId == day.id }
                                            .sortedBy { it.position }
                                        if (previewEntries.isEmpty()) {
                                            Text("No exercises yet", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
                                        } else {
                                            previewEntries.forEach { entry ->
                                                val exerciseName = exercises
                                                    .find { it.id == entry.exerciseId }
                                                    ?.canonicalName
                                                    .orEmpty()
                                                Column {
                                                    Text(exerciseName, style = MaterialTheme.typography.bodyLarge)
                                                    Text(prescriptionSummary(entry), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
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
            item(key = "add-programme") {
                TextButton(
                    onClick = {
                        val id = newId()
                        creatingColour = programmeColour(id)
                        creatingId = id
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("New programme")
                }
            }
        } else {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { chooseColour = true },
                        enabled = !savingProgramme,
                        modifier = Modifier.semantics { contentDescription = "Change programme colour" },
                    ) {
                        Box(Modifier.size(28.dp).background(Color(colourArgb.toInt()), CircleShape))
                    }
                TextField(
                    value = programmeName,
                    onValueChange = { programmeName = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Programme name") },
                    singleLine = true,
                    enabled = !savingProgramme,
                    shape = MaterialTheme.shapes.small,
                    colors = programmeFieldColors(),
                    trailingIcon = {
                        IconButton(
                            onClick = ::saveName,
                            enabled = !savingProgramme && programmeName.isNotBlank() &&
                                (creatingId != null || programmeName.trim() != selectedProgramme?.name),
                        ) { Icon(Icons.Outlined.Check, "Save programme name") }
                    },
                )
                }
                if (creatingId != null) Text(
                    "Name your programme and save it to add exercises.",
                    style = MaterialTheme.typography.bodySmall, color = palette.textSecondary,
                )
            }
            itemsIndexed(dayOrder.ordered(dayRows) { it.id }, key = { _, row -> row.id }) { dayIndex, d ->
                val dayEntries = entries.filter { it.programmeDayId == d.id }.sortedBy { it.position }
                val entryOrder = rememberReorderState(dayEntries.map { it.id }) { key, from, to ->
                    vm.moveEntry(key as String, to - from)
                }
                ReorderItem(dayOrder, d.id, dayIndex, Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().habitGlow(Color(colourArgb.toInt()), palette.isDark)) {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (dayIndex > 0) HorizontalDivider(color = palette.border.copy(alpha = 0.3f))
                            if (dayRows.size > 1) {
                                ReorderHandle(dayOrder, d.id, d.name, longPress = true) {
                                    Text(d.name, Modifier.fillMaxWidth().padding(vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
                                }
                            }
                            VibeActionButton(
                                "Start workout",
                                { requestStart(d.id) },
                                modifier = Modifier.fillMaxWidth(),
                                importance = ActionImportance.PRIMARY,
                                icon = Icons.Outlined.PlayArrow,
                                enabled = !isStartingWorkout,
                            )
                            if (dayEntries.isEmpty()) {
                                Text("No exercises yet", style = MaterialTheme.typography.bodyMedium, color = palette.textSecondary)
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
                                        StagedSwipeToDelete(
                                            label = "programme exercise $exerciseName",
                                            onDelete = { vm.removeEntry(entry.id).join() },
                                        ) {
                                            Column(Modifier.fillMaxWidth()) {
                                                if (index > 0) HorizontalDivider(color = palette.border.copy(alpha = 0.2f))
                                                ReorderHandle(
                                                    entryOrder, entry.id, exerciseName,
                                                    enabled = dayEntries.size > 1, longPress = true,
                                                ) {
                                                    Column(
                                                        Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable(
                                                            onClickLabel = "Edit prescription for $exerciseName",
                                                        ) {
                                                            haptics.perform(VibeHapticEvent.EDIT)
                                                            editPrescription = entry
                                                        }.semantics { contentDescription = "Edit prescription for $exerciseName" },
                                                    ) {
                                                        Text(exerciseName, style = MaterialTheme.typography.titleLarge)
                                                        Text(prescriptionSummary(entry), style = MaterialTheme.typography.bodySmall, color = palette.textSecondary)
                                                        entry.supersetGroup?.let { Text("Circuit: $it", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary) }
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
                item(key = "add-programme-exercise") {
                    TextButton(
                        onClick = ::requestAddProgrammeItem,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedProgramme != null && availableAddTypes.isNotEmpty(),
                    ) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add exercise")
                    }
                }
                if (selectedProgramme != null) item {
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
                            { selectedProgramme?.let { vm.archiveProgramme(it.id); selected = null } },
                            modifier = Modifier.weight(1f),
                            importance = ActionImportance.COMPACT,
                        )
                    }
                }
            }
        }
    pendingStartDayId?.let { dayId ->
        val target = days.find { it.id == dayId }
        val programme = programmes.find { it.id == target?.programmeId }
        fun switchWorkout(finishCurrent: Boolean) {
            val workoutId = pendingStartWorkoutId
            pendingStartDayId = null
            pendingStartWorkoutId = null
            if (workoutId == null) {
                vm.error.value = "The active workout changed. Choose the workout again."
            } else {
                onStart(dayId, workoutId, finishCurrent)
            }
        }
        HabitGlassDialog(
            onDismiss = { pendingStartDayId = null },
            title = "Workout in progress",
            actions = {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { switchWorkout(true) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Save") }
                    TextButton(
                        onClick = { switchWorkout(false) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Discard") }
                    TextButton(
                        onClick = { pendingStartDayId = null },
                        modifier = Modifier.weight(1f),
                    ) { Text("Cancel") }
                }
            },
        ) {
            Text(
                "${activeWorkout?.name.orEmpty()} is still in progress. " +
                    "Save it to History or discard it before starting ${programme?.name ?: target?.name.orEmpty()}.",
            )
        }
    }
    if (chooseColour) HabitGlassDialog(
        title = "Programme colour",
        onDismiss = { chooseColour = false },
        actions = { TextButton(onClick = { chooseColour = false }) { Text("Cancel") } },
    ) {
        HabitOptionsGrid(recordColours) { (name, colour), modifier ->
            IconButton(
                onClick = {
                    if (creatingId != null) creatingColour = colour
                    else selectedProgramme?.let { vm.setProgrammeColour(it.id, colour) }
                    chooseColour = false
                },
                modifier = modifier.semantics { contentDescription = "Set programme colour $name" },
            ) { Box(Modifier.size(32.dp).background(Color(colour.toInt()), CircleShape)) }
        }
    }
    if (pendingAddDayChoice) {
        HabitGlassDialog(
            onDismiss = { pendingAddDayChoice = false },
            title = "Choose programme day",
            actions = {
                TextButton(onClick = { pendingAddDayChoice = false }) { Text("Cancel") }
            },
        ) {
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
        }
    }
    pendingAddTypeDayId?.let { targetDayId ->
        HabitGlassDialog(
            onDismiss = { pendingAddTypeDayId = null },
            title = "Add to programme",
            actions = {
                TextButton(onClick = { pendingAddTypeDayId = null }) { Text("Cancel") }
            },
        ) {
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
        }
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
    var sets by remember(assignment.id) { mutableStateOf(assignment.targetSets?.toString().orEmpty()) }
    var minimum by remember(assignment.id) { mutableStateOf(assignment.targetRepsMin?.toString().orEmpty()) }
    var maximum by remember(assignment.id) { mutableStateOf(assignment.targetRepsMax?.toString().orEmpty()) }
    var rest by remember(assignment.id) { mutableStateOf(assignment.restSeconds.toString()) }
    val parsedSets = sets.toIntOrNull()
    val parsedMinimum = minimum.toIntOrNull()
    val parsedMaximum = maximum.toIntOrNull()
    val parsedRest = rest.toIntOrNull()
    val valid = (sets.isBlank() || parsedSets?.let { it > 0 } == true) &&
        (minimum.isBlank() || parsedMinimum?.let { it >= 0 } == true) &&
        (maximum.isBlank() || parsedMaximum?.let { it >= 0 } == true) &&
        (parsedMinimum == null || parsedMaximum == null || parsedMaximum >= parsedMinimum) &&
        parsedRest?.let { it >= 0 } == true

    HabitGlassDialog(
        onDismiss = onDismiss,
        title = "Programme prescription",
        actions = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        assignment.copy(
                            targetSets = parsedSets,
                            targetRepsMin = if (tracksReps) parsedMinimum else assignment.targetRepsMin,
                            targetRepsMax = if (tracksReps) parsedMaximum else assignment.targetRepsMax,
                            restSeconds = requireNotNull(parsedRest),
                        ),
                    )
                },
            ) { Text("Save") }
        },
    ) {
        Text(exercise?.canonicalName.orEmpty(), style = MaterialTheme.typography.titleMedium)
        EditField("Sets", sets, borderless = true) { sets = it }
        if (tracksReps) {
            EditField("Minimum reps", minimum, borderless = true) { minimum = it }
            EditField("Maximum reps", maximum, borderless = true) { maximum = it }
        }
        EditField("Rest seconds", rest, borderless = true) { rest = it }
    }
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    allowEmpty: Boolean = false,
    glass: Boolean = false,
    onSave: (String) -> Unit,
) {
    var name by remember(initial) { mutableStateOf(initial) }
    if (glass) {
        HabitGlassDialog(
            title = title,
            onDismiss = onDismiss,
            actions = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    onClick = { onSave(name.trim()) },
                    enabled = allowEmpty || name.isNotBlank(),
                ) { Text("Save") }
            },
        ) {
            TextField(
                name, { name = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = !allowEmpty, shape = MaterialTheme.shapes.small,
                colors = programmeFieldColors(),
            )
        }
    } else {
        AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { OutlinedTextField(name, { name = it }, singleLine = !allowEmpty) }, confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = allowEmpty || name.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
    }
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
    val palette = LocalVibePalette.current
    HabitGlassDialog(
        title = "Choose exercise",
        onDismiss = onDismiss,
        actions = { TextButton(onClick = onDismiss) { Text("Close") } },
    ) {
        TextField(
            query, { query = it }, label = { Text("Name, alias or muscle") },
            modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small,
            colors = programmeFieldColors(),
        )
        LazyColumn(Modifier.heightIn(max = 420.dp)) {
            items(
                exercises.filter {
                    !it.isArchived &&
                        (activityTag == null || it.tag == activityTag) &&
                        (it.canonicalName.contains(query, true) || it.id in aliasIds || it.id in mappedIds)
                },
            ) { e ->
                Text(
                    e.canonicalName,
                    Modifier.fillMaxWidth().habitGlow(palette.accent, palette.isDark)
                        .clickable { onChoose(e) }.padding(horizontal = 8.dp, vertical = 14.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
fun EditField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    borderless: Boolean = false,
    onChange: (String) -> Unit,
) {
    if (borderless) {
        TextField(
            value, onChange,
            label = { Text(label) },
            modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
            singleLine = true, shape = MaterialTheme.shapes.small,
            colors = programmeFieldColors(),
        )
    } else {
        OutlinedTextField(
            value,
            onChange,
            label = { Text(label) },
            modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
            singleLine = true,
        )
    }
}

@Composable
internal fun programmeFieldColors(): TextFieldColors {
    val fill = LocalVibePalette.current.surfaceInset.copy(alpha = 0.3f)
    return TextFieldDefaults.colors(
        focusedContainerColor = fill,
        unfocusedContainerColor = fill,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )
}

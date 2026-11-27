package com.petermathie.vibecheck.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.domain.model.recordColours
import androidx.compose.material.icons.outlined.Close
import com.petermathie.vibecheck.ui.theme.LocalVibeReducedMotion
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

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
    val error by vm.error.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var prescriptionInputs by remember(selected) { mutableStateOf(mapOf<String, ProgrammePrescriptionInputs>()) }
    var expandedProgrammeIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var savingProgramme by remember { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    var programmeName by rememberSaveable { mutableStateOf("") }
    var chooseColour by rememberSaveable { mutableStateOf(false) }
    var addExerciseDayId by rememberSaveable { mutableStateOf<String?>(null) }
    var addExerciseActivityTag by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAddDayChoice by rememberSaveable { mutableStateOf(false) }
    var pendingAddTypeDayId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRemoval by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingStartDayId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingStartWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
    val programmeRows = programmes.sortedBy { it.position }
    val selectedProgramme = programmes.find { it.id == selected }
    val editing = selected != null
    val colourArgb = selectedProgramme?.colourArgb ?: 0L
    val dayRows = days.filter { it.programmeId == selected }.sortedBy { it.position }
    val editingEntryIds = entries.filter { entry -> dayRows.any { it.id == entry.programmeDayId } }.map { it.id }.toSet()
    val editedPrescriptions = prescriptionInputs.filterKeys { it in editingEntryIds }
    val prescriptionsValid = editedPrescriptions.values.all { it.valid }
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
    val editorScroll = rememberScrollState()
    LaunchedEffect(selected) { editorScroll.scrollTo(0) }
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
            pendingRemoval == null && pendingStartDayId == null,
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
            0 -> selectedProgramme?.let { programme ->
                vm.ensureProgrammeDay(programme.id) { dayId ->
                    if (selected == programme.id) chooseAddType(dayId)
                }
            }
            1 -> chooseAddType(dayRows.single().id)
            else -> pendingAddDayChoice = true
        }
    }
    fun closeEditor() {
        if (savingProgramme) return
        selected = null
        chooseColour = false
        confirmingDelete = false
    }
    fun openEditor(programme: ProgrammeEntity) {
        programmeName = programme.name
        selected = programme.id
        haptics.perform(VibeHapticEvent.EDIT)
    }
    fun saveProgrammeEdits() {
        val name = programmeName.trim()
        val prescriptions = prescriptionInputs.filterKeys { it in editingEntryIds }
        if (name.isBlank() || prescriptions.values.any { !it.valid } || savingProgramme) return
        val id = selected ?: return
        savingProgramme = true
        vm.saveProgrammeEditor(
            id, name,
            prescriptions.map { (entryId, input) ->
                ProgrammeEntryPrescriptionUpdate(entryId, input.setCount, requireNotNull(input.restSeconds))
            },
            onSaved = { savingProgramme = false; closeEditor() },
            onFailure = { savingProgramme = false },
        )
    }
    HabitGlassDialogHost { backdrop, _ ->
        ScreenList(modifier = backdrop, listState = listState) {
            item { Text("Programmes", style = MaterialTheme.typography.headlineSmall) }
            if (programmeRows.isEmpty()) item {
                com.petermathie.vibecheck.ui.components.VibeStatePanel("Create a programme to organize your sessions.")
            }
            itemsIndexed(programmeOrder.ordered(programmeRows) { it.id }, key = { _, row -> row.id }) { index, p ->
                val expanded = p.id in expandedProgrammeIds
                val previewDays = days.filter { it.programmeId == p.id }.sortedBy { it.position }
                val activeDay = previewDays.find { it.id == activeWorkout?.programmeDayId }
                ReorderItem(programmeOrder, p.id, index, Modifier.fillMaxWidth().animateContentSize()) {
                    Column(Modifier.fillMaxWidth().habitGlow(Color(p.colourArgb.toInt()), palette.isDark)) {
                        if (index > 0) HorizontalDivider(color = palette.border.copy(alpha = 0.3f))
                        ReorderHandle(programmeOrder, p.id, p.name, enabled = programmeRows.size > 1, longPress = true) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    p.name,
                                    Modifier.weight(1f).combinedClickable(
                                        onClick = {
                                            expandedProgrammeIds = if (expanded) expandedProgrammeIds - p.id else expandedProgrammeIds + p.id
                                        },
                                        onDoubleClick = { openEditor(p) },
                                    ).semantics {
                                        stateDescription = if (expanded) "Exercises shown" else "Exercises hidden"
                                        customActions = listOf(CustomAccessibilityAction("Edit programme") { openEditor(p); true })
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                IconButton(
                                    onClick = { (activeDay ?: previewDays.firstOrNull())?.let { requestStart(it.id) } },
                                    enabled = previewDays.isNotEmpty() && !isStartingWorkout,
                                    modifier = Modifier.semantics {
                                        stateDescription = if (activeDay != null) "Workout in progress" else "Ready to start"
                                    },
                                ) {
                                    Icon(
                                        Icons.Outlined.PlayArrow, contentDescription = "Start ${p.name}",
                                        tint = if (activeDay != null) palette.accent else palette.textSecondary,
                                    )
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = expanded,
                            enter = if (reducedMotion) EnterTransition.None else expandVertically() + fadeIn(),
                            exit = if (reducedMotion) ExitTransition.None else shrinkVertically() + fadeOut(),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 12.dp)
                                    .semantics { contentDescription = "Read-only exercises for ${p.name}" },
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (previewDays.isEmpty()) Text("No exercises yet", color = palette.textSecondary)
                                previewDays.forEach { day ->
                                    if (previewDays.size > 1) Text(day.name, style = MaterialTheme.typography.titleMedium)
                                    val previewEntries = entries.filter { it.programmeDayId == day.id }.sortedBy { it.position }
                                    if (previewEntries.isEmpty()) Text("No exercises yet", color = palette.textSecondary)
                                    previewEntries.forEach { entry ->
                                        ProgrammeExercisePreview(
                                            entry.id, exercises.find { it.id == entry.exerciseId }?.canonicalName.orEmpty(),
                                            entry.targetSets, entry.restSeconds,
                                        )
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
                        savingProgramme = true
                        val programme = ProgrammeEntity(newId(), "Program ${programmeRows.size + 1}", defaultMode.name, false, position = programmeRows.size)
                        vm.createProgramme(
                            programme,
                            ProgrammeDayEntity(newId(), programme.id, if (defaultMode == TrainingMode.STRENGTH) "Workout" else "Stretching", 0),
                            onFailure = { savingProgramme = false },
                        ) {
                            savingProgramme = false
                            openEditor(programme)
                        }
                    },
                    enabled = !savingProgramme,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("New programme")
                }
            }
        }
        val childDialogOpen = chooseColour || confirmingDelete || pendingAddDayChoice || pendingAddTypeDayId != null ||
            addExerciseDayId != null || pendingRemoval != null || pendingStartDayId != null
        if (selectedProgramme != null && !childDialogOpen) HabitGlassDialog(
            title = "Edit programme",
            onDismiss = ::closeEditor,
            scrollState = editorScroll,
            actions = {
                if (savingProgramme) LinearProgressIndicator(Modifier.fillMaxWidth())
                else {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val labels = listOf("Save", "Archive", "Duplicate", "Delete")
                        val measurer = rememberTextMeasurer()
                        val density = LocalDensity.current
                        val widths = labels.map { label ->
                            with(density) { measurer.measure(label, MaterialTheme.typography.labelLarge).size.width.toDp() }
                        }
                        val available = (maxWidth - 24.dp).coerceAtLeast(0.dp)
                        val scale = (available.value / widths.sumOf { it.value.toDouble() }.toFloat()).coerceAtMost(1f)
                        // Visual widths follow the labels; pointer targets still expand independently.
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Row(
                                Modifier.fillMaxWidth().testTag("Programme editor actions"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ProgrammeFooterButton(
                                    "Save", ::saveProgrammeEdits,
                                    enabled = programmeName.isNotBlank() && prescriptionsValid,
                                    textAlign = TextAlign.Start,
                                    width = widths[0] * scale,
                                )
                                ProgrammeFooterButton("Archive", {
                                    savingProgramme = true
                                    vm.archiveProgramme(
                                        selectedProgramme.id,
                                        onSaved = { savingProgramme = false; closeEditor() },
                                        onFailure = { savingProgramme = false },
                                    )
                                }, width = widths[1] * scale)
                                ProgrammeFooterButton("Duplicate", {
                                    savingProgramme = true
                                    vm.duplicate(
                                        selectedProgramme,
                                        onDuplicated = { savingProgramme = false; haptics.perform(VibeHapticEvent.SUCCESS) },
                                        onFailure = { savingProgramme = false },
                                    )
                                }, width = widths[2] * scale)
                                ProgrammeFooterButton("Delete", { confirmingDelete = true }, textAlign = TextAlign.End, width = widths[3] * scale)
                            }
                        }
                    }
                }
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { chooseColour = true }, enabled = !savingProgramme,
                    modifier = Modifier.semantics { contentDescription = "Change programme colour" },
                ) { Box(Modifier.size(28.dp).background(Color(colourArgb.toInt()), CircleShape)) }
                TextField(
                    value = programmeName,
                    onValueChange = { name ->
                        programmeName = name
                        if (name.isNotBlank()) vm.renameProgramme(selectedProgramme.id, name)
                    },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Programme name" },
                    label = { Text("Programme name") },
                    isError = programmeName.isBlank(),
                    supportingText = if (programmeName.isBlank()) {
                        { Text("Programme name cannot be blank") }
                    } else null,
                    singleLine = true, enabled = !savingProgramme, shape = MaterialTheme.shapes.small,
                    colors = programmeFieldColors(),
                )
            }
            dayOrder.ordered(dayRows) { it.id }.forEachIndexed { dayIndex, day ->
                key(day.id) {
                    val dayEntries = entries.filter { it.programmeDayId == day.id }.sortedBy { it.position }
                    val entryOrder = rememberReorderState(dayEntries.map { it.id }) { item, from, to -> vm.moveEntry(item as String, to - from) }
                    ReorderItem(dayOrder, day.id, dayIndex, Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().habitGlow(Color(colourArgb.toInt()), palette.isDark), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (dayIndex > 0) HorizontalDivider(color = palette.border.copy(alpha = 0.3f))
                            if (dayRows.size > 1) ReorderHandle(dayOrder, day.id, day.name, longPress = true) {
                                Text(day.name, Modifier.fillMaxWidth().padding(vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
                            }
                            if (dayEntries.isEmpty()) Text("No exercises yet", color = palette.textSecondary)
                            entryOrder.ordered(dayEntries) { it.id }.forEachIndexed { index, entry ->
                                key(entry.id) {
                                    val name = exercises.find { it.id == entry.exerciseId }?.canonicalName.orEmpty()
                                    ReorderItem(entryOrder, entry.id, index, Modifier.fillMaxWidth().animateContentSize()) {
                                            Column(Modifier.fillMaxWidth()) {
                                                if (index > 0) HorizontalDivider(color = palette.border.copy(alpha = 0.2f))
                                                val input = prescriptionInputs[entry.id] ?: ProgrammePrescriptionInputs(
                                                    entry.targetSets?.toString().orEmpty(), entry.restSeconds.toString(),
                                                )
                                                val density = LocalDensity.current
                                                val measurer = rememberTextMeasurer()
                                                val numberStyle = MaterialTheme.typography.bodyLarge
                                                val suffixStyle = MaterialTheme.typography.labelSmall
                                                val setsSuffix = if (input.setCount == 1) "set" else "sets"
                                                fun fieldWidth(value: String, suffix: String): Dp = with(density) {
                                                    val digits = maxOf(
                                                        measurer.measure("999", numberStyle).size.width,
                                                        measurer.measure(value, numberStyle).size.width,
                                                    ).toDp()
                                                    maxOf(56.dp, digits + measurer.measure(suffix, suffixStyle).size.width.toDp() + 22.dp)
                                                }
                                                val setsWidth = fieldWidth(input.sets, setsSuffix)
                                                val restWidth = fieldWidth(input.rest, "s")
                                                val prescriptionFields: @Composable () -> Unit = {
                                                    HabitTextField(
                                                        "Sets", input.sets,
                                                        onValueChange = { text ->
                                                            val updated = (prescriptionInputs[entry.id] ?: input).copy(sets = text)
                                                            prescriptionInputs = prescriptionInputs + (entry.id to updated)
                                                            if (updated.setsValid) vm.setProgrammeSetCount(entry.id, updated.setCount)
                                                        },
                                                        modifier = Modifier.width(setsWidth).semantics { contentDescription = "Sets for $name" },
                                                        numeric = true, plain = true, inset = true, readOnly = savingProgramme,
                                                        textAlign = TextAlign.End,
                                                        suffix = setsSuffix,
                                                        error = if (input.setsValid) null else "Enter a positive whole number",
                                                    )
                                                    HabitTextField(
                                                        "Rest (s)", input.rest,
                                                        onValueChange = { text ->
                                                            val updated = (prescriptionInputs[entry.id] ?: input).copy(rest = text)
                                                            prescriptionInputs = prescriptionInputs + (entry.id to updated)
                                                            if (updated.restValid) vm.setProgrammeRestSeconds(entry.id, requireNotNull(updated.restSeconds))
                                                        },
                                                        modifier = Modifier.width(restWidth).semantics { contentDescription = "Rest seconds for $name" },
                                                        numeric = true, plain = true, inset = true, readOnly = savingProgramme,
                                                        textAlign = TextAlign.End,
                                                        suffix = "s",
                                                        error = if (input.restValid) null else "Enter zero or positive whole seconds",
                                                    )
                                                }
                                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                                    val stacked = maxWidth < 48.dp + 12.dp + 80.dp * density.fontScale + setsWidth + restWidth
                                                    Column {
                                                        Row(
                                                            Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            IconButton(
                                                                onClick = { pendingRemoval = entry.id },
                                                                enabled = !savingProgramme,
                                                            ) {
                                                                Icon(
                                                                    Icons.Outlined.Close, "Remove $name from programme",
                                                                    modifier = Modifier.size(18.dp), tint = palette.textSecondary,
                                                                )
                                                            }
                                                            ReorderHandle(
                                                                entryOrder, entry.id, name, modifier = Modifier.weight(1.4f),
                                                                enabled = dayEntries.size > 1, longPress = true,
                                                            ) {
                                                                Text(
                                                                    name,
                                                                    Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics {
                                                                        contentDescription = "Programme exercise $name"
                                                                    },
                                                                    style = MaterialTheme.typography.bodyLarge,
                                                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                                )
                                                            }
                                                            if (!stacked) prescriptionFields()
                                                        }
                                                        if (stacked) FlowRow(
                                                            Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                                        ) { prescriptionFields() }
                                                    }
                                                }
                                                entry.supersetGroup?.let { Text("Circuit: $it", style = MaterialTheme.typography.bodySmall, color = palette.textSecondary) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            TextButton(
                onClick = ::requestAddProgrammeItem,
                enabled = !savingProgramme && availableAddTypes.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add exercise")
            }
            error?.let { message ->
                TextButton(onClick = { vm.error.value = null }) { Text(message, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (confirmingDelete && selectedProgramme != null) HabitGlassDialog(
            title = "Delete programme?",
            onDismiss = { if (!savingProgramme) confirmingDelete = false },
            actions = {
                TextButton(onClick = { confirmingDelete = false }, enabled = !savingProgramme) { Text("Cancel") }
                TextButton(
                    onClick = {
                        savingProgramme = true
                        vm.removeProgramme(
                            selectedProgramme.id,
                            onRemoved = { savingProgramme = false; closeEditor() },
                            onFailure = { savingProgramme = false },
                        )
                    }, enabled = !savingProgramme,
                ) { Text("Delete") }
            },
        ) {
            Text("Delete ${selectedProgramme.name} and its programmed exercises? Recorded workout history will be kept.")
            error?.let { message ->
                TextButton(onClick = { vm.error.value = null }) { Text(message, color = MaterialTheme.colorScheme.error) }
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
                    selectedProgramme?.let { vm.setProgrammeColour(it.id, colour) }
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
                            Text(day.name, color = selectionTextColor())
                        }
                    }
                }
        }
    }
    pendingAddTypeDayId?.let { targetDayId ->
        HabitGlassDialog(
            onDismiss = { pendingAddTypeDayId = null },
            title = "Add to programme",
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
    pendingRemoval?.let { id ->
        val name = entries.find { it.id == id }?.let { assignment ->
            exercises.find { it.id == assignment.exerciseId }?.canonicalName
        }.orEmpty()
        HabitGlassDialog(
            title = "Remove $name from programme?",
            onDismiss = { if (!savingProgramme) pendingRemoval = null },
            actions = {
                TextButton(onClick = { pendingRemoval = null }, enabled = !savingProgramme) { Text("Cancel") }
                TextButton(
                    enabled = !savingProgramme,
                    onClick = {
                        savingProgramme = true
                        vm.removeEntry(
                            id,
                            onRemoved = {
                                prescriptionInputs = prescriptionInputs - id
                                savingProgramme = false
                                pendingRemoval = null
                            },
                            onFailure = { savingProgramme = false },
                        )
                    },
                ) { Text("Remove") }
            },
        ) {
            Text("Only this programme assignment is removed. The exercise catalogue and workout history stay intact.")
            if (savingProgramme) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    }
}

internal data class ProgrammePrescriptionInputs(val sets: String, val rest: String) {
    val setCount: Int? get() = sets.trim().toIntOrNull()
    val restSeconds: Int? get() = rest.trim().toIntOrNull()
    val setsValid: Boolean get() = sets.isBlank() || setCount?.let { it > 0 } == true
    val restValid: Boolean get() = restSeconds?.let { it >= 0 } == true
    val valid: Boolean get() = setsValid && restValid
}

@Composable
private fun ProgrammeFooterButton(
    label: String, onClick: () -> Unit, width: Dp, enabled: Boolean = true, textAlign: TextAlign = TextAlign.Center,
) {
    TextButton(
        onClick = onClick, enabled = enabled, modifier = Modifier.width(width).heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
    ) {
        Text(
            label, Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelLarge,
            textAlign = textAlign, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgrammePreviewDivider(id: String, index: Int) {
    VerticalDivider(
        Modifier.padding(horizontal = 4.dp, vertical = 4.dp).fillMaxHeight()
            .testTag("Programme preview divider $index $id"),
        color = LocalVibePalette.current.border.copy(alpha = 0.3f),
    )
}

@Composable
internal fun ProgrammeExercisePreview(
    id: String,
    name: String,
    sets: Int?,
    restSeconds: Int?,
) {
    val palette = LocalVibePalette.current
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
        Text(
            name, Modifier.weight(1f).testTag("Programme preview name $id"),
            style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        ProgrammePreviewDivider(id, 0)
        Text(
            sets?.let { "$it ${if (it == 1) "set" else "sets"}" }.orEmpty(),
            Modifier.weight(1f).testTag("Programme preview sets $id"),
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        ProgrammePreviewDivider(id, 1)
        Text(
            restSeconds?.let { "${it}s rest" }.orEmpty(),
            Modifier.weight(1f).testTag("Programme preview rest $id"),
            textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
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
                    color = selectionTextColor(),
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
    enabled: Boolean = true,
    onChange: (String) -> Unit,
) {
    if (borderless) {
        TextField(
            value, onChange,
            label = { Text(label) },
            modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
            singleLine = true, enabled = enabled, shape = MaterialTheme.shapes.small,
            colors = programmeFieldColors(),
        )
    } else {
        OutlinedTextField(
            value,
            onChange,
            label = { Text(label) },
            modifier = modifier.fillMaxWidth().semantics { contentDescription = label },
            singleLine = true, enabled = enabled,
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

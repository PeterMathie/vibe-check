package com.petermathie.vibecheck.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.*
import com.petermathie.vibecheck.ui.components.VibeSurface
import com.petermathie.vibecheck.ui.theme.VibeSurfaceLevel
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.workout.*
import com.petermathie.vibecheck.ui.theme.VibeSurfaceState
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId
import android.os.SystemClock

fun emptySet(id: String, ordinal: Int) = WorkoutSetEntity(newId(), id, ordinal, "WORKING", "COMPLETED", null, null, null, null, null, null, null, null, null, null, null, null, null, "", System.currentTimeMillis(), System.currentTimeMillis())
fun emptyEntryDraft(id: String, ordinal: Int) = WorkoutEntryDraftEntity(id, newId(), ordinal, "", "", false, false, false, "[]", null, "", "", "", "", "", "cm", System.currentTimeMillis())

internal fun newSetEntryDraft(
    workoutExerciseId: String,
    ordinal: Int,
    previous: WorkoutEntryDraftEntity?,
    defaultVariationId: String?,
    validVariationIds: Set<String>,
): WorkoutEntryDraftEntity {
    val variationId = previous?.variationId
        ?.takeIf { it in validVariationIds }
        ?: defaultVariationId?.takeIf { it in validVariationIds }
    return emptyEntryDraft(workoutExerciseId, ordinal).copy(variationId = variationId)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WorkoutEditor(
    vm: EditorViewModel,
    workoutId: String?,
    onChoose: () -> Unit,
    onFinish: (String) -> Unit,
    onDoneEditing: () -> Unit = onChoose,
    onDeleted: () -> Unit = onChoose,
) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val snapshots by vm.workoutExercises.collectAsStateWithLifecycle()
    val sets by vm.sets.collectAsStateWithLifecycle()
    val entryDrafts by vm.entryDrafts.collectAsStateWithLifecycle()
    val definitions by vm.exercises.collectAsStateWithLifecycle()
    val measurements by vm.measurements.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var restRemaining by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) { while(true) { restRemaining=(context.getSharedPreferences("settings",0).getLong("restEnd",0)-System.currentTimeMillis()).coerceAtLeast(0)/1000;kotlinx.coroutines.delay(500) } }
    val workout = workouts.find { it.id == workoutId }
    var add by remember { mutableStateOf(false) }
    var bodyweight by remember { mutableStateOf(false) }
    var editDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (workout != null) {
        RegisterAppFabAction(
            owner = Destination.ACTIVE_WORKOUT,
            destination = AppFabDestination.AddWorkoutExercise,
            visible = !add && !bodyweight && !editDate && !confirmDelete,
        ) {
            add = true
        }
    }
    if (workout == null) {
        Button(
            onClick = onChoose,
            modifier = Modifier.padding(16.dp),
            shape = MaterialTheme.shapes.medium,
        ) { Text("Choose a programme") }
        return
    }
    LaunchedEffect(workout.id, measurements) {
        if (workout.bodyweightKg == null) measurements.filter { it.metric == "Bodyweight" }.minByOrNull { kotlin.math.abs(it.recordedAt - workout.startedAt) }?.let {
            vm.save(workout.copy(bodyweightKg = if (it.unit == "lb") it.value / 2.2046226218 else it.value))
        }
    }
    val rows = snapshots.filter { it.workoutId == workout.id }.sortedBy { it.position }
    val logged = sets.filter { s -> rows.any { it.id == s.workoutExerciseId } }
    val previousWorkouts = workouts.filter {
        it.status == "FINISHED" && it.finishedAt != null && it.finishedAt < workout.startedAt
    }.associateBy { it.id }
    val validEntryRows = remember(workout.id) { mutableStateMapOf<String, Boolean>() }
    val collapsedRows = remember(workout.id) { mutableStateMapOf<String, Boolean>() }
    LazyColumn(
        Modifier
            .fillMaxSize()
            .imePadding()
            .semantics { contentDescription = "Workout exercise list" },
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 12.dp + LocalAppFabClearance.current,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(workout.name, style = MaterialTheme.typography.headlineSmall)
            if(restRemaining>0) VibeActionButton("Rest: ${restRemaining/60}:${(restRemaining%60).toString().padStart(2,'0')} · cancel", { RestTimer.cancel(context) }, importance = ActionImportance.COMPACT)
            if (logged.isNotEmpty()) Text("Logged duration: ${((logged.maxOf { it.loggedAt } - logged.minOf { it.loggedAt }) / 60000)} min")
            VibeActionButton(
                "Bodyweight: ${workout.bodyweightKg?.let(::formatBodyweight) ?: "—"} kg",
                { bodyweight = true },
                importance = ActionImportance.COMPACT,
            )
            if(workout.status=="FINISHED") VibeActionButton("Change workout date", { editDate=true }, importance = ActionImportance.COMPACT)
        }
        rows.forEach { row ->
            val exercise = definitions.find { it.id == row.actualExerciseId }?.let { if(row.exerciseName.isNotBlank())it.copy(canonicalName=row.exerciseName,trackingType=row.trackingType) else it }
            val previous = snapshots.filter {
                it.actualExerciseId == row.actualExerciseId && it.workoutId in previousWorkouts
            }.sortedByDescending {
                previousWorkouts[it.workoutId]?.finishedAt
            }.flatMap { snapshot ->
                sets.filter { it.workoutExerciseId == snapshot.id }.sortedBy { it.ordinal }
            }
            val restSeconds=if(row.supersetGroup==null)row.restSeconds else rows.filter { it.supersetGroup==row.supersetGroup }.maxOf { it.restSeconds }
            stickyHeader(key = "${row.id}:heading") {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            exercise?.canonicalName.orEmpty(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(onClick = { RestTimer.start(context, restSeconds) }) {
                            Icon(Icons.Outlined.Timer, "Start $restSeconds second rest for ${exercise?.canonicalName.orEmpty()}")
                        }
                        val collapsed = collapsedRows[row.id] == true
                        IconButton(onClick = { collapsedRows[row.id] = !collapsed }) {
                            Icon(
                                if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                                "${if (collapsed) "Expand" else "Collapse"} ${exercise?.canonicalName.orEmpty()}",
                            )
                        }
                    }
                }
            }
            item(key = row.id) {
                if (collapsedRows[row.id] != true) WorkoutExerciseCard(
                    vm,
                    row,
                    exercise,
                    sets.filter { it.workoutExerciseId == row.id },
                    previous,
                    onInputValidityChanged = { validEntryRows[row.id] = it },
                )
            }
        }
        item {
            val workoutRowIds = rows.mapTo(mutableSetOf()) { it.id }
            val canFinish = validEntryRows.values.none { !it } &&
                entryDrafts.none { it.workoutExerciseId in workoutRowIds && hasInvalidQuantitativeInput(it) } &&
                logged.any { set ->
                set.result == "COMPLETED" && listOf(
                    set.reps,
                    set.holdMillis?.toDouble(),
                    set.leftReps?.toDouble(),
                    set.rightReps?.toDouble(),
                    set.leftHoldMillis?.toDouble(),
                    set.rightHoldMillis?.toDouble(),
                    set.romValue,
                ).any { (it ?: 0.0) > 0.0 }
            }
            Button(
                onClick = { if (workout.status == "FINISHED") onDoneEditing() else onFinish(workout.id) },
                enabled = workout.status == "FINISHED" || canFinish,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) { Text(if (workout.status == "FINISHED") "Done editing" else "Finish workout") }
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Delete workout")
            }
        }
    }
    if (bodyweight) {
        QuantitativeValueDialog(
            title = "Bodyweight in kg",
            initial = workout.bodyweightKg?.let(::formatQuantitative).orEmpty(),
            onDismiss = { bodyweight = false },
        ) { value ->
            vm.save(workout.copy(bodyweightKg = value))
            bodyweight = false
        }
    }
    if(editDate)NameDialog("Workout date (YYYY-MM-DD)",Instant.ofEpochMilli(workout.finishedAt ?: workout.startedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString(),{editDate=false}) { value ->
        runCatching { java.time.LocalDate.parse(value) }.getOrNull()?.let { date ->
            val old=Instant.ofEpochMilli(workout.finishedAt ?: workout.startedAt).atZone(ZoneId.systemDefault())
            val end=date.atTime(old.toLocalTime()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            vm.changeWorkoutDate(workout,end);editDate=false
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete workout?") },
            text = { Text("Are you sure you want to delete “${workout.name}”? This cannot be undone.") },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("No") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        vm.removeWorkout(workout.id, onDeleted)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Yes, delete") }
            },
        )
    }
    if (add) ExercisePicker(vm, { add = false }) { e -> vm.save(WorkoutExerciseEntity(newId(), workout.id, e.id, e.id, rows.size, "", 120, null)); add = false }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WorkoutExerciseCard(
    vm: EditorViewModel,
    row: WorkoutExerciseEntity,
    exercise: ExerciseEntity?,
    sets: List<WorkoutSetEntity>,
    previous: List<WorkoutSetEntity>,
    onInputValidityChanged: (Boolean) -> Unit,
) {
    val variations by vm.variations.collectAsStateWithLifecycle()
    val availableBands by vm.bands.collectAsStateWithLifecycle()
    var notes by rememberSaveable(row.id) { mutableStateOf(row.notes) }
    var draftLoaded by remember(row.id, row.actualExerciseId) { mutableStateOf(false) }
    var notesOpen by rememberSaveable(row.id) { mutableStateOf(row.notes.isNotBlank()) }
    var activeOrdinal by rememberSaveable(row.id) { mutableIntStateOf(1) }
    var focusNext by remember { mutableStateOf(false) }
    var visibleRows by rememberSaveable(row.id) { mutableIntStateOf(0) }
    val drafts = remember(row.id, row.actualExerciseId) { mutableStateMapOf<Int, WorkoutEntryDraftEntity>() }
    val context = LocalContext.current
    val preferredWeightUnit = if (context.getSharedPreferences("settings", 0).getBoolean("lb", false)) "lb" else "kg"
    val exerciseVariations = variations.filter { it.exerciseId == row.actualExerciseId }.sortedBy { it.progressionRank }
    val defaultVariation = exerciseVariations.firstOrNull()
    val variationIds = exerciseVariations.map { it.id }
    val validVariationIds = variationIds.toSet()
    fun variationFor(id: String?) = exerciseVariations.find { it.id == id }
    fun configFor(id: String?, ordinal: Int? = null): ExerciseInputConfig {
        val snapshot = ordinal?.let { value -> sets.find { it.ordinal == value && it.variationId == id } }
        val variation = variationFor(id)
        return ExerciseInputConfig.decode(
            snapshot?.variationInputConfigSnapshot?.takeIf { it.isNotBlank() }
                ?: variation?.inputConfig?.ifBlank { row.inputConfig }
                ?: row.inputConfig,
            snapshot?.variationTrackingTypeSnapshot?.takeIf { it.isNotBlank() }
                ?: variation?.trackingType?.ifBlank { exercise?.trackingType.orEmpty() }
                ?: exercise?.trackingType.orEmpty(),
            exerciseId = if (snapshot == null) row.actualExerciseId else null,
            defaultManyTuts = if (snapshot != null) false else ExerciseInputConfig.decode(
                row.inputConfig, row.trackingType, row.actualExerciseId,
            ).manyTuts,
        )
    }
    var stopwatchOrdinal by remember { mutableStateOf<Int?>(null) }
    val targetSets = row.targetSets
        ?: row.targets.substringBefore(" sets").toIntOrNull()?.coerceAtLeast(1)
        ?: 3
    LaunchedEffect(row.id, row.actualExerciseId, variationIds) {
        val recovered = vm.entryDrafts(row.id)
        val savedBands = vm.bandsForExercise(row.id)
        sets.forEach { set ->
            val config = configFor(set.variationId, set.ordinal)
            drafts[set.ordinal] = draftFromSet(
                set,
                config.timeHeld,
                config.weightUnit,
                savedBands.filter { it.setId == set.id }.sortedBy { it.ordinal }.map { it.bandId },
                preferredWeightUnit,
            )
        }
        recovered.sortedBy { it.ordinal }.forEach { draft ->
            val variationId = draft.variationId?.takeIf { it in validVariationIds }
                ?: drafts[draft.ordinal - 1]?.variationId
                ?: defaultVariation?.id
            drafts[draft.ordinal] = draft.copy(detailsOpen = false, variationId = variationId)
        }
        drafts.keys.sorted().forEach { ordinal ->
            val draft = drafts[ordinal] ?: return@forEach
            val variationId = draft.variationId?.takeIf { it in validVariationIds }
                ?: drafts[ordinal - 1]?.variationId?.takeIf { it in validVariationIds }
                ?: defaultVariation?.id
            if (variationId != draft.variationId) {
                drafts[ordinal] = draft.copy(variationId = variationId)
            }
        }
        visibleRows = maxOf(targetSets, sets.maxOfOrNull { it.ordinal } ?: 0, recovered.maxOfOrNull { it.ordinal } ?: 0)
        activeOrdinal = (1..visibleRows).firstOrNull { ordinal -> sets.none { it.ordinal == ordinal } } ?: visibleRows
        draftLoaded = true
    }
    fun draftAt(ordinal: Int) = drafts[ordinal] ?: newSetEntryDraft(
        workoutExerciseId = row.id,
        ordinal = ordinal,
        previous = drafts[ordinal - 1],
        defaultVariationId = defaultVariation?.id,
        validVariationIds = validVariationIds,
    )
        .also { drafts[ordinal] = it }
    fun performanceParts(draft: WorkoutEntryDraftEntity): Pair<String,String> {
        val inputConfig = configFor(draft.variationId, draft.ordinal)
        val weighted = inputConfig.weightUnit != null
        if (!weighted) return "" to if (inputConfig.reps) draft.performance else ""
        val parts = draft.performance.split(Regex("\\s*[x×]\\s*"), limit = 2)
        return parts.getOrElse(0) { "" } to parts.getOrElse(1) { "" }
    }
    fun attemptsFor(draft: WorkoutEntryDraftEntity): List<String> =
        timingInputs(draft.tutEntries).ifEmpty { listOf(draft.timeHeld) }
    fun inputError(draft: WorkoutEntryDraftEntity): String? {
        val inputConfig = configFor(draft.variationId, draft.ordinal)
        val (resistance, reps) = performanceParts(draft)
        val values = buildList {
            if (inputConfig.weightUnit != null) add(resistance)
            if (inputConfig.reps) add(reps)
            addAll(attemptsFor(draft))
            if (inputConfig.timeUnderTension) add(draft.timeUnderTension)
            if (inputConfig.addedWeight) add(draft.addedWeight)
        }
        if (values.any { it.isNotEmpty() && !quantitativeInput(it).isValid }) {
            return "Complete or correct the highlighted number"
        }
        if (draft.rpe.isNotEmpty() && !quantitativeInput(draft.rpe, 10.0).isValid) {
            return "RPE must be a decimal from 0 to 10"
        }
        return null
    }
    fun parsedSet(pending: WorkoutEntryDraftEntity): WorkoutSetEntity? {
        if (inputError(pending) != null) return null
        val inputConfig = configFor(pending.variationId, pending.ordinal)
        val (resistance, reps) = performanceParts(pending)
        val base = sets.find { it.ordinal == pending.ordinal }
            ?: emptySet(row.id, pending.ordinal).copy(id = pending.setId)
        val parsed = base.copy(
            weightKg = if (inputConfig.weightUnit == null) base.weightKg else quantitativeInput(resistance).value?.let {
                if (inputConfig.weightUnit == "lb") it / 2.2046226218 else it
            },
            reps = if (inputConfig.reps) quantitativeInput(reps).value else base.reps,
            variationId = pending.variationId,
            holdMillis = attemptsFor(pending).mapNotNull { quantitativeInput(it).value?.times(1000)?.toLong() }
                .takeIf { it.isNotEmpty() }?.sum(),
            tutEntriesMillis = encodeTimingMillis(
                attemptsFor(pending).mapNotNull { quantitativeInput(it).value?.times(1000)?.toLong() },
            ),
            timeUnderTensionMillis = quantitativeInput(pending.timeUnderTension).value?.times(1000)?.toLong(),
            bandResistance = inputConfig.bandResistance,
            addedWeightKg = quantitativeInput(pending.addedWeight).value?.let {
                if (preferredWeightUnit == "lb") it / 2.2046226218 else it
            },
            rpe = quantitativeInput(pending.rpe, 10.0).value,
            updatedAt = System.currentTimeMillis(),
        )
        if (
            parsed.weightKg == null &&
            parsed.reps == null &&
            parsed.holdMillis == null &&
            parsed.timeUnderTensionMillis == null &&
            parsed.addedWeightKg == null &&
            bandIds(pending.bandIds).isEmpty()
        ) return null
        return parsed
    }
    fun updateDraft(updated: WorkoutEntryDraftEntity) {
        drafts[updated.ordinal] = updated
        val parsed = parsedSet(updated)
        if (parsed == null) {
            vm.saveEntryDraft(updated)
        } else {
            vm.submitEntryDraft(parsed, bandIds(updated.bandIds)) {}
        }
    }
    fun updatePerformance(ordinal: Int, resistance: String, result: String) {
        val weighted = configFor(draftAt(ordinal).variationId, ordinal).weightUnit != null
        val performance = if (weighted) {
            if (resistance.isBlank() && result.isBlank()) "" else "$resistance x $result"
        } else result
        updateDraft(draftAt(ordinal).copy(performance = performance, updatedAt = System.currentTimeMillis()))
    }
    fun openNextEmpty(ordinal: Int) {
        val draft = draftAt(ordinal)
        if (inputError(draft) != null || parsedSet(draft) == null) return
        val next = (ordinal + 1..visibleRows).firstOrNull {
            parsedSet(draftAt(it)) == null
        } ?: (1 until ordinal).firstOrNull {
            parsedSet(draftAt(it)) == null
        }
        if (next != null) {
            activeOrdinal = next
            focusNext = true
        }
    }
    SideEffect {
        onInputValidityChanged(drafts.values.none { inputError(it) != null })
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if(row.targets.isNotBlank())Text(row.targets,style=MaterialTheme.typography.bodySmall)
        row.supersetGroup?.let { Text("Circuit: $it · rest after round", style = MaterialTheme.typography.labelSmall) }
        repeat(visibleRows) { index ->
            val ordinal = index + 1
            val draft = draftAt(ordinal)
            val inputConfig = configFor(draft.variationId, ordinal)
            val weighted = inputConfig.weightUnit != null
            val (resistance,result) = performanceParts(draft)
            val isActive = activeOrdinal == ordinal
            val recorded = sets.find { it.ordinal == ordinal }
            val attempts = attemptsFor(draft)
            VibeSurface(
                level = if (isActive) VibeSurfaceLevel.RAISED else VibeSurfaceLevel.CARD,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.shapes.medium)
                    .semantics { contentDescription = "Set $ordinal for ${exercise?.canonicalName.orEmpty()}" },
            ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = if (!isActive && recorded == null) 0.dp else 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    if (recorded != null) {
                        Text(
                            setDescription(recorded, includeTimingAttempts = false, labelTimingTotal = true),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (exerciseVariations.isNotEmpty()) {
                        var variationMenu by remember(ordinal) { mutableStateOf(false) }
                        Box {
                            IconButton(
                                enabled = draftLoaded,
                                onClick = { variationMenu = true },
                            ) {
                                Icon(Icons.Outlined.Tune, "Change variant for ${exercise?.canonicalName.orEmpty()} set $ordinal")
                            }
                            DropdownMenu(expanded = variationMenu, onDismissRequest = { variationMenu = false }) {
                                exerciseVariations.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.name) },
                                        modifier = Modifier.semantics { selected = option.id == draft.variationId },
                                        leadingIcon = if (option.id == draft.variationId) {
                                            { Icon(Icons.Outlined.Check, null) }
                                        } else null,
                                        onClick = {
                                            val nextConfig = configFor(option.id, ordinal)
                                            val previousWeight = quantitativeInput(resistance).value?.let {
                                                if (inputConfig.weightUnit == "lb") it / 2.2046226218 else it
                                            } ?: recorded?.weightKg
                                            val nextWeight = previousWeight?.let {
                                                formatQuantitative(if (nextConfig.weightUnit == "lb") it * 2.2046226218 else it)
                                            }.orEmpty()
                                            val nextReps = result.ifBlank { recorded?.reps?.let(::formatQuantitative).orEmpty() }
                                            val nextPerformance = if (nextConfig.weightUnit == null) nextReps else {
                                                if (nextWeight.isBlank() && nextReps.isBlank()) "" else "$nextWeight x $nextReps"
                                            }
                                            updateDraft(draft.copy(
                                                variationId = option.id,
                                                performance = nextPerformance,
                                                updatedAt = System.currentTimeMillis(),
                                            ))
                                            activeOrdinal = ordinal
                                            variationMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (isActive) SetIconButton(
                        label = "Next set",
                        description = "Complete set $ordinal for ${exercise?.canonicalName.orEmpty()} and open next empty set",
                        icon = Icons.Outlined.Check,
                        enabled = inputError(draft) == null && parsedSet(draft) != null,
                        onClick = { openNextEmpty(ordinal) },
                    )
                    IconButton(onClick = { activeOrdinal = if (isActive) 0 else ordinal }) {
                        Icon(
                            if (isActive) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            "${if (isActive) "Close" else "Open"} set $ordinal for ${exercise?.canonicalName.orEmpty()}",
                        )
                    }
                    if (recorded != null && isActive) {
                        IconButton(onClick = {
                            sets.find { it.ordinal == ordinal }?.let { vm.removeSet(it.id) }
                            vm.discardEntryDraft(row.id, ordinal)
                            drafts[ordinal] = newSetEntryDraft(
                                row.id,
                                ordinal,
                                drafts[ordinal - 1],
                                defaultVariation?.id,
                                validVariationIds,
                            )
                        }) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "Remove set $ordinal for ${exercise?.canonicalName.orEmpty()}",
                            )
                        }
                    }
                }
                if (isActive) {
                val focusRequester = remember(ordinal) { FocusRequester() }
                LaunchedEffect(isActive, focusNext) {
                    if (focusNext) {
                        focusRequester.requestFocus()
                        focusNext = false
                    }
                }
                val comparable = previous.filter {
                    it.variationId == draft.variationId && it.setType == "WORKING" && it.result == "COMPLETED"
                }
                val latest = comparable.firstOrNull()
                val previousSet = comparable.firstOrNull {
                    it.workoutExerciseId == latest?.workoutExerciseId && it.ordinal == ordinal
                } ?: latest
                previousSet?.let {
                    Text(
                        "Previous: ${setDescription(it, includeTimingAttempts = false)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics {
                            contentDescription = "Previous performance for ${exercise?.canonicalName.orEmpty()} set $ordinal"
                        },
                    )
                }
                if (inputConfig.bodyweight) {
                    Text("Bodyweight: tracked from workout", style = MaterialTheme.typography.labelMedium)
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    maxItemsInEachRow = 3,
                ) {
                    if (weighted) QuantitativeTextField(
                        value = resistance,
                        onValueChange = { updatePerformance(ordinal, it, result) },
                        enabled = draftLoaded,
                        label = inputConfig.weightUnit.orEmpty(),
                        description = "Resistance for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                        modifier = Modifier.width(96.dp).weight(1f).focusRequester(focusRequester),
                    )
                    if (inputConfig.reps) QuantitativeTextField(
                        value = result,
                        onValueChange = { updatePerformance(ordinal, resistance, it) },
                        enabled = draftLoaded,
                        label = "Reps",
                        description = "Reps for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                        modifier = Modifier.width(96.dp).weight(1f).then(if (!weighted) Modifier.focusRequester(focusRequester) else Modifier),
                    )
                    if ((inputConfig.timeHeld || inputConfig.manyTuts || attempts.any { it.isNotBlank() }) && attempts.size == 1) QuantitativeTextField(
                        value = attempts.first(),
                        onValueChange = {
                            updateDraft(draft.copy(tutEntries = encodeTimingInputs(listOf(it)), timeHeld = "", updatedAt = System.currentTimeMillis()))
                        },
                        enabled = draftLoaded,
                        label = "TUT (s)",
                        description = "Time Under Tension for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                        modifier = Modifier.width(96.dp).weight(1f).then(if (!weighted && !inputConfig.reps) Modifier.focusRequester(focusRequester) else Modifier),
                    )
                    if (inputConfig.timeUnderTension) QuantitativeTextField(
                        value = draft.timeUnderTension,
                        onValueChange = {
                            updateDraft(draft.copy(timeUnderTension = it, updatedAt = System.currentTimeMillis()))
                        },
                        label = "Total",
                        description = "Total Time for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                        modifier = Modifier.width(96.dp).weight(1f).then(
                            if (!weighted && !inputConfig.reps && !inputConfig.timeHeld && !inputConfig.manyTuts && attempts.all { it.isBlank() }) Modifier.focusRequester(focusRequester) else Modifier,
                        ),
                        trailingIcon = {
                            IconButton(onClick = { stopwatchOrdinal = ordinal }) {
                                Icon(Icons.Outlined.Timer, contentDescription = "Open Total Time stopwatch for set $ordinal")
                            }
                        },
                    )
                    QuantitativeTextField(
                        value = draft.rpe,
                        onValueChange = {
                            updateDraft(draft.copy(rpe = it, updatedAt = System.currentTimeMillis()))
                        },
                        enabled = draftLoaded,
                        label = "RPE",
                        description = "RPE for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                        maximum = 10.0,
                        modifier = Modifier.width(76.dp).weight(1f),
                        onNext = { openNextEmpty(ordinal) },
                    )
                }
                if (attempts.size > 1) {
                    Text("TUT (s)", style = MaterialTheme.typography.labelMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        attempts.chunked(4).forEachIndexed { rowIndex, entries ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                repeat(4) { columnIndex ->
                                    val index = rowIndex * 4 + columnIndex
                                    if (columnIndex < entries.size) {
                                        QuantitativeTextField(
                                            value = entries[columnIndex],
                                            onValueChange = { text ->
                                                updateDraft(draft.copy(
                                                    tutEntries = encodeTimingInputs(attempts.toMutableList().apply { set(index, text) }),
                                                    timeHeld = "",
                                                    updatedAt = System.currentTimeMillis(),
                                                ))
                                            },
                                            label = "${index + 1}",
                                            description = "TUT attempt ${index + 1} for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                                            modifier = Modifier.weight(1f).then(
                                                if (index == 0 && !weighted && !inputConfig.reps) Modifier.focusRequester(focusRequester) else Modifier,
                                            ),
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                if (inputConfig.manyTuts || attempts.size > 1) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            "${attempts.size}/$MAX_TUT_ENTRIES",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (inputConfig.manyTuts) SetIconButton(
                            label = "Add time",
                            description = "Add TUT entry for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                            icon = Icons.Outlined.Add,
                            enabled = attempts.size < MAX_TUT_ENTRIES,
                            onClick = {
                                updateDraft(draft.copy(tutEntries = encodeTimingInputs(attempts + ""), timeHeld = "", updatedAt = System.currentTimeMillis()))
                            },
                        )
                    }
                    if (!inputConfig.manyTuts && attempts.size > 1) Text(
                        "One-entry mode. Previously recorded attempts are retained.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (inputConfig.addedWeight) QuantitativeTextField(
                    value = draft.addedWeight,
                    onValueChange = { updateDraft(draft.copy(addedWeight = it, updatedAt = System.currentTimeMillis())) },
                    label = "Added weight ($preferredWeightUnit)",
                    description = "Added weight for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                    modifier = Modifier.fillMaxWidth(),
                )
                inputError(draft)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (inputConfig.bandResistance) {
                    Text("Band resistance", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        availableBands.forEach { band ->
                            val selected = band.id in bandIds(draft.bandIds)
                            VibeSurface(
                                level = if (selected) VibeSurfaceLevel.INSET else VibeSurfaceLevel.RAISED,
                                state = if (selected) VibeSurfaceState.SELECTED else VibeSurfaceState.RESTING,
                                shape = MaterialTheme.shapes.medium,
                                onClick = {
                                    val values = bandIds(draft.bandIds).toMutableList()
                                    if (selected) values.remove(band.id) else values.add(band.id)
                                    updateDraft(draft.copy(bandIds = encodeBandIds(values), updatedAt = System.currentTimeMillis()))
                                },
                                modifier = Modifier
                                    .shadow(if (selected) 0.dp else 4.dp, MaterialTheme.shapes.medium)
                                    .clip(MaterialTheme.shapes.medium)
                                    .drawWithContent {
                                        drawContent()
                                        if (selected) {
                                            drawRect(
                                                Brush.verticalGradient(
                                                    listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent),
                                                    endY = 8.dp.toPx(),
                                                ),
                                                size = Size(size.width, 8.dp.toPx()),
                                            )
                                        }
                                    }
                                    .semantics {
                                    contentDescription = "${band.name} band for ${exercise?.canonicalName.orEmpty()} set $ordinal"
                                    stateDescription = if (selected) "Selected, depressed" else "Not selected, raised"
                                },
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    if (selected) Icon(Icons.Outlined.Check, null, Modifier.size(16.dp))
                                    Text(band.name, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
                }
            }
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(
                onClick = {
                    val ordinal = visibleRows + 1
                    visibleRows = ordinal
                    activeOrdinal = ordinal
                    updateDraft(
                        newSetEntryDraft(
                            row.id,
                            ordinal,
                            drafts[ordinal - 1],
                            defaultVariation?.id,
                            validVariationIds,
                        ),
                    )
                },
                modifier = Modifier.semantics { contentDescription = "Add set for ${exercise?.canonicalName.orEmpty()}" },
            ) {
                Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add set")
            }
            TextButton(
                onClick = { notesOpen = !notesOpen },
                modifier = Modifier.semantics {
                    contentDescription = "${if (notesOpen) "Hide" else "Show"} notes for ${exercise?.canonicalName.orEmpty()}"
                },
            ) {
                Icon(Icons.Outlined.EditNote, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (notesOpen) "Hide notes" else "Notes")
            }
        }
        if (notesOpen) OutlinedTextField(notes, { notes = it; vm.save(row.copy(notes = it)) }, label = { Text("Exercise notes") }, modifier = Modifier.fillMaxWidth())
    }
    stopwatchOrdinal?.let { ordinal ->
        StopwatchDialog(
            initialMillis = quantitativeInput(draftAt(ordinal).timeUnderTension).value?.times(1000)?.toLong() ?: 0L,
            onDismiss = { stopwatchOrdinal = null },
            onApply = { millis ->
                val draft = draftAt(ordinal)
                updateDraft(
                    draft.copy(
                        timeUnderTension = "%.1f".format(java.util.Locale.US, millis / 1000.0),
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                stopwatchOrdinal = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetIconButton(
    label: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = description)
        }
    }
}

@Composable
private fun QuantitativeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maximum: Double = 1_000_000_000.0,
    trailingIcon: (@Composable () -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    val parsed = quantitativeInput(value, maximum)
    val error = when {
        value.isEmpty() -> null
        parsed.error != null -> parsed.error
        parsed.isTransient -> "Complete the number"
        else -> null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = {
            Text(
                label,
                maxLines = 1,
                softWrap = false,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        supportingText = if (error == null) null else ({ Text(error) }),
        isError = error != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (onNext == null) ImeAction.Default else ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext?.invoke() }),
        trailingIcon = trailingIcon,
        singleLine = true,
        modifier = modifier.semantics {
            contentDescription = description
            stateDescription = error ?: "Valid number"
        },
    )
}

@Composable
private fun QuantitativeValueDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }
    val parsed = quantitativeInput(text)
    val error = when {
        text.isEmpty() -> "Enter a value"
        parsed.error != null -> parsed.error
        parsed.isTransient -> "Complete the number"
        parsed.value == 0.0 -> "Enter a value greater than zero"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                isError = error != null,
                supportingText = { Text(error.orEmpty()) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.semantics {
                    contentDescription = title
                    stateDescription = error ?: "Valid number"
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed.value?.let(onSave) },
                enabled = error == null,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StopwatchDialog(initialMillis: Long, onDismiss: () -> Unit, onApply: (Long) -> Unit) {
    var accumulated by remember { mutableLongStateOf(initialMillis) }
    var startedAt by remember { mutableStateOf<Long?>(null) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(startedAt) {
        while (startedAt != null) {
            now = SystemClock.elapsedRealtime()
            kotlinx.coroutines.delay(50)
        }
    }
    val elapsed = accumulated + (startedAt?.let { now - it } ?: 0L)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Total Time stopwatch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "%02d:%05.2f".format(java.util.Locale.US, elapsed / 60_000, (elapsed % 60_000) / 1000.0),
                    style = MaterialTheme.typography.displaySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val start = startedAt
                        if (start == null) {
                            now = SystemClock.elapsedRealtime()
                            startedAt = now
                        } else {
                            accumulated += SystemClock.elapsedRealtime() - start
                            startedAt = null
                        }
                    }, shape = MaterialTheme.shapes.medium) { Text(if (startedAt == null) "Start" else "Stop") }
                    OutlinedButton(
                        onClick = { accumulated = 0; startedAt = null },
                        shape = MaterialTheme.shapes.medium,
                    ) { Text("Reset") }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onApply(elapsed) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun draftFromSet(set:WorkoutSetEntity,hold:Boolean,weightUnit:String?,bands:List<String> = emptyList(), addedWeightUnit: String = "kg"):WorkoutEntryDraftEntity {
    val reps=set.reps ?: listOfNotNull(set.leftReps,set.rightReps).minOrNull()
    val performance=when {
        weightUnit != null -> "${set.weightKg?.let { if (weightUnit == "lb") it * 2.2046226218 else it }?.let(::formatQuantitative) ?: ""} x ${reps?.let(::formatQuantitative) ?: ""}"
        else -> reps?.let(::formatQuantitative).orEmpty()
    }
    return emptyEntryDraft(set.workoutExerciseId,set.ordinal).copy(
        setId=set.id,
        performance=performance,
        rpe=set.rpe?.toString().orEmpty(),
        warmUp=set.setType=="WARM_UP",
        failed=set.result=="FAILED",
        bandIds=encodeBandIds(bands),
        variationId=set.variationId,
        leftValue=(if(hold)set.leftHoldMillis?.div(1000.0) else set.leftReps)?.toString().orEmpty(),
        rightValue=(if(hold)set.rightHoldMillis?.div(1000.0) else set.rightReps)?.toString().orEmpty(),
        addedWeight=set.addedWeightKg?.let { if (addedWeightUnit == "lb") it * 2.2046226218 else it }?.let(::formatQuantitative).orEmpty(),
        assistance=set.assistanceKg?.toString().orEmpty(),
        romValue=set.romValue?.toString().orEmpty(),
        romUnit=set.romUnit ?: "cm",
        timeHeld=set.holdMillis?.div(1000.0)?.toString().orEmpty(),
        timeUnderTension=set.timeUnderTensionMillis?.div(1000.0)?.toString().orEmpty(),
        tutEntries = timingMillis(set.tutEntriesMillis).takeIf { it.isNotEmpty() }
            ?.map { formatQuantitative(it / 1000.0) }?.let(::encodeTimingInputs).orEmpty(),
    )
}

fun setDescription(s: WorkoutSetEntity, includeTimingAttempts: Boolean = true, labelTimingTotal: Boolean = false): String = when {
    s.romValue != null -> "ROM ${s.romValue} ${s.romUnit.orEmpty()}"
    s.leftReps != null || s.rightReps != null -> "L ${s.leftReps?.let(::formatQuantitative) ?: "0"} / R ${s.rightReps?.let(::formatQuantitative) ?: "0"} reps"
    s.leftHoldMillis != null || s.rightHoldMillis != null -> "L ${(s.leftHoldMillis ?: 0)/1000.0} / R ${(s.rightHoldMillis ?: 0)/1000.0}s"
    includeTimingAttempts && timingMillis(s.tutEntriesMillis).size > 1 -> timingMillis(s.tutEntriesMillis)
        .joinToString(" + ") { "${formatQuantitative(it / 1000.0)}s" } + " = ${formatQuantitative((s.holdMillis ?: 0) / 1000.0)}s"
    s.holdMillis != null -> "${if (labelTimingTotal) "TUT " else ""}${formatQuantitative(s.holdMillis/1000.0)}s"
    s.timeUnderTensionMillis != null -> "Total ${formatQuantitative(s.timeUnderTensionMillis/1000.0)}s"
    else -> "${s.weightKg?.let { "${formatQuantitative(it)} kg × " }.orEmpty()}${s.reps?.let(::formatQuantitative) ?: "0"} reps"
} + (if (s.holdMillis != null && s.timeUnderTensionMillis != null) " · Total ${formatQuantitative(s.timeUnderTensionMillis / 1000.0)}s" else "") +
    (if (s.setType == "WARM_UP") " · warm-up" else "") + s.addedWeightKg?.let { " +${it}kg" }.orEmpty() + s.assistanceKg?.let { " −${it}kg" }.orEmpty()

private fun bandIds(value: String): List<String> {
    val values = JSONArray(value)
    return (0 until values.length()).map(values::getString)
}

private fun encodeBandIds(values: List<String>): String =
    JSONArray().apply { values.forEach { put(it) } }.toString()

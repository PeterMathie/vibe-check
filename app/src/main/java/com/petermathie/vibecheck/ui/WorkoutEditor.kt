package com.petermathie.vibecheck.ui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.*
import com.petermathie.vibecheck.ui.components.VibeSurface
import com.petermathie.vibecheck.ui.theme.VibeSurfaceLevel
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.ExerciseMeasurement
import com.petermathie.vibecheck.domain.workout.*
import com.petermathie.vibecheck.ui.theme.VibeSurfaceState
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId
import android.os.SystemClock
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.launch

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
    val loggingPreferences = remember(context) { context.getSharedPreferences("workout_logging", 0) }
    var lockedSetIds by remember(workoutId) {
        mutableStateOf(loggingPreferences.getStringSet("locked_sets", emptySet()).orEmpty().toSet())
    }
    fun changeSetLocks(ids: Set<String>, locked: Boolean) {
        val updated = loggingPreferences.getStringSet("locked_sets", emptySet()).orEmpty().toMutableSet()
        if (locked) updated.addAll(ids) else updated.removeAll(ids)
        loggingPreferences.edit().putStringSet("locked_sets", updated).apply()
        lockedSetIds = updated
    }
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
    val exerciseSwipeStates = remember(workout.id) { mutableMapOf<String, StagedSwipeState>() }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var headingHeight by remember { mutableIntStateOf(0) }
    suspend fun removeExercise(row: WorkoutExerciseEntity) {
        vm.removeWorkoutExercise(row.id) {
            changeSetLocks(sets.filter { it.workoutExerciseId == row.id }.mapTo(mutableSetOf()) { it.id }, false)
            validEntryRows.remove(row.id)
            collapsedRows.remove(row.id)
            exerciseSwipeStates.remove(row.id)
        }.join()
    }
    Box(Modifier.fillMaxSize()) {
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
        state = listState,
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
            item(key = row.id) {
                val name = exercise?.canonicalName.orEmpty()
                val collapsed = collapsedRows[row.id] == true
                StagedSwipeToDelete(
                    "exercise $name",
                    { removeExercise(row) },
                    state = exerciseSwipeStates.getOrPut(row.id) { StagedSwipeState() },
                ) {
                    VibeSurface(
                        level = VibeSurfaceLevel.CARD,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.shapes.medium)
                            .panelToggle("Exercise $name", !collapsed) { collapsedRows[row.id] = !collapsed },
                    ) {
                        Column {
                            ExerciseHeading(name, !collapsed, { collapsedRows[row.id] = !collapsed })
                            if (!collapsed) WorkoutExerciseCard(
                                vm,
                                row,
                                exercise,
                                sets.filter { it.workoutExerciseId == row.id },
                                previous,
                                lockedSetIds = lockedSetIds,
                                onSetLockChanged = { id, locked -> changeSetLocks(setOf(id), locked) },
                                onInputValidityChanged = { validEntryRows[row.id] = it },
                                onCompleteSet = {
                                    if (workout.status == "DRAFT" && restSeconds > 0) RestTimer.start(context, restSeconds)
                                },
                            )
                        }
                    }
                }
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
    val rowIds = rows.map { it.id }
    val stickyItem by remember(listState, rowIds) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.firstOrNull {
                it.offset < 0 && it.key in rowIds
            }?.let { Triple(it.key, it.offset, it.size) }
        }
    }
    val currentStickyItem = stickyItem
    val stickyRow = rows.find { it.id == currentStickyItem?.first }
    if (stickyRow != null && currentStickyItem != null) {
        val name = stickyRow.exerciseName.ifBlank {
            definitions.find { it.id == stickyRow.actualExerciseId }?.canonicalName.orEmpty()
        }
        val expanded = collapsedRows[stickyRow.id] != true
        val offset = minOf(0, currentStickyItem.second + currentStickyItem.third - headingHeight)
        Box(
            Modifier.offset(y = with(density) { offset.toDp() })
                .padding(horizontal = 12.dp)
                .onSizeChanged { headingHeight = it.height },
        ) {
            StagedSwipeToDelete(
                "exercise $name",
                { removeExercise(stickyRow) },
                state = exerciseSwipeStates.getOrPut(stickyRow.id) { StagedSwipeState() },
            ) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    ExerciseHeading(name, expanded, { collapsedRows[stickyRow.id] = expanded }, pinned = true)
                }
            }
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
                        vm.removeWorkout(workout.id) {
                            changeSetLocks(logged.mapTo(mutableSetOf()) { it.id }, false)
                            onDeleted()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Yes, delete") }
            },
        )
    }
    if (add) ExercisePicker(vm, { add = false }) { e -> vm.save(WorkoutExerciseEntity(newId(), workout.id, e.id, e.id, rows.size, "", 120, null)); add = false }
}

private fun Modifier.panelToggle(label: String, expanded: Boolean, toggle: () -> Unit): Modifier =
    pointerInput(label, expanded) { detectTapGestures { toggle() } }
        .semantics {
            contentDescription = label
            stateDescription = if (expanded) "Expanded" else "Collapsed"
            role = Role.Button
            onClick(label = if (expanded) "Collapse" else "Expand") { toggle(); true }
        }

@Composable
private fun ExerciseHeading(name: String, expanded: Boolean, toggle: () -> Unit, pinned: Boolean = false) {
    Text(
        name,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .panelToggle("${if (pinned) "Pinned exercise" else "Exercise"} heading for $name", expanded, toggle)
            .padding(12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WorkoutExerciseCard(
    vm: EditorViewModel,
    row: WorkoutExerciseEntity,
    exercise: ExerciseEntity?,
    sets: List<WorkoutSetEntity>,
    previous: List<WorkoutSetEntity>,
    lockedSetIds: Set<String>,
    onSetLockChanged: (String, Boolean) -> Unit,
    onInputValidityChanged: (Boolean) -> Unit,
    onCompleteSet: () -> Unit,
) {
    val variations by vm.variations.collectAsStateWithLifecycle()
    val availableBands by vm.bands.collectAsStateWithLifecycle()
    var notes by rememberSaveable(row.id) { mutableStateOf(row.notes) }
    var draftLoaded by remember(row.id, row.actualExerciseId) { mutableStateOf(false) }
    var notesOpen by rememberSaveable(row.id) { mutableStateOf(row.notes.isNotBlank()) }
    var activeOrdinal by rememberSaveable(row.id) { mutableIntStateOf(1) }
    var expandedOrdinal by rememberSaveable(row.id) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var focusNext by remember { mutableStateOf(false) }
    var visibleRows by rememberSaveable(row.id) { mutableIntStateOf(0) }
    val drafts = remember(row.id, row.actualExerciseId) { mutableStateMapOf<Int, WorkoutEntryDraftEntity>() }
    val editedSetIds = remember(row.id, row.actualExerciseId) { mutableSetOf<String>() }
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
            defaultMeasurementOrder = if (snapshot != null) emptyList() else ExerciseInputConfig.decode(
                row.inputConfig, row.trackingType, row.actualExerciseId,
            ).measurementOrder,
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
            editedSetIds.add(draft.setId)
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
        editedSetIds.add(updated.setId)
        activeOrdinal = updated.ordinal
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
            val (resistance,result) = performanceParts(draft)
            val isActive = activeOrdinal == ordinal
            val isExpanded = expandedOrdinal == ordinal
            val isLocked = draft.setId in lockedSetIds
            val recorded = sets.find { it.ordinal == ordinal }
            val attempts = attemptsFor(draft)
            val fields = inputConfig.orderedMeasurements.filter {
                it != ExerciseMeasurement.BODYWEIGHT &&
                    (it in inputConfig.enabledMeasurements() ||
                    it == ExerciseMeasurement.TUT && attempts.any(String::isNotBlank)
                    )
            }.filterNot { it == ExerciseMeasurement.RPE }.sortedBy {
                when (it) {
                    ExerciseMeasurement.REPS, ExerciseMeasurement.TUT, ExerciseMeasurement.TOTAL_TIME -> 0
                    else -> 1
                }
            } + ExerciseMeasurement.RPE
            val firstInput = fields.firstOrNull {
                it != ExerciseMeasurement.BODYWEIGHT && it != ExerciseMeasurement.BANDS
            }
            val fieldScale = maxOf(1f, LocalDensity.current.fontScale / 1.5f)
            val variantName = recorded?.takeIf { it.variationId == draft.variationId }
                ?.variationNameSnapshot?.takeIf(String::isNotBlank)
                ?: variationFor(draft.variationId)?.name.orEmpty()
            val showVariant = isExpanded && exerciseVariations.isNotEmpty()
            val headingInset = if (exerciseVariations.isNotEmpty()) 8.dp * LocalDensity.current.fontScale else 0.dp
            key(draft.setId) {
            var confirming by remember { mutableStateOf(false) }
            StagedSwipeToDelete(
                label = "set $ordinal for ${exercise?.canonicalName.orEmpty()}",
                onDelete = {
                    vm.removeWorkoutSet(row.id, ordinal, visibleRows - 1) {
                        onSetLockChanged(draft.setId, false)
                        editedSetIds.remove(draft.setId)
                        val remaining = drafts.values.filter { it.ordinal != ordinal }.map {
                            if (it.ordinal > ordinal) it.copy(ordinal = it.ordinal - 1) else it
                        }
                        drafts.clear()
                        remaining.forEach { drafts[it.ordinal] = it }
                        visibleRows--
                        expandedOrdinal = when {
                            expandedOrdinal == ordinal -> 0
                            expandedOrdinal > ordinal -> expandedOrdinal - 1
                            else -> expandedOrdinal
                        }
                        activeOrdinal = when {
                            activeOrdinal > ordinal -> activeOrdinal - 1
                            activeOrdinal == ordinal -> minOf(ordinal, visibleRows)
                            else -> activeOrdinal
                        }
                    }.join()
                },
            ) {
            Box(Modifier.fillMaxWidth().padding(top = headingInset)) {
            VibeSurface(
                level = if (isActive) VibeSurfaceLevel.RAISED else VibeSurfaceLevel.CARD,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .border(1.dp, MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.shapes.medium)
                    .pointerInput(isExpanded) {
                        detectTapGestures {
                            activeOrdinal = ordinal
                            expandedOrdinal = if (isExpanded) 0 else ordinal
                        }
                    }
                    .semantics {
                        contentDescription = "Set $ordinal for ${exercise?.canonicalName.orEmpty()}"
                        stateDescription = "${if (isLocked) "Completed" else "Editing"}, ${if (isExpanded) "expanded" else "collapsed"}"
                        role = Role.Button
                        selected = isActive
                        onClick(label = if (isExpanded) "Hide details" else "Show details") {
                            activeOrdinal = ordinal
                            expandedOrdinal = if (isExpanded) 0 else ordinal
                            true
                        }
                    },
            ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = maxOf(8.dp, headingInset + 4.dp)),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val focusRequester = remember(ordinal) { FocusRequester() }
                LaunchedEffect(isActive, focusNext) {
                    if (isActive && focusNext && !isLocked) {
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
                SetMeasurementFlow(
                    expanded = isExpanded,
                    measurements = {
                    fields.forEach { field ->
                        if (isLocked && field != ExerciseMeasurement.BANDS) {
                            CompletedSetMeasurement(field, draft, resistance, result, attempts, inputConfig, preferredWeightUnit)
                        } else {
                        val focus = if (field == firstInput) Modifier.focusRequester(focusRequester) else Modifier
                        when (field) {
                            ExerciseMeasurement.BODYWEIGHT -> Unit
                            ExerciseMeasurement.BANDS -> availableBands.filter {
                                !isLocked || it.id in bandIds(draft.bandIds)
                            }.forEach { band ->
                                val selected = band.id in bandIds(draft.bandIds)
                                LoggingBandButton(
                                    band = band,
                                    selected = selected,
                                    enabled = draftLoaded && !isLocked,
                                    description = "${band.name} band for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                                    modifier = Modifier.width(40.dp * fieldScale),
                                ) {
                                    val ids = bandIds(draft.bandIds).toMutableList()
                                    if (selected) ids.remove(band.id) else ids.add(band.id)
                                    updateDraft(draft.copy(bandIds = encodeBandIds(ids), updatedAt = System.currentTimeMillis()))
                                }
                            }
                            ExerciseMeasurement.WEIGHT -> QuantitativeTextField(
                                value = resistance,
                                onValueChange = { updatePerformance(ordinal, it, result) },
                                enabled = draftLoaded,
                                label = inputConfig.weightUnit.orEmpty(),
                                description = "Resistance for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                                modifier = Modifier.width(64.dp * fieldScale).then(focus),
                            )
                            ExerciseMeasurement.REPS -> QuantitativeTextField(
                                value = result,
                                onValueChange = { updatePerformance(ordinal, resistance, it) },
                                enabled = draftLoaded,
                                label = "Reps",
                                description = "Reps for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                                modifier = Modifier.width(56.dp * fieldScale).then(focus),
                            )
                            ExerciseMeasurement.TUT -> if (!inputConfig.manyTuts && attempts.size == 1) QuantitativeTextField(
                                value = attempts.first(),
                                onValueChange = {
                                    updateDraft(draft.copy(tutEntries = encodeTimingInputs(listOf(it)), timeHeld = "", updatedAt = System.currentTimeMillis()))
                                },
                                enabled = draftLoaded,
                                label = "TUT (s)",
                                description = "Time Under Tension for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                                modifier = Modifier.width(56.dp * fieldScale).then(focus),
                            ) else {
                                attempts.forEachIndexed { index, entry ->
                                    QuantitativeTextField(
                                        value = entry,
                                        onValueChange = { text ->
                                            updateDraft(draft.copy(
                                                tutEntries = encodeTimingInputs(attempts.toMutableList().apply { set(index, text) }),
                                                timeHeld = "",
                                                updatedAt = System.currentTimeMillis(),
                                            ))
                                        },
                                        label = if (index == 0) "TUT s" else "",
                                        description = if (attempts.size == 1) {
                                            "Time Under Tension for ${exercise?.canonicalName.orEmpty()} set $ordinal"
                                        } else "TUT attempt ${index + 1} for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                                        modifier = Modifier.width(50.dp * fieldScale).layoutId("tut")
                                            .then(if (index == 0) focus else Modifier),
                                    )
                                }
                                Text("${attempts.size}/$MAX_TUT_ENTRIES", style = MaterialTheme.typography.labelMedium)
                                val values = attempts.map { quantitativeInput(it) }
                                if (values.none { !it.isValid } && values.any { it.value != null }) Text(
                                    " · TUT ${formatQuantitative(values.sumOf { it.value ?: 0.0 })}s",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.semantics {
                                        contentDescription = "TUT total for ${exercise?.canonicalName.orEmpty()} set $ordinal"
                                    },
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
                            ExerciseMeasurement.TOTAL_TIME -> QuantitativeTextField(
                                value = draft.timeUnderTension,
                                onValueChange = {
                                    updateDraft(draft.copy(timeUnderTension = it, updatedAt = System.currentTimeMillis()))
                                },
                                label = "Total",
                                description = "Total Time for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                                modifier = Modifier.width(104.dp * fieldScale).then(focus),
                                trailingIcon = {
                                    IconButton(onClick = { stopwatchOrdinal = ordinal }) {
                                        Icon(Icons.Outlined.Timer, contentDescription = "Open Total Time stopwatch for set $ordinal")
                                    }
                                },
                            )
                            ExerciseMeasurement.RPE -> QuantitativeTextField(
                                value = draft.rpe,
                                onValueChange = {
                                    updateDraft(draft.copy(rpe = it, updatedAt = System.currentTimeMillis()))
                                },
                                enabled = draftLoaded,
                                label = "RPE",
                                description = "RPE for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                                maximum = 10.0,
                                modifier = Modifier.width(48.dp * fieldScale).then(focus),
                                onNext = { openNextEmpty(ordinal) },
                            )
                            ExerciseMeasurement.ADDED_WEIGHT -> QuantitativeTextField(
                                value = draft.addedWeight,
                                onValueChange = { updateDraft(draft.copy(addedWeight = it, updatedAt = System.currentTimeMillis())) },
                                label = "+$preferredWeightUnit",
                                description = "Added weight for ${exercise?.canonicalName.orEmpty()} set $ordinal",
                                modifier = Modifier.width(80.dp * fieldScale).then(focus),
                            )
                        }
                        }
                    }
                    },
                    tick = {
                    SetIconButton(
                        label = if (isLocked) "Edit set" else "Complete set",
                        description = if (isLocked) "Edit completed set $ordinal for ${exercise?.canonicalName.orEmpty()}"
                            else "Complete set $ordinal for ${exercise?.canonicalName.orEmpty()} and open next empty set",
                        icon = Icons.Outlined.Check,
                        selected = isLocked,
                        enabled = !confirming && (isLocked || inputError(draft) == null && parsedSet(draft) != null),
                        onClick = {
                            if (isLocked) {
                                onSetLockChanged(draft.setId, false)
                                activeOrdinal = ordinal
                            } else {
                                val set = parsedSet(draft)
                                fun complete() {
                                    editedSetIds.remove(draft.setId)
                                    onSetLockChanged(draft.setId, true)
                                    expandedOrdinal = 0
                                    onCompleteSet()
                                    openNextEmpty(ordinal)
                                }
                                if (set != null) {
                                    // An untouched saved set needs only a UI lock, not a rewrite of legacy data.
                                    if (recorded != null && draft.setId !in editedSetIds) complete()
                                    else scope.launch {
                                        confirming = true
                                        try {
                                            vm.submitEntryDraft(set, bandIds(draft.bandIds), ::complete).join()
                                        } finally {
                                            confirming = false
                                        }
                                    }
                                }
                            }
                        },
                    )
                    },
                    swap = if (exerciseVariations.isEmpty()) null else ({
                        var variationMenu by remember(ordinal) { mutableStateOf(false) }
                            Box {
                                IconButton(enabled = draftLoaded && !isLocked, onClick = { variationMenu = true }) {
                                    Icon(Icons.Outlined.Cached, "Change variant for ${exercise?.canonicalName.orEmpty()} set $ordinal")
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
                    }),
                )
                if (!inputConfig.manyTuts && attempts.size > 1 && isExpanded) Text(
                    "One-entry mode. Previously recorded attempts are retained.",
                    style = MaterialTheme.typography.bodySmall,
                )
                previousSet?.takeIf { isExpanded }?.let {
                    Text(
                        "Previous: ${setDescription(it, includeTimingAttempts = false)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics {
                            contentDescription = "Previous performance for ${exercise?.canonicalName.orEmpty()} set $ordinal"
                        },
                    )
                }
                inputError(draft)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                }
                }
                    androidx.compose.animation.AnimatedVisibility(showVariant, enter = fadeIn(), exit = fadeOut()) {
                        val palette = LocalVibePalette.current
                        Text(
                            variantName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp).offset(y = -headingInset)
                                .background(if (isActive) palette.surfaceRaised else palette.surface)
                                .padding(horizontal = 4.dp)
                                .semantics { contentDescription = "Variant for ${exercise?.canonicalName.orEmpty()} set $ordinal" },
                        )
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
                    vm.setWorkoutSetCount(row.id, ordinal)
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

@Composable
private fun SetMeasurementFlow(
    expanded: Boolean,
    measurements: @Composable () -> Unit,
    tick: @Composable () -> Unit,
    swap: (@Composable () -> Unit)?,
) {
    Layout(
        contents = listOf(measurements, {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(48.dp)) { tick() }
                if (swap != null) Box(Modifier.size(48.dp)) { swap() }
            }
        }),
        modifier = Modifier.fillMaxWidth(),
    ) { (inputs, actionNodes), constraints ->
        val gap = 4.dp.roundToPx()
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        // Reserve the same trailing space in both states so expansion never reflows row one.
        val actions = actionNodes.single().measure(loose)
        val inputWidth = (constraints.maxWidth - actions.width - gap).coerceAtLeast(0)
        val placeables = inputs.map { it.measure(Constraints(maxWidth = inputWidth)) }
        val rows = mutableListOf(mutableListOf<Int>())
        var usedWidth = 0
        var timingCells = 0
        placeables.forEachIndexed { index, placeable ->
            val isTiming = inputs[index].layoutId == "tut"
            val spacing = if (rows.last().isEmpty()) 0 else gap
            if (rows.last().isNotEmpty() &&
                (usedWidth + spacing + placeable.width > inputWidth || isTiming && timingCells == 4)
            ) {
                rows.add(mutableListOf())
                usedWidth = 0
                timingCells = 0
            }
            if (rows.last().isNotEmpty()) usedWidth += gap
            rows.last().add(index)
            usedWidth += placeable.width
            if (isTiming) timingCells++
        }
        val shownRows = if (expanded) rows else rows.take(1)
        val heights = shownRows.mapIndexed { index, row ->
            maxOf(
                row.maxOfOrNull { placeables[it].height } ?: 0,
                if (index == 0) actions.height else 0,
            )
        }
        layout(constraints.maxWidth, constraints.constrainHeight(heights.sum() + gap * (shownRows.size - 1))) {
            var y = 0
            shownRows.forEachIndexed { rowIndex, row ->
                val height = heights[rowIndex]
                var x = 0
                row.forEach { index ->
                    val field = placeables[index]
                    field.placeRelative(x, y + (height - field.height) / 2)
                    x += field.width + gap
                }
                if (rowIndex == 0) {
                    actions.placeRelative(constraints.maxWidth - actions.width, y + (height - actions.height) / 2)
                }
                y += height + gap
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetIconButton(
    label: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.semantics { this.selected = selected },
        ) {
            Icon(icon, contentDescription = description, tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current)
        }
    }
}

@Composable
private fun CompletedSetMeasurement(
    field: ExerciseMeasurement,
    draft: WorkoutEntryDraftEntity,
    resistance: String,
    reps: String,
    attempts: List<String>,
    config: ExerciseInputConfig,
    addedWeightUnit: String,
) {
    val value = when (field) {
        ExerciseMeasurement.WEIGHT -> resistance.takeIf(String::isNotBlank)?.let { "$it ${config.weightUnit}" }
        ExerciseMeasurement.REPS -> reps.takeIf(String::isNotBlank)?.let { "$it reps" }
        ExerciseMeasurement.TUT -> attempts.filter(String::isNotBlank).takeIf { it.isNotEmpty() }?.let {
            "${formatQuantitative(it.sumOf { entry -> quantitativeInput(entry).value ?: 0.0 })} s TUT"
        }
        ExerciseMeasurement.TOTAL_TIME -> draft.timeUnderTension.takeIf(String::isNotBlank)?.let { "$it s Total" }
        ExerciseMeasurement.ADDED_WEIGHT -> draft.addedWeight.takeIf(String::isNotBlank)?.let { "+$it $addedWeightUnit" }
        ExerciseMeasurement.RPE -> draft.rpe.takeIf(String::isNotBlank)?.let { "RPE $it" }
        else -> null
    }
    value?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun LoggingBandButton(
    band: BandEntity,
    selected: Boolean,
    enabled: Boolean,
    description: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val abbreviation = when (band.id) {
        "band-yellow" -> "Yw"
        "band-red" -> "Rd"
        "band-black" -> "Bl"
        "band-purple" -> "Pr"
        else -> band.name
    }
    VibeSurface(
        level = if (selected) VibeSurfaceLevel.INSET else VibeSurfaceLevel.RAISED,
        state = if (selected) VibeSurfaceState.SELECTED else VibeSurfaceState.RESTING,
        shape = MaterialTheme.shapes.small,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier.padding(horizontal = 2.dp)
            .shadow(if (selected) 0.dp else 3.dp, MaterialTheme.shapes.small)
            .clip(MaterialTheme.shapes.small)
            .drawWithContent {
                drawContent()
                if (selected) drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent),
                        endY = 8.dp.toPx(),
                    ),
                    size = Size(size.width, 8.dp.toPx()),
                )
            }
            .semantics {
                contentDescription = description
                stateDescription = if (selected) "Selected, depressed" else "Not selected, raised"
                this.selected = selected
            },
    ) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 2.dp),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text(abbreviation, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val interaction = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        interactionSource = interaction,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (onNext == null) ImeAction.Default else ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext?.invoke() }),
        singleLine = true,
        modifier = modifier.heightIn(min = 48.dp).semantics {
            contentDescription = description
            stateDescription = error ?: "Valid number"
        },
        decorationBox = { inner ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = inner,
                enabled = enabled,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interaction,
                label = if (label.isBlank()) null else {
                    { Text(label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) }
                },
                supportingText = if (error == null) null else ({ Text(error) }),
                isError = error != null,
                trailingIcon = trailingIcon,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            )
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

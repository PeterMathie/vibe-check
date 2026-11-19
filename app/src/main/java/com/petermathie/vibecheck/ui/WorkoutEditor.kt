package com.petermathie.vibecheck.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
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
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.MeasurementMetric
import com.petermathie.vibecheck.domain.programme.ResistanceMetric
import com.petermathie.vibecheck.domain.workout.*
import java.time.Instant
import java.time.ZoneId
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.petermathie.vibecheck.ui.theme.LocalVibeReducedMotion

internal const val SCROLL_TO_NEXT_EMPTY_SET = "scrollToNextEmptySet"

private data class SetScrollRequest(
    val rowId: String,
    val ordinal: Int,
    val sourceSetId: String,
    val expectedSetId: String?,
    val serial: Long,
)

private fun WorkoutEntryDraftEntity.isEntirelyEmpty(): Boolean =
    !warmUp && !failed && listOf(
        performance, rpe, leftValue, rightValue, addedWeight, assistance, romValue,
        timeHeld, timeUnderTension,
    ).all(String::isEmpty) && attemptsFor(this).all(String::isEmpty) && bandIds(bandIds).isEmpty()

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
    val loggingData by vm.workoutLoggingData.collectAsStateWithLifecycle()
    val snapshots = loggingData?.exercises.orEmpty()
    val sets = loggingData?.sets.orEmpty()
    val entryDrafts = loggingData?.drafts.orEmpty()
    val definitions = loggingData?.definitions.orEmpty()
    val measurements by vm.measurements.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val loggingPreferences = remember(context) { context.getSharedPreferences("workout_logging", 0) }
    val settings = remember(context) { context.getSharedPreferences("settings", 0) }
    var scrollToNext by remember { mutableStateOf(settings.getBoolean(SCROLL_TO_NEXT_EMPTY_SET, false)) }
    DisposableEffect(settings) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (key == SCROLL_TO_NEXT_EMPTY_SET) scrollToNext = preferences.getBoolean(key, false)
        }
        settings.registerOnSharedPreferenceChangeListener(listener)
        onDispose { settings.unregisterOnSharedPreferenceChangeListener(listener) }
    }
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
    var editDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
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
    val workoutRows = snapshots.filter { it.workoutId == workout.id }.sortedBy { it.position }
    var activeSet by remember(workout.id) { mutableStateOf<WorkoutSetSelection?>(null) }
    var initialSelectionResolved by remember(workout.id) { mutableStateOf(false) }
    val activeSetId = activeSet?.setId
    lateinit var exerciseOrder: ReorderState
    exerciseOrder = key(workout.id) {
        rememberReorderState(workoutRows.map { it.id }) { item, from, to ->
            vm.moveWorkoutExercise(item as String, to - from, onFailure = { exerciseOrder.cancel() })
        }
    }
    val rows = exerciseOrder.ordered(workoutRows) { it.id }
    val logged = sets.filter { s -> rows.any { it.id == s.workoutExerciseId } }
    val previousWorkouts = workouts.filter {
        it.status == "FINISHED" && it.finishedAt != null && it.finishedAt < workout.startedAt
    }.associateBy { it.id }
    val validEntryRows = remember(workout.id) { mutableStateMapOf<String, Boolean>() }
    val collapsedRows = remember(workout.id) { mutableStateMapOf<String, Boolean>() }
    val exerciseSwipeStates = remember(workout.id) { mutableMapOf<String, StagedSwipeState>() }
    val listState = rememberLazyListState()
    val reorderContext = rememberReorderScrollContext(listState)
    val density = LocalDensity.current
    var headingHeight by remember { mutableIntStateOf(0) }
    val reducedMotion = LocalVibeReducedMotion.current
    val observedDrafts = remember(workout.id) { mutableStateMapOf<String, List<WorkoutEntryDraftEntity>>() }
    val editedSetIds = remember(workout.id) { mutableSetOf<String>() }
    val persistedDraftIds = entryDrafts.mapTo(mutableSetOf()) { it.setId }
    LaunchedEffect(workout.id, loggingData, rows.map { it.id }) {
        if (!initialSelectionResolved && loggingData != null && rows.isNotEmpty()) {
            activeSet = firstUntickedWorkoutSet(rows, sets, entryDrafts, lockedSetIds)?.let { target ->
                observedDrafts[target.workoutExerciseId]?.let { target.reconcile(target.workoutExerciseId, it) } ?: target
            }
            initialSelectionResolved = true
        }
    }
    val setCoordinates = remember(workout.id) { mutableStateMapOf<String, LayoutCoordinates>() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var pendingScroll by remember(workout.id) { mutableStateOf<SetScrollRequest?>(null) }
    var scrollSerial by remember(workout.id) { mutableLongStateOf(0L) }
    LaunchedEffect(rows.map { it.id }) { pendingScroll = null }
    val currentRows by rememberUpdatedState(rows)
    val currentSets by rememberUpdatedState(sets)
    val currentEntryDrafts by rememberUpdatedState(entryDrafts)
    val currentLocks by rememberUpdatedState(lockedSetIds)
    val currentStatus by rememberUpdatedState(workout.status)
    val currentReducedMotion by rememberUpdatedState(reducedMotion)
    fun emptyCandidate(rowId: String, ordinal: Int): Boolean {
        if (currentSets.any { it.workoutExerciseId == rowId && it.ordinal == ordinal }) return false
        val draft = observedDrafts[rowId]?.firstOrNull { it.ordinal == ordinal }
            ?: currentEntryDrafts.firstOrNull { it.workoutExerciseId == rowId && it.ordinal == ordinal }
        return draft == null || draft.setId !in currentLocks && draft.isEntirelyEmpty()
    }
    fun alreadyVisible(setId: String): Boolean {
        val coordinates = setCoordinates[setId]?.takeIf { it.isAttached } ?: return false
        val origin = coordinates.localToWindow(Offset.Zero)
        val bounds = Rect(origin, origin + Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
        val pinned = listState.layoutInfo.visibleItemsInfo.any { it.offset < 0 && currentRows.any { row -> row.id == it.key } }
        val top = viewport.top + if (pinned) maxOf(headingHeight, with(density) { 48.dp.roundToPx() }) else 0
        return bounds.top >= top && bounds.bottom <= viewport.bottom && viewport.height > 0
    }
    fun requestNextEmpty(sourceRowId: String, sourceOrdinal: Int, sourceSetId: String) {
        pendingScroll = null
        if (!scrollToNext || currentStatus != "DRAFT" || listState.isScrollInProgress) return
        val sourceIndex = currentRows.indexOfFirst { it.id == sourceRowId }
        if (sourceIndex < 0) return
        currentRows.drop(sourceIndex).forEachIndexed { index, candidate ->
            val count = maxOf(
                candidate.configuredSetCount(),
                currentSets.filter { it.workoutExerciseId == candidate.id }.maxOfOrNull { it.ordinal } ?: 0,
                currentEntryDrafts.filter { it.workoutExerciseId == candidate.id }.maxOfOrNull { it.ordinal } ?: 0,
                observedDrafts[candidate.id]?.maxOfOrNull { it.ordinal } ?: 0,
            )
            val first = if (index == 0) sourceOrdinal + 1 else 1
            for (ordinal in first..count) {
                if (!emptyCandidate(candidate.id, ordinal)) continue
                val knownId = observedDrafts[candidate.id]?.firstOrNull { it.ordinal == ordinal }?.setId
                    ?.takeIf { setCoordinates[it]?.isAttached == true }
                if (knownId != null && alreadyVisible(knownId)) return
                pendingScroll = SetScrollRequest(candidate.id, ordinal, sourceSetId, knownId, ++scrollSerial)
                return
            }
        }
    }
    val manualScroll = remember(listState, workout.id) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) pendingScroll = null
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(pendingScroll, rows.map { Triple(it.id, it.actualExerciseId, it.targetSets) }, scrollToNext, workout.status) {
        val request = pendingScroll ?: return@LaunchedEffect
        try {
            if (!scrollToNext || currentStatus != "DRAFT" || !emptyCandidate(request.rowId, request.ordinal)) return@LaunchedEffect
            collapsedRows[request.rowId] = false
            val itemIndex = currentRows.indexOfFirst { it.id == request.rowId } + 1
            if (itemIndex <= 0) return@LaunchedEffect
            if (listState.layoutInfo.visibleItemsInfo.none { it.key == request.rowId }) {
                if (currentReducedMotion) listState.scrollToItem(itemIndex)
                else listState.animateScrollToItem(itemIndex)
            }
            val target = snapshotFlow {
                observedDrafts[request.rowId]?.firstOrNull { it.ordinal == request.ordinal }
                    ?.takeIf { setCoordinates[it.setId]?.isAttached == true }
            }.first { it != null } ?: return@LaunchedEffect
            withFrameNanos { }
            if (request.expectedSetId != null && target.setId != request.expectedSetId ||
                request.sourceSetId !in currentLocks || !target.isEntirelyEmpty() ||
                !emptyCandidate(request.rowId, request.ordinal) || alreadyVisible(target.setId)) return@LaunchedEffect
            val coordinates = setCoordinates[target.setId]?.takeIf { it.isAttached } ?: return@LaunchedEffect
            val origin = coordinates.localToWindow(Offset.Zero)
            val bounds = Rect(origin, origin + Offset(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()))
            val top = viewport.top + maxOf(headingHeight, with(density) { 48.dp.roundToPx() })
            val delta = if (bounds.top < top || bounds.height > viewport.bottom - top) bounds.top - top
                else (bounds.bottom - viewport.bottom).coerceAtLeast(0f)
            if (delta != 0f) {
                if (currentReducedMotion) listState.scrollBy(delta) else listState.animateScrollBy(delta)
            }
        } finally {
            if (pendingScroll?.serial == request.serial) pendingScroll = null
        }
    }
    suspend fun removeExercise(row: WorkoutExerciseEntity) {
        pendingScroll = null
        vm.removeWorkoutExercise(row.id) {
            if (activeSet?.workoutExerciseId == row.id) {
                activeSet = firstUntickedWorkoutSet(rows.filterNot { it.id == row.id }, sets, entryDrafts, lockedSetIds)
            }
            changeSetLocks(sets.filter { it.workoutExerciseId == row.id }.mapTo(mutableSetOf()) { it.id }, false)
            validEntryRows.remove(row.id)
            collapsedRows.remove(row.id)
            exerciseSwipeStates.remove(row.id)
            editedSetIds.removeAll(observedDrafts.remove(row.id).orEmpty().map { it.setId }.toSet())
        }.join()
    }
    CompositionLocalProvider(LocalReorderScrollContext provides reorderContext) {
    Box(Modifier.fillMaxSize().imePadding()) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .reorderScrollViewport(reorderContext)
            .nestedScroll(manualScroll)
            .onGloballyPositioned { viewport = it.boundsInWindow() }
            .semantics { contentDescription = "Workout exercise list" },
        contentPadding = PaddingValues(
            start = 12.dp,
            top = 12.dp,
            end = 12.dp,
            bottom = 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        state = listState,
    ) {
        item {
            Text(workout.name, style = MaterialTheme.typography.headlineSmall)
            if (logged.isNotEmpty()) Text("Logged duration: ${((logged.maxOf { it.loggedAt } - logged.minOf { it.loggedAt }) / 60000)} min")
            if(workout.status=="FINISHED") VibeActionButton("Change workout date", { editDate=true }, importance = ActionImportance.COMPACT)
        }
        rows.forEachIndexed { index, row ->
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
                ReorderItem(exerciseOrder, row.id, index, Modifier.fillMaxWidth()) {
                StagedSwipeToDelete(
                    "exercise $name",
                    { removeExercise(row) },
                    state = exerciseSwipeStates.getOrPut(row.id) { StagedSwipeState() },
                ) {
                    Surface(
                        color = LocalVibePalette.current.surface,
                        contentColor = LocalVibePalette.current.textPrimary,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                            .border(1.dp, LocalVibePalette.current.textSecondary.copy(alpha = 0.18f), MaterialTheme.shapes.medium)
                            .panelToggle("Exercise $name", !collapsed) { collapsedRows[row.id] = !collapsed },
                    ) {
                        Column {
                            ExerciseHeading(name, !collapsed, { collapsedRows[row.id] = !collapsed }) {
                                if (rows.size > 1) ReorderHandle(
                                    exerciseOrder, row.id, name,
                                    modifier = Modifier.pointerInput(row.id) {
                                        detectTapGestures(onTap = {})
                                    },
                                )
                            }
                            if (!collapsed) WorkoutExerciseCard(
                                vm,
                                row,
                                exercise,
                                sets.filter { it.workoutExerciseId == row.id },
                                previous,
                                variations = loggingData?.variations.orEmpty(),
                                availableBands = loggingData?.bands.orEmpty(),
                                recoveredDrafts = observedDrafts[row.id]
                                    ?: entryDrafts.filter { it.workoutExerciseId == row.id },
                                persistedDraftIds = persistedDraftIds,
                                editedSetIds = editedSetIds,
                                activeSetId = activeSetId,
                                onSetActivated = { draft ->
                                    initialSelectionResolved = true
                                    activeSet = draft?.let { WorkoutSetSelection(row.id, it.ordinal, it.setId) }
                                },
                                lockedSetIds = lockedSetIds,
                                onSetLockChanged = { id, locked ->
                                    if (!locked) pendingScroll = null
                                    changeSetLocks(setOf(id), locked)
                                },
                                onInputValidityChanged = { validEntryRows[row.id] = it },
                                onDraftsChanged = { updated ->
                                    observedDrafts[row.id] = updated
                                    activeSet = activeSet?.reconcile(row.id, updated)
                                    pendingScroll?.takeIf { it.rowId == row.id }?.let { request ->
                                        if (updated.none { it.ordinal == request.ordinal && it.isEntirelyEmpty() }) pendingScroll = null
                                    }
                                },
                                onSetPlaced = { id, coordinates -> setCoordinates[id] = coordinates },
                                onCompleteSet = { ordinal, setId ->
                                    if (workout.status == "DRAFT" && restSeconds > 0) RestTimer.start(context, restSeconds)
                                    requestNextEmpty(row.id, ordinal, setId)
                                },
                            )
                        }
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
            OutlinedButton(
                onClick = { add = true },
                enabled = !add && !editDate && !confirmDelete,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Add exercise to workout" },
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add exercise")
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
    if (restRemaining > 0) Box(
        Modifier.align(Alignment.TopEnd)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        VibeActionButton(
            "Rest: ${restRemaining / 60}:${(restRemaining % 60).toString().padStart(2, '0')} · cancel",
            { RestTimer.cancel(context) },
            modifier = Modifier.semantics { contentDescription = "Rest countdown" },
            importance = ActionImportance.COMPACT,
        )
    }
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
private fun ExerciseHeading(
    name: String,
    expanded: Boolean,
    toggle: () -> Unit,
    pinned: Boolean = false,
    reorderHandle: @Composable (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .panelToggle("${if (pinned) "Pinned exercise" else "Exercise"} heading for $name", expanded, toggle)
                .padding(12.dp),
        )
        reorderHandle?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WorkoutExerciseCard(
    vm: EditorViewModel,
    row: WorkoutExerciseEntity,
    exercise: ExerciseEntity?,
    sets: List<WorkoutSetEntity>,
    previous: List<WorkoutSetEntity>,
    variations: List<ExerciseVariationEntity>,
    availableBands: List<BandEntity>,
    recoveredDrafts: List<WorkoutEntryDraftEntity>,
    persistedDraftIds: Set<String>,
    editedSetIds: MutableSet<String>,
    activeSetId: String?,
    onSetActivated: (WorkoutEntryDraftEntity?) -> Unit,
    lockedSetIds: Set<String>,
    onSetLockChanged: (String, Boolean) -> Unit,
    onInputValidityChanged: (Boolean) -> Unit,
    onDraftsChanged: (List<WorkoutEntryDraftEntity>) -> Unit,
    onSetPlaced: (String, LayoutCoordinates) -> Unit,
    onCompleteSet: (Int, String) -> Unit,
) {
    val recordedBands by vm.setBands.collectAsStateWithLifecycle()
    var notes by rememberSaveable(row.id) { mutableStateOf(row.notes) }
    var draftLoaded by remember(row.id, row.actualExerciseId) { mutableStateOf(false) }
    var notesOpen by rememberSaveable(row.id) { mutableStateOf(row.notes.isNotBlank()) }
    var rpeHelpOpen by remember { mutableStateOf(false) }
    var expandedOrdinal by rememberSaveable(row.id) { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var focusNext by remember { mutableStateOf(false) }
    var visibleRows by rememberSaveable(row.id) { mutableIntStateOf(0) }
    val drafts = remember(row.id, row.actualExerciseId) { mutableStateMapOf<Int, WorkoutEntryDraftEntity>() }
    var variantDrafts by rememberSaveable(row.id) { mutableStateOf(mapOf<String, String>()) }
    val context = LocalContext.current
    val preferredWeightUnit = if (context.getSharedPreferences("settings", 0).getBoolean("lb", false)) "lb" else "kg"
    val exerciseVariations = variations.filter { it.exerciseId == row.actualExerciseId }.sortedBy { it.progressionRank }
    val defaultVariation = exerciseVariations.firstOrNull()
    val variationIds = exerciseVariations.map { it.id }
    val validVariationIds = variationIds.toSet()
    fun variationFor(id: String?) = exerciseVariations.find { it.id == id }
    fun cachedVariant(setId: String?, variantId: String?) =
        setId?.let { variantDrafts[variantDraftKey(it, variantId)] }?.let(::variantDraftMetadata)
    fun variantNameFor(draft: WorkoutEntryDraftEntity): String {
        val name = sets.firstOrNull { it.ordinal == draft.ordinal && it.variationId == draft.variationId }
            ?.variationNameSnapshot?.takeIf(String::isNotBlank)
            ?: cachedVariant(draft.setId, draft.variationId)?.variantName
            ?: variationFor(draft.variationId)?.name.orEmpty()
        return name.takeUnless {
            exerciseVariations.isEmpty() && draft.variationId == null && it == exercise?.canonicalName
        }.orEmpty()
    }
    fun configFor(id: String?, ordinal: Int? = null): ExerciseInputConfig {
        val snapshot = ordinal?.let { value -> sets.find { it.ordinal == value && it.variationId == id } }
        return resolveSetInputConfig(
            row, exercise?.trackingType.orEmpty(), variationFor(id), snapshot,
            cachedVariant(ordinal?.let { drafts[it]?.setId }, id),
            configOverride = null,
        )
    }
    val targetSets = row.configuredSetCount()
    LaunchedEffect(row.id, row.actualExerciseId) {
        val recovered = recoveredDrafts
        val restoredVariations = restoredWorkoutVariationIds(sets, recovered, exerciseVariations)
        val savedBands = vm.bandsForExercise(row.id)
        sets.forEach { set ->
            val config = configFor(set.variationId, set.ordinal)
            drafts[set.ordinal] = draftFromSet(
                set,
                config.timeHeld,
                config.loggingWeightUnit,
                savedBands.filter { it.setId == set.id }.sortedBy { it.ordinal }.map { it.bandId },
                preferredWeightUnit,
            )
        }
        recovered.sortedBy { it.ordinal }.forEach { draft ->
            if (draft.setId in persistedDraftIds) editedSetIds.add(draft.setId)
            drafts[draft.ordinal] = draft.copy(detailsOpen = false)
        }
        drafts.keys.sorted().forEach { ordinal ->
            val draft = drafts[ordinal] ?: return@forEach
            val variationId = restoredVariations[ordinal]
            if (variationId != draft.variationId) {
                drafts[ordinal] = draft.copy(variationId = variationId)
            }
        }
        visibleRows = maxOf(targetSets, sets.maxOfOrNull { it.ordinal } ?: 0, recovered.maxOfOrNull { it.ordinal } ?: 0)
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
    fun logicFor(draft: WorkoutEntryDraftEntity) = WorkoutSetDraftLogic(
        configFor(draft.variationId, draft.ordinal),
        sets.find { it.ordinal == draft.ordinal },
        variationFor(draft.variationId),
        cachedVariant(draft.setId, draft.variationId),
        preferredWeightUnit,
    )
    fun performanceParts(draft: WorkoutEntryDraftEntity) = logicFor(draft).performanceParts(draft)
    fun inputError(draft: WorkoutEntryDraftEntity) = logicFor(draft).inputError(draft)
    fun parsedSet(draft: WorkoutEntryDraftEntity) = logicFor(draft).parsedSet(draft)
    fun snapshotConfig(draft: WorkoutEntryDraftEntity) = logicFor(draft).snapshotConfig()
    fun activateSet(draft: WorkoutEntryDraftEntity) {
        onSetActivated(draft)
    }
    fun updateDraft(next: WorkoutEntryDraftEntity) {
        val updated = next.copy(updatedAt = System.currentTimeMillis())
        editedSetIds.add(updated.setId)
        activateSet(updated)
        drafts[updated.ordinal] = updated
        val logic = logicFor(updated)
        val parsed = logic.editedSet(updated)
        if (parsed == null) {
            vm.saveEntryDraft(updated)
        } else {
            vm.submitEntryDraft(
                parsed, bandIds(updated.bandIds),
                inputConfigOverride = logic.snapshotConfig(),
            ) {}
        }
    }
    fun updatePerformance(ordinal: Int, resistance: String, result: String) {
        val weighted = configFor(draftAt(ordinal).variationId, ordinal).loggingWeightUnit != null
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
            activateSet(draftAt(next))
            focusNext = true
        }
    }
    SideEffect {
        onInputValidityChanged(drafts.values.none { inputError(it) != null })
        if (draftLoaded) onDraftsChanged(drafts.values.filter { it.ordinal <= visibleRows }.sortedBy { it.ordinal })
    }
    if (!draftLoaded) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        row.targets.takeIf {
            it.isNotBlank() && !it.trim().matches(Regex("\\d+\\s+sets?", RegexOption.IGNORE_CASE))
        }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        row.supersetGroup?.let { Text("Circuit: $it · rest after round", style = MaterialTheme.typography.labelSmall) }
        repeat(visibleRows) { index ->
            val ordinal = index + 1
            val draft = draftAt(ordinal)
            val inputConfig = configFor(draft.variationId, ordinal)
            val (resistance,result) = performanceParts(draft)
            val isActive = activeSetId == draft.setId
            val isExpanded = expandedOrdinal == ordinal
            val isLocked = draft.setId in lockedSetIds
            val recorded = sets.find { it.ordinal == ordinal }
            val attempts = attemptsFor(draft)
            val resistanceMetric = inputConfig.loggingResistance()
            val measurementMetric = inputConfig.loggingMeasurement()
            val identity = "${exercise?.canonicalName.orEmpty()} set $ordinal"
            val variantName = variantNameFor(draft)
            val previousSet = previous.firstOrNull {
                it.variationId == draft.variationId && it.setType == "WORKING" &&
                    it.result == "COMPLETED" && it.ordinal == ordinal
            }
            val previousSummary = previousSet?.let {
                previousSetSummary(it, inputConfig, preferredWeightUnit,
                    recordedBands.filter { band -> band.setId == it.id }.sortedBy { band -> band.ordinal },
                    availableBands)
            }
            val palette = LocalVibePalette.current
            val fill = if (isActive) palette.surfaceRaised else palette.surface
            val density = LocalDensity.current
            key(draft.setId) {
            var confirming by remember { mutableStateOf(false) }
            var borderHeight by remember { mutableIntStateOf(with(density) { 16.dp.roundToPx() }) }
            val borderInset = with(density) { (borderHeight / 2f).toDp() }
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
                        if (activeSetId == draft.setId) onSetActivated(drafts[minOf(ordinal, visibleRows)])
                    }.join()
                },
            ) {
            Box(Modifier.fillMaxWidth().onGloballyPositioned { onSetPlaced(draft.setId, it) }.padding(top = borderInset)) {
            Surface(
                color = fill,
                contentColor = palette.textPrimary,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .onFocusChanged { if (it.hasFocus) activateSet(draft) }
                    .focusGroup()
                    .border(1.dp, if (isLocked) palette.accent else palette.textSecondary.copy(alpha = if (isActive) 0.4f else 0.28f),
                        MaterialTheme.shapes.medium)
                    .pointerInput(isExpanded) {
                        detectTapGestures {
                            activateSet(draft)
                            expandedOrdinal = if (isExpanded) 0 else ordinal
                        }
                    }
                    .semantics {
                        contentDescription = "Set $ordinal for ${exercise?.canonicalName.orEmpty()}"
                        stateDescription = "${if (isLocked) "Completed" else "Editing"}, ${if (isExpanded) "expanded" else "collapsed"}"
                        role = Role.Button
                        selected = isActive
                        onClick(label = if (isExpanded) "Hide details" else "Show details") {
                            activateSet(draft)
                            expandedOrdinal = if (isExpanded) 0 else ordinal
                            true
                        }
                    },
            ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(horizontal = 8.dp)
                    .padding(top = borderInset + 4.dp, bottom = 4.dp),
            ) {
                val focusRequester = remember(ordinal) { FocusRequester() }
                LaunchedEffect(isActive, focusNext) {
                    if (isActive && focusNext) {
                        if (!isLocked && resistanceMetric != null && measurementMetric != null &&
                            !(measurementMetric == MeasurementMetric.HOLD && attempts.size > 1)) {
                            focusRequester.requestFocus()
                        }
                        focusNext = false
                    }
                }
                WorkoutSetTile(
                    identity = identity,
                    showResistance = resistanceMetric != ResistanceMetric.BODYWEIGHT,
                    minimumMeasurementWidth = measurementMinimumWidth(measurementMetric),
                    resistanceLayout = SetGroupLayout(large = resistanceMetric == ResistanceMetric.BANDS),
                    measurementLayout = SetGroupLayout(large = measurementMetric == MeasurementMetric.SKILL_HOLD),
                    resistance = {
                        resistanceMetric?.let { metric ->
                            ResistanceRenderer(
                                metric, inputConfig, draft, resistance, preferredWeightUnit,
                                availableBands, bandIds(draft.bandIds),
                                isLocked, draftLoaded, identity,
                                onWeightChange = {
                                    if (inputConfig.addedWeight) updateDraft(draft.copy(addedWeight = it))
                                    else updatePerformance(ordinal, it, result)
                                },
                                onBandsChange = { updateDraft(draft.copy(bandIds = encodeBandIds(it))) },
                            )
                        }
                    },
                    measurement = {
                        measurementMetric?.let { metric ->
                            MeasurementRenderer(
                                metric, inputConfig, draft, result, attempts,
                                isLocked, draftLoaded, identity, Modifier.focusRequester(focusRequester),
                                onRepsChange = { updatePerformance(ordinal, resistance, it) },
                                onDraftChange = ::updateDraft,
                                onTimingDelete = ::updateDraft,
                            )
                        }
                    },
                    rpe = {
                        var rpeFocused by remember { mutableStateOf(false) }
                        val badgeFill = if (isLocked) fill else metricFieldFill(draft.rpe, rpeFocused).compositeOver(fill)
                        Box {
                            MetricValue(
                                draft.rpe, "", "RPE for $identity",
                                isLocked, draftLoaded,
                                width = 64, unit = "RPE", maximum = 10.0, secondary = true, onNext = { openNextEmpty(ordinal) },
                                compactText = measurementMetric == MeasurementMetric.SKILL_HOLD ||
                                    (measurementMetric == MeasurementMetric.HOLD &&
                                        (inputConfig.timeHeld || !inputConfig.timeUnderTension)),
                                showInlineError = false,
                                modifier = Modifier.onFocusChanged { rpeFocused = it.isFocused },
                                onChange = { if (acceptsRpeEdit(draft.rpe, it)) updateDraft(draft.copy(rpe = it)) },
                            )
                            if (!isLocked || draft.rpe.isNotBlank()) {
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    IconButton(
                                        onClick = { rpeHelpOpen = true },
                                        modifier = Modifier.align(Alignment.TopEnd)
                                            .offset(x = 8.dp, y = (-8).dp).size(16.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.Info,
                                            contentDescription = "About RPE for $identity",
                                            tint = palette.textSecondary,
                                            modifier = Modifier.size(12.dp)
                                                .background(badgeFill, CircleShape),
                                        )
                                    }
                                }
                            }
                        }
                    },
                    completion = {
                    SetIconButton(
                        label = if (isLocked) "Edit set" else "Complete set",
                        description = if (isLocked) "Edit completed set $ordinal for ${exercise?.canonicalName.orEmpty()}"
                            else "Complete set $ordinal for ${exercise?.canonicalName.orEmpty()}",
                        icon = Icons.Outlined.Check,
                        selected = isLocked,
                        enabled = !confirming && (isLocked || inputError(draft) == null && parsedSet(draft) != null),
                        onClick = {
                            activateSet(draft)
                            if (isLocked) {
                                onSetLockChanged(draft.setId, false)
                            } else {
                                val set = parsedSet(draft)
                                fun complete() {
                                    editedSetIds.remove(draft.setId)
                                    onSetLockChanged(draft.setId, true)
                                    expandedOrdinal = 0
                                    onCompleteSet(ordinal, draft.setId)
                                }
                                if (set != null) {
                                    // An untouched saved set needs only a UI lock, not a rewrite of legacy data.
                                    if (recorded != null && draft.setId !in editedSetIds) complete()
                                    else scope.launch {
                                        confirming = true
                                        try {
                                            vm.submitEntryDraft(
                                                set, bandIds(draft.bandIds),
                                                inputConfigOverride = snapshotConfig(draft),
                                                onSaved = ::complete,
                                            ).join()
                                        } finally {
                                            confirming = false
                                        }
                                    }
                                }
                            }
                        },
                    )
                    },
                    feedback = {
                        if (resistanceMetric == null || measurementMetric == null) Text(
                            "Choose logging metrics in the exercise DB.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (measurementMetric == MeasurementMetric.HOLD && attempts.size > 1) Text(
                            "Recorded attempts are retained. Choose Skill hold in the exercise DB to edit them.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        inputError(draft)?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                )
                }
                }
                Row(
                    Modifier.align(androidx.compose.ui.Alignment.TopStart).offset(y = -borderInset)
                        .fillMaxWidth().padding(horizontal = 10.dp)
                        .heightIn(min = 16.dp)
                        .onSizeChanged { borderHeight = it.height },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f, fill = false)) {
                        WorkoutSetVariantPicker(
                            identity = identity,
                            ordinal = ordinal,
                            variantName = variantName,
                            currentVariantId = draft.variationId,
                            variations = exerciseVariations,
                            enabled = draftLoaded,
                            showName = ordinal == 1 || draftAt(ordinal - 1).variationId != draft.variationId ||
                                variantNameFor(draftAt(ordinal - 1)) != variantName,
                            backgroundColor = fill,
                            parentBackgroundColor = palette.surface,
                            onSelect = { option ->
                                variantDrafts = variantDrafts + (
                                    variantDraftKey(draft.setId, draft.variationId) to encodeVariantDraft(
                                        draft, inputConfig.encode(), variantName,
                                        recorded?.takeIf { it.variationId == draft.variationId }
                                            ?.variationTrackingTypeSnapshot?.takeIf(String::isNotBlank)
                                            ?: variationFor(draft.variationId)?.trackingType.orEmpty(),
                                        recorded?.takeIf { it.variationId == draft.variationId }?.variationRankSnapshot
                                            ?: variationFor(draft.variationId)?.progressionRank,
                                    )
                                )
                                val nextDraft = WorkoutSetDraftLogic(inputConfig, recorded).changeVariant(
                                    draft, option.id, configFor(option.id, ordinal),
                                )
                                val cached = variantDrafts[variantDraftKey(draft.setId, option.id)]
                                updateDraft(if (cached == null) nextDraft else restoreVariantDraft(nextDraft, cached))
                            },
                        )
                    }
                    previousSummary?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            it, style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp, lineHeight = 11.sp, fontFeatureSettings = "tnum",
                            ),
                            color = palette.textPrimary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(2f, fill = false)
                                .setBorderLabelBackground(palette.surface, fill).padding(horizontal = 2.dp)
                                .semantics { contentDescription = "Previous result for $identity" },
                        )
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
    if (rpeHelpOpen) RpeInformationDialog { rpeHelpOpen = false }
}

@Composable
private fun RpeInformationDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(240.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("RPE", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text("RIR", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                }
                HorizontalDivider()
                listOf(
                    "10" to "0",
                    "9" to "~1",
                    "8" to "~2",
                    "7" to "~3",
                    "6" to "~4",
                    "5" to "5+",
                    "0-4" to "Many",
                ).forEach { (level, reserve) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(level, Modifier.weight(1f))
                        Text(reserve, Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
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
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            IconButton(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.width(24.dp).height(timingCellHeight()).semantics { this.selected = selected },
            ) {
                Icon(icon, contentDescription = description, tint = if (selected) MaterialTheme.colorScheme.primary else LocalContentColor.current)
            }
        }
    }
}

@Composable
private fun metricFieldFill(value: String, focused: Boolean) = with(LocalVibePalette.current) {
    when {
        focused -> surfaceSelected
        value.isEmpty() -> surfaceInset
        else -> textPrimary.copy(alpha = 0.07f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuantitativeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maximum: Double = 1_000_000_000.0,
    trailingIcon: (@Composable () -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    secondary: Boolean = false,
    minimumHeight: Dp = 48.dp,
    compact: Boolean = false,
    compactText: Boolean = compact,
    unit: String = "",
    showInlineError: Boolean = true,
) {
    val parsed = quantitativeInput(value, maximum)
    val error = when {
        value.isEmpty() -> null
        parsed.error != null -> parsed.error
        parsed.isTransient -> "Complete the number"
        else -> null
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val palette = LocalVibePalette.current
    val fill = metricFieldFill(value, focused)
    val outline = when {
        focused -> palette.focusRing
        error != null -> palette.danger
        value.isEmpty() -> palette.textSecondary.copy(alpha = 0.35f)
        else -> palette.border.copy(alpha = 0f)
    }

    Column {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = metricNumberStyle(compactText).copy(
            color = if (secondary) palette.textPrimary.copy(alpha = 0.95f) else palette.textPrimary,
            textAlign = TextAlign.Center,
        ),
        interactionSource = interaction,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = if (onNext == null) ImeAction.Default else ImeAction.Next),
        keyboardActions = KeyboardActions(onNext = { onNext?.invoke() }),
        singleLine = true,
        modifier = modifier.height(minimumHeight).semantics {
            contentDescription = description
            stateDescription = error ?: "Valid number"
        },
        decorationBox = { inner ->
            MetricValueContent(
                value = value, label = label, unit = unit, compact = compact,
                compactText = compactText,
                minimumHeight = minimumHeight, editable = true,
                trailing = trailingIcon,
                modifier = Modifier.fillMaxWidth()
                    .background(fill, MaterialTheme.shapes.small)
                    .border(if (focused) 2.dp else 1.dp, outline, MaterialTheme.shapes.small),
            ) { inner() }
        },
    )
    if (showInlineError && error != null) Text(
        if (compact) if (parsed.isTransient) "Finish" else "Error" else error,
        style = if (compact) MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 12.sp)
            else MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.error,
        maxLines = if (compact) 1 else Int.MAX_VALUE,
        modifier = Modifier.semantics { contentDescription = error },
    )
    }
}

fun setDescription(s: WorkoutSetEntity, includeTimingAttempts: Boolean = true, labelTimingTotal: Boolean = false): String {
    val config = ExerciseInputConfig.decode(s.variationInputConfigSnapshot, s.variationTrackingTypeSnapshot)
    if ("measurementMetric=" in s.variationInputConfigSnapshot) {
        val primary = when (config.measurementMetric) {
            MeasurementMetric.REPETITIONS -> "${s.reps?.let(::formatQuantitative) ?: "—"} reps"
            MeasurementMetric.RANGE_OF_MOTION -> "${s.romValue?.let(::formatQuantitative) ?: "—"} ${if (s.romUnit == "degrees") "°" else s.romUnit.orEmpty()}"
            MeasurementMetric.HOLD -> if (!config.timeHeld && config.timeUnderTension) {
                "Total ${s.timeUnderTensionMillis?.let { formatQuantitative(it / 1000.0) } ?: "—"}s"
            } else "${s.holdMillis?.let { formatQuantitative(it / 1000.0) } ?: "—"}s"
            MeasurementMetric.SKILL_HOLD -> {
                val attempts = timingMillis(s.tutEntriesMillis)
                val held = "${s.holdMillis?.let { formatQuantitative(it / 1000.0) } ?: "—"}s"
                (if (includeTimingAttempts && attempts.size > 1) {
                    attempts.joinToString(" + ") { "${formatQuantitative(it / 1000.0)}s" } + " = $held"
                } else held) + (s.timeUnderTensionMillis?.let { " · Total ${formatQuantitative(it / 1000.0)}s" } ?: "")
            }
            null -> error("A recorded metric configuration requires a measurement type")
        }
        return (if (config.loggingResistance() == ResistanceMetric.KILOGRAMS) {
            if (config.addedWeight) s.addedWeightKg?.let { "+${formatQuantitative(it)} kg · " }.orEmpty()
            else s.weightKg?.let { "${formatQuantitative(it)} kg × " }.orEmpty()
        } else "") + primary + if (s.setType == "WARM_UP") " · warm-up" else ""
    }
    return when {
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
}

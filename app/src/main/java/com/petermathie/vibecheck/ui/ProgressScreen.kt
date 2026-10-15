package com.petermathie.vibecheck.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.ExerciseEntity
import com.petermathie.vibecheck.data.local.ExerciseVariationEntity
import com.petermathie.vibecheck.data.local.TrackerDailyValueEntity
import com.petermathie.vibecheck.data.local.TrackerEntity
import com.petermathie.vibecheck.data.local.TrackerFieldEntity
import com.petermathie.vibecheck.domain.progress.SessionProgress
import com.petermathie.vibecheck.domain.progress.PersonalRecords
import com.petermathie.vibecheck.domain.progress.PersonalRecordVisibility
import com.petermathie.vibecheck.domain.model.ActivityDay
import com.petermathie.vibecheck.domain.tracker.HabitFieldForm
import com.petermathie.vibecheck.domain.tracker.HabitChoiceIntensity
import com.petermathie.vibecheck.domain.tracker.decodeHabitChoices
import com.petermathie.vibecheck.ui.theme.VibeSpacing
import com.petermathie.vibecheck.ui.components.VibeGraph
import com.petermathie.vibecheck.ui.components.VibeGraphStyle
import com.petermathie.vibecheck.ui.components.VibeSurface
import com.petermathie.vibecheck.ui.theme.VibeSurfaceLevel
import java.time.Instant
import java.time.ZoneId
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ProgressScreen(vm:EditorViewModel) {
    val exercises by vm.exercises.collectAsStateWithLifecycle();val workouts by vm.workouts.collectAsStateWithLifecycle();val rows by vm.workoutExercises.collectAsStateWithLifecycle()
    val sets by vm.sets.collectAsStateWithLifecycle();val links by vm.setBands.collectAsStateWithLifecycle();val bands by vm.bands.collectAsStateWithLifecycle();val variations by vm.variations.collectAsStateWithLifecycle()
    val trackers by vm.trackers.collectAsStateWithLifecycle();val fields by vm.fields.collectAsStateWithLifecycle();val values by vm.values.collectAsStateWithLifecycle();val measurements by vm.measurements.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val rangePreferences = remember(context) {
        context.getSharedPreferences("progress-date-range", android.content.Context.MODE_PRIVATE)
    }
    var dateRange by remember {
        mutableStateOf(
            if (rangePreferences.contains("start") && rangePreferences.contains("end")) {
                validProgressDateRange(
                    LocalDate.ofEpochDay(rangePreferences.getLong("start", 0)),
                    LocalDate.ofEpochDay(rangePreferences.getLong("end", 0)),
                )
            } else {
                null
            },
        )
    }
    var rangeDialogOpen by rememberSaveable { mutableStateOf(false) }
    var exerciseId by remember { mutableStateOf<String?>(null) };var picker by remember { mutableStateOf(false) };var filter by remember { mutableStateOf<String?>(null) };var selected by remember { mutableStateOf<Int?>(null) }
    var variationMenu by remember { mutableStateOf(false) };var methodology by remember { mutableStateOf(false) }
    var selectedWeight by remember { mutableStateOf<Int?>(null) };var selectedPhoto by remember { mutableStateOf<File?>(null) }
    var collapsedCards by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val allPoints=exerciseId?.let { SessionProgress.points(it,workouts,rows,sets,links,bands,filter,variations) }.orEmpty()
    val points=allPoints.filter { point -> dateRange?.containsTimestamp(point.date) != false }
    val finishedIds=workouts.filter { it.status=="FINISHED" }.map { it.id }.toSet()
    val trackableSetRowIds = sets.filter { SessionProgress.valid(it) || it.romValue != null }.map { it.workoutExerciseId }.toSet()
    val eligibleExerciseIds = rows.filter { it.workoutId in finishedIds && it.id in trackableSetRowIds }.map { it.actualExerciseId }.toSet()
    val allAggregate = remember(eligibleExerciseIds, workouts, rows, sets, links, bands, variations) {
        SessionProgress.aggregate(
            eligibleExerciseIds.associateWith { id ->
                SessionProgress.points(id, workouts, rows, sets, links, bands, null, variations)
            },
        )
    }
    val aggregate = allAggregate.filter { point -> dateRange?.containsTimestamp(point.date) != false }
    val allBodyweights = measurements.filter { it.metric.equals("Bodyweight", true) }.sortedBy { it.recordedAt }
    val bodyweights = allBodyweights.filter { point -> dateRange?.containsTimestamp(point.recordedAt) != false }
    val progressPhotos = remember(context) {
        File(context.filesDir, "progress-photos").listFiles().orEmpty()
            .mapNotNull { file -> file.name.substringBefore('.').toLongOrNull()?.let { it to file } }
    }
    val exerciseRows=rows.filter { it.actualExerciseId==exerciseId && it.workoutId in finishedIds }.map { it.id }.toSet()
    val valid=sets.filter {
        it.workoutExerciseId in exerciseRows &&
            SessionProgress.valid(it) &&
            (filter==null || it.variationId==filter) &&
            dateRange?.containsTimestamp(it.loggedAt) != false
    }
    val records=SessionProgress.records(valid,points)
    val recordVisibility=SessionProgress.recordVisibility(exercises.find { it.id==exerciseId }?.trackingType)
    val activeTrackers = trackers.filterNot { it.isArchived }.sortedBy { it.position }
    val availableCardKeys = buildList {
        if (allAggregate.isNotEmpty()) add("overall")
        if (allBodyweights.isNotEmpty()) add("bodyweight")
        activeTrackers.forEach { add("habit:${it.id}") }
        add("training")
    }
    val layoutPreferences = remember(context) {
        context.getSharedPreferences("progress-layout", android.content.Context.MODE_PRIVATE)
    }
    var progressCardKeys by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(availableCardKeys) {
        val saved = layoutPreferences.getString("card-order", "").orEmpty()
            .split('|')
            .filter(String::isNotBlank)
        val reconciled = if (layoutPreferences.getBoolean("card-order-customized", false)) {
            saved.filter { it in availableCardKeys } + availableCardKeys.filterNot { it in saved }
        } else {
            availableCardKeys.sortedBy { cardKey ->
                when {
                    cardKey == "overall" -> 0
                    cardKey == "bodyweight" -> 1
                    cardKey.startsWith("habit:") -> {
                        100 + (activeTrackers.find { "habit:${it.id}" == cardKey }?.position ?: 0)
                    }
                    else -> 1_000
                }
            }
        }
        progressCardKeys = reconciled
        layoutPreferences.edit().putString("card-order", reconciled.joinToString("|")).apply()
    }
    val progressOrder = rememberReorderState(progressCardKeys) { _, from, to ->
        val reordered = progressCardKeys.toMutableList()
        if (from in reordered.indices && to in reordered.indices) {
            val moved = reordered.removeAt(from)
            reordered.add(to, moved)
            progressCardKeys = reordered
            layoutPreferences.edit()
                .putString("card-order", reordered.joinToString("|"))
                .putBoolean("card-order-customized", true)
                .apply()
        }
    }
    ScreenList {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(VibeSpacing.medium)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Progress",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f))
                    IconButton(onClick={methodology=true}) { Icon(Icons.Outlined.Info,contentDescription="How progress works") }
                }
                ProgressDateRangeControls(
                    range = dateRange,
                    onEdit = { rangeDialogOpen = true },
                    onReset = {
                        dateRange = null
                        selected = null
                        selectedWeight = null
                        rangePreferences.edit().clear().apply()
                    },
                )
                progressOrder.ordered(progressCardKeys) { it }.forEachIndexed { cardIndex, cardKey ->
                    key(cardKey) {
                        val title = when {
                            cardKey == "overall" -> "Overall training trend"
                            cardKey == "bodyweight" -> "Bodyweight"
                            cardKey == "training" -> "Training progress"
                            cardKey.startsWith("habit:") -> activeTrackers.find { "habit:${it.id}" == cardKey }?.name.orEmpty()
                            else -> cardKey
                        }
                        val expanded = !collapsedCards.contains(cardKey)
                        val toggleExpanded = {
                            collapsedCards = if (expanded) collapsedCards + cardKey else collapsedCards.filterNot { it == cardKey }
                        }
                        when {
                            cardKey == "overall" -> ProgressCardShell(progressOrder, cardKey, cardIndex, title, expanded, toggleExpanded) {
                                Text(
                                    aggregate.lastOrNull()?.let { "Average normalized score across ${it.exerciseCount} exercises" }
                                        ?: "No training progress in the selected date range.",
                                )
                                MiniChart(
                                    values = aggregate.map { it.averageIndex },
                                    dates = aggregate.map { it.date },
                                    unit = "index",
                                    emptyMessage = "No training progress in the selected date range.",
                                ) {}
                            }
                            cardKey == "bodyweight" -> ProgressCardShell(progressOrder, cardKey, cardIndex, title, expanded, toggleExpanded) {
                                MiniChart(
                                    values = bodyweights.map { if (it.unit.equals("lb", true)) it.value / 2.2046226218 else it.value },
                                    dates = bodyweights.map { it.recordedAt },
                                    unit = "kg",
                                    emptyMessage = "No bodyweight entries in the selected date range.",
                                ) { index ->
                                    selectedWeight = index
                                    val day = Instant.ofEpochMilli(bodyweights[index].recordedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                                    selectedPhoto = progressPhotos.firstOrNull { (timestamp, _) ->
                                        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate() == day
                                    }?.second
                                }
                                selectedWeight?.let { bodyweights.getOrNull(it) }?.let {
                                    Text("${Instant.ofEpochMilli(it.recordedAt).atZone(ZoneId.systemDefault()).toLocalDate()} · ${formatBodyweight(it.value)} ${it.unit}")
                                }
                            }
                            cardKey.startsWith("habit:") -> {
                                activeTrackers.find { "habit:${it.id}" == cardKey }?.let { tracker ->
                                    ReorderItem(progressOrder, cardKey, cardIndex, Modifier.fillMaxWidth()) {
                                        VibeCard {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable(onClickLabel = if (expanded) "Collapse ${tracker.name}" else "Expand ${tracker.name}") { toggleExpanded() },
                                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                            ) {
                                                Icon(HabitIconCatalog.icon(tracker.iconName), contentDescription = "${tracker.name} icon", tint = Color(tracker.colourArgb.toInt()))
                                                Spacer(Modifier.width(8.dp))
                                                Text(tracker.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                                ReorderHandle(progressOrder, cardKey, tracker.name)
                                            }
                                            if (expanded) {
                                                val trackerFields = fields.filter { it.trackerId == tracker.id && !it.isArchived }
                                                val fieldIds = trackerFields.map { it.id }.toSet()
                                                val habitDays = values.filter {
                                                    it.fieldId in fieldIds && dateRange?.containsEpochDay(it.epochDay) != false
                                                }
                                                    .groupBy { it.epochDay }
                                                    .map { (epochDay, dailyValues) -> ActivityDay(epochDay, habitHeatmapLevel(tracker, trackerFields, dailyValues)) }
                                                val numericField = trackerFields.firstOrNull { it.valueType in setOf("NUMBER", "COUNT", "DURATION", "RATING") }
                                                val unit = numericField?.unit
                                                Text(habitIntensityDescription(tracker, trackerFields, unit), style = MaterialTheme.typography.bodySmall)
                                                ActivityHeatmap(days = habitDays, onDayClick = {}, activityColor = Color(tracker.colourArgb.toInt()), itemLabel = "${tracker.name.lowercase()} intensity")
                                            }
                                        }
                                    }
                                }
                            }
                            cardKey == "training" -> ProgressCardShell(progressOrder, cardKey, cardIndex, title, expanded, toggleExpanded) {
                                ExerciseProgressSelectors(exercises = exercises, exerciseId = exerciseId, variations = variations, filter = filter, eligible = eligibleExerciseIds.isNotEmpty(), variationMenu = variationMenu, onChooseExercise = { picker = true }, onVariationMenu = { variationMenu = it }, onFilter = { filter = it; selected = null })
                                if (eligibleExerciseIds.isEmpty()) {
                                    Text("Complete a working set before an exercise appears here.")
                                } else if (exerciseId == null) {
                                    Text("Choose an exercise to see its progress.")
                                } else if (points.isEmpty()) {
                                    Text("No training progress in the selected date range.")
                                } else {
                                    MiniChart(points.map { it.index ?: it.score }, points.map { it.trend }, points.map { it.date }, if(points.any { it.index!=null }) "index" else "score") { selected = it }
                                    PersonalRecordsContent(records, recordVisibility)
                                    val last=points.mapNotNull { it.trend }.takeLast(2)
                                    if(last.size==2) Text(if(last[1]>last[0]*1.01)"Rising" else if(last[1]<last[0]*0.99)"Falling" else "Flat")
                                    Text("RPE")
                                    RpeBarChart(values = points.map { it.performance.rpe ?: Double.NaN }, dates = points.map { it.date }) { selected = it }
                                    selected?.let { points.getOrNull(it) }?.let { p ->
                                        Text("${Instant.ofEpochMilli(p.date).atZone(ZoneId.systemDefault()).toLocalDate()} · ${setDescription(p.performance)}")
                                        Text("Bands: ${p.bands.joinToString().ifBlank { "None" }} · RPE ${p.performance.rpe ?: "not recorded"}")
                                        Text(p.notes.ifBlank { "No exercise notes" })
                                    }
                                    ActivityHeatmap(days = points.groupBy { Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() }.map { ActivityDay(it.key,it.value.size) }, onDayClick = { day -> selected=points.indexOfFirst { Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()==day }.takeIf { it>=0 } })
                                }
                            }
                        }
                    }
                }
            }
        }
        val ids=rows.filter { it.actualExerciseId==exerciseId && workouts.any { w -> w.id==it.workoutId && w.status=="FINISHED" } }.map { it.id }.toSet()
        val romSeries=SessionProgress.romSeries(
            sets.filter { it.workoutExerciseId in ids && dateRange?.containsTimestamp(it.loggedAt) != false },
        )
        if(romSeries.isNotEmpty()) item {
            Text("Flexibility / ROM (separate from frequency)")
            romSeries.forEach { (unit,series) ->
                Text("ROM · $unit",style=MaterialTheme.typography.titleSmall)
                MiniChart(series.map { it.romValue!! },dates=series.map { it.loggedAt },unit=unit) { }
                series.forEach { Text("${it.romValue} $unit") }
            }
        }
        items(points.reversed()) { p -> TextButton(onClick={selected=points.indexOf(p)}){Text("${Instant.ofEpochMilli(p.date).atZone(ZoneId.systemDefault()).toLocalDate()} · ${setDescription(p.performance)}")} }
    }
    if (rangeDialogOpen) {
        val availableDates = buildList {
            addAll(allAggregate.map { Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate() })
            addAll(allBodyweights.map { Instant.ofEpochMilli(it.recordedAt).atZone(ZoneId.systemDefault()).toLocalDate() })
            addAll(values.map { LocalDate.ofEpochDay(it.epochDay) })
            addAll(sets.map { Instant.ofEpochMilli(it.loggedAt).atZone(ZoneId.systemDefault()).toLocalDate() })
        }
        val today = LocalDate.now()
        val bounds = (availableDates.minOrNull() ?: today.minusDays(34)) to
            minOf(availableDates.maxOrNull() ?: today, today)
        ProgressDateRangeDialog(
            initial = dateRange,
            bounds = bounds,
            onDismiss = { rangeDialogOpen = false },
            onApply = { updated ->
                dateRange = updated
                selected = null
                selectedWeight = null
                rangePreferences.edit()
                    .putLong("start", updated.start.toEpochDay())
                    .putLong("end", updated.endInclusive.toEpochDay())
                    .apply()
                rangeDialogOpen = false
            },
        )
    }
    if(picker)ProgressExercisePicker(vm,eligibleExerciseIds,{picker=false}){exerciseId=it.id;filter=null;selected=null;picker=false}
    if(methodology)AlertDialog(
        onDismissRequest={methodology=false},
        title={Text("How progress works")},
        text={Text("The first three valid sessions establish a personal baseline of 100. Before that, charts show raw performance. Skill index is a heuristic: variation order sets the main difficulty; holds, reps and assistance adjust progress only within the same variation.")},
        confirmButton={TextButton(onClick={methodology=false}){Text("Close")}},
    )
    selectedPhoto?.let { file ->
        val bitmap = remember(file) {
            android.graphics.BitmapFactory.decodeFile(
                file.path,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 },
            )
        }
        AlertDialog(
            onDismissRequest = { selectedPhoto = null },
            title = { Text("Progress photo") },
            text = {
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Progress photo for selected bodyweight day",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    )
                } ?: Text("Photo could not be opened")
            },
            confirmButton = { TextButton(onClick = { selectedPhoto = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun ProgressDateRangeControls(
    range: ProgressDateRange?,
    onEdit: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onEdit,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                range?.let {
                    "${it.start.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} – " +
                        it.endInclusive.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
                } ?: "Date range: All time",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onReset, enabled = range != null) {
            Text("Reset")
        }
    }
}

@Composable
private fun ProgressDateRangeDialog(
    initial: ProgressDateRange?,
    bounds: Pair<LocalDate, LocalDate>,
    onDismiss: () -> Unit,
    onApply: (ProgressDateRange) -> Unit,
) {
    var startDay by rememberSaveable(initial, bounds) {
        mutableLongStateOf((initial?.start ?: bounds.first).toEpochDay())
    }
    var endDay by rememberSaveable(initial, bounds) {
        mutableLongStateOf((initial?.endInclusive ?: bounds.second).toEpochDay())
    }
    var pickerTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val start = LocalDate.ofEpochDay(startDay)
    val end = LocalDate.ofEpochDay(endDay)
    val validated = validProgressDateRange(start, end)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom progress range") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { pickerTarget = "start" },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start: ${start.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}")
                }
                OutlinedButton(
                    onClick = { pickerTarget = "end" },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("End: ${end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}")
                }
                if (validated == null) {
                    Text(
                        "Start date must be on or before end date.",
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text("Both dates are included.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { validated?.let(onApply) },
                enabled = validated != null,
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    pickerTarget?.let { target ->
        val minimumMillis = bounds.first.toEpochDay() * 86_400_000L
        val maximumMillis = bounds.second.toEpochDay() * 86_400_000L
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (if (target == "start") startDay else endDay) * 86_400_000L,
            selectableDates = remember(bounds) {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                        utcTimeMillis in minimumMillis..maximumMillis
                }
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickerTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selectedMillis ->
                            val selectedDay = Math.floorDiv(selectedMillis, 86_400_000L)
                            if (target == "start") startDay = selectedDay else endDay = selectedDay
                        }
                        pickerTarget = null
                    },
                ) { Text("Use date") }
            },
            dismissButton = {
                TextButton(onClick = { pickerTarget = null }) { Text("Cancel") }
            },
        ) {
            DatePicker(
                state = pickerState,
                title = {
                    Text(
                        if (target == "start") "Select start date" else "Select end date",
                        Modifier.padding(start = 24.dp, top = 16.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun ProgressCardHeader(
    order: ReorderState,
    cardKey: String,
    title: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (expanded) "Collapse $title" else "Expand $title") { onToggleExpanded() },
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        ReorderHandle(order, cardKey, title)
    }
}

@Composable
private fun ProgressCardShell(
    order: ReorderState,
    cardKey: String,
    cardIndex: Int,
    title: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    ReorderItem(order, cardKey, cardIndex, Modifier.fillMaxWidth()) {
        VibeCard {
            Column(modifier = Modifier.fillMaxWidth()) {
                ProgressCardHeader(order, cardKey, title, expanded, onToggleExpanded)
                if (expanded) content()
            }
        }
    }
}

@Composable
private fun ExerciseProgressSelectors(
    exercises: List<ExerciseEntity>,
    exerciseId: String?,
    variations: List<ExerciseVariationEntity>,
    filter: String?,
    eligible: Boolean,
    variationMenu: Boolean,
    onChooseExercise: () -> Unit,
    onVariationMenu: (Boolean) -> Unit,
    onFilter: (String?) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onChooseExercise,
            enabled = eligible,
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(
                exercises.find { it.id == exerciseId }?.canonicalName ?: "Choose exercise",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.weight(1f)) {
            val exerciseVariations = variations.filter { it.exerciseId == exerciseId }
            OutlinedButton(
                onClick = { onVariationMenu(true) },
                enabled = exerciseVariations.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    exerciseVariations.find { it.id == filter }?.name ?: "Variations",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DropdownMenu(
                expanded = variationMenu,
                onDismissRequest = { onVariationMenu(false) },
            ) {
                DropdownMenuItem(
                    text = { Text("All variations") },
                    onClick = {
                        onFilter(null)
                        onVariationMenu(false)
                    },
                )
                exerciseVariations.forEach { variation ->
                    DropdownMenuItem(
                        text = { Text(variation.name) },
                        onClick = {
                            onFilter(variation.id)
                            onVariationMenu(false)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalRecordsContent(
    records: PersonalRecords,
    visibility: PersonalRecordVisibility,
) {
    Text("Personal records", style = MaterialTheme.typography.titleLarge)
    records.scoredPerformance?.let { record ->
        PersonalRecordValue(
            "Best performance",
            "${formatAxis(record.score)} score",
            setDescription(record.performance),
        )
    }
    if (visibility.weight) records.weightKg?.let {
        PersonalRecordValue("Heaviest weight", "${formatAxis(it)} kg")
    }
    if (visibility.hold) records.holdMillis?.let {
        PersonalRecordValue("Longest hold", "${formatAxis(it / 1000.0)} sec")
    }
    if (visibility.estimatedOneRepMax) records.estimatedOneRepMaxKg?.let {
        PersonalRecordValue("Estimated 1RM", "${formatAxis(it)} kg")
    }
}

internal fun heatmapLevel(value: Double, lightBelow: Double, mediumBelow: Double): Int = when {
    value < lightBelow -> 1
    value < mediumBelow -> 2
    else -> 3
}

internal fun habitHeatmapLevel(
    tracker: TrackerEntity,
    fields: List<TrackerFieldEntity>,
    values: List<TrackerDailyValueEntity>,
): Int {
    val fieldsById = fields.associateBy { it.id }
    val numericValues = values.mapNotNull { it.numericValue }
    if (numericValues.isNotEmpty()) {
        return heatmapLevel(numericValues.sum(), tracker.heatmapLightBelow, tracker.heatmapMediumBelow)
    }
    return values.mapNotNull { value ->
        val field = fieldsById[value.fieldId] ?: return@mapNotNull null
        when (field.valueType) {
            "BOOLEAN" -> value.booleanValue?.let { if (it) 3 else 1 }
            HabitFieldForm.CHOICE -> {
                value.choiceIntensity?.let {
                    runCatching { HabitChoiceIntensity.valueOf(it).heatmapLevel }.getOrNull()
                } ?: decodeHabitChoices(field)
                    .firstOrNull { it.id == value.choiceOptionId || value.choiceOptionId == null && it.label == value.textValue }
                    ?.intensity
                    ?.heatmapLevel
            }
            "TEXT", "DATETIME" -> value.textValue?.takeIf(String::isNotBlank)?.let { 2 }
            else -> null
        }
    }.maxOrNull() ?: 1
}

private fun habitIntensityDescription(
    tracker: TrackerEntity,
    fields: List<TrackerFieldEntity>,
    unit: String?,
): String = when {
    fields.any { it.valueType in setOf("NUMBER", "COUNT", "DURATION", "RATING") } ->
        "Low < ${formatAxis(tracker.heatmapLightBelow)}${unitSuffix(unit)} · " +
            "medium < ${formatAxis(tracker.heatmapMediumBelow)}${unitSuffix(unit)} · strong at or above"
    fields.any { it.valueType == HabitFieldForm.CHOICE } ->
        "Each choice uses its configured Light, Medium, or Dark intensity."
    fields.any { it.valueType == "BOOLEAN" } -> "No is low · Yes is strong"
    else -> "A written entry uses the medium shade."
}

private fun unitSuffix(unit: String?): String = unit?.let { " $it" }.orEmpty()

@Composable
private fun PersonalRecordValue(label:String,value:String,detail:String?=null) {
    VibeSurface(VibeSurfaceLevel.INSET, Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal=14.dp,vertical=10.dp),
            horizontalArrangement=Arrangement.spacedBy(12.dp),
            verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label,style=MaterialTheme.typography.labelLarge)
                detail?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(value,style=MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ProgressExercisePicker(
    vm: EditorViewModel,
    eligibleExerciseIds: Set<String>,
    onDismiss: () -> Unit,
    onChoose: (com.petermathie.vibecheck.data.local.ExerciseEntity) -> Unit,
) {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val aliases by vm.aliases.collectAsStateWithLifecycle()
    val mappings by vm.mappings.collectAsStateWithLifecycle()
    val muscles by vm.muscles.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val aliasIds = aliases.filter { it.alias.contains(query, true) }.map { it.exerciseId }.toSet()
    val muscleIds = muscles.filter { it.displayName.contains(query, true) }.map { it.id }.toSet()
    val mappedIds = mappings.filter { it.muscleId in muscleIds }.map { it.exerciseId }.toSet()
    val available = exercises.filter {
        it.id in eligibleExerciseIds &&
            !it.isArchived &&
            (it.canonicalName.contains(query, true) || it.id in aliasIds || it.id in mappedIds)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose exercise") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("Name, alias or muscle") })
                LazyColumn(
                    Modifier
                        .heightIn(max = 420.dp)
                        .semantics { contentDescription = "Eligible progress exercises" },
                ) {
                    if (available.isEmpty()) {
                        item { Text("No matching exercises with progress data") }
                    }
                    items(available, key = { it.id }) { exercise ->
                        Text(
                            exercise.canonicalName,
                            Modifier
                                .fillMaxWidth()
                                .clickable { onChoose(exercise) }
                                .padding(vertical = 14.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
fun MiniChart(
    values: List<Double>,
    smooth: List<Double?> = emptyList(),
    dates: List<Long> = emptyList(),
    unit: String = "",
    emptyMessage: String = "Complete a valid entry to see this chart.",
    onSelect: (Int) -> Unit,
) {
    VibeGraph(
        values = values,
        secondaryValues = smooth,
        dates = dates,
        unit = unit,
        graphState = if (values.any(Double::isFinite)) {
            com.petermathie.vibecheck.ui.components.VibeGraphState.Data
        } else {
            com.petermathie.vibecheck.ui.components.VibeGraphState.Empty(emptyMessage)
        },
        onSelect = onSelect,
    )
}

@Composable
private fun RpeBarChart(values: List<Double>, dates: List<Long>, onSelect: (Int) -> Unit) {
    VibeGraph(
        values = values,
        dates = dates,
        unit = "RPE",
        style = VibeGraphStyle.BARS,
        fixedRange = 0.0..10.0,
        onSelect = onSelect,
    )
}

private fun formatChartDate(value:Long?):String=value?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }.orEmpty()
private fun formatAxis(value:Double):String=if(value%1.0==0.0)value.toLong().toString() else "%.1f".format(value)

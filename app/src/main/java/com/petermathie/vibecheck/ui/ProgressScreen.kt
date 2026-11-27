package com.petermathie.vibecheck.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.ExerciseEntity
import com.petermathie.vibecheck.data.local.ExerciseVariationEntity
import com.petermathie.vibecheck.data.local.WorkoutSetEntity
import com.petermathie.vibecheck.domain.progress.SessionProgress
import com.petermathie.vibecheck.domain.progress.PersonalRecords
import com.petermathie.vibecheck.domain.progress.PersonalRecordVisibility
import com.petermathie.vibecheck.domain.progress.AggregateProgressPoint
import com.petermathie.vibecheck.domain.model.ActivityDay
import com.petermathie.vibecheck.ui.theme.VibeSpacing
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.components.VibeGraph
import com.petermathie.vibecheck.ui.components.VibeGraphStyle
import com.petermathie.vibecheck.ui.components.VibeGraphState
import com.petermathie.vibecheck.ui.components.progressDateRange
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.LocalDate

@Composable
fun ProgressScreen(vm:EditorViewModel) {
    val exercises by vm.exercises.collectAsStateWithLifecycle();val workouts by vm.workouts.collectAsStateWithLifecycle();val rows by vm.workoutExercises.collectAsStateWithLifecycle()
    val sets by vm.sets.collectAsStateWithLifecycle();val links by vm.setBands.collectAsStateWithLifecycle();val bands by vm.bands.collectAsStateWithLifecycle();val variations by vm.variations.collectAsStateWithLifecycle()
    var exerciseId by rememberSaveable { mutableStateOf<String?>(null) };var picker by remember { mutableStateOf(false) };var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var variationMenu by remember { mutableStateOf(false) };var methodology by remember { mutableStateOf(false) }
    var rpeHelp by remember { mutableStateOf(false) }
    var rangeMonths by rememberSaveable { mutableIntStateOf(12) }
    var customRange by rememberSaveable { mutableStateOf(false) }
    var customStart by rememberSaveable { mutableLongStateOf(LocalDate.now().minusYears(1).toEpochDay()) }
    var customEnd by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var rangeDialog by remember { mutableStateOf(false) }
    val dateRange = if (customRange) {
        progressDateRange(LocalDate.ofEpochDay(customStart), LocalDate.ofEpochDay(customEnd), ZoneId.systemDefault())
    } else progressDateRange(rangeMonths, LocalDate.now(), ZoneId.systemDefault())
    val points=exerciseId?.let { SessionProgress.points(it,workouts,rows,sets,links,bands,filter,variations) }.orEmpty()
    val showEstimatedOneRepMax = exercises.find { it.id == exerciseId }?.trackingType == "WEIGHT_REPS"
    val zone = ZoneId.systemDefault()
    val dailyEstimatedOneRepMax = remember(exerciseId, showEstimatedOneRepMax, workouts, rows, sets, filter, zone) {
        exerciseId?.takeIf { showEstimatedOneRepMax }?.let {
            SessionProgress.dailyEstimatedOneRepMax(it, workouts, rows, sets, filter, zone)
        }.orEmpty()
    }
    val finishedIds=workouts.filter { it.status=="FINISHED" }.map { it.id }.toSet()
    val savedRows = rows.associateBy { it.id }
    fun scoringSet(set: WorkoutSetEntity): Boolean {
        val row = savedRows[set.workoutExerciseId]
        return SessionProgress.valid(set, row?.trackingType, row?.inputConfig.orEmpty())
    }
    val trackableSetRowIds = sets.filter { scoringSet(it) || it.romValue != null || it.distanceValue != null }.map { it.workoutExerciseId }.toSet()
    val eligibleExerciseIds = rows.filter { it.workoutId in finishedIds && it.id in trackableSetRowIds }.map { it.actualExerciseId }.toSet()
    val allPoints = remember(eligibleExerciseIds, workouts, rows, sets, links, bands, variations) {
        eligibleExerciseIds.associateWith { id ->
            SessionProgress.points(id, workouts, rows, sets, links, bands, null, variations)
        }
    }
    val aggregate = remember(allPoints) { SessionProgress.aggregate(allPoints) }
    val exerciseRows=rows.filter { it.actualExerciseId==exerciseId && it.workoutId in finishedIds }.map { it.id }.toSet()
    val valid=sets.filter { it.workoutExerciseId in exerciseRows && scoringSet(it) && (filter==null || it.variationId==filter) }
    val records=SessionProgress.records(valid,points)
    val visiblePoints = points.filter { it.date in dateRange }
    val visibleEstimatedOneRepMax = dailyEstimatedOneRepMax.filter { it.date in dateRange }
    val recordVisibility=SessionProgress.recordVisibility(exercises.find { it.id==exerciseId }?.trackingType)
    val selectedDay = rememberSaveable { mutableStateOf<Long?>(LocalDate.now().toEpochDay()) }
    var selectedWorkoutId by remember { mutableStateOf<String?>(null) }
    var exerciseDetailsDay by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val measurementRows = rows.filter { it.actualExerciseId == exerciseId && it.workoutId in finishedIds }.map { it.id }.toSet()
    val romSeries = SessionProgress.romSeries(sets.filter { it.workoutExerciseId in measurementRows })
    val distanceSeries = SessionProgress.distanceSeries(sets.filter { it.workoutExerciseId in measurementRows })
    LaunchedEffect(exerciseId, filter) {
        selectedDay.value = if (exerciseId == null) LocalDate.now().toEpochDay() else null
        listState.scrollToItem(0)
    }
    LaunchedEffect(dateRange) {
        selectedDay.value?.let { day ->
            if (LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() !in dateRange) {
                selectedDay.value = null
            }
        }
    }
    HabitGlassDialogHost(constrainToContent = true) { backdrop, _ ->
        val palette = LocalVibePalette.current
        Box(
            Modifier.fillMaxSize().then(backdrop).habitGlow(palette.accent, palette.isDark).semantics {
                contentDescription = if (exerciseId == null) "Overall progress" else "Exercise progress"
            },
        ) {
            val plotHeight = 200.dp
            ScreenList(
                listState = listState,
                itemSpacing = VibeSpacing.small,
                modifier = Modifier.semantics {
                    contentDescription = "Progress sections"
                },
            ) {
                fun calendarItem() {
                    item(key = "calendar") {
                        key(exerciseId, filter) {
                            val calendarDays = if (exerciseId == null) {
                                workouts.filter { it.status == "FINISHED" }
                                    .groupingBy { workoutLocalDay(it, ZoneId.systemDefault()) }.eachCount()
                            } else points.groupingBy {
                                Instant.ofEpochMilli(it.date).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
                            }.eachCount()
                            ActivityHeatmap(
                                days = calendarDays.map { ActivityDay(it.key, it.value) },
                                onDayClick = {},
                                itemLabel = if (exerciseId == null) "workouts" else "exercise sessions",
                                singularItemLabel = if (exerciseId == null) null else "exercise session",
                                selection = selectedDay,
                                reserveEmptyReadoutSpace = exerciseId != null,
                                opaqueCells = true,
                                onEntryClick = if (exerciseId == null) null else { day -> exerciseDetailsDay = day },
                            )
                        }
                    }
                }
                item(key = "header") {
                    ExerciseProgressHeader(
                        exercises, exerciseId, variations, filter, variationMenu,
                        onChooseExercise = { picker = true },
                        onVariationMenu = { variationMenu = it },
                        onFilter = { filter = it },
                    )
                }
                item(key = "points") {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(VibeSpacing.small),
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when {
                                    exerciseId == null -> "Overall training trend"
                                    showEstimatedOneRepMax -> "Estimated 1RM trend"
                                    else -> "Training trend"
                                },
                                Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                            )
                            ProgressRangeSelector(rangeMonths, customRange, onCustom = { rangeDialog = true }) {
                                rangeMonths = it
                                customRange = false
                            }
                        }
                        if (exerciseId == null) {
                            val visibleAggregate = visibleAggregateProgress(aggregate, dateRange)
                            Text("Weekly average points", style = MaterialTheme.typography.labelSmall)
                            VibeGraph(
                                values = visibleAggregate.map { it.averageIndex }, dates = visibleAggregate.map { it.date },
                                unit = "points", dateRange = dateRange, readoutBelow = true, edgeToEdge = true,
                                plotHeight = 200.dp, compactLayout = true, onValueHelp = { methodology = true },
                                valueFormatter = ::formatProgressValue,
                                selectedDate = selectedDay.value?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() },
                                onDateSelect = { selectedDay.value = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() },
                                graphState = if (visibleAggregate.isEmpty()) VibeGraphState.Empty("No training points recorded in this date range.") else VibeGraphState.Data,
                            )
                        } else if (showEstimatedOneRepMax && dailyEstimatedOneRepMax.isEmpty()) {
                            Text("Record weight and repetitions in a completed working set to see estimated 1RM.")
                        } else if (!showEstimatedOneRepMax && points.isEmpty()) Text("Complete a working set for this exercise to see its progress.")
                        else VibeGraph(
                                values = if (showEstimatedOneRepMax) visibleEstimatedOneRepMax.map { it.kilograms } else visiblePoints.map { it.index ?: it.score },
                                secondaryValues = if (showEstimatedOneRepMax) visibleEstimatedOneRepMax.map { it.trendKilograms } else visiblePoints.map { it.trend },
                                dates = if (showEstimatedOneRepMax) visibleEstimatedOneRepMax.map { it.date } else visiblePoints.map { it.date },
                                unit = if (showEstimatedOneRepMax) "kg" else "points",
                                dateRange = dateRange,
                                readoutBelow = true,
                                edgeToEdge = true,
                                plotHeight = plotHeight,
                                compactLayout = true,
                                onValueHelp = { methodology = true },
                                valueHelpDescription = if (showEstimatedOneRepMax) "About estimated 1RM" else "What are points?",
                                valueFormatter = ::formatProgressValue,
                                selectedDate = selectedDay.value?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() },
                                onDateSelect = { selectedDay.value = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() },
                                graphState = when {
                                    showEstimatedOneRepMax && visibleEstimatedOneRepMax.isEmpty() -> VibeGraphState.Empty("No estimated 1RM recorded in this date range.")
                                    !showEstimatedOneRepMax && visiblePoints.isEmpty() -> VibeGraphState.Empty("No points recorded in this date range.")
                                    else -> VibeGraphState.Data
                                },
                            )
                    }
                }
                if (exerciseId != null && points.isNotEmpty()) {
                    item(key = "effort") {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(VibeSpacing.small),
                        ) {
                            Text("Effort (RPE)", style = MaterialTheme.typography.titleMedium)
                            VibeGraph(
                                values = visiblePoints.map { it.performance.rpe ?: Double.NaN },
                                dates = visiblePoints.map { it.date },
                                unit = "RPE",
                                style = VibeGraphStyle.BARS,
                                fixedRange = 0.0..10.0,
                                dateRange = dateRange,
                                readoutBelow = true,
                                edgeToEdge = true,
                                plotHeight = plotHeight,
                                compactLayout = true,
                                valueFormatter = ::formatProgressValue,
                                onValueHelp = { rpeHelp = true },
                                valueHelpDescription = "About RPE",
                                selectedDate = selectedDay.value?.let { LocalDate.ofEpochDay(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() },
                                onDateSelect = { selectedDay.value = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() },
                                graphState = if (visiblePoints.any { it.performance.rpe?.isFinite() == true }) VibeGraphState.Data else VibeGraphState.Empty("No effort recorded in this date range."),
                            )
                        }
                    }
                }
                if(romSeries.isNotEmpty()) item(key = "rom") {
                    Column(Modifier.fillMaxWidth()) {
                        Text("Flexibility / ROM (separate from frequency)")
                        romSeries.forEach { (unit,series) ->
                            Text("ROM · $unit",style=MaterialTheme.typography.titleSmall)
                            MiniChart(series.map { it.romValue!! },dates=series.map { it.loggedAt },unit=unit, valueFormatter=::formatProgressValue, plotHeight=plotHeight) { }
                            series.forEach { Text("${formatProgressValue(it.romValue!!)} $unit") }
                        }
                    }
                }
                if (distanceSeries.isNotEmpty()) item(key = "distance") {
                    Column(Modifier.fillMaxWidth()) {
                        Text("Distance")
                        distanceSeries.forEach { (unit, series) ->
                            Text("Distance · $unit", style = MaterialTheme.typography.titleSmall)
                            MiniChart(series.map { it.distanceValue!! }, dates = series.map { it.loggedAt },
                                unit = unit, valueFormatter = ::formatProgressValue, plotHeight = plotHeight) { }
                        }
                    }
                }
                calendarItem()
                if (exerciseId != null && points.isNotEmpty()) item(key = "records") {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(VibeSpacing.small)) {
                        PersonalRecordsContent(records, recordVisibility)
                    }
                }
                if (exerciseId == null) item(key = "day-workouts") {
                    val day = selectedDay.value ?: LocalDate.now().toEpochDay()
                    ProgressDayWorkouts(day, workoutsOnDay(workouts, day, ZoneId.systemDefault())) { selectedWorkoutId = it }
                }
            }
        }
        selectedWorkoutId?.let { id ->
            val workout = workouts.find { it.id == id && it.status == "FINISHED" }
            if (workout == null) HabitGlassDialog("Workout unavailable", onDismiss = { selectedWorkoutId = null }) {
                Text("This workout is no longer available.")
            } else ProgressWorkoutDialog(workout, vm) { selectedWorkoutId = null }
        }
        exerciseDetailsDay?.let { day ->
            exerciseId?.let { id ->
                val sessionWorkoutIds = points.mapNotNull { point ->
                    rows.find { it.id == point.performance.workoutExerciseId }?.workoutId
                }.toSet()
                ProgressExerciseDayDialog(
                    day = day,
                    exerciseId = id,
                    variationFilter = filter,
                    workouts = workoutsOnDay(workouts.filter { it.id in sessionWorkoutIds }, day, ZoneId.systemDefault()),
                    vm = vm,
                    onDismiss = { exerciseDetailsDay = null },
                )
            }
        }
        if(picker)ProgressExercisePicker(vm,{picker=false}){exerciseId=it?.id;filter=null;picker=false}
        if (rangeDialog) ProgressCustomRangeDialog(
            start = if (customRange) LocalDate.ofEpochDay(customStart) else LocalDate.now().minusMonths(rangeMonths.toLong()),
            end = if (customRange) LocalDate.ofEpochDay(customEnd) else LocalDate.now(),
            onDismiss = { rangeDialog = false },
        ) { start, end ->
            customStart = start.toEpochDay()
            customEnd = end.toEpochDay()
            customRange = true
            rangeDialog = false
        }
        if(methodology)HabitGlassDialog(
            title = if (showEstimatedOneRepMax) "How estimated 1RM is calculated" else "How points are calculated",
            onDismiss = { methodology = false },
        ) {
            if (exerciseId == null) Text("Overall training trend averages each exercise's personal points for the week, using UTC weeks. Weeks overlapping the date range remain visible, with partial weeks positioned at the range boundary without changing their averages. Weekly points are separate from the selected local day and its workout list. The date range only changes what is shown, not the personal baselines.")
            if (showEstimatedOneRepMax) {
                Text("Each local calendar day shows the highest estimated one-rep max across this exercise's completed working sets and workouts, respecting the selected variation. The Epley estimate uses recorded weight and repetitions; warm-ups, failed sets and ROM measurements are excluded. Bilateral repetitions use the lower recorded side.")
                Text("Values are kilograms, not normalized points. The rolling line averages the last three recorded daily bests. The date range only changes what is shown, not the estimates or their rolling average. Overall training and other exercise scores are unchanged.")
            } else {
                Text("The strongest valid working set supplies each session's points. The first three valid sessions establish a personal baseline of 100 points. Before that, points reflect raw performance. For skills, points are a heuristic: variation order sets the main difficulty; holds, reps and assistance adjust progress only within the same variation.")
            }
            Text("RPE is perceived effort on a 0–10 scale: how hard the working set felt, not a measure of progress.")
        }
        if (rpeHelp) RpeInformationDialog { rpeHelp = false }
    }
}

internal fun visibleAggregateProgress(
    points: List<AggregateProgressPoint>,
    range: LongRange,
): List<AggregateProgressPoint> = points.mapNotNull { point ->
    val weekEnd = Instant.ofEpochMilli(point.date).atZone(ZoneOffset.UTC).toLocalDate()
    val first = weekEnd.minusDays(6).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val endExclusive = weekEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    point.takeIf { first <= range.last && endExclusive > range.first }
        ?.copy(date = point.date.coerceIn(range))
}

@Composable
private fun ExerciseProgressHeader(
    exercises: List<ExerciseEntity>,
    exerciseId: String?,
    variations: List<ExerciseVariationEntity>,
    filter: String?,
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
        TextButton(
            onClick = onChooseExercise,
            enabled = exercises.any { !it.isArchived },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(0.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                exercises.find { it.id == exerciseId }?.canonicalName?.let { "Track progress of $it" } ?: "Choose...",
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleLarge,
                color = selectionTextColor(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Outlined.ExpandMore, "Choose exercise", tint = if (LocalVibePalette.current.isDark) Color.White else LocalVibePalette.current.textPrimary)
            }
        }
        val exerciseVariations = variations.filter { it.exerciseId == exerciseId }
        if (exerciseVariations.isNotEmpty()) Box {
            IconButton(onClick = { onVariationMenu(true) }) {
                Icon(
                    Icons.Outlined.ExpandMore,
                    "Variations: ${exerciseVariations.find { it.id == filter }?.name ?: "All variations"}",
                    tint = selectionTextColor(),
                )
            }
            DropdownMenu(
                expanded = variationMenu,
                onDismissRequest = { onVariationMenu(false) },
            ) {
                DropdownMenuItem(
                    text = { Text("All variations", color = selectionTextColor()) },
                    onClick = {
                        onFilter(null)
                        onVariationMenu(false)
                    },
                )
                exerciseVariations.forEach { variation ->
                    DropdownMenuItem(
                        text = { Text(variation.name, color = selectionTextColor()) },
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
internal fun ProgressRangeSelector(
    months: Int,
    custom: Boolean,
    onCustom: () -> Unit,
    description: String = "Progress date range",
    onChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.semantics {
            contentDescription = description
            stateDescription = if (custom) "Custom" else if (months == 12) "1 year" else if (months == 1) "1 month" else "$months months"
        }, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(if (custom) "Custom" else if (months == 12) "1y" else "${months}m", color = selectionTextColor())
            Icon(Icons.Outlined.ExpandMore, null, tint = selectionTextColor())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        listOf(1 to ("1m" to "1 month"), 3 to ("3m" to "3 months"), 6 to ("6m" to "6 months"), 12 to ("1y" to "1 year")).forEach { (value, labels) ->
            DropdownMenuItem(
                text = { Text(labels.second, color = selectionTextColor()) },
                onClick = { expanded = false; onChange(value) },
                modifier = Modifier.semantics { contentDescription = labels.second },
            )
        }
        DropdownMenuItem(text = { Text("Custom", color = selectionTextColor()) }, onClick = { expanded = false; onCustom() })
        }
    }
}

@Composable
internal fun PersonalRecordsContent(
    records: PersonalRecords,
    visibility: PersonalRecordVisibility,
) {
    Text("Personal records", style = MaterialTheme.typography.titleLarge)
    val values = buildList {
        records.scoredPerformance?.let { add("Best performance" to "${formatAxis(it.score)} points") }
        if (visibility.weight) records.weightKg?.let { add("Heaviest weight" to "${formatAxis(it)} kg") }
        if (visibility.hold) records.holdMillis?.let { add("Longest hold" to "${formatAxis(it / 1000.0)} sec") }
        if (visibility.estimatedOneRepMax) records.estimatedOneRepMaxKg?.let { add("Estimated 1RM" to "${formatAxis(it)} kg") }
    }
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "Personal records" }) {
        values.forEachIndexed { index, (label, value) ->
            if (index > 0) HorizontalDivider(color = LocalVibePalette.current.border.copy(alpha = 0.3f))
            Row(
                Modifier.fillMaxWidth().padding(vertical = VibeSpacing.small),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun ProgressExercisePicker(
    vm: EditorViewModel,
    onDismiss: () -> Unit,
    onChoose: (ExerciseEntity?) -> Unit,
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
        !it.isArchived &&
            (it.canonicalName.contains(query, true) || it.id in aliasIds || it.id in mappedIds)
    }
    HabitGlassDialog(title = "Choose exercise", onDismiss = onDismiss) {
        HabitTextField("Name, alias or muscle", query, { query = it })
        Text(
            "Overall training trend",
            Modifier.fillMaxWidth().clickable { onChoose(null) }.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = selectionTextColor(),
        )
        if (available.isEmpty()) Text("No matching exercises.")
        available.sortedBy { it.canonicalName }.forEach { exercise ->
            Text(
                exercise.canonicalName,
                Modifier.fillMaxWidth().clickable { onChoose(exercise) }.padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = selectionTextColor(),
            )
        }
    }
}

@Composable
fun MiniChart(values:List<Double>,smooth:List<Double?> = emptyList(),dates:List<Long> = emptyList(),unit:String = "",valueFormatter:((Double)->String)? = null,plotHeight:Dp = 138.dp,onSelect:(Int)->Unit) {
    VibeGraph(
        values = values,
        secondaryValues = smooth,
        dates = dates,
        unit = unit,
        onSelect = onSelect,
        valueFormatter = valueFormatter,
        plotHeight = plotHeight,
    )
}

private fun formatAxis(value:Double):String=formatProgressValue(value)

package com.petermathie.vibecheck.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.domain.workout.formatQuantitative
import com.petermathie.vibecheck.domain.workout.timingMillis
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun workoutsOnDay(workouts: List<WorkoutEntity>, day: Long, zone: ZoneId): List<WorkoutEntity> =
    workouts.filter {
        it.status == "FINISHED" && workoutLocalDay(it, zone) == day
    }.sortedWith(compareBy<WorkoutEntity> { it.finishedAt ?: it.startedAt }.thenBy { it.id })

internal fun workoutLocalDay(workout: WorkoutEntity, zone: ZoneId) =
    Instant.ofEpochMilli(workout.finishedAt ?: workout.startedAt).atZone(zone).toLocalDate().toEpochDay()

internal fun recordedSetValues(set: WorkoutSetEntity): List<Pair<String, String>> = buildList {
    fun number(label: String, value: Double?, unit: String = "") {
        value?.let { add(label to "${formatProgressValue(it)}$unit") }
    }
    fun time(label: String, value: Long?) { value?.let { number(label, it / 1000.0, " s") } }
    number("Weight", set.weightKg, " kg")
    number("Reps", set.reps)
    number("Left reps", set.leftReps)
    number("Right reps", set.rightReps)
    time("Hold", set.holdMillis)
    time("Left hold", set.leftHoldMillis)
    time("Right hold", set.rightHoldMillis)
    number("Added weight", set.addedWeightKg, " kg")
    number("Assistance", set.assistanceKg, " kg")
    number("RPE", set.rpe)
    number("ROM", set.romValue, " ${set.romUnit.orEmpty()}".trimEnd())
    time("Total time", set.timeUnderTensionMillis)
    timingMillis(set.tutEntriesMillis).takeIf { it.isNotEmpty() }?.let { attempts ->
        add("Timing attempts" to attempts.joinToString(" + ") { "${formatProgressValue(it / 1000.0)} s" })
    }
}

@Composable
internal fun ProgressDayWorkouts(day: Long, workouts: List<WorkoutEntity>, onChoose: (String) -> Unit) {
    val palette = LocalVibePalette.current
    Column(Modifier.fillMaxWidth().semantics {
        contentDescription = "Selected day workouts"
        stateDescription = formatDisplayDate(LocalDate.ofEpochDay(day))
    }) {
        Text("Workouts", style = MaterialTheme.typography.titleMedium)
        workouts.forEachIndexed { index, workout ->
            if (index > 0) HorizontalDivider(color = palette.border.copy(alpha = 0.3f))
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = "View workout details") { onChoose(workout.id) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(workout.name, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                Text(workoutTime(workout.finishedAt ?: workout.startedAt), style = MaterialTheme.typography.bodySmall)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
            }
        }
    }
}

@Composable
internal fun ProgressWorkoutDialog(workout: WorkoutEntity, vm: EditorViewModel, onDismiss: () -> Unit) {
    val data by vm.workoutLoggingData.collectAsStateWithLifecycle()
    val bandLinks by vm.setBands.collectAsStateWithLifecycle()
    val palette = LocalVibePalette.current
    HabitGlassDialog(title = workoutHeader(workout), onDismiss = onDismiss, titleStyle = MaterialTheme.typography.titleSmall) {
        Column(Modifier.fillMaxWidth().semantics { contentDescription = "Workout details" }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProgressWorkoutNotes(workout.notes)
            val loaded = data
            if (loaded == null) Text("Loading workout...")
            else {
                val rows = loaded.exercises.filter { it.workoutId == workout.id }.sortedBy { it.position }
                if (rows.isEmpty()) Text("No exercises recorded in this workout.")
                rows.forEachIndexed { rowIndex, row ->
                    if (rowIndex > 0) HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = palette.border,
                    )
                    ProgressRecordedExercise(row, loaded, bandLinks)
                }
            }
        }
    }
}

@Composable
internal fun ProgressExerciseDayDialog(
    day: Long,
    exerciseId: String,
    variationFilter: String?,
    workouts: List<WorkoutEntity>,
    vm: EditorViewModel,
    onDismiss: () -> Unit,
) {
    val data by vm.workoutLoggingData.collectAsStateWithLifecycle()
    val bandLinks by vm.setBands.collectAsStateWithLifecycle()
    val definitions by vm.exercises.collectAsStateWithLifecycle()
    val palette = LocalVibePalette.current
    HabitGlassDialog(
        title = definitions.find { it.id == exerciseId }?.canonicalName ?: "Exercise unavailable",
        onDismiss = onDismiss,
    ) {
        Column(
            Modifier.fillMaxWidth().semantics { contentDescription = "Exercise day details" },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(formatDisplayDate(LocalDate.ofEpochDay(day)), style = MaterialTheme.typography.bodySmall)
            val loaded = data
            if (loaded == null) Text("Loading exercise sessions...")
            else if (workouts.isEmpty()) Text("These exercise sessions are no longer available.")
            else workouts.forEachIndexed { sessionIndex, workout ->
                if (sessionIndex > 0) HorizontalDivider(color = palette.border)
                Text(workout.name, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                Text(workoutHeader(workout), style = MaterialTheme.typography.bodySmall)
                loaded.exercises.filter { it.workoutId == workout.id && it.actualExerciseId == exerciseId }
                    .sortedBy { it.position }.forEach { row ->
                        if (variationFilter == null || loaded.sets.any {
                            it.workoutExerciseId == row.id && (it.variationId ?: "default") == variationFilter
                        }) ProgressRecordedExercise(row, loaded, bandLinks, variationFilter)
                    }
            }
        }
    }
}

@Composable
private fun ProgressRecordedExercise(
    row: WorkoutExerciseEntity,
    data: WorkoutLoggingData,
    bandLinks: List<WorkoutSetBandEntity>,
    variationFilter: String? = null,
) {
    val palette = LocalVibePalette.current
    Text(
        row.exerciseName.ifBlank { data.definitions.find { it.id == row.actualExerciseId }?.canonicalName ?: "Exercise unavailable" },
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
    ProgressWorkoutNotes(row.notes)
    val sets = data.sets.filter {
        it.workoutExerciseId == row.id && (variationFilter == null || (it.variationId ?: "default") == variationFilter)
    }.sortedBy { it.ordinal }
    if (sets.isEmpty()) Text("No sets recorded for this exercise.")
    sets.groupBy { set ->
        set.variationNameSnapshot.ifBlank {
            data.variations.find { it.id == set.variationId }?.name.orEmpty()
        }
    }.entries.forEachIndexed { groupIndex, (variation, groupSets) ->
        if (groupIndex > 0) HorizontalDivider(color = palette.border.copy(alpha = 0.3f))
        if (variation.isNotBlank()) Text(variation, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        groupSets.forEachIndexed { setIndex, set ->
            if (setIndex > 0) HorizontalDivider(color = palette.border.copy(alpha = 0.3f))
            ProgressRecordedSet(set, bandLinks.filter { it.setId == set.id }.sortedBy { it.ordinal }, data.bands)
            ProgressWorkoutNotes(set.notes)
        }
    }
}

@Composable
private fun ProgressRecordedSet(set: WorkoutSetEntity, selectedBands: List<WorkoutSetBandEntity>, bands: List<BandEntity>) {
    val palette = LocalVibePalette.current
    val values = recordedSetValues(set)
    val resistance = values.filter {
        when (it.first) {
            "Weight" -> set.weightKg != 0.0
            "Added weight" -> set.addedWeightKg != 0.0
            else -> false
        }
    }
    val performance = values.filter { it.first !in setOf("Weight", "Added weight", "Assistance", "RPE") }
    val hold = performance.firstOrNull { it.first == "Hold" }
    val total = performance.firstOrNull { it.first == "Total time" }
    val displayedPerformance = if (hold != null && total != null) {
        performance.filter { it.first != "Total time" }.map { if (it.first == "Hold") "Hold" to "${hold.second} / ${total.second}" else it }
    } else performance
    val rpe = values.filter { it.first == "RPE" }
    val hasResistance = resistance.isNotEmpty() || selectedBands.isNotEmpty() || set.bandResistance
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (hasResistance) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                resistance.forEach { Text(inlineSetValue(it), style = MaterialTheme.typography.bodyMedium) }
                selectedBands.forEach { link ->
                    val band = bands.find { it.id == link.bandId }
                    val name = link.nameSnapshot.ifBlank { band?.name ?: "Band unavailable" }
                    if (band == null) Text("Band colour unavailable", Modifier.semantics { contentDescription = name }, style = MaterialTheme.typography.bodySmall)
                    else BandColourDot(Color(band.colourArgb), Modifier.semantics { contentDescription = name })
                }
                if (selectedBands.isEmpty() && set.bandResistance) Text("Bands not recorded", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (displayedPerformance.isNotEmpty()) {
            if (hasResistance) Text("·", color = palette.textSecondary)
            Text(
                displayedPerformance.joinToString(" · ", transform = ::inlineSetValue),
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                textAlign = if (!hasResistance) TextAlign.Start else if (rpe.isEmpty()) TextAlign.End else TextAlign.Center,
            )
        }
        if (rpe.isNotEmpty()) {
            if (hasResistance || displayedPerformance.isNotEmpty()) Text("·", color = palette.textSecondary)
            Text(
                rpe.joinToString(" · ", transform = ::inlineSetValue), Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = if (hasResistance || displayedPerformance.isNotEmpty()) TextAlign.End else TextAlign.Start,
            )
        } else if (!hasResistance && displayedPerformance.isEmpty()) {
            Text(if (values.isEmpty()) "No values recorded" else "-", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun inlineSetValue(value: Pair<String, String>): String {
    val (label, number) = value
    return when (label) {
        "Weight", "Hold" -> number
        "Reps" -> "$number reps"
        "Left reps" -> "$number left reps"
        "Right reps" -> "$number right reps"
        "Left hold" -> "$number left hold"
        "Right hold" -> "$number right hold"
        "Added weight" -> "$number added"
        "Assistance" -> "$number assistance"
        "RPE" -> "$number RPE"
        "ROM" -> "$number ROM"
        "Total time" -> "$number TUT"
        "Timing attempts" -> "$number attempts"
        else -> "$number $label"
    }
}

internal fun formatProgressValue(value: Double) = formatQuantitative(value, maximumFractionDigits = 2)

@Composable
private fun ProgressWorkoutNotes(notes: String) {
    if (notes.isNotBlank()) {
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Notes: ") }
            append(notes)
        }, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun workoutTime(time: Long) =
    Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

private fun workoutHeaderTime(time: Long) =
    Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mma", Locale.getDefault())).lowercase(Locale.getDefault())

private fun workoutHeader(workout: WorkoutEntity): String {
    val zone = ZoneId.systemDefault()
    val acrossDays = workout.finishedAt?.let {
        Instant.ofEpochMilli(it).atZone(zone).toLocalDate() != Instant.ofEpochMilli(workout.startedAt).atZone(zone).toLocalDate()
    } == true
    val start = "${workoutDate(workout.startedAt)}, ${workoutHeaderTime(workout.startedAt)}"
    val end = workout.finishedAt?.let {
        if (acrossDays) "${workoutDate(it)}, ${workoutHeaderTime(it)}" else workoutHeaderTime(it)
    } ?: "End time not recorded"
    return "$start - $end"
}

private fun workoutDate(time: Long): String {
    val locale = Locale.getDefault()
    val date = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
    val pattern = if (locale.language == Locale.ENGLISH.language) "d'${dayOrdinalSuffix(date.dayOfMonth)}' MMM yyyy" else "d MMM yyyy"
    return date.format(DateTimeFormatter.ofPattern(pattern, locale))
}

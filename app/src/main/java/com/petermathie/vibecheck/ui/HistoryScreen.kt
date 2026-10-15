package com.petermathie.vibecheck.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.ExerciseEntity
import com.petermathie.vibecheck.data.local.WorkoutExerciseEntity
import com.petermathie.vibecheck.data.local.WorkoutSetEntity
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.workout.formatQuantitative
import com.petermathie.vibecheck.domain.workout.decodeTimeMeasurementsMillis
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate

@Composable
fun HistoryScreen(vm:EditorViewModel,onMap:(Long)->Unit) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val exerciseRows by vm.workoutExercises.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val sets by vm.sets.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val weightUnit = if (context.getSharedPreferences("settings", 0).getBoolean("lb", false)) "lb" else "kg"
    var query by remember { mutableStateOf("") };var selected by remember { mutableStateOf<String?>(null) };var date by remember { mutableStateOf(LocalDate.now().toString()) }
    val filteredWorkouts = workouts.filter { workout -> workout.status=="FINISHED"&&(workout.name.contains(query,true)||exerciseRows.any { row -> row.workoutId==workout.id && (row.notes.contains(query,true)||row.exerciseName.contains(query,true)||exercises.any { it.id==row.actualExerciseId && it.canonicalName.contains(query,true) }) }) }
    BackHandler(selected!=null){selected=null}
    if(selected!=null) { Column(Modifier.fillMaxSize()) { VibeActionButton("Back to history", { selected=null }, importance = ActionImportance.COMPACT);Box(Modifier.weight(1f)){WorkoutEditor(vm,selected,{}, {selected=null}, onDoneEditing = { selected=null }, onDeleted = { selected=null })} };return }
    ScreenList {
        item {
            Text("History",style=MaterialTheme.typography.headlineSmall)
            VibeCard {
                EditField("Search workouts and notes",query){query=it}
                EditField("Body map date (YYYY-MM-DD)",date){date=it}
                VibeActionButton("View historical body map", { runCatching { LocalDate.parse(date).toEpochDay() }.getOrNull()?.let(onMap) }, modifier = Modifier.fillMaxWidth(), importance = ActionImportance.SECONDARY)
            }
        }
        if (filteredWorkouts.isEmpty()) {
            item { com.petermathie.vibecheck.ui.components.VibeStatePanel(if (query.isBlank()) "Finished workouts will appear here." else "No workouts match this search.") }
        }
        items(filteredWorkouts,key={it.id}) { w ->
            VibeCard {
                Text(w.name, style = MaterialTheme.typography.titleMedium)
                Text(Instant.ofEpochMilli(w.finishedAt ?: w.startedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString(), style = MaterialTheme.typography.bodySmall)
                workoutTimingSummaries(w.id, exerciseRows, exercises, sets, w.bodyweightKg, weightUnit).forEach { summary ->
                    Text(summary, style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VibeActionButton("Edit", { selected=w.id }, modifier = Modifier.weight(1f), importance = ActionImportance.SECONDARY)
                    VibeActionButton("Body map", { onMap(Instant.ofEpochMilli(w.finishedAt ?: w.startedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()) }, modifier = Modifier.weight(1f), importance = ActionImportance.SECONDARY)
                    VibeActionButton("Remove", { vm.save(w.copy(status="DISCARDED")) }, modifier = Modifier.weight(1f), importance = ActionImportance.SECONDARY)
                }
            }
        }
    }
}

internal fun workoutTimingSummaries(
    workoutId: String,
    exerciseRows: List<WorkoutExerciseEntity>,
    exercises: List<ExerciseEntity>,
    sets: List<WorkoutSetEntity>,
    bodyweightKg: Double? = null,
    weightUnit: String = "kg",
): List<String> = exerciseRows
    .filter { it.workoutId == workoutId }
    .sortedBy { it.position }
    .mapNotNull { row ->
        val exercise = exercises.find { it.id == row.actualExerciseId }
        val name = row.exerciseName.ifBlank { exercise?.canonicalName.orEmpty() }
        val values = linkedMapOf<String, MutableList<String>>()
        sets.filter { it.workoutExerciseId == row.id }.sortedBy { it.ordinal }.forEach { set ->
            val config = ExerciseInputConfig.decode(
                set.variationInputConfigSnapshot.ifBlank { row.inputConfig },
                set.variationTrackingTypeSnapshot.ifBlank {
                    row.trackingType.ifBlank { exercise?.trackingType.orEmpty() }
                },
            )
            fun add(label: String, millis: Long?) {
                if (millis != null && millis > 0L) {
                    values.getOrPut(label) { mutableListOf() } +=
                        "${formatQuantitative(millis / 1000.0)}s"
                }
            }
            add(
                when {
                    row.actualExerciseId == "core:planche" ||
                        name.equals("Planche", ignoreCase = true) -> "Total Time"
                    config.timeHeld -> "TUT"
                    else -> "Hold"
                },
                set.holdMillis,
            )
            val repeatedTimes = decodeTimeMeasurementsMillis(set.timeMeasurementsMillis)
            if (repeatedTimes.isNotEmpty()) {
                repeatedTimes.forEach { add("Times", it) }
            }
            add("Total", set.timeUnderTensionMillis)
            add("Left hold", set.leftHoldMillis)
            add("Right hold", set.rightHoldMillis)
            if (config.bodyweight && set.reps != null && set.reps > 0) {
                val displayBodyweight = bodyweightKg?.let {
                    if (weightUnit == "lb") it * 2.2046226218 else it
                }
                val displayAdded = set.addedWeightKg?.let {
                    if (weightUnit == "lb") it * 2.2046226218 else it
                }
                val load = buildString {
                    append(
                        displayBodyweight?.let { "Bodyweight ${formatHistoryWeight(it)}$weightUnit" }
                            ?: "Bodyweight not recorded",
                    )
                    if (displayAdded != null) {
                        append(" + ${formatHistoryWeight(displayAdded)}$weightUnit added")
                    }
                    append(" × ${formatQuantitative(set.reps)} reps")
                }
                values.getOrPut("Load") { mutableListOf() } += load
            }
        }
        if (values.isEmpty()) {
            null
        } else {
            "$name · " + values.entries.joinToString(" · ") { (label, durations) ->
                if (label == "Load") durations.joinToString(" / ") else "$label ${durations.joinToString(" / ")}"
            }
        }
    }

private fun formatHistoryWeight(value: Double): String =
    formatBodyweight(value).trimEnd('0').trimEnd('.')

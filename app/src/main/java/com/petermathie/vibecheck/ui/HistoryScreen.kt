package com.petermathie.vibecheck.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate

@Composable
fun HistoryScreen(vm:EditorViewModel,onMap:(Long)->Unit) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val exerciseRows by vm.workoutExercises.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val sets by vm.sets.collectAsStateWithLifecycle()
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
                Text(formatDisplayDate(w.finishedAt ?: w.startedAt), style = MaterialTheme.typography.bodySmall)
                exerciseRows.filter { it.workoutId == w.id }.sortedBy { it.position }.forEach { row ->
                    val results = sets.filter { it.workoutExerciseId == row.id }.sortedBy { it.ordinal }
                    if (results.isNotEmpty()) {
                        Text(
                            row.exerciseName.ifBlank { exercises.find { it.id == row.actualExerciseId }?.canonicalName.orEmpty() },
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(results.joinToString(" · ") { setDescription(it) }, style = MaterialTheme.typography.bodySmall)
                    }
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

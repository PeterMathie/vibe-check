package com.petermathie.vibecheck.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun ArchiveScreen(vm: EditorViewModel, onBack: () -> Unit = {}) {
    val programmes by vm.archivedProgrammes.collectAsStateWithLifecycle()
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val trackers by vm.trackers.collectAsStateWithLifecycle()
    val archivedExercises = exercises.filter { it.isArchived }.sortedBy { it.canonicalName }
    val archivedTrackers = trackers.filter { it.isArchived }.sortedBy { it.name }

    BackHandler(onBack = onBack)
    ScreenList {
        item { MoreChildTitleRow("Archive", onBack) }
        if (programmes.isEmpty() && archivedExercises.isEmpty() && archivedTrackers.isEmpty()) {
            item { com.petermathie.vibecheck.ui.components.VibeStatePanel("Archived plans, exercises, and habits will appear here.") }
        }
        if (programmes.isNotEmpty()) {
            item { Text("Plans", style = MaterialTheme.typography.titleLarge) }
            items(programmes.size, key = { programmes[it].id }) { index ->
                val programme = programmes[index]
                ArchivedItem(programme.name) { vm.save(programme.copy(isArchived = false)) }
            }
        }
        if (archivedExercises.isNotEmpty()) {
            item { Text("Exercises", style = MaterialTheme.typography.titleLarge) }
            items(archivedExercises.size, key = { archivedExercises[it].id }) { index ->
                val exercise = archivedExercises[index]
                ArchivedItem(exercise.canonicalName) { vm.saveExerciseSettings(exercise.copy(isArchived = false)) }
            }
        }
        if (archivedTrackers.isNotEmpty()) {
            item { Text("Habits", style = MaterialTheme.typography.titleLarge) }
            items(archivedTrackers.size, key = { archivedTrackers[it].id }) { index ->
                val tracker = archivedTrackers[index]
                ArchivedItem(tracker.name) { vm.save(tracker.copy(isArchived = false)) }
            }
        }
    }
}

@Composable
private fun ArchivedItem(name: String, onRestore: () -> Unit) {
    VibeCard {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, modifier = Modifier.weight(1f))
            TextButton(onClick = onRestore) { Text("Restore") }
        }
    }
}

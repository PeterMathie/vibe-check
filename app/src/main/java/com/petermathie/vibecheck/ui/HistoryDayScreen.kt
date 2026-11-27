package com.petermathie.vibecheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.domain.model.AnatomySex
import com.petermathie.vibecheck.ui.anatomy.AnatomyView
import com.petermathie.vibecheck.ui.anatomy.MuscleMap
import com.petermathie.vibecheck.ui.components.MuscleDetailsSheet
import com.petermathie.vibecheck.ui.components.muscleDetailsUiState
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.freshnessColors
import java.time.LocalDate

@Composable
internal fun HistoryDayScreen(
    state: MainUiState,
    onBack: () -> Unit,
    onDayChange: (Long) -> Unit,
) {
    val sex = if (LocalContext.current.getSharedPreferences("settings", 0).getBoolean("female", false)) {
        AnatomySex.FEMALE
    } else {
        AnatomySex.MALE
    }
    val day = state.selectedHistoryDay ?: return
    val date = LocalDate.ofEpochDay(day)
    val palette = LocalVibePalette.current
    val haptics = rememberVibeHaptics()
    var selectedMuscle by rememberSaveable(day) { mutableStateOf<String?>(null) }
    var selectedMuscleView by rememberSaveable(day) { mutableStateOf<AnatomyView?>(null) }
    val selectedRecency = state.recency.firstOrNull { it.muscleId == selectedMuscle }
    val frontMapFocusRequester = remember { FocusRequester() }
    val backMapFocusRequester = remember { FocusRequester() }
    val sheetTitleFocusRequester = remember { FocusRequester() }
    val selectMuscle: (String, AnatomyView) -> Unit = { muscleId, view ->
        selectedMuscle = muscleId
        selectedMuscleView = view
        haptics.perform(VibeHapticEvent.SELECTION)
    }
    LaunchedEffect(selectedMuscle) {
        if (selectedMuscle != null) sheetTitleFocusRequester.requestFocus()
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(palette.background)
            .semantics { contentDescription = "Historical day page" },
    ) {
        ScreenList(modifier = Modifier.statusBarsPadding()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                VibeBackChevron(onBack, "Back")
                Text(
                    formatDisplayDate(date),
                    style = MaterialTheme.typography.headlineMedium,
                    color = LocalVibePalette.current.textPrimary,
                )
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().semantics { contentDescription = "Historical day controls" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { onDayChange(day - 1) }) {
                    Icon(Icons.Outlined.KeyboardArrowLeft, "Previous day", tint = LocalVibePalette.current.textPrimary)
                }
                IconButton(onClick = { onDayChange(day + 1) }, enabled = day < LocalDate.now().toEpochDay()) {
                    Icon(Icons.Outlined.KeyboardArrowRight, "Next day", tint = LocalVibePalette.current.textPrimary)
                }
            }
        }
        item {
            VibeCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MuscleMap(
                        sex,
                        AnatomyView.FRONT,
                        state.recency.associate { it.muscleId to it.band },
                        { selectMuscle(it, AnatomyView.FRONT) },
                        Modifier.weight(1f).focusRequester(frontMapFocusRequester).focusable(),
                        selectedMuscle,
                    )
                    MuscleMap(
                        sex,
                        AnatomyView.BACK,
                        state.recency.associate { it.muscleId to it.band },
                        { selectMuscle(it, AnatomyView.BACK) },
                        Modifier.weight(1f).focusRequester(backMapFocusRequester).focusable(),
                        selectedMuscle,
                    )
                }
            }
        }
        val activities = state.historyDay?.activities.orEmpty()
        if (activities.isEmpty()) item { VibeCard { Text("No logged activity", color = palette.textPrimary) } }
        items(activities) { activity ->
            VibeCard {
                Text(activity.title, style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                Text(activity.detail, color = palette.textSecondary)
                if (activity.notes.isNotBlank()) Text(activity.notes, color = palette.textSecondary)
            }
        }
        }
        selectedMuscle?.let { muscleId ->
            MuscleDetailsSheet(
                state = muscleDetailsUiState(
                    muscleId = muscleId,
                    recency = selectedRecency,
                    mode = state.mode,
                    colour = palette.freshnessColors().forBand(
                        selectedRecency?.band ?: com.petermathie.vibecheck.domain.model.MuscleRecencyBand.NEVER,
                    ),
                ),
                titleFocusRequester = sheetTitleFocusRequester,
                onDismiss = {
                    val requester = if (selectedMuscleView == AnatomyView.BACK) {
                        backMapFocusRequester
                    } else {
                        frontMapFocusRequester
                    }
                    selectedMuscle = null
                    selectedMuscleView = null
                    requester.requestFocus()
                },
            )
        }
    }
}

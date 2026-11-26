package com.petermathie.vibecheck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.domain.model.MuscleRecency
import com.petermathie.vibecheck.domain.model.MuscleRecencyBand
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.VibeSpacing
import com.petermathie.vibecheck.ui.theme.VibeSurfaceLevel
import com.petermathie.vibecheck.ui.theme.freshnessColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.petermathie.vibecheck.ui.formatDisplayDate

internal data class MuscleDetailsUiState(
    val title: String,
    val bandLabel: String,
    val meaning: String,
    val lastEventLabel: String,
    val doseLabel: String?,
    val contributions: List<String>,
    val colour: Color,
)

internal fun muscleDetailsUiState(
    muscleId: String,
    recency: MuscleRecency?,
    mode: TrainingMode,
    colour: Color,
): MuscleDetailsUiState {
    val name = muscleId.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
    val modeLabel = if (mode == TrainingMode.STRENGTH) "Freshness" else "Stretch"
    val event = if (mode == TrainingMode.STRENGTH) "trained" else "stretched"
    val band = recency?.band ?: MuscleRecencyBand.NEVER
    val bandLabel = when (band) {
        MuscleRecencyBand.UNDER_24_HOURS -> "Under 24 hours"
        MuscleRecencyBand.HOURS_24_TO_48 -> "24 to 48 hours"
        MuscleRecencyBand.HOURS_48_TO_72 -> "48 to 72 hours"
        MuscleRecencyBand.DAYS_3_TO_7 -> "3 to 7 days"
        MuscleRecencyBand.OVER_7_DAYS -> "Over 7 days"
        MuscleRecencyBand.NEVER -> "No data"
    }
    val meaning = if (band == MuscleRecencyBand.NEVER) {
        "No finished ${if (mode == TrainingMode.STRENGTH) "training" else "stretch"} session recorded."
    } else {
        "$event $bandLabel ago"
    }
    val lastEvent = recency?.lastTrainedAt?.let {
        "Last $event: ${formatDisplayDate(it)} ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))}"
    } ?: "Last $event: No data"
    return MuscleDetailsUiState(
        title = "$name $modeLabel details",
        bandLabel = bandLabel,
        meaning = meaning,
        lastEventLabel = lastEvent,
        doseLabel = recency?.let { "%.1f set-equivalents in 7 days".format(it.setEquivalents) },
        contributions = recency?.contributingExerciseNames.orEmpty(),
        colour = colour,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MuscleDetailsSheet(
    state: MuscleDetailsUiState,
    titleFocusRequester: FocusRequester,
    onDismiss: () -> Unit,
) {
    val palette = LocalVibePalette.current
    val configuration = LocalConfiguration.current
    val fontScale = LocalDensity.current.fontScale
    val mustExpand = configuration.screenWidthDp > configuration.screenHeightDp || fontScale >= 1.3f
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = mustExpand)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surfaceFloating,
        scrimColor = palette.scrim,
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = VibeSpacing.medium, vertical = VibeSpacing.small),
            verticalArrangement = Arrangement.spacedBy(VibeSpacing.compact),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.title,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(titleFocusRequester)
                        .focusable()
                        .semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(48.dp, 48.dp),
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close muscle details")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).background(state.colour, CircleShape))
                Spacer(Modifier.width(VibeSpacing.small))
                Column {
                    Text(state.bandLabel, style = MaterialTheme.typography.titleMedium)
                    Text(state.meaning, color = palette.textSecondary)
                }
            }
            VibeSurface(VibeSurfaceLevel.INSET, Modifier.fillMaxWidth()) {
                Column(Modifier.padding(VibeSpacing.compact), verticalArrangement = Arrangement.spacedBy(VibeSpacing.small)) {
                    Text(state.lastEventLabel)
                    state.doseLabel?.let { Text(it, color = palette.textSecondary) }
                }
            }
            if (state.contributions.isNotEmpty()) {
                Text("Recent contributions", style = MaterialTheme.typography.titleMedium)
                state.contributions.take(4).forEach { Text(it, color = palette.textSecondary) }
                if (state.contributions.size > 4) Text("+${state.contributions.size - 4} more", color = palette.textSecondary)
            }
            Spacer(Modifier.height(VibeSpacing.medium))
        }
    }
}

package com.petermathie.vibecheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.data.local.BandEntity
import com.petermathie.vibecheck.data.local.WorkoutEntryDraftEntity
import com.petermathie.vibecheck.data.local.WorkoutSetEntity
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.MeasurementMetric
import com.petermathie.vibecheck.domain.programme.ResistanceMetric
import com.petermathie.vibecheck.domain.workout.*

@Composable
internal fun WorkoutSetTile(
    resistanceWidth: Int,
    measurementWidth: Int,
    header: @Composable () -> Unit,
    resistance: @Composable () -> Unit,
    measurement: @Composable () -> Unit,
    rpe: @Composable () -> Unit,
    completion: @Composable () -> Unit,
    feedback: @Composable () -> Unit,
) {
    val scale = maxOf(1f, LocalDensity.current.fontScale)
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        header()
        Layout(
            contents = listOf(
                { Column { resistance() } },
                { Column { measurement() } },
                { Box { rpe() } },
                { Box { completion() } },
            ),
            modifier = Modifier.fillMaxWidth(),
        ) { (resistanceNodes, measurementNodes, rpeNodes, completionNodes), constraints ->
            val gap = 3.dp.roundToPx()
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val tick = completionNodes.single().measure(loose)
            val effort = rpeNodes.single().measure(loose)
            val actionsWidth = tick.width + gap + effort.width
            val leftWidth = (constraints.maxWidth - actionsWidth - gap).coerceAtLeast(0)
            val preferredResistance = (resistanceWidth.dp * scale).roundToPx()
            val preferredMeasurement = (measurementWidth.dp * scale).roundToPx()
            val inline = preferredResistance + preferredMeasurement +
                (if (preferredResistance > 0) gap else 0) <= leftWidth
            val stackedLeft = maxOf(preferredResistance, preferredMeasurement) <= leftWidth
            val available = if (inline || stackedLeft) leftWidth else constraints.maxWidth
            val load = resistanceNodes.single().measure(loose.copy(
                maxWidth = minOf(preferredResistance, available),
            ))
            val result = measurementNodes.single().measure(loose.copy(
                maxWidth = if (inline) (available - load.width - if (load.width > 0) gap else 0)
                    .coerceAtLeast(0) else available,
            ))
            val controlsHeight = maxOf(effort.height, tick.height)
            val contentHeight = if (inline) maxOf(load.height, result.height)
                else load.height + (if (load.height > 0) gap else 0) + result.height
            val contentTop = if (inline || stackedLeft) 0 else controlsHeight + gap
            val height = maxOf(controlsHeight, contentTop + contentHeight)
            layout(constraints.maxWidth, height) {
                effort.placeRelative(constraints.maxWidth - actionsWidth, 0)
                tick.placeRelative(constraints.maxWidth - tick.width, 0)
                load.placeRelative(0, contentTop)
                result.placeRelative(
                    if (inline && load.width > 0) load.width + gap else 0,
                    contentTop + if (!inline && load.height > 0) load.height + gap else 0,
                )
            }
        }
        feedback()
    }
}

@Composable
internal fun ResistanceRenderer(
    metric: ResistanceMetric,
    config: ExerciseInputConfig,
    draft: WorkoutEntryDraftEntity,
    weight: String,
    weightUnit: String,
    bands: List<BandEntity>,
    selectedBands: List<String>,
    previous: WorkoutSetEntity?,
    previousBands: List<String>,
    locked: Boolean,
    enabled: Boolean,
    identity: String,
    onWeightChange: (String) -> Unit,
    onBandsChange: (List<String>) -> Unit,
) {
    when (metric) {
        ResistanceMetric.BODYWEIGHT -> Unit
        ResistanceMetric.KILOGRAMS -> {
            val added = config.addedWeight
            val unit = if (added) weightUnit else config.weightUnit ?: "kg"
            val previousWeight = if (added) previous?.addedWeightKg else previous?.weightKg
            MetricValue(
                value = if (added) draft.addedWeight else weight,
                label = if (added) "+$unit" else unit,
                description = "${if (added) "Added weight" else "Resistance"} for $identity",
                previous = previousWeight?.let { formatQuantitative(if (unit == "lb") it * 2.2046226218 else it) },
                locked = locked,
                enabled = enabled,
                width = 56,
                onChange = onWeightChange,
            )
        }
        ResistanceMetric.BANDS -> Column {
            val visible = bands.filter { !locked || it.id in selectedBands }
            visible.chunked(4).forEach { row ->
                Row {
                    row.forEach { band ->
                        BandDot(
                            band, band.id in selectedBands, enabled && !locked,
                            "${band.name} band for $identity",
                        ) {
                            onBandsChange(if (band.id in selectedBands) selectedBands - band.id else selectedBands + band.id)
                        }
                    }
                }
            }
            if (previousBands.isNotEmpty()) PreviousValue(
                previousBands.joinToString(" + "), "Previous resistance bands for $identity",
            )
        }
    }
}

@Composable
internal fun MeasurementRenderer(
    metric: MeasurementMetric,
    config: ExerciseInputConfig,
    draft: WorkoutEntryDraftEntity,
    reps: String,
    attempts: List<String>,
    previous: WorkoutSetEntity?,
    locked: Boolean,
    enabled: Boolean,
    identity: String,
    focus: Modifier,
    onRepsChange: (String) -> Unit,
    onDraftChange: (WorkoutEntryDraftEntity) -> Unit,
    onStopwatch: () -> Unit,
) {
    when (metric) {
        MeasurementMetric.REPETITIONS -> MetricValue(
            reps, "Reps", "Reps for $identity", previous?.reps?.let(::formatQuantitative),
            locked, enabled, modifier = focus, onChange = onRepsChange,
        )
        MeasurementMetric.RANGE_OF_MOTION -> MetricValue(
            draft.romValue, if (draft.romUnit == "degrees") "°" else draft.romUnit,
            "Range of motion for $identity",
            previous?.takeIf { it.romUnit == draft.romUnit }?.romValue?.let(::formatQuantitative),
            locked, enabled, width = 56, modifier = focus,
            onChange = { onDraftChange(draft.copy(romValue = it)) },
        )
        MeasurementMetric.HOLD -> {
            val totalOnly = !config.timeHeld && config.timeUnderTension
            val retainedAttempts = !totalOnly && attempts.size > 1
            val held = if (retainedAttempts) attempts.map { quantitativeInput(it) }
                .takeIf { values -> values.all { it.isValid } }
                ?.let { values -> formatQuantitative(values.sumOf { it.value ?: 0.0 }) }.orEmpty()
                else attempts.first()
            MetricValue(
                if (totalOnly) draft.timeUnderTension else held,
                if (totalOnly) "Total s" else "s",
                "${if (totalOnly) "Total Time" else "Time Under Tension"} for $identity",
                (if (totalOnly) previous?.timeUnderTensionMillis else previous?.holdMillis)
                    ?.let { formatQuantitative(it / 1000.0) },
                locked || retainedAttempts, enabled, modifier = focus,
                onChange = {
                    onDraftChange(if (totalOnly) draft.copy(timeUnderTension = it)
                        else draft.copy(
                            tutEntries = encodeTimingInputs(attempts.toMutableList().apply { set(0, it) }),
                            timeHeld = "",
                        ))
                },
            )
        }
        MeasurementMetric.SKILL_HOLD -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BoxWithConstraints {
                val scale = maxOf(1f, LocalDensity.current.fontScale)
                val columns = minOf(4, ((maxWidth + 2.dp) / (36.dp * scale + 2.dp)).toInt().coerceAtLeast(1))
                val previousAttempts = previous?.let {
                    timingMillis(it.tutEntriesMillis).ifEmpty { listOfNotNull(it.holdMillis) }
                }.orEmpty()
                val slotCount = attempts.size + if (!locked && attempts.size < MAX_TUT_ENTRIES) 1 else 0
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    (0 until slotCount).toList().chunked(columns).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            row.forEach { index ->
                                if (index < attempts.size) {
                                    MetricValue(
                                        attempts[index], "s",
                                        if (attempts.size == 1) "Time Under Tension for $identity"
                                            else "TUT attempt ${index + 1} for $identity",
                                        previousAttempts.getOrNull(index)?.let { formatQuantitative(it / 1000.0) },
                                        locked, enabled, width = 36,
                                        modifier = if (index == 0) focus else Modifier,
                                        onChange = { value ->
                                            onDraftChange(draft.copy(
                                                tutEntries = encodeTimingInputs(attempts.toMutableList().apply { set(index, value) }),
                                                timeHeld = "",
                                            ))
                                        },
                                    )
                                } else Box(
                                    Modifier.width(36.dp * scale).heightIn(min = 48.dp)
                                        .align(Alignment.CenterVertically),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    IconButton(
                                        enabled = enabled,
                                        onClick = {
                                            onDraftChange(draft.copy(tutEntries = encodeTimingInputs(attempts + ""), timeHeld = ""))
                                        },
                                    ) {
                                        Icon(Icons.Outlined.Add, "Add TUT entry for $identity", Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (previousAttempts.size > attempts.size) PreviousValue(
                        previousAttempts.drop(attempts.size).mapIndexed { index, millis ->
                            "${attempts.size + index + 1}: ${formatQuantitative(millis / 1000.0)} s"
                        }.joinToString(" · "),
                        "Previous remaining TUT attempts for $identity",
                    )
                }
            }
            val values = attempts.map { quantitativeInput(it) }
            if (values.all { it.isValid } && values.any { it.value != null }) Text(
                "${formatQuantitative(values.sumOf { it.value ?: 0.0 })} s",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.semantics { contentDescription = "TUT total for $identity" },
            )
            MetricValue(
                draft.timeUnderTension, "Total s", "Total Time for $identity",
                previous?.timeUnderTensionMillis?.let { formatQuantitative(it / 1000.0) },
                locked, enabled, width = 104,
                trailing = if (locked) null else ({
                    IconButton(onClick = onStopwatch) {
                        Icon(Icons.Outlined.Timer, "Open Total Time stopwatch for $identity", Modifier.size(18.dp))
                    }
                }),
                onChange = { onDraftChange(draft.copy(timeUnderTension = it)) },
            )
        }
    }
}

@Composable
internal fun MetricValue(
    value: String,
    label: String,
    description: String,
    previous: String?,
    locked: Boolean,
    enabled: Boolean,
    width: Int = 44,
    modifier: Modifier = Modifier,
    maximum: Double = 1_000_000_000.0,
    secondary: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    val scale = maxOf(1f, LocalDensity.current.fontScale)
    Column(Modifier.width(width.dp * scale)) {
        if (locked) {
            if (value.isNotBlank()) Text(
                "$value $label",
                style = if (secondary) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = if (secondary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = description },
            )
        } else QuantitativeTextField(
            value, onChange, label, description, modifier.fillMaxWidth(),
            enabled = enabled, maximum = maximum, trailingIcon = trailing, onNext = onNext, secondary = secondary,
        )
        if (previous != null) PreviousValue(previous, "Previous $description")
    }
}

@Composable
private fun PreviousValue(value: String, description: String) {
    Text(
        "Prev $value",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { contentDescription = description },
    )
}

@Composable
private fun BandDot(band: BandEntity, selected: Boolean, enabled: Boolean, description: String, onClick: () -> Unit) {
    val colour = Color(band.colourArgb)
    Box(
        Modifier.width(24.dp).height(48.dp).clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(22.dp)
                .border(if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    CircleShape)
                .padding(3.dp)
                .background(colour, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(
                Icons.Outlined.Check, null, Modifier.size(14.dp),
                tint = if (colour.luminance() > 0.45f) Color.Black else Color.White,
            )
        }
    }
}

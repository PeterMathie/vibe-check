package com.petermathie.vibecheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.data.local.TrackerDailyValueEntity
import com.petermathie.vibecheck.data.local.TrackerFieldEntity
import com.petermathie.vibecheck.domain.tracker.*
import com.petermathie.vibecheck.domain.workout.formatQuantitative
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.habitHeatmapColors

@Composable
internal fun HabitDailyInput(
    vm: EditorViewModel,
    field: TrackerFieldEntity,
    value: TrackerDailyValueEntity?,
    epoch: Long?,
    habitColour: Color,
) {
    val haptics = rememberVibeHaptics()
    val onLogged = { haptics.perform(VibeHapticEvent.SUCCESS) }
    key(field.id, epoch) {
        var text by remember { mutableStateOf(value?.numericValue?.let(::formatQuantitative) ?: value?.textValue.orEmpty()) }
        var focused by remember { mutableStateOf(false) }
        LaunchedEffect(value, focused) {
            if (!focused) text = value?.numericValue?.let(::formatQuantitative) ?: value?.textValue.orEmpty()
        }
        when (field.valueType) {
            "BOOLEAN" -> Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = value?.booleanValue == true,
                    onCheckedChange = { checked ->
                        epoch?.let {
                            vm.save(TrackerDailyValueEntity(field.id, it, null, checked, null, "", System.currentTimeMillis()), onLogged)
                        }
                    },
                    enabled = epoch != null,
                    modifier = Modifier.semantics { contentDescription = field.name },
                )
                Text(field.name, Modifier.weight(1f, fill = false), textAlign = TextAlign.End)
                if (value != null) ClearHabitEntry(field.name) { epoch?.let { vm.clearValue(field.id, it) } }
            }
            "CHOICE" -> ChoiceInput(
                field.name, decodeHabitChoices(field), value?.choiceOptionId, value?.textValue.orEmpty(),
                habitColour, onClear = { epoch?.let { vm.clearValue(field.id, it) } },
            ) { option ->
                epoch?.let { day ->
                    vm.save(
                        TrackerDailyValueEntity(
                            field.id, day, null, null, option.label, "", System.currentTimeMillis(),
                            option.id, option.intensity.name,
                        ),
                        onLogged,
                    )
                }
            }
            else -> {
                val numeric = field.valueType in numericHabitTypes
                val number = text.toDoubleOrNull()
                val error = if (numeric && text.isNotBlank()) habitNumericError(field.valueType, number) else null
                HabitTextField(
                    field.unit?.takeIf(String::isNotBlank) ?: field.name, text,
                    onValueChange = { input ->
                        text = input
                        epoch?.let { day ->
                            val parsed = input.toDoubleOrNull()
                            when {
                                input.isBlank() -> vm.clearValue(field.id, day)
                                !numeric || habitNumericError(field.valueType, parsed) == null -> vm.save(
                                    TrackerDailyValueEntity(
                                        field.id, day, if (numeric) parsed else null, null,
                                        if (numeric) null else input, "", System.currentTimeMillis(),
                                    ),
                                    onLogged,
                                )
                            }
                        }
                    },
                    modifier = Modifier.onFocusChanged { focused = it.isFocused },
                    numeric = numeric,
                    error = error,
                    plain = true,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun ClearHabitEntry(name: String, onClear: () -> Unit) {
    IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, "Clear $name entry") }
}

@Composable
private fun ChoiceInput(
    label: String,
    options: List<HabitChoiceOption>,
    selectedId: String?,
    selectedLabel: String,
    habitColour: Color,
    onClear: () -> Unit,
    onSelect: (HabitChoiceOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val palette = LocalVibePalette.current
    val shades = palette.habitHeatmapColors(habitColour)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { expanded = true }, modifier = Modifier.weight(1f)) {
            Text("$label: ${selectedLabel.ifBlank { "Choose..." }}", Modifier.fillMaxWidth(), textAlign = TextAlign.End)
        }
        if (selectedLabel.isNotBlank()) ClearHabitEntry(label, onClear)
    }
    if (expanded) {
        HabitGlassDialog(
            title = label, onDismiss = { expanded = false },
            actions = { TextButton(onClick = { expanded = false }) { Text("Cancel") } },
        ) {
            HabitChoiceIntensity.entries.forEach { intensity ->
                options.filter { it.intensity == intensity }.chunked(3).forEach { group ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.forEach { option ->
                            val selected = option.id == selectedId || selectedId == null && option.label == selectedLabel
                            TextButton(
                                onClick = { onSelect(option); expanded = false },
                                modifier = Modifier.weight(1f).semantics {
                                    contentDescription = "${option.label}, ${option.intensity.name.lowercase()} shade"
                                },
                            ) {
                                Box(Modifier.size(12.dp).background(shades.forLevel(option.intensity.heatmapLevel), CircleShape))
                                Spacer(Modifier.width(8.dp))
                                Text(option.label, Modifier.weight(1f), color = palette.textPrimary, textAlign = TextAlign.Start)
                                if (selected) Icon(Icons.Outlined.Check, "Selected", Modifier.padding(start = 4.dp).size(18.dp))
                            }
                        }
                        repeat(3 - group.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

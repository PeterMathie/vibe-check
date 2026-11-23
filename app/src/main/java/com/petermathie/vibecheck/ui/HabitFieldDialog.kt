package com.petermathie.vibecheck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.data.local.TrackerFieldEntity
import com.petermathie.vibecheck.domain.tracker.HabitFieldForm
import com.petermathie.vibecheck.domain.tracker.HabitChoiceIntensity
import com.petermathie.vibecheck.domain.tracker.HabitChoiceOption
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.habitHeatmapColors
import java.util.UUID

private data class ChoiceDraft(
    val id: String,
    val value: String,
    val intensity: HabitChoiceIntensity,
    val position: Int,
)

@Composable
internal fun HabitMeasurementForm(
    field: TrackerFieldEntity,
    form: HabitFieldForm,
    habitColour: Color,
    locked: Boolean,
    onChange: (HabitFieldForm) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (locked) {
            HabitSettingValue(
                "Measurement type",
                habitTypeLabel(field.valueType),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "Measurement type (locked)"
                },
                trailingIcon = Icons.Outlined.Lock,
            )
        } else {
            HabitSettingDropdown(
                "Measurement type", form.type,
                listOf(
                    HabitSettingOption("BOOLEAN", habitTypeLabel("BOOLEAN")),
                    HabitSettingOption("NUMBER", habitTypeLabel("NUMBER")),
                    HabitSettingOption("TEXT", habitTypeLabel("TEXT")),
                    HabitSettingOption(HabitFieldForm.CHOICE, habitTypeLabel(HabitFieldForm.CHOICE)),
                ),
                onSelect = { onChange(form.copy(type = it)) },
            )
        }
        HabitTextField(
            "Measurement name", form.name, { onChange(form.copy(name = it)) },
            hint = when (form.type) {
                "NUMBER" -> "e.g. Time practised"
                "TEXT" -> "e.g. Reflection"
                HabitFieldForm.CHOICE -> "e.g. Mood"
                else -> "e.g. Practised"
            },
        )
        if (form.isNumeric) {
            if (locked) {
                HabitSettingValue(
                    "Unit", form.unit.ifBlank { "None" },
                    modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Unit (locked)" },
                    trailingIcon = Icons.Outlined.Lock,
                )
            } else {
                HabitTextField(
                    "Unit (optional)", form.unit, { onChange(form.copy(unit = it)) },
                    hint = "e.g. min",
                )
            }
            HabitSettingDropdown(
                "Goal (optional)", form.comparison,
                (listOf(HabitFieldForm.NONE) + HabitFieldForm.COMPARISONS).map {
                    HabitSettingOption(it, if (it == HabitFieldForm.NONE) "None" else targetComparisonLabel(it))
                },
                onSelect = {
                    onChange(
                        if (it == HabitFieldForm.NONE) form.copy(comparison = it, target = "", targetMaximum = "")
                        else form.copy(comparison = it),
                    )
                },
            )
            if (form.comparison != HabitFieldForm.NONE) {
                HabitTextField(
                    if (form.comparison == HabitFieldForm.RANGE) "Minimum goal" else "Goal value",
                    form.target, { onChange(form.copy(target = it)) }, numeric = true,
                    error = goalInputError(form.target, if (locked) field.targetValue else null),
                )
                if (form.comparison == HabitFieldForm.RANGE && form.target.isNotBlank()) {
                    HabitTextField(
                        "Maximum goal", form.targetMaximum,
                        { onChange(form.copy(targetMaximum = it)) }, numeric = true,
                        error = goalInputError(form.targetMaximum, if (locked) field.targetMaxValue else null)
                            ?: if (!form.isRangeValid) "Maximum must be at least the minimum" else null,
                    )
                }
            }
        }
        if (form.type == HabitFieldForm.CHOICE) {
            ChoiceScaleEditor(field.id, form, habitColour, onChange)
        }
    }
}

private fun goalInputError(text: String, previous: Double?): String? {
    if (text.isBlank()) return null
    val value = text.toDoubleOrNull()
    return if (value == null || !value.isFinite() || value < 0 && value != previous) {
        "Enter a number greater than or equal to zero"
    } else null
}

internal fun resolvedHabitFieldForm(field: TrackerFieldEntity): HabitFieldForm {
    val initial = HabitFieldForm.from(field)
    val size = initial.choiceValues.size
    if (initial.type != HabitFieldForm.CHOICE || size < 2) return initial
    val light = initial.choiceLightThrough
        .takeIf { it in 0 until (size - 1) }
        ?: ((size - 1) / 3).coerceAtLeast(0)
    val dark = initial.choiceDarkFrom
        .takeIf { it in 1 until size && it > light }
        ?: ((size * 2 + 2) / 3).coerceIn(light + 1, size - 1)
    return initial.copy(choiceLightThrough = light, choiceDarkFrom = dark)
}

@Composable
internal fun ChoiceScaleEditor(
    fieldKey: String,
    form: HabitFieldForm,
    habitColour: Color,
    onFormChange: (HabitFieldForm) -> Unit,
) {
    val baseForm by androidx.compose.runtime.rememberUpdatedState(form)
    var editorForm by remember(fieldKey) { mutableStateOf(form) }
    var revision by remember(fieldKey) { mutableIntStateOf(0) }
    val choices = remember(fieldKey) {
        mutableStateListOf<ChoiceDraft>().apply {
            if (form.choices.isEmpty()) {
                repeat(2) {
                    add(ChoiceDraft(UUID.randomUUID().toString(), "", HabitChoiceIntensity.LIGHT, it))
                }
            } else {
                addAll(form.choices.map { ChoiceDraft(it.id, it.label, it.intensity, it.position) })
            }
        }
    }
    LaunchedEffect(revision) {
        if (revision > 0) onFormChange(editorForm)
    }
    fun syncChoices() {
        val normalized = HabitChoiceIntensity.entries.flatMap { intensity ->
            choices.filter { it.intensity == intensity }
                .sortedBy(ChoiceDraft::position)
                .mapIndexed { index, choice -> choice.copy(position = index) }
        }
        choices.clear()
        choices.addAll(normalized)
        editorForm = baseForm.copy(
            options = choices.joinToString("\n") { it.value },
            choices = choices.map { HabitChoiceOption(it.id, it.value.trim(), it.intensity, it.position) },
        )
        revision++
    }
    fun marker(intensity: HabitChoiceIntensity) = "$fieldKey:intensity:${intensity.name}"
    val choiceKeys = HabitChoiceIntensity.entries.flatMap { intensity ->
        listOf(marker(intensity)) + choices.filter { it.intensity == intensity }
            .sortedBy(ChoiceDraft::position)
            .map(ChoiceDraft::id)
    }
    lateinit var choiceOrder: ReorderState
    choiceOrder = rememberReorderState(choiceKeys) { key, _, _ ->
        val order = choiceOrder.keysSnapshot()
        var currentIntensity = HabitChoiceIntensity.LIGHT
        val updated = mutableListOf<ChoiceDraft>()
        val positions = mutableMapOf<HabitChoiceIntensity, Int>()
        order.forEach { orderedKey ->
            val markerIntensity = HabitChoiceIntensity.entries.firstOrNull { marker(it) == orderedKey }
            if (markerIntensity != null) {
                currentIntensity = markerIntensity
            } else {
                choices.firstOrNull { it.id == orderedKey }?.let { choice ->
                    val position = positions.getOrDefault(currentIntensity, 0)
                    updated += choice.copy(intensity = currentIntensity, position = position)
                    positions[currentIntensity] = position + 1
                }
            }
        }
        choices.clear()
        choices.addAll(updated)
        syncChoices()
    }
    val heatmapColors = LocalVibePalette.current.habitHeatmapColors(habitColour)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val markerKeys = HabitChoiceIntensity.entries.map(::marker).toSet()
        val choiceIds = choices.mapTo(mutableSetOf(), ChoiceDraft::id)
        val displayedKeys = choiceOrder.keysSnapshot().filter { it in markerKeys || it in choiceIds }
        displayedKeys.forEachIndexed { orderIndex, orderedKey ->
            key(orderedKey) {
                val intensity = HabitChoiceIntensity.entries.firstOrNull { marker(it) == orderedKey }
                    ?: displayedKeys.take(orderIndex + 1).mapNotNull { candidate ->
                        HabitChoiceIntensity.entries.firstOrNull { marker(it) == candidate }
                    }.last()
                val shadeColor = when (intensity) {
                    HabitChoiceIntensity.LIGHT -> heatmapColors.low
                    HabitChoiceIntensity.MEDIUM -> heatmapColors.medium
                    HabitChoiceIntensity.DARK -> heatmapColors.strong
                }
                if (orderedKey == marker(intensity)) {
                    val count = displayedKeys.drop(orderIndex + 1)
                        .takeWhile { candidate -> HabitChoiceIntensity.entries.none { marker(it) == candidate } }
                        .size
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${intensity.name.lowercase().replaceFirstChar(Char::uppercase)} · $count", style = MaterialTheme.typography.labelLarge)
                        ReorderItem(choiceOrder, orderedKey, orderIndex) {
                            Surface(
                                color = shadeColor.copy(alpha = if (count == 0) 0.18f else 0.8f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (count == 0) 48.dp else 8.dp)
                                    .semantics { contentDescription = "${intensity.name.lowercase()} choice drop target" },
                            ) {}
                        }
                    }
                } else {
                    choices.firstOrNull { it.id == orderedKey }?.let { choice ->
                        ReorderItem(choiceOrder, choice.id, orderIndex) {
                        Surface(
                            color = Color.Transparent,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics {
                                    contentDescription = "${choice.value.ifBlank { "Unnamed choice" }} intensity controls"
                                    customActions = HabitChoiceIntensity.entries
                                        .filter { it != choice.intensity }
                                        .map { target ->
                                            CustomAccessibilityAction("Move to ${target.name.lowercase().replaceFirstChar(Char::uppercase)}") {
                                                val targetPosition = choices.count { it.intensity == target }
                                                val itemIndex = choices.indexOfFirst { it.id == choice.id }
                                                if (itemIndex < 0) return@CustomAccessibilityAction false
                                                choices[itemIndex] = choice.copy(intensity = target, position = targetPosition)
                                                syncChoices()
                                                true
                                            }
                                        }
                                },
                        ) {
                    Row(
                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        ReorderHandle(
                            choiceOrder,
                            choice.id,
                            choice.value.ifBlank { "unnamed choice" },
                            enabled = choices.size > 1,
                        )
                        HabitTextField(
                            label = "Choice",
                            value = choice.value,
                            onValueChange = { updated ->
                                val itemIndex = choices.indexOfFirst { it.id == choice.id }
                                choices[itemIndex] = choice.copy(value = updated)
                                syncChoices()
                            },
                            modifier = Modifier.weight(1f),
                            accessibilityLabel = "${intensity.name.lowercase()} choice ${
                                displayedKeys.subList(
                                    displayedKeys.indexOf(marker(intensity)) + 1,
                                    orderIndex + 1,
                                ).count { candidate -> HabitChoiceIntensity.entries.none { marker(it) == candidate } }
                            }",
                        )
                        IconButton(
                            enabled = choices.size > 2,
                            onClick = {
                                choiceOrder.cancel()
                                choices.removeAll { it.id == choice.id }
                                syncChoices()
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Remove ${choice.value.ifBlank { "choice ${choice.position + 1}" }}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            }
                        }
                        }
                    }
                }
            }
        }
        }
        TextButton(
            onClick = {
                choices += ChoiceDraft(
                    UUID.randomUUID().toString(),
                    "",
                    HabitChoiceIntensity.LIGHT,
                    choices.count { it.intensity == HabitChoiceIntensity.LIGHT },
                )
                syncChoices()
            },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Add choice" },
            shape = MaterialTheme.shapes.medium,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Text("Add choice")
        }
        if (
            choices.size < 2 ||
            choices.map { it.value.trim() }.distinct().size != choices.size ||
            choices.any { it.value.isBlank() }
        ) {
            Text(
                "Enter at least two unique, named choices.",
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

internal fun habitTypeLabel(type: String): String = when (type) {
    "BOOLEAN" -> "Yes or no"
    "NUMBER" -> "Number"
    "COUNT", "DURATION", "RATING" -> "Number"
    "TEXT" -> "Written note"
    HabitFieldForm.CHOICE -> "Choose from a list"
    "DATETIME" -> "Written note"
    else -> type.lowercase().replace('_', ' ')
}

internal fun targetComparisonLabel(comparison: String): String = when (comparison) {
    "AT_LEAST" -> "At least"
    "AT_MOST" -> "At most"
    "EXACTLY" -> "Exactly"
    HabitFieldForm.RANGE -> "Between two values"
    else -> comparison.lowercase().replace('_', ' ')
}

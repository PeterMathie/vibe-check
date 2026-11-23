package com.petermathie.vibecheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.domain.tracker.*
import com.petermathie.vibecheck.ui.theme.LocalVibePalette

internal data class HabitEditorSeed(
    val tracker: TrackerEntity,
    val fields: List<TrackerFieldEntity>,
    val creating: Boolean,
)

private enum class HabitEditorPage { MAIN, FIELD, COLOUR, ICON, EXIT, ARCHIVE }

@Composable
internal fun HabitEditorDialog(
    seed: HabitEditorSeed,
    onDismiss: () -> Unit,
    onSave: (TrackerEntity, List<TrackerFieldEntity>, () -> Unit, (String) -> Unit) -> Unit,
) {
    var tracker by remember(seed) { mutableStateOf(seed.tracker) }
    var fields by remember(seed) { mutableStateOf(seed.fields) }
    var page by remember { mutableStateOf(HabitEditorPage.MAIN) }
    var returnPage by remember { mutableStateOf(HabitEditorPage.MAIN) }
    var fieldDraft by remember { mutableStateOf<TrackerFieldEntity?>(null) }
    var fieldForm by remember { mutableStateOf<HabitFieldForm?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val palette = LocalVibePalette.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(page) { focus.clearFocus(); keyboard?.hide() }
    val active = fields.filterNot { it.isArchived }.sortedBy { it.position }
    val primary = habitMeasurement(fields)
    fun original(id: String) = if (seed.creating) null else seed.fields.find { it.id == id }
    fun applyForm(): TrackerFieldEntity? {
        val field = fieldDraft ?: return null
        val form = fieldForm ?: return null
        return form.applyTo(field).let {
            if (original(field.id) != null) it.copy(valueType = field.valueType, unit = field.unit) else it
        }
    }
    val formValid = fieldDraft?.let { fieldForm?.isValidFor(original(it.id)) } != false
    val configurationValid = tracker.name.isNotBlank() && primary != null &&
        (primary == original(primary.id) || HabitFieldForm.from(primary).isValidFor(original(primary.id)))
    val canArchive = configurationValid || active.isEmpty() && tracker.name.isNotBlank()
    val dirty = tracker != seed.tracker || fields != seed.fields ||
        fieldDraft?.let { fieldForm != resolvedHabitFieldForm(it) } == true
    fun requestExit() {
        if (saving) return
        if (page == HabitEditorPage.EXIT) { page = returnPage; return }
        if (dirty) {
            error = null
            returnPage = page
            page = HabitEditorPage.EXIT
        } else onDismiss()
    }
    fun openField(field: TrackerFieldEntity) {
        fieldDraft = field
        fieldForm = resolvedHabitFieldForm(field)
        error = null
        page = HabitEditorPage.FIELD
    }
    fun commitField(): Boolean {
        if (!formValid) {
            error = "Complete the measurement name, goal and choices, or close to discard changes."
            return false
        }
        applyForm()?.let { updated ->
            fields = if (fields.any { it.id == updated.id }) fields.map { if (it.id == updated.id) updated else it }
            else fields + updated
        }
        fieldDraft = null
        fieldForm = null
        error = null
        return true
    }
    fun back() {
        if (saving) return
        when (page) {
            HabitEditorPage.FIELD -> if (!formValid) requestExit()
                else if (commitField()) page = HabitEditorPage.MAIN
            HabitEditorPage.EXIT -> page = returnPage
            else -> page = HabitEditorPage.MAIN
        }
    }
    fun save(archive: Boolean = false) {
        if (saving || !formValid) return
        val pending = applyForm()
        val updated = if (pending == null) fields
        else if (fields.any { it.id == pending.id }) fields.map { if (it.id == pending.id) pending else it }
        else fields + pending
        if (tracker.name.isBlank() || !(archive && updated.none { !it.isArchived }) &&
            habitMeasurement(updated) == null) {
            error = "Enter a habit name and configure its measurement."
            return
        }
        saving = true
        error = null
        focus.clearFocus()
        keyboard?.hide()
        onSave(
            tracker.copy(name = tracker.name.trim(), isArchived = archive),
            updated,
            onDismiss,
            { message -> saving = false; error = message },
        )
    }
    HabitGlassDialog(
        title = when (page) {
            HabitEditorPage.MAIN -> if (seed.creating) "New habit" else "Edit habit"
            HabitEditorPage.FIELD -> "Measurement"
            HabitEditorPage.COLOUR -> "Habit colour"
            HabitEditorPage.ICON -> "Habit icon"
            HabitEditorPage.EXIT -> "Save changes?"
            HabitEditorPage.ARCHIVE -> "Archive habit?"
        },
        onDismiss = ::requestExit,
        onBack = if (page == HabitEditorPage.MAIN) null else ::back,
        actions = {
            if (saving) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else when (page) {
                HabitEditorPage.MAIN -> TextButton(
                    onClick = { save() }, enabled = configurationValid && !saving,
                ) { Text(if (saving) "Saving..." else "Save") }
                HabitEditorPage.FIELD -> TextButton(
                    onClick = { if (commitField()) page = HabitEditorPage.MAIN }, enabled = formValid,
                ) { Text("Done") }
                HabitEditorPage.EXIT -> Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = ::back, modifier = Modifier.weight(1f), enabled = !saving,
                    ) { Text("Keep editing") }
                    TextButton(
                        onClick = onDismiss, modifier = Modifier.weight(1f), enabled = !saving,
                    ) { Text("Discard") }
                    if (formValid && tracker.name.isNotBlank() && (fieldDraft != null || configurationValid)) {
                        TextButton(onClick = { save() }, modifier = Modifier.weight(1f), enabled = !saving) {
                            Text(if (saving) "Saving..." else "Save")
                        }
                    }
                }
                HabitEditorPage.ARCHIVE -> TextButton(
                    onClick = { save(archive = true) }, enabled = canArchive && !saving,
                ) { Text(if (saving) "Saving..." else "Archive") }
                else -> Unit
            }
        },
    ) {
        if (!saving) when (page) {
            HabitEditorPage.MAIN -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    IconButton(
                        onClick = { page = HabitEditorPage.COLOUR },
                        modifier = Modifier.padding(bottom = 4.dp).semantics { contentDescription = "Change habit colour" },
                    ) {
                        Box(Modifier.size(28.dp).background(Color(tracker.colourArgb.toInt()), CircleShape))
                    }
                    IconButton(
                        onClick = { page = HabitEditorPage.ICON },
                        modifier = Modifier.padding(bottom = 4.dp).semantics { contentDescription = "Change habit icon" },
                    ) { Icon(HabitIconCatalog.icon(tracker.iconName), null, tint = Color(tracker.colourArgb.toInt())) }
                    HabitTextField("Habit name", tracker.name, { tracker = tracker.copy(name = it) }, Modifier.weight(1f))
                }
                primary?.let { field ->
                    HabitSettingValue(
                        "Measurement", field.name.ifBlank { "Add measurement" },
                        onClick = { openField(field) },
                        detail = if (field.name.isBlank()) null
                            else habitTypeLabel(field.valueType) + field.unit?.let { " ($it)" }.orEmpty(),
                        trailingIcon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    )
                }
                if (primary == null && fields.isEmpty()) {
                    HabitSettingValue(
                        "Measurement", "Add measurement",
                        onClick = {
                            openField(TrackerFieldEntity(newId(), tracker.id, "", "BOOLEAN", null, null, null, 0))
                        },
                        trailingIcon = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    )
                }
                if (primary?.valueType in numericHabitTypes) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Use goal shading", Modifier.weight(1f))
                        Switch(
                            checked = tracker.heatmapMode == "GOAL",
                            onCheckedChange = { tracker = tracker.copy(heatmapMode = if (it) "GOAL" else "RELATIVE") },
                            modifier = Modifier.semantics { contentDescription = "Use goal shading" },
                            colors = SwitchDefaults.colors(
                                uncheckedThumbColor = palette.textSecondary,
                                uncheckedTrackColor = palette.surfaceInset,
                                uncheckedBorderColor = palette.textSecondary,
                            ),
                        )
                    }
                }
                fields.filter { it.isArchived }.sortedBy { it.position }.take(if (primary == null) 1 else 0).forEach { field ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${field.name} (archived)", Modifier.weight(1f), color = palette.textSecondary)
                        TextButton(onClick = {
                            fields = fields.map {
                                if (it.id == field.id) it.copy(isArchived = false, position = active.size) else it
                            }
                        }) { Text("Restore") }
                    }
                }
                if (!seed.creating) TextButton(
                    onClick = { page = HabitEditorPage.ARCHIVE },
                    enabled = canArchive,
                ) { Text("Archive habit") }
            }
            HabitEditorPage.FIELD -> fieldDraft?.let { field ->
                fieldForm?.let { form ->
                    HabitMeasurementForm(
                        field, form, Color(tracker.colourArgb.toInt()), original(field.id) != null,
                        onChange = { fieldForm = it; error = null },
                    )
                }
            }
            HabitEditorPage.COLOUR -> HabitOptionsGrid(habitColours) { (name, colour), modifier ->
                IconButton(
                    onClick = { tracker = tracker.copy(colourArgb = colour); page = HabitEditorPage.MAIN },
                    modifier = modifier.semantics { contentDescription = "Set habit colour $name" },
                ) { Box(Modifier.size(32.dp).background(Color(colour.toInt()), CircleShape)) }
            }
            HabitEditorPage.ICON -> HabitOptionsGrid(HabitIconCatalog.pickerOptions) { option, modifier ->
                IconButton(
                    onClick = { tracker = tracker.copy(iconName = option.key); page = HabitEditorPage.MAIN },
                    modifier = modifier.background(
                        if (tracker.iconName == option.key) palette.surfaceSelected else Color.Transparent, CircleShape,
                    ).semantics { contentDescription = "Set habit icon ${option.label}" },
                ) { Icon(option.icon, null, tint = Color(tracker.colourArgb.toInt())) }
            }
            HabitEditorPage.EXIT -> Text("Save this habit or discard your changes.")
            HabitEditorPage.ARCHIVE -> Text("Keep all entries and hide this habit. Your changes will be saved.")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun <T> HabitOptionsGrid(options: List<T>, content: @Composable (T, Modifier) -> Unit) {
    Column {
        options.chunked(6).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { content(it, Modifier.weight(1f)) }
            }
        }
    }
}

private val habitColours = listOf(
    "Light pink" to 0xFFF8BBD0L, "Pale yellow" to 0xFFFFF59DL, "Light lime" to 0xFFDCE775L,
    "Light mint" to 0xFFB2DFDBL, "Light blue" to 0xFF90CAF9L, "Lavender" to 0xFFD1C4E9L,
    "Pink" to 0xFFEC407AL, "Yellow" to 0xFFFFEE58L, "Lime" to 0xFFCDDC39L,
    "Mint" to 0xFF80CBC4L, "Sky blue" to 0xFF81D4FAL, "Lilac" to 0xFFB39DDBL,
    "Red" to 0xFFEF5350L, "Orange" to 0xFFFFA726L, "Green" to 0xFF66BB6AL,
    "Teal" to 0xFF26A69AL, "Blue" to 0xFF42A5F5L, "Purple" to 0xFF7E57C2L,
    "Crimson" to 0xFFE53935L, "Amber" to 0xFFFFB300L, "Leaf green" to 0xFF43A047L,
    "Turquoise" to 0xFF00ACC1L, "Cobalt" to 0xFF1E88E5L, "Violet" to 0xFF8E24AAL,
    "Dark red" to 0xFFC62828L, "Burnt orange" to 0xFFEF6C00L, "Forest green" to 0xFF2E7D32L,
    "Dark teal" to 0xFF00897BL, "Deep blue" to 0xFF1565C0L, "Dark purple" to 0xFF6A1B9AL,
    "Burgundy" to 0xFF880E4FL, "Brown" to 0xFF6D4C41L, "Olive" to 0xFF827717L,
    "Pine" to 0xFF004D40L, "Navy" to 0xFF0D47A1L, "Plum" to 0xFF4A148CL,
)

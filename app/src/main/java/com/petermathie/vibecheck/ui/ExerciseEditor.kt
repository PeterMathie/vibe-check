package com.petermathie.vibecheck.ui

import android.content.Context
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.domain.model.TrackingType
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.domain.programme.*
import com.petermathie.vibecheck.domain.workout.loggingMetricsError
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.LocalVibeReducedMotion
import java.io.File
import kotlinx.coroutines.launch

private data class ExerciseItem(
    val base: ExerciseEntity,
    val variant: ExerciseVariationEntity?,
    val muscles: Map<String, String>,
) {
    val id get() = variant?.id ?: base.id
    val name get() = variant?.name ?: base.canonicalName
    val effective: ExerciseEntity get() {
        val parent = ExerciseInputConfig.decode(base.inputConfig, base.trackingType, base.id)
        val config = if (variant == null) parent else ExerciseInputConfig.decode(
            variant.inputConfig, variant.trackingType.ifBlank { base.trackingType }, base.id,
            parent.manyTuts, parent.measurementOrder,
        )
        return base.copy(id = id, canonicalName = name,
            trackingType = variant?.trackingType?.ifBlank { base.trackingType } ?: base.trackingType,
            inputConfig = config.encode())
    }
}

@Composable
fun ExerciseEditor(vm: EditorViewModel, onBack: () -> Unit = {}) {
    val exercises by vm.exercises.collectAsStateWithLifecycle()
    val muscles by vm.muscles.collectAsStateWithLifecycle()
    val mappings by vm.mappings.collectAsStateWithLifecycle()
    val variantMappings by vm.variationMappings.collectAsStateWithLifecycle()
    val aliases by vm.aliases.collectAsStateWithLifecycle()
    val variants by vm.variations.collectAsStateWithLifecycle()
    val bands by vm.bands.collectAsStateWithLifecycle()
    val videos by vm.referenceVideos.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reducedMotion = LocalVibeReducedMotion.current
    var query by rememberSaveable { mutableStateOf("") }
    var exerciseType by rememberSaveable { mutableStateOf("STRENGTH") }
    var menu by remember { mutableStateOf<ExerciseItem?>(null) }
    var editing by remember { mutableStateOf<ExerciseItem?>(null) }
    var section by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var configuringBands by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var newVariantConfig by remember { mutableStateOf("") }
    var newVariantTracking by remember { mutableStateOf("") }
    var newVariantRoles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var createVariant by remember { mutableStateOf<((ExerciseEntity, Map<String, String>) -> Unit)?>(null) }
    var pickerTarget by remember { mutableStateOf<ExerciseItem?>(null) }
    var playing by remember { mutableStateOf<ExerciseReferenceVideoEntity?>(null) }
    var deleting by remember { mutableStateOf<ExerciseReferenceVideoEntity?>(null) }
    val requestedPage = remember { mutableStateMapOf<String, String>() }
    fun failed(message: String) { busy = false; error = message }
    fun closed() { busy = false; editing = null; section = null; createVariant = null; error = null }
    fun open(item: ExerciseItem, choice: String) {
        error = null
        if (choice == "new") {
            busy = true
            vm.prepareExerciseVariant(item.base.id, item.variant?.id, onReady = { defaults ->
                busy = false
                menu = null
                editing = item
                section = choice
                newVariantConfig = defaults.inputConfig
                newVariantTracking = defaults.trackingType
                newVariantRoles = defaults.muscleRoles
                createVariant = { exercise, roles ->
                    busy = true
                    vm.createExerciseVariant(
                        item.base.id, item.variant?.id, exercise.canonicalName,
                        defaults.copy(inputConfig = exercise.inputConfig, muscleRoles = roles),
                        onCreated = { id -> requestedPage[item.base.id] = id; closed() },
                        onFailure = ::failed,
                    )
                }
            }, onFailure = ::failed)
        } else {
            menu = null
            editing = item
            section = choice
        }
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = pickerTarget
        pickerTarget = null
        if (uri != null && target != null) {
            busy = true
            vm.attachReferenceVideo(
                context, target.base.id, uri, uri.lastPathSegment?.substringAfterLast('/').orEmpty(),
                variationId = target.variant?.id, onSaved = { busy = false }, onFailure = ::failed,
            )
        }
    }
    val matchingMuscles = muscles.filter { it.displayName.contains(query, true) }.map { it.id }.toSet()
    val matchingIds = mappings.filter { it.muscleId in matchingMuscles }.map { it.exerciseId }.toSet() +
        aliases.filter { it.alias.contains(query, true) }.map { it.exerciseId } +
        variants.filter { variant ->
            variant.name.contains(query, true) || variantMappings.any {
                it.variationId == variant.id && it.muscleId in matchingMuscles
            }
        }.map { it.exerciseId }
    val visible = exercises.filter {
        !it.isArchived && (it.tag == exerciseType || it.tag == "BOTH") &&
            (it.canonicalName.contains(query, true) || it.id in matchingIds)
    }
    RegisterAppFabAction(
        owner = Destination.EXERCISES, destination = AppFabDestination.NewExercise,
        visible = menu == null && editing == null && !creating && !configuringBands &&
            pickerTarget == null && playing == null && deleting == null && !busy,
    ) { error = null; creating = true }
    val palette = LocalVibePalette.current
    HabitGlassDialogHost(constrainToContent = true) { backdrop, _ ->
        ScreenList(modifier = backdrop) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    VibeBackChevron(onBack)
                    Text("Exercises", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    ModeSelector(
                        mode = if (exerciseType == "STRENGTH") TrainingMode.STRENGTH else TrainingMode.STRETCHING,
                        onModeChange = { exerciseType = if (it == TrainingMode.STRENGTH) "STRENGTH" else "STRETCHING" },
                        modifier = Modifier.width(112.dp).height(48.dp),
                        iconOnly = true,
                    )
                }
                EditField("Name, alias or muscle", query) { query = it }
                VibeActionButton("Configure bands", { configuringBands = true },
                    Modifier.fillMaxWidth(), ActionImportance.SECONDARY)
            }
            itemsIndexed(visible, key = { _, it -> it.id }) { rowIndex, base ->
                val siblings = variants.filter { it.exerciseId == base.id }.sortedBy { it.progressionRank }
                val baseRoles = mappings.filter { it.exerciseId == base.id }.associate { it.muscleId to it.role }
                val pages = listOf(ExerciseItem(base, null, baseRoles)) + siblings.map { variant ->
                    ExerciseItem(base, variant, variantMappings.filter { it.variationId == variant.id }
                        .associate { it.muscleId to it.role })
                }
                val ids = pages.map { it.id }
                val pager = rememberPagerState(pageCount = { pages.size })
                var currentId by rememberSaveable(base.id) { mutableStateOf(base.id) }
                LaunchedEffect(ids, requestedPage[base.id]) {
                    val requested = requestedPage[base.id]
                    val index = ids.indexOf(requested ?: currentId)
                    if (index >= 0) {
                        pager.scrollToPage(index)
                        if (requested != null) requestedPage.remove(base.id)
                    } else if (requested == null) pager.scrollToPage(0)
                }
                LaunchedEffect(pager.currentPage) { currentId = pages[pager.currentPage].id }
                val displayedItem = pages[pager.currentPage.coerceIn(0, pages.lastIndex)]
                Column(Modifier.fillMaxWidth().habitGlow(palette.accent, palette.isDark)
                    .testTag("Exercise tile ${base.id}")
                    .pointerInput(displayedItem) {
                        detectTapGestures(onDoubleTap = { menu = displayedItem; error = null })
                    }) {
                    if (rowIndex > 0) HorizontalDivider(color = palette.border.copy(alpha = 0.3f))
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        HorizontalPager(pager, Modifier.fillMaxWidth(), key = { ids[it] }) { index ->
                            val item = pages[index]
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.name, Modifier.weight(1f)
                                        .semantics { onClick("Settings for ${item.name}") { menu = item; error = null; true } }
                                        .onKeyEvent {
                                            if (it.type == KeyEventType.KeyUp && it.key in listOf(Key.Enter, Key.Spacebar)) {
                                                menu = item; error = null; true
                                            } else false
                                        }.focusable(), style = MaterialTheme.typography.titleMedium)
                                }
                                listOf("PRIMARY" to "Primary", "SECONDARY" to "Secondary").forEach { (role, label) ->
                                    val names = item.muscles.filterValues { it == role }.keys
                                        .mapNotNull { id -> muscles.find { it.id == id }?.displayName }
                                    if (names.isNotEmpty()) Text("$label  ${names.joinToString()}",
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                if (item.variant == null && base.isCustom) Row {
                                    TextButton(onClick = { open(item, "maintenance") }) { Text("Exercise details") }
                                    TextButton(onClick = {
                                        busy = true
                                        vm.saveExercise(base.copy(isArchived = true),
                                            aliases.filter { it.exerciseId == base.id }.map { it.alias }, baseRoles,
                                            onSaved = { busy = false }, onFailure = ::failed)
                                    }, enabled = !busy) { Text("Archive") }
                                }
                                val custom = siblings.filter { !it.isSeeded }
                                val order = custom.indexOfFirst { it.id == item.variant?.id }
                                if (order >= 0 && custom.size > 1) Row {
                                    TextButton(onClick = { vm.moveVariation(item.id, -1) }, enabled = order > 0) {
                                        Text("Move earlier")
                                    }
                                    TextButton(onClick = { vm.moveVariation(item.id, 1) }, enabled = order < custom.lastIndex) {
                                        Text("Move later")
                                    }
                                }
                            }
                        }
                        if (pages.size > 1) {
                            val scope = rememberCoroutineScope()
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = { scope.launch {
                                    if (reducedMotion) pager.scrollToPage(pager.currentPage - 1)
                                    else pager.animateScrollToPage(pager.currentPage - 1)
                                } },
                                    enabled = pager.currentPage > 0,
                                    modifier = Modifier.semantics { contentDescription = "Previous variant for ${base.canonicalName}" }) {
                                    Text("Previous")
                                }
                                Text("${pager.currentPage + 1} / ${pages.size}", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { scope.launch {
                                    if (reducedMotion) pager.scrollToPage(pager.currentPage + 1)
                                    else pager.animateScrollToPage(pager.currentPage + 1)
                                } },
                                    enabled = pager.currentPage < pages.lastIndex,
                                    modifier = Modifier.semantics { contentDescription = "Next variant for ${base.canonicalName}" }) {
                                    Text("Next")
                                }
                            }
                        }
                    }
                }
            }
            if (error != null && editing == null && menu == null && !creating) item {
                Text(checkNotNull(error), color = MaterialTheme.colorScheme.error)
            }
        }
        menu?.let { item ->
            HabitGlassDialog(title = item.name, onDismiss = { if (!busy) menu = null }) {
                listOf("new" to "New variant", "name" to "Modify name", "resistance" to "Modify resistance",
                    "measurements" to "Modify measurements", "muscles" to "Change muscle groups",
                    "video" to "Reference video").forEach { (choice, label) ->
                    TextButton(onClick = { open(item, choice) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(label)
                    }
                }
                if (busy) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        editing?.let { item ->
            when (section) {
                "name" -> ExerciseNameDialog(item.name, busy, error, ::closed) { name ->
                    busy = true
                    vm.renameExerciseItem(item.base.id, item.variant?.id, name, ::closed, ::failed)
                }
                "resistance", "measurements" -> ExerciseSettingsDialog(
                    item.effective, ::closed, parentExerciseId = item.base.id, section = section, busy = busy, error = error,
                ) {
                    busy = true
                    vm.saveExerciseItemConfig(item.base.id, item.variant?.id, it.inputConfig, ::closed, ::failed)
                }
                "muscles" -> MuscleGroupsDialog(item.name, muscles, item.muscles, busy, error, ::closed) { roles ->
                    busy = true
                    vm.saveExerciseItemMuscles(item.base.id, item.variant?.id, roles, ::closed, ::failed)
                }
                "new" -> ExerciseCreationDialog(
                    item.effective.copy(inputConfig = newVariantConfig, trackingType = newVariantTracking), muscles, newVariantRoles,
                    busy, error, ::closed, variant = true,
                ) { exercise, _, roles ->
                    createVariant?.invoke(exercise, roles)
                }
                "maintenance" -> ExerciseDetailsDialog(item.base,
                    aliases.filter { it.exerciseId == item.base.id }.map { it.alias }, item.muscles,
                    busy, error, ::closed) { exercise, names, roles ->
                    busy = true
                    vm.saveExercise(exercise, names, roles, ::closed, ::failed)
                }
                "video" -> HabitGlassDialog(title = "Reference video: ${item.name}", onDismiss = { if (!busy) closed() }) {
                    videos.filter { it.exerciseId == item.base.id && it.variationId == item.variant?.id }.forEach { video ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(video.displayName, Modifier.weight(1f))
                            IconButton(onClick = { playing = video }) { Icon(Icons.Outlined.PlayArrow, "Play ${video.displayName}") }
                            IconButton(onClick = { deleting = video }, enabled = !busy) {
                                Icon(Icons.Outlined.Delete, "Delete ${video.displayName}")
                            }
                        }
                    }
                    TextButton(onClick = {
                        pickerTarget = item
                        videoPicker.launch(arrayOf("video/*"))
                    }, enabled = !busy) { Text("Attach reference video") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (creating) ExerciseCreationDialog(
            ExerciseEntity("new-exercise", "", exerciseType, "WEIGHT_REPS", null, null, "custom", true),
            muscles, emptyMap(), busy, error, { creating = false },
        ) { exercise, names, roles ->
            busy = true
            vm.saveExercise(exercise.copy(id = newId()), names, roles,
                onSaved = { busy = false; creating = false }, onFailure = ::failed)
        }
        if (configuringBands) BandSettingsDialog(bands, { configuringBands = false }) { band, saved, failed ->
            vm.saveBand(band, saved, failed)
        }
        playing?.let { VideoPlaybackDialog(context, it) { playing = null } }
        deleting?.let { video ->
            HabitGlassDialog(title = "Delete reference video?", onDismiss = { if (!busy) deleting = null },
                actions = {
                    TextButton(onClick = { deleting = null }, enabled = !busy) { Text("Cancel") }
                    TextButton(onClick = {
                        busy = true
                        vm.deleteReferenceVideo(context, video, onSaved = { busy = false; deleting = null }, onFailure = ::failed)
                    }, enabled = !busy) { Text("Delete") }
                }) { Text(video.displayName); error?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
        }
    }
}

@Composable
private fun ExerciseNameDialog(initial: String, busy: Boolean, error: String?, dismiss: () -> Unit, save: (String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial) }
    HabitGlassDialog(title = "Modify name", onDismiss = { if (!busy) dismiss() }, actions = {
        TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") }
        TextButton(onClick = { save(name) }, enabled = !busy && name.isNotBlank()) { Text("Save") }
    }) {
        TextField(name, { name = it }, enabled = !busy, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun MuscleGroupsDialog(
    name: String, muscles: List<MuscleEntity>, initial: Map<String, String>, busy: Boolean,
    error: String?, dismiss: () -> Unit, save: (Map<String, String>) -> Unit,
) {
    var roles by remember(name) { mutableStateOf(initial) }
    HabitGlassDialog(title = "Change muscle groups: $name", onDismiss = { if (!busy) dismiss() }, actions = {
        TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") }
        TextButton(onClick = { save(roles) }, enabled = !busy) { Text("Save") }
    }) {
        MuscleChoices(muscles, roles, !busy) { roles = it }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun MuscleChoices(muscles: List<MuscleEntity>, roles: Map<String, String>, enabled: Boolean, changed: (Map<String, String>) -> Unit) {
    Text("Tap muscle to cycle: none → primary → secondary")
    muscles.forEach { muscle ->
        TextButton(onClick = {
            changed(when (roles[muscle.id]) {
                null -> roles + (muscle.id to "PRIMARY")
                "PRIMARY" -> roles + (muscle.id to "SECONDARY")
                else -> roles - muscle.id
            })
        }, enabled = enabled) { Text("${muscle.displayName}: ${roles[muscle.id] ?: "none"}") }
    }
}

@Composable
private fun ExerciseCreationDialog(
    initial: ExerciseEntity, muscles: List<MuscleEntity>, initialRoles: Map<String, String>,
    busy: Boolean, error: String?, dismiss: () -> Unit, variant: Boolean = false,
    save: (ExerciseEntity, List<String>, Map<String, String>) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf("") }
    var aliases by remember(initial.id) { mutableStateOf("") }
    var tag by remember(initial.id) { mutableStateOf(initial.tag) }
    var tracking by remember(initial.id) { mutableStateOf(initial.trackingType) }
    var roles by remember(initial.id) { mutableStateOf(initialRoles) }
    val pounds = LocalContext.current.getSharedPreferences("settings", 0).getBoolean("lb", false)
    var config by remember(initial.id) { mutableStateOf(ExerciseInputConfig.decode(initial.inputConfig, initial.trackingType)) }
    HabitGlassDialog(title = if (variant) "New variant" else "New exercise", onDismiss = { if (!busy) dismiss() }, actions = {
        TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") }
        TextButton(onClick = {
            save(initial.copy(canonicalName = name, tag = tag, trackingType = tracking, inputConfig = config.encode()),
                aliases.split(','), roles)
        }, enabled = !busy && name.isNotBlank() && config.loggingMetricsError() == null &&
            (variant || roles.isNotEmpty())) { Text("Create") }
    }) {
        if (variant) Text("Defaults from ${initial.canonicalName}", style = MaterialTheme.typography.bodySmall)
        TextField(name, { name = it }, enabled = !busy,
            label = { Text(if (variant) "Variant name" else "Name") }, modifier = Modifier.fillMaxWidth())
        Text("Resistance", style = MaterialTheme.typography.titleSmall)
        ResistanceMetric.entries.forEach { metric ->
            MetricChoice(metric.label, config.loggingResistance() == metric, !busy) {
                config = config.withResistance(metric, config.weightUnit ?: if (pounds) "lb" else "kg")
            }
        }
        Text("Measurement", style = MaterialTheme.typography.titleSmall)
        MeasurementMetric.entries.forEach { metric ->
            MetricChoice(metric.label, config.loggingMeasurement() == metric, !busy) { config = config.withMeasurement(metric) }
        }
        MuscleChoices(muscles, roles, !busy) { roles = it }
        if (!variant) {
            TextField(aliases, { aliases = it }, enabled = !busy,
                label = { Text("Aliases separated by commas") }, modifier = Modifier.fillMaxWidth())
            listOf("STRENGTH", "STRETCHING", "BOTH").forEach { option ->
                MetricChoice(option, tag == option, !busy) { tag = option }
            }
            TrackingType.entries.forEach { option ->
                MetricChoice(option.name, tracking == option.name, !busy) {
                    tracking = option.name
                    config = ExerciseInputConfig.defaults(tracking)
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ExerciseDetailsDialog(
    initial: ExerciseEntity, aliases: List<String>, initialRoles: Map<String, String>,
    busy: Boolean, error: String?, dismiss: () -> Unit,
    save: (ExerciseEntity, List<String>, Map<String, String>) -> Unit,
) {
    var names by remember(initial.id) { mutableStateOf(aliases.joinToString()) }
    var tag by remember(initial.id) { mutableStateOf(initial.tag) }
    var tracking by remember(initial.id) { mutableStateOf(initial.trackingType) }
    HabitGlassDialog(title = "Exercise details",
        onDismiss = { if (!busy) dismiss() }, actions = {
            TextButton(onClick = dismiss, enabled = !busy) { Text("Cancel") }
            TextButton(onClick = { save(initial.copy(tag = tag, trackingType = tracking),
                names.split(','), initialRoles) }, enabled = !busy && initial.canonicalName.isNotBlank()) { Text("Save") }
        }) {
        TextField(names, { names = it }, enabled = !busy, label = { Text("Aliases separated by commas") }, modifier = Modifier.fillMaxWidth())
        listOf("STRENGTH", "STRETCHING", "BOTH").forEach { option ->
            MetricChoice(option, tag == option, !busy) { tag = option }
        }
        TrackingType.entries.forEach { option ->
            MetricChoice(option.name, tracking == option.name, !busy) { tracking = option.name }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun VideoPlaybackDialog(context: Context, video: ExerciseReferenceVideoEntity, dismiss: () -> Unit) {
    var error by remember(video.id) { mutableStateOf<String?>(null) }
    HabitGlassDialog(title = video.displayName, onDismiss = dismiss) {
        AndroidView(factory = { viewContext ->
            VideoView(viewContext).apply {
                setVideoPath(File(File(context.filesDir, "exercise-reference-videos"), video.fileName).path)
                setOnPreparedListener { it.isLooping = true; start() }
                setOnErrorListener { _, _, _ -> error = "Unable to play this reference video."; true }
            }
        }, modifier = Modifier.fillMaxWidth().height(240.dp), onRelease = { it.stopPlayback() })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun ExerciseSettingsDialog(
    exercise: ExerciseEntity, onDismiss: () -> Unit, parentExerciseId: String = exercise.id,
    defaultManyTuts: Boolean = parentExerciseId == "core:handstand",
    defaultMeasurementOrder: List<ExerciseMeasurement> = emptyList(),
    section: String? = null, busy: Boolean = false, error: String? = null, onSave: (ExerciseEntity) -> Unit,
) {
    val pounds = LocalContext.current.getSharedPreferences("settings", 0).getBoolean("lb", false)
    val config = remember(exercise.id) { ExerciseInputConfig.decode(
        exercise.inputConfig, exercise.trackingType, parentExerciseId, defaultManyTuts, defaultMeasurementOrder,
    ) }
    var resistance by remember(exercise.id) { mutableStateOf(config.loggingResistance()) }
    var measurement by remember(exercise.id) { mutableStateOf(config.loggingMeasurement()) }
    HabitGlassDialog(title = when (section) {
        "resistance" -> "Modify resistance"
        "measurements" -> "Modify measurements"
        else -> "Exercise measurements"
    }, onDismiss = { if (!busy) onDismiss() }, actions = {
        TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        TextButton(enabled = !busy && (section == "resistance" && resistance != null ||
            section == "measurements" && measurement != null ||
            section == null && resistance != null && measurement != null), onClick = {
            var next = config
            if (section != "measurements") next = next.withResistance(checkNotNull(resistance), config.weightUnit ?: if (pounds) "lb" else "kg")
            if (section != "resistance") next = next.withMeasurement(checkNotNull(measurement))
            onSave(exercise.copy(inputConfig = next.encode()))
        }) { Text("Save") }
    }) {
        Text(exercise.canonicalName, style = MaterialTheme.typography.titleMedium)
        if (section != "measurements") {
            Text("Resistance", style = MaterialTheme.typography.titleSmall)
            ResistanceMetric.entries.forEach { option -> MetricChoice(option.label, resistance == option, !busy) { resistance = option } }
        }
        if (section != "resistance") {
            Text("Measurement", style = MaterialTheme.typography.titleSmall)
            MeasurementMetric.entries.forEach { option -> MetricChoice(option.label, measurement == option, !busy) { measurement = option } }
        }
        Text("Recorded values and units are retained.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun MetricChoice(label: String, checked: Boolean, enabled: Boolean = true, changed: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(checked, changed, enabled = enabled,
            modifier = Modifier.semantics { contentDescription = label })
        Text(label)
    }
}

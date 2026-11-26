package com.petermathie.vibecheck.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.content.SharedPreferences
import android.os.Build
import com.petermathie.vibecheck.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.BodyMeasurementEntity
import com.petermathie.vibecheck.domain.body.BodyweightUnit
import com.petermathie.vibecheck.domain.body.bodyweightInputError
import com.petermathie.vibecheck.domain.body.convertBodyweight
import com.petermathie.vibecheck.domain.tracker.habitMonthCells
import com.petermathie.vibecheck.ui.components.DateValueReadout
import com.petermathie.vibecheck.ui.components.VibeGraph
import com.petermathie.vibecheck.ui.components.VibeGraphState
import com.petermathie.vibecheck.ui.components.progressDateRange
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.habitHeatmapColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.io.File
import java.io.InputStream
import com.petermathie.vibecheck.data.BackupPreferences
import com.petermathie.vibecheck.data.DemoRemovalSummary

@Composable
fun SettingsScreen(
    vm: EditorViewModel,
    onStyle: () -> Unit,
    onRemoveDemo: ((Result<DemoRemovalSummary>) -> Unit) -> Unit,
) {
    val context=LocalContext.current;val prefs=context.getSharedPreferences("settings",0);val scope=rememberCoroutineScope()
    var message by remember { mutableStateOf("") };var last by remember { mutableStateOf(prefs.getLong("backup",0)) }
    var notices by remember { mutableStateOf<String?>(null) }
    var reducedMotionInfo by remember { mutableStateOf(false) }
    var preciseTimerInfo by remember { mutableStateOf(false) }
    var confirmRemoveDemo by remember { mutableStateOf(false) }
    var lb by remember { mutableStateOf(prefs.getBoolean("lb",false)) };var female by remember { mutableStateOf(prefs.getBoolean("female",false)) }
    var auto by remember { mutableStateOf(prefs.getBoolean("autoRest",false)) };var haptic by remember { mutableStateOf(prefs.getBoolean("haptic",true)) };var reduced by remember { mutableStateOf(prefs.getBoolean("reducedMotion",false)) }
    var scrollToNext by remember { mutableStateOf(prefs.getBoolean(SCROLL_TO_NEXT_EMPTY_SET, false)) }
    var notificationsEnabled by remember { mutableStateOf(timerNotificationsEnabled(context)) }
    var preciseTimersEnabled by remember { mutableStateOf(arePreciseTimersEnabled(context)) }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri!=null)scope.launch { try { val text=BackupPreferences.attach(vm.exportJson(),prefs);withContext(Dispatchers.IO){requireNotNull(context.contentResolver.openOutputStream(uri)){"Cannot open backup destination"}.bufferedWriter().use{it.write(text)}};last=System.currentTimeMillis();prefs.edit().putLong("backup",last).apply();message="Backup saved" }catch(e:Exception){message=e.message.orEmpty()} } }
    var pendingImport by remember { mutableStateOf<String?>(null) }
    val restore=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null)scope.launch { try { pendingImport=withContext(Dispatchers.IO){context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}} }catch(e:Exception){message=e.message.orEmpty()} } }
    val csv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if(uri!=null)scope.launch { try {val text=vm.exportCsv();withContext(Dispatchers.IO){context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use{it.write(text)}};message="CSV saved"}catch(e:Exception){message=e.message.orEmpty()} } }
    val notify=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){
        notificationsEnabled = timerNotificationsEnabled(context)
        message=if(it)"Timer notifications enabled" else "Notifications disabled"
    }
    val notificationSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        notificationsEnabled = timerNotificationsEnabled(context)
    }
    val preciseTimerSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        preciseTimersEnabled = arePreciseTimersEnabled(context)
    }
    ScreenList {
        item {
            Text("Settings and data",style=MaterialTheme.typography.headlineSmall)
            VibeActionButton("Colour palette", onStyle, Modifier.fillMaxWidth(), ActionImportance.SECONDARY)
            SettingToggle("Pounds (lb)",lb){lb=it;prefs.edit().putBoolean("lb",it).apply()}
            SettingToggle("Female anatomy",female){female=it;prefs.edit().putBoolean("female",it).apply()}
            SettingToggle("Start rest automatically",auto){auto=it;prefs.edit().putBoolean("autoRest",it).apply()}
            SettingToggle("Haptics",haptic){haptic=it;prefs.edit().putBoolean("haptic",it).apply()}
            SettingToggle("Scroll to next empty set", scrollToNext) {
                scrollToNext = it
                prefs.edit().putBoolean(SCROLL_TO_NEXT_EMPTY_SET, it).apply()
            }
            SettingToggle(
                "Reduced motion",
                reduced,
                onInfo = { reducedMotionInfo = true },
            ) { reduced=it;prefs.edit().putBoolean("reducedMotion",it).apply() }
            SettingToggle("Timer notifications", notificationsEnabled) { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    notificationSettings.launch(
                        Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }
            }
            if(Build.VERSION.SDK_INT>=31) {
                SettingToggle(
                    "Precise background timers",
                    preciseTimersEnabled,
                    onInfo = { preciseTimerInfo = true },
                ) {
                    preciseTimerSettings.launch(
                        Intent(
                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
            }
            VibeActionButton("Cancel rest timer", {RestTimer.cancel(context);message="Timer cancelled"}, Modifier.fillMaxWidth(), ActionImportance.SECONDARY)
            VibeActionButton("JSON backup", {export.launch("vibe-check-backup.json")}, Modifier.fillMaxWidth(), ActionImportance.SECONDARY)
            VibeActionButton("Restore / import structured JSON", {restore.launch(arrayOf("application/json","text/plain"))}, Modifier.fillMaxWidth(), ActionImportance.SECONDARY)
            VibeActionButton("CSV export", {csv.launch("vibe-check-workouts.csv")}, Modifier.fillMaxWidth(), ActionImportance.SECONDARY)
            Text("Last backup: ${if(last==0L)"Never" else Instant.ofEpochMilli(last)}")
            VibeActionButton("Remove demo data", { confirmRemoveDemo = true }, Modifier.fillMaxWidth(), ActionImportance.SECONDARY)
            VibeActionButton("Open-source asset notices", {notices=context.assets.open("THIRD_PARTY_NOTICES.md").bufferedReader().use { it.readText() }}, Modifier.fillMaxWidth(), ActionImportance.SECONDARY)
            Text("Vibe Check ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
            Text(message)
        }
    }
    notices?.let { text -> OpenSourceNoticesDialog(text) { notices = null } }
    if (reducedMotionInfo) AlertDialog(
        onDismissRequest = { reducedMotionInfo = false },
        title = { Text("Reduced motion") },
        text = { Text("Reduced motion disables touch ripples and app-owned animated transitions.") },
        confirmButton = { TextButton(onClick = { reducedMotionInfo = false }) { Text("Close") } },
    )
    if (preciseTimerInfo) AlertDialog(
        onDismissRequest = { preciseTimerInfo = false },
        title = { Text("Precise background timers") },
        text = { Text("This permission lets Android deliver rest-timer alerts at the requested time. Without it, background alerts may be delayed.") },
        confirmButton = { TextButton(onClick = { preciseTimerInfo = false }) { Text("Close") } },
    )
    if (confirmRemoveDemo) AlertDialog(
        onDismissRequest = { confirmRemoveDemo = false },
        title = { Text("Remove all demo personal data?") },
        text = {
            Text("This removes demo workouts, programmes, habits, body history and generated progress photos. Your exercise catalogue and your own records stay intact.")
        },
        confirmButton = {
            TextButton(onClick = {
                confirmRemoveDemo = false
                onRemoveDemo { result ->
                    message = result.fold(
                        onSuccess = { summary -> demoRemovalMessage(summary) },
                        onFailure = { error -> "Demo data was not fully removed: ${error.message.orEmpty()}" },
                    )
                }
            }) { Text("Remove demo data") }
        },
        dismissButton = { TextButton(onClick = { confirmRemoveDemo = false }) { Text("Cancel") } },
    )
    if(pendingImport!=null)AlertDialog(onDismissRequest={pendingImport=null},title={Text("Import records?")},text={Text("Matching record IDs will be updated. Other records are retained. Make a backup first if you want to keep the previous values.")},confirmButton={TextButton(onClick={val text=pendingImport!!;pendingImport=null;scope.launch{try{val restored=BackupPreferences.validate(text);vm.importJson(text);BackupPreferences.restore(restored,prefs);lb=prefs.getBoolean("lb",false);female=prefs.getBoolean("female",false);auto=prefs.getBoolean("autoRest",false);haptic=prefs.getBoolean("haptic",true);reduced=prefs.getBoolean("reducedMotion",false);message="Import complete. Restored colours are active."}catch(e:Exception){message="Import failed: ${e.message}"}}}){Text("Import")}},dismissButton={TextButton(onClick={pendingImport=null}){Text("Cancel")}})
}

private fun demoRemovalMessage(summary: DemoRemovalSummary): String =
    "Removed demo personal data: ${summary.workouts} workouts, ${summary.programmes} programmes, " +
        "${summary.trackers} habits, ${summary.measurements} body entries and ${summary.photos} photos."

private fun timerNotificationsEnabled(context: android.content.Context): Boolean =
    (Build.VERSION.SDK_INT < 24 || context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()) &&
        (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

private fun arePreciseTimersEnabled(context: android.content.Context): Boolean =
    Build.VERSION.SDK_INT < 31 ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

private sealed interface NoticeBlock {
    data class Heading(val level: Int, val text: String) : NoticeBlock
    data class Paragraph(val text: String) : NoticeBlock
    data class Bullet(val text: String) : NoticeBlock
    data class Code(val text: String) : NoticeBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : NoticeBlock
    data object Divider : NoticeBlock
}

private fun parseNotices(markdown: String): List<NoticeBlock> {
    val lines = markdown.lines()
    val blocks = mutableListOf<NoticeBlock>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index].trim()
        when {
            line.isBlank() -> index++
            line.startsWith("```") -> {
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trim().startsWith("```")) code += lines[index++]
                index++
                blocks += NoticeBlock.Code(code.joinToString("\n"))
            }
            line == "---" -> {
                blocks += NoticeBlock.Divider
                index++
            }
            line.startsWith("#") -> {
                val level = line.takeWhile { it == '#' }.length
                blocks += NoticeBlock.Heading(level, line.drop(level).trim())
                index++
            }
            line.startsWith("- ") -> {
                blocks += NoticeBlock.Bullet(line.removePrefix("- ").trim())
                index++
            }
            line.startsWith("|") && index + 1 < lines.size && lines[index + 1].contains("---") -> {
                fun cells(value: String) = value.trim().trim('|').split('|').map(String::trim)
                val headers = cells(line)
                index += 2
                val rows = mutableListOf<List<String>>()
                while (index < lines.size && lines[index].trim().startsWith("|")) rows += cells(lines[index++])
                blocks += NoticeBlock.Table(headers, rows)
            }
            else -> {
                val paragraph = mutableListOf(line)
                index++
                while (index < lines.size) {
                    val next = lines[index].trim()
                    if (next.isBlank() || next.startsWith("#") || next.startsWith("- ") ||
                        next.startsWith("```") || next.startsWith("|") || next == "---"
                    ) break
                    paragraph += next
                    index++
                }
                blocks += NoticeBlock.Paragraph(paragraph.joinToString(" "))
            }
        }
    }
    return blocks
}

private fun renderedMarkdownText(markdown: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < markdown.length) {
        when {
            markdown.startsWith("**", index) -> {
                val end = markdown.indexOf("**", index + 2)
                if (end >= 0) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(markdown.substring(index + 2, end))
                    pop()
                    index = end + 2
                } else append(markdown[index++])
            }
            markdown[index] == '`' -> {
                val end = markdown.indexOf('`', index + 1)
                if (end >= 0) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                    append(markdown.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else append(markdown[index++])
            }
            else -> append(markdown[index++])
        }
    }
}

@Composable
private fun OpenSourceNoticesDialog(markdown: String, onDismiss: () -> Unit) {
    val blocks = remember(markdown) { parseNotices(markdown) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open-source assets") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(blocks) { block ->
                    when (block) {
                        is NoticeBlock.Heading -> Text(
                            renderedMarkdownText(block.text),
                            style = if (block.level == 1) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        )
                        is NoticeBlock.Paragraph -> Text(renderedMarkdownText(block.text))
                        is NoticeBlock.Bullet -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•")
                            Text(renderedMarkdownText(block.text))
                        }
                        is NoticeBlock.Code -> Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(block.text, Modifier.padding(12.dp), fontFamily = FontFamily.Monospace)
                        }
                        is NoticeBlock.Table -> Column(
                            Modifier.horizontalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            NoticeTableRow(block.headers, header = true)
                            block.rows.forEach { NoticeTableRow(it, header = false) }
                        }
                        NoticeBlock.Divider -> HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun NoticeTableRow(cells: List<String>, header: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEach { cell ->
            Text(
                renderedMarkdownText(cell),
                Modifier.width(180.dp),
                fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SettingToggle(
    label: String,
    value: Boolean,
    onInfo: (() -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, Modifier.weight(1f))
        onInfo?.let {
            IconButton(onClick = it) {
                Icon(Icons.Outlined.Info, contentDescription = "About $label")
            }
        }
        Switch(value, onChange)
    }
}

@Composable
fun MeasurementsScreen(
    vm: EditorViewModel,
    preferences: SharedPreferences = LocalContext.current.getSharedPreferences("settings", 0),
) {
    val rows by vm.bodyMeasurements.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val palette = LocalVibePalette.current
    val shades = palette.habitHeatmapColors()
    val today = LocalDate.now()
    val zone = ZoneId.systemDefault()
    var selectedDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var displayedMonth by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var importPhotoDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var rangeMonths by rememberSaveable { mutableIntStateOf(1) }
    var customRange by rememberSaveable { mutableStateOf(false) }
    var customStart by rememberSaveable { mutableLongStateOf(today.minusMonths(1).toEpochDay()) }
    var customEnd by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var rangeDialog by remember { mutableStateOf(false) }
    val dateRange = if (customRange) {
        progressDateRange(LocalDate.ofEpochDay(customStart), LocalDate.ofEpochDay(customEnd), zone)
    } else progressDateRange(rangeMonths, today, zone)
    var message by remember { mutableStateOf("") }
    var pounds by remember(preferences) { mutableStateOf(preferences.getBoolean("lb", false)) }
    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "lb" || key == null) pounds = prefs.getBoolean("lb", false)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val displayUnit = if (pounds) BodyweightUnit.POUNDS else BodyweightUnit.KILOGRAMS
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val targetDay = importPhotoDay
        importPhotoDay = null
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(targetDay) { "Selected photo date was lost; choose the date and try again" }
                    copyBodyPhoto(File(context.filesDir, "progress-photos"), LocalDate.ofEpochDay(targetDay), zone) {
                        requireNotNull(context.contentResolver.openInputStream(uri)) { "Could not open selected photo" }
                    }
                }
                message = "Photo added"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                vm.error.value = "Could not add photo: ${e.message}"
            }
        }
    }
    val bodyweights = rows.orEmpty().filter { it.metric.equals("Bodyweight", true) }
        .sortedWith(compareBy<BodyMeasurementEntity> { it.recordedAt }.thenBy { it.id })
    val weightsByDay = remember(bodyweights, zone) {
        bodyweights.groupBy { Instant.ofEpochMilli(it.recordedAt).atZone(zone).toLocalDate().toEpochDay() }
            .mapValues { (_, values) -> values.last() }
    }
    val converted = remember(bodyweights, displayUnit) {
        bodyweights.associate { row ->
            row.id to try {
                Result.success(convertBodyweight(row.value, BodyweightUnit.stored(row.unit), displayUnit))
            } catch (e: IllegalArgumentException) {
                Result.failure(e)
            }
        }
    }
    val selectedMeasurement = weightsByDay[selectedDay]
    val selectedValue = selectedMeasurement?.let { converted.getValue(it.id) }
    val trendRows = bodyweights.filter { it.recordedAt in dateRange }
    val trendError = trendRows.firstNotNullOfOrNull { converted.getValue(it.id).exceptionOrNull()?.message }
    val month = YearMonth.parse(displayedMonth)
    val cells = remember(month) { habitMonthCells(month) }
    fun dayValue(epochDay: Long): String {
        val row = weightsByDay[epochDay] ?: return "No entry"
        val result = converted.getValue(row.id)
        return result.getOrNull()?.let { "${formatBodyweight(it)} ${displayUnit.symbol}" }
            ?: "${row.value} ${row.unit}: ${result.exceptionOrNull()?.message}"
    }
    RegisterAppFabAction(
        owner = Destination.MEASUREMENTS,
        destination = AppFabDestination.AddBodyPhoto,
        visible = importPhotoDay == null && !rangeDialog,
    ) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        message = ""
        importPhotoDay = selectedDay
        try {
            picker.launch(arrayOf("image/*"))
        } catch (e: android.content.ActivityNotFoundException) {
            importPhotoDay = null
            vm.error.value = "Could not open photo picker: ${e.message}"
        }
    }
    HabitGlassDialogHost(constrainToContent = true) { backdrop, _ ->
        ScreenList(modifier = backdrop.semantics { contentDescription = "Body page" }) {
            item(key = "calendar") {
                Column(
                    Modifier.fillMaxWidth().habitGlow(palette.accent, palette.isDark)
                        .semantics { contentDescription = "Bodyweight calendar" },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HabitHeadingAndEntry(
                        heading = { Text("Bodyweight", style = MaterialTheme.typography.titleLarge) },
                    ) {
                        if (rows == null) {
                            Text("Loading bodyweight...", color = palette.textSecondary)
                        } else key(selectedDay, displayUnit) {
                            val inputDay = LocalDate.ofEpochDay(selectedDay)
                            val loadedText = selectedValue?.getOrNull()?.let(::formatBodyweight)
                                ?: selectedMeasurement?.value?.let(::formatBodyweight).orEmpty()
                            var text by remember { mutableStateOf(loadedText) }
                            var focused by remember { mutableStateOf(false) }
                            LaunchedEffect(loadedText, selectedMeasurement, focused) {
                                if (!focused) text = loadedText
                            }
                            val error = selectedValue?.exceptionOrNull()?.message ?: bodyweightInputError(text)
                            HabitTextField(
                                label = displayUnit.symbol,
                                value = text,
                                onValueChange = { input ->
                                    text = input
                                    if (bodyweightInputError(input) == null &&
                                        (input.isBlank() || selectedValue?.isFailure != true)
                                    ) {
                                        vm.setBodyweight(
                                            inputDay, input.trim().toDoubleOrNull(), displayUnit, zone,
                                        )
                                    }
                                },
                                modifier = Modifier.onFocusChanged { focused = it.isFocused }
                                    .semantics { contentDescription = "Bodyweight for selected date" },
                                numeric = true,
                                error = error,
                                plain = true,
                                textAlign = TextAlign.End,
                            )
                        }
                    }
                    Column(
                        Modifier.pointerInput(month) {
                            var drag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { drag = 0f },
                                onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
                                onDragEnd = {
                                    if (drag != 0f) displayedMonth = month.plusMonths(if (drag < 0f) -1 else 1).toString()
                                }
                            )
                        },
                    ) {
                        CalendarMonthHeader(month, null, null, "Bodyweight") {
                            displayedMonth = month.plusMonths(it.toLong()).toString()
                        }
                        CalendarHeatmapGrid(
                            cells, today, selectedDay,
                            colorForDay = { shades.forLevel(if (weightsByDay.containsKey(it.toEpochDay())) 3 else 0) },
                            descriptionForDay = { "${formatDisplayDate(it)}: ${dayValue(it.toEpochDay())}" },
                            onDayClick = { selectedDay = it.toEpochDay() },
                            allowFutureDates = true,
                        )
                    }
                    DateValueReadout(
                        LocalDate.ofEpochDay(selectedDay), dayValue(selectedDay),
                        modifier = Modifier.semantics { contentDescription = "Bodyweight selected entry" },
                    )
                }
            }
            item(key = "trend") {
                Column(
                    Modifier.fillMaxWidth().habitGlow(palette.accent, palette.isDark),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Bodyweight trend", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        ProgressRangeSelector(
                            rangeMonths, customRange, onCustom = { rangeDialog = true },
                            description = "Bodyweight date range",
                        ) {
                            rangeMonths = it
                            customRange = false
                        }
                    }
                    VibeGraph(
                        values = if (trendError == null) trendRows.map { converted.getValue(it.id).getOrThrow() } else emptyList(),
                        dates = if (trendError == null) trendRows.map { it.recordedAt } else emptyList(),
                        unit = displayUnit.symbol,
                        dateRange = dateRange,
                        readoutBelow = true,
                        edgeToEdge = true,
                        compactLayout = true,
                        plotHeight = 200.dp,
                        valueFormatter = ::formatBodyweight,
                        graphState = when {
                            rows == null -> VibeGraphState.Loading
                            trendError != null -> VibeGraphState.Error(trendError)
                            trendRows.isEmpty() -> VibeGraphState.Empty("No bodyweight entries in this date range.")
                            else -> VibeGraphState.Data
                        },
                        onSelect = { index ->
                            val day = Instant.ofEpochMilli(trendRows[index].recordedAt).atZone(zone).toLocalDate()
                            selectedDay = day.toEpochDay()
                            displayedMonth = YearMonth.from(day).toString()
                        },
                    )
                }
            }
            if (message.isNotBlank()) item { Text(message) }
        }
        if (rangeDialog) ProgressCustomRangeDialog(
            start = if (customRange) LocalDate.ofEpochDay(customStart) else today.minusMonths(rangeMonths.toLong()),
            end = if (customRange) LocalDate.ofEpochDay(customEnd) else today,
            onDismiss = { rangeDialog = false },
        ) { start, end ->
            customStart = start.toEpochDay()
            customEnd = end.toEpochDay()
            customRange = true
            rangeDialog = false
        }
    }
}

@Synchronized
internal fun copyBodyPhoto(
    directory: File,
    day: LocalDate,
    zone: ZoneId,
    openInput: () -> InputStream,
): File {
    check(directory.isDirectory || directory.mkdirs()) { "Could not create photo storage" }
    val pending = File.createTempFile("photo-", ".tmp", directory)
    try {
        openInput().use { input -> pending.outputStream().use { input.copyTo(it) } }
        val start = day.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        var timestamp = start + Math.floorMod(System.currentTimeMillis(), 12 * 60 * 60 * 1000L)
        var destination = File(directory, "$timestamp.jpg")
        while (destination.exists()) destination = File(directory, "${++timestamp}.jpg")
        check(pending.renameTo(destination)) { "Could not finish saving photo" }
        return destination
    } catch (e: Exception) {
        if (pending.exists() && !pending.delete()) e.addSuppressed(IllegalStateException("Could not remove incomplete photo"))
        throw e
    }
}

internal fun formatBodyweight(value: Double): String =
    String.format(java.util.Locale.US, "%.2f", value)

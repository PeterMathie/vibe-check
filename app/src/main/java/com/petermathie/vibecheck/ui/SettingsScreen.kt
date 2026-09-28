package com.petermathie.vibecheck.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.petermathie.vibecheck.BuildConfig
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.data.local.BodyMeasurementEntity
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.File
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
fun MeasurementsScreen(vm:EditorViewModel) {
    val haptics = rememberVibeHaptics()
    val rows by vm.measurements.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()
    var selectedDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var displayedMonth by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var value by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("kg") }
    var note by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    var editing by remember { mutableStateOf<BodyMeasurementEntity?>(null) }
    var message by remember { mutableStateOf("") }
    val prefs = remember(context) { context.getSharedPreferences("body-layout", 0) }
    val directory = File(context.filesDir, "progress-photos")
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    directory.mkdirs()
                    val start = LocalDate.ofEpochDay(selectedDay).atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val timestamp = start + System.currentTimeMillis() % (12 * 60 * 60 * 1000)
                    requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                        File(directory, "$timestamp.jpg").outputStream().use { input.copyTo(it) }
                    }
                }
                refresh++
                message = "Photo added"
            } catch (e: Exception) {
                message = "Could not add photo: ${e.message}"
            }
        }
    }
    val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if(uri!=null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(context.contentResolver.openOutputStream(uri)).use { output ->
                        ZipOutputStream(output).use { zip ->
                            directory.listFiles().orEmpty().forEach { file ->
                                zip.putNextEntry(ZipEntry(file.name))
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                }
                message="Photos exported"
            } catch(e:Exception) { message="Could not export photos: ${e.message}" }
        }
    }
    val bodyweights = rows.filter { it.metric.equals("Bodyweight", true) }.sortedBy { it.recordedAt }
    val weightsByDay = bodyweights.groupBy {
        Instant.ofEpochMilli(it.recordedAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    }.mapValues { (_, values) -> values.maxBy { it.recordedAt } }
    val todayMeasurement = weightsByDay[today.toEpochDay()]
    val selectedMeasurement = weightsByDay[selectedDay]
    LaunchedEffect(todayMeasurement?.id, todayMeasurement?.value, todayMeasurement?.notes) {
        value = todayMeasurement?.value?.toString().orEmpty()
        unit = todayMeasurement?.unit ?: "kg"
        note = todayMeasurement?.notes.orEmpty()
    }
    val photos = remember(refresh, rows.size) { directory.listFiles().orEmpty().sortedByDescending { it.name } }
    val selectedPhotos = photos.filter { file ->
        file.nameWithoutExtension.toLongOrNull()?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay() == selectedDay
        } == true
    }
    val selectedDate = LocalDate.ofEpochDay(selectedDay)
    val month = YearMonth.parse(displayedMonth)
    RegisterAppFabAction(
        owner = Destination.MEASUREMENTS,
        destination = AppFabDestination.AddProgressPhoto,
        visible = editing == null,
    ) {
        picker.launch(arrayOf("image/*"))
    }
    val availableCardKeys = buildList {
        if (bodyweights.isNotEmpty()) add("trend")
        add("calendar")
        add("photos")
    }
    var cardOrder by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(availableCardKeys) {
        val saved = prefs.getString("card-order", "").orEmpty().split('|').filter(String::isNotBlank)
        val reconciled = if (prefs.getBoolean("card-order-customized", false)) saved.filter { it in availableCardKeys } + availableCardKeys.filterNot { it in saved } else availableCardKeys
        cardOrder = reconciled
        prefs.edit().putString("card-order", reconciled.joinToString("|")).apply()
    }
    val reorder = rememberReorderState(cardOrder) { _, from, to ->
        val reordered = cardOrder.toMutableList()
        if (from in reordered.indices && to in reordered.indices) {
            val moved = reordered.removeAt(from)
            reordered.add(to, moved)
            cardOrder = reordered
            prefs.edit().putString("card-order", reordered.joinToString("|")).putBoolean("card-order-customized", true).apply()
        }
    }
    ScreenList {
        item { Text("Bodyweight and photos", style = MaterialTheme.typography.headlineSmall) }
        item {
            VibeCard {
                Text(today.format(DateTimeFormatter.ofPattern("d MMMM yyyy")), style = MaterialTheme.typography.titleLarge)
                EditField("Bodyweight", value) { value = it }
                EditField("Unit", unit) { unit = it }
                EditField("Notes", note) { note = it }
                Button(
                    enabled = value.toDoubleOrNull()?.isFinite() == true,
                    onClick = {
                        val timestamp = today.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        vm.save(todayMeasurement?.copy(recordedAt = timestamp, value = value.toDouble(), unit = unit, notes = note) ?: BodyMeasurementEntity(newId(), timestamp, "Bodyweight", value.toDouble(), unit, note, false))
                        value = ""
                    },
                    shape = MaterialTheme.shapes.medium,
                ) { Text(if (todayMeasurement == null) "Save bodyweight" else "Update bodyweight") }
                todayMeasurement?.let { row ->
                    Row {
                        Text("Bodyweight: ${formatBodyweight(row.value)} ${row.unit}", Modifier.weight(1f))
                        TextButton(onClick = {
                            haptics.perform(VibeHapticEvent.EDIT)
                            editing = row
                        }) { Text("Edit") }
                        TextButton(onClick = { vm.removeMeasurement(row.id) }) { Text("Delete") }
                    }
                }
            }
        }
        reorder.ordered(cardOrder) { it }.forEachIndexed { index, cardKey ->
            when (cardKey) {
                "trend" -> if (bodyweights.isNotEmpty()) item {
                    ReorderItem(reorder, cardKey, index, Modifier.fillMaxWidth()) {
                        VibeCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Bodyweight trend", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                ReorderHandle(reorder, cardKey, "Bodyweight trend")
                            }
                            MiniChart(values = bodyweights.map { if (it.unit.equals("lb", true)) it.value / 2.2046226218 else it.value }, dates = bodyweights.map { it.recordedAt }, unit = "kg") { idx ->
                                val day = Instant.ofEpochMilli(bodyweights[idx].recordedAt).atZone(ZoneId.systemDefault()).toLocalDate()
                                selectedDay = day.toEpochDay(); displayedMonth = YearMonth.from(day).toString()
                            }
                        }
                    }
                }
                "calendar" -> item {
                    ReorderItem(reorder, cardKey, index, Modifier.fillMaxWidth()) {
                        VibeCard(modifier = Modifier.semantics { contentDescription = "Bodyweight calendar card" }) {
                            Row(
                                modifier = Modifier.semantics { contentDescription = "Bodyweight calendar heading" },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Calendar", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                ReorderHandle(reorder, cardKey, "Calendar")
                            }
                            Row(
                                modifier = Modifier.semantics { contentDescription = "Bodyweight calendar month controls" },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                VibeActionButton("‹", { displayedMonth = month.minusMonths(1).toString() }, modifier = Modifier.semantics { contentDescription = "Previous month" }, importance = ActionImportance.COMPACT)
                                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                                VibeActionButton("›", { displayedMonth = month.plusMonths(1).toString() }, modifier = Modifier.semantics { contentDescription = "Next month" }, importance = ActionImportance.COMPACT)
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "Bodyweight calendar weekday labels" },
                            ) {
                                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                                    Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            val leading = month.atDay(1).dayOfWeek.value - 1
                            val cellCount = ((leading + month.lengthOfMonth() + 6) / 7) * 7
                            repeat(cellCount / 7) { week ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .semantics { contentDescription = "Bodyweight calendar week ${week + 1}" },
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    repeat(7) { weekday ->
                                        val dayNumber = week * 7 + weekday - leading + 1
                                        if (dayNumber !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(64.dp)) else {
                                            val date = month.atDay(dayNumber)
                                            val epochDay = date.toEpochDay()
                                            val measurement = weightsByDay[epochDay]
                                            Surface(onClick = { selectedDay = epochDay; displayedMonth = YearMonth.from(date).toString() }, color = if (epochDay == selectedDay) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.weight(1f).height(64.dp)) {
                                                Column(Modifier.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(dayNumber.toString(), style = MaterialTheme.typography.labelSmall)
                                                    measurement?.let { Text(formatBodyweight(it.value), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "photos" -> item {
                    ReorderItem(reorder, cardKey, index, Modifier.fillMaxWidth()) {
                        VibeCard(modifier = Modifier.semantics { contentDescription = "Progress photos card" }) {
                            Row(
                                modifier = Modifier.semantics { contentDescription = "Progress photos heading" },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Photos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                ReorderHandle(reorder, cardKey, "Photos")
                            }
                            Text(
                                selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy")),
                                modifier = Modifier.semantics { contentDescription = "Progress photos date" },
                                style = MaterialTheme.typography.titleLarge,
                            )
                            selectedMeasurement?.let { row -> Text("Bodyweight: ${formatBodyweight(row.value)} ${row.unit}") }
                            Column(Modifier.semantics { contentDescription = "Progress photos actions" }) {
                                TextButton(onClick = { exporter.launch("progress-photos.zip") }) { Text("Export photos separately") }
                            }
                            if (selectedPhotos.isEmpty()) Text("No photo for this day", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            selectedPhotos.forEach { file ->
                                val bitmap = remember(file, refresh) { android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 }) }
                                bitmap?.let {
                                    androidx.compose.foundation.Image(
                                        it.asImageBitmap(),
                                        contentDescription = "Progress photo for selected day",
                                        modifier = Modifier.fillMaxWidth().height(240.dp),
                                    )
                                }
                                TextButton(onClick = { if (file.delete()) { refresh++; message = "Photo removed" } else message = "Could not remove photo" }) { Text("Delete photo") }
                            }
                            Text(message)
                        }
                    }
                }
            }
        }
    }
    editing?.let { row ->
        var amount by remember(row.id){mutableStateOf(row.value.toString())}
        var notes by remember(row.id){mutableStateOf(row.notes)}
        AlertDialog(onDismissRequest={editing=null},title={Text("${row.metric} (${row.unit})")},text={Column{EditField("Value",amount){amount=it};EditField("Notes",notes){notes=it}}},confirmButton={TextButton(enabled=amount.toDoubleOrNull()?.isFinite()==true,onClick={vm.save(row.copy(value=amount.toDouble(),notes=notes));editing=null}){Text("Save")}},dismissButton={TextButton(onClick={editing=null}){Text("Cancel")}})
    }
}

internal fun formatBodyweight(value: Double): String =
    String.format(java.util.Locale.US, "%.2f", value)

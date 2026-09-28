package com.petermathie.vibecheck.ui

import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.OutlinedButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.domain.model.ActivityDay
import com.petermathie.vibecheck.domain.model.AnatomySex
import com.petermathie.vibecheck.domain.model.ExerciseSummary
import com.petermathie.vibecheck.domain.model.MuscleRecency
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.ui.anatomy.AnatomyView
import com.petermathie.vibecheck.ui.anatomy.FreshnessLegend
import com.petermathie.vibecheck.ui.anatomy.FreshnessNoDataKey
import com.petermathie.vibecheck.ui.anatomy.MuscleMap
import com.petermathie.vibecheck.ui.components.VibeSurface
import com.petermathie.vibecheck.ui.components.MuscleDetailsSheet
import com.petermathie.vibecheck.ui.components.TechnicalBackdrop
import com.petermathie.vibecheck.ui.components.muscleDetailsUiState
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.LocalVibeReducedMotion
import com.petermathie.vibecheck.ui.theme.VibePalette
import com.petermathie.vibecheck.ui.theme.VibePalettes
import com.petermathie.vibecheck.ui.theme.VibeThemeMode
import com.petermathie.vibecheck.ui.theme.VibeShapes
import com.petermathie.vibecheck.ui.theme.VibeSpacing
import com.petermathie.vibecheck.ui.theme.VibeSurfaceLevel
import com.petermathie.vibecheck.ui.theme.LocalVibeMotion
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import com.petermathie.vibecheck.ui.theme.habitHeatmapColors
import com.petermathie.vibecheck.ui.theme.heatmapOutlineColor
import com.petermathie.vibecheck.ui.theme.freshnessColors
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal enum class Destination(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    PROGRAMMES("Plans", Icons.Outlined.FitnessCenter),
    ACTIVE_WORKOUT("Workout", Icons.Outlined.PlayArrow),
    PROGRESS("Progress", Icons.Outlined.BarChart),
    MORE("More", Icons.Outlined.MoreHoriz),
    EXERCISES("Exercises", Icons.Outlined.FitnessCenter),
    HABITS("Habits", Icons.Outlined.Check),
    HISTORY("History", Icons.Outlined.LibraryBooks),
    MEASUREMENTS("Body", Icons.Outlined.AccessibilityNew),
    SETTINGS("Settings", Icons.Outlined.Settings),
    STYLE("Style", Icons.Outlined.Palette),
    ARCHIVE("Archive", Icons.Outlined.Inventory2),
}

@Composable
fun VibeCheckApp(viewModel: MainViewModel = hiltViewModel()) {
    val editor: EditorViewModel = hiltViewModel()
    val error by editor.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("settings",0)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val completionEvent by viewModel.completionEvents.collectAsStateWithLifecycle()
    val exercises by viewModel.exerciseResults.collectAsStateWithLifecycle()
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var paletteId by rememberSaveable {
        mutableStateOf(VibePalettes.normalizeId(prefs.getString("palette", VibePalettes.Ocean.id)))
    }
    var themeMode by rememberSaveable {
        mutableStateOf(VibeThemeMode.fromPreference(prefs.getString("themeMode", null)))
    }
    val palette = rememberVibePalette(prefs)
    val haptics = rememberVibeHaptics()
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            when (key) {
                "palette" -> paletteId = VibePalettes.normalizeId(preferences.getString(key, VibePalettes.Ocean.id))
                "themeMode" -> themeMode = VibeThemeMode.fromPreference(preferences.getString(key, null))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    VibeCheckTheme(palette) {
        val motion = LocalVibeMotion.current
        val reducedMotion = LocalVibeReducedMotion.current
        val travelPx = with(LocalDensity.current) { motion.travelDp.dp.roundToPx() }
        if (state.selectedHistoryDay != null) {
            BackHandler { viewModel.selectHistoryDay(null) }
            HistoryDayScreen(
                state = state,
                onBack = { viewModel.selectHistoryDay(null) },
                onDayChange = viewModel::selectHistoryDay,
            )
            return@VibeCheckTheme
        }

        val fabHost = remember(destination) { AppFabHostState(destination) }
        val density = LocalDensity.current
        val imeVisible = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(density) > 0
        val fabAction = fabHost.action.visibleUnlessBlocked(fabHost.dragging, imeVisible)
        androidx.compose.runtime.CompositionLocalProvider(
            LocalAppFabHost provides fabHost,
            LocalAppFabClearance provides if (fabAction == null) 0.dp else 88.dp,
        ) {
            Scaffold(
                containerColor = palette.background,
                floatingActionButtonPosition = FabPosition.Start,
                floatingActionButton = {
                    AnimatedVisibility(
                        visible = fabAction != null,
                        enter = if (reducedMotion) EnterTransition.None else fadeIn(),
                        exit = if (reducedMotion) ExitTransition.None else fadeOut(),
                    ) {
                        fabAction?.let { AppFloatingAction(it, fabHost) }
                    }
                },
                bottomBar = {
                    PrimaryNavigationBar(destination) { destination = it }
                },
            ) { padding ->
            BackHandler(destination in moreDestinations) { destination = Destination.MORE }
            Column(Modifier.fillMaxSize().padding(padding)) {
                if(error != null) TextButton(onClick = { editor.error.value = null }) { Text(error.orEmpty(),color=MaterialTheme.colorScheme.error) }
                AnimatedContent(
                    targetState = destination,
                    modifier = Modifier.weight(1f),
                    transitionSpec = {
                        if (motion.travelDp == 0) {
                            fadeIn(tween(motion.pageEnterMillis)) togetherWith fadeOut(tween(motion.pageExitMillis))
                        } else {
                            (fadeIn(tween(motion.pageEnterMillis)) + slideInHorizontally(tween(motion.pageEnterMillis)) { travelPx }) togetherWith
                                (fadeOut(tween(motion.pageExitMillis)) + slideOutHorizontally(tween(motion.pageExitMillis)) { -travelPx })
                        }
                    },
                    label = "destination content",
                ) { visibleDestination ->
                when (visibleDestination) {
                    Destination.HOME -> HomeScreen(
                        state,
                        viewModel::selectHistoryDay,
                        { destination = Destination.ACTIVE_WORKOUT },
                        viewModel::selectHomeRecencyDay,
                        viewModel::setMode,
                        completionEvent,
                        viewModel::consumeCompletionEvent,
                    )
                    Destination.PROGRAMMES -> ProgrammeEditor(editor, state.mode, viewModel::setMode) { dayId, replace ->
                        viewModel.startWorkout(dayId, replace) {
                            haptics.perform(VibeHapticEvent.PLAY)
                            destination = Destination.ACTIVE_WORKOUT
                        }
                    }
                    Destination.ACTIVE_WORKOUT -> WorkoutEditor(
                        vm = editor,
                        workoutId = state.activeWorkout?.id,
                        onFinish = { id ->
                            viewModel.finishWorkout(
                                id,
                                onFinished = { destination = Destination.HOME },
                                onRejected = { editor.error.value = it },
                            )
                        },
                        onChoose = { destination = Destination.PROGRAMMES },
                        onDoneEditing = { destination = Destination.HOME },
                        onDeleted = { destination = Destination.HOME },
                    )
                    Destination.EXERCISES -> ExerciseEditor(editor)
                    Destination.PROGRESS -> ProgressScreen(editor)
                    Destination.MORE -> MoreScreen { destination = it }
                    Destination.HABITS -> TrackerScreen(editor)
                    Destination.HISTORY -> HistoryScreen(editor,viewModel::selectHistoryDay)
                    Destination.MEASUREMENTS -> MeasurementsScreen(editor)
                    Destination.SETTINGS -> SettingsScreen(editor, { destination=Destination.STYLE }) { result ->
                        viewModel.removeDemoData(result)
                    }
                    Destination.STYLE -> StyleScreen(
                        paletteId,
                        { paletteId = it; prefs.edit().putString("palette", it).apply() },
                        themeMode,
                        { themeMode = it; prefs.edit().putString("themeMode", it.id).apply() },
                    ) { result -> viewModel.removeDemoData(result) }
                    Destination.ARCHIVE -> ArchiveScreen(editor)
                }
                }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ModeSelector(
    mode: TrainingMode,
    onModeChange: (TrainingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberVibeHaptics()
    SingleChoiceSegmentedButtonRow(modifier) {
        TrainingMode.entries.forEachIndexed { index, item ->
            SegmentedButton(
                selected = mode == item,
                onClick = {
                    if (mode != item) {
                        haptics.perform(VibeHapticEvent.SELECTION)
                        onModeChange(item)
                    }
                },
                shape = SegmentedButtonDefaults.itemShape(index, TrainingMode.entries.size),
                label = { Text(if (item == TrainingMode.STRENGTH) "Strength" else "Stretch") },
            )
        }
    }
}

private val primaryDestinations = listOf(
    Destination.HOME,
    Destination.PROGRAMMES,
    Destination.PROGRESS,
    Destination.HABITS,
    Destination.MEASUREMENTS,
    Destination.MORE,
)

private val moreDestinations = setOf(
    Destination.EXERCISES,
    Destination.HISTORY,
    Destination.SETTINGS,
    Destination.STYLE,
    Destination.ARCHIVE,
)

@Composable
internal fun PrimaryNavigationBar(selected: Destination, onSelect: (Destination) -> Unit) {
    val selectedItem = if (selected in moreDestinations) Destination.MORE else selected
    val haptics = rememberVibeHaptics()
    NavigationBar(Modifier.fillMaxWidth().navigationBarsPadding()) {
        primaryDestinations.forEach { item ->
            NavigationBarItem(
                selected = selectedItem == item,
                onClick = {
                    if (selectedItem != item) {
                        haptics.perform(VibeHapticEvent.NAVIGATION)
                        onSelect(item)
                    }
                },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
internal fun MoreScreen(onSelect: (Destination) -> Unit) {
    val haptics = rememberVibeHaptics()
    ScreenList {
        item { Text("More", style = MaterialTheme.typography.headlineLarge) }
        items(moreDestinations.toList(), key = { it.name }) { destination ->
            OutlinedButton(
                onClick = {
                    haptics.perform(VibeHapticEvent.NAVIGATION)
                    onSelect(destination)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(destination.icon, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text(destination.label)
                }
            }
        }
    }
}

@Composable
internal fun rememberVibePalette(prefs: android.content.SharedPreferences): VibePalette {
    var revision by remember { mutableIntStateOf(0) }
    val systemDark = isSystemInDarkTheme()
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "palette" || key == "themeMode") revision++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    revision
    val storedId = prefs.getString("palette", VibePalettes.Ocean.id)
    val id = VibePalettes.normalizeId(storedId)
    val themeMode = VibeThemeMode.fromPreference(prefs.getString("themeMode", null))
    LaunchedEffect(prefs, storedId, id) {
        if (storedId != id || prefs.contains("accent") || prefs.contains("background") || prefs.contains("surface")) {
            prefs.edit()
                .putString("palette", id)
                .remove("accent")
                .remove("background")
                .remove("surface")
                .apply()
        }
    }
    return VibePalettes.resolve(id, themeMode.useDarkPalette(systemDark))
}

@Composable
internal fun HomeScreen(
    state: MainUiState,
    onDayClick: (Long) -> Unit,
    onContinue: () -> Unit,
    onRecencyDayChange: (Long) -> Unit,
    onModeChange: (TrainingMode) -> Unit,
    completionEvent: WorkoutCompletionEvent? = null,
    onCompletionConsumed: (String) -> Unit = {},
) {
    val sex = if(LocalContext.current.getSharedPreferences("settings",0).getBoolean("female",false)) AnatomySex.FEMALE else AnatomySex.MALE
    var selectedMuscle by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMuscleView by rememberSaveable { mutableStateOf<AnatomyView?>(null) }
    val selected = state.recency.firstOrNull { it.muscleId == selectedMuscle }
    val today = LocalDate.now()
    val selectedRecencyDate = LocalDate.ofEpochDay(state.homeRecencyDay ?: today.toEpochDay())
        .coerceAtMost(today)
    val monthRange = freshnessMonthRange(YearMonth.from(selectedRecencyDate), today)
    val palette = LocalVibePalette.current
    val reducedMotion = LocalVibeReducedMotion.current
    val haptics = rememberVibeHaptics()
    var lastHapticDay by remember { mutableStateOf(selectedRecencyDate.toEpochDay()) }
    var sliderPosition by rememberSaveable(monthRange.month.toString()) {
        mutableFloatStateOf(selectedRecencyDate.dayOfMonth.toFloat())
    }
    var sliderDragging by remember { mutableStateOf(false) }
    var celebratedMuscles by remember { mutableStateOf(emptySet<String>()) }
    var confettiEventId by remember { mutableStateOf<String?>(null) }
    var completionAnnouncement by remember { mutableStateOf<String?>(null) }
    val frontMapFocusRequester = remember { FocusRequester() }
    val backMapFocusRequester = remember { FocusRequester() }
    val sheetTitleFocusRequester = remember { FocusRequester() }
    val selectMuscle: (String, AnatomyView) -> Unit = { muscleId, view ->
        selectedMuscle = muscleId
        selectedMuscleView = view
        haptics.perform(VibeHapticEvent.SELECTION)
    }
    LaunchedEffect(completionEvent?.id) {
        val event = completionEvent ?: return@LaunchedEffect
        onCompletionConsumed(event.id)
        val plan = completionAnimationPlan(reducedMotion)
        completionAnnouncement = if (event.affectedMuscleIds.isEmpty()) {
            "${event.mode.name.lowercase().replaceFirstChar(Char::uppercase)} session complete"
        } else {
            "${event.mode.name.lowercase().replaceFirstChar(Char::uppercase)} session complete. Freshness updated."
        }
        haptics.perform(VibeHapticEvent.SUCCESS)
        if (plan.showConfetti) confettiEventId = event.id
        if (plan.muscleStaggerMillis == 0L) {
            celebratedMuscles = event.affectedMuscleIds.toSet()
        } else {
            event.affectedMuscleIds.forEach { muscleId ->
                celebratedMuscles = celebratedMuscles + muscleId
                delay(plan.muscleStaggerMillis)
            }
        }
        delay(plan.highlightHoldMillis)
        celebratedMuscles = emptySet()
        confettiEventId = null
        delay(2_500)
        completionAnnouncement = null
    }
    LaunchedEffect(selectedMuscle) {
        if (selectedMuscle != null) sheetTitleFocusRequester.requestFocus()
    }
    LaunchedEffect(selectedRecencyDate, sliderDragging) {
        if (!sliderDragging) sliderPosition = selectedRecencyDate.dayOfMonth.toFloat()
    }
    val lowerDay = floor(sliderPosition).toInt().coerceIn(1, monthRange.dayCount)
    val upperDay = (lowerDay + 1).coerceAtMost(monthRange.dayCount)
    val lowerStates = state.freshnessByDay[monthRange.month.atDay(lowerDay).toEpochDay()]
        ?.associate { it.muscleId to it.band }
        ?: state.recency.associate { it.muscleId to it.band }
    val upperStates = state.freshnessByDay[monthRange.month.atDay(upperDay).toEpochDay()]
        ?.associate { it.muscleId to it.band }
        ?: lowerStates
    val mapStates = if (reducedMotion || !sliderDragging) {
        state.recency.associate { it.muscleId to it.band }
    } else {
        lowerStates
    }
    val mapNextStates = if (reducedMotion || !sliderDragging) null else upperStates
    val mapInterpolationFraction = if (mapNextStates == null) 0f else sliderPosition - lowerDay
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val freshnessPanelHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.62f)
        .coerceIn(420.dp, 560.dp)
    val horizontalGutter = ((screenWidth - 840.dp) / 2).coerceAtLeast(VibeSpacing.medium)
    val scrollState = rememberScrollState()
    LaunchedEffect(state.activeWorkout?.id) {
        if (state.activeWorkout == null) scrollState.scrollTo(0)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compactHomeChrome = LocalDensity.current.fontScale <= 1.05f &&
            maxWidth >= 360.dp &&
            maxHeight >= 700.dp
        val verticalPadding = if (compactHomeChrome) VibeSpacing.xSmall else VibeSpacing.medium
        val sectionSpacing = if (compactHomeChrome) VibeSpacing.xSmall else VibeSpacing.medium
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .semantics { contentDescription = "Home content" }
                .padding(horizontal = horizontalGutter, vertical = verticalPadding),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
        VibeCard(
            modifier = Modifier.height(freshnessPanelHeight),
            fillHeight = true,
            technicalBackdrop = true,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "FRESHNESS",
                    modifier = Modifier.weight(1f),
                    color = palette.accent,
                    style = MaterialTheme.typography.labelLarge,
                )
                ModeSelector(state.mode, onModeChange, Modifier.weight(1.45f))
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.End,
                ) {
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MuscleMap(
                            sex = sex,
                            view = AnatomyView.FRONT,
                            states = mapStates,
                            onMuscleTap = { selectMuscle(it, AnatomyView.FRONT) },
                            modifier = Modifier.weight(1f).fillMaxSize().focusRequester(frontMapFocusRequester).focusable(),
                            selectedMuscleId = selectedMuscle,
                            nextStates = mapNextStates,
                            interpolationFraction = mapInterpolationFraction,
                            directInterpolation = sliderDragging,
                            celebratedMuscleIds = celebratedMuscles,
                            alignToLegendBounds = true,
                        )
                        MuscleMap(
                            sex = sex,
                            view = AnatomyView.BACK,
                            states = mapStates,
                            onMuscleTap = { selectMuscle(it, AnatomyView.BACK) },
                            modifier = Modifier.weight(1f).fillMaxSize().focusRequester(backMapFocusRequester).focusable(),
                            selectedMuscleId = selectedMuscle,
                            nextStates = mapNextStates,
                            interpolationFraction = mapInterpolationFraction,
                            directInterpolation = sliderDragging,
                            celebratedMuscleIds = celebratedMuscles,
                            alignToLegendBounds = true,
                        )
                        FreshnessLegend(includeNoData = false)
                    }
                    FreshnessNoDataKey()
                }
                confettiEventId?.let { eventId ->
                    CompletionConfetti(eventId, Modifier.fillMaxSize())
                }
                completionAnnouncement?.let { announcement ->
                    Text(
                        text = announcement,
                        color = Color.Transparent,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = announcement
                            },
                    )
                }
            }
            Text(
                "Freshness, ${selectedRecencyDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
                color = palette.textSecondary,
            )
            Slider(
                value = sliderPosition,
                onValueChange = {
                    sliderDragging = true
                    sliderPosition = it
                    val day = monthRange.month.atDay(it.roundToInt().coerceIn(1, monthRange.dayCount)).toEpochDay()
                    if (day != lastHapticDay) {
                        lastHapticDay = day
                        haptics.perform(VibeHapticEvent.SELECTION)
                    }
                    onRecencyDayChange(day)
                },
                onValueChangeFinished = {
                    val settledDay = sliderPosition.roundToInt().coerceIn(1, monthRange.dayCount)
                    val settledEpochDay = monthRange.month.atDay(settledDay).toEpochDay()
                    sliderDragging = false
                    sliderPosition = settledDay.toFloat()
                    onRecencyDayChange(settledEpochDay)
                },
                valueRange = 1f..monthRange.dayCount.toFloat(),
                steps = (monthRange.dayCount - 2).coerceAtLeast(0),
                colors = SliderDefaults.colors(
                    thumbColor = palette.accent,
                    activeTrackColor = palette.border,
                    inactiveTrackColor = palette.border,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Freshness date" },
            )
        }
        state.activeWorkout?.let { workout ->
            VibeCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ACTIVE WORKOUT", color = palette.accent, style = MaterialTheme.typography.labelLarge)
                        Text(workout.name, style = MaterialTheme.typography.titleLarge)
                        Text("${workout.exercises.sumOf { it.sets.size }} sets autosaved", color = palette.textSecondary)
                    }
                    Button(onClick = {
                        haptics.perform(VibeHapticEvent.PLAY)
                        onContinue()
                    }, shape = MaterialTheme.shapes.medium) { Text("Continue") }
                }
            }
        }
            val trackerContent: @Composable ColumnScope.() -> Unit = {
                if (!compactHomeChrome) {
                    Text("WORK TRACKER", color = palette.accent, style = MaterialTheme.typography.labelLarge)
                }
                MonthlyActivityHeatmap(
                    days = state.activityDays,
                    selectedDate = selectedRecencyDate,
                    today = today,
                    compact = compactHomeChrome,
                    expandRows = compactHomeChrome && state.activeWorkout == null,
                    modifier = if (compactHomeChrome && state.activeWorkout == null) {
                        Modifier.weight(1f, fill = true)
                    } else {
                        Modifier
                    },
                    onDayClick = { date ->
                        onRecencyDayChange(date.toEpochDay())
                        onDayClick(date.toEpochDay())
                    },
                    onMonthChange = { month ->
                        onRecencyDayChange(freshnessDateInMonth(selectedRecencyDate, month, today).toEpochDay())
                    },
                )
                Text(
                    "0 none · 1 low · 2 medium · 3+ strong",
                    color = palette.textFaint,
                    style = if (compactHomeChrome) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                )
            }
            if (compactHomeChrome) {
                VibeSurface(
                    level = VibeSurfaceLevel.CARD,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (state.activeWorkout == null) Modifier.weight(1f, fill = true) else Modifier)
                        .semantics { contentDescription = "Work tracker card" },
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .then(if (state.activeWorkout == null) Modifier.fillMaxHeight() else Modifier)
                            .padding(horizontal = VibeSpacing.small, vertical = VibeSpacing.xSmall),
                        verticalArrangement = Arrangement.spacedBy(VibeSpacing.xSmall),
                        content = trackerContent,
                    )
                }
            } else {
                VibeCard(content = trackerContent)
            }
        }
    }
    selectedMuscle?.let { muscleId ->
        MuscleDetailsSheet(
            state = muscleDetailsUiState(
                muscleId = muscleId,
                recency = selected,
                mode = state.mode,
                colour = palette.freshnessColors().forBand(selected?.band ?: com.petermathie.vibecheck.domain.model.MuscleRecencyBand.NEVER),
            ),
            titleFocusRequester = sheetTitleFocusRequester,
            onDismiss = {
                val requester = if (selectedMuscleView == AnatomyView.BACK) backMapFocusRequester else frontMapFocusRequester
                selectedMuscle = null
                selectedMuscleView = null
                requester.requestFocus()
            },
        )
    }
}

@Composable
private fun MonthlyActivityHeatmap(
    days: List<ActivityDay>,
    selectedDate: LocalDate,
    today: LocalDate,
    compact: Boolean = false,
    expandRows: Boolean = false,
    modifier: Modifier = Modifier,
    onDayClick: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
) {
    val palette = LocalVibePalette.current
    val heatmapColors = palette.habitHeatmapColors()
    val range = freshnessMonthRange(YearMonth.from(selectedDate), today)
    val counts = days.associate { it.epochDay to it.activityCount }
    val leadingDays = range.firstDate.dayOfWeek.value % 7
    val weekCount = (leadingDays + range.dayCount + 6) / 7
    Column(
        modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = "Freshness month navigation" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            VibeActionButton("Earlier", { onMonthChange(range.month.minusMonths(1)) }, importance = ActionImportance.COMPACT)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (compact) {
                    Text("WORK TRACKER", color = palette.accent, style = MaterialTheme.typography.labelSmall)
                }
                Text(
                    range.month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            VibeActionButton(
                "Later",
                { onMonthChange(range.month.plusMonths(1)) },
                importance = ActionImportance.COMPACT,
                enabled = range.month < YearMonth.from(today),
            )
        }
        val outerCount = if (compact) 7 else weekCount
        val innerCount = if (compact) weekCount else 7
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (expandRows) Modifier.weight(1f, fill = true) else Modifier)
                .semantics { contentDescription = "Activity heatmap grid, $weekCount weeks" },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(outerCount) { outer ->
                Column(
                    Modifier
                        .weight(1f)
                        .then(if (expandRows) Modifier.fillMaxHeight() else Modifier),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(innerCount) { inner ->
                        val week = if (compact) inner else outer
                        val weekday = if (compact) outer else inner
                        val dayOfMonth = week * 7 + weekday - leadingDays + 1
                        val cellModifier = Modifier
                            .fillMaxWidth()
                            .then(if (expandRows) Modifier.weight(1f, fill = true) else Modifier.height(14.dp))
                        if (dayOfMonth in 1..range.dayCount) {
                            val date = range.month.atDay(dayOfMonth)
                            val count = counts[date.toEpochDay()] ?: 0
                            val color = heatmapColors.forLevel(count.coerceAtMost(3))
                            val outlineColor = when {
                                date == selectedDate || date == today -> palette.heatmapOutlineColor(color)
                                else -> Color.Transparent
                            }
                            Box(
                                cellModifier
                                    .semantics {
                                        contentDescription = "$date: $count activities" +
                                            if (date == selectedDate) ", selected freshness date" else ""
                                    }
                                    .background(color, RoundedCornerShape(VibeShapes.tooltip))
                                    .border(
                                        width = if (date == selectedDate) 2.dp else 1.dp,
                                        color = outlineColor,
                                        shape = RoundedCornerShape(VibeShapes.tooltip),
                                    )
                                    .clickable { onDayClick(date) },
                            )
                        } else {
                            Spacer(cellModifier)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityHeatmap(
    days: List<ActivityDay>,
    onDayClick: (Long) -> Unit,
    activityColor: androidx.compose.ui.graphics.Color? = null,
    itemLabel: String = "activities",
    compact: Boolean = false,
) {
    val palette = LocalVibePalette.current
    val heatmapColors = palette.habitHeatmapColors(activityColor)
    val counts = days.associate { it.epochDay to it.activityCount }
    var offset by rememberSaveable { mutableStateOf(0L) }
    val today = LocalDate.now().toEpochDay() + offset
    val start = today - 34
    val cellSpacing = if (compact) 4.dp else 6.dp
    val cellHeight = if (compact) 14.dp else 22.dp
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "$itemLabel date navigation" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        VibeActionButton("Earlier", { offset -= 35 }, importance = ActionImportance.COMPACT)
        Text(
            "${LocalDate.ofEpochDay(start).format(DateTimeFormatter.ofPattern("d MMM"))} – ${LocalDate.ofEpochDay(today).format(DateTimeFormatter.ofPattern("d MMM"))}",
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        VibeActionButton("Later", { offset = (offset + 35).coerceAtMost(0) }, importance = ActionImportance.COMPACT, enabled = offset < 0)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(cellSpacing)) {
        repeat(5) { week ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(cellSpacing)) {
                repeat(7) { day ->
                    val epochDay = start + week * 7 + day
                    val count = counts[epochDay] ?: 0
                    val color = heatmapColors.forLevel(count.coerceAtMost(3))
                    val isToday = epochDay == LocalDate.now().toEpochDay()
                    Box(
                        Modifier.fillMaxWidth().height(cellHeight)
                            .semantics { contentDescription = "${LocalDate.ofEpochDay(epochDay)}: $count $itemLabel" }
                            .background(color, RoundedCornerShape(VibeShapes.tooltip))
                            .border(
                                width = 1.dp,
                                color = if (isToday) palette.heatmapOutlineColor(color) else Color.Transparent,
                                shape = RoundedCornerShape(VibeShapes.tooltip),
                            )
                            .clickable { onDayClick(epochDay) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseLibraryScreen(exercises: List<ExerciseSummary>, onSearch: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    ScreenList {
        item {
            Text("Exercise library", style = MaterialTheme.typography.headlineLarge)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; onSearch(it) },
                label = { Text("Search by name, alias or muscle") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(exercises, key = { it.id }) { exercise ->
            VibeCard {
                Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                Text("${exercise.tag.name.lowercase()} · ${exercise.trackingType.name.lowercase().replace('_', ' ')}", color = LocalVibePalette.current.textSecondary)
            }
        }
    }
}

@Composable
internal fun VibeCard(
   modifier: Modifier = Modifier,
   fillHeight: Boolean = false,
   technicalBackdrop: Boolean = false,
   content: @Composable ColumnScope.() -> Unit,
) {
    VibeSurface(
        level = VibeSurfaceLevel.CARD,
        modifier = modifier.fillMaxWidth(),
    ) {
        val contentModifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
        val cardContent: @Composable () -> Unit = {
            Column(
                contentModifier.padding(VibeSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(VibeSpacing.small),
            ) {
                content()
            }
        }
        if (technicalBackdrop) {
            TechnicalBackdrop(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Freshness panel texture" },
            ) {
                cardContent()
            }
        } else {
            cardContent()
        }
    }
}

@Composable
internal fun ScreenList(
    modifier: Modifier = Modifier,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
        val sidePadding = ((maxWidth - 840.dp) / 2).coerceAtLeast(VibeSpacing.medium)
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        val reorderContext = rememberReorderScrollContext(listState)
        ReorderOverlayHost(Modifier.fillMaxSize()) {
            androidx.compose.runtime.CompositionLocalProvider(LocalReorderScrollContext provides reorderContext) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .reorderScrollViewport(reorderContext),
                    contentPadding = PaddingValues(
                        start = sidePadding,
                        top = VibeSpacing.medium,
                        end = sidePadding,
                        bottom = VibeSpacing.medium + LocalAppFabClearance.current,
                    ),
                    verticalArrangement = Arrangement.spacedBy(VibeSpacing.medium),
                    content = content,
                )
            }
        }
    }
}

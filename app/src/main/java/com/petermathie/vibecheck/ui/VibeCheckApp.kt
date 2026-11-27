package com.petermathie.vibecheck.ui

import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.path
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.FabPosition
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petermathie.vibecheck.domain.model.ActivityDay
import com.petermathie.vibecheck.domain.model.AnatomySex
import com.petermathie.vibecheck.domain.model.ExerciseSummary
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.ui.anatomy.AnatomyView
import com.petermathie.vibecheck.ui.anatomy.FreshnessLegend
import com.petermathie.vibecheck.ui.anatomy.MuscleMap
import com.petermathie.vibecheck.ui.anatomy.croppedMuscleMapHeight
import com.petermathie.vibecheck.ui.anatomy.rememberMuscleMapBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import com.petermathie.vibecheck.ui.components.VibeSurface
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
import com.petermathie.vibecheck.ui.theme.interpolateFreshnessColor
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
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
    val isStartingWorkout by viewModel.isStartingWorkout.collectAsStateWithLifecycle()
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
        if (state.selectedHistoryDay != null && destination != Destination.HOME) {
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
                floatingActionButtonPosition = FabPosition.End,
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
            MoreNavigationBackHandler(destination, imeVisible) { destination = it }
            Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
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
                        viewModel::selectHomeRecencyDay,
                        viewModel::setMode,
                        completionEvent,
                        viewModel::consumeCompletionEvent,
                        onDayDismiss = { viewModel.selectHistoryDay(null) },
                    )
                    Destination.PROGRAMMES -> ProgrammeEditor(
                        vm = editor,
                        defaultMode = state.mode,
                        onResume = { destination = Destination.ACTIVE_WORKOUT },
                        isStartingWorkout = isStartingWorkout,
                    ) { dayId, activeWorkoutId, finishCurrent ->
                        viewModel.startWorkout(
                            dayId,
                            activeWorkoutId,
                            finishCurrent,
                            onStarted = {
                                haptics.perform(VibeHapticEvent.PLAY)
                                destination = Destination.ACTIVE_WORKOUT
                            },
                            onRejected = { editor.error.value = it },
                        )
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
                        showWorkoutTiming = true,
                        onDeleted = { destination = Destination.HOME },
                    )
                    Destination.EXERCISES -> ExerciseEditor(
                        vm = editor,
                        onBack = { destination = Destination.MORE },
                    )
                    Destination.PROGRESS -> ProgressScreen(editor)
                    Destination.MORE -> MoreScreen { destination = it }
                    Destination.HABITS -> TrackerScreen(editor)
                    Destination.HISTORY -> HistoryScreen(
                        vm = editor,
                        onBack = { destination = Destination.MORE },
                    )
                    Destination.MEASUREMENTS -> MeasurementsScreen(editor)
                    Destination.SETTINGS -> SettingsScreen(
                        vm = editor,
                        onStyle = { destination = Destination.STYLE },
                        onRemoveDemo = { result -> viewModel.removeDemoData(result) },
                        onBack = { destination = Destination.MORE },
                    )
                    Destination.STYLE -> StyleScreen(
                        selectedId = paletteId,
                        onSelect = { paletteId = it; prefs.edit().putString("palette", it).apply() },
                        themeMode = themeMode,
                        onThemeModeChange = { themeMode = it; prefs.edit().putString("themeMode", it.id).apply() },
                        onBack = { destination = Destination.SETTINGS },
                    )
                    Destination.ARCHIVE -> ArchiveScreen(
                        vm = editor,
                        onBack = { destination = Destination.MORE },
                    )
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
    iconOnly: Boolean = false,
) {
    val haptics = rememberVibeHaptics()
    val selectMode: (TrainingMode) -> Unit = { item ->
        if (mode != item) {
            haptics.perform(VibeHapticEvent.SELECTION)
            onModeChange(item)
        }
    }
    if (iconOnly) {
        val palette = LocalVibePalette.current
        val selection by animateFloatAsState(
            targetValue = TrainingMode.entries.indexOf(mode).toFloat(),
            animationSpec = tween(LocalVibeMotion.current.selectionMillis),
            label = "Home mode highlight",
        )
        BoxWithConstraints(modifier.selectableGroup(), contentAlignment = Alignment.Center) {
            val optionWidth = (maxWidth - 8.dp) / TrainingMode.entries.size
            Box(
                Modifier.fillMaxWidth().height(40.dp)
                    .background(if (palette.isDark) palette.surface else palette.surfaceInset, RoundedCornerShape(50)),
            )
            Box(
                Modifier.align(Alignment.CenterStart)
                    .offset(x = 4.dp + optionWidth * selection)
                    .width(optionWidth).height(32.dp)
                    .background(palette.surfaceFloating, RoundedCornerShape(50))
                    .testTag("Home selected mode pill"),
            )
            Row(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                TrainingMode.entries.forEach { item ->
                    val selected = mode == item
                    Box(
                        Modifier.weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .selectable(selected, role = Role.RadioButton, onClick = { selectMode(item) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (item == TrainingMode.STRENGTH) Icons.Outlined.FitnessCenter else SeatedStretchIcon,
                            contentDescription = if (item == TrainingMode.STRENGTH) "Strength" else "Stretch",
                            modifier = Modifier.size(24.dp),
                            tint = if (selected) palette.textPrimary else palette.textSecondary,
                        )
                    }
                }
            }
        }
        return
    }
    val showSelectionIcon = LocalDensity.current.fontScale <= 1.05f
    SingleChoiceSegmentedButtonRow(modifier) {
        TrainingMode.entries.forEachIndexed { index, item ->
            SegmentedButton(
                selected = mode == item,
                onClick = { selectMode(item) },
                shape = SegmentedButtonDefaults.itemShape(index, TrainingMode.entries.size),
                icon = { if (showSelectionIcon) SegmentedButtonDefaults.Icon(mode == item) },
                label = {
                    Text(if (item == TrainingMode.STRENGTH) "Strength" else "Stretch")
                },
            )
        }
    }
}

private val SeatedStretchIcon by lazy {
    ImageVector.Builder("SeatedStretch", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(15.7f, 5.9f)
            curveTo(15.7f, 7.06f, 14.76f, 8f, 13.6f, 8f)
            curveTo(12.44f, 8f, 11.5f, 7.06f, 11.5f, 5.9f)
            curveTo(11.5f, 4.74f, 12.44f, 3.8f, 13.6f, 3.8f)
            curveTo(14.76f, 3.8f, 15.7f, 4.74f, 15.7f, 5.9f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(8.4f, 17.4f)
            curveTo(8.3f, 14.8f, 9.4f, 11.1f, 10.5f, 9.6f)
            curveTo(11.3f, 8.7f, 12.5f, 8.8f, 13.2f, 9.8f)
            lineTo(18.8f, 16.3f)
            quadTo(19.1f, 16.8f, 18.5f, 17.1f)
            lineTo(17.5f, 17.2f)
            lineTo(11.9f, 12.1f)
            lineTo(10.5f, 16.7f)
            quadTo(9.9f, 18f, 8.4f, 17.4f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(7.7f, 16.5f)
            lineTo(20.9f, 18f)
            lineTo(20.9f, 17.4f)
            quadTo(21.1f, 16.1f, 21.5f, 16.2f)
            quadTo(22.1f, 16.3f, 22.1f, 17.8f)
            quadTo(22.1f, 19.3f, 21f, 19.4f)
            lineTo(8.1f, 19.4f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(8.8f, 17.4f)
            lineTo(4.9f, 16.2f)
            quadTo(4.3f, 16f, 3.8f, 16.5f)
            lineTo(1.6f, 18.2f)
            quadTo(1.2f, 18.6f, 1.5f, 19.1f)
            quadTo(1.8f, 19.5f, 2.4f, 19.2f)
            lineTo(4.8f, 18.1f)
            lineTo(7.3f, 19.2f)
            quadTo(8.7f, 19.7f, 9.2f, 18.5f)
            close()
        }
    }.build()
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
internal fun MoreNavigationBackHandler(
    destination: Destination,
    imeVisible: Boolean,
    onNavigate: (Destination) -> Unit,
) {
    BackHandler(destination in moreDestinations && !imeVisible) {
        onNavigate(if (destination == Destination.STYLE) Destination.SETTINGS else Destination.MORE)
    }
}

@Composable
internal fun VibeBackChevron(
    onBack: () -> Unit,
    description: String = "Back to More",
    enabled: Boolean = true,
) {
    IconButton(onClick = onBack, enabled = enabled) {
        Icon(
            Icons.Outlined.ChevronLeft,
            contentDescription = description,
            modifier = Modifier.size(18.dp),
            tint = LocalVibePalette.current.textSecondary,
        )
    }
}

@Composable
internal fun MoreChildTitleRow(
    title: String,
    onBack: () -> Unit,
    backDescription: String = "Back to More",
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        VibeBackChevron(onBack, backDescription)
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge)
    }
}

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
    val palette = LocalVibePalette.current
    ScreenList {
        item { Text("More", style = MaterialTheme.typography.headlineLarge) }
        itemsIndexed(moreDestinations.filter { it != Destination.STYLE }, key = { _, item -> item.name }) { index, destination ->
            Column(Modifier.fillMaxWidth()) {
                if (index > 0) HorizontalDivider(
                    modifier = Modifier.testTag("More option divider"),
                    color = palette.border.copy(alpha = 0.3f),
                )
                TextButton(
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
                        Text(destination.label, style = MaterialTheme.typography.titleLarge)
                    }
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
    onRecencyDayChange: (Long) -> Unit,
    onModeChange: (TrainingMode) -> Unit,
    completionEvent: WorkoutCompletionEvent? = null,
    onCompletionConsumed: (String) -> Unit = {},
    today: LocalDate = LocalDate.now(),
    onDayDismiss: () -> Unit = {},
) {
    val sex = if (LocalContext.current.getSharedPreferences("settings", 0).getBoolean("female", false)) AnatomySex.FEMALE else AnatomySex.MALE
    var selectedMuscle by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedMuscleView by rememberSaveable { mutableStateOf<AnatomyView?>(null) }
    var muscleModalOpen by rememberSaveable { mutableStateOf(false) }
    val selected = state.recency.firstOrNull { it.muscleId == selectedMuscle }
    val selectedRecencyDate = LocalDate.ofEpochDay(state.homeRecencyDay ?: today.toEpochDay())
        .coerceAtMost(today)
    val endOfSelectedDay = selectedRecencyDate.plusDays(1)
        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
    val muscleDataReady = state.recencyMode == state.mode && state.recencyAsOfMillis?.let { atMillis ->
        if (state.homeRecencyDay == null) {
            atMillis > selectedRecencyDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1 &&
                atMillis <= System.currentTimeMillis()
        } else {
            atMillis == endOfSelectedDay
        }
    } == true
    val palette = LocalVibePalette.current
    val reducedMotion = LocalVibeReducedMotion.current
    val haptics = rememberVibeHaptics()
    var celebratedMuscles by remember { mutableStateOf(emptySet<String>()) }
    var confettiEventId by remember { mutableStateOf<String?>(null) }
    var completionAnnouncement by remember { mutableStateOf<String?>(null) }
    val frontMapFocusRequester = remember { FocusRequester() }
    val backMapFocusRequester = remember { FocusRequester() }
    val selectMuscle: (String, AnatomyView) -> Unit = { muscleId, view ->
        if (selectedMuscle != muscleId || selectedMuscleView != view) haptics.perform(VibeHapticEvent.SELECTION)
        selectedMuscle = muscleId
        selectedMuscleView = view
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
    val mapStates = state.recency.associate { it.muscleId to it.band }
    val scrollState = rememberScrollState()
    val modalDay = state.selectedHistoryDay
    val modalKey = modalDay?.let { "day:$it" }
        ?: selectedMuscle?.takeIf { muscleModalOpen }?.let { "muscle:$it:${state.mode}:$selectedRecencyDate" }
    var modalReady by remember(modalKey) { mutableStateOf(false) }
    val modalProgress by key(modalKey) {
        animateFloatAsState(
            targetValue = if (modalReady) 1f else 0f,
            animationSpec = tween(LocalVibeMotion.current.expandMillis),
            label = "Home details presentation",
        )
    }
    var viewportBounds by remember { mutableStateOf(Rect.Zero) }
    var calendarBounds by remember { mutableStateOf(Rect.Zero) }
    BoxWithConstraints(Modifier.fillMaxSize().onGloballyPositioned { viewportBounds = it.boundsInWindow() }) {
        val density = LocalDensity.current
        val fontScale = density.fontScale.coerceAtLeast(1f)
        val stackedControls = maxWidth < 360.dp || fontScale > 1.05f
        val horizontalGutter = ((maxWidth - 840.dp) / 2).coerceAtLeast(VibeSpacing.medium)
        val legendWidth = ((maxWidth - horizontalGutter * 2) / 2 - VibeSpacing.medium).coerceAtLeast(0.dp)
        val verticalPadding = VibeSpacing.xSmall
        val referenceAvailableHeight = with(density) {
            (constraints.maxHeight - verticalPadding.roundToPx() * 2 - VibeSpacing.xSmall.roundToPx())
                .coerceAtLeast(0).toDp()
        }
        val referenceCalendarHeight = 36.dp + (if (fontScale > 1.05f) 192.dp else 184.dp) * fontScale
        // Keep figure sizing stable while redistributing the vertical spacing.
        val referenceFreshnessHeight = with(density) {
            (referenceAvailableHeight.coerceAtLeast(referenceCalendarHeight / 0.3f).roundToPx() * 0.7f)
                .roundToInt().toDp()
        }
        // Budget rounded child heights, row gaps, spacing and panel padding.
        val minimumCalendarHeight = with(density) {
            (48.dp * fontScale).roundToPx() +
                ((if (fontScale > 1.05f) 32.dp else 24.dp) * fontScale).roundToPx() +
                (22.dp * fontScale).roundToPx() * 6 +
                4.dp.roundToPx() * 5 + VibeSpacing.xSmall.roundToPx() * 4
        }
        val headerHeight = if (stackedControls) 80.dp * fontScale else 48.dp
        val referenceMapHeight = referenceFreshnessHeight - VibeSpacing.medium * 2 -
            headerHeight - VibeSpacing.small - 64.dp
        val referenceMapHeightPx = with(density) { referenceMapHeight.toPx() }
        val frontBounds = rememberMuscleMapBounds(sex, AnatomyView.FRONT)
        val backBounds = rememberMuscleMapBounds(sex, AnatomyView.BACK)
        Layout(
            modifier = Modifier
                .fillMaxSize()
                .blur(12.dp * modalProgress)
                .verticalScroll(scrollState)
                .semantics { contentDescription = "Home content" }
                .padding(horizontal = horizontalGutter)
                .padding(
                    top = verticalPadding,
                    bottom = verticalPadding + VibeSpacing.medium + 22.dp * fontScale,
                ),
            content = {
                Layout(
                    modifier = Modifier.fillMaxWidth()
                        .habitGlow(palette.accent, palette.isDark)
                        .semantics { contentDescription = "Freshness panel" }
                        .padding(horizontal = VibeSpacing.medium)
                        .padding(top = VibeSpacing.xSmall),
                    content = {
                        Row(
                            Modifier.fillMaxWidth().height(headerHeight).testTag("Home freshness heading"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                Modifier.weight(1f).padding(end = VibeSpacing.medium),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("Freshness", color = palette.textPrimary, style = MaterialTheme.typography.labelLarge)
                                FreshnessLegend(
                                    modifier = Modifier.widthIn(max = legendWidth).fillMaxWidth(),
                                    includeNoData = false,
                                    horizontal = true,
                                )
                            }
                            ModeSelector(
                                state.mode,
                                onModeChange,
                                Modifier.width(112.dp).height(48.dp).testTag("Home mode selector"),
                                iconOnly = true,
                            )
                        }
                        Box(Modifier.fillMaxWidth()) {
                            Layout(
                                modifier = Modifier.fillMaxWidth(),
                                content = {
                                    MuscleMap(
                                        sex = sex,
                                        view = AnatomyView.FRONT,
                                        states = mapStates,
                                        onMuscleTap = { selectMuscle(it, AnatomyView.FRONT) },
                                        onMuscleDoubleTap = {
                                            selectMuscle(it, AnatomyView.FRONT)
                                            muscleModalOpen = true
                                        },
                                        modifier = Modifier
                                            .focusRequester(frontMapFocusRequester).focusable(),
                                        selectedMuscleId = selectedMuscle,
                                        celebratedMuscleIds = celebratedMuscles,
                                        alignToLegendBounds = true,
                                        musclesOnly = true,
                                        referenceCanvasHeight = referenceMapHeight + (VibeSpacing.medium - VibeSpacing.xSmall) * 2,
                                    )
                                    MuscleMap(
                                        sex = sex,
                                        view = AnatomyView.BACK,
                                        states = mapStates,
                                        onMuscleTap = { selectMuscle(it, AnatomyView.BACK) },
                                        onMuscleDoubleTap = {
                                            selectMuscle(it, AnatomyView.BACK)
                                            muscleModalOpen = true
                                        },
                                        modifier = Modifier
                                            .focusRequester(backMapFocusRequester).focusable(),
                                        selectedMuscleId = selectedMuscle,
                                        celebratedMuscleIds = celebratedMuscles,
                                        alignToLegendBounds = true,
                                        musclesOnly = true,
                                        referenceCanvasHeight = referenceMapHeight + (VibeSpacing.medium - VibeSpacing.xSmall) * 2,
                                    )
                                },
                            ) { measurables, constraints ->
                                val gap = 8.dp.roundToPx()
                                val referenceGutter = 35.dp.roundToPx().coerceAtMost(constraints.maxWidth / 2)
                                val referenceWidth = (constraints.maxWidth - referenceGutter * 2 - gap).coerceAtLeast(0)
                                val sideGutter = 24.dp.roundToPx().coerceAtMost(constraints.maxWidth / 2)
                                val anatomyWidth = (constraints.maxWidth - sideGutter * 2).coerceAtLeast(0)
                                val mapWidth = (anatomyWidth - gap).coerceAtLeast(0)
                                val frontWidth = mapWidth / 2
                                val backWidth = mapWidth - frontWidth
                                val mapHeight = maxOf(
                                    croppedMuscleMapHeight(frontBounds, referenceWidth / 2, referenceMapHeightPx),
                                    croppedMuscleMapHeight(backBounds, referenceWidth - referenceWidth / 2, referenceMapHeightPx),
                                ) + 32.dp.roundToPx() +
                                    2 * (VibeSpacing.medium.roundToPx() - VibeSpacing.xSmall.roundToPx())
                                val front = measurables[0].measure(Constraints.fixed(frontWidth, mapHeight))
                                val back = measurables[1].measure(Constraints.fixed(backWidth, mapHeight))
                                layout(constraints.maxWidth, mapHeight) {
                                    front.placeRelative(sideGutter, 0)
                                    back.placeRelative(sideGutter + frontWidth + gap, 0)
                                }
                            }
                            confettiEventId?.let { eventId ->
                                CompletionConfetti(eventId, Modifier.matchParentSize())
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
                    },
                ) { measurables, constraints ->
                    val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
                    val heading = measurables[0].measure(loose)
                    val anatomy = measurables[1].measure(loose)
                    val gap = (VibeSpacing.small + 16.dp).roundToPx()
                    val height = maxOf(constraints.minHeight, heading.height + anatomy.height + gap * 2)
                    layout(constraints.maxWidth, height) {
                        heading.placeRelative(0, 0)
                        anatomy.placeRelative(0, heading.height + (height - heading.height - anatomy.height) / 2)
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    MonthlyActivityHeatmap(
                        days = state.activityDays,
                        selectedDate = selectedRecencyDate,
                        today = today,
                        modifier = Modifier.fillMaxWidth()
                            .onGloballyPositioned { calendarBounds = it.unclippedWindowBounds() }
                            .habitGlow(palette.accent, palette.isDark)
                            .semantics { contentDescription = "Work tracker card" }
                            .padding(horizontal = VibeSpacing.small, vertical = VibeSpacing.xSmall),
                        onDaySelect = { date ->
                            onRecencyDayChange(date.toEpochDay())
                        },
                        onDayOpen = { date ->
                            onRecencyDayChange(date.toEpochDay())
                            onDayClick(date.toEpochDay())
                        },
                        onMonthChange = { month ->
                            onRecencyDayChange(freshnessDateInMonth(selectedRecencyDate, month, today).toEpochDay())
                        },
                    )
                }
            },
        ) { measurables, constraints ->
            val freshness = measurables[0].measure(
                constraints.copy(
                    minHeight = (constraints.minHeight - minimumCalendarHeight).coerceAtLeast(0),
                    maxHeight = Constraints.Infinity,
                ),
            )
            val calendar = measurables[1].measure(Constraints.fixed(constraints.maxWidth, minimumCalendarHeight))
            layout(constraints.maxWidth, freshness.height + calendar.height) {
                freshness.placeRelative(0, 0)
                calendar.placeRelative(0, freshness.height)
            }
        }
        if (modalKey != null) {
            val minimumHeight = with(LocalDensity.current) { (192.dp * fontScale).toPx() }
            val bottom = minOf(calendarBounds.bottom, viewportBounds.bottom)
            val top = calendarBounds.top.coerceAtMost(bottom - minimumHeight).coerceAtLeast(viewportBounds.top)
            if (calendarBounds.width > 0 && bottom > top) {
                val bounds = Rect(calendarBounds.left, top, calendarBounds.right, bottom)
                key(modalKey) {
                    if (modalDay != null) {
                        HomeDayActivityDialog(
                            day = modalDay,
                            detail = state.historyDay,
                            bounds = bounds,
                            presentationProgress = modalProgress,
                            onReady = { modalReady = true },
                            onDismiss = onDayDismiss,
                        )
                    } else {
                        HomeMuscleActivityDialog(
                            muscleId = checkNotNull(selectedMuscle),
                            recency = selected,
                            mode = state.mode,
                            date = selectedRecencyDate,
                            asOfMillis = state.recencyAsOfMillis.takeIf { muscleDataReady },
                            bounds = bounds,
                            presentationProgress = modalProgress,
                            onReady = { modalReady = true },
                            onDismiss = {
                                muscleModalOpen = false
                                val requester = if (selectedMuscleView == AnatomyView.BACK) backMapFocusRequester else frontMapFocusRequester
                                requester.requestFocus()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun LayoutCoordinates.unclippedWindowBounds(): Rect {
    val position = positionInWindow()
    return Rect(position.x, position.y, position.x + size.width, position.y + size.height)
}

@Composable
private fun MonthlyActivityHeatmap(
    days: List<ActivityDay>,
    selectedDate: LocalDate,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onDaySelect: (LocalDate) -> Unit,
    onDayOpen: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
) {
    val palette = LocalVibePalette.current
    val heatmapColors = palette.habitHeatmapColors()
    val month = YearMonth.from(selectedDate)
    val cells = remember(month) { homeCalendarCells(month) }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val counts = days.associate { it.epochDay to it.activityCount }
    val intensities = homeCalendarIntensities(counts, month, today)
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(VibeSpacing.xSmall),
    ) {
        Row(
            Modifier.fillMaxWidth().height(48.dp * fontScale)
                .semantics { contentDescription = "Freshness month navigation" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IconButton(
                onClick = { onMonthChange(month.minusMonths(1)) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Outlined.ChevronLeft, contentDescription = "Previous month")
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                modifier = Modifier.weight(1f),
                color = palette.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
            )
            IconButton(
                onClick = { onMonthChange(month.plusMonths(1)) },
                modifier = Modifier.size(48.dp),
                enabled = month < YearMonth.from(today),
            ) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = "Next month")
            }
        }
        Row(
            Modifier.fillMaxWidth().height((if (fontScale > 1.05f) 32.dp else 24.dp) * fontScale),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { day ->
                Text(
                    day,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .homeCalendarGestures(cells, today, onDaySelect, onDayOpen)
                .semantics { contentDescription = "Activity heatmap grid, 6 weeks, Monday first" },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(6) { week ->
                Row(
                    Modifier.fillMaxWidth().height(22.dp * fontScale),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(7) { weekday ->
                        val date = cells[week * 7 + weekday]
                        val cellModifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                        if (date != null) {
                            val future = date > today
                            val count = if (future) 0 else counts[date.toEpochDay()] ?: 0
                            val color = if (count == 0) heatmapColors.neutral else interpolateFreshnessColor(
                                heatmapColors.low,
                                heatmapColors.strong,
                                intensities.getValue(date.toEpochDay()),
                            )
                            val outlineColor = when {
                                date == selectedDate -> palette.background
                                date == today -> palette.heatmapOutlineColor(color)
                                else -> Color.Transparent
                            }
                            Box(
                                cellModifier
                                    .semantics {
                                        selected = date == selectedDate
                                        contentDescription = "${formatDisplayDate(date)}: $count activities" +
                                            when {
                                                future -> ", future date"
                                                date == selectedDate -> ", selected freshness date"
                                                else -> ""
                                            }
                                        if (!future) {
                                            customActions = listOf(
                                                CustomAccessibilityAction("Open activity breakdown") {
                                                    onDayOpen(date)
                                                    true
                                                },
                                            )
                                        }
                                    }
                                    .background(color, RoundedCornerShape(VibeShapes.tooltip))
                                    .border(
                                        width = if (date == selectedDate) 3.dp else 1.dp,
                                        color = outlineColor,
                                        shape = RoundedCornerShape(VibeShapes.tooltip),
                                    )
                                    .clickable(enabled = !future, onClickLabel = "Show freshness") { onDaySelect(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    date.dayOfMonth.toString(),
                                    color = if (future) palette.textFaint else palette.heatmapOutlineColor(color),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (date == selectedDate) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                )
                            }
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
    content: @Composable ColumnScope.() -> Unit,
) {
    VibeSurface(
        level = VibeSurfaceLevel.CARD,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(VibeSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(VibeSpacing.small),
        ) {
            content()
        }
    }
}

@Composable
internal fun ScreenList(
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    flingBehavior: androidx.compose.foundation.gestures.FlingBehavior = androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior(),
    itemSpacing: androidx.compose.ui.unit.Dp = VibeSpacing.medium,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
        val sidePadding = ((maxWidth - 840.dp) / 2).coerceAtLeast(VibeSpacing.medium)
        val reorderContext = rememberReorderScrollContext(listState)
        ReorderOverlayHost(Modifier.fillMaxSize()) {
            androidx.compose.runtime.CompositionLocalProvider(LocalReorderScrollContext provides reorderContext) {
                LazyColumn(
                    state = listState,
                    flingBehavior = flingBehavior,
                    modifier = Modifier
                        .fillMaxSize()
                        .reorderScrollViewport(reorderContext),
                    contentPadding = PaddingValues(
                        start = sidePadding,
                        top = VibeSpacing.medium,
                        end = sidePadding,
                        bottom = VibeSpacing.medium + LocalAppFabClearance.current,
                    ),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing),
                    content = content,
                )
            }
        }
    }
}

package com.petermathie.vibecheck.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petermathie.vibecheck.data.TrainingRepository
import com.petermathie.vibecheck.data.local.CatalogueDao
import com.petermathie.vibecheck.domain.model.ActiveWorkout
import com.petermathie.vibecheck.domain.model.ActivityDay
import com.petermathie.vibecheck.domain.model.ExerciseSummary
import com.petermathie.vibecheck.domain.model.ExerciseTag
import com.petermathie.vibecheck.domain.model.HistoryDayDetail
import com.petermathie.vibecheck.domain.model.MuscleRecency
import com.petermathie.vibecheck.domain.model.ProgrammeDaySummary
import com.petermathie.vibecheck.domain.model.SetDraft
import com.petermathie.vibecheck.domain.model.TrackingType
import com.petermathie.vibecheck.domain.model.TrainingMode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.util.UUID

data class MainUiState(
    val mode: TrainingMode = TrainingMode.STRENGTH,
    val programmeDays: List<ProgrammeDaySummary> = emptyList(),
    val activeWorkout: ActiveWorkout? = null,
    val recency: List<MuscleRecency> = emptyList(),
    val freshnessByDay: Map<Long, List<MuscleRecency>> = emptyMap(),
    val activityDays: List<ActivityDay> = emptyList(),
    val homeRecencyDay: Long? = null,
    val selectedHistoryDay: Long? = null,
    val historyDay: HistoryDayDetail? = null,
)

data class WorkoutCompletionEvent(
    override val id: String,
    val mode: TrainingMode,
    val affectedMuscleIds: List<String>,
) : IdentifiedUiEvent

private data class CoreUiState(
    val mode: TrainingMode,
    val programmeDays: List<ProgrammeDaySummary>,
    val activeWorkout: ActiveWorkout?,
    val recency: List<MuscleRecency>,
    val activityDays: List<ActivityDay>,
)

private data class RecencyQuery(
    val mode: TrainingMode,
    val homeDay: Long?,
    val historyDay: Long?,
    val now: Long,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: TrainingRepository,
    private val catalogueDao: CatalogueDao,
) : ViewModel() {
    private val mode = MutableStateFlow(TrainingMode.STRENGTH)
    private val homeRecencyDay = MutableStateFlow<Long?>(null)
    private val selectedHistoryDay = MutableStateFlow<Long?>(null)
    private val searchQuery = MutableStateFlow("")
    private val completionEvent = OneShotEventState<WorkoutCompletionEvent>()
    val completionEvents: StateFlow<WorkoutCompletionEvent?> = completionEvent.state

    private val clock = MutableStateFlow(System.currentTimeMillis())
    init { viewModelScope.launch { while (true) { delay(30_000); clock.value=System.currentTimeMillis() } } }
    private val recency = combine(mode, homeRecencyDay, selectedHistoryDay, clock) { currentMode, homeDay, historyDay, now ->
        RecencyQuery(currentMode, homeDay, historyDay, now)
    }.flatMapLatest { query ->
            val day = query.historyDay ?: query.homeDay
            val atMillis = day?.let { epochDay ->
                LocalDate.ofEpochDay(epochDay).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            } ?: query.now
            repository.observeMuscleRecency(query.mode, atMillis)
        }

    private val history = selectedHistoryDay.flatMapLatest { day ->
        if (day == null) kotlinx.coroutines.flow.flowOf(null)
        else repository.observeHistoryDay(day).map { it as HistoryDayDetail? }
    }

    private val freshnessByDay = combine(mode, homeRecencyDay, clock) { currentMode, homeDay, now ->
        val today = LocalDate.now()
        val selectedDate = LocalDate.ofEpochDay(homeDay ?: today.toEpochDay()).coerceAtMost(today)
        Triple(currentMode, freshnessMonthRange(java.time.YearMonth.from(selectedDate), today), now)
    }.flatMapLatest { (currentMode, range, now) ->
        repository.observeMuscleRecencyRange(
            mode = currentMode,
            firstEpochDay = range.firstDate.toEpochDay(),
            lastEpochDay = range.lastDate.toEpochDay(),
            now = now,
        )
    }

    private val coreState = combine(
        mode,
        repository.observeProgrammeDays(),
        repository.observeDraft(),
        recency,
        repository.observeActivityHeatmap(),
    ) { currentMode, days, workout, currentRecency, activity ->
        CoreUiState(currentMode, days, workout, currentRecency, activity)
    }

    val uiState: StateFlow<MainUiState> = combine(coreState, homeRecencyDay, selectedHistoryDay, history, freshnessByDay) { core, homeDay, selectedDay, dayHistory, freshness ->
        MainUiState(
            mode = core.mode,
            programmeDays = core.programmeDays,
            activeWorkout = core.activeWorkout,
            recency = core.recency,
            freshnessByDay = freshness,
            activityDays = core.activityDays,
            homeRecencyDay = homeDay,
            selectedHistoryDay = selectedDay,
            historyDay = dayHistory,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    val exerciseResults: StateFlow<List<ExerciseSummary>> = searchQuery.flatMapLatest { query ->
        catalogueDao.searchExercises(query).map { rows ->
            rows.map { row ->
                ExerciseSummary(
                    id = row.id,
                    name = row.canonicalName,
                    tag = ExerciseTag.valueOf(row.tag),
                    trackingType = TrackingType.valueOf(row.trackingType),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setMode(value: TrainingMode) { mode.value = value }
    fun setSearchQuery(value: String) { searchQuery.value = value }
    fun selectHomeRecencyDay(epochDay: Long) {
        val today = LocalDate.now().toEpochDay()
        homeRecencyDay.value = epochDay.coerceAtMost(today).takeUnless { it == today }
    }
    fun selectHistoryDay(epochDay: Long?) { selectedHistoryDay.value = epochDay }

    fun startWorkout(dayId: String, replaceExisting: Boolean = false, onStarted: () -> Unit = {}) = viewModelScope.launch {
        repository.startWorkout(dayId, replaceExisting)
        onStarted()
    }

    fun saveCurrentAndStartWorkout(
        currentWorkoutId: String,
        dayId: String,
        onStarted: () -> Unit = {},
        onRejected: (String) -> Unit = {},
    ) = viewModelScope.launch {
        runCatching { repository.finishWorkout(currentWorkoutId, preserveEntryDrafts = true) }
            .onSuccess { completion ->
                if (completion == null) {
                    onRejected("Log at least one completed working set before saving this workout.")
                } else {
                    repository.startWorkout(dayId)
                    mode.value = completion.mode
                    clock.value = System.currentTimeMillis()
                    onStarted()
                }
            }
            .onFailure { onRejected("Workout could not be saved: ${it.message.orEmpty()}") }
    }

    fun addSet(workoutExerciseId: String, draft: SetDraft) = viewModelScope.launch {
        repository.addSet(workoutExerciseId, draft)
    }

    fun updateExerciseNotes(workoutExerciseId: String, notes: String) = viewModelScope.launch {
        repository.updateExerciseNotes(workoutExerciseId, notes)
    }

    fun finishWorkout(
        workoutId: String,
        onFinished: () -> Unit = {},
        onRejected: (String) -> Unit = {},
    ) = viewModelScope.launch {
        runCatching { repository.finishWorkout(workoutId) }
            .onSuccess { completion ->
                if (completion == null) {
                    onRejected("Log at least one completed working set before finishing.")
                } else {
                    mode.value = completion.mode
                    completionEvent.emit(WorkoutCompletionEvent(
                        id = UUID.randomUUID().toString(),
                        mode = completion.mode,
                        affectedMuscleIds = completion.affectedMuscleIds.distinct().sorted(),
                    ))
                    clock.value = System.currentTimeMillis()
                    onFinished()
                }
            }
            .onFailure { onRejected("Workout could not be finished: ${it.message.orEmpty()}") }
    }

    fun consumeCompletionEvent(id: String) {
        completionEvent.consume(id)
    }

    fun removeDemoData(onResult: (Result<com.petermathie.vibecheck.data.DemoRemovalSummary>) -> Unit) =
        viewModelScope.launch {
            onResult(runCatching { repository.removeDemoData() })
        }
}

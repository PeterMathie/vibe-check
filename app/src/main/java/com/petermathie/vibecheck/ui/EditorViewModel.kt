package com.petermathie.vibecheck.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.data.TrackerEditorStore
import com.petermathie.vibecheck.domain.editor.moveItem
import com.petermathie.vibecheck.domain.body.BodyweightUnit
import com.petermathie.vibecheck.domain.body.convertBodyweight
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import android.content.Context
import android.net.Uri
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import com.petermathie.vibecheck.domain.workout.historyWorkoutDateChange
import com.petermathie.vibecheck.domain.workout.historyWorkoutTimeChange
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.workout.loggingMetricsError

fun newId(): String = UUID.randomUUID().toString()

internal data class WorkoutLoggingData(
    val exercises: List<WorkoutExerciseEntity>,
    val sets: List<WorkoutSetEntity>,
    val drafts: List<WorkoutEntryDraftEntity>,
    val variations: List<ExerciseVariationEntity>,
    val bands: List<BandEntity>,
    val definitions: List<ExerciseEntity> = emptyList(),
)

internal data class HabitEditorData(
    val trackers: List<TrackerEntity>,
    val fields: List<TrackerFieldEntity>,
    val values: List<TrackerDailyValueEntity>,
)

internal data class ProgrammeEntryPrescriptionUpdate(val id: String, val sets: Int?, val restSeconds: Int)

data class ExerciseVariantDefaults(
    val exerciseId: String,
    val sourceVariationId: String?,
    val trackingType: String,
    val inputConfig: String,
    val muscleRoles: Map<String, String>,
    val targetSets: Int?,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetRpe: Double?,
    val restSeconds: Int,
)

@HiltViewModel
class EditorViewModel @Inject constructor(private val db: VibeDatabase) : ViewModel() {
    suspend fun exportJson() = com.petermathie.vibecheck.data.DataTransfer.export(db)
    suspend fun importJson(text: String) = com.petermathie.vibecheck.data.DataTransfer.import(db, text)
    suspend fun exportCsv() = com.petermathie.vibecheck.data.DataTransfer.csv(db)
    private val dao = db.editorDao()
    private val trackerStore = TrackerEditorStore(db, ::newId)
    private fun <T> Flow<List<T>>.live() = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val programmes = dao.programmes().live()
    val archivedProgrammes = dao.archivedProgrammes().live()
    val days = dao.days().live()
    val entries = dao.entries().live()
    val exercises = dao.exercises().live()
    val muscles = dao.muscles().live()
    val mappings = dao.mappings().live()
    val variationMappings = dao.variationMappings().live()
    val aliases = dao.aliases().live()
    val variations = dao.variations().live()
    val referenceVideos = dao.referenceVideos().live()
    val workouts = dao.workouts().live()
    val workoutExercises = dao.workoutExercises().live()
    val sets = dao.sets().live()
    val bands = dao.bands().live()
    val setBands = dao.setBands().live()
    val entryDrafts = dao.observeEntryDrafts().live()
    internal val workoutLoggingData: StateFlow<WorkoutLoggingData?> = combine(
        dao.workoutExercises(), dao.sets(), dao.observeEntryDrafts(), dao.variations(), dao.bands(),
    ) { exercises, sets, drafts, variations, bands ->
        WorkoutLoggingData(exercises, sets, drafts, variations, bands)
    }.combine(dao.exercises()) { data, definitions ->
        data.copy(definitions = definitions)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000, replayExpirationMillis = 0), null)
    val trackers = dao.trackers().live()
    val fields = dao.fields().live()
    val values = dao.values().live()
    internal val habitEditorData: StateFlow<HabitEditorData?> = combine(
        dao.trackers(), dao.fields(), dao.values(), ::HabitEditorData,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000, replayExpirationMillis = 0), null)
    val measurements = dao.measurements().live()
    internal val bodyMeasurements: StateFlow<List<BodyMeasurementEntity>?> = dao.measurements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000, replayExpirationMillis = 0), null)
    val error = MutableStateFlow<String?>(null)
    private val writes = Mutex()
    private data class WorkoutWriteTarget(val operation: String, val id: String, val ordinal: Int? = null) {
        val isHistoryTimingIntent: Boolean
            get() = operation == "history-date" || operation == "history-start" || operation == "history-finish"
    }
    private data class WorkoutWriteFailure(val workoutId: String?, val message: String)
    private val unresolvedWorkoutWrites = mutableMapOf<WorkoutWriteTarget, WorkoutWriteFailure>()
    private val rejectedHistoryTimingIntents = mutableMapOf<WorkoutWriteTarget, String>()
    private suspend fun workoutWriteOwner(
        target: WorkoutWriteTarget, workoutId: String?, workoutExerciseId: String?,
    ): String? {
        if (workoutId != null) return workoutId
        if (target.isHistoryTimingIntent || target.operation == "workout" ||
            target.operation == "workout-date" || target.operation == "history-discard"
        ) return target.id
        val exerciseId = workoutExerciseId ?: when (target.operation) {
            "exercise", "exercise-order", "draft", "set-count" -> target.id
            else -> return null
        }
        return dao.workoutExerciseById(exerciseId)?.workoutId
    }
    private fun write(
        onSuccess: () -> Unit = {}, onFailure: () -> Unit = {},
        workoutTarget: WorkoutWriteTarget? = null,
        ownerWorkoutId: String? = null, ownerWorkoutExerciseId: String? = null,
        block: suspend () -> Unit,
    ) =
        viewModelScope.launch {
            writes.withLock {
                var workoutId = ownerWorkoutId
                try {
                    workoutTarget?.let {
                        workoutId = workoutWriteOwner(it, ownerWorkoutId, ownerWorkoutExerciseId)
                    }
                    block()
                    workoutTarget?.let {
                        if (it.isHistoryTimingIntent) rejectedHistoryTimingIntents.remove(it)
                        else unresolvedWorkoutWrites.remove(it)
                    }
                    onSuccess()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error.value = e.message ?: "Could not save"
                    workoutTarget?.let {
                        val message = error.value ?: "Could not save"
                        if (it.isHistoryTimingIntent) rejectedHistoryTimingIntents[it] = message
                        else unresolvedWorkoutWrites[it] = WorkoutWriteFailure(
                            workoutId ?: unresolvedWorkoutWrites[it]?.workoutId, message,
                        )
                    }
                    onFailure()
                }
            }
        }
    private fun metadataWrite(
        onSaved: () -> Unit, onFailure: (String) -> Unit,
        workoutTarget: WorkoutWriteTarget? = null, block: suspend () -> Unit,
    ) = write(onSaved, { onFailure(error.value ?: "Could not save") }, workoutTarget, block = block)
    fun flushWorkoutWrites(onSaved: () -> Unit, onFailure: (String) -> Unit): Job =
        flushWorkoutWrites(onSaved, onFailure, workoutId = null)
    fun flushWorkoutWrites(
        onSaved: () -> Unit, onFailure: (String) -> Unit, workoutId: String? = null,
    ): Job =
        viewModelScope.launch {
            writes.withLock {
                val failure = unresolvedWorkoutWrites.values
                    .firstOrNull { workoutId == null || it.workoutId == null || it.workoutId == workoutId }?.message
                    ?: rejectedHistoryTimingIntents.entries
                        .firstOrNull { workoutId == null || it.key.id == workoutId }?.value
                if (failure == null) onSaved() else onFailure(failure)
            }
        }
    fun cancelHistoryWorkoutDate(workoutId: String): Job =
        cancelHistoryTimingIntent(WorkoutWriteTarget("history-date", workoutId))
    fun cancelHistoryWorkoutTime(workoutId: String, start: Boolean): Job =
        cancelHistoryTimingIntent(WorkoutWriteTarget(if (start) "history-start" else "history-finish", workoutId))
    private fun cancelHistoryTimingIntent(target: WorkoutWriteTarget): Job =
        viewModelScope.launch {
            writes.withLock { rejectedHistoryTimingIntents.remove(target) }
        }
    fun save(row: ProgrammeEntity) = write {
        require(row.colourArgb in 0..0xFFFFFFFFL) { "Invalid programme colour" }
        dao.programme(row)
    }
    fun renameProgramme(
        id: String,
        name: String,
        onSaved: () -> Unit = {},
        onFailure: () -> Unit = {},
    ) = write(onSaved, onFailure) {
        require(name.isNotBlank()) { "Programme name cannot be blank" }
        require(dao.renameProgramme(id, name.trim()) == 1) { "Programme no longer exists" }
    }
    fun setProgrammeColour(id: String, colour: Long) = write {
        require(colour in 0..0xFFFFFFFFL) { "Invalid programme colour" }
        require(dao.setProgrammeColour(id, colour) == 1) { "Programme no longer exists" }
    }
    fun archiveProgramme(
        id: String,
        onSaved: () -> Unit = {},
        onFailure: () -> Unit = {},
    ) = write(onSaved, onFailure) {
        require(dao.setProgrammeArchived(id, true) == 1) { "Programme no longer exists" }
    }
    fun createProgramme(
        row: ProgrammeEntity,
        day: ProgrammeDayEntity,
        onFailure: () -> Unit = {},
        onCreated: () -> Unit = {},
    ) = write(onCreated, onFailure) {
        require(row.name.isNotBlank()) { "Programme name cannot be blank" }
        require(row.colourArgb in 0..0xFFFFFFFFL) { "Invalid programme colour" }
        db.withTransaction {
            dao.programme(row)
            dao.day(day)
        }
    }
    fun ensureProgrammeDay(id: String, onReady: (String) -> Unit) = write {
        val day = db.withTransaction {
            val programme = requireNotNull(dao.programmeById(id)) { "Programme no longer exists" }
            val current = dao.days().first().filter { it.programmeId == id }
            require(current.size <= 1) { "Programme days changed. Choose Add exercise again." }
            current.singleOrNull() ?: ProgrammeDayEntity(
                newId(), id, if (programme.mode == "STRETCHING") "Stretching" else "Workout", 0,
            ).also { dao.day(it) }
        }
        onReady(day.id)
    }
    fun moveProgramme(id: String, delta: Int) = write {
        moveItem(dao.programmes().first(), id, delta) { it.id }?.let { rows ->
            db.withTransaction { rows.forEachIndexed { i,p -> dao.programme(p.copy(position=i)) } }
        }
    }
    fun moveDay(id: String, delta: Int) = write {
        val all=dao.days().first(); val selected=all.find { it.id==id } ?: return@write
        val siblings=all.filter { it.programmeId==selected.programmeId }.sortedBy { it.position }
        moveItem(siblings, id, delta) { it.id }?.let { rows ->
            db.withTransaction { rows.forEachIndexed { i,d -> dao.day(d.copy(position=i)) } }
        }
    }
    fun moveEntry(id: String, delta: Int) = write {
        val all=dao.entries().first(); val selected=all.find { it.id==id } ?: return@write
        val siblings=all.filter { it.programmeDayId==selected.programmeDayId }.sortedBy { it.position }
        moveItem(siblings, id, delta) { it.id }?.let { rows ->
            db.withTransaction { rows.forEachIndexed { i,e -> dao.entry(e.copy(position=i)) } }
        }
    }
    fun moveWorkoutExercise(id: String, delta: Int, onFailure: () -> Unit = {}) =
        write(onFailure = onFailure, workoutTarget = WorkoutWriteTarget("exercise-order", id)) {
        db.withTransaction {
            val selected = requireNotNull(dao.workoutExerciseById(id)) { "Workout exercise no longer exists" }
            moveItem(dao.workoutExercisesForWorkout(selected.workoutId), id, delta) { it.id }?.let { rows ->
                rows.forEachIndexed { index, row -> dao.updateWorkoutExercisePosition(row.id, index) }
            }
        }
    }
    fun save(row: ProgrammeDayEntity, onSaved: () -> Unit = {}) = write(onSaved) { dao.day(row) }
    fun save(row: ProgrammeExerciseEntity, onSaved: () -> Unit = {}) = write(onSaved) { dao.entry(row) }
    fun setProgrammeSetCount(id: String, count: Int?) = write {
        require(count == null || count > 0) { "Sets must be a positive whole number" }
        require(dao.setProgrammeSetCount(id, count) == 1) { "Programme exercise no longer exists" }
    }
    fun setProgrammeRestSeconds(id: String, seconds: Int) = write {
        require(seconds >= 0) { "Rest seconds must be zero or a positive whole number" }
        require(dao.setProgrammeRestSeconds(id, seconds) == 1) { "Programme exercise no longer exists" }
    }
    fun prepareProgrammePrescription(
        id: String, onReady: (ProgrammeExerciseEntity) -> Unit, onFailure: () -> Unit,
    ) = write(onFailure = onFailure) {
        onReady(requireNotNull(dao.programmeEntryById(id)) { "Programme exercise no longer exists" })
    }
    fun saveProgrammePrescription(
        row: ProgrammeExerciseEntity, onSaved: () -> Unit, onFailure: () -> Unit,
    ) = write(onSaved, onFailure) {
        require(row.targetSets == null || row.targetSets > 0) { "Sets must be a positive whole number" }
        require(row.restSeconds >= 0) { "Rest seconds must be zero or a positive whole number" }
        db.withTransaction {
            val current = requireNotNull(dao.programmeEntryById(row.id)) { "Programme exercise no longer exists" }
            require(current.programmeDayId == row.programmeDayId) { "Programme exercise changed. Open the programme again." }
            dao.entry(current.copy(
                targetSets = row.targetSets, restSeconds = row.restSeconds,
                targetRepsMin = row.targetRepsMin, targetRepsMax = row.targetRepsMax,
            ))
        }
    }
    internal fun saveProgrammeEditor(
        id: String,
        name: String,
        prescriptions: List<ProgrammeEntryPrescriptionUpdate>,
        onSaved: () -> Unit,
        onFailure: () -> Unit,
    ) = write(onSaved, onFailure) {
        require(name.isNotBlank()) { "Programme name cannot be blank" }
        db.withTransaction {
            require(dao.renameProgramme(id, name.trim()) == 1) { "Programme no longer exists" }
            val dayIds = dao.days().first().filter { it.programmeId == id }.map { it.id }.toSet()
            prescriptions.forEach { update ->
                require(update.sets == null || update.sets > 0) { "Sets must be a positive whole number" }
                require(update.restSeconds >= 0) { "Rest seconds must be zero or a positive whole number" }
                val entry = requireNotNull(dao.programmeEntryById(update.id)) { "Programme exercise no longer exists" }
                require(entry.programmeDayId in dayIds) { "Programme exercise changed. Open the programme again." }
                require(dao.setProgrammeSetCount(update.id, update.sets) == 1) { "Programme exercise no longer exists" }
                require(dao.setProgrammeRestSeconds(update.id, update.restSeconds) == 1) { "Programme exercise no longer exists" }
            }
        }
    }
    fun save(row: WorkoutEntity) = write(workoutTarget = WorkoutWriteTarget("workout", row.id)) { dao.workout(row) }
    fun changeWorkoutDate(row: WorkoutEntity, end: Long) = write(workoutTarget = WorkoutWriteTarget("workout-date", row.id)) {
        db.withTransaction {
            val current = requireNotNull(dao.workoutById(row.id)) { "Workout no longer exists" }
            val delta = Math.subtractExact(end, current.finishedAt ?: current.startedAt)
            val startedAt = Math.addExact(current.startedAt, delta)
            require(startedAt >= 0 && end >= startedAt) { "Invalid workout date" }
            val shiftedSets = dao.setsForWorkout(row.id).map { it.id to Math.addExact(it.loggedAt, delta) }
            require(shiftedSets.all { it.second >= 0 }) { "Set time cannot be negative" }
            require(dao.updateWorkoutTiming(row.id, startedAt, end) == 1) { "Workout no longer exists" }
            shiftedSets.forEach { (id, timestamp) -> dao.updateSetLoggedAt(id, timestamp) }
        }
    }
    fun changeHistoryWorkoutDate(
        workoutId: String, date: LocalDate, onSaved: () -> Unit = {}, onFailure: (String) -> Unit = {},
    ) = metadataWrite(onSaved, onFailure, WorkoutWriteTarget("history-date", workoutId)) {
        db.withTransaction {
            val current = historyWorkout(workoutId)
            val change = historyWorkoutDateChange(current.startedAt, current.finishedAt, date, ZoneId.systemDefault())
            val shiftedSets = dao.setsForWorkout(workoutId).map {
                it.id to Math.addExact(it.loggedAt, change.setDelta)
            }
            require(shiftedSets.all { it.second >= 0 }) { "Set time cannot be negative" }
            require(dao.updateWorkoutTiming(workoutId, change.startedAt, change.finishedAt) == 1) { "Workout no longer exists" }
            shiftedSets.forEach { (id, timestamp) -> dao.updateSetLoggedAt(id, timestamp) }
        }
    }
    fun changeHistoryWorkoutTime(
        workoutId: String, start: Boolean, time: LocalTime, onSaved: () -> Unit = {}, onFailure: (String) -> Unit = {},
    ) = metadataWrite(onSaved, onFailure, WorkoutWriteTarget(if (start) "history-start" else "history-finish", workoutId)) {
        db.withTransaction {
            val current = historyWorkout(workoutId)
            val change = historyWorkoutTimeChange(current.startedAt, current.finishedAt, start, time, ZoneId.systemDefault())
            require(dao.updateWorkoutTiming(workoutId, change.startedAt, change.finishedAt) == 1) { "Workout no longer exists" }
        }
    }
    fun discardHistoryWorkout(
        id: String, onSaved: () -> Unit = {}, onFailure: (String) -> Unit = {},
    ) = metadataWrite(onSaved, onFailure, WorkoutWriteTarget("history-discard", id)) {
        db.withTransaction {
            historyWorkout(id)
            require(dao.discardHistoryWorkout(id) == 1) { "Workout is no longer finished history" }
        }
    }
    private suspend fun historyWorkout(id: String): WorkoutEntity {
        val current = requireNotNull(dao.workoutById(id)) { "Workout no longer exists" }
        require(current.status == "FINISHED") { "Only finished history workouts can be edited here" }
        return current
    }
    fun save(row: WorkoutExerciseEntity) = write(
        workoutTarget = WorkoutWriteTarget("exercise", row.id), ownerWorkoutId = row.workoutId,
    ) {
        db.withTransaction {
            val definition=dao.effectiveExerciseById(row.actualExerciseId)
            val changed=row.exerciseName.isBlank()
            val current = dao.workoutExerciseById(row.id)
            dao.workoutExercise(
                (if(changed) row.copy(
                    exerciseName=definition?.canonicalName.orEmpty(),
                    trackingType=definition?.trackingType.orEmpty(),
                    inputConfig=definition?.inputConfig.orEmpty(),
                ) else row).copy(
                    position = current?.position ?: row.position,
                    targetSets = if (current == null) row.targetSets else current.targetSets,
                ),
            )
            if(changed) {
                dao.clearWorkoutMuscles(row.id)
                dao.workoutMuscles(dao.muscleMappings(row.actualExerciseId).map { WorkoutMuscleEntity(row.id,it.muscleId,it.role) })
            }
        }
    }
    fun save(row: TrackerEntity) = write { trackerStore.saveTracker(row) }
    fun deleteTrackerIfEmpty(id: String, onSaved: () -> Unit = {}, onFailure: () -> Unit = {}) =
        write(onSaved, onFailure) { trackerStore.deleteTrackerIfEmpty(id) }
    fun createTracker(row: TrackerEntity) = write { trackerStore.createTracker(row) }
    fun saveHabitEditor(
        row: TrackerEntity,
        fields: List<TrackerFieldEntity>,
        creating: Boolean,
        onSaved: () -> Unit,
        onFailure: () -> Unit,
    ) = write(onSaved, onFailure) { trackerStore.saveEditor(row, fields, creating) }
    fun moveTracker(id: String, delta: Int) = write { trackerStore.moveTracker(id, delta) }
    fun save(row: TrackerFieldEntity) = write { trackerStore.saveField(row) }
    fun save(row: TrackerDailyValueEntity, onSaved: () -> Unit = {}) = write(onSaved) { trackerStore.saveValue(row) }
    fun save(row: BodyMeasurementEntity) = write { dao.measurement(row) }
    internal fun setBodyweight(
        day: LocalDate,
        value: Double?,
        displayUnit: BodyweightUnit,
        zone: ZoneId = ZoneId.systemDefault(),
        onSaved: () -> Unit = {},
        onFailure: () -> Unit = {},
    ) = write(onSaved, onFailure) {
        require(value == null || value.isFinite() && value > 0.0) { "Enter a finite bodyweight greater than zero" }
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        db.withTransaction {
            if (value == null) {
                dao.clearBodyweightsForDay(start, end)
            } else {
                val existing = dao.bodyweightsForDay(start, end).firstOrNull()
                val storageUnit = existing?.let { BodyweightUnit.stored(it.unit) } ?: BodyweightUnit.KILOGRAMS
                val storedValue = convertBodyweight(value, displayUnit, storageUnit)
                require(storedValue > 0.0) { "Bodyweight is outside the supported numeric range" }
                dao.measurement(
                    existing?.copy(value = storedValue, isWeightCleared = false)
                        ?: BodyMeasurementEntity(
                            newId(), day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
                            "Bodyweight", storedValue, storageUnit.symbol, "", false,
                        ),
                )
            }
        }
    }
    fun save(row: ExerciseVariationEntity) = write {
        db.withTransaction {
            val current = dao.variationOriginalById(row.id)
            requireNotNull(dao.exerciseById(row.exerciseId)) { "Exercise no longer exists" }
            require(current == null || current.exerciseId == row.exerciseId) { "Variant ownership changed" }
            if (current == null) {
                require(!row.isSeeded) { "Cannot create a built-in variant" }
                require(row.name.isNotBlank()) { "Variant name cannot be blank" }
                dao.variation(row)
                storeItemMuscles(row.exerciseId, row.id, dao.muscleMappings(row.exerciseId).associate { it.muscleId to it.role })
            } else {
                dao.variation(current.copy(inputConfig = row.inputConfig))
                storeItemName(row.exerciseId, row.id, row.name)
            }
        }
    }
    fun renameExerciseItem(
        exerciseId: String, variationId: String? = null, name: String,
        onSaved: () -> Unit = {}, onFailure: (String) -> Unit = {},
    ) = metadataWrite(onSaved, onFailure) {
        db.withTransaction { storeItemName(exerciseId, variationId, name) }
    }
    fun saveExerciseItemMuscles(
        exerciseId: String, variationId: String? = null, muscleRoles: Map<String, String>,
        onSaved: () -> Unit = {}, onFailure: (String) -> Unit = {},
    ) = metadataWrite(onSaved, onFailure) {
        db.withTransaction { storeItemMuscles(exerciseId, variationId, muscleRoles) }
    }
    fun saveExerciseItemConfig(
        exerciseId: String, variationId: String? = null, inputConfig: String,
        onSaved: () -> Unit = {}, onFailure: (String) -> Unit = {},
    ) = metadataWrite(onSaved, onFailure) {
        db.withTransaction {
            validateItemOwner(exerciseId, variationId)
            val exercise = requireNotNull(dao.exerciseById(exerciseId))
            if (variationId == null) {
                ExerciseInputConfig.decode(inputConfig, exercise.trackingType, exerciseId)
                dao.exercise(exercise.copy(inputConfig = inputConfig))
            } else {
                val variation = requireNotNull(dao.variationOriginalById(variationId))
                ExerciseInputConfig.decode(inputConfig, variation.trackingType, exerciseId)
                dao.variation(variation.copy(inputConfig = inputConfig))
            }
        }
    }
    fun prepareExerciseVariant(
        exerciseId: String, sourceVariationId: String? = null,
        onReady: (ExerciseVariantDefaults) -> Unit, onFailure: (String) -> Unit = {},
    ) = metadataWrite({}, onFailure) {
        val defaults = db.withTransaction { variantDefaults(exerciseId, sourceVariationId) }
        onReady(defaults)
    }
    fun createExerciseVariant(
        exerciseId: String, sourceVariationId: String? = null, name: String,
        defaults: ExerciseVariantDefaults? = null,
        onCreated: (String) -> Unit, onFailure: (String) -> Unit = {},
    ) = metadataWrite({}, onFailure) {
        val id = db.withTransaction {
            validateItemOwner(exerciseId, sourceVariationId)
            require(name.isNotBlank()) { "Variant name cannot be blank" }
            val selected = defaults ?: variantDefaults(exerciseId, sourceVariationId)
            require(selected.exerciseId == exerciseId && selected.sourceVariationId == sourceVariationId) { "Variant source changed" }
            val config = ExerciseInputConfig.decode(selected.inputConfig, selected.trackingType, exerciseId)
            require(config.loggingMetricsError() == null) { config.loggingMetricsError().orEmpty() }
            val rank = (dao.variations().first().filter { it.exerciseId == exerciseId }.maxOfOrNull { it.progressionRank } ?: -1) + 1
            require(rank >= 0) { "Too many variants" }
            val id = newId()
            dao.variation(ExerciseVariationEntity(
                id, exerciseId, name.trim(), rank, false, selected.trackingType, config.encode(),
                selected.targetSets, selected.targetRepsMin, selected.targetRepsMax, selected.targetRpe, selected.restSeconds,
            ))
            storeItemMuscles(exerciseId, id, selected.muscleRoles)
            id
        }
        onCreated(id)
    }
    private suspend fun validateItemOwner(exerciseId: String, variationId: String?) {
        requireNotNull(dao.exerciseById(exerciseId)) { "Exercise no longer exists" }
        if (variationId != null) {
            require(dao.variationOriginalById(variationId)?.exerciseId == exerciseId) { "Variant no longer belongs to this exercise" }
        }
    }
    private suspend fun storeItemName(exerciseId: String, variationId: String?, name: String) {
        validateItemOwner(exerciseId, variationId)
        require(name.isNotBlank()) { "Name cannot be blank" }
        if (variationId == null) {
            val current = dao.exerciseOverrideById(exerciseId) ?: ExerciseOverrideEntity(exerciseId)
            dao.exerciseOverride(current.copy(name = name.trim()))
        } else {
            val current = dao.variationOverrideById(variationId) ?: VariationOverrideEntity(variationId)
            dao.variationOverride(current.copy(name = name.trim()))
        }
    }
    private suspend fun storeItemMuscles(exerciseId: String, variationId: String?, roles: Map<String, String>) {
        validateItemOwner(exerciseId, variationId)
        val known = dao.muscles().first().map { it.id }.toSet()
        require(roles.keys.all { it in known }) { "Unknown muscle" }
        require(roles.values.all { it in setOf("PRIMARY", "SECONDARY") }) { "Invalid muscle role" }
        if (variationId == null) {
            val current = dao.exerciseOverrideById(exerciseId) ?: ExerciseOverrideEntity(exerciseId)
            dao.exerciseOverride(current.copy(musclesPresent = true))
            dao.clearMuscleOverrides(exerciseId)
            dao.muscleOverrides(roles.map { ExerciseMuscleOverrideEntity(exerciseId, it.key, it.value) })
        } else {
            val current = dao.variationOverrideById(variationId) ?: VariationOverrideEntity(variationId)
            dao.variationOverride(current.copy(musclesPresent = true))
            dao.clearVariationMuscles(variationId)
            dao.variationMuscles(roles.map { VariationMuscleEntity(variationId, it.key, it.value) })
        }
    }
    private suspend fun variantDefaults(exerciseId: String, variationId: String?): ExerciseVariantDefaults {
        validateItemOwner(exerciseId, variationId)
        val exercise = requireNotNull(dao.effectiveExerciseById(exerciseId))
        val base = ExerciseInputConfig.decode(exercise.inputConfig, exercise.trackingType, exerciseId)
        val variant = variationId?.let { requireNotNull(dao.variationById(it)) }
        val config = variant?.let {
            ExerciseInputConfig.decode(
                it.inputConfig.ifBlank { exercise.inputConfig }, it.trackingType, exerciseId,
                defaultManyTuts = base.manyTuts, defaultMeasurementOrder = base.measurementOrder,
            )
        } ?: base
        val roles = if (variationId == null) dao.muscleMappings(exerciseId).associate { it.muscleId to it.role }
            else dao.variationMuscleMappings(variationId).associate { it.muscleId to it.role }
        return ExerciseVariantDefaults(
            exerciseId, variationId, variant?.trackingType ?: exercise.trackingType, config.encode(), roles,
            variant?.targetSets ?: exercise.targetSets, variant?.targetRepsMin ?: exercise.targetRepsMin,
            variant?.targetRepsMax ?: exercise.targetRepsMax, variant?.targetRpe ?: exercise.targetRpe,
            variant?.restSeconds ?: exercise.restSeconds,
        )
    }
    fun saveBand(row: BandEntity, onSaved: () -> Unit, onFailure: () -> Unit) = write(onSaved, onFailure) {
        require(!isSeededBandId(row.id)) { "Built-in band definitions cannot be changed" }
        require(row.name.isNotBlank()) { "Enter a band name" }
        require(row.widthCentimetres.isFinite() && row.widthCentimetres > 0) { "Band width must be positive" }
        require(row.colourArgb in 0..0xFFFFFFFFL) { "Invalid band colour" }
        db.withTransaction {
            require(dao.bandById(row.id) != null || dao.bandCount() < com.petermathie.vibecheck.domain.programme.MAX_RESISTANCE_BANDS) {
                "At most eight resistance bands can be configured"
            }
            dao.band(row)
        }
    }
    fun attachReferenceVideo(
        context: Context, exerciseId: String, uri: Uri, displayName: String,
        variationId: String? = null, onSaved: () -> Unit = {}, onFailure: (String) -> Unit = {},
    ) = metadataWrite(onSaved, onFailure) {
        val id = newId()
        val directory = File(context.filesDir, "exercise-reference-videos")
        check(directory.exists() || directory.mkdirs()) { "Could not create reference video storage" }
        val file = File(directory, "$id.mp4")
        try {
            requireNotNull(context.contentResolver.openInputStream(uri)) { "Could not open selected video" }
                .use { input -> file.outputStream().use(input::copyTo) }
            db.withTransaction {
                validateItemOwner(exerciseId, variationId)
                dao.referenceVideo(
                    ExerciseReferenceVideoEntity(id, exerciseId, displayName.ifBlank { "Reference video" }, file.name, System.currentTimeMillis(), variationId),
                )
            }
        } catch (error: Exception) {
            if (file.exists() && !file.delete()) error.addSuppressed(IllegalStateException("Could not remove incomplete video"))
            throw error
        }
    }
    fun deleteReferenceVideo(
        context: Context, row: ExerciseReferenceVideoEntity,
        onSaved: () -> Unit = {}, onFailure: (String) -> Unit = {},
    ) = metadataWrite(onSaved, onFailure) {
        db.withTransaction {
            val current = requireNotNull(dao.referenceVideoById(row.id)) { "Reference video no longer exists" }
            require(current.exerciseId == row.exerciseId && current.variationId == row.variationId && current.fileName == row.fileName) { "Reference video target changed" }
            require(File(current.fileName).name == current.fileName) { "Invalid reference video file" }
            val file = File(File(context.filesDir, "exercise-reference-videos"), current.fileName)
            if (file.exists()) check(file.delete()) { "Could not delete ${current.displayName}" }
            dao.deleteReferenceVideo(current.id)
        }
    }
    suspend fun entryDraft(workoutExerciseId: String) = dao.entryDraft(workoutExerciseId)
    suspend fun entryDrafts(workoutExerciseId: String) = dao.entryDrafts(workoutExerciseId)
    suspend fun bandsForExercise(workoutExerciseId: String) = dao.bandsForExercise(workoutExerciseId)
    fun saveEntryDraft(row: WorkoutEntryDraftEntity): Job {
        val target = WorkoutWriteTarget("draft", row.workoutExerciseId, row.ordinal)
        var persisted = false
        var workoutId: String? = null
        return write(
            onSuccess = { if (persisted) unresolvedWorkoutWrites.remove(target) },
            onFailure = {
                unresolvedWorkoutWrites[target] = WorkoutWriteFailure(
                    workoutId ?: unresolvedWorkoutWrites[target]?.workoutId, error.value ?: "Could not save",
                )
            },
        ) {
            workoutId = workoutWriteOwner(target, null, null)
            db.withTransaction {
                if (!dao.canPersistEntryDraft(row.workoutExerciseId, row.setId, row.ordinal)) return@withTransaction
                if (
                    !dao.hasSet(row.setId) &&
                    !row.detailsOpen &&
                    row.variationId == null &&
                    row.performance.isBlank() &&
                    row.rpe.isBlank() &&
                    row.leftValue.isBlank() &&
                    row.rightValue.isBlank() &&
                    row.addedWeight.isBlank() &&
                    row.assistance.isBlank() &&
                    row.romValue.isBlank() &&
                    row.distanceValue.isBlank() &&
                    row.variantDraftCache == "{}" &&
                    row.timeHeld.isBlank() &&
                    row.timeUnderTension.isBlank() &&
                    row.tutEntries.isBlank() &&
                    row.bandIds == "[]"
                ) {
                    dao.deleteEntryDraft(row.workoutExerciseId, row.ordinal)
                } else {
                    dao.entryDraft(row)
                }
                persisted = true
            }
        }
    }
    fun discardEntryDraft(workoutExerciseId: String, ordinal: Int) =
        write(workoutTarget = WorkoutWriteTarget("draft", workoutExerciseId, ordinal)) { dao.deleteEntryDraft(workoutExerciseId, ordinal) }
    fun moveVariation(id: String, delta: Int) = write {
        db.withTransaction {
            val all=dao.variations().first()
            val selected=all.find { it.id==id && !it.isSeeded } ?: return@withTransaction
            val siblings=all.filter { it.exerciseId==selected.exerciseId && !it.isSeeded }.sortedBy { it.progressionRank }
            val start=(all.filter { it.exerciseId==selected.exerciseId && it.isSeeded }.maxOfOrNull { it.progressionRank } ?: -1)+1
            moveItem(siblings, id, delta) { it.id }?.let { rows ->
                rows.forEachIndexed { i,v ->
                    val original = requireNotNull(dao.variationOriginalById(v.id))
                    dao.variation(original.copy(progressionRank=start+i))
                }
            }
        }
    }
    fun removeProgramme(
        id: String,
        onRemoved: () -> Unit = {},
        onFailure: () -> Unit = {},
    ) = write(onRemoved, onFailure) {
        db.withTransaction {
            requireNotNull(dao.programmeById(id)) { "Programme no longer exists" }
            dao.deleteProgramme(id)
        }
    }
    fun removeDay(id: String) = write { dao.deleteDay(id) }
    fun removeEntry(id: String, onRemoved: () -> Unit = {}, onFailure: () -> Unit = {}) = write(onFailure = onFailure) {
        dao.deleteEntry(id)
        onRemoved()
    }
    fun removeSet(id: String) = write { dao.deleteSet(id) }
    fun removeWorkoutSet(id: String, ordinal: Int, remaining: Int, onRemoved: () -> Unit) =
        write(onRemoved) { dao.deleteWorkoutSet(id, ordinal, remaining) }
    fun setWorkoutSetCount(id: String, count: Int) =
        write(workoutTarget = WorkoutWriteTarget("set-count", id)) { dao.setWorkoutSetCount(id, count) }
    fun removeWorkoutExercise(id: String, onRemoved: () -> Unit = {}) =
        write(onRemoved) { dao.deleteWorkoutExercise(id) }
    fun removeWorkout(id: String, onRemoved: () -> Unit = {}) = write(onRemoved) { dao.deleteWorkout(id) }
    fun clearValue(id: String, day: Long) = write { trackerStore.clearValue(id, day) }
    fun removeMeasurement(id: String) = write { dao.deleteMeasurement(id) }
    fun saveSet(row: WorkoutSetEntity, bandIds: List<String>) = write(
        workoutTarget = WorkoutWriteTarget("set", row.id), ownerWorkoutExerciseId = row.workoutExerciseId,
    ) {
        dao.saveSetWithSnapshots(row, bandIds, consumeDraft = false)
    }
    fun submitEntryDraft(
        row: WorkoutSetEntity,
        bandIds: List<String>,
        inputConfigOverride: String? = null,
        entryDraft: WorkoutEntryDraftEntity? = null,
        onSaved: () -> Unit,
    ) = write(
        workoutTarget = WorkoutWriteTarget("set", row.id), ownerWorkoutExerciseId = row.workoutExerciseId,
    ) {
        dao.saveSetWithSnapshots(row, bandIds, consumeDraft = true, inputConfigOverride = inputConfigOverride, entryDraft = entryDraft)
        entryDraft?.let { unresolvedWorkoutWrites.remove(WorkoutWriteTarget("draft", it.workoutExerciseId, it.ordinal)) }
        onSaved()
    }
    fun duplicate(
        programme: ProgrammeEntity,
        onDuplicated: () -> Unit = {},
        onFailure: () -> Unit = {},
    ) = write(onDuplicated, onFailure) {
        db.withTransaction {
            val current = requireNotNull(dao.programmeById(programme.id)) { "Programme no longer exists" }
            val sourceDays = dao.days().first().filter { it.programmeId == current.id }
            val sourceEntries = dao.entries().first()
            val id = newId()
            dao.programme(current.copy(id = id, name = current.name + " (copy)", isDemo = false))
            sourceDays.forEach { day ->
                val dayId = newId()
                dao.day(day.copy(id = dayId, programmeId = id))
                sourceEntries.filter { it.programmeDayId == day.id }.forEach {
                    dao.entry(it.copy(id = newId(), programmeDayId = dayId))
                }
            }
        }
    }
    fun saveExercise(
        row: ExerciseEntity, names: List<String>, muscleRoles: Map<String, String>,
        onSaved: () -> Unit = {}, onFailure: (String) -> Unit = {},
    ) = metadataWrite(onSaved, onFailure) {
        db.withTransaction {
            val current = dao.exerciseById(row.id)
            require(row.canonicalName.isNotBlank()) { "Exercise name cannot be blank" }
            if (current == null) {
                require(row.isCustom && !row.id.startsWith("core:") && !row.id.startsWith("free:")) { "Cannot create a built-in exercise" }
                dao.exercise(row)
            } else if (current.isCustom) {
                dao.exercise(row.copy(canonicalName = current.canonicalName, isCustom = true))
            } else {
                dao.exercise(current.copy(inputConfig = row.inputConfig))
            }
            if (current == null || current.isCustom) {
                dao.clearAliases(row.id)
                db.catalogueDao().insertAliases(names.filter { it.isNotBlank() }.distinct().map { ExerciseAliasEntity(row.id, it.trim(), it.trim().lowercase()) })
            }
            storeItemName(row.id, null, row.canonicalName)
            storeItemMuscles(row.id, null, muscleRoles)
        }
    }
    fun saveExerciseSettings(row: ExerciseEntity) = write {
        db.withTransaction {
            val current = requireNotNull(dao.exerciseById(row.id)) { "Exercise no longer exists" }
            ExerciseInputConfig.decode(row.inputConfig, current.trackingType, current.id)
            dao.exercise(current.copy(inputConfig = row.inputConfig))
        }
    }
}

package com.petermathie.vibecheck.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.data.TrackerEditorStore
import com.petermathie.vibecheck.domain.editor.moveItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import android.content.Context
import android.net.Uri
import java.io.File

fun newId(): String = UUID.randomUUID().toString()

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
    val aliases = dao.aliases().live()
    val variations = dao.variations().live()
    val referenceVideos = dao.referenceVideos().live()
    val workouts = dao.workouts().live()
    val workoutExercises = dao.workoutExercises().live()
    val sets = dao.sets().live()
    val bands = dao.bands().live()
    val setBands = dao.setBands().live()
    val entryDrafts = dao.observeEntryDrafts().live()
    val trackers = dao.trackers().live()
    val fields = dao.fields().live()
    val values = dao.values().live()
    val measurements = dao.measurements().live()
    val error = MutableStateFlow<String?>(null)
    private val writes = Mutex()
    private fun write(onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            writes.withLock {
                try {
                    block()
                    onSuccess()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error.value = e.message ?: "Could not save"
                }
            }
        }
    }
    fun save(row: ProgrammeEntity) = write { dao.programme(row) }
    fun createProgramme(row: ProgrammeEntity, day: ProgrammeDayEntity, onCreated: () -> Unit = {}) = write(onCreated) {
        db.withTransaction {
            dao.programme(row)
            dao.day(day)
        }
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
    fun save(row: ProgrammeDayEntity, onSaved: () -> Unit = {}) = write(onSaved) { dao.day(row) }
    fun save(row: ProgrammeExerciseEntity, onSaved: () -> Unit = {}) = write(onSaved) { dao.entry(row) }
    fun save(row: WorkoutEntity) = write { dao.workout(row) }
    fun changeWorkoutDate(row: WorkoutEntity, end: Long) = write {
        val delta=end-(row.finishedAt ?: row.startedAt)
        val ids=dao.workoutExercises().first().filter { it.workoutId==row.id }.map { it.id }.toSet()
        val sets=dao.sets().first().filter { it.workoutExerciseId in ids }
        db.withTransaction {
            dao.workout(row.copy(startedAt=row.startedAt+delta,finishedAt=end))
            sets.forEach { dao.set(it.copy(loggedAt=it.loggedAt+delta,updatedAt=System.currentTimeMillis())) }
        }
    }
    fun save(row: WorkoutExerciseEntity) = write {
        db.withTransaction {
            val definition=dao.exerciseById(row.actualExerciseId)
            val changed=row.exerciseName.isBlank()
            dao.workoutExercise(
                if(changed) row.copy(
                    exerciseName=definition?.canonicalName.orEmpty(),
                    trackingType=definition?.trackingType.orEmpty(),
                    inputConfig=definition?.inputConfig.orEmpty(),
                ) else row,
            )
            if(changed) {
                dao.clearWorkoutMuscles(row.id)
                dao.workoutMuscles(dao.muscleMappings(row.actualExerciseId).map { WorkoutMuscleEntity(row.id,it.muscleId,it.role) })
            }
        }
    }
    fun save(row: TrackerEntity) = write { trackerStore.saveTracker(row) }
    fun deleteTrackerIfEmpty(id: String) = write { trackerStore.deleteTrackerIfEmpty(id) }
    fun createTracker(row: TrackerEntity) = write { trackerStore.createTracker(row) }
    fun moveTracker(id: String, delta: Int) = write { trackerStore.moveTracker(id, delta) }
    fun save(row: TrackerFieldEntity) = write { trackerStore.saveField(row) }
    fun moveTrackerField(id: String, delta: Int) = write { trackerStore.moveField(id, delta) }
    fun save(row: TrackerDailyValueEntity, onSaved: () -> Unit = {}) = write(onSaved) { trackerStore.saveValue(row) }
    fun save(row: BodyMeasurementEntity) = write { dao.measurement(row) }
    fun save(row: ExerciseVariationEntity) = write { dao.variation(row) }
    fun attachReferenceVideo(context: Context, exerciseId: String, uri: Uri, displayName: String) = write {
        val id = newId()
        val directory = File(context.filesDir, "exercise-reference-videos")
        check(directory.exists() || directory.mkdirs()) { "Could not create reference video storage" }
        val file = File(directory, "$id.mp4")
        try {
            requireNotNull(context.contentResolver.openInputStream(uri)) { "Could not open selected video" }
                .use { input -> file.outputStream().use(input::copyTo) }
            dao.referenceVideo(
                ExerciseReferenceVideoEntity(id, exerciseId, displayName.ifBlank { "Reference video" }, file.name, System.currentTimeMillis()),
            )
        } catch (error: Exception) {
            if (file.exists() && !file.delete()) error.addSuppressed(IllegalStateException("Could not remove incomplete video"))
            throw error
        }
    }
    fun deleteReferenceVideo(context: Context, row: ExerciseReferenceVideoEntity) = write {
        val file = File(File(context.filesDir, "exercise-reference-videos"), row.fileName)
        if (file.exists()) check(file.delete()) { "Could not delete ${row.displayName}" }
        dao.deleteReferenceVideo(row.id)
    }
    suspend fun entryDraft(workoutExerciseId: String) = dao.entryDraft(workoutExerciseId)
    suspend fun entryDrafts(workoutExerciseId: String) = dao.entryDrafts(workoutExerciseId)
    fun saveEntryDraft(row: WorkoutEntryDraftEntity) = write {
        db.withTransaction {
            if (!dao.belongsToDraftWorkout(row.workoutExerciseId)) return@withTransaction
            dao.deleteSet(row.setId)
            if (
                !row.detailsOpen &&
                row.variationId == null &&
                row.performance.isBlank() &&
                row.rpe.isBlank() &&
                row.leftValue.isBlank() &&
                row.rightValue.isBlank() &&
                row.addedWeight.isBlank() &&
                row.assistance.isBlank() &&
                row.romValue.isBlank() &&
                row.timeHeld.isBlank() &&
                row.timeUnderTension.isBlank() &&
                row.bandIds == "[]"
            ) {
                dao.deleteEntryDraft(row.workoutExerciseId, row.ordinal)
            } else {
                dao.entryDraft(row)
            }
        }
    }
    fun discardEntryDraft(workoutExerciseId: String, ordinal: Int) = write { dao.deleteEntryDraft(workoutExerciseId, ordinal) }
    fun moveVariation(id: String, delta: Int) = write {
        val all=dao.variations().first()
        val selected=all.find { it.id==id && !it.isSeeded } ?: return@write
        val siblings=all.filter { it.exerciseId==selected.exerciseId && !it.isSeeded }.sortedBy { it.progressionRank }
        val start=(all.filter { it.exerciseId==selected.exerciseId && it.isSeeded }.maxOfOrNull { it.progressionRank } ?: -1)+1
        moveItem(siblings, id, delta) { it.id }?.let { rows ->
            db.withTransaction { rows.forEachIndexed { i,v -> dao.variation(v.copy(progressionRank=start+i)) } }
        }
    }
    fun removeProgramme(id: String) = write { dao.deleteProgramme(id) }
    fun removeDay(id: String) = write { dao.deleteDay(id) }
    fun removeEntry(id: String) = write { dao.deleteEntry(id) }
    fun removeSet(id: String) = write { dao.deleteSet(id) }
    fun removeWorkout(id: String, onRemoved: () -> Unit = {}) = write(onRemoved) { dao.deleteWorkout(id) }
    fun clearValue(id: String, day: Long) = write { trackerStore.clearValue(id, day) }
    fun removeMeasurement(id: String) = write { dao.deleteMeasurement(id) }
    fun saveSet(row: WorkoutSetEntity, bandIds: List<String>) = write {
        dao.saveSetWithSnapshots(row, bandIds, consumeDraft = false)
    }
    fun submitEntryDraft(row: WorkoutSetEntity, bandIds: List<String>, onSaved: () -> Unit) = write {
        dao.saveSetWithSnapshots(row, bandIds, consumeDraft = true)
        onSaved()
    }
    fun duplicate(programme: ProgrammeEntity) = write {
        val sourceDays = dao.days().first().filter { it.programmeId == programme.id }
        val sourceEntries = dao.entries().first()
        db.withTransaction {
            val id = newId()
            dao.programme(programme.copy(id = id, name = programme.name + " (copy)", isDemo = false))
            sourceDays.forEach { day ->
                val dayId = newId()
                dao.day(day.copy(id = dayId, programmeId = id))
                sourceEntries.filter { it.programmeDayId == day.id }.forEach {
                    dao.entry(it.copy(id = newId(), programmeDayId = dayId))
                }
            }
        }
    }
    fun saveExercise(row: ExerciseEntity, names: List<String>, muscleRoles: Map<String, String>) = write {
        require(row.isCustom) { "Duplicate seeded exercises before editing" }
        db.withTransaction {
            dao.exercise(row)
            dao.clearAliases(row.id)
            dao.clearMappings(row.id)
            db.catalogueDao().insertAliases(names.filter { it.isNotBlank() }.distinct().map { ExerciseAliasEntity(row.id, it.trim(), it.trim().lowercase()) })
            db.catalogueDao().insertExerciseMuscles(muscleRoles.map { ExerciseMuscleEntity(row.id, it.key, it.value) })
        }
    }
    fun saveExerciseSettings(row: ExerciseEntity) = write { dao.exercise(row) }
}

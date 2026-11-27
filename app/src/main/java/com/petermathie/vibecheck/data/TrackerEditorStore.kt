package com.petermathie.vibecheck.data

import androidx.room.withTransaction
import com.petermathie.vibecheck.data.local.TrackerDailyValueEntity
import com.petermathie.vibecheck.data.local.TrackerEntity
import com.petermathie.vibecheck.data.local.TrackerFieldEntity
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.domain.editor.moveItem
import com.petermathie.vibecheck.domain.tracker.HabitFieldForm
import com.petermathie.vibecheck.domain.tracker.habitMeasurement
import com.petermathie.vibecheck.domain.tracker.habitNumericError
import java.util.UUID
import kotlinx.coroutines.flow.first

class TrackerEditorStore(
    private val db: VibeDatabase,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val dao = db.editorDao()

    suspend fun saveTracker(row: TrackerEntity) = dao.tracker(row)

    suspend fun deleteTrackerIfEmpty(id: String) {
        db.withTransaction {
            require(dao.trackerValueCount(id) == 0) { "A habit with entries cannot be permanently deleted" }
            dao.deleteTracker(id)
        }
    }

    suspend fun createTracker(row: TrackerEntity) {
        db.withTransaction {
            val position = dao.trackersSnapshot().maxOfOrNull { it.position }?.plus(1) ?: 0
            dao.tracker(row.copy(position = position))
            dao.field(
                TrackerFieldEntity(
                    idFactory(),
                    row.id,
                    "Done",
                    "BOOLEAN",
                    null,
                    null,
                    null,
                    0,
                ),
            )
        }
    }

    suspend fun saveField(row: TrackerFieldEntity) {
        db.withTransaction {
            val fields = dao.fieldsForTracker(row.trackerId)
            val first = habitMeasurement(fields)
            require(first == null || first.id == row.id) { "A habit has only one measurement" }
            validateField(row, dao.fieldById(row.id))
            dao.field(row)
            retireExtraMeasurements(fields, row.id)
        }
    }

    suspend fun saveEditor(row: TrackerEntity, fields: List<TrackerFieldEntity>, creating: Boolean) {
        db.withTransaction {
            // Cold flow registration can invert Room's observer and SQLite write locks.
            val trackers = dao.trackersSnapshot()
            val existing = trackers.find { it.id == row.id }
            require(creating == (existing == null)) { "This habit changed; reopen its editor" }
            require(row.name.isNotBlank()) { "Enter a habit name" }
            require(row.heatmapMode in setOf("RELATIVE", "GOAL")) { "Invalid heatmap mode" }
            val original = dao.fieldsForTracker(row.id)
            require(fields.map { it.id }.distinct().size == fields.size &&
                fields.all { it.trackerId == row.id } &&
                original.all { old -> fields.any { it.id == old.id } }) { "Habit measurements changed; reopen the editor" }
            val first = habitMeasurement(fields)
            val originalFirst = habitMeasurement(original)
            val added = fields.filter { field -> original.none { it.id == field.id } }
            require(added.size <= (if (originalFirst == null) 1 else 0) &&
                (originalFirst == null || first?.id == originalFirst.id) &&
                fields.filter { it.id != first?.id }.all { field -> original.any { it == field } }) {
                "A habit has only one measurement"
            }
            require(first != null || !creating && row.isArchived) { "Add a measurement" }
            fields.forEach { validateField(it, dao.fieldById(it.id)) }
            val position = existing?.position
                ?: ((trackers.maxOfOrNull { it.position } ?: -1) + 1)
            dao.tracker(row.copy(position = position))
            fields.forEach { dao.field(it) }
            first?.let { retireExtraMeasurements(fields, it.id) }
        }
    }

    private fun validateField(row: TrackerFieldEntity, original: TrackerFieldEntity?) {
        if (row == original) return
        require(original == null || original.trackerId == row.trackerId &&
            original.valueType == row.valueType && original.unit == row.unit) {
            "Measurement type and unit cannot change after creation"
        }
        require(HabitFieldForm.from(row).isValidFor(original)) { "Enter a valid measurement name, non-negative goal and unique choices" }
    }

    suspend fun moveTracker(id: String, delta: Int) {
        val siblings = dao.trackers().first().filterNot { it.isArchived }.sortedBy { it.position }
        moveItem(siblings, id, delta) { it.id }?.let { rows ->
            db.withTransaction {
                rows.forEachIndexed { index, row -> dao.tracker(row.copy(position = index)) }
            }
        }
    }

    suspend fun saveValue(row: TrackerDailyValueEntity) {
        db.withTransaction {
            val field = requireWritableMeasurement(row.fieldId)
            row.numericValue?.let { number ->
                val error = habitNumericError(field.valueType, number)
                if (error != null) throw IllegalArgumentException(error)
            }
            retireExtraMeasurements(dao.fieldsForTracker(field.trackerId), field.id)
            val trackerDao = db.trackerDao()
            val existing = trackerDao.valuesForDay(field.trackerId, row.epochDay).find { it.fieldId == row.fieldId }
            trackerDao.upsertValue(row.copy(notes = existing?.notes ?: row.notes))
        }
    }

    suspend fun clearValue(fieldId: String, day: Long) {
        db.withTransaction {
            val field = requireWritableMeasurement(fieldId)
            retireExtraMeasurements(dao.fieldsForTracker(field.trackerId), field.id)
            val trackerDao = db.trackerDao()
            val existing = trackerDao.valuesForDay(field.trackerId, day).find { it.fieldId == fieldId }
            if (existing != null && existing.notes.isNotEmpty()) {
                trackerDao.upsertValue(existing.copy(
                    numericValue = null, booleanValue = null, textValue = null,
                    choiceOptionId = null, choiceIntensity = null, updatedAt = System.currentTimeMillis(),
                ))
            } else {
                trackerDao.clearValue(fieldId, day)
            }
        }
    }

    private suspend fun requireWritableMeasurement(id: String): TrackerFieldEntity {
        val field = requireNotNull(dao.fieldById(id)) { "This measurement no longer exists" }
        require(habitMeasurement(dao.fieldsForTracker(field.trackerId))?.id == id) {
            "Only the first measurement can be recorded"
        }
        return field
    }

    private suspend fun retireExtraMeasurements(fields: List<TrackerFieldEntity>, retainedId: String) {
        fields.filter { !it.isArchived && it.id != retainedId }.forEach {
            dao.field(it.copy(isArchived = true))
        }
        val tracker = dao.trackersSnapshot().find { it.id == fields.firstOrNull()?.trackerId }
        if (tracker?.heatmapFieldId != null && tracker.heatmapFieldId != retainedId) {
            dao.tracker(tracker.copy(heatmapFieldId = retainedId))
        }
    }
}

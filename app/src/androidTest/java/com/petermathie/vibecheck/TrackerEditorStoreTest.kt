package com.petermathie.vibecheck

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.data.TrackerEditorStore
import com.petermathie.vibecheck.data.local.TrackerDailyValueEntity
import com.petermathie.vibecheck.data.local.TrackerEntity
import com.petermathie.vibecheck.data.local.TrackerFieldEntity
import com.petermathie.vibecheck.data.local.VibeDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackerEditorStoreTest {
    private lateinit var database: VibeDatabase
    private lateinit var store: TrackerEditorStore

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            VibeDatabase::class.java,
        ).build()
        store = TrackerEditorStore(database) { "default-field" }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun creatingTrackerAlsoCreatesDefaultBooleanField() = runBlocking {
        store.createTracker(TrackerEntity("tracker", "Wellbeing", false))

        assertEquals("Wellbeing", database.editorDao().trackers().first().single().name)
        val field = database.editorDao().fields().first().single()
        assertEquals("default-field", field.id)
        assertEquals("tracker", field.trackerId)
        assertEquals("Done", field.name)
        assertEquals("BOOLEAN", field.valueType)
    }

    @Test(timeout = 120_000)
    fun existingExtraMeasurementsAreRetiredWithoutChangingHistory() = runBlocking {
        val trackerDao = database.trackerDao()
        val tracker = TrackerEntity("tracker", "Wellbeing", false, heatmapFieldId = "second")
        val fields = listOf(field("first", 0), field("second", 1), field("archived", 2).copy(isArchived = true))
        trackerDao.insertTrackers(listOf(tracker))
        trackerDao.insertFields(fields)
        val history = TrackerDailyValueEntity("second", 10, 3.0, null, null, "", 1)
        trackerDao.upsertValue(history)
        val outcomes = trackerDao.observeActivityValues().first()

        store.saveEditor(tracker, fields, creating = false)

        assertEquals(listOf(fields.first()), database.editorDao().fields().first().filterNot { it.isArchived })
        assertEquals(fields[1].copy(isArchived = true), database.editorDao().fieldById("second"))
        assertEquals(fields[2], database.editorDao().fieldById("archived"))
        assertEquals(listOf(history), database.editorDao().values().first())
        assertEquals(outcomes, trackerDao.observeActivityValues().first())
        assertEquals("first", database.editorDao().trackers().first().single().heatmapFieldId)
    }

    @Test
    fun dailyValueCanBeSavedAndCleared() = runBlocking {
        val trackerDao = database.trackerDao()
        trackerDao.insertTrackers(listOf(TrackerEntity("tracker", "Wellbeing", false)))
        trackerDao.insertFields(listOf(field("field", 0)))
        store.saveValue(TrackerDailyValueEntity("field", 10, 3.0, null, null, "", 1))

        assertEquals(3.0, database.editorDao().values().first().single().numericValue ?: 0.0, 0.0)
        store.clearValue("field", 10)
        assertTrue(database.editorDao().values().first().isEmpty())
    }

    @Test
    fun editingAndClearingHistoricalMeasurementPreservesNotesAndOtherDays() = runBlocking {
        store.saveEditor(TrackerEntity("tracker", "Practice", false), listOf(field("field", 0)), true)
        val historical = TrackerDailyValueEntity("field", -1000, 7.0, null, null, "Legacy notes", 1)
        val other = historical.copy(epochDay = 20, numericValue = 12.0, notes = "Other day")
        database.trackerDao().upsertValue(historical)
        database.trackerDao().upsertValue(other)
        store.saveValue(historical.copy(numericValue = 8.0, notes = "", updatedAt = 2))
        assertEquals(historical.copy(numericValue = 8.0, updatedAt = 2),
            database.editorDao().values().first().single { it.epochDay == -1000L })
        store.clearValue("field", -1000)
        val cleared = database.editorDao().values().first().single { it.epochDay == -1000L }
        assertEquals("Legacy notes", cleared.notes)
        assertEquals(null, cleared.numericValue)
        assertEquals(other, database.editorDao().values().first().single { it.epochDay == 20L })
        store.saveValue(historical.copy(numericValue = 9.0, notes = "", updatedAt = 3))
        assertEquals("Legacy notes", database.editorDao().values().first().single { it.epochDay == -1000L }.notes)
    }

    @Test
    fun fullCreationPersistsChosenMeasurementAndHeatmapWithoutExtraDefault() = runBlocking {
        val tracker = TrackerEntity("tracker", "Protein", false, heatmapFieldId = "grams", heatmapMode = "GOAL")
        val grams = field("grams", 0).copy(unit = "g", targetComparison = "AT_LEAST", targetValue = 120.0)
        store.saveEditor(tracker, listOf(grams), creating = true)

        assertEquals(tracker, database.editorDao().trackers().first().single())
        assertEquals(listOf(grams), database.editorDao().fields().first())
    }

    @Test
    fun immutableTypeOrUnitFailureRollsBackWholeEditor() = runBlocking {
        val tracker = TrackerEntity("tracker", "Protein", false)
        val grams = field("grams", 0).copy(unit = "g")
        store.saveEditor(tracker, listOf(grams), creating = true)
        listOf(grams.copy(unit = "min"), grams.copy(valueType = "TEXT")).forEach { changed ->
            val failure = runCatching {
                store.saveEditor(tracker.copy(name = "Must roll back"), listOf(changed), creating = false)
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals(tracker, database.editorDao().trackers().first().single())
            assertEquals(grams, database.editorDao().fields().first().single())
        }
    }

    @Test(timeout = 120_000)
    fun additionalMeasurementsAreRejectedByBothEditorAndFieldSave() = runBlocking {
        val tracker = TrackerEntity("tracker", "Practice", false)
        val fields = listOf(field("minutes", 0), field("reps", 1))
        assertTrue(runCatching { store.saveEditor(tracker, fields, true) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(database.editorDao().trackers().first().isEmpty())
        store.saveEditor(tracker, fields.take(1), true)
        assertTrue(runCatching { store.saveField(fields[1]) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { store.saveEditor(tracker.copy(name = "Rejected"), fields, false) }
            .exceptionOrNull() is IllegalArgumentException)
        assertEquals(tracker, database.editorDao().trackers().first().single())
        assertEquals(fields.take(1), database.editorDao().fields().first())
    }

    @Test(timeout = 120_000)
    fun extraCannotBeRecordedAndFirstWriteKeepsOlderValues() = runBlocking {
        val fields = listOf(field("first", 0), field("extra", 1))
        database.trackerDao().insertTrackers(listOf(TrackerEntity("tracker", "Practice", false)))
        database.trackerDao().insertFields(fields)
        val old = TrackerDailyValueEntity("extra", 10, 7.0, null, null, "", 1)
        database.trackerDao().upsertValue(old)
        database.trackerDao().upsertValue(old.copy(epochDay = 11))
        assertTrue(runCatching { store.saveValue(old.copy(epochDay = 11)) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { store.clearValue("extra", 10) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(fields, database.editorDao().fields().first())

        store.saveValue(old.copy(fieldId = "first", epochDay = 11, numericValue = 2.0))

        assertEquals(listOf(old, old.copy(epochDay = 11)),
            database.editorDao().values().first().filter { it.fieldId == "extra" }.sortedBy { it.epochDay })
        assertTrue(requireNotNull(database.editorDao().fieldById("extra")).isArchived)
        store.clearValue("first", 11)
        assertTrue(database.trackerDao().observeActivityValues().first().none { it.epochDay == 11L })
        assertTrue(database.trackerDao().observeActivityValues().first().any { it.epochDay == 10L })
    }

    @Test
    fun settingsPreserveHistoricalValuesArchivedFieldsAndLegacyType() = runBlocking {
        val tracker = TrackerEntity("tracker", "Practice", false)
        val legacy = field("field", 0).copy(valueType = "RATING", unit = "score")
        val archived = field("archived", 1).copy(isArchived = true)
        database.trackerDao().insertTrackers(listOf(tracker))
        database.trackerDao().insertFields(listOf(legacy, archived))
        val negative = TrackerDailyValueEntity("field", 10, -2.0, null, null, "Legacy", 1)
        database.trackerDao().upsertValue(negative)
        val outcomes = database.trackerDao().observeActivityValues().first()
        val updated = legacy.copy(name = "Score", targetComparison = "AT_LEAST", targetValue = 5.0)

        store.saveEditor(tracker.copy(name = "Renamed", heatmapMode = "GOAL"), listOf(updated, archived), false)

        assertEquals(negative, database.editorDao().values().first().single())
        assertEquals(archived, database.editorDao().fieldById("archived"))
        assertEquals("RATING", database.editorDao().fieldById("field")?.valueType)
        assertEquals(outcomes, database.trackerDao().observeActivityValues().first())
    }

    @Test
    fun rejectsNewNegativeOrNonFiniteValuesWithoutReplacingLastValidEntry() = runBlocking {
        store.saveEditor(TrackerEntity("tracker", "Practice", false), listOf(field("field", 0)), true)
        val saved = TrackerDailyValueEntity("field", 10, 0.0, null, null, "", 1)
        store.saveValue(saved)
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { number ->
            assertTrue(runCatching { store.saveValue(saved.copy(numericValue = number)) }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(saved, database.editorDao().values().first().single())
        }
    }

    private fun field(id: String, position: Int) = TrackerFieldEntity(
        id,
        "tracker",
        id,
        "NUMBER",
        null,
        null,
        null,
        position,
    )
}

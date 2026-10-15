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
import java.time.LocalDate

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

    @Test
    fun movingFieldsReordersOnlyActiveSiblings() = runBlocking {
        val trackerDao = database.trackerDao()
        trackerDao.insertTrackers(listOf(TrackerEntity("tracker", "Wellbeing", false)))
        trackerDao.insertFields(
            listOf(
                field("first", 0),
                field("second", 1),
                field("archived", 2).copy(isArchived = true),
            ),
        )

        store.moveField("second", -1)

        val fields = database.editorDao().fields().first().associateBy { it.id }
        assertEquals(1, fields.getValue("first").position)
        assertEquals(0, fields.getValue("second").position)
        assertEquals(2, fields.getValue("archived").position)
        assertTrue(fields.getValue("archived").isArchived)
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
    fun historicalChoiceSnapshotSurvivesFieldEditAndFutureEntriesAreRejected() = runBlocking {
        val trackerDao = database.trackerDao()
        trackerDao.insertTrackers(listOf(TrackerEntity("tracker", "Wellbeing", false)))
        trackerDao.insertFields(listOf(field("field", 0).copy(valueType = "CHOICE", choiceOptions = "Calm\nGreat")))
        val historicalDay = LocalDate.now().minusDays(10).toEpochDay()
        store.saveValue(
            TrackerDailyValueEntity(
                fieldId = "field",
                epochDay = historicalDay,
                numericValue = null,
                booleanValue = null,
                textValue = "Great",
                notes = "",
                updatedAt = 1,
                choiceOptionId = "choice-great",
                choiceIntensity = "DARK",
            ),
        )

        store.saveField(field("field", 0).copy(valueType = "CHOICE", choiceOptions = "Calm"))

        val historical = database.editorDao().values().first().single()
        assertEquals("choice-great", historical.choiceOptionId)
        assertEquals("Great", historical.textValue)
        assertEquals("DARK", historical.choiceIntensity)

        val failure = runCatching {
            store.saveValue(historical.copy(epochDay = LocalDate.now().plusDays(1).toEpochDay()))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("future"))
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

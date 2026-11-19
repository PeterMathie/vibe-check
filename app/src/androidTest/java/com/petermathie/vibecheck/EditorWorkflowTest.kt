package com.petermathie.vibecheck

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.data.DataTransfer
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorWorkflowTest {
    private lateinit var db: VibeDatabase
    @Before fun setup()=runBlocking {
        db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),VibeDatabase::class.java).build()
        DatabaseSeeder(ApplicationProvider.getApplicationContext(),db).seedIfNeeded()
    }
    @After fun close(){db.close()}
    @Test(timeout = 120_000)
    fun historicalSetEditsPreserveUnchangedBandSnapshotsAndSnapshotNewSelections() = runBlocking {
        val dao = db.editorDao()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = com.petermathie.vibecheck.data.TrainingRepository(
            db, db.programmeDao(), db.workoutDao(), db.trackerDao(), context,
        )
        val workout = repository.startWorkout("demo-day-push")
        val exercise = dao.workoutExercisesForWorkout(workout).first()
        val old = BandEntity("custom-old", "Original band", 2.0, 0xFF5085D6L)
        val added = BandEntity("custom-added", "New band", 3.0, 0xFF5085D6L)
        dao.band(old)
        dao.band(added)
        val saved = com.petermathie.vibecheck.ui.emptySet(exercise.id, 1).copy(
            reps = 5.0, bandResistance = true,
        )
        dao.saveSetWithSnapshots(saved, listOf(old.id), consumeDraft = false)
        assertNotNull(repository.finishWorkout(workout))
        val originalLink = dao.bandsForExercise(exercise.id).single()
        dao.band(old.copy(name = "Renamed band", widthCentimetres = 8.0))

        val historical = requireNotNull(dao.setById(saved.id))
        dao.saveSetWithSnapshots(historical.copy(rpe = 7.0), listOf(old.id), consumeDraft = false)
        assertEquals(
            "An RPE-only historical edit must not resnapshot the current band catalogue",
            listOf(originalLink),
            dao.bandsForExercise(exercise.id),
        )
        dao.saveSetWithSnapshots(
            requireNotNull(dao.setById(saved.id)),
            listOf(added.id, old.id),
            consumeDraft = true,
        )
        assertEquals(
            listOf(
                WorkoutSetBandEntity(saved.id, added.id, 0, added.name, added.widthCentimetres),
                originalLink.copy(ordinal = 1),
            ),
            dao.bandsForExercise(exercise.id),
        )
    }

    @Test(timeout=120000) fun programmeUpdatePreservesDaysAndBackupRoundTrips()=runBlocking {
        val dao=db.editorDao()
        dao.programme(ProgrammeEntity("custom-p","Test","STRENGTH",false))
        dao.day(ProgrammeDayEntity("custom-d","custom-p","Day",0))
        dao.programme(ProgrammeEntity("custom-p","Renamed","STRENGTH",false))
        assertTrue(dao.days().first().any { it.id=="custom-d" })
        val backup=DataTransfer.export(db)
        dao.deleteDay("custom-d")
        DataTransfer.import(db,backup)
        assertTrue(dao.days().first().any { it.id=="custom-d" })
        assertEquals("Renamed",dao.programmes().first().find { it.id=="custom-p" }?.name)
        DataTransfer.import(db,backup)
        assertEquals(1,dao.days().first().count { it.id=="custom-d" })
    }
    @Test(timeout=120000) fun invalidImportDoesNotPartiallyWrite()=runBlocking {
        val backup=org.json.JSONObject(DataTransfer.export(db))
        val rows=backup.getJSONObject("tables").getJSONArray("programme_days")
        rows.getJSONObject(0).put("programmeId","missing-parent")
        val count=db.editorDao().days().first().size
        try { DataTransfer.import(db,backup.toString());fail("Expected constraint failure") } catch(_:Exception){}
        assertEquals(count,db.editorDao().days().first().size)
    }
    @Test(timeout=120000) fun draftsDoNotColourMusclesAndDefinitionEditsPreserveFinishedHistory()=runBlocking {
        db.openHelper.writableDatabase.execSQL("DELETE FROM workouts")
        val repository=com.petermathie.vibecheck.data.TrainingRepository(db,db.programmeDao(),db.workoutDao(),db.trackerDao(),ApplicationProvider.getApplicationContext())
        val day=repository.observeProgrammeDays().first().first()
        val id=repository.startWorkout(day.id)
        val exercise=repository.observeDraft().first { it!=null }!!.exercises.first()
        repository.addSet(exercise.id,com.petermathie.vibecheck.domain.model.SetDraft(reps=5.0))
        assertTrue(repository.observeMuscleRecency(day.mode).first().isEmpty())
        repository.finishWorkout(id)
        val before=repository.observeMuscleRecency(day.mode,Long.MAX_VALUE/2).first()
        assertTrue(before.isNotEmpty())
        val snapshot=db.editorDao().workoutExercises().first().first { it.id==exercise.id }
        val definition=db.editorDao().exerciseById(snapshot.actualExerciseId)!!
        db.editorDao().exercise(definition.copy(canonicalName="Renamed later"))
        db.editorDao().clearMappings(definition.id)
        val after=repository.observeMuscleRecency(day.mode,Long.MAX_VALUE/2).first()
        assertEquals(before,after)
        assertEquals(snapshot.exerciseName,db.editorDao().workoutExercises().first().first { it.id==exercise.id }.exerciseName)
        assertTrue(com.petermathie.vibecheck.data.DataTransfer.csv(db).contains("bandWidthCm"))
    }
}

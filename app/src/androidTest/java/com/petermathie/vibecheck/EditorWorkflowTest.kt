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

    @Test(timeout=120000) fun muscleBreakdownUsesSnapshotRolesRawSetsAndRecordedRpeAtItsWindow()=runBlocking {
        db.openHelper.writableDatabase.execSQL("DELETE FROM workouts")
        val definition = db.catalogueDao().searchExercises("").first().first()
        val asOf = 1_800_000_000_000L
        val week = 7 * 86_400_000L
        fun set(id: String, exercise: String, rpe: Double?) = WorkoutSetEntity(
            id=id, workoutExerciseId=exercise, ordinal=0, setType="WORKING", result="COMPLETED",
            variationId=null, weightKg=null, reps=8.0, holdMillis=null, leftReps=null, rightReps=null,
            leftHoldMillis=null, rightHoldMillis=null, addedWeightKg=null, assistanceKg=null, rpe=rpe,
            romValue=null, romUnit=null, notes="", loggedAt=asOf, updatedAt=asOf,
        )
        suspend fun workout(id: String, name: String, role: String, finish: Long, ratings: List<Double?>,
                            mode: String="STRENGTH", status: String="FINISHED", muscle: String="CHEST") {
            db.workoutDao().insertWorkout(WorkoutEntity(id,null,name,mode,status,finish,
                finish.takeIf { status=="FINISHED" },"",null,false))
            db.workoutDao().insertWorkoutExercises(listOf(WorkoutExerciseEntity(
                id,id,definition.id,definition.id,0,"",60,null,exerciseName=name,
            )))
            db.openHelper.writableDatabase.execSQL(
                "INSERT INTO workout_muscles(workoutExerciseId,muscleId,role) VALUES(?,?,?)",
                arrayOf(id,muscle,role),
            )
            db.workoutDao().insertSets(ratings.mapIndexed { index, rating -> set("$id-$index",id,rating) })
        }
        workout("primary","Recorded push up","PRIMARY",asOf-86_400_000,listOf(0.0,8.0,null))
        workout("secondary","Recorded dip","SECONDARY",asOf-3*86_400_000,listOf(6.0))
        workout("edge","Window boundary","PRIMARY",asOf-week,listOf(2.0))
        workout("old","Too old","PRIMARY",asOf-week-1,listOf(10.0))
        workout("future","Future","PRIMARY",asOf+1,listOf(10.0))
        workout("stretch","Other mode","PRIMARY",asOf-1,listOf(10.0),mode="STRETCHING")
        workout("draft","Draft","PRIMARY",asOf-1,listOf(10.0),status="DRAFT")
        workout("unrated","Unrated","PRIMARY",asOf-1,listOf(null),muscle="HAMSTRINGS")
        val excluded = set("excluded","primary",10.0)
        db.workoutDao().insertSets(listOf(
            excluded.copy(id="warmup",setType="WARM_UP"),
            excluded.copy(id="failed",result="FAILED"),
            excluded.copy(id="zero",reps=0.0),
            excluded.copy(id="rom",romValue=45.0,romUnit="deg"),
        ))
        db.editorDao().exercise(definition.copy(canonicalName="Renamed live definition"))
        db.editorDao().clearMappings(definition.id)
        val repository=com.petermathie.vibecheck.data.TrainingRepository(
            db,db.programmeDao(),db.workoutDao(),db.trackerDao(),ApplicationProvider.getApplicationContext(),
        )
        val muscles=repository.observeMuscleRecency(com.petermathie.vibecheck.domain.model.TrainingMode.STRENGTH,asOf).first()
        val chest=muscles.single { it.muscleId=="CHEST" }
        assertEquals(5,chest.weeklyWorkingSets)
        assertEquals(4.0,chest.weeklyAverageRpe!!,0.0)
        assertEquals(4.5,chest.setEquivalents,0.0)
        assertEquals(listOf("Recorded push up"),chest.contributingExerciseNames)
        assertEquals(asOf-86_400_000,chest.lastTrainedAt)
        val contributions=chest.weeklyContributions.associateBy { it.exerciseName }
        assertEquals(setOf("Recorded push up","Recorded dip","Window boundary"),contributions.keys)
        assertEquals(3,contributions.getValue("Recorded push up").setCount)
        assertEquals(com.petermathie.vibecheck.domain.model.MuscleRole.SECONDARY,contributions.getValue("Recorded dip").role)
        assertEquals(1,contributions.getValue("Recorded dip").setCount)
        assertNull(muscles.single { it.muscleId=="HAMSTRINGS" }.weeklyAverageRpe)
        val earlier=repository.observeMuscleRecency(com.petermathie.vibecheck.domain.model.TrainingMode.STRENGTH,asOf-1).first()
        assertEquals(6,earlier.single { it.muscleId=="CHEST" }.weeklyWorkingSets)
        val stretching=repository.observeMuscleRecency(com.petermathie.vibecheck.domain.model.TrainingMode.STRETCHING,asOf).first().single()
        assertEquals(listOf("Other mode"),stretching.weeklyContributions.map { it.exerciseName })
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

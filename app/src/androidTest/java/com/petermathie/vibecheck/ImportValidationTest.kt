package com.petermathie.vibecheck

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.petermathie.vibecheck.data.DataTransfer
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.local.WorkoutEntryDraftEntity
import com.petermathie.vibecheck.domain.workout.encodeTimeMeasurementsMillis
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportValidationTest {
    private lateinit var db: VibeDatabase
    private lateinit var exported: JSONObject

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        DatabaseSeeder(context, db).seedIfNeeded()
        exported = JSONObject(DataTransfer.export(db)).getJSONObject("tables")
    }
    @After fun close() = db.close()

    private fun fixture(table: String): JSONObject = JSONObject(exported.getJSONArray(table).getJSONObject(0).toString())
    private fun rows(table: String, row: JSONObject): JSONObject = JSONObject().put(table, JSONArray().put(row))
    private fun document(tables: JSONObject): String = JSONObject().put("format", "vibe-trainer").put("version", 1).put("tables", tables).toString()

    @Test
    fun vibeTrainerBackupIdentityRemainsImportCompatible() = runBlocking {
        val legacyBackup = JSONObject()
            .put("format", "vibe-trainer")
            .put("version", 1)
            .put("tables", JSONObject())
            .toString()

        DataTransfer.import(db, legacyBackup)

        assertEquals("vibe-trainer", JSONObject(DataTransfer.export(db)).getString("format"))
    }

    @Test
    fun legacyFrontSplitCatalogueDefinitionUpgradesDuringImport() = runBlocking {
        val exercises = exported.getJSONArray("exercises")
        val frontSplit = (0 until exercises.length())
            .map { JSONObject(exercises.getJSONObject(it).toString()) }
            .single { it.getString("id") == "core:front-split" }
            .put("trackingType", "ROM_MEASUREMENT")
            .put("inputConfig", "")

        DataTransfer.import(db, document(rows("exercises", frontSplit)))

        val imported = db.editorDao().exercises().first().single { it.id == "core:front-split" }
        assertEquals("HOLD", imported.trackingType)
        assertTrue(imported.inputConfig.contains("timeHeld=true"))
        assertTrue(imported.inputConfig.contains("reps=false"))
    }

    @Test
    fun unfinishedEntryDraftRoundTripsThroughBackup() = runBlocking {
        val workoutExerciseId = fixture("workout_exercises").getString("id")
        val draft = WorkoutEntryDraftEntity(
            workoutExerciseId = workoutExerciseId,
            setId = "backup-draft-set",
            ordinal = 3,
            performance = "12",
            rpe = "8",
            detailsOpen = true,
            warmUp = false,
            failed = false,
            bandIds = "band-yellow",
            variationId = null,
            leftValue = "",
            rightValue = "",
            addedWeight = "10",
            assistance = "",
            romValue = "",
            romUnit = "",
            updatedAt = 123456L,
            timeHeld = "15",
            timeUnderTension = "9",
            timeMeasurements = "[\"8\",\"10\"]",
        )
        db.editorDao().entryDraft(draft)
        val backup = DataTransfer.export(db)
        db.editorDao().deleteEntryDrafts(workoutExerciseId)

        DataTransfer.import(db, backup)

        assertEquals(draft, db.editorDao().entryDraft(workoutExerciseId))
    }

    @Test
    fun repeatedTimesRoundTripAndMustMatchTheirTotal() = runBlocking {
        val set = fixture("workout_sets")
            .put("timeUnderTensionMillis", 18_000)
            .put("timeMeasurementsMillis", encodeTimeMeasurementsMillis(listOf(8_000, 10_000)))

        DataTransfer.import(db, document(rows("workout_sets", set)))
        val roundTripped = JSONObject(DataTransfer.export(db))
            .getJSONObject("tables")
            .getJSONArray("workout_sets")
        assertTrue(
            (0 until roundTripped.length()).map(roundTripped::getJSONObject).any {
                it.getString("id") == set.getString("id") &&
                    it.getString("timeMeasurementsMillis") == "[8000,10000]"
            },
        )

        rejected(
            rows(
                "workout_sets",
                JSONObject(set.toString()).put("timeUnderTensionMillis", 17_000),
            ),
            "equal total time",
        )
    }

    @Test
    fun version16BackupRowsGainEmptyRepeatedTimeFields() = runBlocking {
        val workoutExerciseId = fixture("workout_exercises").getString("id")
        db.editorDao().entryDraft(
            WorkoutEntryDraftEntity(
                workoutExerciseId = workoutExerciseId,
                setId = "legacy-draft-set",
                ordinal = 4,
                performance = "",
                rpe = "7",
                detailsOpen = false,
                warmUp = false,
                failed = false,
                bandIds = "[]",
                variationId = null,
                leftValue = "",
                rightValue = "",
                addedWeight = "",
                assistance = "",
                romValue = "",
                romUnit = "cm",
                updatedAt = 1,
            ),
        )
        val currentTables = JSONObject(DataTransfer.export(db)).getJSONObject("tables")
        val legacySet = fixture("workout_sets").apply { remove("timeMeasurementsMillis") }
        val legacyDraft = JSONObject(
            currentTables.getJSONArray("workout_entry_drafts").getJSONObject(0).toString(),
        ).apply { remove("timeMeasurements") }

        DataTransfer.import(
            db,
            document(
                JSONObject()
                    .put("workout_sets", JSONArray().put(legacySet))
                    .put("workout_entry_drafts", JSONArray().put(legacyDraft)),
            ),
        )

        val backup = JSONObject(DataTransfer.export(db)).getJSONObject("tables")
        val setRows = backup.getJSONArray("workout_sets")
        val draftRows = backup.getJSONArray("workout_entry_drafts")
        assertTrue((0 until setRows.length()).map(setRows::getJSONObject).any {
            it.getString("id") == legacySet.getString("id") &&
                it.getString("timeMeasurementsMillis") == "[]"
        })
        assertTrue((0 until draftRows.length()).map(draftRows::getJSONObject).any {
            it.getString("setId") == legacyDraft.getString("setId") &&
                it.getString("timeMeasurements") == "[]"
        })
    }

    /** A valid insert comes first, proving later validation errors undo earlier writes. */
    private suspend fun rejected(tables: JSONObject, expected: String) {
        val before = DataTransfer.export(db)
        val exercises = JSONArray().put(fixture("exercises").put("id", "import-rollback-marker").put("isCustom", 1))
        tables.optJSONArray("exercises")?.let { old -> repeat(old.length()) { exercises.put(old.get(it)) } }
        tables.put("exercises", exercises)
        val failure = try { DataTransfer.import(db, document(tables)); null } catch (e: Exception) { e }
        assertNotNull("Import should reject $expected", failure)
        assertTrue("Wrong error: ${failure?.message}", failure?.message?.contains(expected, ignoreCase = true) == true)
        assertEquals("Rejected import must roll back every table", before, DataTransfer.export(db))
    }

    @Test(timeout=120000) fun invalidSetValuesRollBack() = runBlocking {
        rejected(rows("workout_sets", fixture("workout_sets").put("setType", "TYPO")), "setType")
        rejected(rows("workout_sets", fixture("workout_sets").put("reps", -1)), "reps")
        rejected(rows("workout_sets", fixture("workout_sets").put("reps", 1_000_000_001.0)), "range")
        rejected(rows("workout_sets", fixture("workout_sets").put("weightKg", "NaN")), "finite")
        rejected(rows("workout_sets", fixture("workout_sets").put("rpe", 11)), "rpe")
        rejected(rows("workout_sets", fixture("workout_sets").put("result", "FAILED").put("reps", 5)), "zero")
    }

    @Test(timeout=120000) fun malformedTablesAndDuplicateKeysAreRejected() = runBlocking {
        rejected(JSONObject().put("workout_sets", "not-an-array"), "array")
        val row = fixture("programmes").put("id", "duplicate-programme")
        rejected(JSONObject().put("programmes", JSONArray().put(row).put(row)), "Duplicate")
        rejected(rows("programmes", fixture("programmes").put("isDemo", 3)), "0/1")
        rejected(rows("workouts", fixture("workouts").put("finishedAt", JSONObject.NULL)), "finishedAt")
    }

    @Test(timeout=120000) fun seededDefinitionsCannotBeChangedOrAdded() = runBlocking {
        rejected(rows("exercises", fixture("exercises").put("canonicalName", "Modified seed")), "seeded")
        rejected(rows("exercises", fixture("exercises").put("isCustom", 1)), "seeded")
        rejected(rows("exercises", fixture("exercises").put("id", "new-seeded-exercise")), "seeded")
        rejected(rows("bands", fixture("bands").put("widthCentimetres", 99)), "seeded")
        rejected(rows("exercise_variations", fixture("exercise_variations").put("progressionRank", 99)), "seeded")
        rejected(rows("exercise_aliases", fixture("exercise_aliases").put("alias", "Modified alias")), "seeded")
        val mapping=fixture("exercise_muscles")
        rejected(rows("exercise_muscles", mapping.put("role", if(mapping.getString("role")=="PRIMARY") "SECONDARY" else "PRIMARY")), "seeded")
    }

    @Test(timeout=120000) fun crossExerciseVariationsAndUnknownBandsRollBack() = runBlocking {
        val set = fixture("workout_sets")
        rejected(rows("workout_sets", JSONObject(set.toString()).put("variationId", "missing-variation")), "variation")
        val allRows=exported.getJSONArray("workout_exercises")
        val actual=(0 until allRows.length()).map { allRows.getJSONObject(it) }.first { it.getString("id")==set.getString("workoutExerciseId") }.getString("actualExerciseId")
        val other=if(actual=="core:bench-press") "core:pull-up" else "core:bench-press"
        val variation=JSONObject().put("id","wrong-owner").put("exerciseId",other).put("name","Other exercise variation").put("progressionRank",99).put("isSeeded",0)
        rejected(rows("exercise_variations",variation).put("workout_sets",JSONArray().put(JSONObject(set.toString()).put("variationId","wrong-owner"))), "variation")
        rejected(rows("workout_set_bands",JSONObject().put("setId",set.getString("id")).put("bandId","missing-band").put("ordinal",0)), "FOREIGN KEY")
        rejected(rows("workout_muscles",fixture("workout_muscles").put("muscleId","missing-muscle")), "Unknown muscle")
    }

    @Test(timeout=120000) fun incompatibleHabitDataAndTargetsRollBack() = runBlocking {
        rejected(rows("tracker_daily_values",fixture("tracker_daily_values").put("numericValue",JSONObject.NULL).put("textValue","minutes")), "Habit value")
        rejected(rows("tracker_fields",fixture("tracker_fields").put("targetComparison","AT_LEAST").put("targetValue",JSONObject.NULL)), "together")
        rejected(rows("tracker_fields",fixture("tracker_fields").put("valueType","BOOLEAN").put("targetComparison","AT_LEAST").put("targetValue",1)), "numerical")
        rejected(rows("tracker_fields",fixture("tracker_fields").put("targetComparison","RANGE").put("targetValue",10).put("targetMaxValue",5)), "maximum")
        rejected(rows("tracker_daily_values",fixture("tracker_daily_values").put("epochDay",Long.MAX_VALUE)), "calendar range")
        val choiceField=fixture("tracker_fields").put("id","choice-field").put("valueType","CHOICE").put("unit",JSONObject.NULL)
            .put("targetComparison",JSONObject.NULL).put("targetValue",JSONObject.NULL).put("targetMaxValue",JSONObject.NULL).put("choiceOptions","Good\nBad")
        choiceField.remove("choiceOptionsJson")
        val choiceValue=fixture("tracker_daily_values").put("fieldId","choice-field").put("numericValue",JSONObject.NULL).put("booleanValue",JSONObject.NULL).put("textValue","Unknown")
            .put("choiceOptionId","choice-field:bad").put("choiceIntensity","INVALID")
        rejected(rows("tracker_fields",choiceField).put("tracker_daily_values",JSONArray().put(choiceValue)), "choiceIntensity")
    }

    @Test(timeout=120000) fun failedZeroAndBooleanInputAreAccepted() = runBlocking {
        val set=fixture("workout_sets").put("result","FAILED").put("reps",0).put("holdMillis",0)
            .put("leftReps",JSONObject.NULL).put("rightReps",JSONObject.NULL).put("leftHoldMillis",JSONObject.NULL).put("rightHoldMillis",JSONObject.NULL).put("romValue",JSONObject.NULL)
        val exercise=fixture("exercises").put("id","valid-custom").put("isCustom",true).put("isArchived",false)
        DataTransfer.import(db,document(rows("exercises",exercise).put("workout_sets",JSONArray().put(set))))
        val after=JSONObject(DataTransfer.export(db)).getJSONObject("tables").getJSONArray("exercises")
        assertTrue((0 until after.length()).any { after.getJSONObject(it).getString("id")=="valid-custom" })
    }

    @Test(timeout=120000) fun legacyChoiceBackupGainsStableIndependentSnapshots() = runBlocking {
        val trackerId = fixture("trackers").getString("id")
        val field = fixture("tracker_fields")
            .put("id", "legacy-choice")
            .put("trackerId", trackerId)
            .put("name", "Legacy mood")
            .put("valueType", "CHOICE")
            .put("unit", JSONObject.NULL)
            .put("targetComparison", JSONObject.NULL)
            .put("targetValue", JSONObject.NULL)
            .put("targetMaxValue", JSONObject.NULL)
            .put("choiceOptions", "Low\nOkay\nHigh")
            .put("choiceLightThrough", 0)
            .put("choiceDarkFrom", 2)
        field.remove("choiceOptionsJson")
        val value = fixture("tracker_daily_values")
            .put("fieldId", "legacy-choice")
            .put("numericValue", JSONObject.NULL)
            .put("booleanValue", JSONObject.NULL)
            .put("textValue", "Okay")
        value.remove("choiceOptionId")
        value.remove("choiceIntensity")

        DataTransfer.import(
            db,
            document(
                JSONObject()
                    .put("tracker_fields", JSONArray().put(field))
                    .put("tracker_daily_values", JSONArray().put(value)),
            ),
        )

        val exportedTables = JSONObject(DataTransfer.export(db)).getJSONObject("tables")
        val exportedField = (0 until exportedTables.getJSONArray("tracker_fields").length())
            .map { exportedTables.getJSONArray("tracker_fields").getJSONObject(it) }
            .single { it.getString("id") == "legacy-choice" }
        val exportedValue = (0 until exportedTables.getJSONArray("tracker_daily_values").length())
            .map { exportedTables.getJSONArray("tracker_daily_values").getJSONObject(it) }
            .single { it.getString("fieldId") == "legacy-choice" }
        assertTrue(exportedField.getString("choiceOptionsJson").contains("\"id\":\"legacy-choice:choice:1\""))
        assertEquals("legacy-choice:choice:1", exportedValue.getString("choiceOptionId"))
        assertEquals("MEDIUM", exportedValue.getString("choiceIntensity"))
    }

    @Test(timeout=120000) fun multipleActiveDraftsRollBack() = runBlocking {
        val first=fixture("workouts").put("id","draft-one").put("status","DRAFT").put("finishedAt",JSONObject.NULL)
        val second=JSONObject(first.toString()).put("id","draft-two")
        rejected(JSONObject().put("workouts",JSONArray().put(first).put(second)), "one active draft")
    }

    @Test(timeout=120000) fun publishedExampleImportsIdempotentlyWithStackedBands() = runBlocking {
        val example=InstrumentationRegistry.getInstrumentation().context.assets.open("historical-workout.json").bufferedReader().use { it.readText() }
        DataTransfer.import(db,example)
        val once=DataTransfer.export(db)
        DataTransfer.import(db,example)
        assertEquals(once,DataTransfer.export(db))
        val tables=JSONObject(once).getJSONObject("tables")
        val links=tables.getJSONArray("workout_set_bands")
        assertEquals(3,(0 until links.length()).count { links.getJSONObject(it).getString("setId")=="example-set" })
    }
}

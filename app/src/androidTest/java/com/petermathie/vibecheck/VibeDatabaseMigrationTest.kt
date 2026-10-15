package com.petermathie.vibecheck

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.petermathie.vibecheck.data.local.MIGRATION_2_3
import com.petermathie.vibecheck.data.local.MIGRATION_3_4
import com.petermathie.vibecheck.data.local.MIGRATION_4_5
import com.petermathie.vibecheck.data.local.MIGRATION_5_6
import com.petermathie.vibecheck.data.local.MIGRATION_6_7
import com.petermathie.vibecheck.data.local.MIGRATION_7_8
import com.petermathie.vibecheck.data.local.MIGRATION_8_9
import com.petermathie.vibecheck.data.local.MIGRATION_9_10
import com.petermathie.vibecheck.data.local.MIGRATION_10_11
import com.petermathie.vibecheck.data.local.MIGRATION_11_12
import com.petermathie.vibecheck.data.local.MIGRATION_12_13
import com.petermathie.vibecheck.data.local.MIGRATION_13_14
import com.petermathie.vibecheck.data.local.MIGRATION_14_15
import com.petermathie.vibecheck.data.local.MIGRATION_15_16
import com.petermathie.vibecheck.data.local.VibeDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VibeDatabaseMigrationTest {
    private val databaseName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VibeDatabase::class.java,
    )

    @Test
    fun migrate15To16RepairsNamesNotesAndFrontSplitWithoutReplacingRows() {
        helper.createDatabase(databaseName, 15).apply {
            execSQL(
                """
                INSERT INTO exercises
                    (id,canonicalName,tag,trackingType,equipment,instructions,source,isCustom,isArchived,inputConfig,
                     targetSets,targetRepsMin,targetRepsMax,targetRpe,restSeconds)
                VALUES ('core:front-split','Front split','STRETCHING','ROM_MEASUREMENT',NULL,NULL,'vibe-trainer',0,0,'',
                        3,NULL,NULL,NULL,60)
                """.trimIndent(),
            )
            execSQL("INSERT INTO programmes (id,name,mode,isDemo,isArchived,position) VALUES ('p','My mobility','STRETCHING',0,0,0)")
            execSQL("INSERT INTO programme_days (id,programmeId,name,position) VALUES ('d','p','Stretching',0)")
            val leaked =
                "Private programme source abc-2. LEGS + MOBILITY.md, line 20. Explicit rest: 2 minutes; achieved added load/reps were not promoted."
            execSQL(
                """
                INSERT INTO programme_exercises
                    (id,programmeDayId,exerciseId,position,targetSets,targetRepsMin,targetRepsMax,targetHoldSeconds,
                     restSeconds,targetRpe,notes,supersetGroup,inputConfig)
                VALUES ('pe','d','core:front-split',0,3,NULL,NULL,NULL,60,NULL,?,NULL,'')
                """.trimIndent(),
                arrayOf("$leaked\nKeep hips square"),
            )
            execSQL(
                """
                INSERT INTO workouts
                    (id,programmeDayId,name,mode,status,startedAt,finishedAt,notes,bodyweightKg,isDemo)
                VALUES ('w','d','Workout','STRETCHING','DRAFT',1,NULL,'',NULL,0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO workout_exercises
                    (id,workoutId,plannedExerciseId,actualExerciseId,position,notes,restSeconds,supersetGroup,
                     exerciseName,trackingType,targets,inputConfig,targetSets,targetRepsMin,targetRepsMax,
                     targetHoldSeconds,targetRpe)
                VALUES ('we','w','core:front-split','core:front-split',0,?,60,NULL,'Front split',
                        'ROM_MEASUREMENT','','',3,NULL,NULL,NULL,NULL)
                """.trimIndent(),
                arrayOf(leaked),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 16, true, MIGRATION_15_16).use { migrated ->
            migrated.query("SELECT name FROM workouts WHERE id='w'").use {
                assertTrue(it.moveToFirst())
                assertEquals("My mobility", it.getString(0))
            }
            migrated.query("SELECT notes FROM programme_exercises WHERE id='pe'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Keep hips square", it.getString(0))
            }
            migrated.query("SELECT notes,trackingType,inputConfig FROM workout_exercises WHERE id='we'").use {
                assertTrue(it.moveToFirst())
                assertEquals("", it.getString(0))
                assertEquals("HOLD", it.getString(1))
                assertTrue(it.getString(2).contains("timeHeld=true"))
            }
            migrated.query("SELECT canonicalName,trackingType,inputConfig FROM exercises WHERE id='core:front-split'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Front split", it.getString(0))
                assertEquals("HOLD", it.getString(1))
                assertTrue(it.getString(2).contains("reps=false"))
            }
        }
    }

    @Test
    fun migrate14To15UsesRealColumnsForFractionalRepetitions() {
        helper.createDatabase(databaseName, 14).apply {
            execSQL(
                """
                INSERT INTO exercises
                    (id,canonicalName,tag,trackingType,equipment,instructions,source,isCustom,isArchived,inputConfig,
                     targetSets,targetRepsMin,targetRepsMax,targetRpe,restSeconds)
                VALUES ('exercise','Exercise','STRENGTH','WEIGHT_REPS',NULL,NULL,'user',1,0,
                        'weightUnit=kg;bandResistance=true;timeHeld=false;timeUnderTension=false;reps=true',
                        3,5,8,7.5,90)
                """.trimIndent(),
            )
            execSQL("INSERT INTO bands (id,name,widthCentimetres,colourArgb) VALUES ('band','Band',1.2,0)")
            execSQL("INSERT INTO workouts (id,programmeDayId,name,mode,status,startedAt,finishedAt,notes,bodyweightKg,isDemo) VALUES ('workout',NULL,'Workout','STRENGTH','FINISHED',1,2,'',80,0)")
            execSQL(
                """
                INSERT INTO workout_exercises
                    (id,workoutId,plannedExerciseId,actualExerciseId,position,notes,restSeconds,supersetGroup,
                     exerciseName,trackingType,targets,inputConfig)
                VALUES ('entry','workout','exercise','exercise',0,'',90,NULL,'Exercise','WEIGHT_REPS','','')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO workout_sets
                    (id,workoutExerciseId,ordinal,setType,result,variationId,weightKg,reps,holdMillis,
                     leftReps,rightReps,leftHoldMillis,rightHoldMillis,addedWeightKg,assistanceKg,rpe,
                     romValue,romUnit,notes,loggedAt,updatedAt)
                VALUES ('set','entry',1,'WORKING','COMPLETED',NULL,20,7,NULL,6,5,NULL,NULL,NULL,NULL,8,
                        NULL,NULL,'',1,1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO workout_set_bands
                    (setId,bandId,ordinal,nameSnapshot,widthCentimetresSnapshot)
                VALUES ('set','band',0,'Band',1.2)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 15, true, MIGRATION_14_15).use { migrated ->
            migrated.query("PRAGMA table_info(workout_sets)").use { cursor ->
                val types = buildMap {
                    while (cursor.moveToNext()) {
                        put(cursor.getString(cursor.getColumnIndexOrThrow("name")), cursor.getString(cursor.getColumnIndexOrThrow("type")))
                    }
                }
                assertEquals("REAL", types["reps"])
                assertEquals("REAL", types["leftReps"])
                assertEquals("REAL", types["rightReps"])
                assertEquals("INTEGER", types["legacyReps"])
            }
            migrated.query(
                "SELECT reps,leftReps,rightReps,legacyReps,legacyLeftReps,legacyRightReps FROM workout_sets WHERE id='set'",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals(7.0, it.getDouble(0), 0.0)
                assertEquals(6.0, it.getDouble(1), 0.0)
                assertEquals(5.0, it.getDouble(2), 0.0)
                assertEquals(7, it.getInt(3))
                assertEquals(6, it.getInt(4))
                assertEquals(5, it.getInt(5))
            }
            migrated.query(
                "SELECT nameSnapshot,widthCentimetresSnapshot FROM workout_set_bands WHERE setId='set'",
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("Band", it.getString(0))
                assertEquals(1.2, it.getDouble(1), 0.0)
            }
        }
    }

    @Test
    fun migrate12To13PreservesAssignmentsBackfillsEmptyOnesAndSnapshotsWorkouts() {
        helper.createDatabase(databaseName, 12).apply {
            execSQL(
                """
                INSERT INTO exercises
                    (id,canonicalName,tag,trackingType,equipment,instructions,source,isCustom,isArchived,inputConfig,
                     targetSets,targetRepsMin,targetRepsMax,targetRpe,restSeconds)
                VALUES ('exercise','Exercise','STRENGTH','WEIGHT_REPS',NULL,NULL,'user',1,0,
                        'weightUnit=kg;bandResistance=false;timeHeld=false;timeUnderTension=false;reps=true',
                        4,5,8,7.5,90)
                """.trimIndent(),
            )
            execSQL("INSERT INTO programmes (id,name,mode,isDemo,isArchived,position) VALUES ('p1','One','STRENGTH',0,0,0),('p2','Two','STRENGTH',0,0,1)")
            execSQL("INSERT INTO programme_days (id,programmeId,name,position) VALUES ('d1','p1','One',0),('d2','p2','Two',0)")
            execSQL(
                """
                INSERT INTO programme_exercises
                    (id,programmeDayId,exerciseId,position,targetSets,targetRepsMin,targetRepsMax,targetHoldSeconds,restSeconds,targetRpe,notes,supersetGroup,inputConfig)
                VALUES ('preserved','d1','exercise',0,2,10,12,NULL,45,8.0,'',NULL,''),
                       ('empty','d2','exercise',0,NULL,NULL,NULL,NULL,120,NULL,'',NULL,'')
                """.trimIndent(),
            )
            execSQL("INSERT INTO workouts (id,programmeDayId,name,mode,status,startedAt,finishedAt,notes,bodyweightKg,isDemo) VALUES ('workout','d1','One','STRENGTH','DRAFT',1,NULL,'',NULL,0)")
            execSQL(
                """
                INSERT INTO workout_exercises
                    (id,workoutId,plannedExerciseId,actualExerciseId,position,notes,restSeconds,supersetGroup,exerciseName,trackingType,targets,inputConfig)
                VALUES ('snapshot','workout','exercise','exercise',0,'',45,NULL,'Exercise','WEIGHT_REPS','2 sets · 10–12 reps · RPE 8','')
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 13, true, MIGRATION_12_13).use { migrated ->
            migrated.query("SELECT targetSets,targetRepsMin,targetRepsMax,targetRpe,restSeconds FROM programme_exercises WHERE id='preserved'").use {
                assertTrue(it.moveToFirst())
                assertEquals(2, it.getInt(0))
                assertEquals(10, it.getInt(1))
                assertEquals(12, it.getInt(2))
                assertEquals(8.0, it.getDouble(3), 0.0)
                assertEquals(45, it.getInt(4))
            }
            migrated.query("SELECT targetSets,targetRepsMin,targetRepsMax,targetRpe,restSeconds FROM programme_exercises WHERE id='empty'").use {
                assertTrue(it.moveToFirst())
                assertEquals(4, it.getInt(0))
                assertEquals(5, it.getInt(1))
                assertEquals(8, it.getInt(2))
                assertEquals(7.5, it.getDouble(3), 0.0)
                assertEquals(120, it.getInt(4))
            }
            migrated.query("SELECT targetSets,targetRepsMin,targetRepsMax,targetRpe FROM workout_exercises WHERE id='snapshot'").use {
                assertTrue(it.moveToFirst())
                assertEquals(2, it.getInt(0))
                assertEquals(10, it.getInt(1))
                assertEquals(12, it.getInt(2))
                assertEquals(8.0, it.getDouble(3), 0.0)
            }
        }
    }

    @Test
    fun migrate11To12CopiesParentSettingsIntoIndependentVariations() {
        helper.createDatabase(databaseName, 11).apply {
            execSQL(
                """
                INSERT INTO exercises
                    (id,canonicalName,tag,trackingType,equipment,instructions,source,isCustom,isArchived,inputConfig,
                     targetSets,targetRepsMin,targetRepsMax,targetRpe,restSeconds)
                VALUES ('exercise','Exercise','STRENGTH','REPS',NULL,NULL,'user',1,0,
                        'weightUnit=;bandResistance=false;timeHeld=false;timeUnderTension=false;reps=true',
                        4,5,8,7.5,90)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO exercise_variations (id,exerciseId,name,progressionRank,isSeeded)
                VALUES ('variation','exercise','Variation',10,0)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 12, true, MIGRATION_11_12).use { migrated ->
            migrated.query(
                """
                SELECT trackingType,inputConfig,targetSets,targetRepsMin,targetRepsMax,targetRpe,restSeconds
                FROM exercise_variations WHERE id='variation'
                """.trimIndent(),
            ).use {
                assertTrue(it.moveToFirst())
                assertEquals("REPS", it.getString(0))
                assertTrue(it.getString(1).contains("reps=true"))
                assertEquals(4, it.getInt(2))
                assertEquals(5, it.getInt(3))
                assertEquals(8, it.getInt(4))
                assertEquals(7.5, it.getDouble(5), 0.0)
                assertEquals(90, it.getInt(6))
            }
            migrated.query("SELECT COUNT(*) FROM exercise_reference_videos").use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        }
    }

    @Test
    fun migrate2To3PreservesWorkoutAndCreatesEntryDraftStorage() {
        helper.createDatabase(databaseName, 2).apply {
            execSQL(
                """
                INSERT INTO workouts
                    (id, programmeDayId, name, mode, status, startedAt, finishedAt, notes, bodyweightKg, isDemo)
                VALUES ('existing-workout', NULL, 'Existing', 'STRENGTH', 'DRAFT', 123, NULL, 'keep me', NULL, 0)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 3, true, MIGRATION_2_3).use { migrated ->
            migrated.query("SELECT name, notes FROM workouts WHERE id = 'existing-workout'").use {
                assertEquals(true, it.moveToFirst())
                assertEquals("Existing", it.getString(0))
                assertEquals("keep me", it.getString(1))
            }
            migrated.query("SELECT COUNT(*) FROM workout_entry_drafts").use {
                assertEquals(true, it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }

        }
    }


    @Test
    fun migrate3To4PreservesFieldsAndAddsRicherConfiguration() {
        helper.createDatabase(databaseName, 3).apply {
            execSQL("INSERT INTO trackers (id, name, isDemo, isArchived) VALUES ('habit', 'Habit', 0, 0)")
            execSQL(
                """
                    INSERT INTO tracker_fields
                        (id, trackerId, name, valueType, unit, targetComparison, targetValue, position)
                    VALUES ('field', 'habit', 'Existing field', 'NUMBER', 'units', 'AT_LEAST', 5, 0)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 4, true, MIGRATION_3_4).use { migrated ->
            migrated.query("SELECT name, choiceOptions, targetMaxValue, isArchived FROM tracker_fields WHERE id='field'").use {
                assertEquals(true, it.moveToFirst())
                assertEquals("Existing field", it.getString(0))
                assertEquals("", it.getString(1))
                assertEquals(true, it.isNull(2))
                assertEquals(0, it.getInt(3))
            }

        }
    }

    @Test
    fun migrate4To5SnapshotsHistoricalDefinitionsAndOutcomes() {
        helper.createDatabase(databaseName, 4).apply {
                    execSQL("INSERT INTO bands (id,name,widthCentimetres,colourArgb) VALUES ('band','Band',1.2,0)")
                    execSQL("INSERT INTO exercises (id,canonicalName,tag,trackingType,equipment,instructions,source,isCustom,isArchived) VALUES ('e','Exercise','STRENGTH','ASSISTED_REPS',NULL,NULL,'USER',1,0)")
                    execSQL("INSERT INTO exercise_variations (id,exerciseId,name,progressionRank,isSeeded) VALUES ('v','e','Variation',3,0)")
                    execSQL("INSERT INTO workouts (id,programmeDayId,name,mode,status,startedAt,finishedAt,notes,bodyweightKg,isDemo) VALUES ('w',NULL,'Workout','STRENGTH','FINISHED',1,2,'',80,0)")
                    execSQL("INSERT INTO workout_exercises (id,workoutId,plannedExerciseId,actualExerciseId,position,notes,restSeconds,supersetGroup,exerciseName,trackingType,targets) VALUES ('we','w','e','e',0,'',60,NULL,'Exercise','ASSISTED_REPS','')")
                    execSQL("INSERT INTO workout_sets (id,workoutExerciseId,ordinal,setType,result,variationId,weightKg,reps,holdMillis,leftReps,rightReps,leftHoldMillis,rightHoldMillis,addedWeightKg,assistanceKg,rpe,romValue,romUnit,notes,loggedAt,updatedAt) VALUES ('s','we',1,'WORKING','COMPLETED','v',NULL,5,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'',1,1)")
                    execSQL("INSERT INTO workout_set_bands (setId,bandId,ordinal) VALUES ('s','band',0)")
                    execSQL("INSERT INTO trackers (id,name,isDemo,isArchived) VALUES ('t','Tracker',0,0)")
                    execSQL("INSERT INTO tracker_fields (id,trackerId,name,valueType,unit,targetComparison,targetValue,position,choiceOptions,targetMaxValue,isArchived) VALUES ('f','t','Field','NUMBER',NULL,'AT_LEAST',5,0,'',NULL,0)")
                    execSQL("INSERT INTO tracker_daily_values (fieldId,epochDay,numericValue,booleanValue,textValue,notes,updatedAt) VALUES ('f',1,6,NULL,NULL,'',1)")
                    close()
        }

        helper.runMigrationsAndValidate(databaseName, 5, true, MIGRATION_4_5).use { migrated ->
                    migrated.query("SELECT variationRankSnapshot FROM workout_sets WHERE id='s'").use { assertTrue(it.moveToFirst()); assertEquals(3,it.getInt(0)) }
                    migrated.query("SELECT nameSnapshot,widthCentimetresSnapshot FROM workout_set_bands WHERE setId='s'").use { assertTrue(it.moveToFirst()); assertEquals("Band",it.getString(0)); assertEquals(1.2,it.getDouble(1),0.0) }
                    migrated.query("SELECT targetMet FROM tracker_day_outcomes WHERE trackerId='t' AND epochDay=1").use { assertTrue(it.moveToFirst()); assertEquals(1,it.getInt(0)) }
        }
    }

    @Test
    fun migrate5To6PreservesDraftAndAllowsMultipleSetRows() {
        helper.createDatabase(databaseName, 5).apply {
            execSQL("INSERT INTO exercises (id,canonicalName,tag,trackingType,equipment,instructions,source,isCustom,isArchived) VALUES ('e','Exercise','STRENGTH','WEIGHT_REPS',NULL,NULL,'USER',1,0)")
            execSQL("INSERT INTO workouts (id,programmeDayId,name,mode,status,startedAt,finishedAt,notes,bodyweightKg,isDemo) VALUES ('w',NULL,'Workout','STRENGTH','DRAFT',1,NULL,'',NULL,0)")
            execSQL("INSERT INTO workout_exercises (id,workoutId,plannedExerciseId,actualExerciseId,position,notes,restSeconds,supersetGroup,exerciseName,trackingType,targets) VALUES ('we','w','e','e',0,'',60,NULL,'Exercise','WEIGHT_REPS','3 sets')")
            execSQL("INSERT INTO workout_entry_drafts VALUES ('we','set-1',1,'50 x 5','8',0,0,0,'[]',NULL,'','','','','','cm',1)")
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 6, true, MIGRATION_5_6).use { migrated ->
            migrated.query("SELECT setId, ordinal, performance FROM workout_entry_drafts WHERE workoutExerciseId='we'").use {
                assertTrue(it.moveToFirst())
                assertEquals("set-1", it.getString(0))
                assertEquals(1, it.getInt(1))
                assertEquals("50 x 5", it.getString(2))
            }
            migrated.execSQL("INSERT INTO workout_entry_drafts VALUES ('we','set-2',2,'52.5 x 5','8',0,0,0,'[]',NULL,'','','','','','cm',2)")
            migrated.query("SELECT COUNT(*) FROM workout_entry_drafts WHERE workoutExerciseId='we'").use {
                assertTrue(it.moveToFirst())
                assertEquals(2, it.getInt(0))
            }
        }
    }

    @Test
    fun migrate6To7AddsExerciseInputConfigurationWithoutLosingDrafts() {
        helper.createDatabase(databaseName, 6).apply {
            execSQL("INSERT INTO exercises (id,canonicalName,tag,trackingType,equipment,instructions,source,isCustom,isArchived) VALUES ('e','Exercise','STRENGTH','WEIGHT_REPS',NULL,NULL,'USER',1,0)")
            execSQL("INSERT INTO workouts (id,programmeDayId,name,mode,status,startedAt,finishedAt,notes,bodyweightKg,isDemo) VALUES ('w',NULL,'Workout','STRENGTH','DRAFT',1,NULL,'',NULL,0)")
            execSQL("INSERT INTO workout_exercises (id,workoutId,plannedExerciseId,actualExerciseId,position,notes,restSeconds,supersetGroup,exerciseName,trackingType,targets) VALUES ('we','w','e','e',0,'',60,NULL,'Exercise','WEIGHT_REPS','3 sets')")
            execSQL("INSERT INTO workout_entry_drafts VALUES ('we','set-1',1,'50 x 5','8',0,0,0,'[]',NULL,'','','','','','cm',1)")
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 7, true, MIGRATION_6_7).use { migrated ->
            migrated.query("SELECT performance,timeHeld,timeUnderTension FROM workout_entry_drafts WHERE workoutExerciseId='we'").use {
                assertTrue(it.moveToFirst())
                assertEquals("50 x 5", it.getString(0))
                assertEquals("", it.getString(1))
                assertEquals("", it.getString(2))
            }
        }
    }

    @Test
    fun migrate7To8MovesProgrammeSettingsToExerciseAndPreservesWorkoutSnapshot() {
        helper.createDatabase(databaseName, 7).apply {
                execSQL("INSERT INTO exercises (id,canonicalName,tag,trackingType,equipment,instructions,source,isCustom,isArchived) VALUES ('e','Handstand','STRENGTH','SKILL_HOLD',NULL,NULL,'USER',1,0)")
                execSQL("INSERT INTO programmes (id,name,mode,isDemo,isArchived,position) VALUES ('p','Programme','STRENGTH',0,0,0)")
                execSQL("INSERT INTO programme_days (id,programmeId,name,position) VALUES ('d','p','Workout',0)")
                execSQL(
                    """
                    INSERT INTO programme_exercises
                        (id,programmeDayId,exerciseId,position,targetSets,targetRepsMin,targetRepsMax,targetHoldSeconds,restSeconds,targetRpe,notes,supersetGroup,inputConfig)
                    VALUES ('pe','d','e',0,4,5,8,NULL,90,8.5,'keep note',NULL,'weightUnit=;bandResistance=false;timeHeld=true;timeUnderTension=true;reps=false')
                    """.trimIndent(),
                )
                execSQL("INSERT INTO workouts (id,programmeDayId,name,mode,status,startedAt,finishedAt,notes,bodyweightKg,isDemo) VALUES ('w','d','Workout','STRENGTH','FINISHED',1,2,'',NULL,0)")
                execSQL(
                    """
                    INSERT INTO workout_exercises
                        (id,workoutId,plannedExerciseId,actualExerciseId,position,notes,restSeconds,supersetGroup,exerciseName,trackingType,targets,inputConfig)
                    VALUES ('we','w','e','e',0,'',60,NULL,'Historical handstand','SKILL_HOLD','old targets','historical-config')
                    """.trimIndent(),
                )
                listOf(
                    "core:front-split" to "Front split",
                    "core:forward-fold" to "Forward fold",
                    "core:side-split" to "Side split",
                    "core:bridge" to "Bridge",
                ).forEach { (id, name) ->
                    execSQL("INSERT INTO exercises (id,canonicalName,tag,trackingType,equipment,instructions,source,isCustom,isArchived) VALUES ('$id','$name','STRETCHING','HOLD',NULL,NULL,'vibe-trainer',0,0)")
                }
                execSQL("INSERT INTO programmes (id,name,mode,isDemo,isArchived,position) VALUES ('demo-programme-stretch','Stretching','STRETCHING',1,0,0)")
                listOf(
                    Triple("demo-day-front-splits", "Front Splits", "core:front-split"),
                    Triple("demo-day-forward-fold", "Forward Fold", "core:forward-fold"),
                    Triple("demo-day-side-splits", "Side Splits", "core:side-split"),
                    Triple("demo-day-bridge", "Bridge", "core:bridge"),
                ).forEachIndexed { index, (dayId, name, exerciseId) ->
                    execSQL("INSERT INTO programme_days (id,programmeId,name,position) VALUES ('$dayId','demo-programme-stretch','$name',$index)")
                    execSQL(
                        """
                        INSERT INTO programme_exercises
                            (id,programmeDayId,exerciseId,position,targetSets,targetRepsMin,targetRepsMax,targetHoldSeconds,restSeconds,targetRpe,notes,supersetGroup,inputConfig)
                        VALUES ('$dayId:$exerciseId','$dayId','$exerciseId',0,3,NULL,NULL,NULL,60,NULL,'',NULL,'')
                        """.trimIndent(),
                    )
                }
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 8, true, MIGRATION_7_8).use { migrated ->
                migrated.query("SELECT inputConfig,targetSets,targetRepsMin,targetRepsMax,targetRpe,restSeconds FROM exercises WHERE id='e'").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("weightUnit=;bandResistance=false;timeHeld=true;timeUnderTension=true;reps=false", it.getString(0))
                    assertEquals(4, it.getInt(1))
                    assertEquals(5, it.getInt(2))
                    assertEquals(8, it.getInt(3))
                    assertEquals(8.5, it.getDouble(4), 0.0)
                    assertEquals(90, it.getInt(5))
                }
                migrated.query("SELECT exerciseName,inputConfig,targets FROM workout_exercises WHERE id='we'").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("Historical handstand", it.getString(0))
                    assertEquals("historical-config", it.getString(1))
                    assertEquals("old targets", it.getString(2))
                }
                migrated.query("SELECT id,name FROM programme_days WHERE programmeId='demo-programme-stretch'").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("demo-day-front-splits", it.getString(0))
                    assertEquals("Stretching", it.getString(1))
                    assertTrue(!it.moveToNext())
                }
                migrated.query("SELECT exerciseId,position FROM programme_exercises WHERE programmeDayId='demo-day-front-splits' ORDER BY position").use {
                    val exercises = mutableListOf<String>()
                    while (it.moveToNext()) exercises += it.getString(0)
                    assertEquals(listOf("core:front-split", "core:forward-fold", "core:side-split", "core:bridge"), exercises)
                }
            }
        }

    @Test
    fun migrate8To9AddsPersistentHabitColour() {
        helper.createDatabase(databaseName, 8).apply {
            execSQL("INSERT INTO trackers (id,name,isDemo,isArchived) VALUES ('habit','Meditation',0,0)")
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 9, true, MIGRATION_8_9).use { migrated ->
            migrated.query("SELECT name,colourArgb FROM trackers WHERE id='habit'").use {
                assertTrue(it.moveToFirst())
                assertEquals("Meditation", it.getString(0))
            assertEquals(4283215696L, it.getLong(1))
        }
    }
}

@Test
fun migrate9To10AddsHabitOrderAndHeatmapThresholds() {
    helper.createDatabase(databaseName, 9).apply {
        execSQL(
            """
            INSERT INTO trackers (id,name,isDemo,isArchived,colourArgb)
            VALUES ('protein','Protein',0,0,4283215696),
                   ('meditation','Meditation',0,0,4283215696)
            """.trimIndent(),
        )
        close()
    }

    helper.runMigrationsAndValidate(databaseName, 10, true, MIGRATION_9_10).use { migrated ->
        migrated.query(
            """
            SELECT name,position,heatmapLightBelow,heatmapMediumBelow
            FROM trackers ORDER BY position
            """.trimIndent(),
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("Meditation", it.getString(0))
            assertEquals(0, it.getInt(1))
            assertEquals(7.0, it.getDouble(2), 0.0)
            assertEquals(15.0, it.getDouble(3), 0.0)
            assertTrue(it.moveToNext())
            assertEquals("Protein", it.getString(0))
            assertEquals(1, it.getInt(1))
            assertEquals(140.0, it.getDouble(2), 0.0)
        assertEquals(160.0, it.getDouble(3), 0.0)
    }
}
}

@Test
fun migrate10To11AddsChoiceBoundariesAndHabitIconsWithoutLosingData() {
    helper.createDatabase(databaseName, 10).apply {
        execSQL(
            """
            INSERT INTO trackers (
                id,name,isDemo,isArchived,colourArgb,position,heatmapLightBelow,heatmapMediumBelow
            ) VALUES ('mood','Mood',0,0,4283215696,0,7.0,15.0)
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO tracker_fields (
                id,trackerId,name,valueType,unit,choiceOptions,position
            ) VALUES ('mood-choice','mood','Mood','choice','',
                'Terrified
            Lonely
            Sad
            Happy
            Joyful
            Super',0)
            """.trimIndent(),
        )
        close()
    }

    helper.runMigrationsAndValidate(databaseName, 11, true, MIGRATION_10_11).use { migrated ->
        migrated.query("SELECT name,iconName FROM trackers WHERE id = 'mood'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Mood", it.getString(0))
            assertEquals("habit", it.getString(1))
        }
        migrated.query(
            """
            SELECT name,choiceOptions,choiceLightThrough,choiceDarkFrom
            FROM tracker_fields WHERE id = 'mood-choice'
            """.trimIndent(),
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("Mood", it.getString(0))
            assertEquals("Terrified\nLonely\nSad\nHappy\nJoyful\nSuper", it.getString(1))
            assertEquals(-1, it.getInt(2))
            assertEquals(-1, it.getInt(3))
        }
    }
    }

    @Test
    fun migrate13To14SnapshotsStableChoiceIdentityAndIntensity() {
        helper.createDatabase(databaseName, 13).apply {
                execSQL("INSERT INTO trackers (id,name,isDemo,isArchived,colourArgb,position,heatmapLightBelow,heatmapMediumBelow,iconName) VALUES ('mood','Mood',0,0,1,0,7,15,'habit')")
                execSQL(
                    """
                    INSERT INTO tracker_fields (
                        id,trackerId,name,valueType,unit,targetComparison,targetValue,position,
                        choiceOptions,targetMaxValue,isArchived,choiceLightThrough,choiceDarkFrom
                    ) VALUES ('mood-choice','mood','Mood','CHOICE',NULL,NULL,NULL,0,
                        'Low
                    Okay
                    High',NULL,0,0,2)
                    """.trimIndent(),
                )
                execSQL("INSERT INTO tracker_daily_values (fieldId,epochDay,numericValue,booleanValue,textValue,notes,updatedAt) VALUES ('mood-choice',1,NULL,NULL,'Okay','',1)")
                close()
        }

        helper.runMigrationsAndValidate(databaseName, 14, true, MIGRATION_13_14).use { migrated ->
            migrated.query("SELECT choiceOptionsJson FROM tracker_fields WHERE id='mood-choice'").use {
                assertTrue(it.moveToFirst())
                assertTrue(it.getString(0).contains("\"intensity\":\"MEDIUM\""))
            }
            migrated.query("SELECT choiceOptionId,choiceIntensity,textValue FROM tracker_daily_values").use {
                assertTrue(it.moveToFirst())
                assertEquals("mood-choice:choice:1", it.getString(0))
                assertEquals("MEDIUM", it.getString(1))
                assertEquals("Okay", it.getString(2))
            }
        }
    }
}

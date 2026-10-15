package com.petermathie.vibecheck.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.petermathie.vibecheck.domain.tracker.encodeHabitChoices
import com.petermathie.vibecheck.domain.tracker.legacyHabitChoices
import com.petermathie.vibecheck.domain.tracker.legacyHabitChoiceSnapshot
import com.petermathie.vibecheck.data.sanitizeExerciseNotes
import com.petermathie.vibecheck.data.FRONT_SPLIT_HOLD_INPUT_CONFIG

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE programmes ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workout_exercises ADD COLUMN exerciseName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE workout_exercises ADD COLUMN trackingType TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE workout_exercises ADD COLUMN targets TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE workout_exercises SET exerciseName=(SELECT canonicalName FROM exercises WHERE id=actualExerciseId),trackingType=(SELECT trackingType FROM exercises WHERE id=actualExerciseId)")
        db.execSQL("CREATE TABLE IF NOT EXISTS workout_muscles (workoutExerciseId TEXT NOT NULL, muscleId TEXT NOT NULL, role TEXT NOT NULL, PRIMARY KEY(workoutExerciseId,muscleId), FOREIGN KEY(workoutExerciseId) REFERENCES workout_exercises(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_muscles_workoutExerciseId ON workout_muscles(workoutExerciseId)")
        db.execSQL("INSERT INTO workout_muscles SELECT we.id,em.muscleId,em.role FROM workout_exercises we JOIN exercise_muscles em ON em.exerciseId=we.actualExerciseId")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workout_entry_drafts (
                workoutExerciseId TEXT NOT NULL,
                setId TEXT NOT NULL,
                ordinal INTEGER NOT NULL,
                performance TEXT NOT NULL,
                rpe TEXT NOT NULL,
                detailsOpen INTEGER NOT NULL,
                warmUp INTEGER NOT NULL,
                failed INTEGER NOT NULL,
                bandIds TEXT NOT NULL,
                variationId TEXT,
                leftValue TEXT NOT NULL,
                rightValue TEXT NOT NULL,
                addedWeight TEXT NOT NULL,
                assistance TEXT NOT NULL,
                romValue TEXT NOT NULL,
                romUnit TEXT NOT NULL,
                updatedAt INTEGER NOT NULL,
                PRIMARY KEY(workoutExerciseId),
                FOREIGN KEY(workoutExerciseId) REFERENCES workout_exercises(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracker_fields ADD COLUMN choiceOptions TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE tracker_fields ADD COLUMN targetMaxValue REAL")
        db.execSQL("ALTER TABLE tracker_fields ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN variationRankSnapshot INTEGER")
            db.execSQL("UPDATE workout_sets SET variationRankSnapshot=(SELECT progressionRank FROM exercise_variations WHERE id=variationId) WHERE variationId IS NOT NULL")
            db.execSQL("ALTER TABLE workout_set_bands ADD COLUMN nameSnapshot TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE workout_set_bands ADD COLUMN widthCentimetresSnapshot REAL NOT NULL DEFAULT 0")
            db.execSQL("UPDATE workout_set_bands SET nameSnapshot=(SELECT name FROM bands WHERE id=bandId),widthCentimetresSnapshot=(SELECT widthCentimetres FROM bands WHERE id=bandId)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS tracker_day_outcomes (
                    trackerId TEXT NOT NULL,
                    epochDay INTEGER NOT NULL,
                    targetMet INTEGER NOT NULL,
                    PRIMARY KEY(trackerId, epochDay),
                    FOREIGN KEY(trackerId) REFERENCES trackers(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tracker_day_outcomes_trackerId ON tracker_day_outcomes(trackerId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tracker_day_outcomes_epochDay ON tracker_day_outcomes(epochDay)")
            db.execSQL(
                """
                INSERT INTO tracker_day_outcomes
                SELECT days.trackerId, days.epochDay,
                  CASE WHEN EXISTS (SELECT 1 FROM tracker_fields f WHERE f.trackerId=days.trackerId AND f.isArchived=0 AND f.targetComparison IS NOT NULL)
                  THEN NOT EXISTS (
                    SELECT 1 FROM tracker_fields f LEFT JOIN tracker_daily_values v ON v.fieldId=f.id AND v.epochDay=days.epochDay
                    WHERE f.trackerId=days.trackerId AND f.isArchived=0 AND f.targetComparison IS NOT NULL AND (
                      v.numericValue IS NULL OR
                      CASE f.targetComparison WHEN 'AT_LEAST' THEN v.numericValue < f.targetValue
                        WHEN 'AT_MOST' THEN v.numericValue > f.targetValue
                        WHEN 'EXACTLY' THEN v.numericValue != f.targetValue
                        WHEN 'RANGE' THEN v.numericValue < f.targetValue OR v.numericValue > f.targetMaxValue
                        ELSE 1 END
                    )
                  ) ELSE 1 END
                FROM (SELECT DISTINCT f.trackerId,v.epochDay FROM tracker_daily_values v JOIN tracker_fields f ON f.id=v.fieldId) days
                """.trimIndent(),
            )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE workout_entry_drafts_new (
                    workoutExerciseId TEXT NOT NULL,
                    setId TEXT NOT NULL,
                    ordinal INTEGER NOT NULL,
                    performance TEXT NOT NULL,
                    rpe TEXT NOT NULL,
                    detailsOpen INTEGER NOT NULL,
                    warmUp INTEGER NOT NULL,
                    failed INTEGER NOT NULL,
                    bandIds TEXT NOT NULL,
                    variationId TEXT,
                    leftValue TEXT NOT NULL,
                    rightValue TEXT NOT NULL,
                    addedWeight TEXT NOT NULL,
                    assistance TEXT NOT NULL,
                    romValue TEXT NOT NULL,
                    romUnit TEXT NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    PRIMARY KEY(workoutExerciseId, ordinal),
                    FOREIGN KEY(workoutExerciseId) REFERENCES workout_exercises(id)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO workout_entry_drafts_new SELECT * FROM workout_entry_drafts")
            db.execSQL("DROP TABLE workout_entry_drafts")
            db.execSQL("ALTER TABLE workout_entry_drafts_new RENAME TO workout_entry_drafts")
            db.execSQL("CREATE INDEX index_workout_entry_drafts_workoutExerciseId ON workout_entry_drafts(workoutExerciseId)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE programme_exercises ADD COLUMN inputConfig TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE workout_exercises ADD COLUMN inputConfig TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN timeUnderTensionMillis INTEGER")
        db.execSQL("ALTER TABLE workout_sets ADD COLUMN bandResistance INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE workout_entry_drafts ADD COLUMN timeHeld TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE workout_entry_drafts ADD COLUMN timeUnderTension TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exercises ADD COLUMN inputConfig TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE exercises ADD COLUMN targetSets INTEGER")
            db.execSQL("ALTER TABLE exercises ADD COLUMN targetRepsMin INTEGER")
            db.execSQL("ALTER TABLE exercises ADD COLUMN targetRepsMax INTEGER")
            db.execSQL("ALTER TABLE exercises ADD COLUMN targetRpe REAL")
            db.execSQL("ALTER TABLE exercises ADD COLUMN restSeconds INTEGER NOT NULL DEFAULT 120")
            db.execSQL(
                """
                UPDATE exercises
                SET inputConfig = COALESCE((
                        SELECT pe.inputConfig FROM programme_exercises pe
                        WHERE pe.exerciseId = exercises.id AND pe.inputConfig != ''
                        ORDER BY pe.position LIMIT 1
                    ), ''),
                    targetSets = (SELECT pe.targetSets FROM programme_exercises pe WHERE pe.exerciseId = exercises.id ORDER BY pe.position LIMIT 1),
                    targetRepsMin = (SELECT pe.targetRepsMin FROM programme_exercises pe WHERE pe.exerciseId = exercises.id ORDER BY pe.position LIMIT 1),
                    targetRepsMax = (SELECT pe.targetRepsMax FROM programme_exercises pe WHERE pe.exerciseId = exercises.id ORDER BY pe.position LIMIT 1),
                    targetRpe = (SELECT pe.targetRpe FROM programme_exercises pe WHERE pe.exerciseId = exercises.id ORDER BY pe.position LIMIT 1),
                    restSeconds = COALESCE((SELECT pe.restSeconds FROM programme_exercises pe WHERE pe.exerciseId = exercises.id ORDER BY pe.position LIMIT 1), 120)
                """.trimIndent(),
            )
            db.execSQL(
                """
                UPDATE exercises
                SET inputConfig = 'weightUnit=;bandResistance=false;timeHeld=true;timeUnderTension=true;reps=false'
                WHERE id = 'core:handstand' AND inputConfig = ''
                """.trimIndent(),
            )
            db.execSQL("UPDATE programme_days SET name = 'Stretching', position = 0 WHERE id = 'demo-day-front-splits' AND programmeId = 'demo-programme-stretch'")
            db.execSQL(
                """
                UPDATE programme_exercises
                SET programmeDayId = 'demo-day-front-splits',
                    position = CASE exerciseId
                        WHEN 'core:front-split' THEN 0
                        WHEN 'core:forward-fold' THEN 1
                        WHEN 'core:side-split' THEN 2
                        WHEN 'core:bridge' THEN 3
                        ELSE position
                    END
                WHERE programmeDayId IN (
                    'demo-day-front-splits',
                    'demo-day-forward-fold',
                    'demo-day-side-splits',
                    'demo-day-bridge'
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                DELETE FROM programme_days
                WHERE id IN ('demo-day-forward-fold', 'demo-day-side-splits', 'demo-day-bridge')
                  AND programmeId = 'demo-programme-stretch'
                """.trimIndent(),
            )
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE trackers ADD COLUMN colourArgb INTEGER NOT NULL DEFAULT 4283215696")
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE trackers ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE trackers ADD COLUMN heatmapLightBelow REAL NOT NULL DEFAULT 7")
        db.execSQL("ALTER TABLE trackers ADD COLUMN heatmapMediumBelow REAL NOT NULL DEFAULT 15")
        db.execSQL(
            """
            UPDATE trackers
            SET position = (
                SELECT COUNT(*)
                FROM trackers AS preceding
                WHERE lower(preceding.name) < lower(trackers.name)
                   OR (lower(preceding.name) = lower(trackers.name) AND preceding.id < trackers.id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            UPDATE trackers
            SET heatmapLightBelow = 140, heatmapMediumBelow = 160
            WHERE lower(name) = 'protein'
            """.trimIndent(),
        )
    }

}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracker_fields ADD COLUMN choiceLightThrough INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE tracker_fields ADD COLUMN choiceDarkFrom INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE trackers ADD COLUMN iconName TEXT NOT NULL DEFAULT 'habit'")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE exercise_variations ADD COLUMN trackingType TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE exercise_variations ADD COLUMN inputConfig TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE exercise_variations ADD COLUMN targetSets INTEGER")
            db.execSQL("ALTER TABLE exercise_variations ADD COLUMN targetRepsMin INTEGER")
            db.execSQL("ALTER TABLE exercise_variations ADD COLUMN targetRepsMax INTEGER")
            db.execSQL("ALTER TABLE exercise_variations ADD COLUMN targetRpe REAL")
            db.execSQL("ALTER TABLE exercise_variations ADD COLUMN restSeconds INTEGER NOT NULL DEFAULT 120")
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN variationNameSnapshot TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN variationTrackingTypeSnapshot TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE workout_sets ADD COLUMN variationInputConfigSnapshot TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """
                UPDATE exercise_variations
                SET trackingType = (SELECT trackingType FROM exercises WHERE id = exerciseId),
                    inputConfig = (SELECT inputConfig FROM exercises WHERE id = exerciseId),
                    targetSets = (SELECT targetSets FROM exercises WHERE id = exerciseId),
                    targetRepsMin = (SELECT targetRepsMin FROM exercises WHERE id = exerciseId),
                    targetRepsMax = (SELECT targetRepsMax FROM exercises WHERE id = exerciseId),
                    targetRpe = (SELECT targetRpe FROM exercises WHERE id = exerciseId),
                    restSeconds = (SELECT restSeconds FROM exercises WHERE id = exerciseId)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO exercise_variations
                    (id,exerciseId,name,progressionRank,isSeeded,trackingType,inputConfig,targetSets,targetRepsMin,targetRepsMax,targetRpe,restSeconds)
                SELECT 'pull-up-assisted','core:pull-up','Band-assisted pull-up',10,1,'ASSISTED_REPS',
                    'weightUnit=;bandResistance=true;timeHeld=false;timeUnderTension=false;reps=true;bodyweight=true;addedWeight=false',
                    targetSets,targetRepsMin,targetRepsMax,targetRpe,restSeconds
                FROM exercises WHERE id='core:pull-up'
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO exercise_variations
                    (id,exerciseId,name,progressionRank,isSeeded,trackingType,inputConfig,targetSets,targetRepsMin,targetRepsMax,targetRpe,restSeconds)
                SELECT 'pull-up-bodyweight','core:pull-up','Bodyweight pull-up',20,1,'BODYWEIGHT_REPS',
                    'weightUnit=;bandResistance=false;timeHeld=false;timeUnderTension=false;reps=true;bodyweight=true;addedWeight=false',
                    targetSets,targetRepsMin,targetRepsMax,targetRpe,restSeconds
                FROM exercises WHERE id='core:pull-up'
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO exercise_variations
                    (id,exerciseId,name,progressionRank,isSeeded,trackingType,inputConfig,targetSets,targetRepsMin,targetRepsMax,targetRpe,restSeconds)
                SELECT 'pull-up-weighted','core:pull-up','Weighted pull-up',30,1,'WEIGHT_REPS',
                    'weightUnit=;bandResistance=false;timeHeld=false;timeUnderTension=false;reps=true;bodyweight=true;addedWeight=true',
                    targetSets,targetRepsMin,targetRepsMax,targetRpe,restSeconds
                FROM exercises WHERE id='core:pull-up'
                """.trimIndent(),
            )
            db.execSQL(
                """
                UPDATE workout_sets
                SET variationNameSnapshot = COALESCE((SELECT name FROM exercise_variations WHERE id = variationId), ''),
                    variationTrackingTypeSnapshot = COALESCE((SELECT trackingType FROM exercise_variations WHERE id = variationId), ''),
                    variationInputConfigSnapshot = COALESCE((SELECT inputConfig FROM exercise_variations WHERE id = variationId), '')
                WHERE variationId IS NOT NULL
                """.trimIndent(),
            )
            db.execSQL(
                """
                UPDATE exercise_variations
                SET inputConfig = 'weightUnit=;bandResistance=false;timeHeld=true;timeUnderTension=true;reps=false;bodyweight=false;addedWeight=false'
                WHERE id = 'handstand-wall'
                """.trimIndent(),
            )
            db.execSQL(
                """
                UPDATE exercise_variations
                SET inputConfig = 'weightUnit=;bandResistance=false;timeHeld=false;timeUnderTension=true;reps=false;bodyweight=false;addedWeight=false'
                WHERE id = 'handstand-free'
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS exercise_reference_videos (
                    id TEXT NOT NULL,
                    exerciseId TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    fileName TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    PRIMARY KEY(id),
                    FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_reference_videos_exerciseId ON exercise_reference_videos(exerciseId)")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE workout_exercises ADD COLUMN targetSets INTEGER")
            db.execSQL("ALTER TABLE workout_exercises ADD COLUMN targetRepsMin INTEGER")
            db.execSQL("ALTER TABLE workout_exercises ADD COLUMN targetRepsMax INTEGER")
            db.execSQL("ALTER TABLE workout_exercises ADD COLUMN targetHoldSeconds INTEGER")
            db.execSQL("ALTER TABLE workout_exercises ADD COLUMN targetRpe REAL")
            db.execSQL(
                """
                UPDATE programme_exercises
                SET targetSets = (SELECT targetSets FROM exercises WHERE id = exerciseId),
                    targetRepsMin = (SELECT targetRepsMin FROM exercises WHERE id = exerciseId),
                    targetRepsMax = (SELECT targetRepsMax FROM exercises WHERE id = exerciseId),
                    targetRpe = (SELECT targetRpe FROM exercises WHERE id = exerciseId)
                WHERE targetSets IS NULL
                  AND targetRepsMin IS NULL
                  AND targetRepsMax IS NULL
                  AND targetHoldSeconds IS NULL
                  AND targetRpe IS NULL
                """.trimIndent(),
            )
            db.execSQL(
                """
                UPDATE workout_exercises
                SET targetSets = (
                        SELECT pe.targetSets
                        FROM workouts w
                        JOIN programme_exercises pe ON pe.programmeDayId = w.programmeDayId
                        WHERE w.id = workout_exercises.workoutId
                          AND pe.exerciseId = workout_exercises.plannedExerciseId
                          AND pe.position = workout_exercises.position
                        LIMIT 1
                    ),
                    targetRepsMin = (
                        SELECT pe.targetRepsMin
                        FROM workouts w
                        JOIN programme_exercises pe ON pe.programmeDayId = w.programmeDayId
                        WHERE w.id = workout_exercises.workoutId
                          AND pe.exerciseId = workout_exercises.plannedExerciseId
                          AND pe.position = workout_exercises.position
                        LIMIT 1
                    ),
                    targetRepsMax = (
                        SELECT pe.targetRepsMax
                        FROM workouts w
                        JOIN programme_exercises pe ON pe.programmeDayId = w.programmeDayId
                        WHERE w.id = workout_exercises.workoutId
                          AND pe.exerciseId = workout_exercises.plannedExerciseId
                          AND pe.position = workout_exercises.position
                        LIMIT 1
                    ),
                    targetHoldSeconds = (
                        SELECT pe.targetHoldSeconds
                        FROM workouts w
                        JOIN programme_exercises pe ON pe.programmeDayId = w.programmeDayId
                        WHERE w.id = workout_exercises.workoutId
                          AND pe.exerciseId = workout_exercises.plannedExerciseId
                          AND pe.position = workout_exercises.position
                        LIMIT 1
                    ),
                    targetRpe = (
                        SELECT pe.targetRpe
                        FROM workouts w
                        JOIN programme_exercises pe ON pe.programmeDayId = w.programmeDayId
                        WHERE w.id = workout_exercises.workoutId
                          AND pe.exerciseId = workout_exercises.plannedExerciseId
                          AND pe.position = workout_exercises.position
                        LIMIT 1
                    )
                """.trimIndent(),
            )
        }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tracker_fields ADD COLUMN choiceOptionsJson TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE tracker_daily_values ADD COLUMN choiceOptionId TEXT")
        db.execSQL("ALTER TABLE tracker_daily_values ADD COLUMN choiceIntensity TEXT")
        val choicesByField = mutableMapOf<String, List<com.petermathie.vibecheck.domain.tracker.HabitChoiceOption>>()
        db.query(
            """
            SELECT id,choiceOptions,choiceLightThrough,choiceDarkFrom
            FROM tracker_fields
            WHERE UPPER(valueType) = 'CHOICE'
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val fieldId = cursor.getString(0)
                val options = legacyHabitChoices(fieldId, cursor.getString(1), cursor.getInt(2), cursor.getInt(3))
                choicesByField[fieldId] = options
                db.execSQL(
                    "UPDATE tracker_fields SET choiceOptionsJson=? WHERE id=?",
                    arrayOf(encodeHabitChoices(options), fieldId),
                )
            }
        }
        db.query(
            """
            SELECT fieldId,epochDay,textValue
            FROM tracker_daily_values
            WHERE textValue IS NOT NULL
              AND fieldId IN (SELECT id FROM tracker_fields WHERE valueType = 'CHOICE')
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val fieldId = cursor.getString(0)
                val option = legacyHabitChoiceSnapshot(
                    fieldId,
                    cursor.getString(2),
                    choicesByField[fieldId].orEmpty(),
                )
                db.execSQL(
                    """
                    UPDATE tracker_daily_values
                    SET choiceOptionId=?,choiceIntensity=?
                    WHERE fieldId=? AND epochDay=?
                    """.trimIndent(),
                    arrayOf(option.id, option.intensity.name, fieldId, cursor.getLong(1)),
                )
            }
        }
    }
}

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TEMP TABLE workout_set_bands_backup AS
            SELECT setId,bandId,ordinal,nameSnapshot,widthCentimetresSnapshot
            FROM workout_set_bands
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE workout_sets_new (
                id TEXT NOT NULL,
                workoutExerciseId TEXT NOT NULL,
                ordinal INTEGER NOT NULL,
                setType TEXT NOT NULL,
                result TEXT NOT NULL,
                variationId TEXT,
                weightKg REAL,
                reps REAL,
                holdMillis INTEGER,
                leftReps REAL,
                rightReps REAL,
                leftHoldMillis INTEGER,
                rightHoldMillis INTEGER,
                addedWeightKg REAL,
                assistanceKg REAL,
                rpe REAL,
                romValue REAL,
                romUnit TEXT,
                notes TEXT NOT NULL,
                loggedAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                variationRankSnapshot INTEGER,
                timeUnderTensionMillis INTEGER,
                bandResistance INTEGER NOT NULL DEFAULT 0,
                variationNameSnapshot TEXT NOT NULL DEFAULT '',
                variationTrackingTypeSnapshot TEXT NOT NULL DEFAULT '',
                variationInputConfigSnapshot TEXT NOT NULL DEFAULT '',
                legacyReps INTEGER,
                legacyLeftReps INTEGER,
                legacyRightReps INTEGER,
                PRIMARY KEY(id),
                FOREIGN KEY(workoutExerciseId) REFERENCES workout_exercises(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO workout_sets_new (
                id,workoutExerciseId,ordinal,setType,result,variationId,weightKg,
                reps,holdMillis,leftReps,rightReps,leftHoldMillis,rightHoldMillis,
                addedWeightKg,assistanceKg,rpe,romValue,romUnit,notes,loggedAt,updatedAt,
                variationRankSnapshot,timeUnderTensionMillis,bandResistance,
                variationNameSnapshot,variationTrackingTypeSnapshot,variationInputConfigSnapshot,
                legacyReps,legacyLeftReps,legacyRightReps
            )
            SELECT
                id,workoutExerciseId,ordinal,setType,result,variationId,weightKg,
                CAST(reps AS REAL),holdMillis,CAST(leftReps AS REAL),CAST(rightReps AS REAL),
                leftHoldMillis,rightHoldMillis,addedWeightKg,assistanceKg,rpe,romValue,romUnit,
                notes,loggedAt,updatedAt,variationRankSnapshot,timeUnderTensionMillis,bandResistance,
                variationNameSnapshot,variationTrackingTypeSnapshot,variationInputConfigSnapshot,
                reps,leftReps,rightReps
            FROM workout_sets
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE workout_sets")
        db.execSQL("ALTER TABLE workout_sets_new RENAME TO workout_sets")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_workout_sets_workoutExerciseId ON workout_sets(workoutExerciseId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_workout_sets_loggedAt ON workout_sets(loggedAt)",
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO workout_set_bands (
                setId,bandId,ordinal,nameSnapshot,widthCentimetresSnapshot
            )
            SELECT setId,bandId,ordinal,nameSnapshot,widthCentimetresSnapshot
            FROM workout_set_bands_backup
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE workout_set_bands_backup")
    }
}

val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE workouts
            SET name = (
                SELECT p.name
                FROM programme_days d
                JOIN programmes p ON p.id = d.programmeId
                WHERE d.id = workouts.programmeDayId
            )
            WHERE name IN ('Workout', 'Stretching')
              AND programmeDayId IS NOT NULL
              AND EXISTS (
                SELECT 1
                FROM programme_days d
                JOIN programmes p ON p.id = d.programmeId
                WHERE d.id = workouts.programmeDayId
                  AND d.name IN ('Workout', 'Stretching')
                  AND p.name NOT IN ('', 'Workout', 'Stretching')
            )
            """.trimIndent(),
        )

        listOf("programme_exercises", "workout_exercises").forEach { table ->
            val updates = mutableListOf<Pair<String, String>>()
            db.query(
                "SELECT id, notes FROM $table WHERE notes LIKE 'Private programme source %.md, line %'",
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val original = cursor.getString(1)
                    val sanitized = sanitizeExerciseNotes(original)
                    if (sanitized != original) updates += cursor.getString(0) to sanitized
                }

            }
            updates.forEach { (id, notes) ->
                db.execSQL("UPDATE $table SET notes=? WHERE id=?", arrayOf(notes, id))
            }
        }

        db.execSQL(
            """
            UPDATE exercises
            SET trackingType='HOLD', inputConfig=?
            WHERE id='core:front-split'
              AND source='vibe-trainer'
              AND isCustom=0
              AND trackingType='ROM_MEASUREMENT'
              AND inputConfig=''
            """.trimIndent(),
            arrayOf(FRONT_SPLIT_HOLD_INPUT_CONFIG),
        )
        db.execSQL(
            """
            UPDATE workout_exercises
            SET trackingType='HOLD', inputConfig=?
            WHERE actualExerciseId='core:front-split'
              AND trackingType='ROM_MEASUREMENT'
              AND inputConfig=''
              AND workoutId IN (SELECT id FROM workouts WHERE status='DRAFT')
            """.trimIndent(),
            arrayOf(FRONT_SPLIT_HOLD_INPUT_CONFIG),
        )
    }
}

val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE workout_sets ADD COLUMN timeMeasurementsMillis TEXT NOT NULL DEFAULT '[]'",
        )
        db.execSQL(
            "ALTER TABLE workout_entry_drafts ADD COLUMN timeMeasurements TEXT NOT NULL DEFAULT '[]'",
        )
    }
}

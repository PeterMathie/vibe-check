package com.petermathie.vibecheck.data.seed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.room.withTransaction
import com.petermathie.vibecheck.BuildConfig
import com.petermathie.vibecheck.data.local.BandEntity
import com.petermathie.vibecheck.data.local.ExerciseAliasEntity
import com.petermathie.vibecheck.data.local.ExerciseEntity
import com.petermathie.vibecheck.data.local.ExerciseMuscleEntity
import com.petermathie.vibecheck.data.local.ExerciseVariationEntity
import com.petermathie.vibecheck.data.local.MuscleEntity
import com.petermathie.vibecheck.data.local.ProgrammeDayEntity
import com.petermathie.vibecheck.data.local.ProgrammeEntity
import com.petermathie.vibecheck.data.local.ProgrammeExerciseEntity
import com.petermathie.vibecheck.data.local.SeedMetadataEntity
import com.petermathie.vibecheck.data.local.TrackerDailyValueEntity
import com.petermathie.vibecheck.data.local.TrackerEntity
import com.petermathie.vibecheck.data.local.TrackerFieldEntity
import com.petermathie.vibecheck.domain.tracker.encodeHabitChoices
import com.petermathie.vibecheck.domain.tracker.legacyHabitChoices
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.local.WorkoutEntity
import com.petermathie.vibecheck.data.local.WorkoutExerciseEntity
import com.petermathie.vibecheck.data.local.WorkoutSetEntity
import com.petermathie.vibecheck.domain.model.ExerciseTag
import com.petermathie.vibecheck.domain.model.MuscleRole
import com.petermathie.vibecheck.domain.model.SetResult
import com.petermathie.vibecheck.domain.model.SetType
import com.petermathie.vibecheck.domain.model.TrackingType
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.domain.model.WorkoutStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: VibeDatabase,
) {
    suspend fun seedIfNeeded() {
        database.withTransaction {
            val catalogueVersion = database.metadataDao().version(CATALOGUE_KEY) ?: 0
            if (catalogueVersion < CATALOGUE_VERSION) {
                if (catalogueVersion == 0) {
                    seedCatalogue()
                } else {
                    database.catalogueDao().insertExerciseMuscles(CURATED_MAPPINGS)
                }
                database.metadataDao().put(SeedMetadataEntity(CATALOGUE_KEY, CATALOGUE_VERSION))
            }
            if ((database.metadataDao().version(CURATED_ONLY_KEY) ?: 0) < CURATED_ONLY_VERSION) {
                database.openHelper.writableDatabase.execSQL(
                    """
                    UPDATE exercises
                    SET isArchived = 1
                    WHERE (source = 'free-exercise-db' OR id = 'core:lat-pulldown')
                      AND id NOT IN (SELECT exerciseId FROM programme_exercises)
                    """.trimIndent(),
                )
                database.openHelper.writableDatabase.execSQL("UPDATE workouts SET notes = '' WHERE isDemo = 1")
                database.metadataDao().put(SeedMetadataEntity(CURATED_ONLY_KEY, CURATED_ONLY_VERSION))
            }
            if (BuildConfig.SEED_DEMO_DATA && (database.metadataDao().version(DEMO_KEY) ?: 0) < DEMO_VERSION) {
                seedDemo()
                database.metadataDao().put(SeedMetadataEntity(DEMO_KEY, DEMO_VERSION))
            }
            if (BuildConfig.SEED_DEMO_DATA && (database.metadataDao().version(SCHEDULE_FREE_DEMO_KEY) ?: 0) < SCHEDULE_FREE_DEMO_VERSION) {
                val sql = database.openHelper.writableDatabase
                sql.execSQL("UPDATE programme_days SET name='Planche + Push' WHERE id='demo-day-push' AND name='Monday — Planche + Push'")
                sql.execSQL("UPDATE programme_days SET name='Legs + Mobility' WHERE id='demo-day-legs' AND name='Wednesday — Legs + Mobility'")
                sql.execSQL("UPDATE programme_days SET name='Muscle-up + Pull' WHERE id='demo-day-pull' AND name='Saturday — Muscle-up + Pull'")
                database.metadataDao().put(SeedMetadataEntity(SCHEDULE_FREE_DEMO_KEY, SCHEDULE_FREE_DEMO_VERSION))
            }
            if (BuildConfig.SEED_DEMO_DATA && (database.metadataDao().version(PROGRESS_DEMO_KEY) ?: 0) < PROGRESS_DEMO_VERSION) {
                if(database.workoutDao().demoCount() > 0) seedProgressDemo()
                database.metadataDao().put(SeedMetadataEntity(PROGRESS_DEMO_KEY,PROGRESS_DEMO_VERSION))
            }
            // Demo records use the same historical snapshots as real workouts.
            val sql=database.openHelper.writableDatabase
            sql.execSQL("INSERT OR IGNORE INTO workout_muscles SELECT we.id,em.muscleId,em.role FROM workout_exercises we JOIN exercise_muscles em ON em.exerciseId=we.actualExerciseId")
            sql.execSQL("UPDATE workout_exercises SET exerciseName=(SELECT canonicalName FROM exercises WHERE id=actualExerciseId),trackingType=(SELECT trackingType FROM exercises WHERE id=actualExerciseId) WHERE exerciseName=''")
        }
        if (BuildConfig.SEED_DEMO_DATA) DemoProgressPhotos.claimLegacyPhotos(context, database)
    }

    private suspend fun seedCatalogue() {
        database.catalogueDao().insertMuscles(MUSCLES)
        database.catalogueDao().insertBands(BANDS)
        database.catalogueDao().insertExercises(CURATED_EXERCISES)
        database.catalogueDao().insertAliases(
            CURATED_ALIASES.flatMap { (exerciseId, values) ->
                values.map { ExerciseAliasEntity(exerciseId, it, it.normalized()) }
            },
        )
        database.catalogueDao().insertExerciseMuscles(CURATED_MAPPINGS)
        database.catalogueDao().insertVariations(SKILL_VARIATIONS)
    }

    private suspend fun seedDemo() {
        database.programmeDao().insertProgrammes(DEMO_PROGRAMMES)
        database.programmeDao().insertDays(DEMO_DAYS)
        database.programmeDao().insertProgrammeExercises(DEMO_PROGRAMME_EXERCISES)
        database.trackerDao().insertTrackers(DEMO_TRACKERS)
        database.trackerDao().insertFields(DEMO_TRACKER_FIELDS)

        val now = System.currentTimeMillis()
        val dayMillis = 86_400_000L
        val workouts = listOf(
            demoWorkout("demo-workout-push-1", "Planche + Push", "demo-day-push", now - dayMillis, TrainingMode.STRENGTH),
            demoWorkout("demo-workout-legs-1", "Legs + Mobility", "demo-day-legs", now - 3 * dayMillis, TrainingMode.STRENGTH),
            demoWorkout("demo-workout-pull-1", "Muscle-up + Pull", "demo-day-pull", now - 8 * dayMillis, TrainingMode.STRENGTH),
            demoWorkout("demo-stretch-1", "Front Splits", "demo-day-front-splits", now - 2 * dayMillis, TrainingMode.STRETCHING),
        )
        workouts.forEach { database.workoutDao().insertWorkout(it) }

        val workoutExercises = mutableListOf<WorkoutExerciseEntity>()
        val sets = mutableListOf<WorkoutSetEntity>()
        fun add(workoutId: String, exerciseId: String, position: Int, reps: Int? = null, hold: Long? = null, weight: Double? = null) {
            val weId = "$workoutId:$exerciseId"
            workoutExercises += WorkoutExerciseEntity(
                id = weId,
                workoutId = workoutId,
                plannedExerciseId = exerciseId,
                actualExerciseId = exerciseId,
                position = position,
                notes = if (position == 0) "Demo note: technique felt controlled" else "",
                restSeconds = 120,
                supersetGroup = null,
            )
            repeat(3) { ordinal ->
                sets += WorkoutSetEntity(
                    id = "$weId:$ordinal",
                    workoutExerciseId = weId,
                    ordinal = ordinal + 1,
                    setType = SetType.WORKING.name,
                    result = SetResult.COMPLETED.name,
                    variationId = null,
                    weightKg = weight,
                    reps = reps?.toDouble(),
                    holdMillis = hold,
                    leftReps = null,
                    rightReps = null,
                    leftHoldMillis = null,
                    rightHoldMillis = null,
                    addedWeightKg = null,
                    assistanceKg = null,
                    rpe = 7.0 + ordinal * 0.5,
                    romValue = null,
                    romUnit = null,
                    notes = if (ordinal == 2) "Last set moved slowly" else "",
                    loggedAt = now - dayMillis,
                    updatedAt = now - dayMillis,
                )
            }
        }
        add("demo-workout-push-1", "core:handstand", 0, hold = 8_000)
        add("demo-workout-push-1", "core:planche", 1, hold = 6_000)
        add("demo-workout-push-1", "core:bench-press", 2, reps = 4, weight = 70.0)
        add("demo-workout-legs-1", "core:squat", 0, reps = 6, weight = 90.0)
        add("demo-workout-legs-1", "core:cossack-squat", 1, reps = 6, weight = 16.0)
        add("demo-workout-pull-1", "core:pull-up", 0, reps = 6)
        add("demo-workout-pull-1", "core:overhead-press", 1, reps = 5, weight = 45.0)
        add("demo-stretch-1", "core:front-split", 0, hold = 30_000)
        database.workoutDao().insertWorkoutExercises(workoutExercises)
        sets.forEach { database.workoutDao().insertSet(it) }

        val today = LocalDate.now().toEpochDay()
        listOf(
            TrackerDailyValueEntity("demo-piano-minutes", today - 1, 35.0, null, null, "", now),
            TrackerDailyValueEntity("demo-meditation-minutes", today - 1, 12.0, null, null, "", now),
            TrackerDailyValueEntity("demo-protein-grams", today - 2, 122.0, null, null, "", now),
        ).forEach { database.trackerDao().upsertValue(it) }
    }

    private suspend fun seedProgressDemo() {
        database.trackerDao().insertTrackers(DEMO_EXTRA_TRACKERS)
        database.trackerDao().insertFields(DEMO_EXTRA_TRACKER_FIELDS)
        database.openHelper.writableDatabase.execSQL(
            "UPDATE tracker_fields SET valueType='NUMBER' WHERE trackerId IN ('demo-piano','demo-meditation','demo-protein')",
        )
        database.openHelper.writableDatabase.execSQL(
            """
            UPDATE trackers
            SET iconName = CASE id
                WHEN 'demo-piano' THEN 'piano'
                WHEN 'demo-meditation' THEN 'meditation'
                WHEN 'demo-protein' THEN 'protein'
                WHEN 'demo-mood' THEN 'mood'
                WHEN 'demo-journal' THEN 'journal'
                WHEN 'demo-reading' THEN 'reading'
                ELSE iconName
            END
            WHERE id LIKE 'demo-%'
            """.trimIndent(),
        )
        database.openHelper.writableDatabase.execSQL("DELETE FROM workouts WHERE id LIKE 'demo-progress-%'")
        database.openHelper.writableDatabase.execSQL("DELETE FROM body_measurements WHERE isDemo = 1")
        val now = System.currentTimeMillis()
        val programmes = listOf(
            Triple("push", "demo-day-push", "Planche + Push"),
            Triple("legs", "demo-day-legs", "Legs + Mobility"),
            Triple("pull", "demo-day-pull", "Muscle-up + Pull"),
        )
        val workouts = mutableListOf<WorkoutEntity>()
        val rows = mutableListOf<WorkoutExerciseEntity>()
        val sets = mutableListOf<WorkoutSetEntity>()
        val bandLinks = mutableListOf<com.petermathie.vibecheck.data.local.WorkoutSetBandEntity>()
        repeat(52) { week ->
            programmes.forEachIndexed { sessionIndex, (key, dayId, name) ->
                val finished = now - (52 - week) * 7 * 86_400_000L + sessionIndex * 2 * 86_400_000L
                val workoutId = "demo-progress-$week-$key"
                workouts += demoWorkout(workoutId, name, dayId, finished, TrainingMode.STRENGTH)
                DEMO_PROGRAMME_EXERCISES
                    .filter { it.programmeDayId == dayId }
                    .sortedBy { it.position }
                    .forEach { planned ->
                        val definition = CURATED_EXERCISES.first { it.id == planned.exerciseId }
                        val direction = progressDirection(definition.id)
                        val row = WorkoutExerciseEntity(
                            id = "$workoutId:${definition.id}",
                            workoutId = workoutId,
                            plannedExerciseId = definition.id,
                            actualExerciseId = definition.id,
                            position = planned.position,
                            notes = when (direction) {
                                1 -> "Gradually improving"
                                0 -> "Holding a steady plateau"
                                else -> "Recent performance has declined"
                            },
                            restSeconds = planned.restSeconds,
                            supersetGroup = null,
                            exerciseName = definition.canonicalName,
                            trackingType = definition.trackingType,
                            inputConfig = definition.inputConfig,
                            targetSets = planned.targetSets,
                            targetRepsMin = planned.targetRepsMin,
                            targetRepsMax = planned.targetRepsMax,
                            targetHoldSeconds = planned.targetHoldSeconds,
                            targetRpe = planned.targetRpe,
                            targets = com.petermathie.vibecheck.domain.programme.prescriptionSummary(
                                planned.targetSets,
                                planned.targetRepsMin,
                                planned.targetRepsMax,
                                planned.targetHoldSeconds,
                                planned.targetRpe,
                            ),
                        )
                        rows += row
                        repeat(3) { ordinal ->
                            val set = demoProgressSet(row, definition, week, ordinal, direction, finished)
                            sets += set
                            if (definition.id == "core:planche") {
                                val band = BANDS[if (week < 18) 2 else if (week < 36) 1 else 0]
                                bandLinks += com.petermathie.vibecheck.data.local.WorkoutSetBandEntity(
                                    set.id,
                                    band.id,
                                    0,
                                    band.name,
                                    band.widthCentimetres,
                                )
                            }
                        }
                    }
            }
        }
        database.workoutDao().insertWorkouts(workouts)
        database.workoutDao().insertWorkoutExercises(rows)
        database.workoutDao().insertSets(sets)
        database.workoutDao().insertSetBands(bandLinks)
        repeat(52) { week ->
            val recordedAt = now - (52 - week) * 7 * 86_400_000L
            database.editorDao().measurement(
                com.petermathie.vibecheck.data.local.BodyMeasurementEntity(
                    id = "demo-bodyweight-$week",
                    recordedAt = recordedAt,
                    metric = "Bodyweight",
                    value = 78.0 - week * 0.07 + (week % 5 - 2) * 0.08,
                    unit = "kg",
                    notes = "",
                    isDemo = true,
                ),
            )
            if (week in setOf(0, 17, 34, 51)) seedDemoProgressPhoto(recordedAt, week)
            val weekStart = Instant.ofEpochMilli(recordedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
            repeat(7) { day ->
                if (day in listOf(0, 2, 4, 6)) {
                    database.trackerDao().upsertValue(
                        TrackerDailyValueEntity(
                            "demo-piano-minutes",
                            weekStart + day,
                            listOf(5.0, 10.0, 20.0)[(week + day) % 3],
                            null,
                            null,
                            "",
                            now,
                        ),
                    )
                }
                if (day < 5) {
                    database.trackerDao().upsertValue(
                        TrackerDailyValueEntity(
                            "demo-meditation-minutes",
                            weekStart + day,
                            listOf(5.0, 10.0, 20.0)[(week + day) % 3],
                            null,
                            null,
                            "",
                            now,
                        ),
                    )
                }
                if (day != 5) {
                    database.trackerDao().upsertValue(
                        TrackerDailyValueEntity(
                            "demo-protein-grams",
                            weekStart + day,
                            listOf(130.0, 150.0, 170.0)[(week + day) % 3],
                            null,
                            null,
                            "",
                            now,
                        ),
                    )
                }
                val moodOption = legacyHabitChoices(
                    "demo-mood-feeling",
                    MOOD_CHOICES.joinToString("\n"),
                    2,
                    6,
                )[(week + day) % MOOD_CHOICES.size]
                database.trackerDao().upsertValue(
                    TrackerDailyValueEntity(
                        "demo-mood-feeling",
                        weekStart + day,
                        null,
                        null,
                        moodOption.label,
                        "",
                        now,
                        moodOption.id,
                        moodOption.intensity.name,
                    ),
                )
                if (day in listOf(1, 4)) {
                    database.trackerDao().upsertValue(
                        TrackerDailyValueEntity(
                            "demo-journal-entry",
                            weekStart + day,
                            null,
                            null,
                            if ((week + day) % 2 == 0) "A calm and productive day." else "Noticed what helped today.",
                            "",
                            now,
                        ),
                    )
                }
                database.trackerDao().upsertValue(
                    TrackerDailyValueEntity(
                        "demo-reading-completed",
                        weekStart + day,
                        null,
                        (week + day) % 3 != 0,
                        null,
                        "",
                        now,
                    ),
                )
            }
        }
    }

    private fun seedDemoProgressPhoto(recordedAt: Long, week: Int) {
        val directory = java.io.File(context.filesDir, "progress-photos").apply { mkdirs() }
        val file = java.io.File(directory, "$recordedAt.jpg")
        if (file.exists()) return
        val bitmap = Bitmap.createBitmap(720, 960, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val (background, accent) = DemoProgressPhotos.palette(week)
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
        canvas.drawCircle(360f, 230f, 105f, paint)
        canvas.drawRoundRect(215f, 350f, 505f, 790f, 120f, 120f, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 42f
        canvas.drawText("Demo progress", 360f, 890f, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        bitmap.recycle()
        DemoProgressPhotos.markOwned(context, file)
    }

    private fun progressDirection(exerciseId: String): Int = when (exerciseId) {
        "core:handstand", "core:planche", "core:bench-press", "core:squat", "core:pull-up" -> 1
        "core:overhead-press", "core:jefferson-curl", "core:leg-raise" -> -1
        else -> 0
    }

    private fun demoProgressSet(
        row: WorkoutExerciseEntity,
        exercise: ExerciseEntity,
        week: Int,
        ordinal: Int,
        direction: Int,
        finished: Long,
    ): WorkoutSetEntity {
        val cycle = (week % 6 - 3) * 0.2
        val weightedBase = when (exercise.id) {
            "core:bench-press" -> 62.5
            "core:squat" -> 85.0
            "core:lunge" -> 22.5
            "core:cossack-squat" -> 15.0
            "core:jefferson-curl" -> 35.0
            "core:overhead-press" -> 47.5
            "core:back-extension" -> 30.0
            else -> 40.0
        }
        val weight = when (exercise.trackingType) {
            TrackingType.WEIGHT_REPS.name -> when (direction) {
                1 -> weightedBase + week * 0.45
                -1 -> (weightedBase - week * 0.18).coerceAtLeast(weightedBase * 0.7)
                else -> weightedBase + cycle
            } - ordinal * 0.5
            else -> null
        }
        val reps = when (exercise.trackingType) {
            TrackingType.SKILL_HOLD.name, TrackingType.HOLD.name -> null
            TrackingType.ASSISTED_REPS.name -> 5 - ordinal.coerceAtMost(2)
            else -> when (direction) {
                1 -> 5 + week / 13
                -1 -> (10 - week / 10).coerceAtLeast(4)
                else -> 7 + (week % 4) / 3
            } - ordinal.coerceAtMost(1)
        }
        val hold = when (exercise.trackingType) {
            TrackingType.SKILL_HOLD.name, TrackingType.HOLD.name -> when (direction) {
                1 -> 6_000L + week * 300L - ordinal * 300L
                -1 -> (18_000L - week * 180L - ordinal * 250L).coerceAtLeast(6_000L)
                else -> 11_000L + (week % 5 - 2) * 250L - ordinal * 200L
            }
            else -> null
        }
        val assistance = if (exercise.trackingType == TrackingType.ASSISTED_REPS.name) {
            when (direction) {
                1 -> (24.0 - week * 0.3).coerceAtLeast(5.0)
                -1 -> 6.0 + week * 0.25
                else -> 14.0 + cycle
            }
        } else {
            null
        }
        val variation = if (exercise.id == "core:planche") {
            if (week < 30) "planche-tuck" else "planche-advanced-tuck"
        } else {
            null
        }
        return WorkoutSetEntity(
            id = "${row.id}:$ordinal",
            workoutExerciseId = row.id,
            ordinal = ordinal + 1,
            setType = SetType.WORKING.name,
            result = SetResult.COMPLETED.name,
            variationId = variation,
            weightKg = weight,
            reps = reps?.toDouble(),
            holdMillis = hold,
            leftReps = null,
            rightReps = null,
            leftHoldMillis = null,
            rightHoldMillis = null,
            addedWeightKg = null,
            assistanceKg = assistance,
            rpe = (7.0 + ordinal * 0.5 + if (direction < 0) week / 52.0 else 0.0).coerceAtMost(10.0),
            romValue = null,
            romUnit = null,
            notes = "",
            loggedAt = finished - 600_000L + row.position * 60_000L + ordinal * 10_000L,
            updatedAt = finished,
            variationRankSnapshot = when (variation) {
                "planche-tuck" -> 10
                "planche-advanced-tuck" -> 20
                else -> null
            },
            timeUnderTensionMillis = if (exercise.id == "core:handstand") {
                (hold ?: 0L) + 12_000L
            } else {
                null
            },
        )
    }

    private fun demoWorkout(id: String, name: String, dayId: String, finishedAt: Long, mode: TrainingMode) = WorkoutEntity(
        id = id,
        programmeDayId = dayId,
        name = name,
        mode = mode.name,
        status = WorkoutStatus.FINISHED.name,
        startedAt = finishedAt - 3_600_000L,
        finishedAt = finishedAt,
        notes = "",
        bodyweightKg = 76.0,
        isDemo = true,
    )

    private fun String.normalized(): String = lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    companion object {
        private const val CATALOGUE_KEY = "exercise-catalogue"
        private const val CATALOGUE_VERSION = 2
        private const val CURATED_ONLY_KEY = "curated-exercise-catalogue"
        private const val CURATED_ONLY_VERSION = 1
        private const val DEMO_KEY = "debug-demo"
        private const val DEMO_VERSION = 1
        private const val SCHEDULE_FREE_DEMO_KEY = "schedule_free_demo"
        private const val SCHEDULE_FREE_DEMO_VERSION = 1
        private const val PROGRESS_DEMO_KEY = "progress_demo"
        private const val PROGRESS_DEMO_VERSION = 10
        private val MOOD_CHOICES = listOf(
            "Sad",
            "Tired",
            "Irritated",
            "Tense",
            "Neutral",
            "Calm",
            "Alert",
            "Happy",
            "Excited",
        )

        private val MUSCLES = listOf(
            "ABDUCTORS" to "Abductors", "ADDUCTORS" to "Adductors", "BACK_LOWER" to "Lower back",
            "BICEPS" to "Biceps", "CALVES" to "Calves", "CHEST" to "Chest", "CORE" to "Abdominals",
            "FOREARMS" to "Forearms", "GLUTES" to "Glutes", "HAMSTRINGS" to "Hamstrings",
            "LATS" to "Lats", "OBLIQUES" to "Obliques", "QUADS" to "Quadriceps",
            "RHOMBOIDS" to "Rhomboids / middle back", "SHOULDERS_FRONT" to "Front deltoids",
            "SHOULDERS_REAR" to "Rear deltoids", "SHOULDERS_SIDE" to "Side deltoids",
            "TRAPEZIUS" to "Trapezius", "TRICEPS" to "Triceps",
        ).map { (id, name) -> MuscleEntity(id, name, id) }

        private val BANDS = listOf(
            BandEntity("band-yellow", "Yellow", 0.6, 0xFFFFD84DL),
            BandEntity("band-red", "Red", 1.2, 0xFFE94747L),
            BandEntity("band-black", "Black", 2.2, 0xFF1A1A1AL),
            BandEntity("band-purple", "Purple", 3.1, 0xFF7446B8L),
        )

        private fun curated(id: String, name: String, tracking: TrackingType, tag: ExerciseTag = ExerciseTag.STRENGTH) =
            ExerciseEntity(
                id,
                name,
                tag.name,
                tracking.name,
                null,
                null,
                "vibe-trainer",
                false,
                inputConfig = if (id == "core:handstand") {
                    "weightUnit=;bandResistance=false;timeHeld=true;timeUnderTension=true;reps=false"
                } else {
                    ""
                },
            )

        private val CURATED_EXERCISES = listOf(
            curated("core:handstand", "Handstand", TrackingType.SKILL_HOLD),
            curated("core:planche", "Planche", TrackingType.SKILL_HOLD),
            curated("core:muscle-up", "Muscle-up", TrackingType.ASSISTED_REPS),
            curated("core:bench-press", "Bench press", TrackingType.WEIGHT_REPS),
            curated("core:dip", "Dip", TrackingType.BODYWEIGHT_REPS),
            curated("core:leg-raise", "Leg raise", TrackingType.BODYWEIGHT_REPS),
            curated("core:squat", "Back squat", TrackingType.WEIGHT_REPS),
            curated("core:lunge", "Lunge", TrackingType.WEIGHT_REPS),
            curated("core:cossack-squat", "Cossack squat", TrackingType.WEIGHT_REPS),
            curated("core:jefferson-curl", "Jefferson curl", TrackingType.WEIGHT_REPS),
            curated("core:pull-up", "Pull-up", TrackingType.ASSISTED_REPS),
            curated("core:overhead-press", "Overhead press", TrackingType.WEIGHT_REPS),
            curated("core:back-extension", "Back extension", TrackingType.WEIGHT_REPS),
            curated("core:front-split", "Front split", TrackingType.ROM_MEASUREMENT, ExerciseTag.STRETCHING),
            curated("core:forward-fold", "Forward fold", TrackingType.ROM_MEASUREMENT, ExerciseTag.STRETCHING),
            curated("core:side-split", "Side split", TrackingType.ROM_MEASUREMENT, ExerciseTag.STRETCHING),
            curated("core:bridge", "Bridge", TrackingType.ROM_MEASUREMENT, ExerciseTag.STRETCHING),
        )

        private val CURATED_ALIASES = mapOf(
            "core:bench-press" to listOf("bench", "barbell bench"),
            "core:dip" to listOf("dips"),
            "core:pull-up" to listOf("pullup", "pullups", "pull ups", "negative pull-up"),
            "core:overhead-press" to listOf("OHP", "shoulder press"),
            "core:cossack-squat" to listOf("cossack"),
        )

        private fun mapping(exerciseId: String, muscleId: String, role: MuscleRole) = ExerciseMuscleEntity(exerciseId, muscleId, role.name)
        private val CURATED_MAPPINGS = listOf(
            mapping("core:handstand", "SHOULDERS_FRONT", MuscleRole.PRIMARY), mapping("core:handstand", "TRICEPS", MuscleRole.SECONDARY), mapping("core:handstand", "CORE", MuscleRole.SECONDARY), mapping("core:handstand", "TRAPEZIUS", MuscleRole.SECONDARY),
            mapping("core:planche", "SHOULDERS_FRONT", MuscleRole.PRIMARY), mapping("core:planche", "CHEST", MuscleRole.PRIMARY), mapping("core:planche", "TRICEPS", MuscleRole.SECONDARY), mapping("core:planche", "CORE", MuscleRole.SECONDARY),
            mapping("core:muscle-up", "LATS", MuscleRole.PRIMARY), mapping("core:muscle-up", "BICEPS", MuscleRole.SECONDARY), mapping("core:muscle-up", "TRICEPS", MuscleRole.SECONDARY), mapping("core:muscle-up", "CHEST", MuscleRole.SECONDARY), mapping("core:muscle-up", "FOREARMS", MuscleRole.SECONDARY), mapping("core:muscle-up", "RHOMBOIDS", MuscleRole.SECONDARY), mapping("core:muscle-up", "SHOULDERS_REAR", MuscleRole.SECONDARY), mapping("core:muscle-up", "TRAPEZIUS", MuscleRole.SECONDARY),
            mapping("core:bench-press", "CHEST", MuscleRole.PRIMARY), mapping("core:bench-press", "TRICEPS", MuscleRole.SECONDARY), mapping("core:bench-press", "SHOULDERS_FRONT", MuscleRole.SECONDARY),
            mapping("core:dip", "CHEST", MuscleRole.PRIMARY), mapping("core:dip", "TRICEPS", MuscleRole.PRIMARY), mapping("core:dip", "SHOULDERS_FRONT", MuscleRole.SECONDARY),
            mapping("core:leg-raise", "CORE", MuscleRole.PRIMARY), mapping("core:leg-raise", "QUADS", MuscleRole.SECONDARY),
            mapping("core:squat", "QUADS", MuscleRole.PRIMARY), mapping("core:squat", "GLUTES", MuscleRole.PRIMARY), mapping("core:squat", "HAMSTRINGS", MuscleRole.SECONDARY),
            mapping("core:lunge", "QUADS", MuscleRole.PRIMARY), mapping("core:lunge", "GLUTES", MuscleRole.PRIMARY), mapping("core:lunge", "HAMSTRINGS", MuscleRole.SECONDARY),
            mapping("core:cossack-squat", "ADDUCTORS", MuscleRole.PRIMARY), mapping("core:cossack-squat", "QUADS", MuscleRole.PRIMARY), mapping("core:cossack-squat", "GLUTES", MuscleRole.SECONDARY), mapping("core:cossack-squat", "ABDUCTORS", MuscleRole.SECONDARY),
            mapping("core:jefferson-curl", "BACK_LOWER", MuscleRole.PRIMARY), mapping("core:jefferson-curl", "HAMSTRINGS", MuscleRole.SECONDARY),
            mapping("core:pull-up", "LATS", MuscleRole.PRIMARY), mapping("core:pull-up", "BICEPS", MuscleRole.SECONDARY), mapping("core:pull-up", "FOREARMS", MuscleRole.SECONDARY), mapping("core:pull-up", "RHOMBOIDS", MuscleRole.SECONDARY), mapping("core:pull-up", "SHOULDERS_REAR", MuscleRole.SECONDARY), mapping("core:pull-up", "TRAPEZIUS", MuscleRole.SECONDARY),
            mapping("core:overhead-press", "SHOULDERS_FRONT", MuscleRole.PRIMARY), mapping("core:overhead-press", "SHOULDERS_SIDE", MuscleRole.PRIMARY), mapping("core:overhead-press", "TRICEPS", MuscleRole.SECONDARY), mapping("core:overhead-press", "TRAPEZIUS", MuscleRole.SECONDARY),
            mapping("core:back-extension", "BACK_LOWER", MuscleRole.PRIMARY), mapping("core:back-extension", "GLUTES", MuscleRole.SECONDARY), mapping("core:back-extension", "HAMSTRINGS", MuscleRole.SECONDARY),
            mapping("core:front-split", "HAMSTRINGS", MuscleRole.PRIMARY), mapping("core:front-split", "QUADS", MuscleRole.SECONDARY),
            mapping("core:forward-fold", "HAMSTRINGS", MuscleRole.PRIMARY), mapping("core:forward-fold", "BACK_LOWER", MuscleRole.SECONDARY),
            mapping("core:side-split", "ADDUCTORS", MuscleRole.PRIMARY), mapping("core:side-split", "HAMSTRINGS", MuscleRole.SECONDARY),
            mapping("core:bridge", "SHOULDERS_FRONT", MuscleRole.PRIMARY), mapping("core:bridge", "BACK_LOWER", MuscleRole.PRIMARY), mapping("core:bridge", "QUADS", MuscleRole.SECONDARY), mapping("core:bridge", "GLUTES", MuscleRole.SECONDARY),
        )

        private val SKILL_VARIATIONS = listOf(
            ExerciseVariationEntity(
                "handstand-wall", "core:handstand", "Wall handstand", 10, true,
                TrackingType.SKILL_HOLD.name,
                "weightUnit=;bandResistance=false;timeHeld=true;timeUnderTension=true;reps=false;bodyweight=false;addedWeight=false",
            ),
            ExerciseVariationEntity(
                "handstand-free", "core:handstand", "Freestanding handstand", 20, true,
                TrackingType.SKILL_HOLD.name,
                "weightUnit=;bandResistance=false;timeHeld=false;timeUnderTension=true;reps=false;bodyweight=false;addedWeight=false",
            ),
            ExerciseVariationEntity("planche-tuck", "core:planche", "Tuck planche", 10, true, TrackingType.SKILL_HOLD.name, "weightUnit=;bandResistance=false;timeHeld=true;timeUnderTension=false;reps=false"),
            ExerciseVariationEntity("planche-advanced-tuck", "core:planche", "Advanced tuck planche", 20, true, TrackingType.SKILL_HOLD.name, "weightUnit=;bandResistance=false;timeHeld=true;timeUnderTension=false;reps=false"),
            ExerciseVariationEntity("planche-straddle", "core:planche", "Straddle planche", 30, true, TrackingType.SKILL_HOLD.name, "weightUnit=;bandResistance=false;timeHeld=true;timeUnderTension=false;reps=false"),
            ExerciseVariationEntity("planche-full", "core:planche", "Full planche", 40, true, TrackingType.SKILL_HOLD.name, "weightUnit=;bandResistance=false;timeHeld=true;timeUnderTension=false;reps=false"),
            ExerciseVariationEntity(
                "pull-up-assisted", "core:pull-up", "Band-assisted pull-up", 10, true,
                TrackingType.ASSISTED_REPS.name,
                "weightUnit=;bandResistance=true;timeHeld=false;timeUnderTension=false;reps=true;bodyweight=true;addedWeight=false",
            ),
            ExerciseVariationEntity(
                "pull-up-bodyweight", "core:pull-up", "Bodyweight pull-up", 20, true,
                TrackingType.BODYWEIGHT_REPS.name,
                "weightUnit=;bandResistance=false;timeHeld=false;timeUnderTension=false;reps=true;bodyweight=true;addedWeight=false",
            ),
            ExerciseVariationEntity(
                "pull-up-weighted", "core:pull-up", "Weighted pull-up", 30, true,
                TrackingType.WEIGHT_REPS.name,
                "weightUnit=;bandResistance=false;timeHeld=false;timeUnderTension=false;reps=true;bodyweight=true;addedWeight=true",
            ),
        )

        private val DEMO_PROGRAMMES = listOf(
            ProgrammeEntity("demo-programme-push", "Planche + Push", TrainingMode.STRENGTH.name, true),
            ProgrammeEntity("demo-programme-legs", "Legs + Mobility", TrainingMode.STRENGTH.name, true),
            ProgrammeEntity("demo-programme-pull", "Muscle-up + Pull", TrainingMode.STRENGTH.name, true),
            ProgrammeEntity("demo-programme-stretch", "Stretching", TrainingMode.STRETCHING.name, true),
        )
        private val DEMO_DAYS = listOf(
            ProgrammeDayEntity("demo-day-push", "demo-programme-push", "Planche + Push", 0),
            ProgrammeDayEntity("demo-day-legs", "demo-programme-legs", "Legs + Mobility", 0),
            ProgrammeDayEntity("demo-day-pull", "demo-programme-pull", "Muscle-up + Pull", 0),
            ProgrammeDayEntity("demo-day-front-splits", "demo-programme-stretch", "Stretching", 0),
        )

        private fun programmeExercise(day: String, exercise: String, position: Int, rest: Int = 120) = ProgrammeExerciseEntity(
            id = "$day:$exercise",
            programmeDayId = day,
            exerciseId = exercise,
            position = position,
            targetSets = 3,
            targetRepsMin = null,
            targetRepsMax = null,
            targetHoldSeconds = null,
            restSeconds = rest,
            targetRpe = null,
            notes = "",
            supersetGroup = null,
        )

        private val DEMO_PROGRAMME_EXERCISES = buildList {
            listOf("core:handstand", "core:planche", "core:muscle-up", "core:bench-press", "core:dip", "core:leg-raise").forEachIndexed { i, id -> add(programmeExercise("demo-day-push", id, i)) }
            listOf("core:handstand", "core:squat", "core:lunge", "core:cossack-squat", "core:jefferson-curl").forEachIndexed { i, id -> add(programmeExercise("demo-day-legs", id, i)) }
            listOf("core:handstand", "core:muscle-up", "core:planche", "core:pull-up", "core:overhead-press", "core:back-extension").forEachIndexed { i, id -> add(programmeExercise("demo-day-pull", id, i)) }
            listOf("core:front-split", "core:forward-fold", "core:side-split", "core:bridge").forEachIndexed { i, id ->
                add(programmeExercise("demo-day-front-splits", id, i, 60))
            }
        }

        private val DEMO_EXTRA_TRACKERS = listOf(
            TrackerEntity("demo-mood", "Mood", true, colourArgb = 0xFF42A5F5L, position = 3, iconName = "mood"),
            TrackerEntity("demo-journal", "Journal", true, colourArgb = 0xFFFFB74DL, position = 4, iconName = "journal"),
            TrackerEntity("demo-reading", "Reading", true, colourArgb = 0xFF5C6BC0L, position = 5, iconName = "reading"),
        )
        private val DEMO_EXTRA_TRACKER_FIELDS = listOf(
            TrackerFieldEntity(
                "demo-mood-feeling",
                "demo-mood",
                "Feeling",
                "CHOICE",
                null,
                null,
                null,
                0,
                choiceOptions = MOOD_CHOICES.joinToString("\n"),
                choiceLightThrough = 2,
                choiceDarkFrom = 6,
                choiceOptionsJson = encodeHabitChoices(
                    legacyHabitChoices("demo-mood-feeling", MOOD_CHOICES.joinToString("\n"), 2, 6),
                ),
            ),
            TrackerFieldEntity("demo-journal-entry", "demo-journal", "Entry", "TEXT", null, null, null, 0),
            TrackerFieldEntity("demo-reading-completed", "demo-reading", "Read today", "BOOLEAN", null, null, null, 0),
        )
        private val DEMO_TRACKERS = listOf(
            TrackerEntity("demo-piano", "Piano", true, colourArgb = 0xFF7E57C2L, position = 0, iconName = "piano"),
            TrackerEntity("demo-meditation", "Meditation", true, colourArgb = 0xFF26A69AL, position = 1, iconName = "meditation"),
            TrackerEntity(
                "demo-protein",
                "Protein",
                true,
                colourArgb = 0xFFEF5350L,
                position = 2,
                heatmapLightBelow = 140.0,
                heatmapMediumBelow = 160.0,
                iconName = "protein",
            ),
        ) + DEMO_EXTRA_TRACKERS
        private val DEMO_TRACKER_FIELDS = listOf(
            TrackerFieldEntity("demo-piano-minutes", "demo-piano", "Duration", "NUMBER", "min", null, null, 0),
            TrackerFieldEntity("demo-meditation-minutes", "demo-meditation", "Duration", "NUMBER", "min", "AT_LEAST", 10.0, 0),
            TrackerFieldEntity("demo-protein-grams", "demo-protein", "Protein", "NUMBER", "g", "AT_LEAST", 120.0, 0),
        ) + DEMO_EXTRA_TRACKER_FIELDS
    }
}

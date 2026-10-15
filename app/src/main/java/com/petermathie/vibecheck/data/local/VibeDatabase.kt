package com.petermathie.vibecheck.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SeedMetadataEntity::class,
        MuscleEntity::class,
        ExerciseEntity::class,
        ExerciseAliasEntity::class,
        ExerciseMuscleEntity::class,
        ExerciseVariationEntity::class,
        ExerciseReferenceVideoEntity::class,
        BandEntity::class,
        ProgrammeEntity::class,
        ProgrammeDayEntity::class,
        ProgrammeExerciseEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutMuscleEntity::class,
        WorkoutSetEntity::class,
        WorkoutSetBandEntity::class,
        WorkoutEntryDraftEntity::class,
        TrackerEntity::class,
        TrackerFieldEntity::class,
        TrackerDailyValueEntity::class,
        TrackerDayOutcomeEntity::class,
        BodyMeasurementEntity::class,
    ],
    version = 16,
    exportSchema = true,
)
abstract class VibeDatabase : RoomDatabase() {
    abstract fun editorDao(): EditorDao
    abstract fun catalogueDao(): CatalogueDao
    abstract fun programmeDao(): ProgrammeDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun trackerDao(): TrackerDao
    abstract fun metadataDao(): MetadataDao
}

package com.petermathie.vibecheck.di

import android.content.Context
import androidx.room.Room
import com.petermathie.vibecheck.data.local.CatalogueDao
import com.petermathie.vibecheck.data.local.ProgrammeDao
import com.petermathie.vibecheck.data.local.TrackerDao
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.local.WorkoutDao
import com.petermathie.vibecheck.data.local.MIGRATION_1_2
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): VibeDatabase =
        Room.databaseBuilder(context, VibeDatabase::class.java, "vibe-check.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
            .build()

    @Provides fun catalogueDao(db: VibeDatabase): CatalogueDao = db.catalogueDao()
    @Provides fun programmeDao(db: VibeDatabase): ProgrammeDao = db.programmeDao()
    @Provides fun workoutDao(db: VibeDatabase): WorkoutDao = db.workoutDao()
    @Provides fun trackerDao(db: VibeDatabase): TrackerDao = db.trackerDao()
}

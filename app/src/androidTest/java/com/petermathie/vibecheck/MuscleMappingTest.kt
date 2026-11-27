package com.petermathie.vibecheck

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.local.SeedMetadataEntity
import com.petermathie.vibecheck.data.local.ExerciseOverrideEntity
import com.petermathie.vibecheck.data.local.ExerciseMuscleOverrideEntity
import com.petermathie.vibecheck.data.local.VariationOverrideEntity
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MuscleMappingTest {
    private lateinit var database: VibeDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking { DatabaseSeeder(context, database).seedIfNeeded() }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun freshSeededVariantsOwnInitialMusclesAndMissingMetadataInitializesWithoutRefillingEmptyMappings() = runBlocking {
        val dao = database.editorDao()
        val original = dao.variationMuscleMappings("handstand-wall")
        val parent = dao.muscleMappings("core:handstand").associate { it.muscleId to it.role }
        assertTrue(original.isNotEmpty())
        assertEquals(parent, original.associate { it.muscleId to it.role })
        assertTrue(dao.variationOverrideById("handstand-wall")!!.musclesPresent)
        val history = dao.sets().first()
        database.withTransaction {
            dao.exerciseOverride(ExerciseOverrideEntity("core:handstand", musclesPresent = true))
            dao.muscleOverrides(listOf(ExerciseMuscleOverrideEntity("core:handstand", "LATS", "PRIMARY")))
        }
        assertEquals(original, dao.variationMuscleMappings("handstand-wall"))
        database.withTransaction {
            dao.variationOverride(VariationOverrideEntity("handstand-wall", musclesPresent = true))
            dao.clearVariationMuscles("handstand-wall")
            database.openHelper.writableDatabase.execSQL("DELETE FROM variation_overrides WHERE variationId='handstand-free'")
        }
        assertEquals(listOf("LATS"), dao.variationMuscleMappings("handstand-free").map { it.muscleId })
        DatabaseSeeder(ApplicationProvider.getApplicationContext(), database).seedIfNeeded()
        assertTrue(dao.variationMuscleMappings("handstand-wall").isEmpty())
        assertTrue(dao.variationOverrideById("handstand-free")!!.musclesPresent)
        assertEquals(listOf("LATS"), dao.variationMuscleMappings("handstand-free").map { it.muscleId })
        database.withTransaction {
            dao.clearMuscleOverrides("core:handstand")
            dao.muscleOverrides(listOf(ExerciseMuscleOverrideEntity("core:handstand", "HAMSTRINGS", "SECONDARY")))
        }
        DatabaseSeeder(ApplicationProvider.getApplicationContext(), database).seedIfNeeded()
        assertTrue(dao.variationMuscleMappings("handstand-wall").isEmpty())
        assertEquals(listOf("LATS"), dao.variationMuscleMappings("handstand-free").map { it.muscleId })
        assertEquals(history, dao.sets().first())
    }

    @Test
    fun pullingAndOverheadExercisesCoverUpperBackAndTraps() {
        assertMappings(
            "core:pull-up",
            "LATS:PRIMARY",
            "BICEPS:SECONDARY",
            "FOREARMS:SECONDARY",
            "RHOMBOIDS:SECONDARY",
            "SHOULDERS_REAR:SECONDARY",
            "TRAPEZIUS:SECONDARY",
        )
        assertMappings(
            "core:muscle-up",
            "LATS:PRIMARY",
            "BICEPS:SECONDARY",
            "TRICEPS:SECONDARY",
            "CHEST:SECONDARY",
            "FOREARMS:SECONDARY",
            "RHOMBOIDS:SECONDARY",
            "SHOULDERS_REAR:SECONDARY",
            "TRAPEZIUS:SECONDARY",
        )
        assertMapping("core:handstand", "TRAPEZIUS", "SECONDARY")
        assertMapping("core:overhead-press", "TRAPEZIUS", "SECONDARY")
    }

    @Test
    fun lowerBodyAdditionsRemainConservative() {
        assertMapping("core:lunge", "HAMSTRINGS", "SECONDARY")
        assertMapping("core:cossack-squat", "ABDUCTORS", "SECONDARY")
        assertMapping("core:bridge", "GLUTES", "SECONDARY")

        val calfMappings = database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM exercise_muscles WHERE muscleId='CALVES'",
        ).use {
            it.moveToFirst()
            it.getInt(0)
        }
        assertEquals(0, calfMappings)
    }

    @Test
    fun versionOneCatalogueUpgradesMappingsWithoutReplacingReferencedParents() {
        val sql = database.openHelper.writableDatabase
        sql.execSQL(
            """
            DELETE FROM exercise_muscles
            WHERE muscleId IN ('TRAPEZIUS','RHOMBOIDS','SHOULDERS_REAR','ABDUCTORS')
               OR (exerciseId='core:lunge' AND muscleId='HAMSTRINGS')
               OR (exerciseId='core:bridge' AND muscleId='GLUTES')
            """.trimIndent(),
        )
        runBlocking {
            database.metadataDao().put(SeedMetadataEntity("exercise-catalogue", 1))
            DatabaseSeeder(ApplicationProvider.getApplicationContext(), database).seedIfNeeded()
        }

        assertMapping("core:pull-up", "RHOMBOIDS", "SECONDARY")
        assertMapping("core:handstand", "TRAPEZIUS", "SECONDARY")
        assertMapping("core:cossack-squat", "ABDUCTORS", "SECONDARY")
        assertMapping("core:lunge", "HAMSTRINGS", "SECONDARY")
        assertMapping("core:bridge", "GLUTES", "SECONDARY")
    }

    private fun assertMappings(exerciseId: String, vararg expected: String) {
        val actual = database.openHelper.readableDatabase.query(
            "SELECT muscleId,role FROM exercise_muscles WHERE exerciseId=?",
            arrayOf(exerciseId),
        ).use { cursor ->
            buildSet {
                while (cursor.moveToNext()) add("${cursor.getString(0)}:${cursor.getString(1)}")
            }
        }
        assertEquals(expected.toSet(), actual)
    }

    private fun assertMapping(exerciseId: String, muscleId: String, role: String) {
        val exists = database.openHelper.readableDatabase.query(
            "SELECT 1 FROM exercise_muscles WHERE exerciseId=? AND muscleId=? AND role=?",
            arrayOf(exerciseId, muscleId, role),
        ).use { it.moveToFirst() }
        assertTrue("$exerciseId should map to $muscleId as $role", exists)
    }
}

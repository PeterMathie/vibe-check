package com.petermathie.vibecheck

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.petermathie.vibecheck.data.local.ExerciseEntity
import com.petermathie.vibecheck.data.local.BandEntity
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.ExerciseMeasurement
import com.petermathie.vibecheck.domain.programme.MeasurementMetric
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import com.petermathie.vibecheck.ui.BandSettingsDialog
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.ExerciseEditor
import com.petermathie.vibecheck.ui.ExerciseSettingsDialog
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

@RunWith(AndroidJUnit4::class)
@android.annotation.SuppressLint("ViewModelConstructorInComposable")
class ExerciseEditorUiTest {
    private lateinit var database: VibeDatabase

    @get:Rule
    val lifecycle = ComposeRoomLifecycleRule {
        if (::database.isInitialized) database else null
    }
    private val compose get() = lifecycle.compose

    @Test
    fun canonicalSettingsCanBeEditedForSeededExercise() {
        val preferences = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.content.Context>()
            .getSharedPreferences("settings", 0)
        preferences.edit().putBoolean("lb", true).commit()
        val exercise = ExerciseEntity(
            "handstand", "Handstand", "STRENGTH", "SKILL_HOLD", null, null, "core", false,
        )
        var saved: ExerciseEntity? = null
        compose.setContent {
            VibeCheckTheme {
                ExerciseSettingsDialog(
                    exercise = exercise,
                    onDismiss = {},
                    onSave = { saved = it },
                )
            }
        }

        compose.onNodeWithText("Exercise measurements").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kilograms").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Hold duration").assertIsSelected()
        compose.onNodeWithContentDescription("Skill hold").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Sets").assertDoesNotExist()
        compose.onNodeWithContentDescription("Target RPE").assertDoesNotExist()
        compose.onNodeWithContentDescription("Rest seconds").assertDoesNotExist()
        compose.onNodeWithText("Save").performClick()

        val result = requireNotNull(saved)
        val config = ExerciseInputConfig.decode(result.inputConfig, result.trackingType)
        assertTrue(config.timeHeld)
        assertTrue(config.timeUnderTension)
        assertEquals("lb", config.weightUnit)
        assertEquals(null, result.targetSets)
        assertEquals(120, result.restSeconds)
        preferences.edit().putBoolean("lb", false).commit()
    }

    @Test
    fun catalogueGroupsMusclesAndExposesVariationSettingsAndVideos() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking { DatabaseSeeder(context, database).seedIfNeeded() }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { ExerciseEditor(viewModel) } }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Handstand"))
        assertTrue(compose.onAllNodesWithText("Primary", substring = true).fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("Secondary", substring = true).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithContentDescription("Edit Handstand").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings for Handstand").assertIsDisplayed()
        compose.onNodeWithContentDescription("Duplicate Handstand").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Attach reference video").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithContentDescription("Settings for Wall handstand").performClick()
        compose.onNodeWithContentDescription("Bodyweight").assertIsSelected()
        compose.onNodeWithContentDescription("Skill hold").assertIsSelected()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Settings for Freestanding handstand").performClick()
        compose.onNodeWithContentDescription("Skill hold").assertIsSelected()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Stretch"))
        compose.onNodeWithText("Stretch").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Front split"))
        compose.onNodeWithContentDescription("Edit Front split").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings for Front split").assertIsDisplayed()
        compose.onNodeWithContentDescription("Duplicate Front split").assertIsDisplayed()
    }

    @Test
    fun exclusiveMetricSelectionReopensWithoutRewritingLegacyOrder() {
        val exercise = mutableStateOf(ExerciseEntity(
            "compact-test", "Compact exercise", "STRENGTH", "WEIGHT_REPS", null, null, "core", true,
            inputConfig = ExerciseInputConfig(
                weightUnit = "kg", reps = true, measurementOrder = listOf(ExerciseMeasurement.RPE),
            ).encode(),
        ))
        val open = mutableStateOf(true)
        compose.setContent {
            VibeCheckTheme {
                if (open.value) ExerciseSettingsDialog(exercise.value, { open.value = false }) {
                    exercise.value = it
                    open.value = false
                } else androidx.compose.material3.TextButton(onClick = { open.value = true }) {
                    androidx.compose.material3.Text("Reopen")
                }
            }
        }
        compose.onNodeWithContentDescription("Bodyweight").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Range of motion").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(
            listOf(ExerciseMeasurement.RPE),
            ExerciseInputConfig.decode(exercise.value.inputConfig, "WEIGHT_REPS").measurementOrder,
        )
        compose.onNodeWithText("Reopen").performClick()
        compose.onNodeWithContentDescription("Bodyweight").assertIsSelected()
        compose.onNodeWithContentDescription("Range of motion").performScrollTo().assertIsSelected()
    }

    @Test
    fun variationMetricSelectionSavesWithoutChangingParentSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking { DatabaseSeeder(context, database).seedIfNeeded() }
        val parentBefore = runBlocking { database.editorDao().exerciseById("core:handstand")!!.inputConfig }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent { VibeCheckTheme { ExerciseEditor(viewModel) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Handstand"))
        compose.onNodeWithContentDescription("Settings for Wall handstand").performClick()
        compose.onNodeWithContentDescription("Range of motion").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(15_000) {
            runBlocking { ExerciseInputConfig.decode(database.editorDao().variationById("handstand-wall")!!.inputConfig, "SKILL_HOLD").loggingMeasurement() == MeasurementMetric.RANGE_OF_MOTION }
        }
        assertEquals(parentBefore, runBlocking { database.editorDao().exerciseById("core:handstand")!!.inputConfig })
        compose.onNodeWithContentDescription("Settings for Wall handstand").performClick()
        compose.onNodeWithContentDescription("Range of motion").performScrollTo().assertIsSelected()
    }

    @Test
    fun customBandCanBeAddedAndEditedWithoutChangingItsIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking { DatabaseSeeder(context, database).seedIfNeeded() }
        val vm = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                BandSettingsDialog(vm.bands.collectAsState().value, {},
                    onSave = { band, saved, failed -> vm.saveBand(band, saved, failed) })
            }
        }
        compose.onNodeWithText("Add band").performClick()
        compose.onNodeWithText("Save band").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Band name").performTextInput("Custom blue")
        compose.onNodeWithContentDescription("Band width in centimetres").performTextInput("4")
        compose.onNodeWithContentDescription("Band colour").performTextReplacement("5085D6")
        compose.onNodeWithText("Save band").performClick()
        compose.waitUntil(15_000) {
            runBlocking { database.editorDao().bands().first().any { it.name == "Custom blue" } }
        }
        val band = runBlocking { database.editorDao().bands().first().single { it.name == "Custom blue" } }
        compose.onNodeWithText("Edit").performClick()
        compose.onNodeWithContentDescription("Band name").performTextReplacement("Renamed blue")
        compose.onNodeWithText("Save band").performClick()
        compose.waitUntil(15_000) {
            runBlocking { database.editorDao().bandById(band.id)?.name == "Renamed blue" }
        }
        assertEquals(band.copy(name = "Renamed blue"), runBlocking { database.editorDao().bandById(band.id) })
        runBlocking {
            repeat(3) { database.editorDao().band(BandEntity("limit-$it", "Band $it", it + 5.0, 0xFF5085D6L)) }
        }
        compose.waitUntil(15_000) {
            runBlocking { database.editorDao().bandCount() == 8 } &&
                compose.onAllNodesWithText("8/8 bands configured").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add band").assertIsNotEnabled()
        var rejected = false
        runBlocking {
            vm.saveBand(BandEntity("ninth", "Ninth", 10.0, 0xFF5085D6L), {}, { rejected = true }).join()
        }
        assertTrue(rejected)
        assertEquals(8, runBlocking { database.editorDao().bandCount() })
    }
}

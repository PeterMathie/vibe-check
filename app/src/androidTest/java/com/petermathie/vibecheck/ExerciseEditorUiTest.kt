package com.petermathie.vibecheck

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.petermathie.vibecheck.data.local.ExerciseEntity
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
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
        compose.onNodeWithContentDescription("Weight (lb)").assertExists()
        compose.onNodeWithContentDescription("Weight (kg)").assertDoesNotExist()
        compose.onNodeWithContentDescription("Time Under Tension (seconds)").assertExists()
        compose.onNodeWithContentDescription("Total Time (seconds)").performClick()
        compose.onNodeWithContentDescription("Sets").assertDoesNotExist()
        compose.onNodeWithContentDescription("Target RPE").assertDoesNotExist()
        compose.onNodeWithContentDescription("Rest seconds").assertDoesNotExist()
        compose.onNodeWithText("Save").performClick()

        val result = requireNotNull(saved)
        val config = ExerciseInputConfig.decode(result.inputConfig, result.trackingType)
        assertTrue(config.timeHeld)
        assertTrue(config.timeUnderTension)
        assertEquals(null, result.targetSets)
        assertEquals(120, result.restSeconds)
        preferences.edit().putBoolean("lb", false).commit()
    }

    @Test
    fun plancheSettingsUseTotalTimeTerminologyOnly() {
        val planche = ExerciseEntity(
            "core:planche", "Planche", "STRENGTH", "SKILL_HOLD", null, null, "vibe-trainer", false,
        )
        compose.setContent {
            VibeCheckTheme {
                ExerciseSettingsDialog(planche, onDismiss = {}, onSave = {})
            }
        }

        compose.onNodeWithContentDescription("Total Time (seconds)").assertExists()
        compose.onNodeWithContentDescription("Elapsed Time (seconds)").assertExists()
        compose.onNodeWithContentDescription("Time Under Tension (seconds)").assertDoesNotExist()
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
        compose.onNodeWithContentDescription("Time Under Tension (seconds)").assertIsOn()
        compose.onNodeWithContentDescription("Total Time (seconds)").assertIsOn()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Settings for Freestanding handstand").performClick()
        compose.onNodeWithContentDescription("Time Under Tension (seconds)").assertIsOff()
        compose.onNodeWithContentDescription("Total Time (seconds)").assertIsOn()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Stretch"))
        compose.onNodeWithText("Stretch").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Front split"))
        compose.onNodeWithContentDescription("Edit Front split").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings for Front split").assertIsDisplayed()
        compose.onNodeWithContentDescription("Duplicate Front split").assertIsDisplayed()
    }
}

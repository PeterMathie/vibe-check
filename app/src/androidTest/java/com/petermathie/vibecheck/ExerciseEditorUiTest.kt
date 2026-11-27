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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.ui.AppFabHostState
import com.petermathie.vibecheck.ui.LocalAppFabHost
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
        compose.onNodeWithText("Handstand").assertHasClickAction()
        compose.onNodeWithContentDescription("Settings for Handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("Duplicate Handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("Next variant for Handstand").performClick()
        compose.onNodeWithText("Wall handstand").performTouchInput { doubleClick() }
        compose.onNodeWithText("New variant").assertIsDisplayed()
        compose.onNodeWithText("Modify measurements").performClick()
        compose.onNodeWithContentDescription("Skill hold").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Next variant for Handstand").performClick()
        compose.onNodeWithText("Freestanding handstand").performTouchInput { doubleClick() }
        compose.onNodeWithText("Reference video").performClick()
        compose.onNodeWithText("Attach reference video").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close", substring = true).performClick()

        compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("Stretch"))
        compose.onNodeWithContentDescription("Stretch").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Front split"))
        compose.onNodeWithText("Front split").assertHasClickAction()
        compose.onNodeWithContentDescription("Duplicate Front split").assertDoesNotExist()
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
        compose.onNodeWithContentDescription("ROM (degrees)").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()
        assertEquals(
            listOf(ExerciseMeasurement.RPE),
            ExerciseInputConfig.decode(exercise.value.inputConfig, "WEIGHT_REPS").measurementOrder,
        )
        compose.onNodeWithText("Reopen").performClick()
        compose.onNodeWithContentDescription("Bodyweight").assertIsSelected()
        compose.onNodeWithContentDescription("ROM (degrees)").performScrollTo().assertIsSelected()
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
        compose.onNodeWithContentDescription("Next variant for Handstand").performClick()
        compose.onNodeWithText("Wall handstand").performTouchInput { doubleClick() }
        compose.onNodeWithText("Modify measurements").performClick()
        compose.onNodeWithContentDescription("ROM (degrees)").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(15_000) {
            runBlocking { ExerciseInputConfig.decode(database.editorDao().variationById("handstand-wall")!!.inputConfig, "SKILL_HOLD").loggingMeasurement() == MeasurementMetric.RANGE_OF_MOTION }
        }
        assertEquals(parentBefore, runBlocking { database.editorDao().exerciseById("core:handstand")!!.inputConfig })
        compose.onNodeWithText("Wall handstand").performTouchInput { doubleClick() }
        compose.onNodeWithText("Modify measurements").performClick()
        compose.onNodeWithContentDescription("ROM (degrees)").performScrollTo().assertIsSelected()
    }

    @Test
    fun tileSurfaceDoubleTapCreatesIndependentDistanceSiblingWithoutCopyingExercise() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking { DatabaseSeeder(context, database).seedIfNeeded() }
        val vm = lifecycle.own(EditorViewModel(database))
        val exerciseCount = runBlocking { database.editorDao().exercises().first().size }
        compose.setContent { VibeCheckTheme { ExerciseEditor(vm) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Handstand"))
        compose.onNodeWithContentDescription("Next variant for Handstand").performClick()
        compose.onNodeWithTag("Exercise tile core:handstand").performScrollTo()
            .performTouchInput { doubleClick(Offset(center.x, height.toFloat() - 16f)) }
        compose.onNodeWithText("New variant").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Variant name").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Variant name").performTextInput("Independent reach")
        compose.onNodeWithText("Tap muscle to cycle: none → primary → secondary").assertExists()
        compose.onNodeWithContentDescription("Distance (cm)").performScrollTo().performClick()
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(15_000) { vm.variations.value.any { it.name == "Independent reach" } }
        val created = vm.variations.value.single { it.name == "Independent reach" }
        assertEquals("core:handstand", created.exerciseId)
        assertEquals(MeasurementMetric.DISTANCE, ExerciseInputConfig.decode(created.inputConfig, created.trackingType).loggingMeasurement())
        assertEquals(exerciseCount, runBlocking { database.editorDao().exercises().first().size })
        assertTrue(vm.referenceVideos.value.none { it.variationId == created.id })
        compose.onNodeWithText("Independent reach").assertHasClickAction()
        compose.onNodeWithContentDescription("Duplicate Independent reach").assertDoesNotExist()
    }

    @Test
    fun baseCreationSharesMetricEditorAndModalClearsAppTabs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking { DatabaseSeeder(context, database).seedIfNeeded() }
        val vm = lifecycle.own(EditorViewModel(database))
        val fab = AppFabHostState()
        compose.setContent {
            VibeCheckTheme {
                CompositionLocalProvider(LocalAppFabHost provides fab) {
                    Scaffold(bottomBar = {
                        Box(Modifier.fillMaxWidth().height(72.dp).semantics { contentDescription = "Test app tabs" }) {
                            Text("Tabs")
                        }
                    }) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) { ExerciseEditor(vm) }
                    }
                }
            }
        }
        compose.waitUntil(15_000) { vm.exercises.value.isNotEmpty() && fab.action != null }
        compose.onNodeWithContentDescription("Back to More").assertIsDisplayed()
        val title = compose.onNodeWithText("Exercises").fetchSemanticsNode().boundsInRoot
        val mode = compose.onNodeWithContentDescription("Strength").fetchSemanticsNode().boundsInRoot
        assertEquals(title.center.y, mode.center.y, 1f)
        val before = vm.exercises.value.size
        compose.runOnIdle { fab.invoke(requireNotNull(fab.action)) }
        val pane = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "New exercise"),
            useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates.boundsInWindow()
        val tabs = compose.onNodeWithContentDescription("Test app tabs")
            .fetchSemanticsNode().layoutInfo.coordinates.boundsInWindow()
        assertTrue("Exercise modal leaves at least24dp above app tabs",
            pane.bottom <= tabs.top - with(compose.density) { 24.dp.toPx() } + 1f)
        compose.onNodeWithText("Name").performTextInput("New base")
        val top = { label: String ->
            compose.onNodeWithText(label).fetchSemanticsNode().layoutInfo.coordinates
                .localToWindow(Offset.Zero).y
        }
        val musclesLabel = "Tap muscle to cycle: none → primary → secondary"
        assertTrue(top("Name") < top("Resistance"))
        assertTrue(top("Resistance") < top("Measurement"))
        assertTrue(top("Measurement") < top(musclesLabel))
        assertTrue(top(musclesLabel) < top("Aliases separated by commas"))
        compose.onNodeWithText("Aliases separated by commas").performScrollTo().performTextInput("Base alias")
        compose.onNodeWithText("Resistance").assertExists()
        compose.onNodeWithContentDescription("Distance (cm)").performScrollTo().performClick()
        val muscle = vm.muscles.value.first()
        compose.onNodeWithText("${muscle.displayName}: none").performScrollTo().performClick()
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(15_000) {
            vm.exercises.value.any { it.canonicalName == "New base" } &&
                vm.aliases.value.any { it.alias == "Base alias" } &&
                vm.mappings.value.any { it.muscleId == muscle.id && it.role == "PRIMARY" &&
                    vm.exercises.value.any { exercise -> exercise.id == it.exerciseId && exercise.canonicalName == "New base" } }
        }
        val created = vm.exercises.value.single { it.canonicalName == "New base" }
        assertEquals(before + 1, vm.exercises.value.size)
        assertEquals(MeasurementMetric.DISTANCE,
            ExerciseInputConfig.decode(created.inputConfig, created.trackingType).loggingMeasurement())
        assertTrue(vm.aliases.value.any { it.exerciseId == created.id && it.alias == "Base alias" })
        assertTrue(vm.variations.value.none { it.name == "New base" })
        assertTrue(vm.mappings.value.any { it.exerciseId == created.id && it.muscleId == muscle.id && it.role == "PRIMARY" })
    }

    @Test
    fun customBandCanBeAddedAndEditedWithoutChangingItsIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        runBlocking { DatabaseSeeder(context, database).seedIfNeeded() }
        val vm = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                ExerciseEditor(vm)
            }
        }
        compose.onNodeWithText("Configure bands").performScrollTo().performClick()
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

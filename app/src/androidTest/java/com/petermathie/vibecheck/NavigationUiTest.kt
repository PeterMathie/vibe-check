package com.petermathie.vibecheck

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.local.ExerciseEntity
import com.petermathie.vibecheck.data.local.ProgrammeEntity
import com.petermathie.vibecheck.data.local.TrackerEntity
import com.petermathie.vibecheck.ui.ArchiveScreen
import com.petermathie.vibecheck.ui.AppFabHostState
import com.petermathie.vibecheck.ui.AppFloatingAction
import com.petermathie.vibecheck.ui.Destination
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.ExerciseEditor
import com.petermathie.vibecheck.ui.MeasurementsScreen
import com.petermathie.vibecheck.ui.MoreScreen
import com.petermathie.vibecheck.ui.MoreNavigationBackHandler
import com.petermathie.vibecheck.ui.LocalAppFabClearance
import com.petermathie.vibecheck.ui.LocalAppFabHost
import com.petermathie.vibecheck.ui.PrimaryNavigationBar
import com.petermathie.vibecheck.ui.ProgrammeEditor
import com.petermathie.vibecheck.ui.TrackerScreen
import com.petermathie.vibecheck.ui.SettingsScreen
import com.petermathie.vibecheck.ui.StyleScreen
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import com.petermathie.vibecheck.ui.theme.VibePalettes
import com.petermathie.vibecheck.ui.theme.VibeThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class NavigationUiTest {
    private lateinit var database: VibeDatabase

    @get:Rule
    val lifecycle = ComposeRoomLifecycleRule {
        if (::database.isInitialized) database else null
    }
    private val compose get() = lifecycle.compose

    @Test
    fun fixedNavigationDirectlyExposesHabitsAndBody() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            VibeDatabase::class.java,
        ).build()
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                var destination by remember { mutableStateOf(Destination.HOME) }
                Column(Modifier.width(320.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val fabHost = remember(destination) { AppFabHostState(destination) }
                        CompositionLocalProvider(
                            LocalAppFabHost provides fabHost,
                            LocalAppFabClearance provides 88.dp,
                        ) {
                            when (destination) {
                                Destination.MORE -> MoreScreen { destination = it }
                                Destination.PROGRAMMES -> ProgrammeEditor(
                                    viewModel,
                                    com.petermathie.vibecheck.domain.model.TrainingMode.STRENGTH,
                                    onResume = {},
                                    onStart = { _, _, _ -> },
                                )
                                Destination.HABITS -> TrackerScreen(viewModel)
                                Destination.MEASUREMENTS -> MeasurementsScreen(viewModel)
                                Destination.EXERCISES -> ExerciseEditor(
                                    vm = viewModel,
                                    onBack = { destination = Destination.MORE },
                                )
                                else -> Unit
                            }
                            fabHost.action?.takeIf { it.visible }?.let {
                                AppFloatingAction(
                                    it,
                                    fabHost,
                                    Modifier.align(Alignment.BottomStart).padding(16.dp),
                                )
                            }
                        }
                    }
                    PrimaryNavigationBar(destination) { destination = it }
                }
            }
        }

        listOf("Home", "Plans", "Progress", "Habits", "Body", "More").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
        compose.onNodeWithText("Workout").assertDoesNotExist()
        compose.onNodeWithContentDescription("Create new programme").assertDoesNotExist()
        compose.onNodeWithText("Plans").performClick()
        compose.onNodeWithText("New programme").assertIsDisplayed()
        compose.onNodeWithContentDescription("Create new programme").assertDoesNotExist()
        compose.onNodeWithText("Habits").performClick()
        compose.onNodeWithContentDescription("Create new habit").assertIsDisplayed()
        compose.onNodeWithContentDescription("Create new programme").assertDoesNotExist()
        compose.onNodeWithText("Body").assertIsDisplayed()
        compose.onNodeWithText("Body").performClick()
        compose.onNodeWithContentDescription("Add photo").assertIsDisplayed()
        compose.onNodeWithText("More").assertIsDisplayed()
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Exercises").performClick()
        compose.onNodeWithContentDescription("Add custom exercise").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add photo").assertDoesNotExist()
    }

    @Test
    fun moreArchiveRestoresArchivedItems() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            VibeDatabase::class.java,
        ).build()
        runBlocking {
            database.editorDao().programme(ProgrammeEntity("archived-plan", "Old plan", "STRENGTH", false, true))
            database.editorDao().exercise(
                ExerciseEntity("archived-exercise", "Old exercise", "STRENGTH", "", null, null, "custom", true, true),
            )
            database.editorDao().tracker(TrackerEntity("archived-habit", "Old habit", false, true))
        }
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                var destination by remember { mutableStateOf(Destination.MORE) }
                when (destination) {
                    Destination.MORE -> MoreScreen { destination = it }
                    Destination.ARCHIVE -> ArchiveScreen(
                        viewModel,
                        onBack = { destination = Destination.MORE },
                    )
                    else -> Unit
                }
            }
        }

        compose.onNodeWithText("Archive").performClick()
        listOf("Old plan", "Old exercise", "Old habit").forEach {
            compose.waitUntil(5_000) { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.onAllNodesWithText("Restore")[0].performClick()
        compose.waitUntil(5_000) {
            runBlocking { database.editorDao().archivedProgrammes().first().none { it.id == "archived-plan" } }
        }
        compose.onNodeWithContentDescription("Back to More").performClick()
        compose.onNodeWithText("Exercises").assertIsDisplayed()
    }

    @Test
    fun moreHidesStyleAndPaletteBackReturnsThroughSettings() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            VibeDatabase::class.java,
        ).build()
        val viewModel = lifecycle.own(EditorViewModel(database))
        var dispatcher: OnBackPressedDispatcher? = null
        var originalActionTextSize = 0f
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            VibeCheckTheme {
                originalActionTextSize = androidx.compose.material3.MaterialTheme.typography.labelLarge.fontSize.value
                var destination by remember { mutableStateOf(Destination.MORE) }
                MoreNavigationBackHandler(destination, imeVisible = false) { destination = it }
                Column(Modifier.width(360.dp)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (destination) {
                            Destination.MORE -> MoreScreen { destination = it }
                            Destination.SETTINGS -> SettingsScreen(
                                viewModel,
                                onStyle = { destination = Destination.STYLE },
                                onRemoveDemo = {},
                                onBack = { destination = Destination.MORE },
                            )
                            Destination.STYLE -> StyleScreen(
                                selectedId = VibePalettes.Ocean.id,
                                onSelect = {},
                                themeMode = VibeThemeMode.SYSTEM,
                                onThemeModeChange = {},
                                onBack = { destination = Destination.SETTINGS },
                            )
                            Destination.ARCHIVE -> ArchiveScreen(
                                viewModel,
                                onBack = { destination = Destination.MORE },
                            )
                            else -> Unit
                        }
                    }
                    PrimaryNavigationBar(destination) { destination = it }
                }
            }
        }

        listOf("Exercises", "History", "Settings", "Archive").forEach {
            compose.onNodeWithText(it).assertIsDisplayed().assertHasClickAction()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(it, useUnmergedTree = true)
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
                    action(layouts)
                }
            assertTrue(layouts.single().layoutInput.style.fontSize.value > originalActionTextSize)
        }
        val options = listOf("Exercises", "History", "Settings", "Archive").map {
            compose.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        }
        val dividers = compose.onAllNodesWithTag("More option divider").fetchSemanticsNodes()
        assertEquals(3, dividers.size)
        dividers.forEachIndexed { index, divider ->
            assertTrue(divider.boundsInRoot.top > options[index].bottom)
            assertTrue(divider.boundsInRoot.bottom < options[index + 1].top)
        }
        compose.onNodeWithText("Style").assertDoesNotExist()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("More").assertIsSelected()
        compose.onNodeWithContentDescription("Back to More").performClick()
        compose.onNodeWithText("Exercises").assertIsDisplayed()

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Colour palette").performClick()
        compose.onNodeWithText("More").assertIsSelected()
        compose.onNodeWithContentDescription("Back to Settings").performClick()
        compose.onNodeWithText("Display & units").assertIsDisplayed()
        compose.onNodeWithText("Colour palette").performClick()
        compose.runOnIdle { dispatcher!!.onBackPressed() }
        compose.onNodeWithText("Display & units").assertIsDisplayed()
        compose.onNodeWithText("More").assertIsSelected()
        compose.runOnIdle { dispatcher!!.onBackPressed() }
        compose.onNodeWithText("Exercises").assertIsDisplayed()

        compose.onNodeWithText("Archive").performClick()
        compose.onNodeWithText("More").assertIsSelected()
        compose.onNodeWithText("Archived plans, exercises, and habits will appear here.").assertIsDisplayed()
        compose.runOnIdle { dispatcher!!.onBackPressed() }
        compose.onNodeWithText("Exercises").assertIsDisplayed()
    }

    @Test
    fun moreBackDefersToLocalStateAndImeBeforeLeavingDirectChildren() {
        var dispatcher: OnBackPressedDispatcher? = null
        var destination by mutableStateOf(Destination.HISTORY)
        var nested by mutableStateOf(true)
        var imeVisible by mutableStateOf(false)
        var platformBacks = 0
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            BackHandler { platformBacks++ }
            MoreNavigationBackHandler(destination, imeVisible) { destination = it }
            BackHandler(enabled = nested) { nested = false }
            VibeCheckTheme {
                PrimaryNavigationBar(destination) { destination = it }
            }
        }

        compose.onNodeWithText("More").assertIsSelected()
        compose.runOnIdle { dispatcher!!.onBackPressed() }
        compose.runOnIdle { assertEquals(Destination.HISTORY, destination) }
        compose.runOnIdle { dispatcher!!.onBackPressed() }
        compose.runOnIdle { assertEquals(Destination.MORE, destination) }

        compose.runOnIdle {
            destination = Destination.EXERCISES
            imeVisible = true
        }
        compose.onNodeWithText("More").assertIsSelected()
        compose.runOnIdle { dispatcher!!.onBackPressed() }
        compose.runOnIdle {
            assertEquals(1, platformBacks)
            assertEquals(Destination.EXERCISES, destination)
            imeVisible = false
        }
        compose.runOnIdle { dispatcher!!.onBackPressed() }
        compose.runOnIdle { assertEquals(Destination.MORE, destination) }
    }
}

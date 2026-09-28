package com.petermathie.vibecheck

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.ui.AppFabHostState
import com.petermathie.vibecheck.ui.AppFloatingAction
import com.petermathie.vibecheck.ui.Destination
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.LocalAppFabClearance
import com.petermathie.vibecheck.ui.LocalAppFabHost
import com.petermathie.vibecheck.ui.MeasurementsScreen
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@android.annotation.SuppressLint("ViewModelConstructorInComposable")
class MeasurementsUiTest {
    private lateinit var database: VibeDatabase

    private val deviceConfiguration = DeviceConfigurationRule()
    private val lifecycle = ComposeRoomLifecycleRule {
        if (::database.isInitialized) database else null
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(deviceConfiguration).around(lifecycle)
    private val compose get() = lifecycle.compose

    @Test
    fun addEntrySavesBodyweightAndNotesThenUpdatesCalendarAndChart() {
        database = inMemoryDatabase()
        val viewModel = lifecycle.own(EditorViewModel(database))
        setBodyContent(viewModel)

        compose.onNodeWithContentDescription("Add body entry").performClick()
        compose.onNodeWithContentDescription("Add body entry dialog").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bodyweight (optional)").performTextInput("78.126")
        compose.onNodeWithContentDescription("Notes (optional)").performTextInput("Morning check-in")
        compose.onNodeWithText("Save without photo").performClick()
        compose.waitUntil(15_000) {
            runBlocking { database.editorDao().measurements().first().isNotEmpty() }
        }

        val saved = runBlocking { database.editorDao().measurements().first().single() }
        assertEquals("Bodyweight", saved.metric)
        assertEquals(78.126, saved.value, 0.0)
        assertEquals("Morning check-in", saved.notes)
        compose.onNodeWithContentDescription("Add body entry dialog").assertDoesNotExist()
        compose.onNodeWithContentDescription("Bodyweight calendar card").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Bodyweight trend"))
        compose.onNodeWithContentDescription("Progress chart", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Photos").assertDoesNotExist()
    }

    @Test
    fun bodyPageContainsOnlyCalendarTrendAndSquareBottomEndAction() {
        database = inMemoryDatabase()
        val viewModel = lifecycle.own(EditorViewModel(database))
        setBodyContent(viewModel)

        compose.onNodeWithText("Calendar").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Bodyweight trend"))
        compose.onNodeWithText("Bodyweight trend").assertIsDisplayed()
        compose.onNodeWithText("Photos").assertDoesNotExist()
        compose.onNodeWithText("Bodyweight (optional)").assertDoesNotExist()

        val action = compose.onNodeWithContentDescription("Add body entry").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("action=$action root=$root", action.left > root.center.x)
        assertEquals(action.width, action.height, 0.5f)
    }

    @Test
    fun calendarRemainsNonOverlappingAtNarrowWidthAndLargeType() {
        database = inMemoryDatabase()
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                VibeCheckTheme {
                    Box(Modifier.size(320.dp, 760.dp)) {
                        BodyWithFab(viewModel)
                    }
                }
            }
        }

        val heading = compose.onNodeWithContentDescription("Bodyweight calendar heading")
            .fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithContentDescription("Bodyweight calendar month controls")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("heading=$heading controls=$controls", heading.bottom <= controls.top + 0.5f)
        compose.onNode(hasScrollAction())
            .performScrollToNode(hasContentDescription("Bodyweight calendar weekday labels"))
        val labels = compose.onNodeWithContentDescription("Bodyweight calendar weekday labels")
            .fetchSemanticsNode().boundsInRoot
        val firstWeek = compose.onNodeWithContentDescription("Bodyweight calendar week 1")
            .fetchSemanticsNode().boundsInRoot
        assertTrue("labels=$labels firstWeek=$firstWeek", labels.bottom <= firstWeek.top + 0.5f)
    }

    @Test
    fun entryDialogSupportsDateWeightNotesAndPhotoPathAtLargeType() {
        database = inMemoryDatabase()
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                VibeCheckTheme {
                    Box(Modifier.size(320.dp, 760.dp)) {
                        BodyWithFab(viewModel)
                    }
                }
            }
        }

        compose.onNodeWithContentDescription("Add body entry").performClick()
        compose.onNodeWithContentDescription("Add body entry dialog").assertIsDisplayed()
        compose.onNodeWithText("Save without photo").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Notes (optional)").performTextInput("Post-workout")
        compose.onNodeWithText("Save without photo").assertIsEnabled()
        compose.onNodeWithText("Add photo and save").assertIsEnabled()
        compose.onNodeWithContentDescription("Choose body entry date").performClick()
        compose.onNodeWithContentDescription("Add body entry dialog").assertDoesNotExist()
        compose.onNodeWithText("Select body entry date").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Add body entry dialog").assertIsDisplayed()
    }

    @Test
    fun noteOnlyEntryIsPersistedWithoutInventingBodyweight() {
        database = inMemoryDatabase()
        val viewModel = lifecycle.own(EditorViewModel(database))
        setBodyContent(viewModel)

        compose.onNodeWithContentDescription("Add body entry").performClick()
        compose.onNodeWithContentDescription("Notes (optional)").performTextInput("Recovery day")
        compose.onNodeWithText("Save without photo").performClick()
        compose.waitUntil(15_000) {
            java.io.File(
                ApplicationProvider.getApplicationContext<Context>().filesDir,
                "body-notes/${java.time.LocalDate.now().toEpochDay()}.txt",
            ).isFile
        }
        assertTrue(runBlocking { database.editorDao().measurements().first().isEmpty() })
        compose.onNodeWithText("No bodyweight entries yet").assertIsDisplayed()
    }

    private fun inMemoryDatabase(): VibeDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        java.io.File(context.filesDir, "body-notes").listFiles().orEmpty().forEach { it.delete() }
        return Room.inMemoryDatabaseBuilder(
            context,
            VibeDatabase::class.java,
        ).build()
    }

    private fun setBodyContent(viewModel: EditorViewModel) {
        compose.setContent {
            VibeCheckTheme {
                Box(Modifier.size(411.dp, 780.dp)) {
                    BodyWithFab(viewModel)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun BodyWithFab(viewModel: EditorViewModel) {
    val host = remember { AppFabHostState(Destination.MEASUREMENTS) }
    CompositionLocalProvider(
        LocalAppFabHost provides host,
        LocalAppFabClearance provides 88.dp,
    ) {
        MeasurementsScreen(viewModel)
        host.action?.takeIf { it.visible }?.let {
            Box(Modifier.fillMaxSize()) {
                AppFloatingAction(
                    it,
                    host,
                    Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }
}

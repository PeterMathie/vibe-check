package com.petermathie.vibecheck

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.*
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBack
import com.petermathie.vibecheck.data.DemoRemovalSummary
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.SettingsScreen
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@android.annotation.SuppressLint("ViewModelConstructorInComposable")
class SettingsUiTest {
    private lateinit var database: VibeDatabase

    @get:Rule
    val lifecycle = ComposeRoomLifecycleRule {
        if (::database.isInitialized) database else null
    }
    private val compose get() = lifecycle.compose

    @Test
    fun permissionsUseSwitchesAndNoticesRenderMarkdown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                SettingsScreen(viewModel, onStyle = {}, onRemoveDemo = {})
            }
        }

        val list = compose.onNode(hasScrollToIndexAction())
        compose.onNodeWithText("Back").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to More").assertHasClickAction()
        mapOf(
            "Display & units" to listOf("Pounds (lb)", "Female anatomy"),
            "Workout & timers" to listOf(
                "Start rest automatically", "Scroll to next empty set",
                "Timer notifications", "Precise background timers",
            ),
            "Interaction & accessibility" to listOf("Haptics", "Reduced motion"),
        ).forEach { (section, controls) ->
            list.performScrollToNode(hasText(section))
            compose.onNodeWithText(section).assertExists()
            controls.forEach { label ->
                list.performScrollToNode(hasText(label))
                compose.onNode(isToggleable() and hasAnySibling(hasText(label))).assertExists()
            }
        }
        compose.onNodeWithText("Enable timer notifications").assertDoesNotExist()
        compose.onNodeWithText("Allow precise background timers").assertDoesNotExist()

        listOf(
            "Cancel rest timer", "Data & backups", "JSON backup", "Restore / import structured JSON",
            "CSV export", "Remove demo data", "About",
        ).forEach { label ->
            list.performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        list.performScrollToNode(hasText("Last backup:", substring = true))
        compose.onNodeWithText("Last backup:", substring = true).assertIsDisplayed()
        list.performScrollToNode(hasText("Open-source asset notices"))
        compose.onNodeWithText("Vibe Check ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})").assertExists()
        compose.onNodeWithText("Open-source asset notices").performClick()
        compose.onNodeWithText("Third-party notices").assertExists()
        compose.onNodeWithText("MuscleMap anatomy vectors").assertExists()
        compose.onNodeWithText("# Third-party notices").assertDoesNotExist()
        compose.onNodeWithText("**MuscleMap**", substring = true).assertDoesNotExist()
    }

    @Test
    fun demoRemovalRequiresConfirmationAndReportsExactResult() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        var invoked = false
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                SettingsScreen(
                    viewModel,
                    onStyle = {},
                    onRemoveDemo = { report ->
                        invoked = true
                        report(Result.success(DemoRemovalSummary(2, 1, 3, 4, 5)))
                    },
                )
            }
        }

        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Remove demo data"))
        compose.onNodeWithText("Remove demo data").performClick()
        compose.onNodeWithText("Remove all demo personal data?").assertExists()
        compose.onNodeWithText("Your exercise catalogue and your own records stay intact.", substring = true).assertExists()
        compose.runOnIdle { assertFalse(invoked) }

        compose.onNode(hasText("Remove demo data") and hasAnyAncestor(isDialog())).performClick()

        compose.runOnIdle { assertTrue(invoked) }
        compose.onNodeWithText(
            "Removed demo personal data: 2 workouts, 1 programmes, 3 habits, 4 body entries and 5 photos.",
        ).assertExists()
    }

    @Test
    fun backDismissesLocalDialogsAndWaitsForDemoRemovalResult() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val viewModel = lifecycle.own(EditorViewModel(database))
        var dispatcher: OnBackPressedDispatcher? = null
        var removalResult: ((Result<DemoRemovalSummary>) -> Unit)? = null
        var backs = 0
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            VibeCheckTheme {
                SettingsScreen(
                    viewModel,
                    onStyle = {},
                    onRemoveDemo = { removalResult = it },
                    onBack = { backs++ },
                )
            }
        }

        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Remove demo data"))
        compose.onNodeWithText("Remove demo data").performClick()
        pressBack()
        compose.onNodeWithText("Remove all demo personal data?").assertDoesNotExist()
        compose.runOnIdle {
            assertNull(removalResult)
            assertEquals(0, backs)
        }

        list.performScrollToNode(hasContentDescription("About Reduced motion"))
        compose.onNodeWithContentDescription("About Reduced motion").performClick()
        compose.onNodeWithText("Reduced motion disables touch ripples", substring = true).assertExists()
        pressBack()
        compose.onNodeWithText("Reduced motion disables touch ripples", substring = true).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, backs) }

        list.performScrollToNode(hasText("Remove demo data"))
        compose.onNodeWithText("Remove demo data").performClick()
        compose.onNode(hasText("Remove demo data") and hasAnyAncestor(isDialog())).performClick()
        list.performScrollToIndex(0)
        compose.onNodeWithContentDescription("Back to More").performClick()
        compose.runOnIdle { dispatcher!!.onBackPressed() }
        compose.runOnIdle {
            assertEquals(0, backs)
            removalResult!!(Result.failure(IllegalStateException("Blocked")))
        }
        list.performScrollToNode(hasText("Demo data was not fully removed: Blocked"))
        compose.onNodeWithText("Demo data was not fully removed: Blocked").assertIsDisplayed()
        list.performScrollToIndex(0)
        compose.onNodeWithContentDescription("Back to More").performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }
}

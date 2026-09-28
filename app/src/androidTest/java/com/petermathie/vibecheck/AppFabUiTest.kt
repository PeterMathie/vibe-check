package com.petermathie.vibecheck

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.ui.AppFabAction
import com.petermathie.vibecheck.ui.AppFabDestination
import com.petermathie.vibecheck.ui.AppFabHostState
import com.petermathie.vibecheck.ui.AppFloatingAction
import com.petermathie.vibecheck.ui.Destination
import com.petermathie.vibecheck.ui.LocalAppFabClearance
import com.petermathie.vibecheck.ui.LocalAppFabHost
import com.petermathie.vibecheck.ui.RegisterAppFabAction
import com.petermathie.vibecheck.ui.ScreenList
import com.petermathie.vibecheck.ui.visibleUnlessBlocked
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppFabUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun actionIsBottomStartAccessibleModalAwareAndDebounced() {
        var invocations by mutableIntStateOf(0)
        var modalOpen by mutableStateOf(false)
        compose.setContent {
            VibeCheckTheme {
                val host = remember { AppFabHostState(Destination.PROGRAMMES) }
                CompositionLocalProvider(LocalAppFabHost provides host) {
                    Box(Modifier.size(411.dp, 780.dp)) {
                        RegisterAppFabAction(
                            owner = Destination.PROGRAMMES,
                            destination = AppFabDestination.AddProgrammeItem,
                            visible = !modalOpen,
                        ) {
                            invocations++
                            modalOpen = true
                        }

                        host.action?.takeIf { it.visible }?.let { action ->
                            Box(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                                AppFloatingAction(action, host)
                            }
                        }
                    }
                }
            }
        }

        val action = compose.onNodeWithContentDescription("Add exercise or stretch")
            .assertIsDisplayed()
        val bounds = action.fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("fab=$bounds root=$root", bounds.left < root.center.x)
        assertTrue("fab=$bounds root=$root", bounds.bottom <= root.bottom)
        action.performClick()
        compose.onNodeWithContentDescription("Add exercise or stretch").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, invocations) }

        val host = AppFabHostState(Destination.PROGRAMMES)
        val direct = AppFabAction(
            owner = Destination.PROGRAMMES,
            destination = AppFabDestination.NewProgramme,
        ) { invocations++ }
        compose.runOnIdle {
            host.invoke(direct)
            host.invoke(direct)
            host.invoke(
                AppFabAction(
                    owner = Destination.PROGRAMMES,
                    destination = AppFabDestination.AddProgrammeItem,
                ) { invocations++ },
            )
        }
        assertEquals(3, invocations)
    }

    @Test
    fun keyboardAndDragStateHideTheActionWithoutLosingItsRouteRegistration() {
        var keyboardOpen by mutableStateOf(false)
        var dragging by mutableStateOf(false)
        compose.setContent {
            VibeCheckTheme {
                val host = remember { AppFabHostState(Destination.HABITS) }
                val registeredAction = remember {
                    AppFabAction(
                        owner = Destination.HABITS,
                        destination = AppFabDestination.NewHabit,
                    ) {}
                }
                Box(Modifier.size(411.dp, 780.dp)) {
                    registeredAction.visibleUnlessBlocked(dragging, keyboardOpen)?.let {
                        AppFloatingAction(it, host, Modifier.align(Alignment.BottomStart))
                    }
                }
            }
        }

        compose.onNodeWithContentDescription("Create new habit").assertIsDisplayed()
        compose.runOnIdle { keyboardOpen = true }
        compose.onNodeWithContentDescription("Create new habit").assertDoesNotExist()
        compose.runOnIdle {
            keyboardOpen = false
            dragging = true
        }
        compose.onNodeWithContentDescription("Create new habit").assertDoesNotExist()
        compose.runOnIdle { dragging = false }
        compose.onNodeWithContentDescription("Create new habit").assertIsDisplayed()
    }

    @Test
    fun screenListBottomClearanceKeepsFinalItemAboveFloatingAction() {
        compose.setContent {
            VibeCheckTheme {
                Box(Modifier.size(411.dp, 780.dp)) {
                    CompositionLocalProvider(LocalAppFabClearance provides 88.dp) {
                        ScreenList {
                            items(30) { index ->
                                androidx.compose.material3.Text("Item $index")
                            }
                        }
                    }
                    val host = remember { AppFabHostState(Destination.HABITS) }
                    AppFloatingAction(
                        action = AppFabAction(
                            owner = Destination.HABITS,
                            destination = AppFabDestination.NewHabit,
                        ) {},
                        host = host,
                        modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    )
                }
            }
        }

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Item 29"))
        val item = compose.onNodeWithText("Item 29").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithContentDescription("Create new habit").fetchSemanticsNode().boundsInRoot
        assertTrue("item=$item action=$action", item.bottom <= action.top)
    }
}

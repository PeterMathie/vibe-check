package com.petermathie.vibecheck.ui

import android.os.SystemClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal sealed interface AppFabDestination {
    val label: String
    val accessibilityLabel: String

    data object NewProgramme : AppFabDestination {
        override val label = "New programme"
        override val accessibilityLabel = "Create new programme"
    }

    data object AddProgrammeItem : AppFabDestination {
        override val label = "Add"
        override val accessibilityLabel = "Add exercise or stretch"
    }

    data object NewHabit : AppFabDestination {
        override val label = "New habit"
        override val accessibilityLabel = "Create new habit"
    }

    data object NewExercise : AppFabDestination {
        override val label = "New exercise"
        override val accessibilityLabel = "Add custom exercise"
    }

    data object AddWorkoutExercise : AppFabDestination {
        override val label = "Add exercise"
        override val accessibilityLabel = "Add exercise to workout"
    }

    data object AddProgressPhoto : AppFabDestination {
        override val label = "Add photo"
        override val accessibilityLabel = "Add progress photo"
    }
}

internal data class AppFabAction(
    val owner: Destination,
    val destination: AppFabDestination,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val onClick: () -> Unit,
)

@Stable
internal class AppFabHostState(private val activeDestination: Destination? = null) {
    var action by mutableStateOf<AppFabAction?>(null)
        private set
    var dragging by mutableStateOf(false)
        private set
    private var lastClickAt by mutableLongStateOf(0L)
    private var lastAction: AppFabAction? = null

    fun register(action: AppFabAction) {
        if (activeDestination == null || action.owner == activeDestination) {
            this.action = action
        }
    }

    fun unregister(action: AppFabAction) {
        if (this.action === action) this.action = null
    }

    fun updateDragging(value: Boolean) {
        dragging = value
    }

    fun clear() {
        action = null
        dragging = false
    }

    fun invoke(action: AppFabAction, onAccepted: () -> Unit = {}) {
        val now = SystemClock.elapsedRealtime()
        if (!action.enabled || action === lastAction && now - lastClickAt < 500L) return
        lastClickAt = now
        lastAction = action
        onAccepted()
        action.onClick()
    }
}

internal val LocalAppFabHost = compositionLocalOf<AppFabHostState?> { null }
internal val LocalAppFabClearance = compositionLocalOf<Dp> { 0.dp }

internal fun AppFabAction?.visibleUnlessBlocked(dragging: Boolean, imeVisible: Boolean): AppFabAction? =
    this?.takeIf { it.visible && !dragging && !imeVisible }

@Composable
internal fun RegisterAppFabAction(
    owner: Destination,
    destination: AppFabDestination,
    enabled: Boolean = true,
    visible: Boolean = true,
    onClick: () -> Unit,
) {
    val host = LocalAppFabHost.current ?: return
    val currentOnClick by rememberUpdatedState(onClick)
    val action = remember(owner, destination, enabled, visible) {
        AppFabAction(owner, destination, enabled, visible) { currentOnClick() }
    }
    DisposableEffect(host, action) {
        host.register(action)
        onDispose { host.unregister(action) }
    }
}

@Composable
internal fun AppFloatingAction(action: AppFabAction, host: AppFabHostState, modifier: Modifier = Modifier) {
    val haptics = rememberVibeHaptics()
    ExtendedFloatingActionButton(
        onClick = {
            host.invoke(action) {
                haptics.perform(VibeHapticEvent.SELECTION)
            }
        },
        modifier = modifier.semantics { contentDescription = action.destination.accessibilityLabel },
        icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
        text = { Text(action.destination.label) },
        expanded = true,
    )
}

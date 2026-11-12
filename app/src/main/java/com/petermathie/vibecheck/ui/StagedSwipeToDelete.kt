package com.petermathie.vibecheck.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal class StagedSwipeState {
    var revealed by mutableStateOf(false)
    var revealedAtStart by mutableStateOf(false)
    var dragging by mutableStateOf(false)
    var deleting by mutableStateOf(false)
    var distance by mutableFloatStateOf(0f)
}

@Composable
internal fun StagedSwipeToDelete(
    label: String,
    onDelete: suspend () -> Unit,
    modifier: Modifier = Modifier,
    state: StagedSwipeState = remember { StagedSwipeState() },
    content: @Composable () -> Unit,
) = with(state) {
    var width by remember { mutableIntStateOf(0) }
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val restingOffset by animateFloatAsState(if (revealed) -width / 3f else 0f, label = "delete reveal")
    val offset = if (dragging) {
        ((if (revealedAtStart) -width / 3f else 0f) + distance).coerceIn(-width.toFloat(), 0f)
    } else restingOffset
    val currentDelete by rememberUpdatedState(onDelete)
    val scope = rememberCoroutineScope()
    fun delete() {
        if (!deleting) {
            deleting = true
            scope.launch {
                try {
                    currentDelete()
                } finally {
                    deleting = false
                }
            }
        }
    }
    Box(
        modifier.fillMaxWidth().clipToBounds()
            .onSizeChanged { width = it.width }
            .semantics {
                contentDescription = "Swipe actions for $label"
                stateDescription = if (revealed) "Delete revealed" else "Closed"
                customActions = if (revealed) listOf(
                    CustomAccessibilityAction("Delete $label") { delete(); true },
                    CustomAccessibilityAction("Close delete") { revealed = false; true },
                ) else listOf(
                    CustomAccessibilityAction("Reveal delete") { revealed = true; true },
                )
            }
            .pointerInput(state, label, threshold, deleting) {
                if (!deleting) detectHorizontalDragGestures(
                    onDragStart = {
                        revealedAtStart = revealed
                        distance = 0f
                        dragging = true
                    },
                    onDragEnd = {
                        dragging = false
                        if (distance <= -threshold) {
                            if (revealedAtStart) delete() else revealed = true
                        } else if (distance >= threshold) {
                            revealed = false
                        }
                        distance = 0f
                    },
                    onDragCancel = { dragging = false; distance = 0f },
                    onHorizontalDrag = { change, delta -> change.consume(); distance += delta },
                )
            },
    ) {
        if (revealed || dragging) {
            IconButton(
                onClick = { delete() },
                enabled = revealed && !dragging && !deleting,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Outlined.Delete, "Delete $label", tint = MaterialTheme.colorScheme.error)
            }
        }
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }) { content() }
    }
}

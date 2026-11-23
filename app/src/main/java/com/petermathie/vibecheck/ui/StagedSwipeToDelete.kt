package com.petermathie.vibecheck.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
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
import com.petermathie.vibecheck.ui.theme.LocalVibeReducedMotion

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
) = StagedSwipeAction(label, onDelete, modifier, state, edit = false, content)

@Composable
internal fun StagedSwipeToEdit(
    label: String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) = StagedSwipeAction(label, { onEdit() }, modifier, remember { StagedSwipeState() }, edit = true, content)

@Composable
private fun StagedSwipeAction(
    label: String,
    onAction: suspend () -> Unit,
    modifier: Modifier,
    state: StagedSwipeState,
    edit: Boolean,
    content: @Composable () -> Unit,
) = with(state) {
    var width by remember { mutableIntStateOf(0) }
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    val revealWidth = if (edit) with(LocalDensity.current) { 64.dp.toPx() } else width / 3f
    val direction = if (edit) 1f else -1f
    val actionName = if (edit) "Edit" else "Delete"
    val restingOffset by animateFloatAsState(
        if (revealed) direction * revealWidth else 0f,
        if (edit && LocalVibeReducedMotion.current) snap() else spring(),
        label = "swipe action reveal",
    )
    val offset = if (dragging) {
        direction * ((if (revealedAtStart) revealWidth else 0f) + direction * distance)
            .coerceIn(0f, width.toFloat())
    } else restingOffset
    val currentAction by rememberUpdatedState(onAction)
    val scope = rememberCoroutineScope()
    fun act() {
        if (!deleting) {
            deleting = true
            if (edit) revealed = false
            scope.launch {
                try {
                    currentAction()
                } finally {
                    deleting = false
                }
            }
        }
    }
    Box(
        modifier.then(if (edit) Modifier.widthIn(min = 64.dp) else Modifier.fillMaxWidth()).clipToBounds()
            .onSizeChanged { width = it.width }
            .semantics {
                contentDescription = "Swipe actions for $label"
                stateDescription = if (revealed) "$actionName revealed" else "Closed"
                customActions = if (revealed) listOf(
                    CustomAccessibilityAction("$actionName $label") { act(); true },
                    CustomAccessibilityAction("Close ${actionName.lowercase()}") { revealed = false; true },
                ) else listOf(
                    CustomAccessibilityAction("Reveal ${actionName.lowercase()}") { revealed = true; true },
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
                        if (distance * direction >= threshold) {
                            if (revealedAtStart && !edit) act() else revealed = true
                        } else if (distance * direction <= -threshold) {
                            revealed = false
                        }
                        distance = 0f
                    },
                    onDragCancel = { dragging = false; distance = 0f },
                    onHorizontalDrag = { change, delta -> change.consume(); distance += delta },
                )
            },
    ) {
        if (edit && (revealed || dragging)) {
            Box(Modifier.matchParentSize().drawWithContent {
                clipRect(right = offset.coerceAtLeast(0f)) { this@drawWithContent.drawContent() }
            }) {
                IconButton(
                    onClick = { act() },
                    enabled = revealed && !dragging && !deleting,
                    modifier = Modifier.align(Alignment.CenterStart).padding(horizontal = 8.dp),
                ) { Icon(Icons.Outlined.Edit, "Edit $label settings", tint = MaterialTheme.colorScheme.primary) }
            }
        } else if (!edit && (revealed || dragging)) {
            IconButton(
                onClick = { act() },
                enabled = revealed && !dragging && !deleting,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Outlined.Delete, "Delete $label", tint = MaterialTheme.colorScheme.error)
            }
        }
        Box(Modifier.offset { IntOffset(offset.roundToInt(), 0) }) { content() }
    }
}

package com.petermathie.vibecheck.ui

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.DragScope
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.petermathie.vibecheck.ui.theme.LocalVibeMotion
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.LocalVibeReducedMotion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val LocalHabitOverlay = compositionLocalOf<(Boolean) -> Unit> { {} }

@Composable
internal fun HabitGlassDialog(
    title: String,
    onDismiss: () -> Unit,
    onBack: (() -> Unit)? = null,
    actions: @Composable FlowRowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalVibePalette.current
    val motion = LocalVibeMotion.current
    val glassTint = if (palette.isDark) lerp(palette.surfaceFloating, palette.textPrimary, 0.06f) else palette.surfaceFloating
    val overlay = LocalHabitOverlay.current
    DisposableEffect(Unit) {
        overlay(true)
        onDispose { overlay(false) }
    }
    Dialog(
        onDismissRequest = onBack ?: onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.apply {
                setDimAmount(0f)
                setWindowAnimations(0)
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier.matchParentSize()
                    .background(palette.scrim.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ).clearAndSetSemantics {},
            )
            Box(
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                        .semantics { paneTitle = title }
                        .animateContentSize(tween(motion.expandMillis), alignment = Alignment.Center)
                        .pointerInput(Unit) { detectTapGestures(onTap = {}) },
                    shape = RoundedCornerShape(24.dp),
                    color = glassTint.copy(
                        alpha = if (Build.VERSION.SDK_INT < 31) 0.97f else if (palette.isDark) 0.68f else 0.8f,
                    ),
                    contentColor = palette.textPrimary,
                    border = BorderStroke(1.dp, palette.border.copy(alpha = 0.5f)),
                ) {
                    ReorderOverlayHost {
                        Column(
                            Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                onBack?.let { back ->
                                    IconButton(onClick = back) {
                                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back")
                                    }
                                }
                                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                                IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close") }
                            }
                            key(title) {
                                val scroll = rememberScrollState()
                                val reorderContext = rememberReorderScrollContext(scroll)
                                CompositionLocalProvider(LocalReorderScrollContext provides reorderContext) {
                                    Box(Modifier.weight(1f, fill = false).fillMaxWidth()) {
                                        Column(
                                            Modifier.fillMaxWidth().padding(end = 8.dp)
                                                .reorderScrollViewport(reorderContext).verticalScroll(scroll),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            content = content,
                                        )
                                        HabitDialogScrollIndicator(scroll)
                                    }
                                }
                            }
                            FlowRow(
                                Modifier.fillMaxWidth().padding(end = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                                content = actions,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.HabitDialogScrollIndicator(scroll: ScrollState) {
    val maximum = scroll.maxValue
    val viewport = scroll.viewportSize
    if (maximum <= 0 || viewport <= 0) return
    val height = with(LocalDensity.current) { viewport.toDp() }
    val thumb = (height * (viewport.toFloat() / (viewport.toFloat() + maximum)))
        .coerceAtLeast(24.dp).coerceAtMost(height)
    val offset = (height - thumb) * (scroll.value.toFloat() / maximum).coerceIn(0f, 1f)
    val scrollPerPixel = maximum / (viewport - with(LocalDensity.current) { thumb.toPx() }).coerceAtLeast(1f)
    val dragState = remember(scroll, scrollPerPixel) {
        object : DraggableState {
            override suspend fun drag(dragPriority: MutatePriority, block: suspend DragScope.() -> Unit) {
                scroll.scroll(dragPriority) {
                    val content = this
                    block(object : DragScope {
                        override fun dragBy(pixels: Float) {
                            content.scrollBy(pixels * scrollPerPixel)
                        }
                    })
                }
            }

            override fun dispatchRawDelta(delta: Float) {
                scroll.dispatchRawDelta(delta * scrollPerPixel)
            }
        }
    }
    val scope = rememberCoroutineScope()
    val color = LocalVibePalette.current.textSecondary.copy(alpha = 0.6f)
    Box(Modifier.matchParentSize()) {
        Box(
            Modifier.align(Alignment.TopEnd).offset(y = offset).size(width = 24.dp, height = thumb)
                .draggable(dragState, Orientation.Vertical, startDragImmediately = true)
                .semantics {
                    contentDescription = "Scroll dialog"
                    progressBarRangeInfo = ProgressBarRangeInfo(scroll.value.toFloat(), 0f..maximum.toFloat())
                    setProgress { target ->
                        scope.launch { scroll.scrollTo(target.roundToInt()) }
                        true
                    }
                }.testTag("Habit dialog scrollbar"),
        ) {
            Box(
                Modifier.align(Alignment.TopEnd).fillMaxHeight().width(3.dp)
                    .background(color, RoundedCornerShape(50))
                    .testTag("Habit dialog scroll indicator"),
            )
        }
    }
}

@Composable
internal fun HabitTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    readOnly: Boolean = false,
    error: String? = null,
    plain: Boolean = false,
    textAlign: TextAlign = TextAlign.Start,
    hint: String? = null,
    accessibilityLabel: String = label,
) {
    val palette = LocalVibePalette.current
    val container = if (plain) Color.Transparent else palette.surfaceInset.copy(alpha = 0.3f)
    val defaults = TextFieldDefaults.colors()
    val input: @Composable () -> Unit = {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = if (plain) {
                {
                    Text(
                        label,
                        if (textAlign == TextAlign.End) Modifier.fillMaxWidth() else Modifier,
                        textAlign = textAlign,
                    )
                }
            } else null,
            placeholder = hint?.let { { Text(it, color = palette.textSecondary) } },
            modifier = if (plain) modifier.fillMaxWidth()
                else Modifier.fillMaxWidth().semantics { contentDescription = accessibilityLabel },
            textStyle = LocalTextStyle.current.copy(textAlign = textAlign),
            singleLine = true,
            readOnly = readOnly,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            shape = if (plain) TextFieldDefaults.shape else MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = container,
                unfocusedContainerColor = container,
                errorContainerColor = container,
                disabledContainerColor = if (plain) Color.Transparent else defaults.disabledContainerColor,
                focusedIndicatorColor = if (plain) Color.Transparent else defaults.focusedIndicatorColor,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = if (plain) Color.Transparent else defaults.errorIndicatorColor,
                disabledIndicatorColor = if (plain) Color.Transparent else defaults.disabledIndicatorColor,
            ),
        )
    }
    if (plain) {
        input()
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HabitSettingLabel(label)
            input()
        }
    }
}

@Composable
private fun HabitSettingLabel(label: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = LocalVibePalette.current.textSecondary)
}

@Composable
internal fun HabitSettingValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    trailingIcon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalVibePalette.current
    val content: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, style = MaterialTheme.typography.bodyLarge)
                detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = palette.textSecondary) }
            }
            trailingIcon?.let { Icon(it, null, tint = palette.textSecondary) }
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HabitSettingLabel(label)
        if (onClick == null) {
            Surface(color = palette.surfaceInset.copy(alpha = 0.3f), shape = MaterialTheme.shapes.small) {
                content()
            }
        } else {
            Column(Modifier.clickable(role = Role.Button, onClick = onClick)) {
                HorizontalDivider(color = palette.border)
                content()
                HorizontalDivider(color = palette.border)
            }
        }
    }
}

internal data class HabitSettingOption(val value: String, val label: String)

private object HabitDropdownPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left
            else anchorBounds.right - popupContentSize.width
        val y = if (anchorBounds.bottom + popupContentSize.height <= windowSize.height) anchorBounds.bottom
            else anchorBounds.top - popupContentSize.height
        return IntOffset(
            x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HabitSettingDropdown(
    label: String,
    value: String,
    options: List<HabitSettingOption>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val palette = LocalVibePalette.current
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            if (it) {
                focus.clearFocus()
                keyboard?.hide()
            }
            expanded = it
        },
    ) {
        HabitSettingValue(
            label,
            options.single { it.value == value }.label,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .semantics { contentDescription = label },
            trailingIcon = Icons.Outlined.ExpandMore,
        )
        val menuContent: @Composable ColumnScope.() -> Unit = {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = {
                        if (option.value == value) Icon(Icons.Outlined.Check, "Selected")
                    },
                    modifier = Modifier.semantics { selected = option.value == value },
                    onClick = {
                        expanded = false
                        onSelect(option.value)
                    },
                )
            }
        }
        if (LocalVibeReducedMotion.current) {
            if (expanded) Popup(
                popupPositionProvider = HabitDropdownPosition,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    modifier = Modifier.exposedDropdownSize().semantics { paneTitle = "$label options" },
                    shape = MaterialTheme.shapes.small,
                    color = palette.surfaceFloating,
                    shadowElevation = MenuDefaults.ShadowElevation,
                ) {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        content = menuContent,
                    )
                }
            }
        } else {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = palette.surfaceFloating,
                shape = MaterialTheme.shapes.small,
                content = menuContent,
            )
        }
    }
}

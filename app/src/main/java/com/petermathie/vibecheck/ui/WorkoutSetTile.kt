package com.petermathie.vibecheck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.petermathie.vibecheck.data.local.BandEntity
import com.petermathie.vibecheck.data.local.ExerciseVariationEntity
import com.petermathie.vibecheck.data.local.WorkoutEntryDraftEntity
import com.petermathie.vibecheck.data.local.WorkoutSetEntity
import com.petermathie.vibecheck.data.local.WorkoutSetBandEntity
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.MeasurementMetric
import com.petermathie.vibecheck.domain.programme.ResistanceMetric
import com.petermathie.vibecheck.domain.workout.*
import com.petermathie.vibecheck.ui.theme.LocalVibePalette

// Sums and validation messages below a box must not move its visual centre.
private val SetBoxCenter = HorizontalAlignmentLine { previous, next -> maxOf(previous, next) }
private val TimingBoxGap = 2.dp
internal data class SetGroupLayout(val large: Boolean = false)
private val LocalLargeSetGroup = staticCompositionLocalOf { false }

internal fun Modifier.setBorderLabelBackground(parentColor: Color, setColor: Color): Modifier = drawBehind {
    drawRect(parentColor)
    drawRect(setColor, topLeft = Offset(0f, size.height / 2), size = Size(size.width, size.height / 2))
}

private fun Placeable.boxCenter(): Int =
    this[SetBoxCenter].takeUnless { it == AlignmentLine.Unspecified } ?: height / 2

private fun Modifier.withBoxCenter(height: Dp): Modifier = layout { measurable, constraints ->
    val content = measurable.measure(constraints)
    layout(content.width, content.height, mapOf(SetBoxCenter to if (content.height == 0) 0 else height.roundToPx() / 2)) {
        content.placeRelative(0, 0)
    }
}

@Composable
internal fun setSingleRowHeight(): Dp {
    val density = LocalDensity.current
    val label = MaterialTheme.typography.labelSmall
    val number = MaterialTheme.typography.bodyMedium
    val labelledHeight = with(density) { label.lineHeight.toDp() + number.lineHeight.toDp() } + 8.dp
    return maxOf(48.dp * maxOf(1f, density.fontScale), labelledHeight)
}

@Composable
internal fun timingCellWidth(): Dp = 28.dp * maxOf(1f, LocalDensity.current.fontScale)

@Composable
internal fun timingCellHeight(): Dp = setSingleRowHeight() / 2

internal fun previousSetSummary(
    set: WorkoutSetEntity,
    config: ExerciseInputConfig,
    preferredWeightUnit: String,
    snapshots: List<WorkoutSetBandEntity>,
    bands: List<BandEntity>,
): String? {
    val parts = mutableListOf<String>()
    when (config.loggingResistance()) {
        ResistanceMetric.KILOGRAMS -> {
            val unit = if (config.addedWeight) preferredWeightUnit else config.weightUnit ?: "kg"
            val weight = if (config.addedWeight) set.addedWeightKg else set.weightKg
            weight?.let { parts += "${formatQuantitative(if (unit == "lb") it * 2.2046226218 else it)}$unit" }
        }
        ResistanceMetric.BANDS -> if (snapshots.isNotEmpty()) {
            parts += snapshots.joinToString("+") { snapshot ->
                when (snapshot.bandId) {
                    "band-yellow" -> "Yw"
                    "band-red" -> "Rd"
                    "band-black" -> "Bl"
                    "band-purple" -> "Pr"
                    else -> bands.firstOrNull { it.id == snapshot.bandId }?.let {
                        "#%06X".format(it.colourArgb and 0xFFFFFFL)
                    } ?: snapshot.nameSnapshot
                }
            }
        }
        else -> Unit
    }
    fun seconds(millis: Long) = "${formatQuantitative(millis / 1000.0)}s"
    when (config.loggingMeasurement()) {
        MeasurementMetric.REPETITIONS -> set.reps?.let { parts += formatQuantitative(it) }
        MeasurementMetric.RANGE_OF_MOTION -> set.romValue?.let {
            parts += "ROM: ${formatQuantitative(it)}${set.romUnit.orEmpty()}"
        }
        MeasurementMetric.HOLD -> {
            val duration = if (config.timeUnderTension && !config.timeHeld) set.timeUnderTensionMillis else set.holdMillis
            duration?.let { parts += seconds(it) }
        }
        MeasurementMetric.SKILL_HOLD -> {
            val attempts = timingMillis(set.tutEntriesMillis)
            val sum = if (attempts.isNotEmpty()) attempts.sum() else set.holdMillis
            when {
                sum != null && set.timeUnderTensionMillis != null ->
                    parts += "${seconds(sum)} / ${seconds(set.timeUnderTensionMillis)}"
                sum != null -> parts += seconds(sum)
                set.timeUnderTensionMillis != null -> parts += "Total: ${seconds(set.timeUnderTensionMillis)}"
            }
        }
        null -> Unit
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Prev: ")
}

@Composable
internal fun WorkoutSetVariantPicker(
    identity: String,
    ordinal: Int,
    variantName: String,
    currentVariantId: String?,
    variations: List<ExerciseVariationEntity>,
    enabled: Boolean,
    showName: Boolean,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    parentBackgroundColor: Color = backgroundColor,
    onSelect: (ExerciseVariationEntity) -> Unit,
) {
    var expanded by remember(ordinal) { mutableStateOf(false) }
    val palette = LocalVibePalette.current
    val glassTint = if (palette.isDark) lerp(palette.surfaceFloating, palette.textPrimary, 0.06f)
        else palette.surfaceFloating
    val selectable = variations.isNotEmpty()
    if (!selectable && (!showName || variantName.isBlank())) return
    Box(modifier) {
        Box(
            Modifier.heightIn(min = 16.dp).widthIn(min = if (selectable) 36.dp else 0.dp)
                .then(if (selectable) Modifier
                    .clickable(enabled = enabled, role = Role.Button) { expanded = true }
                    .semantics {
                        contentDescription = "Change variant for $identity"
                        stateDescription = variantName
                    } else Modifier)
                .padding(horizontal = 2.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (showName && variantName.isNotBlank()) Text(
                variantName,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.setBorderLabelBackground(parentBackgroundColor, backgroundColor)
                    .semantics { contentDescription = "Variant for $identity" },
            ) else if (variations.isNotEmpty()) Icon(
                Icons.Outlined.ExpandMore, null,
                Modifier.size(16.dp).setBorderLabelBackground(parentBackgroundColor, backgroundColor),
                tint = palette.textSecondary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.large,
            containerColor = glassTint.copy(alpha = 0.94f),
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, palette.border.copy(alpha = 0.5f)),
        ) {
            variations.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.textPrimary,
                        )
                    },
                    modifier = Modifier.semantics { selected = option.id == currentVariantId },
                    leadingIcon = if (option.id == currentVariantId) {
                        { Icon(Icons.Outlined.Check, null) }
                    } else null,
                    onClick = {
                        if (option.id != currentVariantId) onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun WorkoutSetTile(
    identity: String,
    showResistance: Boolean = true,
    minimumMeasurementWidth: Dp = metricControlWidth(64),
    resistanceLayout: SetGroupLayout = SetGroupLayout(),
    measurementLayout: SetGroupLayout = SetGroupLayout(),
    resistance: @Composable () -> Unit,
    measurement: @Composable () -> Unit,
    rpe: @Composable () -> Unit,
    completion: @Composable () -> Unit,
    feedback: @Composable () -> Unit,
) {
    val rowHeight = timingCellHeight()
    val dividerColour = LocalVibePalette.current.border.copy(alpha = 0.4f)
    val compactLargeGroups = listOfNotNull(
        resistanceLayout.takeIf { showResistance }, measurementLayout,
    ).count { it.large } >= 2
    Column(Modifier.fillMaxWidth()) {
        Layout(
            contents = listOf<@Composable () -> Unit>(
                {
                    CompositionLocalProvider(LocalLargeSetGroup provides (resistanceLayout.large && !compactLargeGroups)) {
                        Box(Modifier.semantics { contentDescription = "Resistance column for $identity" }) { resistance() }
                    }
                },
                {
                    CompositionLocalProvider(LocalLargeSetGroup provides (measurementLayout.large && !compactLargeGroups)) {
                    Box(Modifier.semantics { contentDescription = "Measurement column for $identity" },
                        contentAlignment = Alignment.CenterStart) {
                        measurement()
                    }
                    }
                },
                { Box(Modifier.semantics { contentDescription = "RPE column for $identity" }) { rpe() } },
                { Box(Modifier.semantics { contentDescription = "Completion column for $identity" }) { completion() } },
                {
                    repeat(if (showResistance) 2 else 1) {
                        Box(
                            Modifier.width(1.dp).height(16.dp).background(dividerColour)
                                .testTag("Value divider for $identity"),
                        )
                    }
                },
            ).filterIndexed { index, _ -> showResistance || index != 0 },
            modifier = Modifier.fillMaxWidth(),
        ) { groups, constraints ->
            val columns = groups.dropLast(1)
            val dividers = groups.last().map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
            val gap = 8.dp.roundToPx()
            val rowGap = 2.dp.roundToPx()
            val loose = constraints.copy(minWidth = 0, minHeight = 0)
            val tick = columns.last().single().measure(loose)
            val contentWidth = (constraints.maxWidth - tick.width - gap).coerceAtLeast(0)
            val effort = columns[columns.lastIndex - 1].single().measure(loose.copy(maxWidth = contentWidth))
            val load = if (showResistance) columns.first().single().measure(
                loose.copy(maxWidth = contentWidth),
            ) else null
            val measurementWidth = contentWidth - effort.width -
                (load?.width ?: 0) - gap * (columns.size - 2)
            // Wrap logical groups rather than constrain their fixed controls.
            val wrapped = measurementWidth < minimumMeasurementWidth.roundToPx()
            val measurements = columns[if (showResistance) 1 else 0].single().measure(
                loose.copy(
                    maxWidth = if (wrapped) contentWidth else measurementWidth,
                ),
            )
            val rows = mutableListOf<MutableList<Placeable>>()
            listOfNotNull(load, measurements, effort).forEach { field ->
                val row = rows.lastOrNull()
                if (row == null || row.sumOf { it.width } + gap * row.size + field.width > contentWidth) {
                    rows += mutableListOf(field)
                } else row += field
            }
            val centers = rows.map { row -> maxOf(rowHeight.roundToPx() / 2, row.maxOf { it.boxCenter() }) }
            val heights = rows.mapIndexed { index, row ->
                centers[index] + maxOf(rowHeight.roundToPx() / 2, row.maxOf { it.height - it.boxCenter() })
            }
            val offsets = heights.runningFold(0) { y, height -> y + height + rowGap }
            val contentHeight = heights.sum() + rowGap * (rows.size - 1)
            val center = maxOf(tick.boxCenter(), if (rows.size == 1) centers.single() else contentHeight / 2)
            val height = maxOf(contentHeight, center + tick.height - tick.boxCenter())
            val slack = if (rows.size == 1) contentWidth - rows.single().sumOf { it.width } -
                gap * (rows.single().size - 1) else gap
            // Balance painted check edges, not its transparent icon frame, on nearly full rows.
            val checkInset = 3.dp.roundToPx()
            val opticalGap = if (slack in 0 until gap) (slack + checkInset * 2) / 3 else 0
            val tickOffset = if (opticalGap > 0) checkInset - opticalGap else 0
            layout(constraints.maxWidth, height, mapOf(SetBoxCenter to center)) {
                var dividerIndex = 0
                rows.forEachIndexed { index, row ->
                    var x = 0
                    row.forEachIndexed { fieldIndex, field ->
                        if (field === effort) x += opticalGap
                        if (fieldIndex > 0) {
                            val divider = dividers[dividerIndex++]
                            divider.placeRelative(
                                x - (gap + divider.width) / 2,
                                offsets[index] + centers[index] - divider.height / 2,
                            )
                        }
                        field.placeRelative(x, offsets[index] + centers[index] - field.boxCenter())
                        x += field.width + gap
                    }
                }
                tick.placeRelative(constraints.maxWidth - tick.width + tickOffset, center - tick.boxCenter())
            }
        }
        feedback()
    }
}

@Composable
internal fun ResistanceRenderer(
    metric: ResistanceMetric,
    config: ExerciseInputConfig,
    draft: WorkoutEntryDraftEntity,
    weight: String,
    weightUnit: String,
    bands: List<BandEntity>,
    selectedBands: List<String>,
    locked: Boolean,
    enabled: Boolean,
    identity: String,
    onWeightChange: (String) -> Unit,
    onBandsChange: (List<String>) -> Unit,
) {
    when (metric) {
        ResistanceMetric.BODYWEIGHT -> Unit
        ResistanceMetric.KILOGRAMS -> {
            val added = config.addedWeight
            val unit = if (added) weightUnit else config.weightUnit ?: "kg"
            MetricValue(
                value = if (added) draft.addedWeight else weight,
                label = if (added) "+" else "",
                unit = unit,
                description = "${if (added) "Added weight" else "Resistance"} for $identity",
                locked = locked,
                enabled = enabled,
                width = 60,
                onChange = onWeightChange,
            )
        }
        ResistanceMetric.BANDS -> {
            if (bands.isEmpty()) return
            val width = 24.dp
            val height = timingCellHeight()
            val columns = if (LocalLargeSetGroup.current) bands.size else minOf(4, bands.size)
            val twoRows = bands.size > columns
            val groupHeight = maxOf(height * if (twoRows) 2f else 1f,
                setSingleRowHeight() * if (twoRows) 1.5f else 1f)
            Column(
                Modifier.width(width * columns).height(groupHeight)
                    .semantics { contentDescription = "Resistance grid for $identity" },
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                bands.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { band ->
                            if (locked && band.id !in selectedBands) Spacer(Modifier.size(width, height))
                            else BandDot(
                                band, band.id in selectedBands, enabled && !locked,
                                "${band.name} band for $identity", width, height,
                            ) {
                                onBandsChange(if (band.id in selectedBands) selectedBands - band.id else selectedBands + band.id)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MeasurementRenderer(
    metric: MeasurementMetric,
    config: ExerciseInputConfig,
    draft: WorkoutEntryDraftEntity,
    reps: String,
    attempts: List<String>,
    locked: Boolean,
    enabled: Boolean,
    identity: String,
    focus: Modifier,
    onRepsChange: (String) -> Unit,
    onDraftChange: (WorkoutEntryDraftEntity) -> Unit,
    onTimingDelete: (WorkoutEntryDraftEntity) -> Unit = onDraftChange,
) {
    when (metric) {
        MeasurementMetric.REPETITIONS -> MetricValue(
            reps, "", "Reps for $identity",
            locked, enabled, modifier = focus, unit = "Reps", onChange = onRepsChange,
        )
        MeasurementMetric.RANGE_OF_MOTION -> MetricValue(
            draft.romValue, "",
            "Range of motion for $identity",
            locked, enabled, width = 60, modifier = focus,
            unit = if (draft.romUnit == "degrees") "°" else draft.romUnit,
            onChange = { onDraftChange(draft.copy(romValue = it)) },
        )
        MeasurementMetric.HOLD -> {
            val totalOnly = !config.timeHeld && config.timeUnderTension
            val retainedAttempts = !totalOnly && attempts.size > 1
            val held = if (retainedAttempts) attempts.map { quantitativeInput(it) }
                .takeIf { values -> values.all { it.isValid } }
                ?.let { values -> formatQuantitative(values.sumOf { it.value ?: 0.0 }) }.orEmpty()
                else attempts.first()
            MetricValue(
                if (totalOnly) draft.timeUnderTension else held,
                if (totalOnly) "Total" else "",
                "${if (totalOnly) "Total Time" else "Time Under Tension"} for $identity",
                locked || retainedAttempts, enabled, modifier = focus, unit = "s",
                width = 64,
                compact = !totalOnly,
                minimumHeight = timingCellHeight(),
                onChange = {
                    onDraftChange(if (totalOnly) draft.copy(timeUnderTension = it)
                        else draft.copy(
                            tutEntries = encodeTimingInputs(attempts.toMutableList().apply { set(0, it) }),
                            timeHeld = "",
                        ))
                },
            )
        }
        MeasurementMetric.SKILL_HOLD -> BoxWithConstraints {
            val rowHeight = setSingleRowHeight()
            val focusManager = LocalFocusManager.current
            // Hidden empty slots keep completion from repacking the tile.
            val layoutAttempts = visibleTimingInputs(attempts, locked = false)
            val values = attempts.filter(String::isNotEmpty).map { quantitativeInput(it) }
            val sum = if (values.all { it.isValid } && values.any { it.value != null })
                "${formatQuantitative(values.sumOf { it.value ?: 0.0 })} s" else null
            val compactWidth = timingCellWidth()
            val large = LocalLargeSetGroup.current
            // Reserve four timing cells, the inline sum and Total, plus rounding clearance.
            val cellWidth = if (large) {
                minOf(
                    48.dp * maxOf(1f, LocalDensity.current.fontScale),
                    maxOf(compactWidth, (maxWidth - TimingBoxGap * 5 - 1.dp) / 6),
                )
            } else compactWidth
            val cellHeight = timingCellHeight()
            Row(horizontalArrangement = Arrangement.spacedBy(TimingBoxGap), verticalAlignment = Alignment.CenterVertically) {
                BoxWithConstraints(Modifier.weight(1f, fill = false).alignBy(SetBoxCenter)) {
                    val slots: List<@Composable () -> Unit> = layoutAttempts.indices.map { index ->
                        {
                            val canDelete = !locked && enabled && layoutAttempts[index].isNotEmpty()
                            var showDelete by remember(draft.setId, index, layoutAttempts[index], canDelete) {
                                mutableStateOf(false)
                            }
                            val deletionProvider = object : TextContextMenuProvider {
                                override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
                                    focusManager.clearFocus()
                                    showDelete = true
                                }
                            }
                            val toolbar = LocalTextContextMenuToolbarProvider.current
                            val dropdown = LocalTextContextMenuDropdownProvider.current
                            val deleteModifier = if (canDelete) Modifier.semantics {
                                customActions = listOf(CustomAccessibilityAction("Show delete time") {
                                    focusManager.clearFocus()
                                    showDelete = true
                                    true
                                })
                            } else Modifier
                            Box {
                                CompositionLocalProvider(
                                    LocalTextContextMenuToolbarProvider provides if (canDelete) deletionProvider else toolbar,
                                    LocalTextContextMenuDropdownProvider provides if (canDelete) deletionProvider else dropdown,
                                ) {
                                    MetricValue(
                                        layoutAttempts[index], "",
                                        if (index == 0 && attempts.size == 1) "Time Under Tension for $identity"
                                            else "TUT attempt ${index + 1} for $identity",
                                        locked, enabled,
                                        minimumHeight = cellHeight, compact = true, unit = "s",
                                        widthModifier = Modifier.width(cellWidth),
                                        modifier = (if (index == 0) focus else Modifier).then(deleteModifier),
                                        onChange = { value ->
                                            val next = attempts.toMutableList()
                                            if (index == next.size) next.add(value) else next[index] = value
                                            onDraftChange(draft.copy(tutEntries = encodeTimingInputs(next), timeHeld = ""))
                                        },
                                    )
                                }
                                if (showDelete && canDelete) TimingDeletePopup(
                                    label = "Delete time ${index + 1} for $identity",
                                    onDismiss = { showDelete = false },
                                    onDelete = {
                                        showDelete = false
                                        onTimingDelete(draft.copy(
                                            tutEntries = encodeTimingInputs(attempts.toMutableList().apply { removeAt(index) }),
                                            timeHeld = "",
                                        ))
                                    },
                                )
                            }
                        }
                    }
                    Layout(
                        contents = slots,
                        modifier = Modifier.semantics {
                            contentDescription = "Timing grid for $identity"
                        },
                    ) { nodes, constraints ->
                        val gap = TimingBoxGap.roundToPx()
                        val cells = nodes.map {
                            it.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
                        }
                        val rows = mutableListOf<MutableList<Int>>()
                        cells.indices.forEach { index ->
                            val row = rows.lastOrNull()
                            val width = row?.sumOf { cells[it].width } ?: 0
                            if (row == null || row.size == 4 ||
                                width + gap * row.size + cells[index].width > constraints.maxWidth) {
                                rows += mutableListOf(index)
                            } else row += index
                        }
                        val heights = rows.map { row -> row.maxOf { cells[it].height } }
                        val rowGap = TimingBoxGap.roundToPx()
                        val packedHeight = heights.sum() + rowGap * (rows.size - 1)
                        val height = maxOf(cellHeight.roundToPx() * 2 + rowGap, packedHeight)
                        val width = rows.maxOf { row -> row.sumOf { cells[it].width } + gap * (row.size - 1) }
                        val offsets = heights.runningFold((height - packedHeight) / 2) { y, h -> y + h + rowGap }
                        val centers = rows.flatMapIndexed { index, row ->
                            row.filter { cells[it].height > 0 }.map { offsets[index] + cells[it].boxCenter() }
                        }
                        val center = if (centers.isEmpty()) height / 2 else (centers.min() + centers.max()) / 2
                        layout(width, height, mapOf(SetBoxCenter to center)) {
                            rows.forEachIndexed { rowIndex, row ->
                                var x = 0
                                row.forEach { index ->
                                    cells[index].placeRelative(x, offsets[rowIndex])
                                    x += cells[index].width + gap
                                }
                            }
                        }
                    }
                }
                Column(
                    Modifier.heightIn(min = cellHeight).withBoxCenter(rowHeight).alignBy(SetBoxCenter)
                        .semantics { contentDescription = "Timing total for $identity" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val summaryStyle = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp, lineHeight = 12.sp, fontFeatureSettings = "tnum",
                    )
                    val stacked = !large && layoutAttempts.size >= 4
                    val inline = sum != null && !stacked
                    val accumulatedValue: @Composable (Modifier) -> Unit = { modifier ->
                        if (sum != null) Text(
                            if (locked && draft.timeUnderTension.isBlank()) sum else "$sum /",
                            style = summaryStyle, maxLines = 1, softWrap = false,
                            modifier = modifier.horizontalScroll(rememberScrollState())
                                .semantics { contentDescription = "TUT total for $identity" },
                        )
                    }
                    Text(
                        "Total", style = summaryStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, softWrap = false,
                        modifier = Modifier.height(rowHeight / 4)
                            .width(cellWidth)
                            .align(Alignment.Start).wrapContentWidth(Alignment.CenterHorizontally)
                            .semantics { contentDescription = "Timing total label for $identity" },
                    )
                    if (sum != null && stacked) accumulatedValue(Modifier.height(rowHeight / 4))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(TimingBoxGap),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (inline) Box(
                            Modifier.width(cellWidth).height(cellHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            accumulatedValue(Modifier)
                        }
                        MetricValue(
                            draft.timeUnderTension, "", "Total Time for $identity", locked, enabled,
                            widthModifier = Modifier.width(cellWidth),
                            minimumHeight = cellHeight, compact = true, unit = "s", secondary = true,
                            onChange = { onDraftChange(draft.copy(timeUnderTension = it)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimingDeletePopup(label: String, onDismiss: () -> Unit, onDelete: () -> Unit) {
    Popup(
        alignment = Alignment.TopEnd,
        offset = with(LocalDensity.current) { IntOffset(8.dp.roundToPx(), -8.dp.roundToPx()) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Surface(
                    modifier = Modifier.size(16.dp),
                    shape = CircleShape,
                    color = LocalVibePalette.current.surfaceFloating.copy(alpha = 0.94f),
                    contentColor = LocalVibePalette.current.textSecondary,
                    border = BorderStroke(1.dp, LocalVibePalette.current.border.copy(alpha = 0.5f)),
                    shadowElevation = 2.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Close, label, Modifier.size(32.dp / 3f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun MetricValue(
    value: String,
    label: String,
    description: String,
    locked: Boolean,
    enabled: Boolean,
    width: Int = 64,
    widthModifier: Modifier? = null,
    modifier: Modifier = Modifier,
    minimumHeight: Dp = timingCellHeight(),
    compact: Boolean = false,
    compactText: Boolean = compact,
    maximum: Double = 1_000_000_000.0,
    secondary: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    unit: String = "",
    showInlineError: Boolean = true,
    onNext: (() -> Unit)? = null,
    onChange: (String) -> Unit,
) {
    val labelHeight = if (label.isBlank()) 0.dp else with(LocalDensity.current) {
        MaterialTheme.typography.labelSmall.lineHeight.toDp()
    }
    Column((widthModifier ?: Modifier.width(metricControlWidth(width))).withBoxCenter(minimumHeight + labelHeight * 2)) {
        if (label.isNotBlank()) Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = "Label for $description" },
        )
        if (locked) {
            Box(Modifier.fillMaxWidth().heightIn(min = minimumHeight)) {
                if (value.isNotBlank()) {
                    MetricValueContent(
                        value = value, label = "", unit = unit, compact = compact,
                        compactText = compactText,
                        minimumHeight = minimumHeight,
                        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                            contentDescription = description
                            stateDescription = listOf(label, value, unit).filter(String::isNotBlank).joinToString(" ")
                        },
                    ) {
                        Text(value, style = metricNumberStyle(compactText), maxLines = 1, softWrap = false,
                            modifier = Modifier.horizontalScroll(rememberScrollState()))
                    }
                }
            }
        } else QuantitativeTextField(
            value, onChange, "", description, modifier.fillMaxWidth(),
            enabled = enabled, maximum = maximum, trailingIcon = trailing, onNext = onNext, secondary = secondary,
            minimumHeight = minimumHeight, compact = compact,
            compactText = compactText,
            unit = unit, showInlineError = showInlineError,
        )
    }
}

@Composable
internal fun metricNumberStyle(compact: Boolean): TextStyle =
    (if (compact) MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
    else MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp)).copy(
        fontWeight = FontWeight.Medium, letterSpacing = 0.sp, fontFeatureSettings = "tnum",
    )

@Composable
internal fun metricControlWidth(width: Int): Dp = width.dp * maxOf(1f, LocalDensity.current.fontScale)

@Composable
internal fun skillHoldMinimumWidth(): Dp =
    timingCellWidth() * 4 + TimingBoxGap * 3 + 1.dp

@Composable
internal fun measurementMinimumWidth(metric: MeasurementMetric?): Dp = when (metric) {
    MeasurementMetric.SKILL_HOLD -> skillHoldMinimumWidth()
    MeasurementMetric.REPETITIONS -> metricControlWidth(64)
    MeasurementMetric.RANGE_OF_MOTION -> metricControlWidth(60)
    MeasurementMetric.HOLD -> metricControlWidth(64)
    null -> metricControlWidth(64)
}

@Composable
internal fun MetricValueContent(
    value: String,
    label: String,
    unit: String,
    compact: Boolean,
    compactText: Boolean = compact,
    minimumHeight: Dp,
    editable: Boolean = false,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    number: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val cursorWidth = if (editable) 2.dp else 0.dp
    val numberWidth = if (value.isEmpty()) cursorWidth else with(density) {
        measurer.measure(value, metricNumberStyle(compactText), softWrap = false).size.width.toDp()
    } + cursorWidth
    val labelStyle = MaterialTheme.typography.labelSmall.let {
        it.copy(fontSize = if (compactText) 9.sp else 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
    }
    Column(
        modifier.height(minimumHeight)
            .padding(horizontal = if (compact) 2.dp else 4.dp,
                vertical = if (compact || minimumHeight < setSingleRowHeight()) 2.dp else 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (label.isNotBlank() || trailing != null) Row(
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (label.isNotBlank()) Text(
                label, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false), maxLines = 1,
            )
            trailing?.invoke()
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (value.isEmpty()) 0.dp else 2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.weight(1f, fill = false)
                    .width(numberWidth)
                    .alignByBaseline(),
                contentAlignment = Alignment.Center,
            ) { number() }
            if (unit.isNotBlank()) Text(unit, style = labelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alignByBaseline())
            if (value.isEmpty() && unit.isNotBlank()) Spacer(Modifier.width(cursorWidth))
        }
    }
}

@Composable
private fun BandDot(
    band: BandEntity, selected: Boolean, enabled: Boolean, description: String,
    width: Dp, height: Dp, onClick: () -> Unit,
) {
    val colour = Color(band.colourArgb)
    Box(
        Modifier.width(width).height(height).clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .semantics {
                contentDescription = description
                this.selected = selected
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        contentAlignment = Alignment.Center,
    ) {
        BandColourDot(colour, diameter = minOf(22.dp, (width - 2.dp).coerceAtLeast(0.dp)), selected = selected)
    }
}

@Composable
internal fun BandColourDot(colour: Color, modifier: Modifier = Modifier, diameter: Dp = 22.dp, selected: Boolean = false) {
    Box(
        modifier.size(diameter)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
            .padding(3.dp)
            .background(colour, CircleShape),
    )
}

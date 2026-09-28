package com.petermathie.vibecheck.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.data.DemoRemovalSummary
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.VibePalette
import com.petermathie.vibecheck.ui.theme.VibePalettePreset
import com.petermathie.vibecheck.ui.theme.VibePalettes
import com.petermathie.vibecheck.ui.theme.VibeShapes
import com.petermathie.vibecheck.ui.theme.VibeThemeMode
import com.petermathie.vibecheck.ui.theme.VibeVisualStyle
import com.petermathie.vibecheck.ui.theme.LocalVibeStyleTokens
import com.petermathie.vibecheck.ui.theme.LocalVibeDashboardTypography
import com.petermathie.vibecheck.ui.theme.VibeSurfaceLevel
import com.petermathie.vibecheck.ui.theme.VibeSurfaceState
import com.petermathie.vibecheck.ui.components.VibeSurface

@Composable
internal fun StyleScreen(
    visualStyle: VibeVisualStyle,
    onVisualStyleChange: (VibeVisualStyle) -> Unit,
    selectedId: String,
    onSelect: (String) -> Unit,
    themeMode: VibeThemeMode,
    onThemeModeChange: (VibeThemeMode) -> Unit,
    onRemoveDemo: ((Result<DemoRemovalSummary>) -> Unit) -> Unit,
) {
    var confirmRemoveDemo by remember { mutableStateOf(false) }
    var demoMessage by remember { mutableStateOf<String?>(null) }
    ScreenList {
        item {
            Text("Style", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Visual style, colour palette and light or dark appearance are independent.",
                color = LocalVibePalette.current.textSecondary,
            )
            Text("Visual style", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                VibeVisualStyle.entries.forEachIndexed { index, style ->
                    SegmentedButton(
                        selected = visualStyle == style,
                        onClick = { onVisualStyleChange(style) },
                        shape = SegmentedButtonDefaults.itemShape(index, VibeVisualStyle.entries.size),
                        label = { Text(style.displayName) },
                    )
                }
            }
            VisualStylePreview(visualStyle)
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                VibeThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, VibeThemeMode.entries.size),
                        label = { Text(mode.displayName) },
                    )
                }
            }
        }
        items(VibePalettes.presets, key = { it.id }) { preset ->
            PaletteCard(preset, selectedId == preset.id) { onSelect(preset.id) }
        }
        item {
            VibeCard {
                Text("Development data", style = MaterialTheme.typography.titleLarge)
                Text("Remove all fake personal history while keeping the complete exercise catalogue and your own data.", color = LocalVibePalette.current.textSecondary)
                OutlinedButton(
                    onClick = { confirmRemoveDemo = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) { Text("Remove demo data") }
                demoMessage?.let { Text(it, color = LocalVibePalette.current.textSecondary) }
            }
        }
    }
    if (confirmRemoveDemo) AlertDialog(
        onDismissRequest = { confirmRemoveDemo = false },
        title = { Text("Remove all demo personal data?") },
        text = { Text("Demo workouts, programmes, habits, body history and generated photos will be removed. Catalogue definitions and your own records will remain.") },
        confirmButton = {
            TextButton(onClick = {
                confirmRemoveDemo = false
                onRemoveDemo { result ->
                    demoMessage = result.fold(
                        onSuccess = { "Demo personal data removed. Exercise catalogue preserved." },
                        onFailure = { "Demo data was not fully removed: ${it.message.orEmpty()}" },
                    )
                }
            }) { Text("Remove demo data") }
        },
        dismissButton = { TextButton(onClick = { confirmRemoveDemo = false }) { Text("Cancel") } },
    )
}

@Composable
private fun VisualStylePreview(style: VibeVisualStyle) {
    val palette = LocalVibePalette.current
    val tokens = LocalVibeStyleTokens.current
    val typography = LocalVibeDashboardTypography.current
    VibeSurface(
        level = VibeSurfaceLevel.INSET,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "${style.displayName} visual style preview"
            },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "LIVE PREVIEW",
                style = typography.microLabel,
                color = if (tokens.showInstrumentDetails) tokens.instrumentSignal else palette.textSecondary,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("72.4 kg", style = typography.metricCompact, color = palette.accent)
                Text(
                    "  SAMPLE PROGRESS",
                    modifier = Modifier.weight(1f),
                    style = typography.annotation,
                    color = palette.textSecondary,
                )
            }
            Canvas(Modifier.fillMaxWidth().height(72.dp)) {
                val grid = if (tokens.showInstrumentDetails) tokens.instrumentSignal else palette.textPrimary
                val gridAlpha = if (tokens.showInstrumentDetails) 0.18f else 0.08f
                repeat(5) { row ->
                    val y = size.height * row / 4f
                    drawLine(grid.copy(alpha = gridAlpha), Offset(0f, y), Offset(size.width, y), 1f)
                }
                repeat(9) { column ->
                    val x = size.width * column / 8f
                    drawLine(grid.copy(alpha = gridAlpha), Offset(x, 0f), Offset(x, size.height), 1f)
                }
                val trace = Path().apply {
                    moveTo(0f, size.height * 0.72f)
                    lineTo(size.width * 0.22f, size.height * 0.58f)
                    lineTo(size.width * 0.45f, size.height * 0.66f)
                    lineTo(size.width * 0.7f, size.height * 0.28f)
                    lineTo(size.width, size.height * 0.38f)
                }
                if (tokens.showInstrumentDetails) {
                    drawPath(trace, palette.accent.copy(alpha = tokens.graphGlowAlpha), style = Stroke(7.dp.toPx()))
                }
                drawPath(trace, palette.accent, style = Stroke(2.dp.toPx()))
                val marker = Offset(size.width * 0.7f, size.height * 0.28f)
                drawLine(palette.accent, marker.copy(x = marker.x - 5.dp.toPx()), marker.copy(x = marker.x + 5.dp.toPx()), 1.5.dp.toPx())
                drawLine(palette.accent, marker.copy(y = marker.y - 5.dp.toPx()), marker.copy(y = marker.y + 5.dp.toPx()), 1.5.dp.toPx())
                val bodyX = size.width * 0.88f
                drawCircle(palette.diagramLine, 4.dp.toPx(), Offset(bodyX, 11.dp.toPx()), style = Stroke(1.dp.toPx()))
                drawLine(palette.diagramLine, Offset(bodyX, 16.dp.toPx()), Offset(bodyX, 49.dp.toPx()), 1.dp.toPx())
                drawLine(palette.diagramLine, Offset(bodyX, 23.dp.toPx()), Offset(bodyX - 8.dp.toPx(), 38.dp.toPx()), 1.dp.toPx())
                drawLine(palette.diagramLine, Offset(bodyX, 23.dp.toPx()), Offset(bodyX + 8.dp.toPx(), 38.dp.toPx()), 1.dp.toPx())
                drawLine(palette.diagramLine, Offset(bodyX, 49.dp.toPx()), Offset(bodyX - 7.dp.toPx(), 67.dp.toPx()), 1.dp.toPx())
                drawLine(palette.diagramLine, Offset(bodyX, 49.dp.toPx()), Offset(bodyX + 7.dp.toPx(), 67.dp.toPx()), 1.dp.toPx())
            }
        }
    }
}

@Composable
private fun PaletteCard(preset: VibePalettePreset, selected: Boolean, onSelect: () -> Unit) {
    val darkPreview = LocalVibePalette.current.isDark
    fun swatches(palette: VibePalette) = listOf(
        palette.background,
        palette.surface,
        palette.accent,
        palette.secondary,
        palette.tertiary,
        palette.danger,
    )
    VibeSurface(
        level = if (selected) VibeSurfaceLevel.SELECTED else VibeSurfaceLevel.RAISED,
        state = if (selected) VibeSurfaceState.SELECTED else VibeSurfaceState.RESTING,
        onClick = onSelect,
        modifier = Modifier.semantics {
            contentDescription =
                "${preset.displayName} palette, ${if (darkPreview) "dark" else "light"} preview" +
                    if (selected) ", selected" else ""
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(preset.displayName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (selected) Icon(Icons.Outlined.Check, "Selected")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val preview = if (darkPreview) preset.dark else preset.light
                swatches(preview).forEach { color ->
                    Box(Modifier.size(18.dp).background(color, RoundedCornerShape(50)))
                }
            }
        }
    }
}

package com.petermathie.vibecheck.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.VibePalette
import com.petermathie.vibecheck.ui.theme.VibePalettePreset
import com.petermathie.vibecheck.ui.theme.VibePalettes
import com.petermathie.vibecheck.ui.theme.VibeThemeMode
import com.petermathie.vibecheck.ui.theme.VibeSurfaceLevel
import com.petermathie.vibecheck.ui.theme.VibeSurfaceState
import com.petermathie.vibecheck.ui.components.VibeSurface

@Composable
internal fun StyleScreen(
    selectedId: String,
    onSelect: (String) -> Unit,
    themeMode: VibeThemeMode,
    onThemeModeChange: (VibeThemeMode) -> Unit,
    onBack: () -> Unit = {},
) {
    BackHandler(onBack = onBack)
    ScreenList {
        item {
            MoreChildTitleRow("Style", onBack, backDescription = "Back to Settings")
            Text(
                "Choose one curated palette. Interface, charts and Freshness colours update together.",
                color = LocalVibePalette.current.textSecondary,
            )
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

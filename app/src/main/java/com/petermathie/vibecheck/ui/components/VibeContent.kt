package com.petermathie.vibecheck.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.LocalVibeDashboardTypography
import com.petermathie.vibecheck.ui.theme.VibeShapes
import com.petermathie.vibecheck.ui.theme.VibeSpacing

@Composable
fun VibeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(VibeSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        action?.invoke()
    }
}

@Composable
fun VibeDivider(modifier: Modifier = Modifier) {
    val palette = LocalVibePalette.current
    androidx.compose.material3.HorizontalDivider(modifier, thickness = 1.dp, color = palette.textPrimary.copy(alpha = if (palette.isDark) 0.10f else 0.12f))
}

@Composable
fun VibeStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    colour: Color = LocalVibePalette.current.accent,
) {
    val typography = LocalVibeDashboardTypography.current
    Text(
        text,
        modifier
            .background(colour.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = VibeSpacing.compact, vertical = VibeSpacing.xSmall),
        color = colour,
        style = typography.label,
    )
}

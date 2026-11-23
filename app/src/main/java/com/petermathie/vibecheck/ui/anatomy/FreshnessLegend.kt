package com.petermathie.vibecheck.ui.anatomy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import com.petermathie.vibecheck.ui.theme.freshnessColors

@Composable
fun FreshnessLegend(
    modifier: Modifier = Modifier,
    includeNoData: Boolean = true,
    horizontal: Boolean = false,
) {
    val palette = LocalVibePalette.current
    val colors = palette.freshnessColors()
    val shape = RoundedCornerShape(50)
    if (horizontal) {
        val ageStops = if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
            colors.ageStops.reversed()
        } else {
            colors.ageStops
        }
        Column(
            modifier.clearAndSetSemantics {
                contentDescription =
                    "Freshness colour scale. Most recent under 24 hours at the start; " +
                        "24 to 48 hours; 48 to 72 hours; 3 to 7 days; least recent over 7 days at the end. " +
                        "No data is separate."
            },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                Modifier.fillMaxWidth().height(4.dp)
                    .background(Brush.horizontalGradient(ageStops), shape),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Recent", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                Text("Older", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
            }
            if (includeNoData) FreshnessNoDataKey()
        }
        return
    }
    Column(
        modifier = modifier
            .widthIn(min = 62.dp, max = 74.dp)
            .fillMaxHeight()
            .clearAndSetSemantics {
                contentDescription =
                    "Freshness colour scale. Most recent under 24 hours at the top; " +
                        "24 to 48 hours; 48 to 72 hours; 3 to 7 days; least recent over 7 days at the bottom. " +
                        "No data is separate."
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Most\nrecent", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        Box(
            Modifier
                .weight(1f)
                .width(18.dp)
                .background(
                    Brush.verticalGradient(
                        colors.ageStops,
                    ),
                    shape,
                )
                .border(1.dp, palette.border, shape),
        )
        Text("Least\nrecent", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        if (includeNoData) FreshnessNoDataKey()
    }
}

@Composable
fun FreshnessNoDataKey(modifier: Modifier = Modifier) {
    val colors = LocalVibePalette.current.freshnessColors()
    Row(
        modifier = modifier.padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.width(14.dp).height(10.dp).background(colors.noData, RoundedCornerShape(50)))
        Text("No data", style = MaterialTheme.typography.labelSmall)
    }
}

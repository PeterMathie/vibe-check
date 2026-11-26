package com.petermathie.vibecheck.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.ui.formatDisplayDate
import com.petermathie.vibecheck.ui.MetricInformationButton
import com.petermathie.vibecheck.ui.theme.LocalVibePalette
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun DateValueReadout(
    date: LocalDate?,
    value: String,
    modifier: Modifier = Modifier,
    dateModifier: Modifier = Modifier,
    valueModifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    valueColor: Color = LocalVibePalette.current.textSecondary,
    dateLabel: String? = null,
    onValueHelp: (() -> Unit)? = null,
    compactValue: String? = null,
    valueHelpDescription: String = "What are points?",
) {
    val palette = LocalVibePalette.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val dateStyle = style.copy(fontFamily = FontFamily.Monospace)
    val locale = Locale.getDefault()
    val helpSpace = if (onValueHelp != null) 12.dp else 0.dp
    val helpWidth = if (onValueHelp != null) 16.dp else 0.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val valueSpace = compactValue?.let {
            with(density) { measurer.measure(it, style).size.width.toDp() + maxOf(8.dp, helpWidth) }.coerceAtMost(maxWidth / 2)
        } ?: (maxWidth / 2)
        val dateWidth = with(density) { (maxWidth - valueSpace - 12.dp).roundToPx().coerceAtLeast(1) }
        val maximumValueWidth = maxWidth / 2
        val year = date?.year ?: LocalDate.now().year
        val dateHeight = remember(measurer, dateWidth, year, dateStyle, locale, density) {
            (1..12).maxOf { month ->
                measurer.measure(
                    formatDisplayDate(LocalDate.of(year, month, 28), locale),
                    dateStyle,
                    constraints = Constraints(maxWidth = dateWidth),
                ).size.height
            }
        }
        val height = with(density) {
            maxOf(dateHeight.toDp(), style.lineHeight.toDp() * if (compactValue == null) 2 else 1) + helpSpace +
                if (dateLabel != null) MaterialTheme.typography.labelSmall.lineHeight.toDp() else 0.dp
        }
        Box(Modifier.fillMaxWidth().height(height)) {
            if (date != null || value.isNotEmpty()) {
                Column(Modifier.fillMaxSize()) {
                    if (dateLabel != null) Text(dateLabel, style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            date?.let { formatDisplayDate(it, locale) }.orEmpty(),
                            dateModifier.weight(1f).fillMaxHeight().padding(top = helpSpace),
                            style = dateStyle, color = palette.textSecondary, textAlign = TextAlign.Start,
                        )
                        Box(Modifier.widthIn(max = maximumValueWidth).fillMaxHeight()) {
                            Text(
                                value, valueModifier.fillMaxHeight().padding(top = helpSpace, end = helpWidth)
                                    .verticalScroll(rememberScrollState()),
                                style = style, color = valueColor, textAlign = TextAlign.End,
                            )
                            onValueHelp?.let { help ->
                                MetricInformationButton(
                                    valueHelpDescription, palette.background, help,
                                    Modifier.align(androidx.compose.ui.Alignment.TopEnd),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

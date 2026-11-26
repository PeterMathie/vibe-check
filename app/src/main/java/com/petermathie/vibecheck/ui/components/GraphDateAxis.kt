package com.petermathie.vibecheck.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.ui.theme.VibeDashboardTypography
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

internal fun progressDateRange(months: Int, today: LocalDate, zone: ZoneId): LongRange {
    require(months > 0)
    return progressDateRange(today.minusMonths(months.toLong()), today, zone)
}

internal fun progressDateRange(start: LocalDate, end: LocalDate, zone: ZoneId): LongRange {
    require(start <= end)
    return start.atStartOfDay(zone).toInstant().toEpochMilli()..
        (end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1)
}

internal fun graphDateX(date: Long, range: LongRange, width: Float, padding: Float): Float {
    require(range.first <= range.last)
    val fraction = if (range.first == range.last) 0.5 else {
        ((date.toDouble() - range.first) / (range.last.toDouble() - range.first)).coerceIn(0.0, 1.0)
    }
    return padding + fraction.toFloat() * (width - 2 * padding).coerceAtLeast(1f)
}

internal fun graphDateAtX(x: Float, range: LongRange, width: Float, padding: Float): Long {
    require(range.first <= range.last)
    val fraction = ((x - padding) / (width - 2 * padding).coerceAtLeast(1f)).coerceIn(0f, 1f)
    return (range.first + fraction.toDouble() * (range.last.toDouble() - range.first))
        .roundToLong().coerceIn(range)
}

internal fun graphDateTicks(start: LocalDate, end: LocalDate, maximum: Int): List<LocalDate> {
    require(start <= end && maximum >= 2)
    val span = ChronoUnit.DAYS.between(start, end)
    val steps = if (span <= 45) listOf(0 to 7, 0 to 14, 1 to 0, 2 to 0, 3 to 0, 6 to 0, 12 to 0)
        else listOf(1 to 0, 2 to 0, 3 to 0, 6 to 0, 12 to 0)
    for ((months, days) in steps) {
        var tick = if (months > 0) {
            start.withDayOfMonth(1).withMonth(((start.monthValue - 1) / months) * months + 1)
        } else start.minusDays((start.dayOfWeek.value - 1).toLong())
        while (tick < start) tick = if (months > 0) tick.plusMonths(months.toLong()) else tick.plusDays(days.toLong())
        val ticks = buildList {
            while (tick <= end) {
                add(tick)
                tick = if (months > 0) tick.plusMonths(months.toLong()) else tick.plusDays(days.toLong())
            }
        }
        if (ticks.size in 2..maximum) return ticks
    }
    return listOf(start, end).distinct()
}

@Composable
internal fun GraphDateAxis(range: LongRange, padding: Float) {
    val density = LocalDensity.current
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(range.first).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(range.last).atZone(zone).toLocalDate()
    val formatter = DateTimeFormatter.ofPattern(
        if (ChronoUnit.DAYS.between(start, end) > 90) "MMM yyyy" else "d MMM",
        Locale.getDefault(),
    )
    val style = VibeDashboardTypography.annotation
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(with(density) { style.lineHeight.toDp() } + 4.dp)
            .semantics { contentDescription = "Graph date axis" },
    ) {
        val width = with(density) { maxWidth.toPx() }
        val maximumLabelWidth = (0..12).maxOf { offset ->
            measurer.measure(start.plusMonths(offset.toLong()).withDayOfMonth(28).format(formatter), style).size.width
        }
        val maximum = ((width - padding * 2) / (maximumLabelWidth + with(density) { 12.dp.toPx() }))
            .toInt().coerceAtLeast(2)
        graphDateTicks(start, end, maximum).forEach { date ->
            val text = date.format(formatter)
            val textWidth = measurer.measure(text, style).size.width.toFloat()
            val x = graphDateX(date.atStartOfDay(zone).toInstant().toEpochMilli(), range, width, padding)
            val left = (x - textWidth / 2).coerceIn(0f, (width - textWidth).coerceAtLeast(0f))
            Text(text, Modifier.offset(x = with(density) { left.toDp() }), style = style, maxLines = 1)
        }
    }
}

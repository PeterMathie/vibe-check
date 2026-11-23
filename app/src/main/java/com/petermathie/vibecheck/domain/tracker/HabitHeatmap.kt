package com.petermathie.vibecheck.domain.tracker

import com.petermathie.vibecheck.data.local.TrackerDailyValueEntity
import com.petermathie.vibecheck.data.local.TrackerEntity
import com.petermathie.vibecheck.data.local.TrackerFieldEntity
import java.time.LocalDate
import java.time.YearMonth

internal val numericHabitTypes = setOf("NUMBER", "COUNT", "DURATION", "RATING")

internal fun habitNumericError(type: String, value: Double?): String? = when {
    value == null || !value.isFinite() || value < 0.0 -> "Enter a number greater than or equal to zero"
    type == "COUNT" && value % 1.0 != 0.0 -> "Enter a whole number greater than or equal to zero"
    else -> null
}

internal fun habitMeasurement(fields: List<TrackerFieldEntity>): TrackerFieldEntity? =
    fields.filterNot { it.isArchived }.minByOrNull { it.position }

internal fun firstHabitMonth(
    fieldId: String?,
    values: List<TrackerDailyValueEntity>,
    today: LocalDate,
): YearMonth {
    val first = values.asSequence()
        .filter { it.fieldId == fieldId && it.epochDay <= today.toEpochDay() }
        .minOfOrNull { it.epochDay } ?: today.toEpochDay()
    return YearMonth.from(LocalDate.ofEpochDay(first))
}

internal fun hasHabitGoal(field: TrackerFieldEntity): Boolean =
    field.valueType in numericHabitTypes && field.targetValue?.isFinite() == true &&
        when (field.targetComparison) {
            "AT_LEAST", "AT_MOST", "EXACTLY" -> true
            "RANGE" -> field.targetMaxValue?.let { it.isFinite() && it >= field.targetValue } == true
            else -> false
        }

internal fun habitGoalMet(field: TrackerFieldEntity, value: Double): Boolean = when (field.targetComparison) {
    "AT_LEAST" -> value >= requireNotNull(field.targetValue)
    "AT_MOST" -> value <= requireNotNull(field.targetValue)
    "EXACTLY" -> value == field.targetValue
    "RANGE" -> value >= requireNotNull(field.targetValue) && value <= requireNotNull(field.targetMaxValue)
    else -> false
}

internal data class HabitHeatmapDay(
    val value: TrackerDailyValueEntity,
    val intensity: Float?,
    val level: Int?,
    val goalMet: Boolean? = null,
)

internal fun habitMonthHeatmap(
    tracker: TrackerEntity,
    field: TrackerFieldEntity?,
    values: List<TrackerDailyValueEntity>,
    month: YearMonth,
    today: LocalDate,
): Map<Long, HabitHeatmapDay> =
    habitHeatmap(tracker, field, values, month.atDay(1), month.atEndOfMonth(), today)

internal fun habitWeekCells(today: LocalDate): List<LocalDate> {
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return List(7) { monday.plusDays(it.toLong()) }
}

internal fun habitWeekHeatmap(
    tracker: TrackerEntity,
    field: TrackerFieldEntity?,
    values: List<TrackerDailyValueEntity>,
    today: LocalDate,
): Map<Long, HabitHeatmapDay> {
    val week = habitWeekCells(today)
    return habitHeatmap(tracker, field, values, week.first(), week.last(), today)
}

private fun habitHeatmap(
    tracker: TrackerEntity,
    field: TrackerFieldEntity?,
    values: List<TrackerDailyValueEntity>,
    first: LocalDate,
    last: LocalDate,
    today: LocalDate,
): Map<Long, HabitHeatmapDay> {
    if (field == null || field.trackerId != tracker.id || field.isArchived) return emptyMap()
    val start = first.toEpochDay()
    val end = minOf(last, today).toEpochDay()
    val visible = values.filter { it.fieldId == field.id && it.epochDay in start..end }
    if (field.valueType in numericHabitTypes) {
        val numbers = visible.mapNotNull { it.numericValue?.takeIf(Double::isFinite) }
        val minimum = numbers.minOrNull() ?: 0.0
        val maximum = numbers.maxOrNull() ?: 0.0
        val goal = tracker.heatmapMode == "GOAL" && hasHabitGoal(field)
        return visible.mapNotNull { row ->
            val number = row.numericValue?.takeIf(Double::isFinite) ?: return@mapNotNull null
            val met = if (goal) habitGoalMet(field, number) else null
            val intensity = when {
                met != null -> if (met) 1f else 0f
                number == 0.0 -> 0f
                minimum == maximum -> 1f
                else -> {
                    // Scaling first also keeps a finite legacy signed range from overflowing.
                    val scale = maxOf(kotlin.math.abs(minimum), kotlin.math.abs(maximum))
                    ((number / scale - minimum / scale) / (maximum / scale - minimum / scale)).toFloat()
                }
            }
            row.epochDay to HabitHeatmapDay(row, intensity.coerceIn(0f, 1f), null, met)
        }.toMap()
    }
    return visible.mapNotNull { row ->
        val level = when (field.valueType) {
            "BOOLEAN" -> row.booleanValue?.let { if (it) 3 else 0 }
            "CHOICE" -> row.choiceIntensity?.let { recorded ->
                HabitChoiceIntensity.entries.firstOrNull { it.name == recorded }?.heatmapLevel
            } ?: decodeHabitChoices(field).firstOrNull {
                it.id == row.choiceOptionId || row.choiceOptionId == null && it.label == row.textValue
            }?.intensity?.heatmapLevel
            "TEXT", "DATETIME" -> row.textValue?.takeIf(String::isNotBlank)?.let { 2 }
            else -> null
        } ?: return@mapNotNull null
        row.epochDay to HabitHeatmapDay(row, null, level)
    }.toMap()
}

internal fun habitMonthCells(month: YearMonth): List<LocalDate?> {
    val leading = month.atDay(1).dayOfWeek.value - 1
    return List(42) { index ->
        (index - leading + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
    }
}

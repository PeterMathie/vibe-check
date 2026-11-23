package com.petermathie.vibecheck.domain.tracker

import com.petermathie.vibecheck.data.local.TrackerFieldEntity

data class HabitFieldForm(
    val name: String,
    val type: String,
    val unit: String,
    val options: String,
    val target: String,
    val targetMaximum: String,
    val comparison: String,
    val choiceLightThrough: Int = -1,
    val choiceDarkFrom: Int = -1,
    val choices: List<HabitChoiceOption> = emptyList(),
) {
    val isNumeric: Boolean
        get() = type in NUMERIC_TYPES

    val choiceValues: List<String>
        get() = if (choices.isNotEmpty()) choices.map(HabitChoiceOption::label)
        else options.split(',', '\n').map(String::trim).filter(String::isNotBlank)

    val isTargetValid: Boolean
        get() = target.isBlank() || target.toDoubleOrNull()?.isFinite() == true

    val isRangeValid: Boolean
        get() = comparison != RANGE || target.isBlank() || (
            target.toDoubleOrNull()?.isFinite() == true &&
                targetMaximum.toDoubleOrNull()?.isFinite() == true &&
                targetMaximum.toDouble() >= target.toDouble()
            )

    val areChoicesValid: Boolean
        get() = type != CHOICE || (
            choiceValues.size >= 2 &&
                choiceValues.all { it.trim().isNotEmpty() } &&
                choiceValues.map { it.trim() }.distinct().size == choiceValues.size
            )

    val canSave: Boolean
        get() = type in TYPES && name.isNotBlank() && isTargetValid && isRangeValid &&
            (!isNumeric || comparison == NONE && target.isBlank() ||
                comparison in COMPARISONS && target.isNotBlank()) && areChoicesValid && areChoiceShadesValid

    fun isValidFor(original: TrackerFieldEntity?): Boolean = canSave &&
        (target.isBlank() || target.toDoubleOrNull()?.let { it >= 0.0 || it == original?.targetValue } == true) &&
        (comparison != RANGE || target.isBlank() ||
            targetMaximum.toDoubleOrNull()?.let { it >= 0.0 || it == original?.targetMaxValue } == true)

    val areChoiceShadesValid: Boolean
        get() = type != CHOICE || choices.isNotEmpty() || (
            choiceLightThrough in 0 until choiceValues.lastIndex &&
                choiceDarkFrom in 1..choiceValues.lastIndex &&
                choiceLightThrough < choiceDarkFrom
            )

    fun applyTo(field: TrackerFieldEntity): TrackerFieldEntity {
        val minimum = if (isNumeric && comparison != NONE) target.toDoubleOrNull() else null
        return field.copy(
            name = name,
            valueType = type,
            unit = unit.takeIf { isNumeric && it.isNotBlank() },
            targetValue = minimum,
            targetComparison = if (minimum == null) null else comparison,
            position = field.position,
            choiceOptions = if (type == CHOICE) choiceValues.joinToString("\n") else "",
            choiceLightThrough = if (type == CHOICE) choiceLightThrough else -1,
            choiceDarkFrom = if (type == CHOICE) choiceDarkFrom else -1,
            choiceOptionsJson = if (type == CHOICE) {
                encodeHabitChoices(choices.map { it.copy(label = it.label.trim()) })
            } else {
                ""
            },
            targetMaxValue = if (minimum != null && comparison == RANGE) {
                targetMaximum.toDoubleOrNull()
            } else {
                null
            },
        )
    }

    companion object {
        const val CHOICE = "CHOICE"
        const val RANGE = "RANGE"
        const val NONE = "NONE"
        val TYPES = listOf(
            "BOOLEAN",
            "NUMBER",
            "TEXT",
            CHOICE,
        )
        val COMPARISONS = listOf("AT_LEAST", "AT_MOST", "EXACTLY", RANGE)
        private val NUMERIC_TYPES = setOf("NUMBER", "COUNT", "DURATION", "RATING")

        fun from(field: TrackerFieldEntity) = HabitFieldForm(
            name = field.name,
            type = when (field.valueType) {
                "COUNT", "DURATION", "RATING" -> "NUMBER"
                "DATETIME" -> "TEXT"
                else -> field.valueType
            },
            unit = field.unit.orEmpty(),
            options = field.choiceOptions,
            target = field.targetValue?.toString().orEmpty(),
            targetMaximum = field.targetMaxValue?.toString().orEmpty(),
            comparison = field.targetComparison ?: NONE,
            choiceLightThrough = field.choiceLightThrough,
            choiceDarkFrom = field.choiceDarkFrom,
            choices = decodeHabitChoices(field),
        )
    }
}

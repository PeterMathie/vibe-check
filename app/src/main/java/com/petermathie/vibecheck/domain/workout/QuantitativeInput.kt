package com.petermathie.vibecheck.domain.workout

import com.petermathie.vibecheck.data.local.WorkoutEntryDraftEntity

private const val MAX_QUANTITATIVE_VALUE = 1_000_000_000.0
private val DECIMAL_INPUT = Regex("""(?:\d+(?:[.,]\d*)?|[.,]\d+)""")

data class QuantitativeInput(
    val value: Double? = null,
    val error: String? = null,
    val isTransient: Boolean = false,
) {
    val isValid: Boolean get() = error == null && !isTransient
}

fun quantitativeInput(
    text: String,
    maximum: Double = MAX_QUANTITATIVE_VALUE,
): QuantitativeInput {
    if (text.isEmpty()) return QuantitativeInput(isTransient = true)
    if (text == "." || text == ",") return QuantitativeInput(isTransient = true)
    if (!DECIMAL_INPUT.matches(text)) {
        return QuantitativeInput(error = "Enter a non-negative decimal")
    }
    if (text.last() == '.' || text.last() == ',') {
        return QuantitativeInput(isTransient = true)
    }
    val value = text.replace(',', '.').toDoubleOrNull()
        ?: return QuantitativeInput(error = "Enter a non-negative decimal")
    if (!value.isFinite() || value > maximum) {
        return QuantitativeInput(error = "Value is too large")
    }
    return QuantitativeInput(value = value)
}

fun formatQuantitative(value: Double, maximumFractionDigits: Int? = null): String {
    val decimal = java.math.BigDecimal.valueOf(value)
    if (maximumFractionDigits != null) require(maximumFractionDigits >= 0)
    return (maximumFractionDigits?.let { decimal.setScale(it, java.math.RoundingMode.HALF_UP) } ?: decimal)
        .stripTrailingZeros().toPlainString()
}

fun hasInvalidQuantitativeInput(draft: WorkoutEntryDraftEntity): Boolean {
    if (draft.performance.isNotEmpty()) {
        val parts = draft.performance.split(Regex("\\s*[x×]\\s*"))
        if (parts.size !in 1..2 || parts.any { !quantitativeInput(it).isValid }) return true
    }
    if (
        listOf(
            draft.leftValue,
            draft.rightValue,
            draft.addedWeight,
            draft.assistance,
            draft.romValue,
            draft.distanceValue,
            draft.timeHeld,
            draft.timeUnderTension,
        ).plus(timingInputs(draft.tutEntries)).any { it.isNotEmpty() && !quantitativeInput(it).isValid }
    ) return true
    return draft.rpe.isNotEmpty() && !quantitativeInput(draft.rpe, maximum = 10.0).isValid
}

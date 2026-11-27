package com.petermathie.vibecheck.domain.body

private const val POUNDS_PER_KILOGRAM = 2.2046226218

internal enum class BodyweightUnit(val symbol: String) {
    KILOGRAMS("kg"),
    POUNDS("lb");

    companion object {
        fun stored(unit: String): BodyweightUnit =
            entries.firstOrNull { it.symbol.equals(unit.trim(), ignoreCase = true) }
                ?: throw IllegalArgumentException("Cannot convert bodyweight unit \"$unit\"; expected kg or lb")
    }
}

internal fun convertBodyweight(value: Double, from: BodyweightUnit, to: BodyweightUnit): Double {
    val converted = when {
        from == to -> value
        from == BodyweightUnit.POUNDS -> value / POUNDS_PER_KILOGRAM
        else -> value * POUNDS_PER_KILOGRAM
    }
    require(converted.isFinite()) { "Bodyweight is outside the supported numeric range" }
    return converted
}

internal fun bodyweightInputError(text: String): String? =
    if (text.isBlank() || text.trim().toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true) null
    else "Enter a finite bodyweight greater than zero"

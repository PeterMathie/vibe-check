package com.petermathie.vibecheck.domain.programme

data class ExerciseInputConfig(
    val weightUnit: String? = null,
    val bandResistance: Boolean = false,
    val timeHeld: Boolean = false,
    val timeUnderTension: Boolean = false,
    val reps: Boolean = false,
    val bodyweight: Boolean = false,
    val addedWeight: Boolean = false,
    val repeatedTimeMeasurements: Boolean = false,
) {
    fun encode(): String = listOf(
        "weightUnit=${weightUnit.orEmpty()}",
        "bandResistance=$bandResistance",
        "timeHeld=$timeHeld",
        "timeUnderTension=$timeUnderTension",
        "reps=$reps",
        "bodyweight=$bodyweight",
        "addedWeight=$addedWeight",
        "repeatedTimeMeasurements=$repeatedTimeMeasurements",
    ).joinToString(";")

    companion object {
        fun decode(value: String, trackingType: String): ExerciseInputConfig {
            if (value.isBlank()) return defaults(trackingType)
            val fields = value.split(";").mapNotNull {
                val parts = it.split("=", limit = 2)
                parts.takeIf { pair -> pair.size == 2 }?.let { pair -> pair[0] to pair[1] }
            }.toMap()
            val defaults = defaults(trackingType)
            return ExerciseInputConfig(
                weightUnit = if ("weightUnit" in fields) {
                    fields["weightUnit"].takeIf { it == "kg" || it == "lb" }
                } else defaults.weightUnit,
                bandResistance = fields["bandResistance"]?.toBooleanStrictOrNull() ?: defaults.bandResistance,
                timeHeld = fields["timeHeld"]?.toBooleanStrictOrNull() ?: defaults.timeHeld,
                timeUnderTension = fields["timeUnderTension"]?.toBooleanStrictOrNull() ?: defaults.timeUnderTension,
                reps = fields["reps"]?.toBooleanStrictOrNull() ?: defaults.reps,
                bodyweight = fields["bodyweight"]?.toBooleanStrictOrNull() ?: defaults.bodyweight,
                addedWeight = fields["addedWeight"]?.toBooleanStrictOrNull() ?: defaults.addedWeight,
                repeatedTimeMeasurements = fields["repeatedTimeMeasurements"]?.toBooleanStrictOrNull()
                    ?: defaults.repeatedTimeMeasurements,
            )
        }

        fun defaults(trackingType: String) = when (trackingType) {
            "WEIGHT_REPS" -> ExerciseInputConfig(weightUnit = "kg", reps = true)
            "BODYWEIGHT_REPS" -> ExerciseInputConfig(reps = true, bodyweight = true)
            "ASSISTED_REPS" -> ExerciseInputConfig(reps = true, bodyweight = true, bandResistance = true)
            "HOLD", "SKILL_HOLD" -> ExerciseInputConfig(timeHeld = true)
            else -> ExerciseInputConfig(reps = true)
        }
    }
}

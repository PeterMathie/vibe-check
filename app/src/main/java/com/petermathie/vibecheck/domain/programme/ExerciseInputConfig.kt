package com.petermathie.vibecheck.domain.programme

enum class ExerciseMeasurement(val key: String, val label: String, val placeholder: String) {
    BODYWEIGHT("bodyweight", "Bodyweight", "Bodyweight"),
    WEIGHT("weight", "Weight", "— kg"),
    REPS("reps", "Reps", "— reps"),
    TUT("tut", "Time Under Tension", "TUT — s"),
    TOTAL_TIME("totalTime", "Total Time", "Total — s"),
    RPE("rpe", "RPE", "RPE —"),
    ADDED_WEIGHT("addedWeight", "Added weight", "+— kg"),
    BANDS("bands", "Band resistance", "Bands —"),
}

data class ExerciseInputConfig(
    val weightUnit: String? = null,
    val bandResistance: Boolean = false,
    val timeHeld: Boolean = false,
    val timeUnderTension: Boolean = false,
    val reps: Boolean = false,
    val bodyweight: Boolean = false,
    val addedWeight: Boolean = false,
    val manyTuts: Boolean = false,
    val measurementOrder: List<ExerciseMeasurement> = emptyList(),
) {
    val orderedMeasurements: List<ExerciseMeasurement>
        get() = (measurementOrder + ExerciseMeasurement.entries).distinct()

    fun enabledMeasurements(): List<ExerciseMeasurement> = orderedMeasurements.filter {
        when (it) {
            ExerciseMeasurement.BODYWEIGHT -> bodyweight
            ExerciseMeasurement.WEIGHT -> weightUnit != null
            ExerciseMeasurement.REPS -> reps
            ExerciseMeasurement.TUT -> timeHeld || manyTuts
            ExerciseMeasurement.TOTAL_TIME -> timeUnderTension
            ExerciseMeasurement.RPE -> true
            ExerciseMeasurement.ADDED_WEIGHT -> addedWeight
            ExerciseMeasurement.BANDS -> bandResistance
        }
    }

    fun encode(): String = (listOf(
        "weightUnit=${weightUnit.orEmpty()}",
        "bandResistance=$bandResistance",
        "timeHeld=$timeHeld",
        "timeUnderTension=$timeUnderTension",
        "reps=$reps",
        "bodyweight=$bodyweight",
        "addedWeight=$addedWeight",
        "manyTuts=$manyTuts",
    ) + if (measurementOrder.isEmpty()) emptyList() else listOf(
        "measurementOrder=${measurementOrder.joinToString(",") { it.key }}",
    )).joinToString(";")

    companion object {
        fun decode(
            value: String,
            trackingType: String,
            exerciseId: String? = null,
            defaultManyTuts: Boolean = exerciseId == "core:handstand",
            defaultMeasurementOrder: List<ExerciseMeasurement> = emptyList(),
        ): ExerciseInputConfig {
            if (value.isBlank()) return defaults(trackingType).copy(
                manyTuts = defaultManyTuts,
                measurementOrder = defaultMeasurementOrder,
            )
            val fields = value.split(";").mapNotNull {
                val parts = it.split("=", limit = 2)
                parts.takeIf { pair -> pair.size == 2 }?.let { pair -> pair[0] to pair[1] }
            }.toMap()
            val orders = value.split(";").filter { it.startsWith("measurementOrder=") }
            require(orders.size <= 1) { "Duplicate measurement order" }
            val order = orders.singleOrNull()?.substringAfter("=")?.let { encoded ->
                val keys = encoded.split(",")
                require(keys.distinct().size == keys.size) { "Duplicate measurement in order" }
                keys.map { key ->
                    requireNotNull(ExerciseMeasurement.entries.find { it.key == key }) {
                        "Unknown measurement in order: $key"
                    }
                }
            } ?: defaultMeasurementOrder
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
                manyTuts = fields["manyTuts"]?.toBooleanStrictOrNull() ?: defaultManyTuts,
                measurementOrder = order,
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

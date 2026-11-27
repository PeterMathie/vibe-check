package com.petermathie.vibecheck.domain.programme

const val MAX_RESISTANCE_BANDS = 8

enum class ResistanceMetric(val label: String) {
    BODYWEIGHT("Bodyweight"),
    KILOGRAMS("Kilograms"),
    BANDS("Resistance bands"),
}

enum class MeasurementMetric(val label: String) {
    REPETITIONS("Repetitions"),
    HOLD("Hold duration"),
    SKILL_HOLD("Skill hold"),
    RANGE_OF_MOTION("ROM (degrees)"),
    DISTANCE("Distance (cm)"),
}

enum class ExerciseMeasurement(val key: String, val label: String, val placeholder: String) {
    BODYWEIGHT("bodyweight", "Bodyweight", "Bodyweight"),
    WEIGHT("weight", "Weight", "— kg"),
    REPS("reps", "Reps", "— reps"),
    TUT("tut", "Time Under Tension", "TUT — s"),
    TOTAL_TIME("totalTime", "Total Time", "Total — s"),
    RPE("rpe", "RPE", "RPE —"),
    ADDED_WEIGHT("addedWeight", "Added weight", "+— kg"),
    BANDS("bands", "Band resistance", "Bands —"),
    ROM("rom", "Range of motion", "— °"),
    DISTANCE("distance", "Distance", "— cm"),
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
    val resistanceMetric: ResistanceMetric? = null,
    val measurementMetric: MeasurementMetric? = null,
) {
    fun loggingResistance(): ResistanceMetric? = resistanceMetric ?: when {
        weightUnit != null && addedWeight -> null
        bandResistance && (weightUnit != null || addedWeight) -> null
        bandResistance -> ResistanceMetric.BANDS
        weightUnit != null || addedWeight -> ResistanceMetric.KILOGRAMS
        else -> ResistanceMetric.BODYWEIGHT
    }

    fun loggingMeasurement(): MeasurementMetric? = measurementMetric ?: when {
        reps && (timeHeld || manyTuts || timeUnderTension) -> null
        manyTuts || timeHeld && timeUnderTension -> MeasurementMetric.SKILL_HOLD
        timeHeld || timeUnderTension -> MeasurementMetric.HOLD
        reps -> MeasurementMetric.REPETITIONS
        else -> null
    }

    val loggingWeightUnit: String?
        get() = if (loggingResistance() == ResistanceMetric.KILOGRAMS && !addedWeight) weightUnit ?: "kg" else null

    fun withResistance(metric: ResistanceMetric, unit: String = "kg"): ExerciseInputConfig {
        val useAddedWeight = metric == ResistanceMetric.KILOGRAMS &&
            (addedWeight || weightUnit == null && bodyweight)
        return copy(
            resistanceMetric = metric,
            weightUnit = unit.takeIf { metric == ResistanceMetric.KILOGRAMS && !useAddedWeight },
            addedWeight = useAddedWeight,
            bandResistance = metric == ResistanceMetric.BANDS,
        )
    }

    fun withMeasurement(metric: MeasurementMetric): ExerciseInputConfig {
        val totalOnly = metric == MeasurementMetric.HOLD && !timeHeld && timeUnderTension && !manyTuts
        return copy(
            measurementMetric = metric,
            reps = metric == MeasurementMetric.REPETITIONS,
            timeHeld = metric == MeasurementMetric.HOLD && !totalOnly || metric == MeasurementMetric.SKILL_HOLD,
            manyTuts = metric == MeasurementMetric.SKILL_HOLD,
            timeUnderTension = totalOnly || metric == MeasurementMetric.SKILL_HOLD,
        )
    }

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
            ExerciseMeasurement.ROM -> measurementMetric == MeasurementMetric.RANGE_OF_MOTION
            ExerciseMeasurement.DISTANCE -> measurementMetric == MeasurementMetric.DISTANCE
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
    ) + listOfNotNull(
        resistanceMetric?.let { "resistanceMetric=${it.name}" },
        measurementMetric?.let { "measurementMetric=${it.name}" },
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
            fun <T : Enum<T>> metric(key: String, choices: List<T>): T? {
                val entries = value.split(";").filter { it.startsWith("$key=") }
                require(entries.size <= 1) { "Duplicate $key" }
                return entries.singleOrNull()?.substringAfter("=")?.let { name ->
                    requireNotNull(choices.find { it.name == name }) { "Unknown $key: $name" }
                }
            }
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
                resistanceMetric = metric("resistanceMetric", ResistanceMetric.entries),
                measurementMetric = metric("measurementMetric", MeasurementMetric.entries)
                    ?: defaults.measurementMetric?.takeIf {
                        fields.keys.none { key -> key in setOf("reps", "timeHeld", "timeUnderTension", "manyTuts") }
                    },
            )
        }

        fun defaults(trackingType: String) = when (trackingType) {
            "WEIGHT_REPS" -> ExerciseInputConfig(weightUnit = "kg", reps = true)
            "BODYWEIGHT_REPS" -> ExerciseInputConfig(reps = true, bodyweight = true)
            "ASSISTED_REPS" -> ExerciseInputConfig(reps = true, bodyweight = true, bandResistance = true)
            "HOLD", "SKILL_HOLD" -> ExerciseInputConfig(timeHeld = true)
            "ROM_MEASUREMENT" -> ExerciseInputConfig(measurementMetric = MeasurementMetric.RANGE_OF_MOTION)
            else -> ExerciseInputConfig(reps = true)
        }
    }
}

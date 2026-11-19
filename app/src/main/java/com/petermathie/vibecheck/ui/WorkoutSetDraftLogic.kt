package com.petermathie.vibecheck.ui

import com.petermathie.vibecheck.data.local.ExerciseVariationEntity
import com.petermathie.vibecheck.data.local.WorkoutEntryDraftEntity
import com.petermathie.vibecheck.data.local.WorkoutExerciseEntity
import com.petermathie.vibecheck.data.local.WorkoutSetEntity
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.MeasurementMetric
import com.petermathie.vibecheck.domain.programme.ResistanceMetric
import com.petermathie.vibecheck.domain.workout.*
import org.json.JSONArray
import org.json.JSONObject

internal fun variantDraftMetadata(encoded: String): VariantDraftMetadata = object : VariantDraftMetadata {
    private val values = JSONObject(encoded)
    override val inputConfig get() = values.getString("inputConfig")
    override val variantName get() = values.getString("variantName")
    override val trackingType get() = values.getString("trackingType")
    override val rank get() = if (values.isNull("rank")) null else values.getInt("rank")
}

internal fun acceptsRpeEdit(current: String, proposed: String): Boolean {
    if (!quantitativeInput(current, 10.0).isValid && proposed.length < current.length) return true
    val parsed = quantitativeInput(proposed, 10.0)
    return parsed.error == null &&
        (!parsed.isTransient || quantitativeInput(proposed.dropLast(1), 10.0).error == null)
}

/** Resolves draft values without owning Compose state, persistence or completion effects. */
internal class WorkoutSetDraftLogic(
    private val config: ExerciseInputConfig,
    private val recorded: WorkoutSetEntity? = null,
    private val variation: ExerciseVariationEntity? = null,
    private val cached: VariantDraftMetadata? = null,
    private val preferredWeightUnit: String = "kg",
) {
    fun performanceParts(draft: WorkoutEntryDraftEntity): Pair<String, String> {
        if (config.loggingWeightUnit == null) return "" to
            if (config.loggingMeasurement() == MeasurementMetric.REPETITIONS) draft.performance else ""
        val parts = draft.performance.split(Regex("\\s*[x×]\\s*"), limit = 2)
        return parts.getOrElse(0) { "" } to parts.getOrElse(1) { "" }
    }

    fun inputError(draft: WorkoutEntryDraftEntity): String? {
        val resistanceMetric = config.loggingResistance()
        val measurementMetric = config.loggingMeasurement()
        if (resistanceMetric == null || measurementMetric == null) return config.loggingMetricsError()
        val (resistance, reps) = performanceParts(draft)
        val values = buildList {
            if (resistanceMetric == ResistanceMetric.KILOGRAMS) add(if (config.addedWeight) draft.addedWeight else resistance)
            when (measurementMetric) {
                MeasurementMetric.REPETITIONS -> add(reps)
                MeasurementMetric.RANGE_OF_MOTION -> add(draft.romValue)
                MeasurementMetric.HOLD -> if (!config.timeHeld && config.timeUnderTension) add(draft.timeUnderTension)
                    else addAll(attemptsFor(draft))
                MeasurementMetric.SKILL_HOLD -> {
                    addAll(attemptsFor(draft))
                    add(draft.timeUnderTension)
                }
            }
        }
        if (values.any { it.isNotEmpty() && !quantitativeInput(it).isValid }) {
            return "Complete or correct the highlighted number"
        }
        if (draft.rpe.isNotEmpty() && !quantitativeInput(draft.rpe, 10.0).isValid) {
            return "RPE: 0 to 10"
        }
        return null
    }

    fun parsedSet(draft: WorkoutEntryDraftEntity): WorkoutSetEntity? {
        if (inputError(draft) != null) return null
        val measurementMetric = config.loggingMeasurement()
        val (resistance, reps) = performanceParts(draft)
        val hasMeasurement = when (measurementMetric) {
            MeasurementMetric.REPETITIONS -> quantitativeInput(reps).value != null
            MeasurementMetric.RANGE_OF_MOTION -> quantitativeInput(draft.romValue).value != null
            MeasurementMetric.HOLD -> if (!config.timeHeld && config.timeUnderTension) {
                quantitativeInput(draft.timeUnderTension).value != null
            } else attemptsFor(draft).any { quantitativeInput(it).value != null }
            MeasurementMetric.SKILL_HOLD -> attemptsFor(draft).any { quantitativeInput(it).value != null } ||
                quantitativeInput(draft.timeUnderTension).value != null
            null -> false
        }
        if (!hasMeasurement) return null
        val base = recorded ?: emptySet(draft.workoutExerciseId, draft.ordinal).copy(id = draft.setId)
        val sameVariant = base.variationId == draft.variationId
        val durations = attemptsFor(draft).mapNotNull { quantitativeInput(it).value?.times(1000)?.toLong() }
        return base.copy(
            weightKg = if (config.loggingWeightUnit == null) base.weightKg else quantitativeInput(resistance).value?.let {
                if (config.loggingWeightUnit == "lb") it / 2.2046226218 else it
            },
            reps = if (measurementMetric == MeasurementMetric.REPETITIONS) quantitativeInput(reps).value else base.reps,
            variationId = draft.variationId,
            variationNameSnapshot = if (sameVariant) base.variationNameSnapshot else cached?.variantName ?: variation?.name.orEmpty(),
            variationTrackingTypeSnapshot = if (sameVariant) base.variationTrackingTypeSnapshot else cached?.trackingType ?: variation?.trackingType.orEmpty(),
            variationRankSnapshot = if (sameVariant) base.variationRankSnapshot else cached?.rank ?: variation?.progressionRank,
            holdMillis = durations.takeIf { it.isNotEmpty() }?.sum(),
            tutEntriesMillis = encodeTimingMillis(durations),
            timeUnderTensionMillis = quantitativeInput(draft.timeUnderTension).value?.times(1000)?.toLong(),
            bandResistance = config.bandResistance,
            addedWeightKg = quantitativeInput(draft.addedWeight).value?.let {
                if (preferredWeightUnit == "lb") it / 2.2046226218 else it
            },
            rpe = quantitativeInput(draft.rpe, 10.0).value,
            romValue = if (measurementMetric == MeasurementMetric.RANGE_OF_MOTION) quantitativeInput(draft.romValue).value else base.romValue,
            romUnit = if (measurementMetric == MeasurementMetric.RANGE_OF_MOTION) draft.romUnit else base.romUnit,
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun editedSet(draft: WorkoutEntryDraftEntity): WorkoutSetEntity? {
        parsedSet(draft)?.let { return it }
        if (inputError(draft) != null) return null
        val cleared = recorded?.let { config.clearedRecordedMeasurement(draft, it) } ?: return null
        val (resistance, _) = performanceParts(draft)
        return cleared.copy(
            weightKg = if (config.loggingWeightUnit == null) cleared.weightKg else
                quantitativeInput(resistance).value?.let {
                    if (config.loggingWeightUnit == "lb") it / 2.2046226218 else it
                },
            addedWeightKg = if (!config.addedWeight) cleared.addedWeightKg else
                quantitativeInput(draft.addedWeight).value?.let {
                    if (preferredWeightUnit == "lb") it / 2.2046226218 else it
                },
            rpe = quantitativeInput(draft.rpe, 10.0).value,
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun snapshotConfig(): String = config.copy(
        resistanceMetric = config.loggingResistance(),
        measurementMetric = config.loggingMeasurement(),
    ).encode()

    fun changeVariant(
        draft: WorkoutEntryDraftEntity,
        nextVariantId: String,
        nextConfig: ExerciseInputConfig,
    ): WorkoutEntryDraftEntity {
        val (resistance, result) = performanceParts(draft)
        val previousWeight = quantitativeInput(resistance).value?.let {
            if (config.loggingWeightUnit == "lb") it / 2.2046226218 else it
        } ?: recorded?.weightKg
        val nextWeight = previousWeight?.let {
            formatQuantitative(if (nextConfig.loggingWeightUnit == "lb") it * 2.2046226218 else it)
        }.orEmpty()
        val nextReps = result.ifBlank { recorded?.reps?.let(::formatQuantitative).orEmpty() }
        val nextPerformance = if (nextConfig.loggingWeightUnit == null) nextReps else {
            if (nextWeight.isBlank() && nextReps.isBlank()) "" else "$nextWeight x $nextReps"
        }
        return draft.copy(
            variationId = nextVariantId,
            performance = nextPerformance,
            updatedAt = System.currentTimeMillis(),
        )
    }
}

fun emptySet(id: String, ordinal: Int) = WorkoutSetEntity(newId(), id, ordinal, "WORKING", "COMPLETED", null, null, null, null, null, null, null, null, null, null, null, null, null, "", System.currentTimeMillis(), System.currentTimeMillis())
fun emptyEntryDraft(id: String, ordinal: Int) = WorkoutEntryDraftEntity(id, newId(), ordinal, "", "", false, false, false, "[]", null, "", "", "", "", "", "degrees", System.currentTimeMillis())

internal data class WorkoutSetSelection(val workoutExerciseId: String, val ordinal: Int, val setId: String?) {
    fun reconcile(rowId: String, drafts: List<WorkoutEntryDraftEntity>): WorkoutSetSelection? {
        if (rowId != workoutExerciseId) return this
        val draft = drafts.firstOrNull { it.setId == setId } ?: drafts.firstOrNull { it.ordinal == ordinal }
        return draft?.let { copy(ordinal = it.ordinal, setId = it.setId) }
    }
}

internal fun firstUntickedWorkoutSet(
    rows: List<WorkoutExerciseEntity>,
    sets: List<WorkoutSetEntity>,
    drafts: List<WorkoutEntryDraftEntity>,
    lockedSetIds: Set<String>,
): WorkoutSetSelection? {
    rows.forEach { row ->
        val saved = sets.filter { it.workoutExerciseId == row.id }
        val recovered = drafts.filter { it.workoutExerciseId == row.id }
        val count = maxOf(
            row.configuredSetCount(),
            saved.maxOfOrNull { it.ordinal } ?: 0,
            recovered.maxOfOrNull { it.ordinal } ?: 0,
        )
        for (ordinal in 1..count) {
            val id = recovered.firstOrNull { it.ordinal == ordinal }?.setId
                ?: saved.firstOrNull { it.ordinal == ordinal }?.id
            if (id !in lockedSetIds) return WorkoutSetSelection(row.id, ordinal, id)
        }
    }
    return null
}

internal fun newSetEntryDraft(
    workoutExerciseId: String,
    ordinal: Int,
    previous: WorkoutEntryDraftEntity?,
    defaultVariationId: String?,
    validVariationIds: Set<String>,
): WorkoutEntryDraftEntity {
    val variationId = previous?.variationId
        ?.takeIf { it in validVariationIds }
        ?: defaultVariationId?.takeIf { it in validVariationIds }
    return emptyEntryDraft(workoutExerciseId, ordinal).copy(variationId = variationId)
}

internal fun attemptsFor(draft: WorkoutEntryDraftEntity): List<String> =
    timingInputs(draft.tutEntries).ifEmpty { listOf(draft.timeHeld) }

internal fun visibleTimingInputs(attempts: List<String>, locked: Boolean): List<String> {
    require(attempts.size <= MAX_TUT_ENTRIES)
    val last = attempts.indexOfLast(String::isNotEmpty)
    val offerNext = !locked && last >= 0 && quantitativeInput(attempts[last]).value != null
    val count = maxOf(1, last + 1 + if (offerNext) 1 else 0).coerceAtMost(MAX_TUT_ENTRIES)
    return List(count) { attempts.getOrElse(it) { "" } }
}

internal fun variantDraftKey(setId: String, variantId: String?): String =
    JSONArray().put(setId).put(variantId).toString()

internal fun encodeVariantDraft(
    draft: WorkoutEntryDraftEntity,
    inputConfig: String,
    variantName: String,
    trackingType: String,
    rank: Int?,
): String = JSONObject().apply {
    put("inputConfig", inputConfig)
    put("variantName", variantName)
    put("trackingType", trackingType)
    put("rank", rank ?: JSONObject.NULL)
    put("performance", draft.performance)
    put("rpe", draft.rpe)
    put("bands", draft.bandIds)
    put("left", draft.leftValue)
    put("right", draft.rightValue)
    put("addedWeight", draft.addedWeight)
    put("assistance", draft.assistance)
    put("rom", draft.romValue)
    put("romUnit", draft.romUnit)
    put("held", draft.timeHeld)
    put("total", draft.timeUnderTension)
    put("attempts", draft.tutEntries)
}.toString()

internal fun restoreVariantDraft(draft: WorkoutEntryDraftEntity, encoded: String): WorkoutEntryDraftEntity {
    val values = JSONObject(encoded)
    return draft.copy(
        performance = values.getString("performance"),
        rpe = values.getString("rpe"),
        bandIds = values.getString("bands"),
        leftValue = values.getString("left"),
        rightValue = values.getString("right"),
        addedWeight = values.getString("addedWeight"),
        assistance = values.getString("assistance"),
        romValue = values.getString("rom"),
        romUnit = values.getString("romUnit"),
        timeHeld = values.getString("held"),
        timeUnderTension = values.getString("total"),
        tutEntries = values.getString("attempts"),
    )
}

internal fun draftFromSet(
    set: WorkoutSetEntity,
    hold: Boolean,
    weightUnit: String?,
    bands: List<String> = emptyList(),
    addedWeightUnit: String = "kg",
): WorkoutEntryDraftEntity {
    val reps = set.reps ?: listOfNotNull(set.leftReps, set.rightReps).minOrNull()
    val performance = when {
        weightUnit != null -> "${set.weightKg?.let { if (weightUnit == "lb") it * 2.2046226218 else it }?.let(::formatQuantitative) ?: ""} x ${reps?.let(::formatQuantitative) ?: ""}"
        else -> reps?.let(::formatQuantitative).orEmpty()
    }
    return emptyEntryDraft(set.workoutExerciseId, set.ordinal).copy(
        setId = set.id,
        performance = performance,
        rpe = set.rpe?.toString().orEmpty(),
        warmUp = set.setType == "WARM_UP",
        failed = set.result == "FAILED",
        bandIds = encodeBandIds(bands),
        variationId = set.variationId,
        leftValue = (if (hold) set.leftHoldMillis?.div(1000.0) else set.leftReps)?.toString().orEmpty(),
        rightValue = (if (hold) set.rightHoldMillis?.div(1000.0) else set.rightReps)?.toString().orEmpty(),
        addedWeight = set.addedWeightKg?.let { if (addedWeightUnit == "lb") it * 2.2046226218 else it }?.let(::formatQuantitative).orEmpty(),
        assistance = set.assistanceKg?.toString().orEmpty(),
        romValue = set.romValue?.toString().orEmpty(),
        romUnit = set.romUnit ?: "cm",
        timeHeld = set.holdMillis?.div(1000.0)?.toString().orEmpty(),
        timeUnderTension = set.timeUnderTensionMillis?.div(1000.0)?.let(::formatQuantitative).orEmpty(),
        tutEntries = timingMillis(set.tutEntriesMillis).takeIf { it.isNotEmpty() }
            ?.map { formatQuantitative(it / 1000.0) }?.let(::encodeTimingInputs).orEmpty(),
    )
}

internal fun bandIds(value: String): List<String> {
    val values = JSONArray(value)
    return (0 until values.length()).map(values::getString)
}

internal fun encodeBandIds(values: List<String>): String =
    JSONArray().apply { values.forEach { put(it) } }.toString()

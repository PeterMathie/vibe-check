package com.petermathie.vibecheck.domain.workout

import com.petermathie.vibecheck.data.local.WorkoutEntryDraftEntity
import com.petermathie.vibecheck.data.local.WorkoutSetEntity

data class SetDetailsForm(
    val performance: String,
    val rpe: String,
    val warmUp: Boolean,
    val failed: Boolean,
    val variationId: String?,
    val leftValue: String,
    val rightValue: String,
    val addedWeight: String,
    val assistance: String,
    val romValue: String,
    val romUnit: String,
    val distanceValue: String = "",
    val distanceUnit: String = "cm",
) {
    fun applyTo(draft: WorkoutEntryDraftEntity): WorkoutEntryDraftEntity = draft.copy(
        performance = performance,
        rpe = rpe,
        warmUp = warmUp,
        failed = failed,
        variationId = variationId,
        leftValue = leftValue,
        rightValue = rightValue,
        addedWeight = addedWeight,
        assistance = assistance,
        romValue = romValue,
        romUnit = romUnit,
        distanceValue = distanceValue,
        distanceUnit = distanceUnit,
    )

    fun buildSet(
        original: WorkoutSetEntity,
        hold: Boolean,
        weighted: Boolean,
        pounds: Boolean,
        updatedAt: Long = System.currentTimeMillis(),
    ): SetDetailsResult {
        val parsedRpe = quantitativeInput(rpe, MAX_RPE)
        if (rpe.isNotBlank() && !parsedRpe.isValid) {
            return SetDetailsResult(error = "RPE must be 0–10")
        }
        val sideValues = listOf(leftValue, rightValue)
        val numericValues = sideValues + listOf(addedWeight, assistance, romValue, distanceValue)
        if (numericValues.any { it.isNotBlank() && !quantitativeInput(it).isValid }) {
            return SetDetailsResult(error = "Use finite, non-negative numbers")
        }

        val parsed = parsePerformance(
            original,
            if (failed) {
                if (weighted) "0 x 0" else "0"
            } else {
                performance
            },
            hold,
            weighted,
            pounds,
        )
        if (
            parsed == null &&
            quantitativeInput(romValue).value == null &&
            quantitativeInput(distanceValue).value == null &&
            quantitativeInput(leftValue).value == null &&
            quantitativeInput(rightValue).value == null
        ) {
            return SetDetailsResult(error = "Enter a valid result")
        }

        var saved = (parsed ?: original).copy(
            setType = if (warmUp) "WARM_UP" else "WORKING",
            result = if (failed) "FAILED" else "COMPLETED",
            variationId = variationId,
            rpe = parsedRpe.value,
            leftReps = if (!hold) quantitativeInput(leftValue).value else null,
            rightReps = if (!hold) quantitativeInput(rightValue).value else null,
            leftHoldMillis = if (hold) quantitativeInput(leftValue).value?.times(1000)?.toLong() else null,
            rightHoldMillis = if (hold) quantitativeInput(rightValue).value?.times(1000)?.toLong() else null,
            addedWeightKg = quantitativeInput(addedWeight).value,
            assistanceKg = quantitativeInput(assistance).value,
            romValue = quantitativeInput(romValue).value,
            romUnit = romUnit,
            distanceValue = quantitativeInput(distanceValue).value,
            distanceUnit = distanceUnit,
            updatedAt = updatedAt,
        )
        if (failed) {
            saved = saved.copy(
                reps = 0.0,
                holdMillis = 0,
                leftReps = 0.0,
                rightReps = 0.0,
                leftHoldMillis = 0,
                rightHoldMillis = 0,
                romValue = null,
                distanceValue = null,
            )
        } else if (leftValue.isNotBlank() || rightValue.isNotBlank()) {
            saved = saved.copy(reps = null, holdMillis = null)
        }
        return SetDetailsResult(set = saved)
    }

    companion object {
        private const val MIN_RPE = 0.0
        private const val MAX_RPE = 10.0

        fun from(draft: WorkoutEntryDraftEntity) = SetDetailsForm(
            performance = draft.performance,
            rpe = draft.rpe,
            warmUp = draft.warmUp,
            failed = draft.failed,
            variationId = draft.variationId,
            leftValue = draft.leftValue,
            rightValue = draft.rightValue,
            addedWeight = draft.addedWeight,
            assistance = draft.assistance,
            romValue = draft.romValue,
            romUnit = draft.romUnit,
            distanceValue = draft.distanceValue,
            distanceUnit = draft.distanceUnit,
        )

        fun from(set: WorkoutSetEntity, hold: Boolean, weighted: Boolean, pounds: Boolean) = SetDetailsForm(
            performance = when {
                hold -> set.holdMillis?.let { (it / 1000.0).toString() }.orEmpty()
                weighted -> "${set.weightKg?.let { if (pounds) it * POUNDS_PER_KILOGRAM else it } ?: 0} x ${set.reps ?: 0}"
                else -> set.reps?.toString().orEmpty()
            },
            rpe = set.rpe?.toString().orEmpty(),
            warmUp = set.setType == "WARM_UP",
            failed = set.result == "FAILED",
            variationId = set.variationId,
            leftValue = (if (hold) set.leftHoldMillis?.div(1000.0) else set.leftReps)?.toString().orEmpty(),
            rightValue = (if (hold) set.rightHoldMillis?.div(1000.0) else set.rightReps)?.toString().orEmpty(),
            addedWeight = set.addedWeightKg?.toString().orEmpty(),
            assistance = set.assistanceKg?.toString().orEmpty(),
            romValue = set.romValue?.toString().orEmpty(),
            romUnit = set.romUnit ?: "cm",
            distanceValue = set.distanceValue?.toString().orEmpty(),
            distanceUnit = set.distanceUnit ?: "cm",
        )
    }
}

data class SetDetailsResult(
    val set: WorkoutSetEntity? = null,
    val error: String? = null,
)

fun parsePerformance(
    base: WorkoutSetEntity,
    text: String,
    hold: Boolean,
    weighted: Boolean,
    pounds: Boolean,
): WorkoutSetEntity? {
    val parts = text.replace('×', 'x')
        .split('x')
        .map { it.trim() }
    if (hold) {
        val seconds = parts.singleOrNull()?.let(::quantitativeInput)?.value ?: return null
        return base.copy(
            holdMillis = (seconds * 1000).toLong(),
            result = if (seconds == 0.0) "FAILED" else "COMPLETED",
        )
    }
    val reps = parts.lastOrNull()?.let(::quantitativeInput)?.value ?: return null
    val weight = if (weighted) parts.takeIf { it.size == 2 }?.first()?.let(::quantitativeInput)?.value ?: return null else null
    return base.copy(
        reps = reps,
        weightKg = weight?.let { if (pounds) it / POUNDS_PER_KILOGRAM else it },
        result = if (reps == 0.0) "FAILED" else "COMPLETED",
    )
}

private const val POUNDS_PER_KILOGRAM = 2.2046226218

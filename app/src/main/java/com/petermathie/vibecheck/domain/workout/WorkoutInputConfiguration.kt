package com.petermathie.vibecheck.domain.workout

import com.petermathie.vibecheck.data.local.ExerciseVariationEntity
import com.petermathie.vibecheck.data.local.WorkoutExerciseEntity
import com.petermathie.vibecheck.data.local.WorkoutEntryDraftEntity
import com.petermathie.vibecheck.data.local.WorkoutSetEntity
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig

internal interface VariantDraftMetadata {
    val inputConfig: String
    val variantName: String
    val trackingType: String
    val rank: Int?
}

internal fun resolveSetInputConfig(
    row: WorkoutExerciseEntity,
    exerciseTrackingType: String,
    variation: ExerciseVariationEntity?,
    recorded: WorkoutSetEntity?,
    cached: VariantDraftMetadata?,
    configOverride: String?,
): ExerciseInputConfig {
    val defaults = if (recorded == null) ExerciseInputConfig.decode(
        row.inputConfig, row.trackingType, row.actualExerciseId,
    ) else null
    return ExerciseInputConfig.decode(
        configOverride
            ?: recorded?.variationInputConfigSnapshot?.takeIf(String::isNotBlank)
            ?: cached?.inputConfig
            ?: variation?.inputConfig?.ifBlank { row.inputConfig }
            ?: row.inputConfig,
        recorded?.variationTrackingTypeSnapshot?.takeIf(String::isNotBlank)
            ?: cached?.trackingType
            ?: variation?.trackingType?.ifBlank { exerciseTrackingType }
            ?: exerciseTrackingType,
        exerciseId = if (recorded == null) row.actualExerciseId else null,
        defaultManyTuts = defaults?.manyTuts ?: false,
        defaultMeasurementOrder = defaults?.measurementOrder.orEmpty(),
    )
}

internal fun ExerciseInputConfig.loggingMetricsError(): String? =
    if (loggingResistance() == null || loggingMeasurement() == null) {
        "Choose logging metrics before entering this set"
    } else null

internal fun WorkoutExerciseEntity.configuredSetCount(): Int =
    targetSets ?: targets.substringBefore(" sets").toIntOrNull()?.coerceAtLeast(1) ?: 3

internal fun restoredWorkoutVariationIds(
    sets: List<WorkoutSetEntity>,
    drafts: List<WorkoutEntryDraftEntity>,
    variations: List<ExerciseVariationEntity>,
): Map<Int, String?> {
    val validIds = variations.mapTo(mutableSetOf()) { it.id }
    val defaultId = variations.firstOrNull()?.id
    val ids = sets.associateTo(mutableMapOf()) { it.ordinal to it.variationId }
    drafts.sortedBy { it.ordinal }.forEach { draft ->
        ids[draft.ordinal] = draft.variationId?.takeIf { it in validIds }
            ?: ids[draft.ordinal - 1] ?: defaultId
    }
    ids.keys.sorted().forEach { ordinal ->
        ids[ordinal] = ids[ordinal]?.takeIf { it in validIds }
            ?: ids[ordinal - 1]?.takeIf { it in validIds } ?: defaultId
    }
    return ids
}

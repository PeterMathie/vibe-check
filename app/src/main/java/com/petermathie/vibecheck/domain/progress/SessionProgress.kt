package com.petermathie.vibecheck.domain.progress

import com.petermathie.vibecheck.data.local.*

data class ProgressPoint(val date: Long, val score: Double, val performance: WorkoutSetEntity, val notes: String, val variation: String?, val bands: List<String>, val index: Double? = null, val trend: Double? = null, val rank: Int = 0)
data class PersonalRecords(
    val weightKg: Double?,
    val holdMillis: Long?,
    val estimatedOneRepMaxKg: Double?,
    val scoredPerformance: ProgressPoint?,
)

data class PersonalRecordVisibility(
    val weight: Boolean,
    val hold: Boolean,
    val estimatedOneRepMax: Boolean,
)
data class AggregateProgressPoint(val date: Long, val averageIndex: Double, val exerciseCount: Int)

object SessionProgress {
    fun aggregate(pointsByExercise: Map<String, List<ProgressPoint>>): List<AggregateProgressPoint> {
        val weeklyByExercise = pointsByExercise.mapValues { (_, points) ->
            points.filter { it.index != null }
                .groupBy { java.time.Instant.ofEpochMilli(it.date).atZone(java.time.ZoneOffset.UTC).toLocalDate().toEpochDay() / 7 }
                .mapValues { (_, values) -> values.mapNotNull { it.index }.average() }
        }
        return weeklyByExercise.values.flatMap { it.keys }.distinct().sorted().mapNotNull { week ->
            val values = weeklyByExercise.values.mapNotNull { it[week] }
            if (values.isEmpty()) null else AggregateProgressPoint(
                date = java.time.LocalDate.ofEpochDay(week * 7 + 6).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
                averageIndex = values.average(),
                exerciseCount = values.size,
            )
        }
    }

    fun valid(s: WorkoutSetEntity): Boolean = s.setType == "WORKING" && s.result == "COMPLETED" && s.romValue == null && ((s.reps ?: 0.0)>0.0 || (s.holdMillis ?: 0)>0 || (s.timeUnderTensionMillis ?: 0)>0 || (s.leftReps ?: 0.0)>0.0 || (s.rightReps ?: 0.0)>0.0 || (s.leftHoldMillis ?: 0)>0 || (s.rightHoldMillis ?: 0)>0)
    fun score(s: WorkoutSetEntity, bodyweight: Double?, width: Double, trackingType: String? = null): Double? {
        if(!valid(s)) return null
        val reps = s.reps ?: listOfNotNull(s.leftReps,s.rightReps).minOrNull() ?: 0.0
        val hold = s.holdMillis ?: s.timeUnderTensionMillis ?: listOfNotNull(s.leftHoldMillis,s.rightHoldMillis).minOrNull()
        if(hold != null && hold>0) return ProgressScorer.assistedHold(hold,width)
        if(width>0 || trackingType=="ASSISTED_REPS") {
            val loadRatio=if(bodyweight!=null && bodyweight>0) ((bodyweight+(s.addedWeightKg ?: 0.0)-(s.assistanceKg ?: 0.0))/bodyweight).coerceAtLeast(0.0) else 1.0
            return ProgressScorer.assistedReps(reps,width)*loadRatio
        }
        val load = s.weightKg ?: bodyweight?.let { (it+(s.addedWeightKg ?: 0.0)-(s.assistanceKg ?: 0.0)).coerceAtLeast(0.0) }
        return if(load != null && load>0) ProgressScorer.weightedReps(load,reps) else reps
    }
    /** Assistance is normalised only within a variation. Never compare band force between movements. */
    fun points(exerciseId:String, workouts:List<WorkoutEntity>, exercises:List<WorkoutExerciseEntity>, sets:List<WorkoutSetEntity>, links:List<WorkoutSetBandEntity>, bands:List<BandEntity>, filter:String?=null, variations:List<ExerciseVariationEntity> = emptyList()):List<ProgressPoint> {
        val raw=workouts.filter { it.status=="FINISHED" }.sortedBy { it.finishedAt }.mapNotNull { w ->
            val rows=exercises.filter { it.workoutId==w.id && it.actualExerciseId==exerciseId }
            val results=sets.filter { s -> rows.any { it.id==s.workoutExerciseId } && (filter==null || (s.variationId ?: "default")==filter) }
            results.mapNotNull { s ->
                val used=links.filter { it.setId==s.id }
                val width=used.sumOf { link -> link.widthCentimetresSnapshot.takeIf { it > 0 } ?: bands.find { it.id==link.bandId }?.widthCentimetres ?: 0.0 }
                val names=used.map { link -> link.nameSnapshot.ifBlank { bands.find { it.id==link.bandId }?.name.orEmpty() } }
                score(s,w.bodyweightKg,width,rows.find { it.id==s.workoutExerciseId }?.trackingType)?.let { ProgressPoint(w.finishedAt ?: w.startedAt,it,s,rows.find { it.id==s.workoutExerciseId }?.notes.orEmpty(),s.variationId,names,rank=s.variationRankSnapshot ?: variations.find { it.id==s.variationId }?.progressionRank ?: 0) }
            }.maxWithOrNull(compareBy<ProgressPoint> { it.rank }.thenBy { it.score })
        }
        val skillLevels=raw.mapNotNull { it.variation?.let { id -> id to it.rank } }.distinct().sortedBy { it.second }.map { it.first }
        val sessionScores=raw.map { point ->
            if(filter!=null || skillLevels.isEmpty()) point.score
            else {
                val peers=raw.filter { it.variation==point.variation }.take(3)
                val reference=peers.map { it.score }.average().coerceAtLeast(0.000001)
                val relative=point.score/reference
                val level=(skillLevels.indexOf(point.variation)+1).coerceAtLeast(1)
                // Ordered level dominates; duration/assistance contributes only relative to the same variation.
                level*100.0+50.0*relative/(1.0+relative)
            }
        }
        val indexed=raw.mapIndexed { i,point -> point.copy(index=ProgressScorer.progressIndex(sessionScores[i],sessionScores.take(3))) }
        return indexed.mapIndexed { i,p -> val values=indexed.take(i+1).takeLast(3).mapNotNull { it.index }; p.copy(trend=if(values.size==3)values.average() else null) }
    }

    fun records(sets: List<WorkoutSetEntity>, points: List<ProgressPoint>) = PersonalRecords(
        weightKg = sets.mapNotNull { it.weightKg }.maxOrNull(),
        holdMillis = sets.flatMap { listOfNotNull(it.holdMillis, it.timeUnderTensionMillis, it.leftHoldMillis, it.rightHoldMillis) }.maxOrNull(),
        estimatedOneRepMaxKg = sets.filter { it.weightKg != null && it.reps != null }
            .maxOfOrNull { it.weightKg!! * (1 + it.reps!! / 30.0) },
        scoredPerformance = points.maxByOrNull { it.score },
    )

    fun recordVisibility(trackingType: String?) = PersonalRecordVisibility(
        weight = trackingType == "WEIGHT_REPS",
        hold = trackingType == "HOLD" || trackingType == "SKILL_HOLD",
        estimatedOneRepMax = trackingType == "WEIGHT_REPS",
    )

    fun romSeries(sets: List<WorkoutSetEntity>): Map<String, List<WorkoutSetEntity>> =
        sets.filter { it.romValue != null }
            .groupBy { it.romUnit?.trim().orEmpty().ifBlank { "unspecified" } }
            .mapValues { (_, values) -> values.sortedBy { it.loggedAt } }
}

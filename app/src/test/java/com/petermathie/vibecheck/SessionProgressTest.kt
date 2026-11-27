package com.petermathie.vibecheck

import com.petermathie.vibecheck.domain.progress.SessionProgress
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.MeasurementMetric
import com.petermathie.vibecheck.domain.workout.parsePerformance
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.ui.emptySet
import org.junit.Test
import org.junit.Assert.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SessionProgressTest {
    private fun finished(id: String, time: Long) =
        WorkoutEntity(id, null, id, "STRENGTH", "FINISHED", time, time, "", 80.0, false)

    private fun row(id: String, workout: String, exercise: String = "bench") =
        WorkoutExerciseEntity(id, workout, exercise, exercise, 0, "", 60, null, trackingType = "WEIGHT_REPS")

    @Test fun dailyE1rmUsesTheHighestEstimateAcrossAllSetsAndWorkoutsNotVariationRank() {
        val zone = ZoneId.of("UTC")
        val day = LocalDate.of(2026, 11, 20)
        val time = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val workouts = listOf(finished("morning", time), finished("evening", time + 36_000_000),
            finished("draft", time).copy(status = "IN_PROGRESS"))
        val rows = listOf(row("am", "morning"), row("pm", "evening"), row("draft-row", "draft"),
            row("other", "morning", "squat"))
        val sets = listOf(
            emptySet("am", 1).copy(weightKg = 100.0, reps = 1.0),
            emptySet("am", 2).copy(weightKg = 80.0, reps = 10.0),
            emptySet("pm", 1).copy(weightKg = 90.0, reps = 8.0),
            emptySet("am", 3).copy(weightKg = 50.0, reps = 6.0, variationId = "paused", variationRankSnapshot = 9),
            emptySet("am", 4).copy(weightKg = 300.0, reps = 20.0, setType = "WARM_UP"),
            emptySet("pm", 2).copy(weightKg = 300.0, reps = 20.0, result = "FAILED"),
            emptySet("draft-row", 1).copy(weightKg = 300.0, reps = 20.0),
            emptySet("other", 1).copy(weightKg = 300.0, reps = 20.0),
        )
        val daily = SessionProgress.dailyEstimatedOneRepMax("bench", workouts, rows, sets, zone = zone).single()
        assertEquals(day.atStartOfDay(zone).toInstant().toEpochMilli(), daily.date)
        assertEquals(114.0, daily.kilograms, 0.0001)
        assertEquals(60.0, SessionProgress.dailyEstimatedOneRepMax(
            "bench", workouts, rows, sets, filter = "paused", zone = zone,
        ).single().kilograms, 0.0001)
    }

    @Test fun dailyE1rmGroupsByTheViewingLocalDayIncludingTheDaylightSavingBoundary() {
        val zone = ZoneId.of("America/New_York")
        val times = listOf("2026-11-01T03:30:00Z", "2026-11-01T04:30:00Z", "2026-11-02T04:30:00Z")
            .map { Instant.parse(it).toEpochMilli() }
        val workouts = times.mapIndexed { index, time -> finished("w$index", time) }
        val rows = workouts.mapIndexed { index, workout -> row("r$index", workout.id) }
        val sets = rows.mapIndexed { index, row -> emptySet(row.id, 1).copy(weightKg = 60.0 + index * 10, reps = 6.0) }
        val daily = SessionProgress.dailyEstimatedOneRepMax("bench", workouts, rows, sets, zone = zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1)),
            daily.map { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() })
        assertEquals(listOf(72.0, 96.0), daily.map { it.kilograms })
        assertEquals(Instant.parse("2026-11-01T04:00:00Z").toEpochMilli(), daily.last().date)
    }

    @Test fun dailyE1rmRemainsRawAfterThreeDaysAndItsTrendIsCalculatedBeforeRangeFiltering() {
        val zone = ZoneId.of("UTC")
        val workouts = (0..3).map { index ->
            finished("w$index", LocalDate.of(2026, 11, 1).plusDays(index.toLong()).atStartOfDay(zone).toInstant().toEpochMilli())
        }
        val rows = workouts.mapIndexed { index, workout -> row("r$index", workout.id) }
        val sets = rows.mapIndexed { index, row -> emptySet(row.id, 1).copy(weightKg = 60.0 + index * 10, reps = 6.0) }
        val original = SessionProgress.points("bench", workouts, rows, sets, emptyList(), emptyList())
        val daily = SessionProgress.dailyEstimatedOneRepMax("bench", workouts, rows, sets, zone = zone)
        assertEquals(listOf(72.0, 84.0, 96.0, 108.0), daily.map { it.kilograms })
        assertNull(daily[1].trendKilograms)
        assertEquals(84.0, daily[2].trendKilograms!!, 0.0001)
        assertEquals(96.0, daily.filter { it.date == daily.last().date }.single().trendKilograms!!, 0.0001)
        assertEquals(original, SessionProgress.points("bench", workouts, rows, sets, emptyList(), emptyList()))
    }

    @Test fun dailyE1rmUsesBilateralAndFractionalRepsAndExcludesUnusableMeasurements() {
        val workout = finished("w", 1)
        val row = row("r", "w")
        val base = emptySet("r", 1).copy(weightKg = 60.0, reps = 4.5)
        val bilateral = base.copy(ordinal = 2, reps = null, leftReps = 5.0, rightReps = 4.0)
        val unusable = listOf(
            base.copy(weightKg = null), base.copy(weightKg = 0.0),
            base.copy(weightKg = Double.POSITIVE_INFINITY), base.copy(weightKg = -60.0),
            base.copy(reps = 0.0), base.copy(reps = Double.NaN), base.copy(romValue = 20.0),
        )
        assertTrue(SessionProgress.dailyEstimatedOneRepMax("bench", listOf(workout), listOf(row), unusable).isEmpty())
        assertEquals(69.0, SessionProgress.dailyEstimatedOneRepMax(
            "bench", listOf(workout), listOf(row), listOf(base, bilateral) + unusable,
        ).single().kilograms, 0.0001)
        assertEquals(68.0, SessionProgress.dailyEstimatedOneRepMax(
            "bench", listOf(workout), listOf(row), listOf(bilateral),
        ).single().kilograms, 0.0001)
    }

    @Test fun zeroAndWarmupsAreExcluded(){
        val set=emptySet("e",1).copy(weightKg=100.0,reps=0.0)
        assertNull(SessionProgress.score(set,80.0,0.0))
        assertNull(SessionProgress.score(set.copy(reps=5.0,setType="WARM_UP"),80.0,0.0))
    }
    @Test fun stacksReduceDifficultyWithinVariation(){
        val set=emptySet("e",1).copy(holdMillis=10000)
        assertTrue(SessionProgress.score(set,null,0.6)!! > SessionProgress.score(set,null,3.7)!!)
    }
    @Test fun compactWeightedEntryConvertsPounds(){
        val set=parsePerformance(emptySet("e",1),"220.46226218 x 5",false,true,true)!!
        assertEquals(100.0,set.weightKg!!,0.00001)
        assertEquals(5.0,set.reps)
        assertNull(parsePerformance(set,"100 x -2",false,true,false))
    }
    @Test fun removingLastBandKeepsTheSameAssistanceScale(){
        val set=emptySet("e",1).copy(reps=5.0)
        val band=SessionProgress.score(set,80.0,0.6,"ASSISTED_REPS")!!
        val free=SessionProgress.score(set,80.0,0.0,"ASSISTED_REPS")!!
        assertEquals(1.6,free/band,0.00001)
        assertTrue(SessionProgress.score(set.copy(addedWeightKg=10.0),80.0,0.0,"ASSISTED_REPS")!!>free)
    }
    @Test fun romSeriesNeverMixUnits(){
        val sets=listOf(
            emptySet("e",1).copy(romValue=10.0,romUnit="cm",loggedAt=2),
            emptySet("e",2).copy(romValue=30.0,romUnit="degrees",loggedAt=1),
            emptySet("e",3).copy(romValue=12.0,romUnit="cm",loggedAt=3),
        )
        val series=SessionProgress.romSeries(sets)
        assertEquals(setOf("cm","degrees"),series.keys)
        assertEquals(listOf(10.0,12.0),series.getValue("cm").map { it.romValue })
    }
    @Test fun distanceSeriesStaySeparateFromRomAndDoNotProduceStrengthScores() {
        val rom = emptySet("e", 1).copy(romValue = 10.0, romUnit = "cm", loggedAt = 1)
        val distance = emptySet("e", 2).copy(distanceValue = 18.125, distanceUnit = "cm", loggedAt = 2, reps = 5.0)
        assertEquals(listOf(rom), SessionProgress.romSeries(listOf(rom, distance)).getValue("cm"))
        assertEquals(listOf(distance), SessionProgress.distanceSeries(listOf(rom, distance)).getValue("cm"))
        assertNull(SessionProgress.score(distance, 80.0, 0.0))
    }
    @Test fun recordedPrimaryMetricExcludesRetainedLegacyResultsEvenWhenItsValueWasCleared() {
        val workout = finished("w", 1)
        val row = row("r", "w")
        val legacy = emptySet("r", 1).copy(weightKg = 60.0, reps = 5.0, holdMillis = 10_000)
        listOf(
            MeasurementMetric.DISTANCE,
            MeasurementMetric.RANGE_OF_MOTION,
        ).forEach { metric ->
            val config = ExerciseInputConfig().withMeasurement(metric).encode()
            val recorded = legacy.copy(variationInputConfigSnapshot = config)
            assertNull(SessionProgress.score(recorded, 80.0, 0.0))
            assertTrue(SessionProgress.points("bench", listOf(workout), listOf(row), listOf(recorded),
                emptyList(), emptyList()).isEmpty())
            assertTrue(SessionProgress.dailyEstimatedOneRepMax("bench", listOf(workout), listOf(row), listOf(recorded)).isEmpty())
            val fallback = row.copy(inputConfig = config)
            assertNull(SessionProgress.score(legacy, 80.0, 0.0, fallback.trackingType, fallback.inputConfig))
            assertTrue(SessionProgress.points("bench", listOf(workout), listOf(fallback), listOf(legacy),
                emptyList(), emptyList()).isEmpty())
            assertTrue(SessionProgress.dailyEstimatedOneRepMax("bench", listOf(workout), listOf(fallback), listOf(legacy)).isEmpty())
            val recordedReps = legacy.copy(
                variationInputConfigSnapshot = ExerciseInputConfig().withMeasurement(MeasurementMetric.REPETITIONS).encode(),
            )
            assertNotNull(SessionProgress.score(recordedReps, 80.0, 0.0, fallback.trackingType, fallback.inputConfig))
            assertEquals(60.0, recorded.weightKg!!, 0.0)
            assertEquals(5.0, recorded.reps!!, 0.0)
            assertEquals(10_000L, recorded.holdMillis)
        }
        assertNotNull(SessionProgress.score(legacy, 80.0, 0.0))
        assertFalse(SessionProgress.dailyEstimatedOneRepMax("bench", listOf(workout), listOf(row), listOf(legacy)).isEmpty())
    }
    @Test fun recordsExposeHighestScoredPerformanceInsteadOfRepetitionCount(){
        val lowerScore=emptySet("e",1).copy(weightKg=50.0,reps=12.0)
        val higherScore=emptySet("e",2).copy(weightKg=100.0,reps=3.0)
        val points=listOf(
            com.petermathie.vibecheck.domain.progress.ProgressPoint(1,60.0,lowerScore,"",null,emptyList()),
            com.petermathie.vibecheck.domain.progress.ProgressPoint(2,110.0,higherScore,"",null,emptyList()),
        )
        val records=SessionProgress.records(listOf(lowerScore,higherScore),points)
        assertEquals(higherScore.id,records.scoredPerformance?.performance?.id)
        assertEquals(110.0,records.scoredPerformance?.score!!,0.0001)
        assertEquals(110.0,records.estimatedOneRepMaxKg!!,0.0001)
    }
    @Test fun recordVisibilityFollowsExerciseTrackingType(){
        assertEquals(
            com.petermathie.vibecheck.domain.progress.PersonalRecordVisibility(true,false,true),
            SessionProgress.recordVisibility("WEIGHT_REPS"),
        )
        assertEquals(
            com.petermathie.vibecheck.domain.progress.PersonalRecordVisibility(false,true,false),
            SessionProgress.recordVisibility("SKILL_HOLD"),
        )
        assertEquals(
            com.petermathie.vibecheck.domain.progress.PersonalRecordVisibility(false,false,false),
            SessionProgress.recordVisibility("ASSISTED_REPS"),
        )
    }
    @Test fun savedVariationRankAndBandDefinitionRemainHistorical(){
        val workout=WorkoutEntity("w",null,"Workout","STRENGTH","FINISHED",1,2,"",80.0,false)
        val exercise=WorkoutExerciseEntity("we","w","e","e",0,"",60,null,"Exercise","ASSISTED_REPS","")
        val set=emptySet("we",1).copy(reps=5.0,variationId="variation",variationRankSnapshot=2)
        val link=WorkoutSetBandEntity(set.id,"band",0,"Original",0.6)
        val changedBand=BandEntity("band","Changed",3.1,0)
        val changedVariation=ExerciseVariationEntity("variation","e","Variation",9,false)

        val point=SessionProgress.points("e",listOf(workout),listOf(exercise),listOf(set),listOf(link),listOf(changedBand),variations=listOf(changedVariation)).single()

        assertEquals(2,point.rank)
        assertEquals(listOf("Original"),point.bands)
        assertEquals(SessionProgress.score(set,80.0,0.6,"ASSISTED_REPS"),point.score)
    }
    @Test fun aggregateAveragesNormalizedExerciseScoresByWeek(){
        fun point(day: Long, index: Double) = com.petermathie.vibecheck.domain.progress.ProgressPoint(
            date = java.time.LocalDate.ofEpochDay(day).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            score = index,
            performance = emptySet("e", day.toInt()),
            notes = "",
            variation = null,
            bands = emptyList(),
            index = index,
        )
        val aggregate = SessionProgress.aggregate(
            mapOf(
                "improving" to listOf(point(0, 100.0), point(7, 120.0)),
                "declining" to listOf(point(0, 100.0), point(7, 90.0)),
                "raw-only" to listOf(point(7, 80.0).copy(index = null)),
            ),
        )
        assertEquals(2, aggregate.size)
        assertEquals(100.0, aggregate[0].averageIndex, 0.0001)
        assertEquals(105.0, aggregate[1].averageIndex, 0.0001)
        assertEquals(2, aggregate[1].exerciseCount)
    }
}

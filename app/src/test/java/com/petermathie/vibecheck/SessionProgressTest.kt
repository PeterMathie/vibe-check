package com.petermathie.vibecheck

import com.petermathie.vibecheck.domain.progress.SessionProgress
import com.petermathie.vibecheck.domain.workout.parsePerformance
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.ui.emptySet
import org.junit.Test
import org.junit.Assert.*

class SessionProgressTest {
    @Test fun zeroAndWarmupsAreExcluded(){
        val set=emptySet("e",1).copy(weightKg=100.0,reps=0.0)
        assertNull(SessionProgress.score(set,80.0,0.0))
        assertNull(SessionProgress.score(set.copy(reps=5.0,setType="WARM_UP"),80.0,0.0))
    }
    @Test fun stacksReduceDifficultyWithinVariation(){
        val set=emptySet("e",1).copy(holdMillis=10000)
        assertTrue(SessionProgress.score(set,null,0.6)!! > SessionProgress.score(set,null,3.7)!!)
    }
    @Test fun totalTimeOnlySetQualifiesForCompletionAndProgress(){
        val set=emptySet("e",1).copy(timeUnderTensionMillis=18_000)
        assertTrue(SessionProgress.valid(set))
        assertEquals(18.0, SessionProgress.score(set,null,0.0)!!, 0.00001)
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

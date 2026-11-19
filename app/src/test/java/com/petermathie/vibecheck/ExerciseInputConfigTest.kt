package com.petermathie.vibecheck

import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.ExerciseMeasurement
import com.petermathie.vibecheck.domain.programme.MeasurementMetric
import com.petermathie.vibecheck.domain.programme.ResistanceMetric
import org.junit.Assert.*
import org.junit.Test

class ExerciseInputConfigTest {
    @Test
    fun allTwelveMetricPairsRoundTripAsExclusiveConfigurations() {
        ResistanceMetric.entries.forEach { resistance ->
            MeasurementMetric.entries.forEach { measurement ->
                val config = ExerciseInputConfig().withResistance(resistance).withMeasurement(measurement)
                val restored = ExerciseInputConfig.decode(config.encode(), "REPS")
                assertEquals(config, restored)
                assertEquals(resistance, restored.loggingResistance())
                assertEquals(measurement, restored.loggingMeasurement())
            }
        }
    }

    @Test
    fun ambiguousLegacyFlagsRequireSelectionInsteadOfDroppingAMetric() {
        val legacy = ExerciseInputConfig(reps = true, timeHeld = true, bandResistance = true, weightUnit = "kg")
        val restored = ExerciseInputConfig.decode(legacy.encode(), "REPS")
        assertNull(restored.loggingResistance())
        assertNull(restored.loggingMeasurement())
        assertEquals(legacy, restored)
    }

    @Test
    fun legacySkillHoldAndAddedWeightRetainTheirBackingMeasurements() {
        val skill = ExerciseInputConfig.decode(
            "timeHeld=true;timeUnderTension=true;reps=false", "SKILL_HOLD", defaultManyTuts = true,
        )
        assertEquals(MeasurementMetric.SKILL_HOLD, skill.loggingMeasurement())
        val weighted = ExerciseInputConfig(bodyweight = true, reps = true)
            .withResistance(ResistanceMetric.KILOGRAMS)
        assertTrue(weighted.addedWeight)
        assertNull(weighted.weightUnit)
        assertEquals(ResistanceMetric.KILOGRAMS, weighted.loggingResistance())
    }

    @Test
    fun invalidExplicitMetricChoicesAreRejected() {
        listOf(
            "resistanceMetric=UNKNOWN",
            "measurementMetric=UNKNOWN",
            "resistanceMetric=BANDS;resistanceMetric=BODYWEIGHT",
            "measurementMetric=HOLD;measurementMetric=REPETITIONS",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                ExerciseInputConfig.decode(value, "REPS")
            }

        }
    }

    @Test
    fun choosingTheSameHoldTypeDoesNotConvertLegacyTotalTimeIntoHeldTime() {
        val config = ExerciseInputConfig(timeUnderTension = true)
            .withMeasurement(MeasurementMetric.HOLD)
        assertFalse(config.timeHeld)
        assertTrue(config.timeUnderTension)
        assertEquals(MeasurementMetric.HOLD, config.loggingMeasurement())
    }

    @Test
    fun passiveBodyweightMetadataDoesNotConvertAnExistingLoadIntoAddedWeight() {
        val config = ExerciseInputConfig(weightUnit = "lb", bodyweight = true, reps = true)
            .withResistance(ResistanceMetric.KILOGRAMS, "lb")
        assertEquals("lb", config.weightUnit)
        assertFalse(config.addedWeight)
    }

    @Test
    fun explicitMetricSelectionControlsResistanceWithoutDiscardingLegacyFlags() {
        val kilograms = ExerciseInputConfig.decode("resistanceMetric=KILOGRAMS;measurementMetric=REPETITIONS", "HOLD")
        assertEquals("kg", kilograms.loggingWeightUnit)
        assertEquals(MeasurementMetric.REPETITIONS, kilograms.loggingMeasurement())
        val bodyweight = ExerciseInputConfig.decode("resistanceMetric=BODYWEIGHT;weightUnit=lb", "WEIGHT_REPS")
        assertNull(bodyweight.loggingWeightUnit)
        assertEquals("lb", bodyweight.weightUnit)
    }

    @Test
    fun orderingRoundTripsAndUnlistedFieldsKeepTheirDefaults() {
        val config = ExerciseInputConfig(
            weightUnit = "kg", reps = true,
            measurementOrder = listOf(ExerciseMeasurement.RPE, ExerciseMeasurement.REPS),
        )
        val restored = ExerciseInputConfig.decode(config.encode(), "WEIGHT_REPS")
        assertEquals(config, restored)
        assertEquals(
            listOf(ExerciseMeasurement.RPE, ExerciseMeasurement.REPS, ExerciseMeasurement.WEIGHT),
            restored.enabledMeasurements(),
        )
        assertEquals(
            listOf(ExerciseMeasurement.WEIGHT, ExerciseMeasurement.REPS, ExerciseMeasurement.RPE),
            ExerciseInputConfig.decode("weightUnit=kg;reps=true", "WEIGHT_REPS").enabledMeasurements(),
        )
    }

    @Test
    fun omittedVariantOrderInheritsButExplicitOrderOverrides() {
        val inherited = listOf(ExerciseMeasurement.RPE, ExerciseMeasurement.REPS)
        assertEquals(inherited, ExerciseInputConfig.decode(
            "reps=true", "REPS", defaultMeasurementOrder = inherited,
        ).measurementOrder)
        assertEquals(listOf(ExerciseMeasurement.REPS), ExerciseInputConfig.decode(
            "reps=true;measurementOrder=reps", "REPS", defaultMeasurementOrder = inherited,
        ).measurementOrder)
    }

    @Test
    fun invalidOrDuplicateOrderIsRejectedRatherThanSilentlyChanged() {
        listOf(
            "measurementOrder=reps,reps",
            "measurementOrder=unknown",
            "measurementOrder=",
            "measurementOrder=reps;measurementOrder=rpe",
        ).forEach { value ->
            assertThrows(IllegalArgumentException::class.java) {
                ExerciseInputConfig.decode(value, "REPS")
            }
        }
    }
}

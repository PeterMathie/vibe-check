package com.petermathie.vibecheck

import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.ExerciseMeasurement
import org.junit.Assert.*
import org.junit.Test

class ExerciseInputConfigTest {
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

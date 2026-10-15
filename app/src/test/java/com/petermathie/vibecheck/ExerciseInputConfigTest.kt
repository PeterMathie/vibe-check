package com.petermathie.vibecheck

import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseInputConfigTest {
    @Test
    fun repeatedTimeConfigurationRoundTripsAndOldConfigsRemainCompatible() {
        val config = ExerciseInputConfig(
            timeUnderTension = true,
            repeatedTimeMeasurements = true,
        )

        assertEquals(config, ExerciseInputConfig.decode(config.encode(), "SKILL_HOLD"))
        assertFalse(
            ExerciseInputConfig.decode(
                "weightUnit=;timeHeld=false;timeUnderTension=true;reps=false",
                "SKILL_HOLD",
            ).repeatedTimeMeasurements,
        )
        assertTrue(ExerciseInputConfig.defaults("SKILL_HOLD").timeHeld)
    }
}

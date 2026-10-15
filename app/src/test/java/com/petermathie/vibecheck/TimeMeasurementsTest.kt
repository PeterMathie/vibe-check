package com.petermathie.vibecheck

import com.petermathie.vibecheck.domain.workout.decodeTimeMeasurementInputs
import com.petermathie.vibecheck.domain.workout.decodeTimeMeasurementsMillis
import com.petermathie.vibecheck.domain.workout.encodeTimeMeasurementInputs
import com.petermathie.vibecheck.domain.workout.encodeTimeMeasurementsMillis
import com.petermathie.vibecheck.domain.workout.parseTimeMeasurementsMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeMeasurementsTest {
    @Test
    fun inputAndPersistedMeasurementsRoundTripWithoutLosingOrder() {
        val inputs = listOf("8.25", "10", "6.5")
        val millis = listOf(8_250L, 10_000L, 6_500L)

        assertEquals(inputs, decodeTimeMeasurementInputs(encodeTimeMeasurementInputs(inputs)))
        assertEquals(millis, parseTimeMeasurementsMillis(encodeTimeMeasurementInputs(inputs)))
        assertEquals(millis, decodeTimeMeasurementsMillis(encodeTimeMeasurementsMillis(millis)))
    }

    @Test
    fun transientOrInvalidMeasurementDoesNotProduceASetValue() {
        assertNull(parseTimeMeasurementsMillis("[\"8\",\"\"]"))
        assertNull(parseTimeMeasurementsMillis("[\"8\",\"-1\"]"))
    }
}

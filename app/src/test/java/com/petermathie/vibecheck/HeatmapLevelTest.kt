package com.petermathie.vibecheck

import com.petermathie.vibecheck.ui.formatBodyweight
import org.junit.Assert.assertEquals
import org.junit.Test

class HeatmapLevelTest {
    @Test
    fun bodyweightAlwaysDisplaysTwoDecimalPlaces() {
        assertEquals("78.00", formatBodyweight(78.0))
        assertEquals("74.35", formatBodyweight(74.345))
    }
}

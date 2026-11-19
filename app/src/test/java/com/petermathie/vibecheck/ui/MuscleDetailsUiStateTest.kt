package com.petermathie.vibecheck.ui

import androidx.compose.ui.graphics.Color
import com.petermathie.vibecheck.domain.model.MuscleRecency
import com.petermathie.vibecheck.domain.model.MuscleRecencyBand
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.ui.components.muscleDetailsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleDetailsUiStateTest {
    @Test
    fun homeElapsedTimeUsesNaturalRoundedUnitsAtTheViewingAnchor() {
        val asOf = 1_800_000_000_000L
        assertEquals("about 5 days ago", homeMuscleElapsedLabel(asOf - (4 * 86_400L + 23 * 3_600 + 2 * 60) * 1_000, asOf))
        assertEquals("about an hour ago", homeMuscleElapsedLabel(asOf - 3_600_000, asOf))
        assertEquals("about 2 minutes ago", homeMuscleElapsedLabel(asOf - 90_000, asOf))
        assertEquals("about an hour ago", homeMuscleElapsedLabel(asOf - 3_580_000, asOf))
        assertEquals("about a day ago", homeMuscleElapsedLabel(asOf - (23 * 3_600L + 40 * 60) * 1_000, asOf))
        assertEquals("about a week ago", homeMuscleElapsedLabel(asOf - 7 * 86_400_000L, asOf))
        assertEquals("about a month ago", homeMuscleElapsedLabel(asOf - 30 * 86_400_000L, asOf))
        assertEquals("about a year ago", homeMuscleElapsedLabel(asOf - 365 * 86_400_000L, asOf))
        assertEquals("just now", homeMuscleElapsedLabel(asOf - 42_000, asOf))
        assertEquals("No recorded training", homeMuscleElapsedLabel(null, asOf))
    }

    @Test
    fun mapsStrengthFactsWithoutInventingActions() {
        val state = muscleDetailsUiState(
            "CHEST",
            MuscleRecency("CHEST", 1_700_000_000_000, MuscleRecencyBand.UNDER_24_HOURS, listOf("Push up"), 3.5),
            TrainingMode.STRENGTH,
            Color.Red,
        )
        assertEquals("Chest Freshness details", state.title)
        assertEquals("Under 24 hours", state.bandLabel)
        assertEquals("3.5 set-equivalents in 7 days", state.doseLabel)
        assertEquals(listOf("Push up"), state.contributions)
    }

    @Test
    fun mapsStretchNoDataTruthfully() {
        val state = muscleDetailsUiState("HAMSTRINGS", null, TrainingMode.STRETCHING, Color.Gray)
        assertEquals("Hamstrings Stretch details", state.title)
        assertEquals("No data", state.bandLabel)
        assertTrue(state.lastEventLabel.contains("Last stretched: No data"))
    }
}

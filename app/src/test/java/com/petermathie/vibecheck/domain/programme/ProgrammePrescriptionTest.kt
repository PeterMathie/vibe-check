package com.petermathie.vibecheck.domain.programme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.petermathie.vibecheck.ui.ProgrammePrescriptionInputs
import org.junit.Test

class ProgrammePrescriptionTest {
    @Test
    fun inlineSetsAreOptionalButRestRequiresNonnegativeWholeSeconds() {
        val cleared = ProgrammePrescriptionInputs("", "0")
        assertTrue(cleared.valid)
        assertNull(cleared.setCount)
        assertEquals(0, cleared.restSeconds)
        assertFalse(ProgrammePrescriptionInputs("", "").valid)
        assertTrue(ProgrammePrescriptionInputs(" 4 ", "90").valid)
    }

    @Test
    fun invalidInlineValuesRemainUnchangedAndCannotBeSaved() {
        listOf("0", "-1", "2.5", "NaN", "2147483648").forEach { text ->
            val input = ProgrammePrescriptionInputs(text, "60")
            assertFalse(input.valid)
            assertEquals(text, input.sets)
        }
        listOf("-1", "1.5", "Infinity", "2147483648").forEach { text ->
            val input = ProgrammePrescriptionInputs("3", text)
            assertFalse(input.valid)
            assertEquals(text, input.rest)
        }
    }

    @Test
    fun summaryUsesAssignmentTargets() {
        assertEquals(
            "4 sets · 6–10 reps · RPE 8.5",
            prescriptionSummary(4, 6, 10, null, 8.5),
        )
        assertEquals(
            "2 sets · 30 seconds · RPE 7",
            prescriptionSummary(2, null, null, 30, 7.0),
        )
    }
}

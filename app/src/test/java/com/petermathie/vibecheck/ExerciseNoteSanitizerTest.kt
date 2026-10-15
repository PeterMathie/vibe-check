package com.petermathie.vibecheck

import com.petermathie.vibecheck.data.sanitizeExerciseNotes
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseNoteSanitizerTest {
    @Test
    fun removesOnlyKnownProgrammeProvenanceLines() {
        val leaked =
            "Private programme source 2f55a8f5-5878-4295-aa5d-22db384a9338-2. LEGS + MOBILITY.md, line 20. " +
                "Explicit rest: 2 minutes; achieved added load/reps were not promoted."

        assertEquals("", sanitizeExerciseNotes(leaked))
        assertEquals(
            "Keep hips square\nUser-authored cue",
            sanitizeExerciseNotes("Keep hips square\n$leaked\nUser-authored cue"),
        )
        assertEquals(
            "Private programme source is useful wording in my own note",
            sanitizeExerciseNotes("Private programme source is useful wording in my own note"),
        )
    }
}

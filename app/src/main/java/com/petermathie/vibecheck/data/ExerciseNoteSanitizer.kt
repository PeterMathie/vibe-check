package com.petermathie.vibecheck.data

private val leakedProgrammeSource = Regex(
    """^Private programme source .+\.md, line \d+\.\s*(?:Explicit rest: .+;\s*achieved added load/reps were not promoted\.)?$""",
    RegexOption.IGNORE_CASE,
)

const val FRONT_SPLIT_HOLD_INPUT_CONFIG =
    "weightUnit=;bandResistance=false;timeHeld=true;timeUnderTension=false;reps=false;bodyweight=false;addedWeight=false"

fun sanitizeExerciseNotes(notes: String): String =
    notes.lineSequence()
        .filterNot { leakedProgrammeSource.matches(it.trim()) }
        .joinToString("\n")
        .trim()

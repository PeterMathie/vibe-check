package com.petermathie.vibecheck.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HabitHeadingTest {
    @Test
    fun splittingAWordReservesSpaceForAHyphenWithinTheCharacterLimit() {
        assertEquals("i".repeat(18), habitHeadingLabel("i".repeat(18)))
        assertEquals("i".repeat(17) + "-\nii", habitHeadingLabel("i".repeat(19)))
        assertEquals("i".repeat(18) + "\nj", habitHeadingLabel("i".repeat(18) + " j"))
    }

    @Test
    fun wordsWrapWithoutChangingTheNameOrHidingOverflowFromEllipsis() {
        val name = "Daily deliberate practice and reflection"
        assertEquals("Daily deliberate\npractice and\nreflection", habitHeadingLabel(name))
        assertEquals(name, habitHeadingLabel(name).replace('\n', ' '))
    }

    @Test
    fun longWordsWrapWithoutSplittingSupplementaryCharacters() {
        val character = "\uD83D\uDE42"
        assertEquals(character.repeat(17) + "-\n" + character.repeat(2), habitHeadingLabel(character.repeat(19)))
    }

    @Test
    fun anExistingHyphenIsNotDoubledAtTheLineBreak() {
        assertEquals("i".repeat(17) + "-\nii", habitHeadingLabel("i".repeat(17) + "-ii"))
    }
}

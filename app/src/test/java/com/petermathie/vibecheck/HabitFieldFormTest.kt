package com.petermathie.vibecheck

import com.petermathie.vibecheck.data.local.TrackerFieldEntity
import com.petermathie.vibecheck.domain.tracker.HabitFieldForm
import com.petermathie.vibecheck.domain.tracker.HabitChoiceIntensity
import com.petermathie.vibecheck.domain.tracker.HabitChoiceOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitFieldFormTest {
    private val field = TrackerFieldEntity(
        id = "field",
        trackerId = "tracker",
        name = "Minutes",
        valueType = "NUMBER",
        unit = "min",
        targetComparison = "AT_LEAST",
        targetValue = 20.0,
        position = 3,
        targetMaxValue = null,
    )

    @Test
    fun goalDefaultsToNoneAndSelectedGoalsRequireAValue() {
        val empty = field.copy(targetComparison = null, targetValue = null)
        val form = HabitFieldForm.from(empty)
        assertEquals(HabitFieldForm.NONE, form.comparison)
        assertTrue(form.canSave)
        assertNull(form.applyTo(empty).targetValue)
        assertFalse(form.copy(comparison = "AT_LEAST").canSave)
        val goal = form.copy(comparison = "AT_LEAST", target = "12")
        assertTrue(goal.canSave)
        assertEquals(12.0, goal.applyTo(empty).targetValue)
        val cleared = goal.copy(comparison = HabitFieldForm.NONE, target = "", targetMaximum = "").applyTo(field)
        assertNull(cleared.targetComparison)
        assertNull(cleared.targetValue)
        assertNull(cleared.targetMaxValue)
    }

    @Test
    fun numericRangeRoundTripsWithoutChangingIdentityOrPosition() {
        val updated = HabitFieldForm.from(field).copy(
            target = "10",
            targetMaximum = "30",
            comparison = HabitFieldForm.RANGE,
        ).applyTo(field)

        assertEquals("field", updated.id)
        assertEquals("tracker", updated.trackerId)
        assertEquals(3, updated.position)
        assertEquals(10.0, updated.targetValue ?: 0.0, 0.0)
        assertEquals(30.0, updated.targetMaxValue ?: 0.0, 0.0)
        assertEquals(HabitFieldForm.RANGE, updated.targetComparison)
    }

    @Test
    fun rangeAndNumericValidationPreserveExistingRules() {
        val form = HabitFieldForm.from(field).copy(
            target = "20",
            targetMaximum = "10",
            comparison = HabitFieldForm.RANGE,
        )

        assertFalse(form.isRangeValid)
        assertFalse(form.canSave)
        assertFalse(form.copy(target = "NaN").isTargetValid)
        assertFalse(form.copy(target = "invalid", targetMaximum = "30").isRangeValid)
        assertFalse(form.copy(target = "Infinity", targetMaximum = "30").isRangeValid)
        assertTrue(form.copy(target = "").isRangeValid)
    }

    @Test
    fun newNegativeGoalsAreRejectedWithoutBlockingUnchangedLegacyGoals() {
        val form = HabitFieldForm.from(field).copy(target = "-1")
        assertFalse(form.isValidFor(null))
        assertFalse(form.isValidFor(field))
        assertTrue(form.isValidFor(field.copy(targetValue = -1.0)))
        assertTrue(form.copy(target = "0").isValidFor(null))
        assertFalse(form.copy(target = "Infinity").isValidFor(null))
    }

    @Test
    fun choicesRequireTwoUniqueValuesAndAreNormalised() {
        val form = HabitFieldForm.from(field).copy(
            type = HabitFieldForm.CHOICE,
            options = "Good, Bad\nGood",
            choiceLightThrough = 0,
            choiceDarkFrom = 2,
        )

        assertFalse(form.areChoicesValid)
        val saved = form.copy(options = " Good, Bad \n", choiceDarkFrom = 1).applyTo(field)
        assertEquals("Good\nBad", saved.choiceOptions)
        assertNull(saved.targetValue)
        assertNull(saved.targetComparison)
    }

    @Test
    fun explicitChoicesRejectBlankOrDuplicateTrimmedLabels() {
        val choices = listOf(
            HabitChoiceOption("one", "Good", HabitChoiceIntensity.LIGHT, 0),
            HabitChoiceOption("two", " ", HabitChoiceIntensity.DARK, 0),
        )
        val form = HabitFieldForm.from(field).copy(type = HabitFieldForm.CHOICE, choices = choices)

        assertFalse(form.canSave)
        assertFalse(form.copy(choices = choices.mapIndexed { index, option ->
            option.copy(label = if (index == 0) "Good" else " Good ")
        }).canSave)
    }

    @Test
    fun legacyChoiceBoundariesBecomeExplicitBucketsAndRoundTrip() {
        val choiceField = field.copy(
            valueType = HabitFieldForm.CHOICE,
            choiceOptions = "Terrified\nLonely\nSad\nHappy\nJoyful\nSuper",
            choiceLightThrough = 2,
            choiceDarkFrom = 3,
        )
        val form = HabitFieldForm.from(choiceField)

        assertTrue(form.canSave)
        assertEquals(listOf("LIGHT", "LIGHT", "LIGHT", "DARK", "DARK", "DARK"), form.choices.map { it.intensity.name })
        val saved = form.applyTo(choiceField)
        assertEquals(2, saved.choiceLightThrough)
        assertEquals(3, saved.choiceDarkFrom)
        assertEquals(form.choices, HabitFieldForm.from(saved).choices)
    }

    @Test
    fun nonChoiceTypeClearsStaleChoicesAndTargets() {
        val saved = HabitFieldForm.from(
            field.copy(
                choiceOptions = "A\nB",
                targetMaxValue = 30.0,
                choiceLightThrough = 0,
                choiceDarkFrom = 1,
            ),
        ).copy(type = "TEXT").applyTo(field)

        assertEquals("", saved.choiceOptions)
        assertEquals("", saved.choiceOptionsJson)
        assertEquals(-1, saved.choiceLightThrough)
        assertEquals(-1, saved.choiceDarkFrom)
        assertNull(saved.targetValue)
        assertNull(saved.targetComparison)
        assertNull(saved.targetMaxValue)
    }
}

package com.petermathie.vibecheck

import com.petermathie.vibecheck.data.local.ExerciseVariationEntity
import com.petermathie.vibecheck.data.local.WorkoutExerciseEntity
import com.petermathie.vibecheck.data.local.WorkoutSetEntity
import com.petermathie.vibecheck.data.local.WorkoutSetBandEntity
import com.petermathie.vibecheck.data.local.BandEntity
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.ExerciseMeasurement
import com.petermathie.vibecheck.domain.programme.MeasurementMetric
import com.petermathie.vibecheck.domain.programme.ResistanceMetric
import com.petermathie.vibecheck.domain.workout.encodeTimingInputs
import com.petermathie.vibecheck.domain.workout.quantitativeInput
import com.petermathie.vibecheck.domain.workout.timingMillis
import com.petermathie.vibecheck.ui.*
import org.junit.Assert.*
import org.junit.Test

class WorkoutSetDraftLogicTest {
    @Test
    fun entrySelectionIncludesUntickedSavedAndPartialSetsAndSkipsExplicitLocks() {
        val first = row().copy(targetSets = 2)
        val second = first.copy(id = "second", position = 1)
        val completed = emptySet(first.id, 1)
        val autosaved = emptySet(first.id, 2).copy(holdMillis = 5_000)
        val partial = emptyEntryDraft(second.id, 1).copy(timeHeld = ".")
        val saved = listOf(completed, autosaved)

        assertEquals(WorkoutSetSelection(first.id, 2, autosaved.id),
            firstUntickedWorkoutSet(listOf(first, second), saved, listOf(partial), setOf(completed.id)))
        assertEquals(WorkoutSetSelection(second.id, 1, partial.setId),
            firstUntickedWorkoutSet(listOf(first, second), saved, listOf(partial), saved.map { it.id }.toSet()))
        assertEquals(".", partial.timeHeld)
        assertEquals(5_000L, autosaved.holdMillis)
    }

    @Test
    fun entrySelectionKeepsEmptyTargetUntilItsDraftExistsAndDoesNotInventExtraSets() {
        val first = row().copy(targetSets = 1)
        val second = first.copy(id = "second", position = 1)
        val completed = emptySet(first.id, 1)
        val target = firstUntickedWorkoutSet(listOf(first, second), listOf(completed), emptyList(), setOf(completed.id))
        assertEquals(WorkoutSetSelection(second.id, 1, null), target)
        val draft = emptyEntryDraft(second.id, 1)
        assertEquals(WorkoutSetSelection(second.id, 1, draft.setId), target?.reconcile(second.id, listOf(draft)))
        assertNull(firstUntickedWorkoutSet(listOf(first), listOf(completed), emptyList(), setOf(completed.id)))
        assertNull(firstUntickedWorkoutSet(emptyList(), emptyList(), emptyList(), emptySet()))
    }

    @Test
    fun selectionSurvivesIdentityHydrationAndPreservesExplicitActivationAndDeletionShifts() {
        val generated = emptyEntryDraft("row", 2)
        val restored = generated.copy(setId = "saved-id")
        val selected = WorkoutSetSelection("row", 2, generated.setId)
        assertEquals(WorkoutSetSelection("row", 2, restored.setId), selected.reconcile("row", listOf(restored)))

        val manuallySelected = WorkoutSetSelection("other", 3, "manual-id")
        assertEquals(manuallySelected, manuallySelected.reconcile("row", listOf(restored)))
        val shifted = emptyEntryDraft("other", 2).copy(setId = "manual-id")
        assertEquals(WorkoutSetSelection("other", 2, "manual-id"),
            manuallySelected.reconcile("other", listOf(shifted)))
    }

    @Test
    fun rpeEditingRejectsOutOfRangeInputButAllowsDecimalsAndExistingInvalidValueCorrection() {
        listOf("", ".", ",", "0", "7.5", "9,5", "10", "10.0", "10.", "10,").forEach {
            assertTrue("Accept $it", acceptsRpeEdit("8", it))
        }
        listOf("11", "99", "10.1", "11.", "11,", "-1", "bad", "NaN", "1e1").forEach {
            assertFalse("Reject $it", acceptsRpeEdit("8", it))
        }
        assertTrue(acceptsRpeEdit("366", "36"))
        assertTrue(acceptsRpeEdit("36", "3"))
        assertTrue(acceptsRpeEdit("366", "8"))
        assertFalse(acceptsRpeEdit("366", "3666"))
    }

    @Test
    fun automaticTimingFieldsOfferOnlyOneNextInputWithoutRewritingRawBuffers() {
        val emptyBuffer = List(8) { "" }
        assertEquals(listOf(""), visibleTimingInputs(emptyBuffer, false))
        assertEquals(List(8) { "" }, emptyBuffer)
        assertEquals(listOf("0", ""), visibleTimingInputs(listOf("0"), false))
        listOf(".", "1.", "bad", "1000000001").forEach {
            assertEquals(listOf(it), visibleTimingInputs(listOf(it), false))
        }
        assertEquals(listOf("4", "", "6", ""), visibleTimingInputs(listOf("4", "", "6", "", ""), false))
        assertEquals(listOf("4", ""), visibleTimingInputs(listOf("4", "", ""), false))
        assertEquals(listOf("4", "6"), visibleTimingInputs(listOf("4", "6", ""), true))
        val eight = List(8) { (it + 1).toString() }
        assertEquals(eight, visibleTimingInputs(eight, false))
    }

    @Test
    fun offeredBlankTimingFieldsDoNotBecomeRecordedAttemptsOrEraseRetainedTotal() {
        val draft = emptyEntryDraft("row", 1).copy(
            tutEntries = encodeTimingInputs(listOf("0", "", "4", "", "")),
            timeUnderTension = "30",
        )
        val logic = WorkoutSetDraftLogic(config(MeasurementMetric.SKILL_HOLD))
        visibleTimingInputs(attemptsFor(draft), false)
        val set = requireNotNull(logic.parsedSet(draft))
        assertEquals(listOf(0L, 4_000L), timingMillis(set.tutEntriesMillis))
        assertEquals(4_000L, set.holdMillis)
        assertEquals(30_000L, set.timeUnderTensionMillis)
        assertEquals(listOf("0", "", "4", "", ""), attemptsFor(draft))
    }

    private fun config(metric: MeasurementMetric) = ExerciseInputConfig()
        .withResistance(ResistanceMetric.BODYWEIGHT)
        .withMeasurement(metric)

    private fun metadata(config: String, name: String = "Cached", type: String = "HOLD", rank: Int? = 3) =
        object : VariantDraftMetadata {
            override val inputConfig = config
            override val variantName = name
            override val trackingType = type
            override val rank = rank
        }

    private fun row(config: String = "") = WorkoutExerciseEntity(
        "row", "workout", "core:handstand", "core:handstand", 0, "", 120, null,
        trackingType = "SKILL_HOLD", inputConfig = config,
    )

    @Test
    fun previousSummaryFormatsResistanceAndRepsInTheirDisplayedUnits() {
        val config = config(MeasurementMetric.REPETITIONS).withResistance(ResistanceMetric.KILOGRAMS)
            .copy(weightUnit = "lb")
        val set = emptySet("previous", 1).copy(weightKg = 25 / 2.2046226218, reps = 7.0)
        assertEquals("Prev: 25lb, 7",
            previousSetSummary(set, config, "kg", emptyList(), emptyList()))
    }

    @Test
    fun previousSummaryUsesKnownBandAbbreviationsAndDoesNotInventMissingColours() {
        val config = config(MeasurementMetric.REPETITIONS).withResistance(ResistanceMetric.BANDS)
        val set = emptySet("previous", 1).copy(reps = 4.0)
        val snapshots = listOf(
            WorkoutSetBandEntity(set.id, "band-yellow", 0, "Recorded yellow"),
            WorkoutSetBandEntity(set.id, "custom", 1, "Recorded custom"),
            WorkoutSetBandEntity(set.id, "missing", 2, "Archived band"),
        )
        val bands = listOf(BandEntity("custom", "Current custom", 1.0, 0xFF5085D6L))
        assertEquals("Prev: Yw+#5085D6+Archived band, 4",
            previousSetSummary(set, config, "kg", snapshots, bands))
    }

    @Test
    fun previousAddedWeightUsesTheSamePreferredUnitAsItsCurrentInput() {
        val config = config(MeasurementMetric.REPETITIONS).withResistance(ResistanceMetric.KILOGRAMS)
            .copy(addedWeight = true, weightUnit = "kg")
        val set = emptySet("previous", 1).copy(addedWeightKg = 25 / 2.2046226218, reps = 7.0)
        assertEquals("Prev: 25lb, 7",
            previousSetSummary(set, config, "lb", emptyList(), emptyList()))
    }

    @Test
    fun previousSummaryKeepsAttemptSumIndependentAndDoesNotFabricateAbsentTimes() {
        val config = config(MeasurementMetric.SKILL_HOLD)
        val set = emptySet("previous", 1).copy(
            tutEntriesMillis = "[1000,2000]", holdMillis = 999_000, timeUnderTensionMillis = 9_000,
        )
        assertEquals("Prev: 3s / 9s", previousSetSummary(set, config, "kg", emptyList(), emptyList()))
        assertEquals("Prev: 3s", previousSetSummary(set.copy(timeUnderTensionMillis = null), config, "kg", emptyList(), emptyList()))
        assertNull(previousSetSummary(emptySet("previous", 1).copy(rpe = 7.5), config, "kg", emptyList(), emptyList()))
    }

    @Test
    fun previousSummaryRetainsRecordedRomUnitsButOmitsRpe() {
        val set = emptySet("previous", 1).copy(romValue = 12.0, romUnit = "cm", rpe = 7.5)
        assertEquals("Prev: ROM: 12cm",
            previousSetSummary(set, config(MeasurementMetric.RANGE_OF_MOTION), "kg", emptyList(), emptyList()))
    }

    @Test
    fun configurationUsesOverrideThenRecordedThenCachedThenCatalogueThenRow() {
        val configs = MeasurementMetric.entries.associateWith { config(it).encode() }
        val row = row(configs.getValue(MeasurementMetric.REPETITIONS))
        val variation = ExerciseVariationEntity(
            "variant", row.actualExerciseId, "Live", 9, false, "HOLD",
            configs.getValue(MeasurementMetric.HOLD),
        )
        val recorded = emptySet(row.id, 1).copy(
            variationInputConfigSnapshot = configs.getValue(MeasurementMetric.RANGE_OF_MOTION),
            variationTrackingTypeSnapshot = "ROM_MEASUREMENT",
        )
        val cached = metadata(configs.getValue(MeasurementMetric.SKILL_HOLD))
        fun resolve(
            record: WorkoutSetEntity?,
            cache: VariantDraftMetadata?,
            variant: ExerciseVariationEntity?,
            overrideConfig: String? = null,
        ) =
            resolveSetInputConfig(row, "SKILL_HOLD", variant, record, cache, overrideConfig).loggingMeasurement()

        assertEquals(MeasurementMetric.REPETITIONS, resolve(recorded, cached, variation, row.inputConfig))
        assertEquals(MeasurementMetric.RANGE_OF_MOTION, resolve(recorded, cached, variation))
        assertEquals(MeasurementMetric.SKILL_HOLD, resolve(recorded.copy(variationInputConfigSnapshot = ""), cached, variation))
        assertEquals(MeasurementMetric.HOLD, resolve(null, null, variation))
        assertEquals(MeasurementMetric.REPETITIONS, resolve(null, null, variation.copy(inputConfig = "")))
        assertEquals(MeasurementMetric.REPETITIONS, resolve(null, null, null))
    }

    @Test
    fun onlyUnrecordedSetsInheritFrozenManyTimingAndMeasurementOrderDefaults() {
        val row = row(ExerciseInputConfig(
            timeHeld = true, manyTuts = true,
            measurementOrder = listOf(ExerciseMeasurement.TUT, ExerciseMeasurement.BANDS),
        ).encode())
        val variation = ExerciseVariationEntity("variant", row.actualExerciseId, "Live", 1, false, "HOLD", "timeHeld=true")
        val unrecorded = resolveSetInputConfig(row, "HOLD", variation, null, null, null)
        assertTrue(unrecorded.manyTuts)
        assertEquals(listOf(ExerciseMeasurement.TUT, ExerciseMeasurement.BANDS), unrecorded.measurementOrder)

        val recorded = resolveSetInputConfig(row, "HOLD", variation, emptySet(row.id, 1), null, null)
        assertFalse(recorded.manyTuts)
        assertTrue(recorded.measurementOrder.isEmpty())
    }

    @Test
    fun validationRejectsUnresolvedAndTransientActiveValuesButIgnoresInactiveValues() {
        val draft = emptyEntryDraft("row", 1).copy(
            performance = "7.5", romValue = "unfinished", timeHeld = "unfinished",
            timeUnderTension = "unfinished", addedWeight = "unfinished",
        )
        val reps = WorkoutSetDraftLogic(config(MeasurementMetric.REPETITIONS))
        assertNull(reps.inputError(draft))
        assertNotNull(reps.parsedSet(draft))
        listOf(".", ",", "-1", "1..2").forEach {
            assertNotNull(reps.inputError(draft.copy(performance = it)))
            assertNull(reps.parsedSet(draft.copy(performance = it)))
        }
        assertNotNull(reps.inputError(draft.copy(rpe = "10.1")))
        assertNotNull(reps.inputError(draft.copy(rpe = ".")))
        assertNull(reps.inputError(draft.copy(rpe = "10")))
        assertNotNull(WorkoutSetDraftLogic(ExerciseInputConfig(reps = true, timeHeld = true)).inputError(draft))
        assertNull(reps.parsedSet(emptyEntryDraft("row", 1).copy(rpe = "8")))
    }

    @Test
    fun allTwelveConfigurationsPreservePassiveValuesAndUseActiveMeasurementAndUnits() {
        val recorded = emptySet("row", 1).copy(
            variationId = "variant", weightKg = 17.0, reps = 99.0, romValue = 20.0, romUnit = "degrees",
            leftReps = 4.0, rightReps = 5.0, assistanceKg = 3.0, notes = "Retained",
            loggedAt = 123L, variationNameSnapshot = "Original", variationTrackingTypeSnapshot = "Original type",
            variationRankSnapshot = 4,
        )
        for (resistance in ResistanceMetric.entries) for (measurement in MeasurementMetric.entries) {
            val config = ExerciseInputConfig(
                weightUnit = "lb".takeIf { resistance == ResistanceMetric.KILOGRAMS }, bodyweight = true,
            ).withResistance(resistance, "lb").withMeasurement(measurement)
            val draft = emptyEntryDraft("row", 1).copy(
                variationId = "variant", performance = if (config.loggingWeightUnit == null) "7.5" else "22.046226218 x 7.5",
                addedWeight = "4.4092452436", romValue = "45", romUnit = "cm", rpe = "9",
                tutEntries = encodeTimingInputs(listOf("1.25", "", "2.5")), timeUnderTension = "10.5",
            )
            val parsed = requireNotNull(WorkoutSetDraftLogic(
                config, recorded, preferredWeightUnit = "lb",
            ).parsedSet(draft))
            assertEquals(if (resistance == ResistanceMetric.KILOGRAMS) 10.0 else 17.0, requireNotNull(parsed.weightKg), 0.000001)
            assertEquals(if (measurement == MeasurementMetric.REPETITIONS) 7.5 else 99.0, parsed.reps)
            assertEquals(if (measurement == MeasurementMetric.RANGE_OF_MOTION) 45.0 else 20.0, parsed.romValue)
            assertEquals(if (measurement == MeasurementMetric.RANGE_OF_MOTION) "cm" else "degrees", parsed.romUnit)
            assertEquals(2.0, requireNotNull(parsed.addedWeightKg), 0.000001)
            assertEquals(3_750L, parsed.holdMillis)
            assertEquals(listOf(1_250L, 2_500L), timingMillis(parsed.tutEntriesMillis))
            assertEquals(10_500L, parsed.timeUnderTensionMillis)
            assertEquals(recorded.id, parsed.id)
            assertEquals(recorded.loggedAt, parsed.loggedAt)
            assertEquals(recorded.leftReps, parsed.leftReps)
            assertEquals(recorded.rightReps, parsed.rightReps)
            assertEquals(recorded.assistanceKg, parsed.assistanceKg)
            assertEquals(recorded.notes, parsed.notes)
            assertEquals(recorded.variationNameSnapshot, parsed.variationNameSnapshot)
            assertEquals(recorded.variationTrackingTypeSnapshot, parsed.variationTrackingTypeSnapshot)
            assertEquals(recorded.variationRankSnapshot, parsed.variationRankSnapshot)
        }
    }

    @Test
    fun totalOnlyAndRomOnlyEntriesQualifyWithoutInventingAttempts() {
        val draft = emptyEntryDraft("row", 1).copy(timeUnderTension = "12.25")
        val configs = listOf(
            config(MeasurementMetric.SKILL_HOLD),
            ExerciseInputConfig(timeUnderTension = true).withMeasurement(MeasurementMetric.HOLD),
        )
        configs.forEach {
            val parsed = requireNotNull(WorkoutSetDraftLogic(it).parsedSet(draft))
            assertNull(parsed.holdMillis)
            assertEquals(emptyList<Long>(), timingMillis(parsed.tutEntriesMillis))
            assertEquals(12_250L, parsed.timeUnderTensionMillis)
            assertEquals(draft.setId, parsed.id)
        }
        val rom = requireNotNull(WorkoutSetDraftLogic(config(MeasurementMetric.RANGE_OF_MOTION))
            .parsedSet(emptyEntryDraft("row", 1).copy(romValue = "0", romUnit = "degrees")))
        assertEquals(0.0, rom.romValue)
        assertEquals("degrees", rom.romUnit)
    }

    @Test
    fun variantConversionCarriesUnitsAndNeverInterpretsBodyweightRepsAsLoad() {
        val kilograms = ExerciseInputConfig(weightUnit = "kg", reps = true)
        val pounds = kilograms.copy(weightUnit = "lb")
        val bodyweight = config(MeasurementMetric.REPETITIONS)
        val draft = emptyEntryDraft("row", 1).copy(performance = "20 x 8")
        val converted = WorkoutSetDraftLogic(kilograms).changeVariant(draft, "lb-variant", pounds)
        assertEquals(44.092452436, requireNotNull(quantitativeInput(
            WorkoutSetDraftLogic(pounds).performanceParts(converted).first,
        ).value), 0.005)
        assertEquals("8", WorkoutSetDraftLogic(pounds).performanceParts(converted).second)
        assertEquals(draft.setId, converted.setId)
        assertEquals("lb-variant", converted.variationId)
        assertEquals("8", WorkoutSetDraftLogic(kilograms).changeVariant(draft, "bodyweight", bodyweight).performance)
        val reps = draft.copy(performance = "7.5")
        assertEquals(" x 7.5", WorkoutSetDraftLogic(bodyweight).changeVariant(reps, "weighted", kilograms).performance)
        assertEquals("10 x 7.5", WorkoutSetDraftLogic(bodyweight, emptySet("row", 1).copy(weightKg = 10.0))
            .changeVariant(reps, "weighted", kilograms).performance)
    }

    @Test
    fun variantMetadataUsesRecordedSnapshotForSameVariantAndCachedSnapshotWhenChanged() {
        val config = config(MeasurementMetric.REPETITIONS)
        val recorded = emptySet("row", 1).copy(
            variationId = "original", variationNameSnapshot = "Recorded",
            variationTrackingTypeSnapshot = "Recorded type", variationRankSnapshot = null,
        )
        val live = ExerciseVariationEntity("next", "exercise", "Live", 9, false, "Live type")
        val cached = metadata(config.encode())
        val draft = emptyEntryDraft("row", 1).copy(performance = "8", variationId = "original")
        val logic = WorkoutSetDraftLogic(config, recorded, live, cached)
        val same = requireNotNull(logic.parsedSet(draft))
        assertEquals("Recorded", same.variationNameSnapshot)
        assertEquals("Recorded type", same.variationTrackingTypeSnapshot)
        assertNull(same.variationRankSnapshot)
        val changed = requireNotNull(logic.parsedSet(draft.copy(variationId = "next")))
        assertEquals("Cached", changed.variationNameSnapshot)
        assertEquals("HOLD", changed.variationTrackingTypeSnapshot)
        assertEquals(3, changed.variationRankSnapshot)
        assertEquals(9, WorkoutSetDraftLogic(config, recorded, live, metadata(config.encode(), rank = null))
            .parsedSet(draft.copy(variationId = "next"))?.variationRankSnapshot)
    }
}

package com.petermathie.vibecheck

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.petermathie.vibecheck.data.local.BandEntity
import com.petermathie.vibecheck.data.DataTransfer
import com.petermathie.vibecheck.data.TrainingRepository
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.data.seed.DatabaseSeeder
import com.petermathie.vibecheck.domain.programme.ExerciseInputConfig
import com.petermathie.vibecheck.domain.programme.ExerciseMeasurement
import com.petermathie.vibecheck.domain.programme.MeasurementMetric
import com.petermathie.vibecheck.domain.programme.ResistanceMetric
import com.petermathie.vibecheck.domain.workout.encodeTimingMillis
import com.petermathie.vibecheck.domain.workout.timingMillis
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.WorkoutEditor
import com.petermathie.vibecheck.ui.WorkoutSetDraftLogic
import com.petermathie.vibecheck.ui.HistoryScreen
import com.petermathie.vibecheck.ui.ExerciseSettingsDialog
import com.petermathie.vibecheck.ui.RestTimer
import com.petermathie.vibecheck.ui.emptySet
import com.petermathie.vibecheck.ui.draftFromSet
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutSetTilesTest {
    private lateinit var db: VibeDatabase
    @get:Rule val lifecycle = ComposeRoomLifecycleRule { if (::db.isInitialized) db else null }
    private val compose get() = lifecycle.compose

    private fun start(day: String = "demo-day-push"): String = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        DatabaseSeeder(context, db).seedIfNeeded()
        TrainingRepository(db, db.programmeDao(), db.workoutDao(), db.trackerDao(), context)
            .startWorkout(day)
    }

    private fun show(workout: String): EditorViewModel {
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Time Under Tension for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        return vm
    }

    private fun expandSet(exercise: String, ordinal: Int) {
        compose.onNodeWithContentDescription("Set $ordinal for $exercise")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
    }

    private fun variantOption(name: String) = compose.onNode(
        hasText(name) and hasClickAction() and
            SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Selected),
    )

    private fun showAtSize(workout: String, width: Dp, fontScale: Float) {
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VibeCheckTheme { Box(Modifier.width(width)) { WorkoutEditor(vm, workout, {}, {}) } }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun rpeTextMatchesAdjacentMeasurementWithoutChangingFieldHeight() {
        show(start())
        fun fontSize(node: SemanticsNodeInteraction): Float {
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            return layouts.single().layoutInput.style.fontSize.value
        }
        listOf("Handstand" to "Time Under Tension", "Planche" to "Time Under Tension", "Bench press" to "Reps")
            .forEach { (exercise, measurement) ->
                val identity = "$exercise set 1"
                compose.onNodeWithContentDescription("Workout exercise list")
                    .performScrollToNode(hasContentDescription("RPE for $identity"))
                val field = compose.onNodeWithContentDescription("$measurement for $identity")
                val rpe = compose.onNodeWithContentDescription("RPE for $identity")
                assertEquals("$exercise numeric text matches", fontSize(field), fontSize(rpe), 0f)
                fun unit(text: String, description: String) = compose.onNode(
                    hasText(text) and hasAnyAncestor(hasContentDescription(description)),
                    useUnmergedTree = true,
                )
                assertEquals("$exercise unit text matches",
                    fontSize(unit(if (measurement == "Reps") "Reps" else "s", "$measurement for $identity")),
                    fontSize(unit("RPE", "RPE for $identity")), 0f)
                val fieldBounds = field.getUnclippedBoundsInRoot()
                val rpeBounds = rpe.getUnclippedBoundsInRoot()
                assertEquals("$exercise field height is unchanged",
                    (fieldBounds.bottom - fieldBounds.top).value,
                    (rpeBounds.bottom - rpeBounds.top).value, 0.01f)
            }
    }

    @Test
    fun entrySelectsFirstUntickedSavedSetWithoutFocusAndMetadataDoesNotOverrideManualActivation() {
        val workout = start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("workout_logging", 0)
        val originalLocks = preferences.getStringSet("locked_sets", emptySet()).orEmpty().toSet()
        val (completed, autosaved) = runBlocking {
            val row = db.editorDao().workoutExercisesForWorkout(workout).first { it.actualExerciseId == "core:handstand" }
            val first = emptySet(row.id, 1).copy(variationId = "handstand-wall", holdMillis = 5_000)
            val second = emptySet(row.id, 2).copy(variationId = "handstand-wall", holdMillis = 7_000)
            db.editorDao().saveSetWithSnapshots(first, emptyList(), false)
            db.editorDao().saveSetWithSnapshots(second, emptyList(), false)
            first to second
        }
        try {
            assertTrue(preferences.edit().putStringSet("locked_sets", originalLocks + completed.id).commit())
            val vm = show(workout)
            compose.onNodeWithContentDescription("Set 1 for Handstand").assertIsNotSelected()
            compose.onNodeWithContentDescription("Set 2 for Handstand").assertIsSelected()
            compose.onNodeWithContentDescription("Time Under Tension for Handstand set 2")
                .assertTextEquals("7.0", "s").assertIsNotFocused()
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").assertIsSelected()

            expandSet("Handstand", 3)
            compose.onNodeWithContentDescription("Set 3 for Handstand").assertIsSelected()
            val variant = runBlocking {
                val original = db.editorDao().variationById("handstand-wall")!!
                original.copy(id = "${original.id}-entry-review", name = "Later metadata", isSeeded = false).also {
                    db.editorDao().variation(it)
                }
            }
            compose.waitUntil(15_000) { vm.workoutLoggingData.value?.variations?.any { it.id == variant.id } == true }
            compose.onNodeWithContentDescription("Set 3 for Handstand").assertIsSelected()
            compose.onNodeWithContentDescription("Set 2 for Handstand").assertIsNotSelected()
            assertEquals(7_000L, runBlocking { db.editorDao().setById(autosaved.id) }?.holdMillis)
        } finally {
            assertTrue(preferences.edit().putStringSet("locked_sets", originalLocks).commit())
        }
    }

    @Test
    fun tickStartsConfiguredRestAndParentContainsIndependentlyTappableSets() {
        val workout = start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        RestTimer.cancel(context)
        try {
            show(workout)
            val tick = compose.onNodeWithContentDescription("Complete set 1 for Handstand")
            tick.assertIsNotEnabled()
            compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("5")
            compose.runOnIdle {
                assertEquals(0, context.getSharedPreferences("settings", 0).getLong("restEnd", 0))
            }
            val parent = compose.onNodeWithContentDescription("Exercise Handstand").getUnclippedBoundsInRoot()
            val last = compose.onNodeWithContentDescription("Set 3 for Handstand").getUnclippedBoundsInRoot()
            assertTrue(parent.bottom >= last.bottom && parent.left < last.left && parent.right > last.right)
            tick.performScrollTo().performClick()
            compose.onNodeWithContentDescription("Time Under Tension for Handstand set 2").assertExists()
            compose.onNodeWithContentDescription("Exercise Handstand")
                .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Expanded"))
            val rest = runBlocking {
                db.editorDao().workoutExercises().first().first { it.workoutId == workout && it.actualExerciseId == "core:handstand" }.restSeconds
            }
            compose.runOnIdle {
                val remaining = context.getSharedPreferences("settings", 0).getLong("restEnd", 0) - System.currentTimeMillis()
                assertTrue(remaining in (rest * 1_000L - 10_000)..(rest * 1_000L))
            }
            compose.onNodeWithContentDescription("Start 120 second rest for Handstand").assertDoesNotExist()
        } finally {
            RestTimer.cancel(context)
        }
    }

    @Test
    fun restoredUntouchedSetsStayUnchangedWhilePersistedPendingEditsStillSave() {
        val workout = start()
        val (untouched, pending) = runBlocking {
            val dao = db.editorDao()
            val row = dao.workoutExercises().first().first {
                it.workoutId == workout && it.actualExerciseId == "core:handstand"
            }
            dao.workoutExercise(row.copy(restSeconds = 0))
            val untouched = emptySet(row.id, 1).copy(
                holdMillis = 10_123L, timeUnderTensionMillis = 30_000L, rpe = 3.0,
                reps = 5.0, weightKg = 20.0, notes = "Retain legacy fields and snapshots",
            )
            val pending = emptySet(row.id, 2).copy(variationId = "handstand-wall", holdMillis = 4_000L)
            dao.set(untouched)
            dao.set(pending)
            dao.entryDraft(draftFromSet(pending, hold = true, weightUnit = null).copy(timeHeld = "7.125"))
            untouched to pending
        }
        val preferences = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("workout_logging", 0)
        try {
            show(workout)
            compose.onNodeWithContentDescription("Exercise heading for Handstand").performClick()
            compose.onNodeWithContentDescription("Set 1 for Handstand").assertDoesNotExist()
            compose.onNodeWithContentDescription("Exercise heading for Handstand").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("Complete set 1 for Handstand")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Complete set 1 for Handstand").performScrollTo().performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("Edit completed set 1 for Handstand")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            assertEquals("Restoring a card is not an edit", untouched, runBlocking {
                db.editorDao().setById(untouched.id)
            })
            compose.onNodeWithContentDescription("Time Under Tension for Handstand set 2")
                .performScrollTo().assertTextContains("7.125")
            compose.onNodeWithContentDescription("Complete set 2 for Handstand").performScrollTo().performClick()
            compose.waitUntil(15_000) {
                runBlocking { db.editorDao().setById(pending.id)?.holdMillis == 7_125L }
            }
            assertNull(runBlocking { db.editorDao().entryDrafts(pending.workoutExerciseId).firstOrNull { it.setId == pending.id } })
        } finally {
            val remaining = preferences.getStringSet("locked_sets", emptySet()).orEmpty() -
                setOf(untouched.id, pending.id)
            preferences.edit().putStringSet("locked_sets", remaining).commit()
        }
    }

    @Test
    fun staleExerciseNotesCannotRestoreDeletedSlotsOrUndoAddedSlots() {
        val workout = start()
        val row = runBlocking {
            val dao = db.editorDao()
            dao.workoutExercises().first().first { it.workoutId == workout }.copy(targetSets = 3).also {
                dao.workoutExercise(it)
            }
        }
        val vm = lifecycle.own(EditorViewModel(db))
        runBlocking {
            listOf(0, 4).forEach { count ->
                vm.setWorkoutSetCount(row.id, count).join()
                vm.save(row.copy(notes = "Notes after changing to $count slots")).join()
                assertEquals("A stale notes save must retain the current slot count", row.copy(
                    targetSets = count, notes = "Notes after changing to $count slots",
                ), db.editorDao().workoutExerciseById(row.id))
            }
            val withoutExplicitCount = row.copy(position = 7, targetSets = null)
            db.editorDao().workoutExercise(withoutExplicitCount)
            vm.save(row.copy(notes = "Preserve legacy count and current order")).join()
            assertEquals(withoutExplicitCount.copy(notes = "Preserve legacy count and current order"),
                db.editorDao().workoutExerciseById(row.id))
            val added = row.copy(id = java.util.UUID.randomUUID().toString(), position = 8, targetSets = 2)
            vm.save(added).join()
            assertEquals(added, db.editorDao().workoutExerciseById(added.id))
        }
    }

    @Test
    fun tickLocksOnlyFilledValuesAcrossEditorRecreationAndUnlockRestoresOriginalFields() {
        val workout = start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = runBlocking {
            repeat(4) { index ->
                db.editorDao().band(BandEntity("locked-band-$index", "Custom $index", 4.0 + index, 0xFF5085D6L))
            }
            val variant = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(variant.copy(inputConfig = ExerciseInputConfig(
                timeHeld = true, manyTuts = true, timeUnderTension = true, bandResistance = true,
            ).encode()))
            val row = db.editorDao().workoutExercises().first().first {
                it.workoutId == workout && it.actualExerciseId == "core:handstand"
            }
            val set = emptySet(row.id, 1).copy(
                variationId = variant.id, holdMillis = 10_000L,
                tutEntriesMillis = encodeTimingMillis(listOf(4_000L, 6_000L)),
                timeUnderTensionMillis = 30_000L,
            )
            db.editorDao().saveSetWithSnapshots(set, listOf("band-yellow", "band-black"), false)
            db.editorDao().setById(set.id)!!
        }
        val generation = mutableIntStateOf(0)
        val firstVm = lifecycle.own(EditorViewModel(db))
        val reopenedVm = lifecycle.own(EditorViewModel(db))
        compose.setContent {
            key(generation.intValue) {
                VibeCheckTheme {
                    WorkoutEditor(if (generation.intValue == 0) firstVm else reopenedVm, workout, {}, {})
                }
            }
        }
        RestTimer.cancel(context)
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("TUT attempt 2 for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").assertDoesNotExist()
            val beforeCompletion = compose.onNodeWithContentDescription("Swipe actions for set 1 for Handstand")
                .getUnclippedBoundsInRoot()
            val editingImage = compose.onNodeWithContentDescription("Swipe actions for set 1 for Handstand")
                .captureToImage().asAndroidBitmap()
            File(context.cacheDir, "centred-locked-editing-set.png").outputStream().use {
                assertTrue(editingImage.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            compose.onNodeWithContentDescription("Complete set 1 for Handstand").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("Edit completed set 1 for Handstand").fetchSemanticsNodes().isNotEmpty()
            }
            val afterCompletion = compose.onNodeWithContentDescription("Swipe actions for set 1 for Handstand")
                .getUnclippedBoundsInRoot()
            assertEquals("Ticking a real set must not change its occupied height",
                (beforeCompletion.bottom - beforeCompletion.top).value,
                (afterCompletion.bottom - afterCompletion.top).value, 0.01f)
            compose.onAllNodes(hasSetTextAction() and hasAnyAncestor(hasContentDescription("Set 1 for Handstand")))
                .assertCountEquals(0)
            compose.onNodeWithContentDescription("TUT attempt 1 for Handstand set 1").assertTextEquals("4", "s")
            compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").assertTextEquals("6", "s")
            compose.onNodeWithContentDescription("Total Time for Handstand set 1").assertExists()
            compose.onNodeWithContentDescription("RPE for Handstand set 1").assertDoesNotExist()
            compose.onNodeWithContentDescription("Yellow band for Handstand set 1").assertIsNotEnabled().assertIsSelected()
            compose.onNodeWithContentDescription("Red band for Handstand set 1").assertDoesNotExist()
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").assertIsSelected()
            compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertIsDisplayed().assertIsEnabled()
            val completedImage = compose.onNodeWithContentDescription("Swipe actions for set 1 for Handstand")
                .captureToImage().asAndroidBitmap()
            File(context.cacheDir, "centred-locked-completed-set.png").outputStream().use {
                assertTrue(completedImage.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            compose.runOnIdle {
                assertTrue(context.getSharedPreferences("workout_logging", 0).getStringSet("locked_sets", emptySet())!!.contains(original.id))
                generation.intValue++
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("Yellow band for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").assertExists()
            val recreated = compose.onNodeWithContentDescription("Swipe actions for set 1 for Handstand")
                .getUnclippedBoundsInRoot()
            assertEquals("Recreated locked sets retain the editing height",
                (beforeCompletion.bottom - beforeCompletion.top).value,
                (recreated.bottom - recreated.top).value, 0.01f)
            compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertIsDisplayed().assertIsEnabled()
            compose.onNodeWithContentDescription("TUT attempt 1 for Handstand set 1").assertTextEquals("4", "s")
            val saved = runBlocking { db.editorDao().setById(original.id)!! }
            assertEquals(original, saved)
            RestTimer.cancel(context)
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").performClick()
            val afterUnlock = compose.onNodeWithContentDescription("Swipe actions for set 1 for Handstand")
                .getUnclippedBoundsInRoot()
            assertEquals("Unlocking must retain the same occupied height",
                (beforeCompletion.bottom - beforeCompletion.top).value,
                (afterUnlock.bottom - afterUnlock.top).value, 0.01f)
            File(context.cacheDir, "centred-locked-real-set-geometry.txt").writeText(
                "Editing=$beforeCompletion\nLocked=$afterCompletion\nRecreated=$recreated\nUnlocked=$afterUnlock\n",
            )
            compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertIsDisplayed().assertIsEnabled()
            expandSet("Handstand", 1)
            compose.onNodeWithContentDescription("TUT attempt 1 for Handstand set 1").assertTextContains("4")
            compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").assertTextContains("6")
            compose.onNodeWithContentDescription("Total Time for Handstand set 1").assertExists()
            compose.onNodeWithContentDescription("Yellow band for Handstand set 1").assertIsEnabled().assertIsSelected()
            compose.onNodeWithContentDescription("Black band for Handstand set 1").assertIsSelected()
            compose.runOnIdle {
                assertEquals(0, context.getSharedPreferences("settings", 0).getLong("restEnd", 0))
                assertFalse(context.getSharedPreferences("workout_logging", 0).getStringSet("locked_sets", emptySet())!!.contains(original.id))
            }
        } finally {
            RestTimer.cancel(context)
        }
    }

    @Test
    fun largeFontTileKeepsVariantHeaderAndDirectlyAccessibleBandDots() {
        val workout = start()
        runBlocking {
            val variant = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(variant.copy(inputConfig = ExerciseInputConfig(
                bodyweight = true, bandResistance = true, timeHeld = true, manyTuts = true,
            ).encode()))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                VibeCheckTheme { Box(Modifier.width(320.dp)) { WorkoutEditor(vm, workout, {}, {}) } }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Yellow band for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Bodyweight: tracked from workout").assertDoesNotExist()
        compose.onNodeWithContentDescription("Reps for Handstand set 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Wall handstand")
        val firstRow = listOf("Time Under Tension for Handstand set 1",
            "Complete set 1 for Handstand", "Change variant for Handstand set 1")
        val collapsed = firstRow.map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot }
        expandSet("Handstand", 1)
        val bandBounds = listOf("Yellow", "Red", "Black", "Purple").map { name ->
            compose.onNodeWithContentDescription("$name band for Handstand set 1")
                .getUnclippedBoundsInRoot()
        }
        val swap = compose.onNodeWithContentDescription("Change variant for Handstand set 1").getUnclippedBoundsInRoot()
        val tick = compose.onNodeWithContentDescription("Complete set 1 for Handstand").getUnclippedBoundsInRoot()
        assertEquals(collapsed, firstRow.map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot })
        assertTrue("Bands must wrap in order without overlap: $bandBounds", bandBounds.zipWithNext().all { (left, right) ->
            if (kotlin.math.abs((left.top + left.bottom).value - (right.top + right.bottom).value) < 2f) {
                left.right <= right.left
            } else left.bottom <= right.top
        })
        val rpe = compose.onNodeWithContentDescription("RPE for Handstand set 1").getUnclippedBoundsInRoot()
        assertTrue("RPE and completion remain narrow trailing controls", rpe.right <= tick.left)
        assertTrue("Variant selector is a header, not a separate swap", swap.top < tick.top)
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Wall handstand")
        val heading = compose.onNodeWithContentDescription("Variant for Handstand set 1").getUnclippedBoundsInRoot()
        val tile = compose.onNodeWithContentDescription("Set 1 for Handstand").getUnclippedBoundsInRoot()
        assertTrue("Variant heading overlaps the top border", heading.top < tile.top && heading.bottom > tile.top)
        compose.onNodeWithContentDescription("Yellow band for Handstand set 1").performClick().assertIsSelected()
        compose.onNodeWithContentDescription("Black band for Handstand set 1").performClick().assertIsSelected()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun stagedDeletionRemovesEmptyAndSavedTilesWithoutResurrectionOnReopening() {
        val workout = start()
        show(workout)
        val row = runBlocking { db.editorDao().workoutExercises().first().first { it.workoutId == workout && it.actualExerciseId == "core:handstand" } }
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("5")
        compose.waitUntil("Handstand set must be autosaved before testing its deletion", 15_000) {
            runBlocking { db.editorDao().sets().first().any {
                it.workoutExerciseId == row.id && it.ordinal == 1 && it.holdMillis == 5_000L
            } }
        }
        val savedId = runBlocking { db.editorDao().sets().first().single { it.workoutExerciseId == row.id }.id }
        compose.onNodeWithContentDescription("Set 3 for Handstand").performScrollTo()
        val empty = compose.onNodeWithContentDescription("Swipe actions for set 3 for Handstand")
        empty.performCustomAccessibilityActionWithLabel("Reveal delete")
        empty.performCustomAccessibilityActionWithLabel("Delete set 3 for Handstand")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().workoutExerciseById(row.id)?.targetSets == 2 }
        }
        compose.onNodeWithContentDescription("Set 3 for Handstand").assertDoesNotExist()
        val saved = compose.onNodeWithContentDescription("Swipe actions for set 1 for Handstand")
        saved.performCustomAccessibilityActionWithLabel("Reveal delete")
        saved.performCustomAccessibilityActionWithLabel("Delete set 1 for Handstand")
        compose.waitUntil(15_000) { runBlocking { db.editorDao().setById(savedId) == null && db.editorDao().workoutExerciseById(row.id)?.targetSets == 1 } }
        compose.onNodeWithContentDescription("Exercise heading for Handstand").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Exercise heading for Handstand").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Set 2 for Handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1")
            .assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText, androidx.compose.ui.text.AnnotatedString("")))
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun pinnedHeadingAndExerciseBodyShareOneDeletionStage() {
        val workout = start()
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent {
            VibeCheckTheme { Box(Modifier.height(320.dp)) { WorkoutEditor(vm, workout, {}, {}) } }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("RPE for Handstand set 3").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Add set for Handstand").performScrollTo()
        compose.onNodeWithContentDescription("Pinned exercise heading for Handstand").assertIsDisplayed()
        val wrappers = compose.onAllNodesWithContentDescription("Swipe actions for exercise Handstand")
        wrappers.assertCountEquals(2)
        wrappers.onFirst().performCustomAccessibilityActionWithLabel("Reveal delete")
        wrappers.onLast().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Delete revealed"))
        wrappers.onLast().performCustomAccessibilityActionWithLabel("Close delete")
        wrappers.onFirst().assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Closed"))
        wrappers.onLast().performCustomAccessibilityActionWithLabel("Reveal delete")
        wrappers.onLast().performCustomAccessibilityActionWithLabel("Delete exercise Handstand")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().workoutExercises().first().none { it.workoutId == workout && it.actualExerciseId == "core:handstand" } }
        }
        assertTrue(runBlocking { db.editorDao().workoutExercises().first().any { it.workoutId == workout && it.actualExerciseId == "core:planche" } })
    }

    @Test
    fun manyTimingHasExactlyEightSlotsAndPersistsEveryAttemptAndTotal() {
        val workout = start()
        show(workout)
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("1")
        for (index in 2..8) {
            compose.onNodeWithContentDescription("TUT attempt $index for Handstand set 1").performScrollTo().performTextInput(index.toString())
        }
        assertTimingFlex()
        compose.onNodeWithContentDescription("Add TUT entry for Handstand set 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("TUT attempt 9 for Handstand set 1").assertDoesNotExist()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("TUT total for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("TUT total for Handstand set 1").assertTextContains("36 s", substring = true)
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").performTextReplacement("60")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any {
                timingMillis(it.tutEntriesMillis) == List(8) { i -> (i + 1) * 1000L } &&
                    it.holdMillis == 36_000L && it.timeUnderTensionMillis == 60_000L
            } }
        }
        val backup = runBlocking { DataTransfer.export(db) }
        runBlocking { DataTransfer.import(db, backup) }
        assertTrue(runBlocking { db.editorDao().sets().first().any {
            timingMillis(it.tutEntriesMillis).size == 8 && it.holdMillis == 36_000L && it.timeUnderTensionMillis == 60_000L
        } })
    }

    @Test
    fun eightAttemptsStayAccessibleWithoutExpansionAt320DpAndDoubleFontScale() {
        val workout = start()
        runBlocking {
            val row = db.editorDao().workoutExercises().first().first {
                it.workoutId == workout && it.actualExerciseId == "core:handstand"
            }
            db.editorDao().set(emptySet(row.id, 1).copy(
                variationId = "handstand-wall",
                variationInputConfigSnapshot = ExerciseInputConfig(timeHeld = true, manyTuts = true).encode(),
                holdMillis = 36_000L,
                tutEntriesMillis = encodeTimingMillis(List(8) { (it + 1) * 1_000L }),
            ))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                VibeCheckTheme {
                    Box(Modifier.width(320.dp)) { WorkoutEditor(vm, workout, {}, {}) }
                }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty()
        }
        fun tileHeight() = compose.onNodeWithContentDescription("Set 1 for Handstand")
            .getUnclippedBoundsInRoot().let { it.bottom - it.top }
        val collapsedHeight = tileHeight()
        expandSet("Handstand", 1)
        assertEquals(collapsedHeight, tileHeight())
        assertTimingFlex(2f)
        compose.onNodeWithContentDescription("TUT attempt 8 for Handstand set 1").performScrollTo().performTextReplacement("9")
        compose.waitUntil(15_000) {
            runBlocking {
                db.editorDao().sets().first().any {
                    timingMillis(it.tutEntriesMillis) == listOf(1_000L, 2_000L, 3_000L, 4_000L, 5_000L, 6_000L, 7_000L, 9_000L)
                }
            }
        }
    }

    private fun assertTimingFlex(fontScale: Float = 1f) {
        (1..8).forEach {
            compose.onNodeWithContentDescription("TUT attempt $it for Handstand set 1")
                .performScrollTo().assertIsDisplayed().assertTextContains(it.toString())
        }
        val bounds = (1..8).map {
            compose.onNodeWithContentDescription("TUT attempt $it for Handstand set 1")
                .getUnclippedBoundsInRoot()
        }
        val cell = compose.onNodeWithContentDescription("TUT attempt 1 for Handstand set 1").getUnclippedBoundsInRoot()
        assertTrue("TUT cells must remain content-sized", cell.right - cell.left <= 56.dp * fontScale + 0.5.dp)
        assertTrue("TUT attempts must wrap in order without overlap: $bounds", bounds.zipWithNext().all { (left, right) ->
            if (kotlin.math.abs((left.top + left.bottom).value - (right.top + right.bottom).value) < 2f) left.right <= right.left else left.bottom <= right.top
        })
        assertTrue(bounds.groupBy { it.top }.values.all { it.size <= 4 })
    }

    @Test
    fun variantsAreIndependentAndSwitchingToOneRetainsExistingAttempts() {
        val workout = start()
        runBlocking {
            val variation = db.editorDao().variationById("handstand-free")!!
            db.editorDao().variation(variation.copy(inputConfig = ExerciseInputConfig(timeHeld = true, manyTuts = false).encode()))
        }
        show(workout)
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("4")
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").performTextInput("6")
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performScrollTo().performClick()
        compose.onNodeWithText("Freestanding handstand").performClick()
        compose.onNodeWithContentDescription("Add TUT entry for Handstand set 1").assertDoesNotExist()
        compose.onNodeWithText("Recorded attempts are retained. Choose Skill hold in the exercise DB to edit them.").assertExists()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").assertTextEquals("10", "s")
        expandSet("Handstand", 2)
        compose.onNodeWithContentDescription("Change variant for Handstand set 2").performScrollTo().performClick()
        variantOption("Wall handstand").assertIsSelected().performClick()
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.variationId == "handstand-free" && it.holdMillis == 10_000L && timingMillis(it.tutEntriesMillis) == listOf(4_000L, 6_000L) } }
        }
    }

    @Test
    fun completingTileSelectsNextEmptyAndExercisesCollapseWithoutLosingValues() {
        val workout = start()
        show(workout)
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("5")
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("RPE for Handstand set 1").performTextInput("8")
        compose.onNodeWithContentDescription("RPE for Handstand set 1").performImeAction()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 2").assertExists()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").assertTextContains("5")
        compose.onNodeWithContentDescription("Set 2 for Handstand").assertIsSelected()
        compose.onNodeWithContentDescription("Exercise heading for Handstand").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Set 2 for Handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("Exercise heading for Handstand").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(runBlocking { db.editorDao().sets().first().any { it.holdMillis == 5_000L && it.rpe == 8.0 } })
        compose.onNodeWithContentDescription("More actions for Handstand").assertDoesNotExist()
        compose.onNodeWithText("Substitute exercise").assertDoesNotExist()
    }

    @Test
    fun recordedTotalsReopenCompactlyWithoutLosingFractionalDurationOrAttempts() {
        listOf(30_000L to "30", 30_125L to "30.125").forEach { (total, displayed) ->
            val recorded = emptySet("row", 1).copy(
                holdMillis = 9_000L, tutEntriesMillis = encodeTimingMillis(List(3) { 3_000L }),
                timeUnderTensionMillis = total,
            )
            val draft = draftFromSet(recorded, true, null)
            assertEquals(displayed, draft.timeUnderTension)
            val config = ExerciseInputConfig().withResistance(ResistanceMetric.BODYWEIGHT)
                .withMeasurement(MeasurementMetric.SKILL_HOLD)
            val parsed = requireNotNull(WorkoutSetDraftLogic(config).parsedSet(draft))
            assertEquals(total, parsed.timeUnderTensionMillis)
            assertEquals(List(3) { 3_000L }, timingMillis(parsed.tutEntriesMillis))
            assertEquals(9_000L, parsed.holdMillis)
        }
    }

    @Test
    fun timingFieldsRemainDirectlyEditableWithOneSumAndPreserveRecordedTotalTime() {
        val workout = start()
        runBlocking {
            val row = db.editorDao().workoutExercises().first().first {
                it.workoutId == workout && it.actualExerciseId == "core:handstand"
            }
            db.editorDao().set(emptySet(row.id, 1).copy(
                variationId = "handstand-wall", timeUnderTensionMillis = 30_000L,
            ))
        }
        show(workout)
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("3")
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").performTextInput("3")
        compose.onNodeWithContentDescription("TUT attempt 3 for Handstand set 1").performTextInput("3")
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").assertTextContains("30")
        compose.onNodeWithContentDescription("Open Total Time stopwatch for Handstand set 1").assertDoesNotExist()
        compose.waitUntil(15_000) {
            runBlocking {
                db.editorDao().sets().first().any {
                    timingMillis(it.tutEntriesMillis) == List(3) { 3_000L } && it.timeUnderTensionMillis == 30_000L
                }
            }
        }

        compose.onNodeWithContentDescription("TUT total for Handstand set 1").assertTextContains("9 s /", substring = true)
        compose.onNodeWithText("4s + 6s = 10s · Total 30s").assertDoesNotExist()
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Wall handstand")
        compose.onNodeWithContentDescription("TUT attempt 1 for Handstand set 1").assertTextContains("3")
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").assertTextContains("3")
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").performTextReplacement("45")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any {
                timingMillis(it.tutEntriesMillis) == List(3) { 3_000L } && it.timeUnderTensionMillis == 45_000L
            } }
        }
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").performTextReplacement("30")
        try {
            compose.onNodeWithContentDescription("Complete set 1 for Handstand").performClick()
            compose.onNodeWithContentDescription("Add TUT entry for Handstand set 1").assertDoesNotExist()
            compose.onNodeWithContentDescription("TUT attempt 4 for Handstand set 1").assertDoesNotExist()
            val last = compose.onNodeWithContentDescription("Timing grid for Handstand set 1").getUnclippedBoundsInRoot()
            val sum = compose.onNodeWithContentDescription("Timing total for Handstand set 1").getUnclippedBoundsInRoot()
            assertEquals("Locked partial sum follows the timing grid with the same gap", 2f, (sum.left - last.right).value, 0.5f)
            val totalInput = compose.onNodeWithContentDescription("Total Time for Handstand set 1").getUnclippedBoundsInRoot()
            assertEquals("Locked inline timing numbers align without counting the caption as numeric height",
                (last.top + last.bottom).value / 2, (totalInput.top + totalInput.bottom).value / 2, 0.5f)
        } finally {
            RestTimer.cancel(ApplicationProvider.getApplicationContext())
        }
    }

    @Test
    fun previousResultMatchesExactVariantAndOrdinalOnBorder() = assertPreviousResultPlacement(1f)

    @Test
    fun previousResultStaysOnBorderAt320DpAndDoubleFontScale() = assertPreviousResultPlacement(2f)

    private fun assertPreviousResultPlacement(fontScale: Float) {
        val workout = start()
        runBlocking {
            val row = db.editorDao().workoutExercises().first().first {
                it.workoutId == workout && it.actualExerciseId == "core:handstand"
            }
            val session = db.editorDao().workouts().first().first { it.id == workout }
            listOf(
                Triple("previous-wall", 4_000L, "handstand-wall"),
                Triple("previous-free", 2_000L, "handstand-free"),
                Triple("unfinished-wall", 1_000L, "handstand-wall"),
            ).forEach { (id, offset, variant) ->
                val finished = id != "unfinished-wall"
                db.editorDao().workout(session.copy(
                    id = id,
                    status = if (finished) "FINISHED" else "DRAFT",
                    startedAt = session.startedAt - offset - 60_000L,
                    finishedAt = if (finished) session.startedAt - offset else null,
                ))
                db.editorDao().workoutExercise(row.copy(id = "$id-exercise", workoutId = id))
                val durations = when (id) {
                    "previous-wall" -> listOf(7_000L, 9_000L)
                    "previous-free" -> listOf(11_000L)
                    else -> listOf(99_000L)
                }
                durations.forEachIndexed { index, duration ->
                    db.editorDao().set(emptySet("$id-exercise", index + 1).copy(
                        variationId = variant,
                        holdMillis = duration,
                    ))
                }
            }
        }
        showAtSize(workout, if (fontScale == 1f) 411.dp else 320.dp, fontScale)
        compose.waitUntil(15_000) {
            compose.onAllNodes(
                hasContentDescription("Previous result for Handstand set 1") and hasText("Prev: 7s"),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        fun assertInline(ordinal: Int) {
            val comparison = compose.onNodeWithContentDescription("Previous result for Handstand set $ordinal")
            val note = comparison.getUnclippedBoundsInRoot()
            val tile = compose.onNodeWithContentDescription("Set $ordinal for Handstand")
                .getUnclippedBoundsInRoot()
            assertTrue("Previous result sits on the right", note.left >= tile.left + (tile.right - tile.left) / 3)
            assertTrue("Previous result overlaps the top border", note.top < tile.top && note.bottom > tile.top)
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            comparison.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            val textWidth = layouts.single().getLineRight(0) - layouts.single().getLineLeft(0)
            assertTrue("Border mask fits actual comparison text, not an allocated column",
                comparison.fetchSemanticsNode().boundsInRoot.width <= textWidth + with(compose.density) { 4.dp.toPx() } + 2f)
        }
        compose.onNodeWithContentDescription("Previous result for Handstand set 1").assertIsDisplayed()
        assertInline(1)
        compose.onNodeWithContentDescription("Previous result for Handstand set 2").assertTextEquals("Prev: 9s")
        compose.onNodeWithContentDescription("Previous result for Handstand set 3").assertDoesNotExist()
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Previous result for Handstand set 1").assertTextEquals("Prev: 7s")
        assertInline(1)

        compose.onNodeWithContentDescription("Set 2 for Handstand")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        compose.onNodeWithContentDescription("Previous result for Handstand set 1").assertTextEquals("Prev: 7s")
        compose.onNodeWithContentDescription("Previous result for Handstand set 2").assertTextEquals("Prev: 9s")
        assertInline(2)
        compose.onNodeWithContentDescription("Change variant for Handstand set 2").performScrollTo().performClick()
        variantOption("Freestanding handstand").performClick()
        compose.onNodeWithContentDescription("Previous result for Handstand set 2").assertDoesNotExist()
        expandSet("Handstand", 2)
        compose.onNodeWithContentDescription("Previous result for Handstand set 2").assertDoesNotExist()
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performScrollTo().performClick()
        variantOption("Freestanding handstand").performClick()
        compose.onNodeWithContentDescription("Previous result for Handstand set 1").assertTextEquals("Prev: 11s")
        compose.onNodeWithText("Prev: 99s").assertDoesNotExist()
    }

    @Test
    fun emptyTilesProvideFieldsAndLabelledFooterActionsShareARow() {
        val workout = start()
        show(workout)
        compose.onNodeWithText("3 sets").assertDoesNotExist()
        compose.onNodeWithContentDescription("Workout exercise list")
            .performScrollToNode(hasContentDescription("Set 1 for Dip"))
        compose.onNodeWithText("Ready to log").assertDoesNotExist()
        compose.onNodeWithContentDescription("Reps for Dip set 2").assertIsEnabled()
        compose.onNodeWithContentDescription("Add set for Dip").performScrollTo().assertTextContains("Add set")
        compose.onNodeWithContentDescription("Show notes for Dip").performScrollTo().assertTextContains("Notes")
        val addBounds = compose.onNodeWithContentDescription("Add set for Dip").fetchSemanticsNode().boundsInRoot
        val notesBounds = compose.onNodeWithContentDescription("Show notes for Dip").fetchSemanticsNode().boundsInRoot
        assertTrue(kotlin.math.abs(addBounds.center.y - notesBounds.center.y) < 2f)
        assertTrue(addBounds.right <= notesBounds.left)

        compose.onNodeWithContentDescription("Add set for Dip").performClick()
        compose.onNodeWithContentDescription("Reps for Dip set 4").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Show notes for Dip").performScrollTo().performClick()
        compose.onAllNodes(hasText("Exercise notes") and hasSetTextAction())[0].performTextInput("Keep elbows close")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().workoutExercises().first().any { it.notes == "Keep elbows close" } }
        }
    }

    @Test
    fun consecutiveRunsNameTheirFirstSetAndKeepEveryPickerAccessible() {
        val workout = start()
        show(workout)
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Wall handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 2").assertDoesNotExist()
        compose.onNodeWithContentDescription("Change variant for Handstand set 2").assertExists()
        compose.onNodeWithText("Freestanding handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performClick()
        compose.onNodeWithText("Configure metrics").assertDoesNotExist()
        compose.onNodeWithText("Configure bands").assertDoesNotExist()
        variantOption("Wall handstand").assertIsSelected()
        variantOption("Freestanding handstand").assertIsNotSelected().performClick()
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Freestanding handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 2").assertTextEquals("Wall handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 3").assertDoesNotExist()
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Freestanding handstand")
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performClick()
        variantOption("Freestanding handstand").assertIsSelected().performClick()
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Freestanding handstand")
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun variantHeadersUseIdentityAndSnapshotsAndRecomputeAfterEditsAndDeletion() {
        val workout = start()
        val row = runBlocking {
            val row = db.editorDao().workoutExercises().first().first {
                it.workoutId == workout && it.actualExerciseId == "core:handstand"
            }
            db.editorDao().workoutExercise(row.copy(targetSets = 5, targets = "5 sets · 4s hold"))
            listOf("handstand-wall", "handstand-wall", "handstand-free", "handstand-free", "handstand-wall")
                .forEachIndexed { index, variant ->
                    db.editorDao().set(emptySet(row.id, index + 1).copy(
                        variationId = variant,
                        variationNameSnapshot = "Recorded handstand",
                        variationInputConfigSnapshot = ExerciseInputConfig(timeHeld = true).encode(),
                        holdMillis = (index + 1) * 1_000L,
                    ))
                }
            row
        }
        show(workout)
        compose.onNodeWithText("5 sets · 4s hold").assertExists()
        listOf(1, 3, 5).forEach {
            compose.onNodeWithContentDescription("Variant for Handstand set $it").assertTextEquals("Recorded handstand")
        }
        listOf(2, 4).forEach {
            compose.onNodeWithContentDescription("Variant for Handstand set $it").assertDoesNotExist()
            compose.onNodeWithContentDescription("Change variant for Handstand set $it").assertExists()
        }
        compose.onNodeWithContentDescription("Change variant for Handstand set 2").performScrollTo().performClick()
        variantOption("Freestanding handstand").performClick()
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any {
                it.workoutExerciseId == row.id && it.ordinal == 2 && it.variationId == "handstand-free"
            } }
        }
        compose.onNodeWithContentDescription("Variant for Handstand set 2").assertTextEquals("Freestanding handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 3").assertTextEquals("Recorded handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 4").assertDoesNotExist()
        compose.onNodeWithContentDescription("Variant for Handstand set 5").assertTextEquals("Recorded handstand")

        compose.onNodeWithContentDescription("Set 2 for Handstand").performScrollTo()
        val swipe = compose.onNodeWithContentDescription("Swipe actions for set 2 for Handstand")
        swipe.performCustomAccessibilityActionWithLabel("Reveal delete")
        swipe.performCustomAccessibilityActionWithLabel("Delete set 2 for Handstand")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().workoutExerciseById(row.id)?.targetSets == 4 }
        }
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Recorded handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 2").assertTextEquals("Recorded handstand")
        compose.onNodeWithContentDescription("Variant for Handstand set 3").assertDoesNotExist()
        compose.onNodeWithContentDescription("Variant for Handstand set 4").assertTextEquals("Recorded handstand")
        compose.onNodeWithContentDescription("Set 5 for Handstand").assertDoesNotExist()
    }

    @Test
    fun iconActionsRemainAccessibleAndTimingSummaryIsNotRepeated() {
        val workout = start()
        show(workout)
        compose.onNodeWithText("Next set").assertDoesNotExist()
        compose.onNodeWithText("Add time", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Complete set 1 for Handstand")
            .assertHasClickAction().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextInput("4")
        val addTime = compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1")
            .assert(hasSetTextAction()).performScrollTo()
        val tileBounds = compose.onNodeWithContentDescription("Set 1 for Handstand").fetchSemanticsNode().boundsInRoot
        assertTrue(addTime.fetchSemanticsNode().boundsInRoot.right < tileBounds.center.x)
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").performTextInput("6")
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("TUT total for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription("TUT total for Handstand set 1").assertCountEquals(1)
        compose.onNodeWithText("TUT total:", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Complete set 1 for Handstand")
            .performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 2").assertExists()
        compose.onNodeWithContentDescription("TUT attempt 1 for Handstand set 1").assertTextEquals("4", "s")
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").assertTextEquals("6", "s")
    }

    @Test
    fun plancheShowsDirectBandGridAndCompactHoldAtOrdinaryReviewSize() = assertPlancheCompactRow(1f)

    @Test
    fun plancheCompactInputsWrapWithoutClippingAt320DpAndDoubleFontScale() = assertPlancheCompactRow(2f)

    private fun assertPlancheCompactRow(fontScale: Float) {
        val workout = start()
        showAtSize(workout, if (fontScale == 1f) 411.dp else 320.dp, fontScale)
        compose.onNodeWithContentDescription("Workout exercise list")
            .performScrollToNode(hasContentDescription("Set 1 for Planche"))
        if (fontScale > 1f) expandSet("Planche", 1)
        compose.onNodeWithContentDescription("Set 1 for Planche").performScrollTo()
        val descriptions = listOf("Time Under Tension", "Yellow band", "Red band", "Black band", "Purple band", "RPE")
        val fields = descriptions.map {
            compose.onNodeWithContentDescription("$it for Planche set 1").assertIsDisplayed().getUnclippedBoundsInRoot()
        }
        val header = compose.onNodeWithContentDescription("Change variant for Planche set 1")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        val tick = compose.onNodeWithContentDescription("Complete set 1 for Planche")
            .assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Variant selector remains above the body", header.top < tick.top)
        fields.forEachIndexed { index, a ->
            fields.drop(index + 1).forEach { b ->
                assertTrue("Measurements must not overlap", a.right <= b.left || b.right <= a.left ||
                    a.bottom <= b.top || b.bottom <= a.top)
            }
        }
        val bands = fields.subList(1, 5)
        assertTrue("Four band dots stay left aligned on one row", bands.all { it.top == bands.first().top })
        assertTrue("RPE remains beside completion", fields.last().right <= tick.left)
        compose.onNodeWithContentDescription("Time Under Tension for Planche set 1")
            .assertTextContains("s").performTextInput("12")
        compose.onNodeWithContentDescription("RPE for Planche set 1").performScrollTo().performTextInput("8")
        compose.onNodeWithContentDescription("Yellow band for Planche set 1")
            .performScrollTo().performTouchInput { click() }
        compose.onNodeWithContentDescription("Yellow band for Planche set 1").assertIsSelected()
        compose.onNodeWithContentDescription("Red band for Planche set 1").assertIsNotSelected()
        compose.waitUntil(15_000) {
            runBlocking {
                val row = db.editorDao().workoutExercises().first().single {
                    it.workoutId == workout && it.actualExerciseId == "core:planche"
                }
                db.editorDao().sets().first().any { it.workoutExerciseId == row.id && it.holdMillis == 12_000L && it.rpe == 8.0 }
            }
        }
    }

    @Test
    fun simpleExerciseKeepsInputsInOneRowWithoutASeparateCompletionRow() {
        val workout = start("demo-day-pull")
        show(workout)
        compose.onNodeWithContentDescription("Workout exercise list")
            .performScrollToNode(hasContentDescription("Set 1 for Back extension"))
        compose.onNodeWithContentDescription("Change variant for Back extension set 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Variant for Back extension set 1").assertDoesNotExist()
        val fields = listOf("Resistance" to 60.dp, "Reps" to 64.dp, "RPE" to 64.dp).map { (label, expectedWidth) ->
            val field = compose.onNodeWithContentDescription("$label for Back extension set 1")
            val width = field.getUnclippedBoundsInRoot().let { bounds -> bounds.right - bounds.left }
            assertEquals("Input widths stay fixed: $label", expectedWidth.value, width.value, 0.5f)
            val height = field.getUnclippedBoundsInRoot().let { bounds -> bounds.bottom - bounds.top }
            assertEquals("Every numeric box uses the compact timing height: $label", 24f, height.value, 0.5f)
            field
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        assertTrue(fields.zipWithNext().all { (left, right) ->
            kotlin.math.abs(left.center.y - right.center.y) < 2f && left.right <= right.left
        })
        val dividers = compose.onAllNodesWithTag("Value divider for Back extension set 1", useUnmergedTree = true)
            .assertCountEquals(2).fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }
        dividers.zip(fields.zipWithNext()).forEach { (divider, neighbours) ->
            val (left, right) = neighbours
            assertTrue("Divider stays within the inter-field gap", divider.left >= left.right && divider.right <= right.left)
            assertTrue("Divider is shorter than the input", divider.height < left.height)
            assertTrue("Divider remains vertically centred", kotlin.math.abs(divider.center.y - left.center.y) < 2f)
        }
        assertTrue("Single-row inputs share a fixed height", fields.map { it.height }.distinct().size == 1)
        val completion = compose.onNodeWithContentDescription("Complete set 1 for Back extension")
            .assertHasClickAction().fetchSemanticsNode().boundsInRoot
        assertTrue("Tick remains beside the compact primary row", kotlin.math.abs(completion.center.y - fields.first().center.y) < 8f)
        assertTrue(completion.left >= fields.last().right)
        val bounds = compose.onNodeWithContentDescription("Swipe actions for set 1 for Back extension").getUnclippedBoundsInRoot()
        val height = bounds.bottom - bounds.top
        assertTrue("Removing the header does not increase the simple tile's old occupied height, was $height", height <= 96.5.dp)
        for (ordinal in 2..3) {
            val empty = compose.onNodeWithContentDescription("Set $ordinal for Back extension")
                .performScrollTo().getUnclippedBoundsInRoot()
            assertTrue("Every simple tile remains compact with editable fields", empty.bottom - empty.top <= 160.dp)
            compose.onNodeWithContentDescription("Resistance for Back extension set $ordinal").assertIsEnabled()
            compose.onNodeWithContentDescription("Change variant for Back extension set $ordinal").assertDoesNotExist()
            compose.onNodeWithContentDescription("Reps for Back extension set $ordinal").assertIsEnabled()
            compose.onNodeWithContentDescription("Complete set $ordinal for Back extension").assertIsNotEnabled()
        }
    }

    @Test
    fun borderlessTilesRetainOneAccessibleActiveSelection() {
        val workout = start()
        show(workout)
        val first = compose.onNodeWithContentDescription("Set 1 for Handstand").performScrollTo()
        first.assertIsSelected()
        val pixels = first.captureToImage().toPixelMap()
        assertEquals("No left set outline", pixels[0, pixels.height / 2], pixels[4, pixels.height / 2])
        compose.onAllNodesWithTag("Value divider for Handstand set 1", useUnmergedTree = true)
            .assertCountEquals(1)
        compose.onNodeWithContentDescription("Set 2 for Handstand")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        first.assertIsNotSelected()
        compose.onNodeWithContentDescription("Set 2 for Handstand").assertIsSelected()
    }

    @Test
    fun everyTileKeepsDirectFieldsAndVariantHeaderWithoutExpansion() {
        val workout = start()
        show(workout)
        val tile = compose.onNodeWithContentDescription("Set 1 for Handstand")
        for (ordinal in 1..3) {
            compose.onNodeWithContentDescription("Time Under Tension for Handstand set $ordinal").assertIsEnabled()
            compose.onNodeWithContentDescription("Complete set $ordinal for Handstand").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Change variant for Handstand set $ordinal").assertExists()
        }
        val bounds = tile.getUnclippedBoundsInRoot()
        val swap = compose.onNodeWithContentDescription("Change variant for Handstand set 1").getUnclippedBoundsInRoot()
        val tick = compose.onNodeWithContentDescription("Complete set 1 for Handstand").getUnclippedBoundsInRoot()
        assertTrue("Variant selector starts at the tile's leading edge", swap.left < bounds.left + (bounds.right - bounds.left) / 2)
        assertTrue("Variant selector is above completion", swap.top < tick.top)
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 2")
            .performScrollTo().performTextInput("4")
        compose.onNodeWithContentDescription("Complete set 2 for Handstand").assertIsEnabled()
        compose.onNodeWithContentDescription("Complete set 1 for Handstand").assertIsNotEnabled()
        tile.performScrollTo().performTouchInput { click(androidx.compose.ui.geometry.Offset(8f, 8f)) }
        compose.onNodeWithContentDescription("Open set 1 for Handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close set 1 for Handstand").assertDoesNotExist()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 2").assertTextContains("4")
    }

    @Test
    fun configuredOrderRendersCompactFieldsAndKeepsTimingTogetherAtLargeFont() {
        val workout = start()
        runBlocking {
            val variant = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(variant.copy(inputConfig = ExerciseInputConfig(
                timeHeld = true, manyTuts = true, timeUnderTension = true,
                measurementOrder = listOf(ExerciseMeasurement.RPE, ExerciseMeasurement.TOTAL_TIME, ExerciseMeasurement.TUT, ExerciseMeasurement.REPS),
            ).encode()))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                VibeCheckTheme { Box(Modifier.width(320.dp)) { WorkoutEditor(vm, workout, {}, {}) } }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Time Under Tension for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("RPE for Handstand set 1").performScrollTo()
        val ordered = listOf("Time Under Tension", "RPE").map {
            compose.onNodeWithContentDescription("$it for Handstand set 1").getUnclippedBoundsInRoot()
        }
        assertTrue("Configured fields must wrap in order: $ordered", ordered.zipWithNext().all { (a, b) ->
            a.top < b.top || a.right <= b.left
        })
        compose.onNodeWithContentDescription("Total Time for Handstand set 1").assertExists()
        compose.onNodeWithContentDescription("Open Total Time stopwatch for Handstand set 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performScrollTo().performTextInput("4")
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").performScrollTo().performTextInput("6")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.holdMillis == 10_000L && it.timeUnderTensionMillis == null } }
        }
    }

    @Test
    fun changingRecordedSetVariantPreservesAllMeasurements() {
        val workout = start()
        val recorded = runBlocking {
            val config = ExerciseInputConfig(
                timeHeld = true, manyTuts = true, timeUnderTension = true, bandResistance = true).encode()
            listOf("handstand-wall", "handstand-free").forEach { id ->
                db.editorDao().variation(db.editorDao().variationById(id)!!.copy(inputConfig = config))
            }
            val row = db.editorDao().workoutExercises().first().first { it.workoutId == workout && it.actualExerciseId == "core:handstand" }
            val set = emptySet(row.id, 1).copy(
                variationId = "handstand-wall", reps = 5.0, weightKg = 20.0, addedWeightKg = 3.0,
                holdMillis = 10_000L, tutEntriesMillis = encodeTimingMillis(listOf(4_000L, 6_000L)),
                timeUnderTensionMillis = 30_000L, rpe = 8.0,
            )
            db.editorDao().saveSetWithSnapshots(set, listOf("band-yellow", "band-black"), false)
            set
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performScrollTo().performClick()
        compose.onNodeWithText("Freestanding handstand").performClick()
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.id == recorded.id && it.variationId == "handstand-free" } }
        }
        val updated = runBlocking { db.editorDao().sets().first().single { it.id == recorded.id } }
        assertEquals(recorded.reps, updated.reps)
        assertEquals(recorded.weightKg, updated.weightKg)
        assertEquals(recorded.addedWeightKg, updated.addedWeightKg)
        assertEquals(recorded.tutEntriesMillis, updated.tutEntriesMillis)
        assertEquals(recorded.holdMillis, updated.holdMillis)
        assertEquals(recorded.timeUnderTensionMillis, updated.timeUnderTensionMillis)
        assertEquals(recorded.rpe, updated.rpe)
        assertEquals(listOf("band-yellow", "band-black"), runBlocking { db.editorDao().setBands().first().filter { it.setId == recorded.id }.map { it.bandId } })
        compose.onNodeWithContentDescription("Set 1 for Handstand").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Editing, collapsed"),
        )
        compose.onNodeWithContentDescription("TUT attempt 2 for Handstand set 1").assertTextContains("6")
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performScrollTo().performClick()
        variantOption("Freestanding handstand").assertIsSelected()
    }

    @Test
    fun inheritedMeasurementOrderIsFrozenInRecordedVariationSnapshots() {
        start()
        runBlocking {
            val parent = db.editorDao().exerciseById("core:handstand")!!
            val inherited = listOf(ExerciseMeasurement.RPE, ExerciseMeasurement.TUT)
            db.editorDao().exercise(parent.copy(inputConfig = ExerciseInputConfig(
                timeHeld = true, measurementOrder = inherited,
            ).encode()))
            val variant = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(variant.copy(inputConfig = ExerciseInputConfig(timeHeld = true).encode()))
            val row = db.editorDao().workoutExercises().first().first { it.actualExerciseId == parent.id }
            db.editorDao().workoutExercise(row.copy(inputConfig = ExerciseInputConfig(
                timeHeld = true, measurementOrder = inherited,
            ).encode()))
            val set = emptySet(row.id, 1).copy(variationId = variant.id, holdMillis = 4_000L)
            db.editorDao().saveSetWithSnapshots(set, emptyList(), false)
            val saved = db.editorDao().sets().first().single { it.id == set.id }
            assertEquals(inherited, ExerciseInputConfig.decode(saved.variationInputConfigSnapshot, variant.trackingType).measurementOrder)
            db.editorDao().exercise(parent.copy(inputConfig = ExerciseInputConfig(
                timeHeld = true, measurementOrder = inherited.reversed(),
            ).encode()))
            db.editorDao().saveSetWithSnapshots(saved.copy(holdMillis = 5_000L), emptyList(), false)
            assertEquals(saved.variationInputConfigSnapshot, db.editorDao().sets().first().single { it.id == set.id }.variationInputConfigSnapshot)
        }
    }

    @Test
    fun editingTimingPreservesRepetitionsAndLoad() {
        val workout = start()
        runBlocking {
            val row = db.editorDao().workoutExercises().first().first { it.workoutId == workout && it.actualExerciseId == "core:handstand" }
            val variant = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(variant.copy(inputConfig = ExerciseInputConfig(weightUnit = "kg", timeHeld = true, manyTuts = true).encode()))
            db.editorDao().set(emptySet(row.id, 1).copy(variationId = variant.id, weightKg = 20.0, reps = 5.0, holdMillis = 4_000L))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() }
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1").performTextReplacement("9")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.holdMillis == 9_000L && it.reps == 5.0 && it.weightKg == 20.0 } }
        }
    }

    @Test
    fun bandSelectionIsExplicitAndSurvivesCollapsingTheExercise() {
        val workout = start()
        runBlocking {
            val dip = db.editorDao().workoutExercises().first().first { it.workoutId == workout && it.actualExerciseId == "core:dip" }
            db.editorDao().workoutExercise(dip.copy(inputConfig = ExerciseInputConfig(reps = true, bodyweight = true, bandResistance = true).encode()))
        }
        show(workout)
        compose.onNodeWithContentDescription("Workout exercise list")
            .performScrollToNode(hasContentDescription("Set 1 for Dip"))
        compose.onNodeWithContentDescription("Yellow band for Dip set 1").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Yellow band for Dip set 1").assertIsSelected()
        compose.onNodeWithContentDescription("Black band for Dip set 1").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Not selected"),
        )
        compose.onNodeWithContentDescription("Reps for Dip set 1").performScrollTo().performTextInput("5")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().setBands().first().any { it.bandId == "band-yellow" } }
        }
        compose.onNodeWithContentDescription("Reps for Dip set 1").assertTextContains("5")
        compose.onNodeWithContentDescription("Exercise heading for Dip").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Exercise heading for Dip").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Set 1 for Dip").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Yellow band for Dip set 1").performScrollTo().assertIsSelected()
    }

    @Test
    fun historyShowsBothRecordedTimingMeasurementsWithoutOpeningEdit() {
        val workout = start()
        runBlocking {
            val row = db.editorDao().workoutExercises().first().first { it.workoutId == workout && it.actualExerciseId == "core:handstand" }
            val session = db.editorDao().workouts().first().first { it.id == workout }
            db.editorDao().workout(session.copy(status = "FINISHED", finishedAt = System.currentTimeMillis()))
            db.editorDao().set(emptySet(row.id, 1).copy(holdMillis = 3_000, timeUnderTensionMillis = 12_000))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { HistoryScreen(vm, {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("3s · Total 12s").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("3s · Total 12s").assertExists()
    }

    @Test
    fun oneManyTogglePersistsAndRestoresIntoFreshSeededDatabase() {
        start()
        val vm = lifecycle.own(EditorViewModel(db))
        val planche = runBlocking { db.editorDao().exerciseById("core:planche")!! }
        compose.setContent {
            VibeCheckTheme {
                ExerciseSettingsDialog(planche, {}) { vm.saveExerciseSettings(it) }
            }
        }
        compose.onNodeWithContentDescription("Hold duration").assertIsSelected()
        compose.onNodeWithContentDescription("Skill hold").performScrollTo().performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(15_000) {
            runBlocking { ExerciseInputConfig.decode(db.editorDao().exerciseById(planche.id)!!.inputConfig, planche.trackingType).manyTuts }
        }
        runBlocking {
            val backup = DataTransfer.export(db)
            val context = ApplicationProvider.getApplicationContext<Context>()
            val restored = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
            try {
                DatabaseSeeder(context, restored).seedIfNeeded()
                DataTransfer.import(restored, backup)
                assertTrue(ExerciseInputConfig.decode(restored.editorDao().exerciseById(planche.id)!!.inputConfig, planche.trackingType).manyTuts)
            } finally {
                restored.close()
            }
        }
    }

    @Test
    fun changingVariantDoesNotReinterpretRepsAsWeight() {
        val workout = start()
        runBlocking {
            val variation = db.editorDao().variationById("handstand-free")!!
            db.editorDao().variation(variation.copy(inputConfig = ExerciseInputConfig(weightUnit = "kg", reps = true).encode()))
            val wall = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(wall.copy(inputConfig = ExerciseInputConfig(reps = true).encode()))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Reps for Handstand set 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Reps for Handstand set 1").performTextInput("5")
        expandSet("Handstand", 1)
        compose.onNodeWithContentDescription("Change variant for Handstand set 1").performClick()
        compose.onNodeWithText("Freestanding handstand").performClick()
        compose.onNodeWithContentDescription("Reps for Handstand set 1").assertTextContains("5")
        compose.onNodeWithContentDescription("Resistance for Handstand set 1").assertTextEquals("", "kg")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.variationId == "handstand-free" && it.reps == 5.0 && it.weightKg == null } }
        }
    }

    @Test
    fun romOnlySetPersistsAndCanBeCompletedWithoutOtherMeasurements() {
        val workout = start()
        runBlocking {
            val wall = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(wall.copy(inputConfig = ExerciseInputConfig()
                .withResistance(ResistanceMetric.BODYWEIGHT)
                .withMeasurement(MeasurementMetric.RANGE_OF_MOTION).encode()))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Range of motion for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Range of motion for Handstand set 1").performTextInput("45")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.romValue == 45.0 && it.romUnit == "degrees" && it.reps == null && it.holdMillis == null } }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            compose.onNodeWithContentDescription("Complete set 1 for Handstand").performClick()
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").assertExists()
        } finally {
            RestTimer.cancel(context)
        }
    }

    @Test
    fun explicitMetricKeysControlLoggingDespiteLegacyBackingFlags() {
        val workout = start()
        runBlocking {
            val wall = db.editorDao().variationById("handstand-wall")!!
            db.editorDao().variation(wall.copy(
                inputConfig = "resistanceMetric=KILOGRAMS;measurementMetric=REPETITIONS;reps=false",
            ))
        }
        val vm = lifecycle.own(EditorViewModel(db))
        compose.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Resistance for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Resistance for Handstand set 1").performTextInput("20")
        compose.onNodeWithContentDescription("Reps for Handstand set 1").performTextInput("6")
        compose.waitUntil(15_000) {
            runBlocking { db.editorDao().sets().first().any { it.weightKg == 20.0 && it.reps == 6.0 && it.holdMillis == null } }
        }
        compose.onNodeWithContentDescription("Complete set 1 for Handstand").assertIsEnabled()
    }

    @Test
    fun worstCaseAndSimpleLoggerRenderInTheRealExerciseContainer() {
        val workout = start()
        runBlocking {
            repeat(4) { index ->
                db.editorDao().band(BandEntity("review-band-$index", "Custom $index", 4.0 + index, 0xFF5085D6L))
            }
            val config = ExerciseInputConfig(
                bandResistance = true, timeHeld = true, timeUnderTension = true, manyTuts = true,
            ).encode()
            val exercise = db.editorDao().exerciseById("core:handstand")!!.copy(
                id = "review-skill-exercise", canonicalName = "Skill hold demo", isCustom = true, inputConfig = config,
            )
            db.editorDao().exercise(exercise)
            val variant = db.editorDao().variationById("handstand-wall")!!.copy(
                id = "review-skill-variant", exerciseId = exercise.id, name = "Test variant",
                isSeeded = false, inputConfig = config,
            )
            db.editorDao().variation(variant)
            val rows = db.editorDao().workoutExercises().first().filter { it.workoutId == workout }
            rows.forEach { db.editorDao().workoutExercise(it.copy(position = it.position + 1)) }
            val handstandRow = db.editorDao().workoutExercises().first().first {
                it.workoutId == workout && it.actualExerciseId == "core:handstand"
            }
            db.editorDao().saveSetWithSnapshots(emptySet(handstandRow.id, 1).copy(
                variationId = "handstand-free",
            ), emptyList(), false)
            val row = handstandRow.copy(id = "review-skill-row", plannedExerciseId = exercise.id, actualExerciseId = exercise.id,
                exerciseName = exercise.canonicalName, inputConfig = config, position = 0)
            db.editorDao().workoutExercise(row)
            db.editorDao().saveSetWithSnapshots(emptySet(row.id, 1).copy(
                variationId = variant.id, holdMillis = 36_000L,
                tutEntriesMillis = encodeTimingMillis(List(8) { (it + 1) * 1_000L }),
                timeUnderTensionMillis = 45_000L, rpe = 8.0,
            ), listOf("band-yellow", "band-black"), false)
            db.editorDao().saveSetWithSnapshots(emptySet(row.id, 2).copy(
                variationId = variant.id, holdMillis = 9_000L,
                tutEntriesMillis = encodeTimingMillis(List(3) { 3_000L }),
                timeUnderTensionMillis = 30_000L, rpe = 8.0,
            ), listOf("band-red"), false)
        }
        val vm = lifecycle.own(EditorViewModel(db))
        val font = androidx.compose.runtime.mutableFloatStateOf(1f)
        val width = androidx.compose.runtime.mutableStateOf(411.dp)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, font.floatValue)) {
                VibeCheckTheme { Box(Modifier.width(width.value)) { WorkoutEditor(vm, workout, {}, {}) } }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("TUT attempt 8 for Skill hold demo set 1").fetchSemanticsNodes().isNotEmpty()
        }
        var visibleTickGaps = emptyList<Float>()
        fun capture(name: String) {
            val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, name)
            file.outputStream().use {
                assertTrue(compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            val exercise = when (name) {
                "composable-simple.png" -> "Bench press"
                "composable-empty-skill.png" -> "Handstand"
                else -> "Skill hold demo"
            }
            val bounds = compose.onNodeWithContentDescription("Swipe actions for set 1 for $exercise").getUnclippedBoundsInRoot()
            val previousHeight = when (name) {
                "composable-simple.png" -> 56f
                "composable-empty-skill.png" -> 74.28571f
                "composable-large-font.png" -> 384f
                else -> 96.38097f
            }
            assertEquals("Typography preserves the previous exact-build occupied height",
                previousHeight, (bounds.bottom - bounds.top).value, 0.01f)
            if (name == "composable-simple.png") {
                val image = compose.onNodeWithContentDescription("Swipe actions for set 1 for $exercise")
                    .captureToImage().asAndroidBitmap()
                File(file.parentFile, "typography-simple-set.png").outputStream().use {
                    assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            }
            val geometry = buildString {
                appendLine("Occupied tile: ${bounds.right - bounds.left},${bounds.bottom - bounds.top}")
                val rpe = compose.onNodeWithContentDescription("RPE for $exercise set 1").getUnclippedBoundsInRoot()
                appendLine("Normal box: ${rpe.right - rpe.left},${rpe.bottom - rpe.top}")
                if (exercise == "Skill hold demo") {
                    listOf("Resistance grid", "Timing grid", "Timing total", "Total Time").forEach { label ->
                        val group = compose.onNodeWithContentDescription("$label for $exercise set 1").getUnclippedBoundsInRoot()
                        appendLine("$label: ${group.right - group.left},${group.bottom - group.top}")
                    }
                    val attempts = (1..8).map {
                        compose.onNodeWithContentDescription("TUT attempt $it for $exercise set 1").getUnclippedBoundsInRoot()
                    }
                    val first = attempts.first()
                    appendLine("Attempt box: ${first.right - first.left},${first.bottom - first.top}")
                    appendLine("Attempt rows: ${attempts.groupBy { it.top }.values.map { it.size }}")
                    if (name == "composable-worst-case.png") {
                        appendLine("Visible Total-RPE / RPE-check / check-border gaps: $visibleTickGaps dp")
                    }
                }
            }
            File(file.parentFile, name.removeSuffix(".png") + "-geometry.txt")
                .writeText(geometry)
        }
        compose.onNodeWithContentDescription("Complete set 1 for Skill hold demo").assertIsEnabled()
        val groups = listOf("Resistance column", "Measurement column", "RPE column").map {
            compose.onNodeWithContentDescription("$it for Skill hold demo set 1").getUnclippedBoundsInRoot()
        }
        val boxedAttempts = (1..8).map {
            compose.onNodeWithContentDescription("TUT attempt $it for Skill hold demo set 1").getUnclippedBoundsInRoot()
        }
        val boxedCenter = (boxedAttempts.minOf { it.top } + boxedAttempts.maxOf { it.bottom }).value / 2
        val total = compose.onNodeWithContentDescription("Timing total for Skill hold demo set 1").getUnclippedBoundsInRoot()
        assertEquals("The ordinary maximum uses two four-column timing rows",
            listOf(4, 4), boxedAttempts.groupBy { it.top }.values.map { it.size })
        val effort = compose.onNodeWithContentDescription("RPE for Skill hold demo set 1").getUnclippedBoundsInRoot()
        val timingRows = boxedAttempts.groupBy { it.top }.values.toList()
        val horizontalGap = timingRows.first()[1].left - timingRows.first()[0].right
        val verticalGap = timingRows[1].first().top - timingRows.first().first().bottom
        assertEquals("Timing boxes use the same horizontal and vertical spacing",
            verticalGap.value, horizontalGap.value, 0.5f)
        assertEquals("Total uses the same spacing as the accumulated boxes",
            verticalGap.value, (total.left - boxedAttempts.maxOf { it.right }).value, 0.5f)
        val totalBox = compose.onNodeWithContentDescription("Total Time for Skill hold demo set 1").getUnclippedBoundsInRoot()
        assertTrue("Maximum timing group leaves clear space before RPE",
            (effort.left - totalBox.right).value >= 7.5f)
        boxedAttempts.forEach {
            assertEquals("Attempts and RPE have the same frame height",
                (effort.bottom - effort.top).value, (it.bottom - it.top).value, 0.5f)
        }
        assertEquals("Total and RPE have the same frame height",
            (effort.bottom - effort.top).value, (totalBox.bottom - totalBox.top).value, 0.5f)
        assertTrue("All maximum-size groups stay in one ordinary row", groups.all { it.top < effort.bottom && it.bottom > effort.top })
        compose.onNodeWithContentDescription("Total Time for Skill hold demo set 1").assertTextContains("45")
        listOf(groups.first(), groups.last(), total).forEach {
            assertEquals("Worst-case boxes align without the footer biasing their centre",
                boxedCenter, (it.top + it.bottom).value / 2, 0.5f)
        }
        assertTrue("Worst-case groups must not overlap", groups.zipWithNext().all { (a, b) -> a.right <= b.left })
        assertEquals("Resistance and timing stay left-packed",
            8f, (groups[1].left - groups[0].right).value, 0.5f)
        val tickNode = compose.onNodeWithContentDescription("Complete set 1 for Skill hold demo")
        val tick = tickNode.getUnclippedBoundsInRoot()
        val completionGroup = compose.onNodeWithContentDescription("Completion column for Skill hold demo set 1")
            .getUnclippedBoundsInRoot()
        assertEquals("Only the tick stays pinned at the content's right edge",
            completionGroup.right.value, tick.right.value, 0.5f)
        assertTrue("The independently placed tick does not overlap RPE", tick.left >= effort.right)
        val icon = tickNode.captureToImage().asAndroidBitmap()
        val paintedColumns = (0 until icon.width).filter { x ->
            (0 until icon.height).any { y ->
                val colour = icon.getPixel(x, y)
                android.graphics.Color.red(colour) > 160 &&
                    android.graphics.Color.green(colour) > 160 && android.graphics.Color.blue(colour) > 160
            }
        }
        assertTrue("The completion check is visibly painted", paintedColumns.isNotEmpty())
        val dpPerPixel = (tick.right - tick.left).value / icon.width
        val paintedLeft = tick.left.value + paintedColumns.first() * dpPerPixel
        val paintedRight = tick.left.value + (paintedColumns.last() + 1) * dpPerPixel
        val tile = compose.onNodeWithContentDescription("Swipe actions for set 1 for Skill hold demo").getUnclippedBoundsInRoot()
        visibleTickGaps = listOf(
            (effort.left - totalBox.right).value,
            paintedLeft - effort.right.value,
            tile.right.value - paintedRight,
        )
        assertTrue("Full-row spacing matches actual painted check edges: $visibleTickGaps",
            visibleTickGaps.max() - visibleTickGaps.min() <= 1f)
        capture("composable-worst-case.png")
        compose.onNodeWithContentDescription("Workout exercise list").performScrollToNode(hasContentDescription("Exercise heading for Bench press"))
        compose.onNodeWithContentDescription("Reps for Bench press set 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous result for Bench press set 2").assertExists()
        capture("composable-simple.png")
        compose.onNodeWithContentDescription("Workout exercise list").performScrollToNode(hasContentDescription("Exercise heading for Handstand"))
        val emptyAttempt = compose.onNodeWithContentDescription("Time Under Tension for Handstand set 1")
            .performScrollTo().getUnclippedBoundsInRoot()
        val emptyGrid = compose.onNodeWithContentDescription("Timing grid for Handstand set 1").getUnclippedBoundsInRoot()
        assertEquals("Empty Freestanding handstand reserves both timing rows",
            (emptyAttempt.bottom - emptyAttempt.top).value * 2 + 2f,
            (emptyGrid.bottom - emptyGrid.top).value, 0.5f)
        capture("composable-empty-skill.png")
        compose.runOnIdle { font.floatValue = 2f; width.value = 320.dp }
        compose.onNodeWithContentDescription("Workout exercise list").performScrollToNode(hasContentDescription("Set 1 for Skill hold demo"))
        compose.onNodeWithContentDescription("TUT attempt 8 for Skill hold demo set 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("TUT total for Skill hold demo set 1").performScrollTo().assertIsDisplayed()
        capture("composable-large-font.png")
    }

    @Test
    fun variantRoundTripRestoresRawValuesUnitsAndSnapshotsAcrossRecreationAndCompletedChanges() {
        val workout = start()
        val original = runBlocking {
            val wall = db.editorDao().variationById("handstand-wall")!!
            val free = db.editorDao().variationById("handstand-free")!!
            val reps = ExerciseInputConfig().withResistance(ResistanceMetric.BODYWEIGHT)
                .withMeasurement(MeasurementMetric.REPETITIONS).encode()
            db.editorDao().variation(wall.copy(inputConfig = reps))
            db.editorDao().variation(free.copy(inputConfig = ExerciseInputConfig()
                .withResistance(ResistanceMetric.BODYWEIGHT)
                .withMeasurement(MeasurementMetric.RANGE_OF_MOTION).encode()))
            val row = db.editorDao().workoutExercises().first().first {
                it.workoutId == workout && it.actualExerciseId == "core:handstand"
            }
            emptySet(row.id, 1).copy(
                variationId = wall.id, reps = 7.0, romValue = 12.0, romUnit = "cm",
                variationNameSnapshot = "Recorded wall", variationRankSnapshot = 42,
                variationTrackingTypeSnapshot = "BODYWEIGHT_REPS", variationInputConfigSnapshot = reps,
            ).also { db.editorDao().set(it) }
        }
        val vm = lifecycle.own(EditorViewModel(db))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { VibeCheckTheme { WorkoutEditor(vm, workout, {}, {}) } }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Reps for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        fun select(name: String) {
            compose.onNodeWithContentDescription("Change variant for Handstand set 1").performScrollTo().performClick()
            variantOption(name).performClick()
        }
        compose.onNodeWithContentDescription("Reps for Handstand set 1").performTextReplacement("7.")
        select("Freestanding handstand")
        compose.onNodeWithContentDescription("Range of motion for Handstand set 1").assertTextContains("cm", substring = true)
            .performTextReplacement("45")
        compose.waitUntil(15_000) { runBlocking { db.editorDao().setById(original.id)?.romValue == 45.0 } }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Range of motion for Handstand set 1").fetchSemanticsNodes().isNotEmpty()
        }
        select("Wall handstand")
        compose.onNodeWithContentDescription("Reps for Handstand set 1").assertTextContains("7.")
        compose.onNodeWithContentDescription("Variant for Handstand set 1").assertTextEquals("Recorded wall")
        compose.onNodeWithContentDescription("Complete set 1 for Handstand").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Reps for Handstand set 1").performTextReplacement("8")
        compose.waitUntil(15_000) { runBlocking { db.editorDao().setById(original.id)?.reps == 8.0 } }
        val saved = runBlocking { db.editorDao().setById(original.id)!! }
        assertEquals(original.variationNameSnapshot, saved.variationNameSnapshot)
        assertEquals(original.variationRankSnapshot, saved.variationRankSnapshot)
        assertEquals(original.variationTrackingTypeSnapshot, saved.variationTrackingTypeSnapshot)
        assertEquals("cm", saved.romUnit)
        assertEquals(45.0, saved.romValue!!, 0.0)
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            compose.onNodeWithContentDescription("Complete set 1 for Handstand").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithContentDescription("Edit completed set 1 for Handstand").fetchSemanticsNodes().isNotEmpty() &&
                    context.getSharedPreferences("settings", 0).getLong("restEnd", 0) > System.currentTimeMillis()
            }
            val rest = context.getSharedPreferences("settings", 0).getLong("restEnd", 0)
            assertTrue(rest > System.currentTimeMillis())
            select("Freestanding handstand")
            compose.onNodeWithContentDescription("Range of motion for Handstand set 1").assertTextContains("cm", substring = true)
            compose.onNodeWithContentDescription("Edit completed set 1 for Handstand").assertExists()
            assertEquals(rest, context.getSharedPreferences("settings", 0).getLong("restEnd", 0))
        } finally {
            RestTimer.cancel(context)
        }
    }
}

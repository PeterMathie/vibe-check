package com.petermathie.vibecheck

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.petermathie.vibecheck.ui.HomeScreen
import com.petermathie.vibecheck.ui.MainUiState
import com.petermathie.vibecheck.ui.ReorderHandle
import com.petermathie.vibecheck.ui.ReorderItem
import com.petermathie.vibecheck.ui.ReorderOverlayHost
import com.petermathie.vibecheck.ui.WorkoutCompletionEvent
import com.petermathie.vibecheck.ui.rememberReorderState
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.domain.model.ActiveWorkout
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class EndUserControlsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeKeepsDateControlsTogetherWithoutRemovedHeadings() {
        var previewDay: Long? = null
        var selectedMode: TrainingMode? = null
        var state by mutableStateOf(MainUiState())
        compose.setContent {
            VibeCheckTheme {
                HomeScreen(
                    state,
                    {},
                    {},
                    {
                        previewDay = it
                        state = state.copy(homeRecencyDay = it)
                    },
                    { selectedMode = it },
                )
            }
        }

        compose.onNodeWithText("FRESHNESS").assertIsDisplayed()
        val legend = compose.onNodeWithContentDescription("Freshness colour scale", substring = true)
        legend.assertIsDisplayed()
        val frontMapBounds = compose.onNodeWithContentDescription("male front freshness map").fetchSemanticsNode().boundsInRoot
        val backMapBounds = compose.onNodeWithContentDescription("male back freshness map").fetchSemanticsNode().boundsInRoot
        val legendBounds = legend.fetchSemanticsNode().boundsInRoot
        val texture = compose.onNodeWithContentDescription("Freshness panel texture")
        val textureBounds = texture.fetchSemanticsNode().boundsInRoot
        val titleBounds = compose.onNodeWithText("FRESHNESS").fetchSemanticsNode().boundsInRoot
        val texturePixels = texture.captureToImage().toPixelMap()
        assertTrue("texture=$textureBounds title=$titleBounds", textureBounds.top < titleBounds.top)
        assertTrue("texture=$textureBounds map=$frontMapBounds", textureBounds.bottom > frontMapBounds.bottom)
        assertTrue(texturePixels.width > 0 && texturePixels.height > 0)
        assertTrue(
            "front=$frontMapBounds legend=$legendBounds",
            kotlin.math.abs(frontMapBounds.top - legendBounds.top) < 2f,
        )
        assertTrue(
            "front=$frontMapBounds legend=$legendBounds",
            kotlin.math.abs(frontMapBounds.bottom - legendBounds.bottom) < 2f,
        )
        assertTrue(
            "back=$backMapBounds legend=$legendBounds",
            kotlin.math.abs(backMapBounds.top - legendBounds.top) < 2f,
        )
        assertTrue(
            "back=$backMapBounds legend=$legendBounds",
            kotlin.math.abs(backMapBounds.bottom - legendBounds.bottom) < 2f,
        )
        compose.onNodeWithText("Overview").assertDoesNotExist()
        compose.onNodeWithText("Recency, not recovery or fatigue").assertDoesNotExist()
        compose.onNodeWithText("Habits, workouts and stretching").assertDoesNotExist()
        compose.onNodeWithContentDescription("Freshness month navigation").assertExists()
        compose.onNodeWithText("Earlier").assertExists()
        compose.onNodeWithText("Later").assertExists()
        val earlierCenter = compose.onNodeWithText("Earlier").fetchSemanticsNode().boundsInRoot.center.y
        val laterCenter = compose.onNodeWithText("Later").fetchSemanticsNode().boundsInRoot.center.y
        assertTrue(kotlin.math.abs(earlierCenter - laterCenter) < 2f)
        compose.onNodeWithText("Strength").assertIsDisplayed()
        compose.onNodeWithText("Stretch").assertIsDisplayed().performClick()
        assertEquals(TrainingMode.STRETCHING, selectedMode)
        val today = LocalDate.now()
        val targetDay = (today.dayOfMonth - 1).coerceAtLeast(1)
        compose.onNodeWithText(
            "Freshness, ${today.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
        ).assertIsDisplayed()
        val slider = compose.onNodeWithContentDescription("Freshness date")
        val range = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(1f, range.range.start)
        assertEquals(today.dayOfMonth.toFloat(), range.range.endInclusive)
        assertEquals((today.dayOfMonth - 2).coerceAtLeast(0), range.steps)
        slider.performSemanticsAction(SemanticsActions.SetProgress) {
            it(targetDay.toFloat())
        }
        compose.waitUntil(15_000) { previewDay == today.withDayOfMonth(targetDay).toEpochDay() }
        assertEquals(today.withDayOfMonth(targetDay).toEpochDay(), previewDay)
        compose.onNodeWithText(
            "Freshness, ${today.withDayOfMonth(targetDay).format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
        ).assertIsDisplayed()

        compose.onNodeWithText("Earlier").performClick()
        val previousMonth = YearMonth.from(today).minusMonths(1)
        compose.waitUntil(15_000) {
            previewDay?.let(LocalDate::ofEpochDay)?.let(YearMonth::from) == previousMonth
        }
        compose.onNodeWithText(previousMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))).assertIsDisplayed()
        val previousRange = compose.onNodeWithContentDescription("Freshness date")
            .fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(previousMonth.lengthOfMonth().toFloat(), previousRange.range.endInclusive)
        compose.onNodeWithContentDescription("Freshness date").performSemanticsAction(SemanticsActions.SetProgress) {
            it(10.49f)
        }
        compose.waitUntil(15_000) { previewDay == previousMonth.atDay(10).toEpochDay() }
        compose.onNodeWithContentDescription("Freshness date").performSemanticsAction(SemanticsActions.SetProgress) {
            it(10.51f)
        }
        compose.waitUntil(15_000) { previewDay == previousMonth.atDay(11).toEpochDay() }
        assertNotNull(previewDay)
    }

    @Test
    fun dragHandleCommitsOneCompletedMove() {
        val moves = mutableListOf<Triple<Any, Int, Int>>()
        compose.setContent {
            VibeCheckTheme {
                val keys = listOf("one", "two", "three", "four")
                val state = rememberReorderState(keys) { key, from, to ->
                    moves += Triple(key, from, to)
                }

                ReorderOverlayHost {
                    Column {
                        state.ordered(keys) { it }.forEachIndexed { index, key ->
                            ReorderItem(
                                state,
                                key,
                                index,
                                Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "Tile $key" },
                            ) {
                                ReorderHandle(state, key, key)
                            }
                        }
                    }
                }
            }
        }

        compose.onNodeWithContentDescription("Reorder one").performTouchInput {
            down(center)
            advanceEventTime(1_000)
            repeat(3) {
                moveBy(Offset(0f, 60f))
                advanceEventTime(100)
            }
            up()
        }
        compose.waitForIdle()

        assertEquals(listOf(Triple("one", 0, 1)), moves)
        val firstTile = compose.onNodeWithContentDescription("Tile one").fetchSemanticsNode().boundsInRoot
        val secondTile = compose.onNodeWithContentDescription("Tile two").fetchSemanticsNode().boundsInRoot
        assertTrue(firstTile.top > secondTile.top)
    }

    @Test
    fun muscleDetailsSheetPreservesMapGeometryAndDismissesWithClose() {
        compose.setContent {
            VibeCheckTheme {
                HomeScreen(
                    state = MainUiState(),
                    onDayClick = {},
                    onContinue = {},
                    onRecencyDayChange = {},
                    onModeChange = {},
                )
            }
        }
        val map = compose.onNodeWithContentDescription("male front freshness map")
        val before = map.fetchSemanticsNode().boundsInRoot
        val inspectChest = map.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label.startsWith("Inspect CHEST") }

        compose.runOnIdle { inspectChest.action() }
        compose.onNodeWithText("Chest Freshness details").assertIsDisplayed()
        val during = map.fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, during.left, 0.5f)
        assertEquals(before.top, during.top, 0.5f)
        assertEquals(before.right, during.right, 0.5f)
        assertEquals(before.bottom, during.bottom, 0.5f)

        compose.onNodeWithContentDescription("Close muscle details").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Chest Freshness details").assertDoesNotExist()
        val after = map.fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, after.left, 0.5f)
        assertEquals(before.top, after.top, 0.5f)
        assertEquals(before.right, after.right, 0.5f)
        assertEquals(before.bottom, after.bottom, 0.5f)
    }

    @Test
    fun activeWorkoutAppearsAfterInvariantFreshnessAndBeforeWorkTracker() {
        var activeWorkout by mutableStateOf<ActiveWorkout?>(null)
        compose.setContent {
            VibeCheckTheme {
                Box(Modifier.width(411.dp).height(780.dp)) {
                    HomeScreen(
                        state = MainUiState(activeWorkout = activeWorkout),
                        onDayClick = {},
                        onContinue = {},
                        onRecencyDayChange = {},
                        onModeChange = {},
                    )
                }
            }
        }
        val map = compose.onNodeWithContentDescription("male front freshness map")
        val before = map.fetchSemanticsNode().boundsInRoot
        val home = compose.onNodeWithContentDescription("Home content")
        val viewport = home.fetchSemanticsNode().boundsInRoot
        val trackerBefore = compose.onNodeWithContentDescription("Work tracker card")
            .fetchSemanticsNode().boundsInRoot
        val initialScrollRange = home.fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        assertEquals(0f, initialScrollRange.value(), 0.5f)
        assertEquals(0f, initialScrollRange.maxValue(), 0.5f)
        assertTrue(
            "tracker=$trackerBefore viewport=$viewport",
            viewport.bottom - trackerBefore.bottom in 0f..12f,
        )

        repeat(10) { iteration ->
            compose.runOnIdle {
                activeWorkout = ActiveWorkout(
                    id = "active-$iteration",
                    name = "Strength session",
                    mode = TrainingMode.STRENGTH,
                    startedAt = iteration.toLong(),
                    notes = "",
                    exercises = emptyList(),
                )
            }
            compose.waitUntil(15_000) {
                home.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f
            }

            val during = map.fetchSemanticsNode().boundsInRoot
            assertEquals(before.left, during.left, 0.5f)
            assertEquals(before.top, during.top, 0.5f)
            assertEquals(before.right, during.right, 0.5f)
            assertEquals(before.bottom, during.bottom, 0.5f)
            val active = compose.onNodeWithText("ACTIVE WORKOUT").fetchSemanticsNode().boundsInRoot
            val tracker = compose.onNodeWithText("WORK TRACKER").fetchSemanticsNode().boundsInRoot
            assertTrue(active.top > during.bottom)
            assertTrue(active.bottom < tracker.top)

            home.performTouchInput { swipeUp() }
            compose.runOnIdle { activeWorkout = null }
            compose.waitUntil(15_000) {
                val range = home.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
                range.value() == 0f && range.maxValue() == 0f
            }
            val after = map.fetchSemanticsNode().boundsInRoot
            assertEquals(before.left, after.left, 0.5f)
            assertEquals(before.top, after.top, 0.5f)
            assertEquals(before.right, after.right, 0.5f)
            assertEquals(before.bottom, after.bottom, 0.5f)
            val trackerAfter = compose.onNodeWithContentDescription("Work tracker card")
                .fetchSemanticsNode().boundsInRoot
            assertTrue("iteration=$iteration tracker=$trackerAfter viewport=$viewport", trackerAfter.bottom <= viewport.bottom + 0.5f)
        }
    }

    @Test
    fun noWorkoutTrackerConsumesRemainingReferenceViewportsWithoutChangingFreshness() {
        var viewportWidth by mutableStateOf<Dp>(411.dp)
        var viewportHeight by mutableStateOf<Dp>(761.dp)
        compose.setContent {
            VibeCheckTheme {
                Box(Modifier.width(viewportWidth).height(viewportHeight)) {
                    HomeScreen(
                        state = MainUiState(),
                        onDayClick = {},
                        onContinue = {},
                        onRecencyDayChange = {},
                        onModeChange = {},
                    )
                }
            }
        }

        val referenceMapBounds = compose.onNodeWithContentDescription("male front freshness map")
            .fetchSemanticsNode().boundsInRoot
        listOf(
            411.dp to 761.dp,
            411.dp to 891.dp,
        ).forEach { (width, height) ->
            compose.runOnIdle {
                viewportWidth = width
                viewportHeight = height
            }
            compose.waitForIdle()
            val home = compose.onNodeWithContentDescription("Home content").fetchSemanticsNode()
            val viewport = home.boundsInRoot
            val range = home.config[SemanticsProperties.VerticalScrollAxisRange]
            val tracker = compose.onNodeWithContentDescription("Work tracker card").fetchSemanticsNode().boundsInRoot
            val map = compose.onNodeWithContentDescription("male front freshness map").fetchSemanticsNode().boundsInRoot
            assertEquals("$width x $height scroll", 0f, range.maxValue(), 0.5f)
            assertTrue("$width x $height tracker=$tracker viewport=$viewport", viewport.bottom - tracker.bottom in 0f..12f)
            assertEquals(referenceMapBounds.width, map.width, 0.5f)
            assertEquals(referenceMapBounds.height, map.height, 0.5f)
        }
    }

    @Test
    fun trackerDistributesAvailableHeightAcrossFourFiveAndSixWeekMonths() {
        val fourWeeks = YearMonth.of(2026, 2)
        val fiveWeeks = YearMonth.of(2026, 6)
        val sixWeeks = YearMonth.of(2026, 8)
        var viewportWidth by mutableStateOf<Dp>(411.dp)
        var viewportHeight by mutableStateOf<Dp>(761.dp)
        var activeWorkout by mutableStateOf<ActiveWorkout?>(null)
        var selectedDate by mutableStateOf(fourWeeks.atDay(1))
        compose.setContent {
            VibeCheckTheme {
                Box(Modifier.width(viewportWidth).height(viewportHeight)) {
                    HomeScreen(
                        state = MainUiState(
                            activeWorkout = activeWorkout,
                            homeRecencyDay = selectedDate.toEpochDay(),
                        ),
                        onDayClick = {},
                        onContinue = {},
                        onRecencyDayChange = { selectedDate = LocalDate.ofEpochDay(it) },
                        onModeChange = {},
                    )
                }
            }
        }

        fun cellHeight(month: YearMonth): Float {
            compose.runOnIdle { selectedDate = month.atDay(1) }
            compose.waitForIdle()
            val first = compose.onNodeWithContentDescription("${month.atDay(1)}: 0 activities", substring = true)
                .fetchSemanticsNode().boundsInRoot
            val last = compose.onNodeWithContentDescription("${month.atEndOfMonth()}: 0 activities", substring = true)
                .fetchSemanticsNode().boundsInRoot
            val grid = compose.onNodeWithContentDescription("Activity heatmap grid", substring = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals("$month first=$first last=$last", first.height, last.height, 1.5f)
            assertTrue("$month first=$first grid=$grid", first.top >= grid.top && last.bottom <= grid.bottom)
            return first.height
        }

        val fourRowHeight = cellHeight(fourWeeks)
        val fiveRowHeight = cellHeight(fiveWeeks)
        val sixRowHeight = cellHeight(sixWeeks)
        assertTrue("4=$fourRowHeight 5=$fiveRowHeight", fourRowHeight > fiveRowHeight)
        assertTrue("5=$fiveRowHeight 6=$sixRowHeight", fiveRowHeight > sixRowHeight)
        val home = compose.onNodeWithContentDescription("Home content").fetchSemanticsNode()
        assertEquals(0f, home.config[SemanticsProperties.VerticalScrollAxisRange].maxValue(), 0.5f)

        compose.runOnIdle {
            viewportHeight = 891.dp
            selectedDate = fiveWeeks.atDay(1)
        }
        compose.waitForIdle()
        val tallFiveRowHeight = cellHeight(fiveWeeks)
        assertTrue("761dp=$fiveRowHeight 891dp=$tallFiveRowHeight", tallFiveRowHeight > fiveRowHeight)
        assertEquals(
            0f,
            compose.onNodeWithContentDescription("Home content").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].maxValue(),
            0.5f,
        )

        compose.runOnIdle {
            viewportHeight = 761.dp
            selectedDate = sixWeeks.atDay(1)
            activeWorkout = ActiveWorkout(
                id = "active",
                name = "Strength session",
                mode = TrainingMode.STRENGTH,
                startedAt = 0L,
                notes = "",
                exercises = emptyList(),
            )
        }
        compose.waitUntil(15_000) {
            compose.onNodeWithContentDescription("Home content").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f
        }
        val activeSixRowHeight = compose
            .onNodeWithContentDescription("${sixWeeks.atDay(1)}: 0 activities", substring = true)
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue("fitted=$sixRowHeight active=$activeSixRowHeight", activeSixRowHeight < sixRowHeight)

        compose.runOnIdle {
            activeWorkout = null
            viewportWidth = 320.dp
        }
        compose.waitUntil(15_000) {
            compose.onNodeWithContentDescription("Home content").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f
        }
        compose.onNodeWithContentDescription("Home content").performScrollToNode(
            hasContentDescription("${sixWeeks.atDay(1)}: 0 activities", substring = true),
        )
        val smallCell = compose.onNodeWithContentDescription("${sixWeeks.atDay(1)}: 0 activities", substring = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("small cell=$smallCell", smallCell.width > 0f && smallCell.height > 0f)
    }

    @Test
    fun homeRetainsScrollingForLargeTypeAndConstrainedWidth() {
        var fontScale by mutableFloatStateOf(1.3f)
        var viewportWidth by mutableStateOf<Dp>(411.dp)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VibeCheckTheme {
                    Box(Modifier.width(viewportWidth).height(780.dp)) {
                        HomeScreen(
                            state = MainUiState(),
                            onDayClick = {},
                            onContinue = {},
                            onRecencyDayChange = {},
                            onModeChange = {},
                        )
                    }
                }
            }
        }
        fun assertScrollable(label: String) {
            compose.waitUntil(15_000) {
                compose.onNodeWithContentDescription("Home content").fetchSemanticsNode()
                    .config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f
            }
            val range = compose.onNodeWithContentDescription("Home content").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue("$label max=${range.maxValue()}", range.maxValue() > 0f)
        }

        assertScrollable("1.3x font")
        compose.runOnIdle { fontScale = 2f }
        assertScrollable("2x font")
        compose.runOnIdle {
            fontScale = 1f
            viewportWidth = 320.dp
        }
        assertScrollable("320dp width")
    }

    @Test
    fun reducedMotionCompletionAcknowledgesOnceWithoutAnimatedDelay() {
        val preferences = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("settings", 0)
        preferences.edit().putBoolean("reducedMotion", true).commit()
        var consumedId: String? = null
        var completionEvent by mutableStateOf<WorkoutCompletionEvent?>(null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VibeCheckTheme {
                HomeScreen(
                    state = MainUiState(),
                    onDayClick = {},
                    onContinue = {},
                    onRecencyDayChange = {},
                    onModeChange = {},
                    completionEvent = completionEvent,
                    onCompletionConsumed = { consumedId = it },
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val map = compose.onNodeWithContentDescription("male front freshness map")
        val before = map.fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            completionEvent = WorkoutCompletionEvent("event-1", TrainingMode.STRENGTH, listOf("CHEST"))
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithContentDescription(
            "Strength session complete. Freshness updated.",
            useUnmergedTree = true,
        ).assertExists()
        compose.onNodeWithText("Session saved", substring = true).assertDoesNotExist()
        val during = map.fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, during.left, 0.5f)
        assertEquals(before.top, during.top, 0.5f)
        assertEquals(before.right, during.right, 0.5f)
        assertEquals(before.bottom, during.bottom, 0.5f)
        compose.runOnIdle { assertEquals("event-1", consumedId) }
        compose.mainClock.advanceTimeBy(300)
        val after = map.fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, after.left, 0.5f)
        assertEquals(before.top, after.top, 0.5f)
        assertEquals(before.right, after.right, 0.5f)
        assertEquals(before.bottom, after.bottom, 0.5f)
        preferences.edit().remove("reducedMotion").commit()
        compose.mainClock.autoAdvance = true
    }
}

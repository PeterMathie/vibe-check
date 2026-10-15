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
        compose.onNodeWithContentDescription("Activity heatmap grid, 5 weeks, 35 days").assertExists()
        compose.onNodeWithContentDescription("${LocalDate.now().minusDays(34)}: 0 activities", substring = true).assertExists()
        compose.onNodeWithContentDescription("${LocalDate.now()}: 0 activities", substring = true).assertExists()
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

        val earliestHeatmapDate = today.minusDays(34)
        compose.onNodeWithContentDescription("$earliestHeatmapDate: 0 activities", substring = true).performClick()
        val previousMonth = YearMonth.from(earliestHeatmapDate)
        compose.waitUntil(15_000) { previewDay == earliestHeatmapDate.toEpochDay() }
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
    fun activeWorkoutUsesOverlayPlayWithoutReflowingHomeCards() {
        var activeWorkout by mutableStateOf<ActiveWorkout?>(null)
        var continued = false
        compose.setContent {
            VibeCheckTheme {
                Box(Modifier.width(411.dp).height(780.dp)) {
                    HomeScreen(
                        state = MainUiState(activeWorkout = activeWorkout),
                        onDayClick = {},
                        onContinue = { continued = true },
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
        assertTrue(initialScrollRange.maxValue() <= 64f)
        assertTrue(
            "tracker=$trackerBefore viewport=$viewport",
            viewport.bottom - trackerBefore.bottom in 0f..12f,
        )

        compose.runOnIdle {
            activeWorkout = ActiveWorkout(
                id = "active",
                name = "Strength session",
                mode = TrainingMode.STRENGTH,
                startedAt = 1L,
                notes = "",
                exercises = emptyList(),
            )
        }
        val play = compose.onNodeWithContentDescription("Continue active workout").assertIsDisplayed()
        compose.onNodeWithText("ACTIVE WORKOUT").assertDoesNotExist()
        compose.onNodeWithText("Strength session").assertDoesNotExist()
        val during = map.fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, during.left, 0.5f)
        assertEquals(before.top, during.top, 0.5f)
        assertEquals(before.right, during.right, 0.5f)
        assertEquals(before.bottom, during.bottom, 0.5f)
        val trackerDuring = compose.onNodeWithContentDescription("Work tracker card")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(trackerBefore.top, trackerDuring.top, 0.5f)
        assertEquals(trackerBefore.bottom, trackerDuring.bottom, 0.5f)
        assertTrue(play.fetchSemanticsNode().boundsInRoot.right <= viewport.right)
        play.performClick()
        compose.runOnIdle { assertTrue(continued) }
        compose.runOnIdle { activeWorkout = null }
        compose.onNodeWithContentDescription("Continue active workout").assertDoesNotExist()
    }

    @Test
    fun homeHeatmapNavigatesFromRollingCurrentRangeToFullPriorMonths() {
        val today = LocalDate.now()
        compose.setContent {
            VibeCheckTheme {
                Box(Modifier.width(411.dp).height(780.dp)) {
                    HomeScreen(
                        state = MainUiState(homeRecencyDay = today.toEpochDay()),
                        onDayClick = {},
                        onContinue = {},
                        onRecencyDayChange = {},
                        onModeChange = {},
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Current activity range, today and previous 34 days")
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("${today.minusDays(34)}: 0 activities", substring = true).assertExists()
        compose.onNodeWithContentDescription("$today: 0 activities", substring = true).assertExists()

        compose.onNodeWithText("Earlier").performClick()
        val prior = YearMonth.from(today).minusMonths(1)
        compose.onNodeWithText(prior.format(DateTimeFormatter.ofPattern("MMMM yyyy"))).assertIsDisplayed()
        compose.onNodeWithContentDescription("${prior.atDay(1)}: 0 activities", substring = true).assertExists()
        compose.onNodeWithContentDescription(
            "${prior.atEndOfMonth()}: 0 activities",
            substring = true,
        ).assertExists()
        compose.onNodeWithContentDescription("$today: 0 activities", substring = true).assertDoesNotExist()

        compose.onNodeWithText("Earlier").performClick()
        val twoMonthsBack = prior.minusMonths(1)
        compose.onNodeWithText(twoMonthsBack.format(DateTimeFormatter.ofPattern("MMMM yyyy")))
            .assertIsDisplayed()
        compose.onNodeWithText("Later").performClick()
        compose.onNodeWithText(prior.format(DateTimeFormatter.ofPattern("MMMM yyyy"))).assertIsDisplayed()
        compose.onNodeWithText("Later").performClick()
        compose.onNodeWithContentDescription("Current activity range, today and previous 34 days")
            .assertIsDisplayed()
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
            assertTrue("$width x $height scroll=${range.maxValue()}", range.maxValue() <= 64f)
            assertTrue("$width x $height tracker=$tracker viewport=$viewport", viewport.bottom - tracker.bottom in 0f..12f)
            assertEquals(referenceMapBounds.width, map.width, 0.5f)
            assertEquals(referenceMapBounds.height, map.height, 0.5f)
        }
    }

    @Test
    fun trackerAlwaysDistributesAvailableHeightAcrossExactlyFiveWeeks() {
        var viewportWidth by mutableStateOf<Dp>(411.dp)
        var viewportHeight by mutableStateOf<Dp>(761.dp)
        var activeWorkout by mutableStateOf<ActiveWorkout?>(null)
        var selectedDate by mutableStateOf(LocalDate.now())
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

        fun cellHeight(): Float {
            compose.waitForIdle()
            val today = LocalDate.now()
            val first = compose.onNodeWithContentDescription("${today.minusDays(34)}: 0 activities", substring = true)
                .fetchSemanticsNode().boundsInRoot
            val last = compose.onNodeWithContentDescription("$today: 0 activities", substring = true)
                .fetchSemanticsNode().boundsInRoot
            val grid = compose.onNodeWithContentDescription("Activity heatmap grid, 5 weeks, 35 days")
                .fetchSemanticsNode().boundsInRoot
            assertEquals("first=$first last=$last", first.height, last.height, 1.5f)
            assertTrue("first=$first grid=$grid", first.top >= grid.top && last.bottom <= grid.bottom)
            return first.height
        }

        val standardRowHeight = cellHeight()
        val home = compose.onNodeWithContentDescription("Home content").fetchSemanticsNode()
        assertTrue(home.config[SemanticsProperties.VerticalScrollAxisRange].maxValue() <= 64f)

        compose.runOnIdle {
            viewportHeight = 891.dp
        }
        compose.waitForIdle()
        val tallFiveRowHeight = cellHeight()
        assertTrue("761dp=$standardRowHeight 891dp=$tallFiveRowHeight", tallFiveRowHeight > standardRowHeight)
        assertTrue(
            compose.onNodeWithContentDescription("Home content").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].maxValue() <= 64f,
        )

        compose.runOnIdle {
            viewportHeight = 761.dp
            activeWorkout = ActiveWorkout(
                id = "active",
                name = "Strength session",
                mode = TrainingMode.STRENGTH,
                startedAt = 0L,
                notes = "",
                exercises = emptyList(),
            )
        }
        compose.onNodeWithContentDescription("Continue active workout").assertIsDisplayed()
        val activeFiveRowHeight = compose
            .onNodeWithContentDescription("${LocalDate.now().minusDays(34)}: 0 activities", substring = true)
            .fetchSemanticsNode().boundsInRoot.height
        assertEquals("fitted=$standardRowHeight active=$activeFiveRowHeight", standardRowHeight, activeFiveRowHeight, 1.5f)
        assertTrue(
            compose.onNodeWithContentDescription("Home content").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].maxValue() <= 64f,
        )

        compose.runOnIdle {
            activeWorkout = null
            viewportWidth = 320.dp
        }
        compose.waitUntil(15_000) {
            compose.onNodeWithContentDescription("Home content").fetchSemanticsNode()
                .config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f
        }
        compose.onNodeWithContentDescription("Home content").performScrollToNode(
            hasContentDescription("${LocalDate.now().minusDays(34)}: 0 activities", substring = true),
        )
        val smallCell = compose.onNodeWithContentDescription("${LocalDate.now().minusDays(34)}: 0 activities", substring = true)
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

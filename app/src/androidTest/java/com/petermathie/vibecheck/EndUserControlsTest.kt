package com.petermathie.vibecheck

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.pressKey
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import com.petermathie.vibecheck.domain.model.ActiveWorkout
import com.petermathie.vibecheck.domain.model.ActivityDay
import com.petermathie.vibecheck.domain.model.AnatomySex
import com.petermathie.vibecheck.domain.model.HistoryActivity
import com.petermathie.vibecheck.domain.model.HistoryDayDetail
import com.petermathie.vibecheck.domain.model.MuscleContribution
import com.petermathie.vibecheck.domain.model.MuscleRecency
import com.petermathie.vibecheck.domain.model.MuscleRecencyBand
import com.petermathie.vibecheck.domain.model.MuscleRole
import com.petermathie.vibecheck.domain.model.TrainingMode
import com.petermathie.vibecheck.ui.Destination
import com.petermathie.vibecheck.ui.HomeScreen
import com.petermathie.vibecheck.ui.MainUiState
import com.petermathie.vibecheck.ui.ModeSelector
import com.petermathie.vibecheck.ui.PrimaryNavigationBar
import com.petermathie.vibecheck.ui.ReorderHandle
import com.petermathie.vibecheck.ui.ReorderItem
import com.petermathie.vibecheck.ui.ReorderOverlayHost
import com.petermathie.vibecheck.ui.StagedSwipeToDelete
import com.petermathie.vibecheck.ui.WorkoutCompletionEvent
import com.petermathie.vibecheck.ui.formatDisplayDate
import com.petermathie.vibecheck.ui.rememberReorderState
import com.petermathie.vibecheck.ui.anatomy.AnatomyView
import com.petermathie.vibecheck.ui.anatomy.MuscleMap
import com.petermathie.vibecheck.ui.theme.LocalVibeMotion
import com.petermathie.vibecheck.ui.theme.LocalVibeReducedMotion
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import com.petermathie.vibecheck.ui.theme.VibeMotion
import com.petermathie.vibecheck.ui.theme.VibePalettes
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

    private fun calendarDay(date: LocalDate): SemanticsNodeInteraction {
        val prefix = "${formatDisplayDate(date)}:"
        return compose.onNode(SemanticsMatcher("calendar day $prefix") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.startsWith(prefix) }
        })
    }

    @Test
    fun homeMuscleViewingDateAccompaniesElapsedTimeBelowName() {
        val date = LocalDate.of(2026, 11, 13)
        val asOf = date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        val state = MainUiState(
            homeRecencyDay = date.toEpochDay(),
            recencyMode = TrainingMode.STRENGTH,
            recencyAsOfMillis = asOf,
            recency = listOf(MuscleRecency(
                "GLUTES", asOf - 5 * 86_400_000L, MuscleRecencyBand.DAYS_3_TO_7, emptyList(), 0.0,
            )),
        )
        compose.setContent {
            VibeCheckTheme {
                Box(Modifier.width(411.dp).height(761.dp)) {
                    HomeScreen(state, {}, {}, {}, today = LocalDate.of(2026, 11, 18))
                }
            }
        }
        val inspect = compose.onNodeWithContentDescription("male back freshness map")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label.startsWith("Inspect GLUTES") }
        compose.runOnIdle { assertTrue(inspect.action()) }
        val heading = compose.onNodeWithText("Glutes").assertIsDisplayed()
        val summary = compose.onNodeWithText("${formatDisplayDate(date)}, last trained about 5 days ago").assertIsDisplayed()
        val headingLayouts = mutableListOf<TextLayoutResult>()
        val summaryLayouts = mutableListOf<TextLayoutResult>()
        heading.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(headingLayouts) }
        summary.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(summaryLayouts) }
        val headingLayout = headingLayouts.single()
        val summaryLayout = summaryLayouts.single()
        assertTrue("The full date and elapsed time must fit without clipping", !summaryLayout.hasVisualOverflow)
        val summaryText = summaryLayout.layoutInput.text.text
        assertEquals(summaryText.length, summaryLayout.getLineEnd(summaryLayout.lineCount - 1, visibleEnd = true))
        summaryText.indices.forEach { index ->
            val character = summaryLayout.getBoundingBox(index)
            assertTrue(
                "Summary character $index fits: $character within ${summaryLayout.size}",
                character.left >= -0.5f && character.right <= summaryLayout.size.width + 0.5f &&
                    character.top >= -0.5f && character.bottom <= summaryLayout.size.height + 0.5f,
            )
        }
        assertTrue(
            "The date is smaller than the muscle name",
            summaryLayout.getBoundingBox(0).height < headingLayout.getBoundingBox(0).height,
        )
        val headingBounds = heading.fetchSemanticsNode().boundsInWindow
        val summaryBounds = summary.fetchSemanticsNode().boundsInWindow
        assertTrue(
            "The date has whitespace below the name",
            summaryBounds.top - headingBounds.bottom >= with(compose.density) { 8.dp.toPx() },
        )
        compose.onNodeWithText("As of", substring = true).assertDoesNotExist()
        compose.onNodeWithText(formatDisplayDate(date)).assertDoesNotExist()
        compose.onNodeWithContentDescription("Close muscle activity").performClick()
    }

    @Test
    fun homeMuscleColourCueMatchesNaturalTimeAndViewingDate() {
        val date = LocalDate.of(2026, 11, 13)
        val asOf = date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        val palette = VibePalettes.Ocean.dark
        var state by mutableStateOf(MainUiState(
            homeRecencyDay = date.toEpochDay(),
            recencyMode = TrainingMode.STRENGTH,
            recencyAsOfMillis = asOf,
            recency = listOf(MuscleRecency(
                "GLUTES", asOf - (4 * 86_400L + 23 * 3_600 + 2 * 60) * 1_000,
                MuscleRecencyBand.DAYS_3_TO_7, listOf("Back squat"), 1.0,
                weeklyWorkingSets = 1,
                weeklyContributions = listOf(MuscleContribution("Back squat", MuscleRole.PRIMARY, 1)),
            )),
        ))
        compose.setContent {
            VibeCheckTheme(palette) {
                Box(Modifier.width(411.dp).height(761.dp)) {
                    HomeScreen(
                        state,
                        {}, {}, {},
                        today = LocalDate.of(2026, 11, 18),
                    )
                }
            }
        }
        val inspect = compose.onNodeWithContentDescription("male back freshness map")
            .fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label.startsWith("Inspect GLUTES") }
        compose.runOnIdle { assertTrue(inspect.action()) }
        compose.onNodeWithText("${formatDisplayDate(date)}, last trained about 5 days ago").assertIsDisplayed()
        val swatch = compose.onNodeWithTag("Home muscle freshness colour")
        val pixels = swatch.captureToImage().toPixelMap()
        assertEquals(palette.recency3To7.toArgb(), pixels[pixels.width / 2, pixels.height / 2].toArgb())
        assertEquals(pixels.width, pixels.height)
        assertTrue("The colour swatch is circular, not a square", pixels[0, 0].toArgb() != palette.recency3To7.toArgb())
        compose.runOnIdle { state = state.copy(recency = emptyList()) }
        compose.onNodeWithText("${formatDisplayDate(date)}, no recorded training").assertIsDisplayed()
        val neutral = swatch.captureToImage().toPixelMap()
        assertEquals(palette.recencyNever.toArgb(), neutral[neutral.width / 2, neutral.height / 2].toArgb())
        compose.onNodeWithContentDescription("Close muscle activity").performClick()
    }

    @Test
    fun homeReclaimsSliderSpaceExtendsKeyAndUsesConstantInsetSelection() {
        val today = LocalDate.of(2026, 11, 18)
        var state by mutableStateOf(MainUiState(
            homeRecencyDay = today.withDayOfMonth(9).toEpochDay(),
            activityDays = listOf(ActivityDay(today.withDayOfMonth(13).toEpochDay(), 8)),
        ))
        var palette by mutableStateOf(VibePalettes.Ocean.dark)
        compose.setContent {
            VibeCheckTheme(palette) {
                Box(Modifier.width(411.dp).height(761.dp).background(palette.background).testTag("Home fixture")) {
                    HomeScreen(state, {}, { state = state.copy(homeRecencyDay = it) }, {}, today = today)
                }
            }
        }
        compose.onNodeWithContentDescription("Freshness date").assertDoesNotExist()
        compose.onNodeWithTag("Home time navigation").assertDoesNotExist()
        compose.onNodeWithText("View date").assertDoesNotExist()
        val fixture = compose.onNodeWithTag("Home fixture").fetchSemanticsNode().boundsInRoot
        val key = compose.onNodeWithContentDescription("Freshness colour scale", substring = true).fetchSemanticsNode().boundsInRoot
        val front = compose.onNodeWithContentDescription("male front freshness map").fetchSemanticsNode().boundsInRoot
        val calendar = compose.onNodeWithContentDescription("Work tracker card").fetchSemanticsNode().boundsInRoot
        assertEquals("Key reaches the middle of the page", fixture.center.x, key.right, 1.5f)
        assertTrue("Figures have room below the heading", front.top - fixture.top >= with(compose.density) { 72.dp.toPx() })
        val heading = compose.onNodeWithTag("Home freshness heading").fetchSemanticsNode().boundsInRoot
        assertTrue("Figures do not overlap the heading or calendar", front.top >= heading.bottom && front.bottom <= calendar.top)
        assertEquals("Figures are centred between the heading and calendar", (heading.bottom + calendar.top) / 2f, front.center.y, 1f)
        val originalBounds = listOf(front, calendar)
        for (theme in listOf(VibePalettes.Ocean.dark, VibePalettes.Ocean.light)) {
            compose.runOnIdle { palette = theme }
            val paints = listOf(9, 13).map { day ->
                val cell = calendarDay(today.withDayOfMonth(day))
                cell.performClick().assertIsSelected()
                val pixels = cell.captureToImage().toPixelMap()
                assertEquals("Selection is background-coloured at either intensity", theme.background.toArgb(), pixels[2, pixels.height / 2].toArgb())
                pixels[pixels.width / 4, pixels.height / 4].toArgb()
            }
            assertTrue("Selection preserves the different activity intensities", paints[0] != paints[1])
            assertEquals(originalBounds, listOf(
                compose.onNodeWithContentDescription("male front freshness map").fetchSemanticsNode().boundsInRoot,
                compose.onNodeWithContentDescription("Work tracker card").fetchSemanticsNode().boundsInRoot,
            ))
        }
    }

    @Test
    fun homeMuscleDoubleTapShowsWeeklyRowsWithSynchronizedSofterGlass() {
        val today = LocalDate.of(2026, 11, 18)
        val date = today.withDayOfMonth(13)
        fun endOfDay(day: LocalDate) = day.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        val asOf = endOfDay(date)
        var state by mutableStateOf(MainUiState(
            homeRecencyDay = date.toEpochDay(),
            recencyMode = TrainingMode.STRENGTH,
            recencyAsOfMillis = asOf,
            recency = listOf(MuscleRecency(
                "CHEST", asOf - (2 * 86_400L + 3 * 3_600 + 4 * 60) * 1_000,
                MuscleRecencyBand.UNDER_24_HOURS, listOf("Push up"), 6.5,
                weeklyWorkingSets = 8,
                weeklyAverageRpe = 4.0,
                weeklyContributions = listOf(
                    MuscleContribution("Push up", MuscleRole.PRIMARY, 2),
                    MuscleContribution("Dips", MuscleRole.SECONDARY, 1),
                ) + (1..5).map { MuscleContribution("Exercise $it", MuscleRole.PRIMARY, 1) },
            )),
        ))
        val palette = VibePalettes.Ocean.dark.copy(recencyUnder24 = Color.Green)
        compose.setContent {
            VibeCheckTheme(palette) {
                Box(Modifier.width(411.dp).height(761.dp).background(palette.background)) {
                    HomeScreen(state, {}, { state = state.copy(homeRecencyDay = it) }, {}, today = today)
                }
            }
        }
        val map = compose.onNodeWithContentDescription("male front freshness map")
        val sharp = map.captureToImage().toPixelMap()
        fun isGreen(pixels: PixelMap, x: Int, y: Int) =
            pixels[x, y].green > 0.98f && pixels[x, y].red < 0.02f && pixels[x, y].blue < 0.02f
        val point = (5 until sharp.height - 5).firstNotNullOf { y ->
            (5 until sharp.width - 5).firstOrNull { x ->
                listOf(-4, 0, 4).all { dx -> listOf(-4, 0, 4).all { dy -> isGreen(sharp, x + dx, y + dy) } }
            }?.let { x -> Offset(x.toFloat(), y.toFloat()) }
        }
        map.performTouchInput { down(point) }
        map.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected chest"))
        compose.onNodeWithTag("Home muscle activity").assertDoesNotExist()
        map.performTouchInput { up(); advanceEventTime(500) }
        compose.mainClock.advanceTimeBy(500)
        compose.mainClock.autoAdvance = false
        map.performTouchInput { doubleClick(point) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64)
        val modal = compose.onNodeWithTag("Home muscle activity")
        val appearing = modal.captureToImage().toPixelMap()
        val alpha = appearing[appearing.width / 2, appearing.height - 16].alpha
        assertTrue("The same transition presents muscle glass and blur", alpha in 0.03f..0.8f)
        val softening = map.captureToImage().toPixelMap()
        assertTrue("Backdrop is softening during presentation", softening[point.x.toInt(), point.y.toInt()].green < sharp[point.x.toInt(), point.y.toInt()].green)
        compose.mainClock.advanceTimeBy(250)
        compose.mainClock.autoAdvance = true
        val blurred = map.captureToImage().toPixelMap()
        val tint = blurred[point.x.toInt(), point.y.toInt()]
        assertTrue("Softer blur still retains recognizable muscle colour", tint.green > maxOf(tint.red, tint.blue) + 0.15f)
        val calendar = compose.onNodeWithContentDescription("Work tracker card").fetchSemanticsNode().boundsInWindow
        assertEquals(calendar, modal.fetchSemanticsNode().boundsInWindow)
        compose.onNodeWithText("${formatDisplayDate(date)}, last trained about 2 days ago").assertIsDisplayed()
        compose.onNodeWithContentDescription("Past 7 days. 8 sets").assertIsDisplayed()
        compose.onNodeWithContentDescription("Average recorded RPE. 4.0").assertIsDisplayed()
        compose.onNodeWithContentDescription("Push up. Primary \u00b7 2 sets").assertIsDisplayed()
        compose.onNodeWithContentDescription("Dips. Secondary \u00b7 1 set").assertIsDisplayed()
        compose.onNodeWithText("6.5 set-equivalents", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("Home muscle activity list").performScrollToNode(hasContentDescription("Exercise 5. Primary \u00b7 1 set"))
        compose.onNodeWithContentDescription("Exercise 5. Primary \u00b7 1 set").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(homeRecencyDay = date.minusDays(1).toEpochDay()) }
        compose.onNodeWithText("Loading activity...").assertIsDisplayed()
        compose.onNodeWithContentDescription("Push up.", substring = true).assertDoesNotExist()
        compose.runOnIdle { state = state.copy(recency = emptyList(), recencyAsOfMillis = endOfDay(date.minusDays(1))) }
        compose.onNodeWithText("${formatDisplayDate(date.minusDays(1))}, no recorded training").assertIsDisplayed()
        compose.onNodeWithContentDescription("Average recorded RPE. Not recorded").assertIsDisplayed()
        compose.onNodeWithText("No contributing exercises in the past 7 days").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close muscle activity").performClick()
        modal.assertDoesNotExist()
        assertEquals(date.minusDays(1).toEpochDay(), state.homeRecencyDay)
        val inspect = map.fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label.startsWith("Inspect CHEST") }
        compose.runOnIdle { assertTrue(inspect.action()) }
        modal.assertIsDisplayed()
        onView(isRoot()).inRoot(isDialog())
            .check { view, error ->
                if (error != null) throw error
                assertTrue("The native dialog owns input focus before Back", view.hasWindowFocus())
            }
            .perform(pressKey(KeyEvent.KEYCODE_BACK))
        modal.assertDoesNotExist()
    }

    @Test
    fun nestedSwipeRequiresTwoSeparateLeftGesturesAndNeverDeletesTheExercise() {
        var setsDeleted = 0
        var exercisesDeleted = 0
        compose.setContent {
            VibeCheckTheme {
                Box(Modifier.width(300.dp)) {
                    StagedSwipeToDelete("exercise", { exercisesDeleted++ }) {
                        Column {
                            Box(Modifier.fillMaxWidth().height(48.dp))
                            StagedSwipeToDelete("set", { setsDeleted++ }) {
                                Box(Modifier.fillMaxWidth().height(80.dp).semantics { contentDescription = "set content" })
                            }
                        }
                    }
                }
            }
        }
        val set = compose.onNodeWithContentDescription("Swipe actions for set")
        val before = compose.onNodeWithContentDescription("set content").getUnclippedBoundsInRoot()
        set.performTouchInput { swipeLeft() }
        set.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Delete revealed"))
        compose.onNodeWithContentDescription("Swipe actions for exercise")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Closed"))
        val after = compose.onNodeWithContentDescription("set content").getUnclippedBoundsInRoot()
        assertEquals(before.left.value - (before.right - before.left).value / 3, after.left.value, 1f)
        assertEquals(0, setsDeleted)
        set.performTouchInput { swipeRight() }
        set.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Closed"))
        set.performTouchInput { swipeLeft() }
        assertEquals(0, setsDeleted)
        set.performTouchInput { swipeLeft() }
        compose.runOnIdle {
            assertEquals(1, setsDeleted)
            assertEquals(0, exercisesDeleted)
        }
        val exercise = compose.onNodeWithContentDescription("Swipe actions for exercise")
        exercise.performTouchInput { swipe(Offset(width * 0.9f, 20f), Offset(width * 0.1f, 20f)) }
        exercise.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Delete revealed"))
        compose.runOnIdle { assertEquals(0, exercisesDeleted) }
        exercise.performTouchInput { swipe(Offset(width * 0.9f, 20f), Offset(width * 0.1f, 20f)) }
        compose.runOnIdle { assertEquals(1, exercisesDeleted) }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun shortAndCancelledSwipesDoNotDeleteAndAccessibleActionsCanCloseOrConfirm() {
        var deleted = 0
        compose.setContent {
            VibeCheckTheme {
                StagedSwipeToDelete("set", { deleted++ }, Modifier.width(300.dp)) {
                    Box(Modifier.fillMaxWidth().height(80.dp))
                }
            }
        }
        val set = compose.onNodeWithContentDescription("Swipe actions for set")
        set.performTouchInput { swipe(center, center - Offset(10f, 0f)) }
        set.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Closed"))
        set.performCustomAccessibilityActionWithLabel("Reveal delete")
        set.performTouchInput {
            down(center)
            moveTo(center - Offset(width / 2f, 0f))
            cancel()
        }
        compose.runOnIdle { assertEquals(0, deleted) }
        set.performCustomAccessibilityActionWithLabel("Close delete")
        set.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Closed"))
        set.performCustomAccessibilityActionWithLabel("Reveal delete")
        compose.onNodeWithContentDescription("Delete set").performClick()
        compose.runOnIdle { assertEquals(1, deleted) }
    }

    @Test
    fun homeKeepsCalendarSelectionWithoutRemovedHeadings() {
        var previewDay: Long? = null
        var selectedMode: TrainingMode? = null
        var state by mutableStateOf(MainUiState())
        compose.setContent {
            VibeCheckTheme {
                HomeScreen(
                    state,
                    {},
                    {
                        previewDay = it
                        state = state.copy(homeRecencyDay = it)
                    },
                    { selectedMode = it },
                )
            }
        }

        compose.onNodeWithText("Freshness").assertIsDisplayed()
        val legend = compose.onNodeWithContentDescription("Freshness colour scale", substring = true)
        legend.assertIsDisplayed()
        legend.assert(hasContentDescription("No data is separate.", substring = true))
        compose.onNodeWithText("No data", useUnmergedTree = true).assertDoesNotExist()
        val frontMapBounds = compose.onNodeWithContentDescription("male front freshness map").fetchSemanticsNode().boundsInRoot
        val backMapBounds = compose.onNodeWithContentDescription("male back freshness map").fetchSemanticsNode().boundsInRoot
        val legendBounds = legend.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Freshness panel texture").assertDoesNotExist()
        assertTrue("Key belongs with the heading", legendBounds.bottom <= frontMapBounds.top)
        assertTrue("Key is horizontal", legendBounds.width > legendBounds.height)
        val calendarBounds = compose.onNodeWithContentDescription("Work tracker card").fetchSemanticsNode().boundsInRoot
        assertEquals(frontMapBounds.bottom, backMapBounds.bottom, 1f)
        assertTrue(calendarBounds.top >= frontMapBounds.bottom)
        compose.onNodeWithContentDescription("Freshness date").assertDoesNotExist()
        compose.onNodeWithText("Freshness,", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Overview").assertDoesNotExist()
        compose.onNodeWithText("Recency, not recovery or fatigue").assertDoesNotExist()
        compose.onNodeWithText("Habits, workouts and stretching").assertDoesNotExist()
        compose.onNodeWithContentDescription("Freshness month navigation").assertExists()
        compose.onNodeWithText("WORK TRACKER").assertDoesNotExist()
        compose.onNodeWithText("Earlier").assertDoesNotExist()
        compose.onNodeWithText("Later").assertDoesNotExist()
        compose.onNodeWithText("0 none", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Previous month").assertExists()
        compose.onNodeWithContentDescription("Next month").assertExists()
        val earlierCenter = compose.onNodeWithContentDescription("Previous month").fetchSemanticsNode().boundsInRoot.center.y
        val laterCenter = compose.onNodeWithContentDescription("Next month").fetchSemanticsNode().boundsInRoot.center.y
        assertTrue(kotlin.math.abs(earlierCenter - laterCenter) < 2f)
        val monthLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        compose.onAllNodesWithText(monthLabel).assertCountEquals(1)
        assertEquals(earlierCenter, compose.onNodeWithText(monthLabel).fetchSemanticsNode().boundsInRoot.center.y, 2f)
        compose.onNodeWithContentDescription("Strength").assertIsDisplayed()
        compose.onNodeWithContentDescription("Stretch").assertIsDisplayed().performClick()
        assertEquals(TrainingMode.STRETCHING, selectedMode)
        val today = LocalDate.now()
        val targetDay = (today.dayOfMonth - 1).coerceAtLeast(1)
        calendarDay(today.withDayOfMonth(targetDay)).performClick()
        compose.waitUntil(15_000) { previewDay == today.withDayOfMonth(targetDay).toEpochDay() }
        assertEquals(today.withDayOfMonth(targetDay).toEpochDay(), previewDay)
        compose.onNodeWithContentDescription(
            "${formatDisplayDate(today.withDayOfMonth(targetDay))}: 0 activities, selected freshness date",
        ).assertExists()

        compose.onNodeWithContentDescription("Previous month").performClick()
        val previousMonth = YearMonth.from(today).minusMonths(1)
        compose.waitUntil(15_000) {
            previewDay?.let(LocalDate::ofEpochDay)?.let(YearMonth::from) == previousMonth
        }
        compose.onNodeWithText(previousMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy"))).assertIsDisplayed()
        calendarDay(previousMonth.atDay(10)).performClick()
        compose.waitUntil(15_000) { previewDay == previousMonth.atDay(10).toEpochDay() }
        calendarDay(previousMonth.atDay(11)).performClick()
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
    fun homeMuscleDialogPreservesMapGeometryAndDismissesWithClose() {
        compose.setContent {
            VibeCheckTheme {
                HomeScreen(
                    state = MainUiState(),
                    onDayClick = {},
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
        val modal = compose.onNodeWithTag("Home muscle activity")
        modal.assertIsDisplayed()
        compose.onNodeWithText("Chest").assertIsDisplayed()
        val during = map.fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, during.left, 0.5f)
        assertEquals(before.top, during.top, 0.5f)
        assertEquals(before.right, during.right, 0.5f)
        assertEquals(before.bottom, during.bottom, 0.5f)

        compose.onNodeWithContentDescription("Close muscle activity").performClick()
        compose.waitForIdle()
        modal.assertDoesNotExist()
        compose.onNodeWithText("Chest").assertDoesNotExist()
        val after = map.fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, after.left, 0.5f)
        assertEquals(before.top, after.top, 0.5f)
        assertEquals(before.right, after.right, 0.5f)
        assertEquals(before.bottom, after.bottom, 0.5f)
    }

    @Test
    fun homeTightPanelsIgnoreDraftAndModeChanges() {
        var activeWorkout by mutableStateOf<ActiveWorkout?>(null)
        var mode by mutableStateOf(TrainingMode.STRENGTH)
        compose.setContent {
            VibeCheckTheme {
                Box(Modifier.width(411.dp).height(780.dp)) {
                    HomeScreen(
                        state = MainUiState(activeWorkout = activeWorkout, mode = mode),
                        onDayClick = {},
                        onRecencyDayChange = {},
                        onModeChange = { mode = it },
                    )
                }
            }
        }
        val descriptions = listOf("Freshness panel", "Work tracker card", "male front freshness map")
        fun bounds() = descriptions.map {
            compose.onNodeWithContentDescription(it).getUnclippedBoundsInRoot()
        }
        val before = bounds()
        val freshnessHeight = (before[0].bottom - before[0].top).value
        val calendarHeight = (before[1].bottom - before[1].top).value
        assertTrue(freshnessHeight / (freshnessHeight + calendarHeight) < 0.7f)
        listOf(true, false, true).forEachIndexed { iteration, hasDraft ->
            compose.runOnIdle {
                activeWorkout = if (hasDraft) ActiveWorkout(
                    id = "active-$iteration",
                    name = "Strength session",
                    mode = TrainingMode.STRENGTH,
                    startedAt = iteration.toLong(),
                    notes = "",
                    exercises = emptyList(),
                ) else null
            }
            compose.waitForIdle()
            compose.onNodeWithText("ACTIVE WORKOUT").assertDoesNotExist()
            compose.onNodeWithText("Continue").assertDoesNotExist()
            compose.onNodeWithContentDescription("Resume workout").assertDoesNotExist()
            assertEquals(before, bounds())
        }
        compose.onNodeWithContentDescription("Stretch").performClick()
        compose.waitForIdle()
        assertEquals(TrainingMode.STRETCHING, mode)
        assertEquals(before, bounds())
    }

    @Test
    fun homeAnatomyIsBorderlessWithSubtleGlowAndIntegratedKey() {
        var palette by mutableStateOf(VibePalettes.Ocean.dark)
        compose.setContent {
            VibeCheckTheme(palette) {
                Box(Modifier.width(411.dp).height(761.dp).background(palette.background)) {
                    HomeScreen(MainUiState(), {}, {}, {}, today = LocalDate.of(2026, 11, 18))
                }
            }
        }
        fun distance(first: Color, second: Color) = maxOf(
            kotlin.math.abs(first.red - second.red),
            kotlin.math.abs(first.green - second.green),
            kotlin.math.abs(first.blue - second.blue),
        )
        for (theme in listOf(VibePalettes.Ocean.dark, VibePalettes.Ocean.light)) {
            compose.runOnIdle { palette = theme }
            val panel = compose.onNodeWithContentDescription("Freshness panel")
            val panelBounds = panel.fetchSemanticsNode().boundsInRoot
            val calendar = compose.onNodeWithContentDescription("Work tracker card")
            val calendarBounds = calendar.fetchSemanticsNode().boundsInRoot
            val front = compose.onNodeWithContentDescription("male front freshness map").fetchSemanticsNode().boundsInRoot
            val back = compose.onNodeWithContentDescription("male back freshness map").fetchSemanticsNode().boundsInRoot
            val legend = compose.onNodeWithContentDescription("Freshness colour scale", substring = true)
            val key = legend.fetchSemanticsNode().boundsInRoot
            legend.assert(hasContentDescription("at the start", substring = true))
            legend.assert(hasContentDescription("at the end", substring = true))
            legend.assert(hasContentDescription("No data is separate.", substring = true))
            assertTrue(key.width > key.height)
            assertTrue(key.bottom <= front.top)
            assertEquals(panelBounds.center.x, (front.left + back.right) / 2f, 1f)
            assertTrue(front.bottom <= calendarBounds.top)
            val titlePixels = compose.onNodeWithText("Freshness").captureToImage().toPixelMap()
            assertTrue(
                "Freshness uses normal text colour",
                (0 until titlePixels.height).any { y ->
                    (0 until titlePixels.width).any { x -> distance(titlePixels[x, y], theme.textPrimary) < 0.01f }
                },
            )
            for (label in listOf("Recent", "Older")) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(label, useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                // The paragraph reserves the Row's width; only its text lines must fit the Text node.
                assertTrue(
                    "$label must fit: size=${layout.size}, lineRight=${layout.getLineRight(0)}",
                    !layout.multiParagraph.didExceedMaxLines &&
                        (0 until layout.lineCount).all {
                            layout.getLineLeft(it) >= -1f &&
                                layout.getLineRight(it) <= layout.size.width + 1f &&
                                layout.getLineBottom(it) <= layout.size.height + 1f
                        },
                )
            }
            for (surface in listOf(panel, calendar)) {
                val pixels = surface.captureToImage().toPixelMap()
                for ((x, y) in listOf(pixels.width / 2 to 1, 1 to pixels.height / 2, pixels.width - 2 to pixels.height / 2)) {
                    assertTrue("No enclosing border or card fill", distance(pixels[x, y], theme.background) < 0.005f)
                }
            }
            val glow = panel.captureToImage().toPixelMap()
            val y = ((front.center.y - panelBounds.top).toInt()).coerceIn(0, glow.height - 1)
            val centre = distance(glow[glow.width / 2, y], theme.background)
            val edge = distance(glow[0, y], theme.background)
            assertTrue("Whole-section glow reaches the gap between figures: $centre", centre in 0.008f..0.065f)
            assertTrue("Glow must fade towards the edges: centre=$centre edge=$edge", centre > edge + 0.004f)
            val monthPixels = calendar.captureToImage().toPixelMap()
            val firstDay = calendarDay(LocalDate.of(2026, 11, 1)).fetchSemanticsNode().boundsInRoot
            val blankRowY = (firstDay.center.y - calendarBounds.top).toInt()
            val calendarGlow = distance(monthPixels[monthPixels.width / 2, blankRowY], theme.background)
            assertTrue("Calendar has a subtle glow in its empty first-row cells: $calendarGlow", calendarGlow in 0.008f..0.065f)
        }
    }

    @Test
    fun homeCalendarPressDragAndDoubleTapSeparatePreviewFromBreakdown() {
        val today = LocalDate.of(2026, 11, 18)
        fun recency(day: Int) = listOf(
            MuscleRecency("CHEST", null, if (day % 2 == 1) MuscleRecencyBand.UNDER_24_HOURS else MuscleRecencyBand.NEVER, emptyList(), 0.0),
        )
        var state by mutableStateOf(MainUiState(homeRecencyDay = today.withDayOfMonth(8).toEpochDay(), recency = recency(8)))
        val previews = mutableListOf<Int>()
        val palette = VibePalettes.Ocean.dark.copy(recencyNever = Color.Red, recencyUnder24 = Color.Green)
        compose.setContent {
            VibeCheckTheme(palette) {
                Box(Modifier.width(411.dp).height(761.dp).background(palette.background)) {
                    HomeScreen(
                        state,
                        { state = state.copy(selectedHistoryDay = it, historyDay = HistoryDayDetail(it, emptyList())) },
                        {
                            previews += LocalDate.ofEpochDay(it).dayOfMonth
                            state = state.copy(homeRecencyDay = it, recency = recency(previews.last()))
                        },
                        {},
                        today = today,
                        onDayDismiss = { state = state.copy(selectedHistoryDay = null) },
                    )
                }
            }
        }
        val grid = compose.onNodeWithContentDescription("Activity heatmap grid, 6 weeks, Monday first")
        val origin = grid.fetchSemanticsNode().boundsInRoot.topLeft
        fun point(day: Int) = calendarDay(today.withDayOfMonth(day))
            .fetchSemanticsNode().boundsInRoot.center - origin
        fun greenPixels() = compose.onNodeWithContentDescription("male front freshness map").captureToImage().toPixelMap().let { pixels ->
            (0 until pixels.width).sumOf { x -> (0 until pixels.height).count { y -> pixels[x, y].green > 0.8f && pixels[x, y].red < 0.2f } }
        }
        val before = greenPixels()
        val ninth = point(9)
        grid.performTouchInput { down(ninth) }
        assertEquals("Press previews before release or double-tap timeout", today.withDayOfMonth(9).toEpochDay(), state.homeRecencyDay)
        compose.onNodeWithTag("Home day activity").assertDoesNotExist()
        grid.performTouchInput { up() }
        assertTrue("Selected date changes the actual SVG paint", greenPixels() > before + 100)
        val path = listOf(9, 10, 11, 12, 13, 16).map(::point)
        grid.performTouchInput {
            advanceEventTime(400)
            down(path.first())
            path.drop(1).forEach { moveTo(it, 64) }
            up()
        }
        assertEquals(listOf(9, 10, 11, 12, 13, 16), previews.takeLast(6))
        assertEquals(today.withDayOfMonth(16).toEpochDay(), state.homeRecencyDay)
        compose.onNodeWithTag("Home day activity").assertDoesNotExist()
        val future = point(19)
        val blank = Offset(point(2).x, point(1).y)
        grid.performTouchInput { doubleClick(future); click(blank) }
        assertEquals("Inactive cells cannot preview or open", today.withDayOfMonth(16).toEpochDay(), state.homeRecencyDay)
        compose.onNodeWithTag("Home day activity").assertDoesNotExist()
        val seventeenth = point(17)
        grid.performTouchInput { doubleClick(seventeenth) }
        compose.onNodeWithTag("Home day activity").assertIsDisplayed()
        compose.onNodeWithText("17th of November 2026").assertIsDisplayed()
        compose.onNodeWithContentDescription("Historical day page").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close day activity").performClick()
        val open = calendarDay(today.withDayOfMonth(17))
            .fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Open activity breakdown" }
        compose.runOnIdle { assertTrue(open.action()) }
        compose.onNodeWithTag("Home day activity").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close day activity").performClick()
    }

    @Test
    fun homeDayModalKeepsRowsCompactBlursBackdropAndGuardsDate() {
        val today = LocalDate.of(2026, 11, 18)
        val day = today.withDayOfMonth(8).toEpochDay()
        var state by mutableStateOf(MainUiState(homeRecencyDay = day))
        var viewport by mutableStateOf(411.dp to 761.dp)
        var largeRtl by mutableStateOf(false)
        val palette = VibePalettes.Ocean.dark.copy(recencyNever = Color.Green)
        compose.setContent {
            VibeCheckTheme(palette) {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, if (largeRtl) 1.4f else 1f),
                    LocalLayoutDirection provides if (largeRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    Box(Modifier.width(viewport.first).height(viewport.second).background(palette.background)) {
                        HomeScreen(
                            state,
                            { state = state.copy(selectedHistoryDay = it) },
                            { state = state.copy(homeRecencyDay = it) },
                            {},
                            today = today,
                            onDayDismiss = { state = state.copy(selectedHistoryDay = null) },
                        )
                    }
                }
            }
        }
        val calendar = compose.onNodeWithContentDescription("Work tracker card").fetchSemanticsNode().boundsInWindow
        fun mapPaint() = compose.onNodeWithContentDescription("male front freshness map").captureToImage().toPixelMap()
        fun greenCounts(pixels: PixelMap): Pair<Int, Int> {
            var solid = 0
            var soft = 0
            for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
                val color = pixels[x, y]
                if (color.green > color.red + 0.1f && color.green > color.blue + 0.1f) {
                    if (color.green > 0.98f) solid++ else soft++
                }
            }
            return solid to soft
        }
        val sharp = greenCounts(mapPaint())
        compose.mainClock.autoAdvance = false
        compose.runOnIdle {
            state = state.copy(selectedHistoryDay = day, historyDay = HistoryDayDetail(day - 1, listOf(HistoryActivity("Wrong day", "Stale value"))))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(64)
        val modal = compose.onNodeWithTag("Home day activity")
        val appearing = modal.captureToImage().toPixelMap()
        val panelAlpha = appearing[appearing.width / 2, appearing.height - 16].alpha
        val softening = greenCounts(mapPaint())
        assertTrue("Modal is already appearing while the backdrop softens: alpha=$panelAlpha", panelAlpha in 0.03f..0.8f)
        assertTrue("Backdrop blur advances in the same transition", softening.second > sharp.second + 50)
        compose.mainClock.advanceTimeBy(250)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Loading activity...").assertIsDisplayed()
        compose.onNodeWithText("Wrong day").assertDoesNotExist()
        val bounds = modal.fetchSemanticsNode().boundsInWindow
        assertEquals(calendar.left, bounds.left, 1f)
        assertEquals(calendar.top, bounds.top, 1f)
        assertEquals(calendar.right, bounds.right, 1f)
        assertEquals(calendar.bottom, bounds.bottom, 1f)
        val blurred = greenCounts(mapPaint())
        assertTrue("Real background blur softens formerly solid pixels: $sharp -> $blurred", blurred.first < sharp.first * 0.7f)
        assertTrue("Blur spreads softened anatomy paint beyond its sharp edges", blurred.second > sharp.second + 100)
        val activities = listOf(
            HistoryActivity("Meditation", "Duration: 12.0 min"),
            HistoryActivity("Mood", "Feeling: Calm"),
            HistoryActivity("Protein", "Protein: 50.0 g"),
        ) + (1..10).map { HistoryActivity("Session $it", "Strength workout", "Full recorded note $it") }
        compose.runOnIdle { state = state.copy(historyDay = HistoryDayDetail(day, activities)) }
        val rows = compose.onAllNodesWithTag("Home day activity row", useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue(rows.size >= 3)
        val spacing = rows[1].boundsInRoot.top - rows[0].boundsInRoot.top
        assertTrue("One compact row, not stacked tiles: $spacing", spacing in with(compose.density) { 40.dp.toPx()..44.dp.toPx() })
        for (text in listOf("Meditation", "Duration: 12.0 min", "Mood", "Feeling: Calm")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
        }
        val last = "Session 10. Strength workout. Full recorded note 10"
        compose.onNodeWithTag("Home day activity list").performScrollToNode(hasContentDescription(last))
        compose.onNodeWithContentDescription(last).performTouchInput { click() }
        modal.assertIsDisplayed()
        compose.onNodeWithContentDescription("Close day activity").performClick()
        assertEquals(day, state.homeRecencyDay)
        compose.runOnIdle { state = state.copy(selectedHistoryDay = day, historyDay = HistoryDayDetail(day, emptyList())) }
        compose.onNodeWithText("No logged activity").assertIsDisplayed()
        onView(isRoot()).inRoot(isDialog())
            .check { view, error ->
                if (error != null) throw error
                assertTrue("The native dialog owns input focus before Back", view.hasWindowFocus())
            }
            .perform(pressKey(KeyEvent.KEYCODE_BACK))
        modal.assertDoesNotExist()
        compose.runOnIdle { state = state.copy(selectedHistoryDay = day) }
        compose.onNodeWithTag("Home day activity overlay").performTouchInput { click(Offset(5f, 5f)) }
        modal.assertDoesNotExist()
        assertEquals(day, state.homeRecencyDay)
        compose.runOnIdle { viewport = 320.dp to 280.dp; largeRtl = true }
        val home = compose.onNodeWithContentDescription("Home content")
        home.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        val visible = home.fetchSemanticsNode().boundsInWindow
        val scrolledAnatomy = compose.onNodeWithContentDescription("Freshness panel").fetchSemanticsNode().boundsInWindow
        assertTrue("The anatomy is scrolled out of view", scrolledAnatomy.width == 0f || scrolledAnatomy.bottom <= visible.top)
        calendarDay(today.withDayOfMonth(8)).performTouchInput { doubleClick() }
        modal.assertIsDisplayed()
        val constrained = modal.fetchSemanticsNode().boundsInWindow
        assertEquals(visible.top, constrained.top, 1f)
        assertTrue("Scrolled/RTL modal remains inside the Home viewport", constrained.left >= visible.left && constrained.right <= visible.right + 1f && constrained.bottom <= visible.bottom + 1f)
        compose.onNodeWithContentDescription("Close day activity").performClick()
    }

    @Test
    fun homeModeHighlightMovesContinuouslyAndSnapsWithReducedMotion() {
        var mode by mutableStateOf(TrainingMode.STRENGTH)
        var reducedMotion by mutableStateOf(false)
        compose.setContent {
            VibeCheckTheme {
                CompositionLocalProvider(
                    LocalVibeReducedMotion provides reducedMotion,
                    LocalVibeMotion provides VibeMotion.resolve(reducedMotion),
                ) {
                    ModeSelector(mode, { mode = it }, Modifier.width(112.dp).height(48.dp), iconOnly = true)
                }
            }
        }
        val pill = compose.onNodeWithTag("Home selected mode pill", useUnmergedTree = true)
        val start = pill.fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Stretch").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Stretch").assertIsSelected()
        compose.mainClock.advanceTimeBy(48)
        val middle = pill.fetchSemanticsNode().boundsInRoot.left
        compose.mainClock.advanceTimeBy(200)
        val end = pill.fetchSemanticsNode().boundsInRoot.left
        assertTrue("One highlight travels through the space between icons", middle > start + 1 && middle < end - 1)
        compose.onNodeWithContentDescription("Strength").performClick()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithContentDescription("Strength").assertIsSelected()
        assertEquals(start, pill.fetchSemanticsNode().boundsInRoot.left, 1f)
        compose.runOnIdle { reducedMotion = true }
        compose.onNodeWithContentDescription("Stretch").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Stretch").assertIsSelected()
        assertEquals("Reduced motion has no travelling intermediate state", end, pill.fetchSemanticsNode().boundsInRoot.left, 1f)
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun homeCompactModeIconsStayLinkedToCalendar() {
        val today = LocalDate.of(2026, 11, 18)
        var state by mutableStateOf(MainUiState(homeRecencyDay = today.withDayOfMonth(8).toEpochDay()))
        var palette by mutableStateOf(VibePalettes.Ocean.dark)
        var modeChanges = 0
        compose.setContent {
            VibeCheckTheme(palette) {
                Box(Modifier.width(411.dp).height(761.dp).background(palette.background)) {
                    HomeScreen(
                        state, {},
                        { state = state.copy(homeRecencyDay = it) },
                        { modeChanges++; state = state.copy(mode = it) },
                        today = today,
                    )
                }
            }
        }
        compose.onNodeWithText("View date").assertDoesNotExist()
        compose.onNodeWithText("Strength").assertDoesNotExist()
        compose.onNodeWithText("Stretch").assertDoesNotExist()
        for (theme in listOf(VibePalettes.Ocean.dark, VibePalettes.Ocean.light)) {
            compose.runOnIdle { palette = theme }
            val modeBounds = compose.onNodeWithTag("Home mode selector").getUnclippedBoundsInRoot()
            assertTrue(modeBounds.right - modeBounds.left <= 112.dp)
            for (label in listOf("Strength", "Stretch")) {
                val target = compose.onNodeWithContentDescription(label).getUnclippedBoundsInRoot()
                assertTrue("$label full touch target", target.right - target.left >= 48.dp && target.bottom - target.top >= 48.dp)
            }
            val pill = compose.onNodeWithTag("Home selected mode pill", useUnmergedTree = true)
            val paint = pill.captureToImage().toPixelMap()
            assertEquals(theme.surfaceFloating.toArgb(), paint[paint.width / 2, 1].toArgb())
            assertTrue("Inset selection has rounded corners", paint[0, 0].toArgb() != theme.surfaceFloating.toArgb())
        }
        val pill = compose.onNodeWithTag("Home selected mode pill", useUnmergedTree = true)
        val first = pill.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Stretch").performClick().assertIsSelected()
        assertEquals(TrainingMode.STRETCHING, state.mode)
        assertTrue("Selection pill moves to Stretch", pill.fetchSemanticsNode().boundsInRoot.left > first.left)
        val changed = modeChanges
        compose.onNodeWithContentDescription("Stretch").performClick()
        assertEquals("Reselecting current mode does not dispatch", changed, modeChanges)
        compose.onNodeWithContentDescription("Strength").performClick().assertIsSelected()
        assertEquals(TrainingMode.STRENGTH, state.mode)
        val calendar = compose.onNodeWithContentDescription("Work tracker card").fetchSemanticsNode().boundsInRoot
        val front = compose.onNodeWithContentDescription("male front freshness map").fetchSemanticsNode().boundsInRoot
        assertTrue(front.bottom <= calendar.top)
        val oldDay = compose.onNodeWithContentDescription("8th of November 2026: 0 activities, selected freshness date")
        oldDay.assertIsSelected()
        calendarDay(LocalDate.of(2026, 11, 12)).performClick()
        compose.waitForIdle()
        val selected = LocalDate.ofEpochDay(requireNotNull(state.homeRecencyDay))
        assertTrue(selected.dayOfMonth > 8)
        compose.onNodeWithContentDescription("${formatDisplayDate(selected)}: 0 activities, selected freshness date").assertIsSelected()
        compose.onNodeWithContentDescription("8th of November 2026: 0 activities").assertIsNotSelected()
    }

    @Test
    fun homeExpandedAnatomyUsesSpareSpaceAndKeepsTransformedTaps() {
        var showHome by mutableStateOf(false)
        val palette = VibePalettes.Ocean.dark.copy(recencyNever = Color.Green)
        compose.setContent {
            VibeCheckTheme(palette) {
                Box(Modifier.width(411.dp).height(761.dp).background(palette.background)) {
                    if (showHome) {
                        HomeScreen(MainUiState(), {}, {}, {}, today = LocalDate.of(2026, 11, 18))
                    } else {
                        Row {
                            AnatomyView.entries.forEach { view ->
                                MuscleMap(
                                    sex = AnatomySex.MALE, view = view, states = emptyMap(), onMuscleTap = {},
                                    // Previous411dp review geometry, before reclaiming gutters/padding.
                                    modifier = Modifier.width(134.5.dp).height(312.dp),
                                    alignToLegendBounds = true, musclesOnly = true, referenceCanvasHeight = 372.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
        fun muscleBounds(pixels: PixelMap): androidx.compose.ui.geometry.Rect {
            var left = pixels.width
            var top = pixels.height
            var right = 0
            var bottom = 0
            for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
                val color = pixels[x, y]
                if (color.green > 0.8f && color.red < 0.2f && color.blue < 0.2f) {
                    left = minOf(left, x); top = minOf(top, y)
                    right = maxOf(right, x); bottom = maxOf(bottom, y)
                }
            }
            assertTrue("Visible muscle pixels", right > left && bottom > top)
            return androidx.compose.ui.geometry.Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        }
        val before = AnatomyView.entries.associateWith { view ->
            muscleBounds(compose.onNodeWithContentDescription("male ${view.name.lowercase()} freshness map").captureToImage().toPixelMap())
        }
        compose.runOnIdle { showHome = true }
        AnatomyView.entries.forEach { view ->
            val map = compose.onNodeWithContentDescription("male ${view.name.lowercase()} freshness map")
            val pixels = map.captureToImage().toPixelMap()
            val after = muscleBounds(pixels)
            val original = before.getValue(view)
            val widthGrowth = after.width / original.width
            val heightGrowth = after.height / original.height
            assertTrue("$view visibly wider: $widthGrowth", widthGrowth in 1.05f..1.12f)
            assertTrue("$view visibly taller: $heightGrowth", heightGrowth in 1.05f..1.12f)
            assertTrue("$view preserves proportions", kotlin.math.abs(widthGrowth - heightGrowth) < 0.03f)
            assertTrue("$view no clipping", after.left > 0 && after.top > 0 && after.right < pixels.width - 1 && after.bottom < pixels.height - 1)
        }
        compose.onNodeWithContentDescription("male front freshness map").performTouchInput {
            doubleClick(Offset(width * 0.4f, height * 0.15f))
        }
        compose.onNodeWithText("Chest").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close muscle activity").performClick()
    }

    @Test
    fun homeFiguresStayCenteredAboveTheBottomCalendarWhenTheViewportGrows() {
        var viewportWidth by mutableStateOf<Dp>(411.dp)
        var viewportHeight by mutableStateOf<Dp>(761.dp)
        compose.setContent {
            VibeCheckTheme {
                Box(Modifier.width(viewportWidth).height(viewportHeight)) {
                    HomeScreen(
                        state = MainUiState(),
                        onDayClick = {},
                        onRecencyDayChange = {},
                        onModeChange = {},
                    )
                }
            }
        }

        var previousBounds: List<androidx.compose.ui.geometry.Rect>? = null
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
            val freshness = compose.onNodeWithContentDescription("Freshness panel").fetchSemanticsNode().boundsInRoot
            val grid = compose.onNodeWithContentDescription("Activity heatmap grid", substring = true)
                .fetchSemanticsNode().boundsInRoot
            val heading = compose.onNodeWithTag("Home freshness heading").fetchSemanticsNode().boundsInRoot
            val front = compose.onNodeWithContentDescription("male front freshness map").fetchSemanticsNode().boundsInRoot
            val back = compose.onNodeWithContentDescription("male back freshness map").fetchSemanticsNode().boundsInRoot
            assertEquals("$width x $height scroll", 0f, range.maxValue(), 0.5f)
            assertEquals(with(compose.density) { 42.dp.toPx() }, viewport.bottom - tracker.bottom, 1f)
            assertEquals(with(compose.density) { 46.dp.toPx() }, viewport.bottom - grid.bottom, 1.5f)
            assertTrue(freshness.bottom <= tracker.top)
            val centre = (heading.bottom + tracker.top) / 2f
            assertEquals(centre, front.center.y, 1f)
            assertEquals(centre, back.center.y, 1f)
            previousBounds?.let { previous ->
                assertEquals(previous[1].height, tracker.height, 0.5f)
                val extraHeight = viewport.height - previous[2].height
                assertEquals(extraHeight, freshness.height - previous[0].height, 0.5f)
                assertEquals(extraHeight, tracker.top - previous[1].top, 0.5f)
                assertEquals(previous[3].height, grid.height, 0.5f)
                assertEquals(extraHeight, grid.top - previous[3].top, 0.5f)
                assertEquals(previous[4], heading)
                listOf(front, back).forEachIndexed { index, figure ->
                    val before = previous[5 + index]
                    assertEquals(before.width, figure.width, 0.5f)
                    assertEquals(before.height, figure.height, 0.5f)
                    assertEquals(extraHeight / 2f, figure.center.y - before.center.y, 1f)
                }
            }
            previousBounds = listOf(freshness, tracker, viewport, grid, heading, front, back)
        }
    }

    @Test
    fun homeCalendarMatchesHabitCellHeightAndPrimaryMonthColourAtLargerText() {
        val today = LocalDate.of(2026, 11, 20)
        var fontScale by mutableFloatStateOf(1f)
        var palette by mutableStateOf(VibePalettes.Ocean.dark)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VibeCheckTheme(palette) {
                    Box(Modifier.width(411.dp).height(761.dp)) {
                        HomeScreen(MainUiState(), {}, {}, {}, today = today)
                    }
                }
            }
        }
        for (theme in listOf(VibePalettes.Ocean.dark, VibePalettes.Ocean.light)) {
            for (scale in listOf(1f, 1.6f)) {
                compose.runOnIdle {
                    palette = theme
                    fontScale = scale
                }
                val first = calendarDay(today.withDayOfMonth(1))
                    .getUnclippedBoundsInRoot()
                val last = calendarDay(today.withDayOfMonth(30))
                    .getUnclippedBoundsInRoot()
                assertEquals(22f * scale, (first.bottom - first.top).value, 0.5f)
                assertEquals(first.bottom - first.top, last.bottom - last.top)
                val grid = compose.onNodeWithContentDescription("Activity heatmap grid", substring = true)
                    .getUnclippedBoundsInRoot()
                assertTrue(last.bottom <= grid.bottom)
                val heading = compose.onNodeWithTag("Home freshness heading").getUnclippedBoundsInRoot()
                val calendar = compose.onNodeWithContentDescription("Work tracker card").getUnclippedBoundsInRoot()
                val centre = (heading.bottom + calendar.top) / 2
                for (view in listOf("front", "back")) {
                    val figure = compose.onNodeWithContentDescription("male $view freshness map").getUnclippedBoundsInRoot()
                    assertEquals(centre.value, ((figure.top + figure.bottom) / 2).value, 0.5f)
                    assertTrue(figure.top > heading.bottom && figure.bottom < calendar.top)
                }
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText("November 2026")
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(theme.textPrimary, layouts.single().layoutInput.style.color)
                assertTrue(!layouts.single().hasVisualOverflow)
            }
        }
    }

    @Test
    fun homeCalendarKeepsSixMondayFirstRowsAcrossMonths() {
        var selectedDate by mutableStateOf(LocalDate.of(2021, 2, 1))
        compose.setContent {
            VibeCheckTheme {
                Box(Modifier.width(411.dp).height(761.dp)) {
                    HomeScreen(
                        state = MainUiState(
                            homeRecencyDay = selectedDate.toEpochDay(),
                        ),
                        onDayClick = {},
                        onRecencyDayChange = { selectedDate = LocalDate.ofEpochDay(it) },
                        onModeChange = {},
                    )
                }
            }
        }

        val fixedBounds = listOf("Freshness panel", "Work tracker card", "Activity heatmap grid, 6 weeks, Monday first")
            .map { compose.onNodeWithContentDescription(it).getUnclippedBoundsInRoot() }
        var firstCellHeight: Float? = null
        listOf(YearMonth.of(2021, 2), YearMonth.of(2024, 2), YearMonth.of(2026, 6), YearMonth.of(2026, 8)).forEach { month ->
            compose.runOnIdle { selectedDate = month.atDay(1) }
            compose.waitForIdle()
            val first = calendarDay(month.atDay(1))
                .fetchSemanticsNode().boundsInRoot
            val last = calendarDay(month.atEndOfMonth())
                .fetchSemanticsNode().boundsInRoot
            val grid = compose.onNodeWithContentDescription("Activity heatmap grid", substring = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals("$month first=$first last=$last", first.height, last.height, 1.5f)
            assertTrue("$month first=$first grid=$grid", first.top >= grid.top && last.bottom <= grid.bottom)
            firstCellHeight?.let { assertEquals("$month fixed cell height", it, first.height, 0.5f) }
            firstCellHeight = first.height
            assertEquals(
                fixedBounds,
                listOf("Freshness panel", "Work tracker card", "Activity heatmap grid, 6 weeks, Monday first")
                    .map { compose.onNodeWithContentDescription(it).getUnclippedBoundsInRoot() },
            )
            calendarDay(month.atDay(1).minusDays(1)).assertDoesNotExist()
            calendarDay(month.atEndOfMonth().plusDays(1)).assertDoesNotExist()
            val headings = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                .map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
            headings.zipWithNext().forEach { (left, right) ->
                assertTrue(left.center.x < right.center.x)
                assertEquals(left.center.y, right.center.y, 0.5f)
            }
            assertEquals(headings[month.atDay(1).dayOfWeek.value - 1].center.x, first.center.x, 1f)
        }
    }

    @Test
    fun homeCalendarShadesRelativeUsageWithoutSaturatingAtThreeActivities() {
        val today = LocalDate.of(2026, 11, 18)
        val month = YearMonth.from(today)
        val palette = VibePalettes.Ocean.light
        val counts = listOf(1 to 1, 2 to 4, 3 to 8, 19 to 1000)
            .map { (day, count) -> ActivityDay(month.atDay(day).toEpochDay(), count) } +
            ActivityDay(month.minusMonths(1).atDay(1).toEpochDay(), 1000)
        compose.setContent {
            VibeCheckTheme(palette) {
                Box(Modifier.width(411.dp).height(780.dp)) {
                    HomeScreen(
                        state = MainUiState(homeRecencyDay = today.toEpochDay(), activityDays = counts),
                        onDayClick = {},
                        onRecencyDayChange = {},
                        onModeChange = {},
                        today = today,
                    )
                }
            }
        }
        fun fill(day: Int) = calendarDay(month.atDay(day))
            .captureToImage().toPixelMap().let { it[it.width / 2, 3] }
        val light = fill(1)
        val medium = fill(2)
        val strong = fill(3)
        assertTrue("More usage should darken light-mode cells", light.luminance() > medium.luminance())
        assertTrue("Counts above three must remain distinguishable", medium.luminance() > strong.luminance())
        assertEquals(palette.heatmapThreePlus.toArgb(), strong.toArgb())
        assertEquals(palette.heatmapNeutral.toArgb(), fill(4).toArgb())
        assertEquals(palette.heatmapNeutral.toArgb(), fill(19).toArgb())
    }

    @Test
    fun homeCalendarShowsFutureDatesWithoutAllowingSelection() {
        val today = LocalDate.of(2026, 9, 15)
        var state by mutableStateOf(MainUiState(homeRecencyDay = today.toEpochDay()))
        var openedDay: Long? = null
        compose.setContent {
            VibeCheckTheme {
                Box(Modifier.width(411.dp).height(780.dp)) {
                    HomeScreen(
                        state = state,
                        onDayClick = { openedDay = it },
                        onRecencyDayChange = { state = state.copy(homeRecencyDay = it) },
                        onModeChange = {},
                        today = today,
                    )
                }
            }
        }
        val future = compose.onNodeWithContentDescription("${formatDisplayDate(today.plusDays(1))}: 0 activities, future date")
        future.assertIsDisplayed().assertIsNotEnabled().performTouchInput { click() }
        assertEquals(today.toEpochDay(), state.homeRecencyDay)
        assertEquals(null, openedDay)
        compose.onNodeWithContentDescription("${formatDisplayDate(today.withDayOfMonth(30))}: 0 activities, future date").assertExists()
        compose.onNodeWithContentDescription("Next month").assertIsNotEnabled()
        compose.onNodeWithContentDescription("${formatDisplayDate(today.minusDays(1))}: 0 activities").performClick()
        assertEquals(LocalDate.of(2026, 9, 14).toEpochDay(), state.homeRecencyDay)
        assertEquals(null, openedDay)
    }

    @Test
    fun bottomNavigationStaysPinnedWhileHomeScrollsAndChangesMonth() {
        var state by mutableStateOf(MainUiState())
        compose.setContent {
            VibeCheckTheme {
                Box(Modifier.width(411.dp).height(640.dp)) {
                    Scaffold(
                        bottomBar = {
                            Box(Modifier.semantics { contentDescription = "Bottom navigation fixture" }) {
                                PrimaryNavigationBar(Destination.HOME) {}
                            }
                        },
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            HomeScreen(
                                state = state,
                                onDayClick = {},
                                onRecencyDayChange = { state = state.copy(homeRecencyDay = it) },
                                onModeChange = {},
                            )
                        }
                    }
                }
            }
        }
        val bar = compose.onNodeWithContentDescription("Bottom navigation fixture")
        val before = bar.getUnclippedBoundsInRoot()
        compose.onNodeWithContentDescription("Previous month").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(before, bar.getUnclippedBoundsInRoot())
        compose.onNodeWithText("Plans").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithText("Freshness").performScrollTo()
        assertEquals(before, bar.getUnclippedBoundsInRoot())
    }

    @Test
    fun homeRetainsScrollingForLargeTypeAndConstrainedWidth() {
        var fontScale by mutableFloatStateOf(1.3f)
        var viewportWidth by mutableStateOf<Dp>(411.dp)
        var viewportHeight by mutableStateOf<Dp>(600.dp)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VibeCheckTheme {
                    Box(Modifier.width(viewportWidth).height(viewportHeight)) {
                        HomeScreen(
                            state = MainUiState(),
                            onDayClick = {},
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
            viewportHeight = 400.dp
        }
        assertScrollable("320dp width")
        compose.runOnIdle { fontScale = 2f }
        compose.onNodeWithText("Sun").performScrollTo()
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEach { weekday ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(weekday).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            assertEquals(1, layouts.size)
            assertTrue("$weekday must fit without clipping at 320dp / 200%", !layouts.single().hasVisualOverflow)
        }
    }

    @Test
    fun reducedMotionCompletionAcknowledgesOnceWithoutAnimatedDelay() {
        var consumedId: String? = null
        var completionEvent by mutableStateOf<WorkoutCompletionEvent?>(null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            VibeCheckTheme {
                CompositionLocalProvider(LocalVibeReducedMotion provides true) {
                    HomeScreen(
                        state = MainUiState(),
                        onDayClick = {},
                        onRecencyDayChange = {},
                        onModeChange = {},
                        completionEvent = completionEvent,
                        onCompletionConsumed = { consumedId = it },
                    )
                }
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
        compose.mainClock.autoAdvance = true
    }
}

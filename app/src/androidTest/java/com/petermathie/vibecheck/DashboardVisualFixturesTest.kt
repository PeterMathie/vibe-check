package com.petermathie.vibecheck

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.petermathie.vibecheck.ui.DashboardTokenPreview
import com.petermathie.vibecheck.ui.DashboardVisualFixtures
import com.petermathie.vibecheck.ui.ActivityHeatmap
import com.petermathie.vibecheck.ui.HabitCalendarState
import com.petermathie.vibecheck.ui.HabitMonthHeatmap
import com.petermathie.vibecheck.ui.HabitGlassDialog
import com.petermathie.vibecheck.ui.HabitGlassDialogHost
import com.petermathie.vibecheck.ui.PersonalRecordsContent
import com.petermathie.vibecheck.domain.progress.PersonalRecords
import com.petermathie.vibecheck.domain.progress.PersonalRecordVisibility
import com.petermathie.vibecheck.data.local.TrackerEntity
import com.petermathie.vibecheck.data.local.TrackerFieldEntity
import com.petermathie.vibecheck.data.local.TrackerDailyValueEntity
import com.petermathie.vibecheck.domain.model.ActivityDay
import com.petermathie.vibecheck.ui.components.VibeGraph
import com.petermathie.vibecheck.ui.components.VibeGraphStyle
import com.petermathie.vibecheck.ui.components.formatGraphDate
import com.petermathie.vibecheck.ui.components.VibeSurface
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import com.petermathie.vibecheck.ui.theme.VibePalettes
import com.petermathie.vibecheck.ui.theme.VibeSurfaceLevel
import com.petermathie.vibecheck.ui.theme.VibeSurfaceState
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DashboardVisualFixturesTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun everyPaletteModeHasADeterministicTokenPreview() {
        val activePalette = mutableStateOf(DashboardVisualFixtures.palettes.first())
        compose.setContent { DashboardTokenPreview(activePalette.value) }
        DashboardVisualFixtures.palettes.forEach { palette ->
            compose.runOnIdle { activePalette.value = palette }
            compose.onNodeWithContentDescription(
                "${palette.displayName} ${if (palette.isDark) "dark" else "light"} dashboard tokens",
            ).assertIsDisplayed()
        }
    }

    @Test
    fun sharedSurfaceExposesSelectedAndDisabledSemantics() {
        compose.setContent {
            VibeCheckTheme {
                VibeSurface(
                    level = VibeSurfaceLevel.SELECTED,
                    state = VibeSurfaceState.SELECTED,
                    enabled = false,
                    onClick = {},
                ) {
                    Text("Selected fixture")
                }
            }
        }
        compose.onNodeWithText("Selected fixture")
            .assertIsSelected()
            .assertIsNotEnabled()
    }

    @Test
    fun sharedSurfaceClipsTopHighlightToItsRoundedShape() {
        val palette = VibePalettes.Mono.dark
        var insetPx = 0
        compose.setContent {
            val density = LocalDensity.current
            insetPx = with(density) { 10.dp.roundToPx() }
            VibeCheckTheme(palette) {
                Box(
                    Modifier
                        .size(140.dp, 100.dp)
                        .background(palette.background)
                        .semantics { contentDescription = "Clipped surface fixture" },
                ) {
                    VibeSurface(
                        level = VibeSurfaceLevel.CARD,
                        modifier = Modifier.size(120.dp, 80.dp).align(Alignment.Center),
                    ) {}
                }
            }
        }

        val pixels = compose.onNodeWithContentDescription("Clipped surface fixture").captureToImage().toPixelMap()
        assertEquals(palette.background.toArgb(), pixels[insetPx, insetPx].toArgb())
        assertEquals(palette.background.toArgb(), pixels[pixels.width - insetPx - 1, insetPx].toArgb())
    }

    @Test
    fun monoPreviewRemainsVisibleAt320DpAndTwoHundredPercentFontScale() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(320.dp)) {
                    DashboardTokenPreview(VibePalettes.Mono.dark)
                }
            }
        }

        val preview = compose.onNodeWithContentDescription("Mono dark dashboard tokens")
        preview.assertIsDisplayed()
        val bitmap = preview.captureToImage()
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }

    @Test
    fun graphHasMinimumAccessibleRegionAndDatumActions() {
        compose.setContent {
            VibeCheckTheme {
                VibeGraph(
                    values = listOf(1.0, 2.0, 1.5),
                    dates = listOf(1_700_000_000_000, 1_700_086_400_000, 1_700_172_800_000),
                    unit = "kg",
                )
            }
        }

        compose.onNodeWithContentDescription("Progress chart", substring = true)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun graphReadoutStaysAbovePlotWhileScrubbingAtTwoHundredPercentFontScale() {
        val dates = listOf(1_700_000_000_000L, 1_700_086_400_000L, 1_700_172_800_000L)
        var selected = -1
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(320.dp)) {
                    VibeCheckTheme {
                        VibeGraph(
                            values = listOf(2.0, 8.0, 4.0),
                            secondaryValues = listOf(3.0, 6.0, 5.0),
                            dates = dates,
                            unit = "kg",
                            onSelect = { selected = it },
                        )
                    }
                }
            }
        }
        val graph = compose.onNodeWithContentDescription("Progress chart", substring = true)
        val readout = compose.onNodeWithContentDescription("Graph readout")
        val initialReadoutBounds = readout.fetchSemanticsNode().boundsInRoot
        val initialPlotBounds = graph.fetchSemanticsNode().boundsInRoot
        readout.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "${formatGraphDate(dates[2])}, 4 kg"))
        assertEquals(-1, selected)
        assertTrue(initialReadoutBounds.bottom <= initialPlotBounds.top)
        assertTrue(initialReadoutBounds.left >= initialPlotBounds.left && initialReadoutBounds.right <= initialPlotBounds.right)

        graph.performTouchInput {
            down(center)
            moveTo(Offset(1f, center.y), delayMillis = 200)
        }
        readout.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "${formatGraphDate(dates[0])}, 2 kg"))
        assertEquals(0, selected)
        assertEquals(initialReadoutBounds, readout.fetchSemanticsNode().boundsInRoot)
        assertEquals(initialPlotBounds, graph.fetchSemanticsNode().boundsInRoot)

        graph.performTouchInput {
            moveTo(Offset(right - 1f, center.y), delayMillis = 200)
            up()
        }
        readout.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "${formatGraphDate(dates[2])}, 4 kg"))
        assertEquals(2, selected)
        assertEquals(initialReadoutBounds, readout.fetchSemanticsNode().boundsInRoot)
        assertEquals(initialPlotBounds, graph.fetchSemanticsNode().boundsInRoot)

        fun selectPoint(label: String) {
            val action = graph.fetchSemanticsNode().config[SemanticsActions.CustomActions]
                .first { it.label == label }
            compose.runOnIdle { assertTrue(action.action()) }
            compose.waitForIdle()
        }
        selectPoint("Previous data point")
        readout.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "${formatGraphDate(dates[1])}, 8 kg"))
        assertEquals(1, selected)
        selectPoint("Next data point")
        assertEquals(2, selected)
        assertEquals(initialReadoutBounds, readout.fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("Graph tooltip").assertDoesNotExist()
    }

    @Test
    fun barReadoutUsesLatestFiniteValueAndSkipsMissingEntries() {
        val dates = listOf(1_700_000_000_000L, 1_700_086_400_000L, 1_700_172_800_000L, 1_700_259_200_000L)
        val values = mutableStateOf(listOf(2.0, Double.NaN, 7.0, Double.NaN))
        var selected = -1
        compose.setContent {
            VibeCheckTheme {
                VibeGraph(
                    values = values.value,
                    dates = dates,
                    unit = "RPE",
                    style = VibeGraphStyle.BARS,
                    fixedRange = 0.0..10.0,
                    onSelect = { selected = it },
                )
            }
        }
        val graph = compose.onNodeWithContentDescription("0 to 10 RPE", substring = true)
        val readout = compose.onNodeWithContentDescription("Graph readout")
        val bounds = readout.fetchSemanticsNode().boundsInRoot
        readout.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "${formatGraphDate(dates[2])}, 7 RPE"))
        graph.performTouchInput { click(Offset(1f, center.y)) }
        assertEquals(0, selected)
        readout.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "${formatGraphDate(dates[0])}, 2 RPE"))
        graph.performTouchInput { click(Offset(right - 1f, center.y)) }
        assertEquals(2, selected)
        readout.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "${formatGraphDate(dates[2])}, 7 RPE"))
        assertEquals(bounds, readout.fetchSemanticsNode().boundsInRoot)
        assertTrue(bounds.bottom <= graph.fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithContentDescription("Graph tooltip").assertDoesNotExist()

        compose.runOnIdle { values.value = List(4) { Double.NaN } }
        readout.assertDoesNotExist()
        compose.onNodeWithText("Complete a valid entry to see this chart.").assertIsDisplayed()
    }

    @Test
    fun activityCalendarPreservesCountsSelectionAndMonthNavigation() {
        val today = LocalDate.of(2026, 3, 12)
        val history = listOf(
            ActivityDay(LocalDate.of(2026, 2, 8).toEpochDay(), 3),
            ActivityDay(today.withDayOfMonth(10).toEpochDay(), 2),
        )
        var selected: Long? = null
        compose.setContent {
            VibeCheckTheme {
                ActivityHeatmap(history, onDayClick = { selected = it }, today = today)
            }
        }
        val calendar = compose.onNodeWithContentDescription("activities calendar")
        val readout = compose.onNodeWithContentDescription("activities entry")
        val initialBounds = readout.fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText("Earlier").assertDoesNotExist()
        compose.onNodeWithText("Later").assertDoesNotExist()
        compose.onNodeWithContentDescription("Next month for activities").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous month for activities").assertIsEnabled()
        val monday = compose.onNodeWithContentDescription("2nd of March 2026: 0 activities").fetchSemanticsNode().boundsInRoot
        val sunday = compose.onNodeWithContentDescription("1st of March 2026: 0 activities").fetchSemanticsNode().boundsInRoot
        assertEquals(compose.onNodeWithText("Mon").fetchSemanticsNode().boundsInRoot.center.x, monday.center.x, 0.5f)
        assertEquals(compose.onNodeWithText("Sun").fetchSemanticsNode().boundsInRoot.center.x, sunday.center.x, 0.5f)
        assertTrue(sunday.top < monday.top)
        compose.onNodeWithContentDescription("13th of March 2026: 0 activities, future date").assertIsNotEnabled()
        assertEquals(null, selected)

        compose.onNodeWithContentDescription("10th of March 2026: 2 activities").performClick().assertIsSelected()
        compose.onNode(hasText("2 activities") and hasAnyAncestor(hasContentDescription("activities entry"))).assertIsDisplayed()
        assertEquals(today.withDayOfMonth(10).toEpochDay(), selected)
        assertEquals(initialBounds, readout.fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("11th of March 2026: 0 activities").performClick().assertIsSelected()
        assertEquals(today.withDayOfMonth(11).toEpochDay(), selected)
        compose.onNode(hasText("0 activities") and hasAnyAncestor(hasContentDescription("activities entry"))).assertIsDisplayed()
        assertEquals(initialBounds, readout.fetchSemanticsNode().boundsInRoot)

        compose.onNodeWithContentDescription("Previous month for activities").performClick().assertIsNotEnabled()
        compose.onNodeWithContentDescription("8th of February 2026: 3 activities").performClick().assertIsSelected()
        assertEquals(history[0].epochDay, selected)
        assertEquals(initialBounds, readout.fetchSemanticsNode().boundsInRoot)
        calendar.performTouchInput { swipeRight() }
        compose.onNodeWithContentDescription("Next month for activities").assertIsNotEnabled()
        compose.onNodeWithContentDescription("10th of March 2026: 2 activities").assertExists()
        calendar.performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Previous month for activities").assertIsNotEnabled()
        compose.onNodeWithContentDescription("8th of February 2026: 3 activities").assertExists()
    }

    @Test
    fun activityCalendarMatchesHabitCellsAtLargeText() {
        val today = LocalDate.of(2026, 3, 12)
        val recorded = today.withDayOfMonth(10)
        val palette = VibePalettes.Mono.dark
        val tracker = TrackerEntity("calendar-fixture", "Calendar fixture", false, colourArgb = palette.heatmapThreePlus.toArgb().toLong())
        val field = TrackerFieldEntity("field", tracker.id, "Done", "BOOLEAN", null, null, null, 0)
        val value = TrackerDailyValueEntity(field.id, recorded.toEpochDay(), null, true, null, "", 1)
        val state = HabitCalendarState("2026-03")
        val showHabit = mutableStateOf(false)
        val expanded = mutableStateOf(true)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(320.dp)) {
                    VibeCheckTheme(palette) {
                        if (showHabit.value) {
                            HabitMonthHeatmap(tracker, field, listOf(value), today, state, expanded.value, {})
                        } else {
                            ActivityHeatmap(listOf(ActivityDay(recorded.toEpochDay(), 3)), {}, today = today)
                        }
                    }
                }
            }
        }
        val activityCell = compose.onNodeWithContentDescription("10th of March 2026: 3 activities")
        val bounds = activityCell.fetchSemanticsNode().boundsInRoot
        val pixels = activityCell.captureToImage().toPixelMap()
        val calendarBounds = compose.onNodeWithContentDescription("activities calendar").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { showHabit.value = true }
        val habitCell = compose.onNodeWithContentDescription("10th of March 2026: Yes")
        assertEquals(bounds, habitCell.fetchSemanticsNode().boundsInRoot)
        val habitPixels = habitCell.captureToImage().toPixelMap()
        assertEquals(pixels[3, pixels.height / 2], habitPixels[3, habitPixels.height / 2])
        val habitBounds = compose.onNodeWithTag("Habit calendar ${tracker.id}").fetchSemanticsNode().boundsInRoot
        assertEquals(calendarBounds.top, habitBounds.top)
        assertEquals(calendarBounds.width, habitBounds.width)
        assertTrue("Habits no longer reserves the activity readout", habitBounds.height < calendarBounds.height)
        habitCell.performClick().assertIsSelected()
        compose.onNodeWithTag("Habit entry value ${tracker.id}").assertDoesNotExist()
        compose.runOnIdle { expanded.value = false }
        compose.onNodeWithContentDescription("Previous month for ${tracker.name}").assertDoesNotExist()
        compose.onNodeWithContentDescription("10th of March 2026: Yes").performClick().assertIsSelected()
        compose.onNodeWithTag("Habit entry value ${tracker.id}").assertDoesNotExist()
    }

    @Test
    fun datedGraphsKeepOneAlignedReadoutBelowThePlotAtLargeText() {
        val start = LocalDate.of(2026, 10, 16).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val day = 86_400_000L
        val dates = listOf(start, start + day, start + 28 * day)
        val style = mutableStateOf(VibeGraphStyle.LINE)
        val fontScale = mutableStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                Box(Modifier.width(320.dp)) {
                    VibeCheckTheme {
                        VibeGraph(
                            values = if (style.value == VibeGraphStyle.LINE) listOf(99.3, 120.0, 144.3) else listOf(2.0, 5.0, 7.0),
                            dates = dates, unit = if (style.value == VibeGraphStyle.LINE) "points" else "RPE",
                            style = style.value,
                            fixedRange = if (style.value == VibeGraphStyle.BARS) 0.0..10.0 else null,
                            dateRange = start..(start + 31 * day),
                            readoutBelow = true,
                        )
                    }
                }
            }
        }
        listOf(1f, 2f).forEach { scale ->
            VibeGraphStyle.entries.forEach { chartStyle ->
                compose.runOnIdle { fontScale.value = scale; style.value = chartStyle }
                val graph = compose.onNodeWithContentDescription("Progress chart", substring = true)
                val readout = compose.onNodeWithContentDescription("Graph readout")
                val axis = compose.onNodeWithContentDescription("Graph date axis")
                val bounds = readout.fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.top >= axis.fetchSemanticsNode().boundsInRoot.bottom)
                assertTrue(axis.fetchSemanticsNode().boundsInRoot.top >= graph.fetchSemanticsNode().boundsInRoot.bottom)
                val labels = axis.onChildren().fetchSemanticsNodes().sortedBy { it.boundsInRoot.left }
                assertTrue(labels.size >= if (scale == 1f) 3 else 2)
                assertTrue(labels.zipWithNext().all { (a, b) -> a.boundsInRoot.right <= b.boundsInRoot.left })
                val unit = if (chartStyle == VibeGraphStyle.LINE) "points" else "RPE"
                val latest = if (chartStyle == VibeGraphStyle.LINE) "144.3" else "7"
                compose.onAllNodesWithText("$latest $unit").assertCountEquals(1)
                val dateNode = compose.onNode(hasText(formatGraphDate(dates.last())) and hasAnyAncestor(hasContentDescription("Graph readout")))
                val valueNode = compose.onNode(hasText("$latest $unit") and hasAnyAncestor(hasContentDescription("Graph readout")))
                assertEquals(bounds.left, dateNode.fetchSemanticsNode().boundsInRoot.left, 0.5f)
                assertEquals(bounds.right, valueNode.fetchSemanticsNode().boundsInRoot.right, 0.5f)
                graph.performTouchInput { click(Offset(width * 0.2f, center.y)) }
                val middle = if (chartStyle == VibeGraphStyle.LINE) "120" else "5"
                readout.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "${formatGraphDate(dates[1])}, $middle $unit"))
                graph.performTouchInput {
                    down(center)
                    moveTo(Offset(1f, center.y), delayMillis = 200)
                    up()
                }
                val first = if (chartStyle == VibeGraphStyle.LINE) "99.3" else "2"
                readout.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "${formatGraphDate(dates[0])}, $first $unit"))
                assertEquals(bounds, readout.fetchSemanticsNode().boundsInRoot)
                graph.performTouchInput { click(Offset(right - 1f, center.y)) }
                assertEquals(bounds, readout.fetchSemanticsNode().boundsInRoot)
            }
        }
    }

    @Test
    fun personalRecordsAreReadOnlyRowsWithoutInsetFill() {
        val palette = VibePalettes.Mono.dark
        compose.setContent {
            VibeCheckTheme(palette) {
                Box(Modifier.width(320.dp).background(palette.background)) {
                    Column {
                        PersonalRecordsContent(
                            PersonalRecords(80.0, null, 100.0, null),
                            PersonalRecordVisibility(weight = true, hold = false, estimatedOneRepMax = true),
                        )
                    }
                }
            }
        }
        val records = compose.onNodeWithContentDescription("Personal records")
        val pixels = records.captureToImage().toPixelMap()
        assertEquals(palette.background.toArgb(), pixels[5, 5].toArgb())
        compose.onNodeWithText("80 kg").assertIsDisplayed()
        compose.onNodeWithText("100 kg").assertIsDisplayed()
        compose.onAllNodes(hasClickAction() and hasAnyAncestor(hasContentDescription("Personal records"))).assertCountEquals(0)
    }

    @Test
    fun sharedGlassHostBlursBackdropAndRestoresItAfterDismissal() {
        val showing = mutableStateOf(false)
        compose.setContent {
            VibeCheckTheme {
                HabitGlassDialogHost { backdrop, _ ->
                    Canvas(Modifier.size(160.dp).then(backdrop).testTag("Modal backdrop")) {
                        drawRect(Color.Black)
                        drawRect(Color.White, topLeft = Offset(size.width / 2, 0f), size = androidx.compose.ui.geometry.Size(size.width / 2, size.height))
                    }
                    if (showing.value) HabitGlassDialog("Fixture", { showing.value = false }) {
                        Text("Sharp modal content")
                    }
                }
            }
        }
        val background = compose.onNodeWithTag("Modal backdrop")
        val before = background.captureToImage().toPixelMap()
        val x = before.width / 2 + 6
        val y = before.height / 2
        assertEquals(Color.White.toArgb(), before[x, y].toArgb())
        compose.runOnIdle { showing.value = true }
        compose.onNodeWithText("Sharp modal content").assertIsDisplayed()
        val blurred = background.captureToImage().toPixelMap()[x, y]
        assertTrue("Backdrop edge should blur instead of remaining readable/sharp", blurred.red in 0.45f..0.95f)
        compose.onNodeWithContentDescription("Close").performClick()
        assertEquals(before[x, y].toArgb(), background.captureToImage().toPixelMap()[x, y].toArgb())
    }
}

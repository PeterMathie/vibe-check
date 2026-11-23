package com.petermathie.vibecheck

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.test.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.petermathie.vibecheck.data.local.*
import com.petermathie.vibecheck.domain.tracker.*
import com.petermathie.vibecheck.ui.*
import com.petermathie.vibecheck.ui.theme.*
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HabitUiTest {
    private lateinit var database: VibeDatabase
    private lateinit var dismissFixtureKeyboard: () -> Unit
    @get:Rule val lifecycle = ComposeRoomLifecycleRule { if (::database.isInitialized) database else null }
    private val compose get() = lifecycle.compose
    private val tracker = TrackerEntity("tracker", "Wellbeing", false)
    private val number = TrackerFieldEntity("number", "tracker", "Amount", "NUMBER", "g", null, null, 0)
    private val fixturePreferences by lazy {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("habits-ui-test", 0)
    }

    @Before fun resetFixturePreferences() {
        assertTrue(fixturePreferences.edit().clear().commit())
    }

    @After fun clearFixturePreferences() {
        assertTrue(fixturePreferences.edit().clear().commit())
    }

    private fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), VibeDatabase::class.java,
        ).build()
    }

    @Composable
    private fun HabitFixture(
        palette: VibePalette = VibePalettes.Ocean.dark,
        content: @Composable () -> Unit,
    ) {
        VibeCheckTheme(palette) {
            val palette = LocalVibePalette.current
            val reducedMotion = LocalVibeReducedMotion.current
            val fabHost = remember { AppFabHostState(Destination.HABITS) }
            val focus = LocalFocusManager.current
            val keyboard = LocalSoftwareKeyboardController.current
            SideEffect { dismissFixtureKeyboard = { focus.clearFocus(); keyboard?.hide() } }
            val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            val fabAction = fabHost.action.visibleUnlessBlocked(fabHost.dragging, imeVisible)
            CompositionLocalProvider(
                LocalAppFabHost provides fabHost,
                LocalAppFabClearance provides if (fabAction == null) 0.dp else 88.dp,
            ) {
                Scaffold(
                    containerColor = palette.background,
                    floatingActionButtonPosition = FabPosition.End,
                    floatingActionButton = {
                        AnimatedVisibility(
                            visible = fabAction != null,
                            enter = if (reducedMotion) EnterTransition.None else fadeIn(),
                            exit = if (reducedMotion) ExitTransition.None else fadeOut(),
                        ) {
                            fabAction?.let { AppFloatingAction(it, fabHost) }
                        }
                    },
                    bottomBar = { PrimaryNavigationBar(Destination.HABITS) {} },
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                        content()
                    }
                }
            }
        }
    }

    private fun launch(
        fields: List<TrackerFieldEntity>,
        habit: TrackerEntity = tracker,
        values: List<TrackerDailyValueEntity> = emptyList(),
    ) {
        createDatabase()
        runBlocking {
            database.trackerDao().insertTrackers(listOf(habit))
            database.trackerDao().insertFields(fields)
            values.forEach { database.trackerDao().upsertValue(it) }
        }
        val vm = lifecycle.own(EditorViewModel(database))
        compose.setContent { HabitFixture { TrackerScreen(vm, fixturePreferences) } }
        awaitHabit(habit.name)
    }

    private fun awaitHabit(name: String = tracker.name) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Reorder $name").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun input(label: String) = compose.onNode(
        (hasText(label) or hasContentDescription(label)) and hasSetTextAction(),
    )
    private fun button(label: String) = compose.onNode(hasText(label) and hasClickAction())
    private fun menuOption(label: String) = compose.onNode(
        hasText(label) and hasClickAction() and hasAnyAncestor(isPopup()),
    )
    private fun assertExitActionsShareRow(vararg labels: String) {
        val actions = labels.map { button(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        actions.zipWithNext().forEach { (left, right) ->
            assertEquals("Exit actions must share one row", left.center.y, right.center.y, 1f)
            assertTrue("Exit actions must not overlap", left.right <= right.left)
        }
    }
    private fun edit(name: String = tracker.name) {
        compose.onNodeWithContentDescription("Edit $name settings").performScrollTo().performClick()
    }
    private fun chooseGoal(label: String) {
        compose.onNodeWithContentDescription("Goal (optional)").performScrollTo().performClick()
        menuOption(label).performClick()
    }
    private fun awaitSaved(predicate: suspend () -> Boolean) {
        compose.waitUntil(15_000) { runBlocking { predicate() } }
    }

    @Test(timeout = 120_000)
    fun habitIconOpensSettingsWhileNameTogglesAndLongPressReorders() {
        launch(listOf(number))
        val icon = compose.onNodeWithContentDescription("Edit Wellbeing settings")
        val iconBounds = icon.getUnclippedBoundsInRoot()
        withCiFailureDiagnostics(
            compose, "habits-icon-touch-target",
            mapOf(
                "icon" to icon,
                "heading" to compose.onNodeWithContentDescription("Reorder Wellbeing"),
                "calendar" to compose.onNodeWithTag("Habit calendar ${tracker.id}"),
                "viewport" to compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)),
            ),
        ) {
            assertTrue(iconBounds.right - iconBounds.left >= 48.dp && iconBounds.bottom - iconBounds.top >= 48.dp)
        }
        icon.performTouchInput { click() }
        compose.onNodeWithText("Edit habit").assertExists()
        assertFalse(fixturePreferences.getStringSet(COLLAPSED_HABITS, emptySet()).orEmpty().contains(tracker.id))
        captureFixture("habits-icon-edit-menu")
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithContentDescription("Previous month for ${tracker.name}").assertExists()
        val second = tracker.copy(id = "second", name = "Reading", position = 1)
        runBlocking {
            database.trackerDao().insertTrackers(listOf(second))
            database.trackerDao().insertFields(listOf(number.copy(id = "second-field", trackerId = second.id)))
        }
        awaitHabit(second.name)
        val header = compose.onNodeWithContentDescription("Reorder Wellbeing")
        compose.onNodeWithContentDescription("Swipe actions for Wellbeing").assertDoesNotExist()
        header.performTouchInput { swipeRight() }
        compose.onNodeWithText("Edit habit").assertDoesNotExist()
        header.performTouchInput { swipeLeft() }
        compose.onNodeWithText("Edit habit").assertDoesNotExist()
        icon.assertIsDisplayed()
        compose.onNodeWithText(tracker.name).performClick()
        compose.onNodeWithContentDescription("Previous month for ${tracker.name}").assertDoesNotExist()
        icon.performTouchInput { click() }
        compose.onNodeWithText("Edit habit").assertExists()
        assertTrue(fixturePreferences.getStringSet(COLLAPSED_HABITS, emptySet()).orEmpty().contains(tracker.id))
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithContentDescription("Previous month for ${tracker.name}").assertDoesNotExist()
        compose.onNodeWithText(tracker.name).performClick()
        compose.onNodeWithContentDescription("Previous month for ${tracker.name}").assertExists()
        header.performTouchInput { swipeUp() }
        assertEquals(listOf(tracker.id, second.id),
            runBlocking { database.editorDao().trackers().first() }.sortedBy { it.position }.map { it.id })
        val firstHandle = compose.onNodeWithContentDescription("Reorder Wellbeing").performScrollTo()
        val firstBounds = firstHandle.fetchSemanticsNode().boundsInRoot
        val secondBounds = compose.onNodeWithContentDescription("Reorder Reading").fetchSemanticsNode().boundsInRoot
        val distance = secondBounds.bottom - firstBounds.center.y + 50f
        firstHandle.performTouchInput {
            down(center)
            advanceEventTime(650)
            moveTo(center + Offset(0f, distance), delayMillis = 500)
            up()
        }
        awaitSaved {
            database.editorDao().trackers().first().sortedBy { it.position }.map { it.id } == listOf(second.id, tracker.id)
        }
        compose.onNodeWithTag("Habit calendar ${tracker.id}").assertExists()
        captureFixture("habits-icon-header-reordered")
        edit()
        compose.onNodeWithText("Edit habit").assertExists()
    }

    @Test(timeout = 120_000)
    fun habitGlowReachesTheHeaderAndFadesToZeroAtAllFourEdges() {
        compose.setContent {
            Box(Modifier.size(200.dp, 120.dp).background(Color.Black).habitGlow(Color.Red, true).testTag("glow"))
        }
        val pixels = compose.onNodeWithTag("glow").captureToImage().toPixelMap()
        val x = pixels.width / 2
        val y = pixels.height / 2
        listOf(0 to y, pixels.width - 1 to y, x to 0, x to pixels.height - 1).forEach { (px, py) ->
            assertTrue("Glow edge ($px,$py) must reach zero", pixels[px, py].red <= 1f / 255f)
        }
        assertTrue(pixels[x, y].red > 0.08f)
        assertTrue("The whole-habit glow must remain restrained", pixels[x, y].red <= 0.10f)
        assertTrue("Glow must extend into the header", pixels[x, pixels.height / 6].red > 0.02f)
        assertTrue(pixels[x, pixels.height * 5 / 6].red > 0.02f)
    }

    @Test(timeout = 120_000)
    fun offCentreHabitGlowsPeakAtTheirOriginAndFadeAcrossEveryEdge() {
        var origin by mutableStateOf(Offset(0.23f, 0.71f))
        var dark by mutableStateOf(true)
        compose.setContent {
            Box(Modifier.size(200.dp, 120.dp).background(Color.Black).habitGlow(Color.Red, dark, origin).testTag("glow"))
        }
        listOf(Offset(0.23f, 0.71f) to true, Offset(0.78f, 0.28f) to false).forEach { (point, night) ->
            compose.runOnIdle {
                origin = point
                dark = night
            }
            val pixels = compose.onNodeWithTag("glow").captureToImage().toPixelMap()
            val x = (pixels.width * point.x).toInt()
            val y = (pixels.height * point.y).toInt()
            val peak = pixels[x, y].red
            assertTrue(peak > if (night) 0.08f else 0.04f)
            assertTrue(peak > pixels[pixels.width / 2, pixels.height / 2].red + 0.015f)
            for (px in 0 until pixels.width) {
                assertTrue(pixels[px, 0].red <= 1f / 255f)
                assertTrue(pixels[px, pixels.height - 1].red <= 1f / 255f)
            }
            for (py in 0 until pixels.height) {
                assertTrue(pixels[0, py].red <= 1f / 255f)
                assertTrue(pixels[pixels.width - 1, py].red <= 1f / 255f)
            }
            listOf(x / 2, (x + pixels.width) / 2).forEach { px ->
                assertTrue(pixels[px, y].red in 0.01f..(peak - 0.01f))
            }
            listOf(y / 2, (y + pixels.height) / 2).forEach { py ->
                assertTrue(pixels[x, py].red in 0.01f..(peak - 0.01f))
            }
        }
    }

    @Test(timeout = 120_000)
    fun calendarCellsRevealTwentyPercentOfTheBackgroundInMonthAndWeek() {
        val today = LocalDate.of(2026, 11, 20)
        val values = listOf(17 to 10.0, 18 to 20.0).map { (day, amount) ->
            val epoch = today.withDayOfMonth(day).toEpochDay()
            TrackerDailyValueEntity(number.id, epoch, amount, null, null, "", epoch)
        }
        var palette by mutableStateOf(VibePalettes.Ocean.dark)
        var expanded by mutableStateOf(true)
        compose.setContent {
            HabitFixture(palette) {
                Box(Modifier.background(Color.Magenta)) {
                    val state = remember { HabitCalendarState(YearMonth.from(today).toString()) }
                    HabitMonthHeatmap(tracker, number, values, today, state, expanded, {})
                }
            }
        }
        listOf(VibePalettes.Ocean.dark, VibePalettes.Ocean.light).forEach { theme ->
            listOf(true, false).forEach { showMonth ->
                compose.runOnIdle { palette = theme; expanded = showMonth }
                val shades = theme.habitHeatmapColors(Color(tracker.colourArgb.toInt()))
                listOf(
                    Triple("17 November 2026: 10 g", shades.low, 0.8f),
                    Triple("18 November 2026: 20 g", shades.strong, 0.8f),
                    Triple("19 November 2026: No entry", shades.neutral, 0.8f),
                    Triple("21 November 2026: No entry", shades.neutral, 0.32f * 0.8f),
                ).forEach { (description, fill, alpha) ->
                    val pixels = compose.onNodeWithContentDescription(description).captureToImage().toPixelMap()
                    val pixel = pixels[pixels.width / 2, with(compose.density) { 1.dp.roundToPx() }]
                    val expected = fill.copy(alpha = alpha).compositeOver(Color.Magenta)
                    assertEquals("$description red", expected.red, pixel.red, 2f / 255f)
                    assertEquals("$description green", expected.green, pixel.green, 2f / 255f)
                    assertEquals("$description blue", expected.blue, pixel.blue, 2f / 255f)
                }
            }
        }
    }

    @Test(timeout = 120_000)
    fun dailyTextInputsStayUnboxedAndRightAlignedInEveryState() {
        createDatabase()
        val vm = lifecycle.own(EditorViewModel(database))
        var palette by mutableStateOf(VibePalettes.Ocean.dark)
        compose.setContent {
            VibeCheckTheme(palette) {
                key(palette) {
                    Box(Modifier.width(300.dp).background(Color.Magenta)) {
                        HabitDailyInput(vm, number, null, LocalDate.now().toEpochDay(), Color.Red)
                    }
                }
            }
        }
        fun assertUnboxed() {
            val pixels = input("g").captureToImage().toPixelMap()
            val x = with(compose.density) { 8.dp.roundToPx() }
            (0 until pixels.height).forEach { y ->
                val pixel = pixels[x, y]
                assertEquals("No fill or indicator at ($x,$y)", 1f, pixel.red, 1f / 255f)
                assertEquals("No fill or indicator at ($x,$y)", 0f, pixel.green, 1f / 255f)
                assertEquals("No fill or indicator at ($x,$y)", 1f, pixel.blue, 1f / 255f)
            }
        }
        listOf(VibePalettes.Ocean.dark, VibePalettes.Ocean.light).forEach { theme ->
            compose.runOnIdle { palette = theme }
            assertUnboxed()
            val label = compose.onNodeWithText("g", useUnmergedTree = true)
            val field = input("g").getUnclippedBoundsInRoot()
            assertEquals((field.right - 16.dp).value, label.getUnclippedBoundsInRoot().right.value, 0.5f)
            assertTextEndsAtRight(label)
            input("g").performClick()
            assertUnboxed()
            input("g").performTextReplacement("-3")
            compose.onNodeWithText("Enter a number greater than or equal to zero").assertExists()
            assertUnboxed()
            assertTextEndsAtRight(input("g"))
            assertTextEndsAtRight(label)
            captureFixture(if (theme.isDark) "habits-plain-entry-dark" else "habits-plain-entry-light")
        }
        assertTrue(runBlocking { database.editorDao().values().first() }.isEmpty())
    }

    @Test(timeout = 120_000)
    fun calendarInspectionKeepsDatesLeftAndMeasurementsRightWithoutMovingTheNextHabit() {
        val today = LocalDate.of(2026, 11, 20)
        val history = listOf(18 to 20.0, 19 to 0.0).map { (day, value) ->
            TrackerDailyValueEntity(number.id, today.withDayOfMonth(day).toEpochDay(), value, null, null, "", 1)
        } + TrackerDailyValueEntity(number.id, today.withMonth(9).withDayOfMonth(30).toEpochDay(), 5.0, null, null, "", 1)
        val state = HabitCalendarState("2026-11")
        var expanded by mutableStateOf(true)
        compose.setContent {
            HabitFixture {
                Column {
                    HabitMonthHeatmap(tracker, number, history, today, state, expanded, {})
                    Text("Next habit", Modifier.testTag("Next habit"))
                }
            }
        }
        fun assertDateAndAlignment(date: LocalDate) {
            compose.onNodeWithText(": ").assertDoesNotExist()
            val dateNode = compose.onNodeWithTag("Habit entry date ${tracker.id}")
            dateNode.assertTextEquals(habitCalendarDate(date))
            val bounds = dateNode.getUnclippedBoundsInRoot()
            val readout = compose.onNodeWithTag("Habit entry ${tracker.id}").getUnclippedBoundsInRoot()
            val value = compose.onNodeWithTag("Habit entry value ${tracker.id}")
            assertEquals(readout.left, bounds.left)
            assertEquals(readout.right, value.getUnclippedBoundsInRoot().right)
            withCiFailureDiagnostics(
                compose, "habits-readout-$date-$expanded",
                mapOf(
                    "readout" to compose.onNodeWithTag("Habit entry ${tracker.id}"),
                    "date" to dateNode,
                    "value" to value,
                    "calendar" to compose.onNodeWithTag("Habit calendar ${tracker.id}"),
                    "nextHabit" to compose.onNodeWithTag("Next habit"),
                ),
            ) {
                assertTextEndsAtRight(value)
            }
            dateNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>()
                assertTrue(action(layouts))
                assertFalse(layouts.single().hasVisualOverflow)
                assertEquals(FontFamily.Monospace, layouts.single().layoutInput.style.fontFamily)
                assertEquals(0f, layouts.single().getLineLeft(0), 0.5f)
            }
        }
        listOf(true, false).forEach { month ->
            compose.runOnIdle {
                expanded = month
                state.selectedDay = null
                state.selectedWeekDay = null
            }
            val readout = compose.onNodeWithTag("Habit entry ${tracker.id}")
            val reserved = readout.getUnclippedBoundsInRoot()
            val calendar = compose.onNodeWithTag("Habit calendar ${tracker.id}")
            val before = calendar.getUnclippedBoundsInRoot()
            val next = compose.onNodeWithTag("Next habit").getUnclippedBoundsInRoot()
            listOf("Mon", "Tue", "Wed", "Thur", "Fri", "Sat", "Sun").forEach { label ->
                compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("Habit calendar ${tracker.id}")))
                    .assertIsDisplayed()
            }
            readout.onChildren().assertCountEquals(0)
            listOf(18 to "20 g", 19 to "0 g", 17 to "No entry").forEach { (day, entry) ->
                val description = "$day November 2026: $entry"
                compose.onNodeWithContentDescription(description).performClick().assertIsSelected()
                compose.onNode(hasText(entry) and hasAnyAncestor(hasTestTag("Habit entry ${tracker.id}")))
                    .assertIsDisplayed()
                assertDateAndAlignment(today.withDayOfMonth(day))
                assertEquals(reserved, readout.getUnclippedBoundsInRoot())
                assertEquals(before, calendar.getUnclippedBoundsInRoot())
                assertEquals(next, compose.onNodeWithTag("Next habit").getUnclippedBoundsInRoot())
            }
        }
        compose.runOnIdle {
            expanded = true
            state.show(YearMonth.of(2026, 9))
        }
        compose.onNodeWithContentDescription("30 September 2026: 5 g").performClick()
        assertDateAndAlignment(today.withMonth(9).withDayOfMonth(30))
        compose.runOnIdle { state.show(YearMonth.of(2026, 11)) }
        compose.onNodeWithContentDescription("1 November 2026: No entry").performClick()
        assertDateAndAlignment(today.withDayOfMonth(1))
    }

    @Test(timeout = 120_000)
    fun longCalendarNotesScrollInsideTheReservedReadoutAtLargeText() {
        val today = LocalDate.of(2026, 11, 20)
        val field = number.copy(valueType = "TEXT", unit = null)
        val note = (1..12).joinToString("\n") { "Line $it of the complete recorded note." }
        val day = TrackerDailyValueEntity(field.id, today.toEpochDay(), null, null, note, "", 1)
        val state = HabitCalendarState("2026-11")
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                HabitFixture {
                    Column {
                        HabitMonthHeatmap(tracker, field, listOf(day), today, state, true, {})
                        Text("Next habit", Modifier.testTag("Next habit"))
                    }
                }
            }
        }
        val readout = compose.onNodeWithTag("Habit entry ${tracker.id}")
        val before = readout.getUnclippedBoundsInRoot()
        val next = compose.onNodeWithTag("Next habit").getUnclippedBoundsInRoot()
        compose.onNodeWithContentDescription("20 November 2026: $note").performClick()
        compose.onNode(hasText(note) and hasAnyAncestor(hasTestTag("Habit entry ${tracker.id}"))).assertExists()
        val scrolling = compose.onNode(
            hasScrollAction() and hasAnyAncestor(hasTestTag("Habit entry ${tracker.id}")),
        )
        scrolling.performSemanticsAction(SemanticsActions.ScrollBy) { assertTrue(it(0f, 10_000f)) }
        assertTrue(scrolling.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        assertEquals(before, readout.getUnclippedBoundsInRoot())
        assertEquals(next, compose.onNodeWithTag("Next habit").getUnclippedBoundsInRoot())
    }

    @Test(timeout = 120_000)
    fun headerTapShowsThisWeekAndPersistsCollapseWithoutLosingMonthSelectionOrDraft() {
        val month = YearMonth.from(LocalDate.now()).minusMonths(1)
        val date = month.atDay(1)
        val old = TrackerDailyValueEntity(number.id, date.toEpochDay(), 10.0, null, null, "", 1)
        val second = tracker.copy(id = "reading", name = "Reading", position = 1)
        val read = number.copy(id = "read", trackerId = second.id, name = "Read today", valueType = "BOOLEAN", unit = null)
        createDatabase()
        runBlocking {
            database.trackerDao().insertTrackers(listOf(tracker, second))
            database.trackerDao().insertFields(listOf(number, read))
            database.trackerDao().upsertValue(old)
        }
        val vm = lifecycle.own(EditorViewModel(database))
        var showing by mutableStateOf(true)
        compose.setContent {
            HabitFixture { if (showing) TrackerScreen(vm, fixturePreferences) }
        }
        awaitHabit()
        compose.onNodeWithTag("Habit calendar ${tracker.id}").performTouchInput { swipeLeft() }
        val description = "${date.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy"))}: 10 g"
        val cell = compose.onNodeWithContentDescription(description)
        cell.performTouchInput { click() }
        cell.assertIsSelected()
        val image = cell.captureToImage().toPixelMap()
        val edge = image[image.width / 2, 2]
        val background = VibePalettes.Ocean.dark.background
        assertEquals(background.red, edge.red, 1f / 255f)
        assertEquals(background.green, edge.green, 1f / 255f)
        assertEquals(background.blue, edge.blue, 1f / 255f)
        input("g").performTextReplacement("-3")
        compose.onNodeWithText(tracker.name).performClick()
        compose.onNodeWithTag("Habit calendar ${tracker.id}").assertExists()
        compose.onNodeWithContentDescription(description).assertDoesNotExist()
        compose.onNodeWithContentDescription("Previous month for ${tracker.name}").assertDoesNotExist()
        habitWeekCells(LocalDate.now()).forEach { day ->
            val prefix = day.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy"))
            compose.onNode(
                hasContentDescription("$prefix:", substring = true) and
                    hasAnyAncestor(hasTestTag("Habit calendar ${tracker.id}")),
            ).assertExists()
        }
        input("g").assertTextContains("-3")
        captureFixture("habits-collapsed-calendar")
        compose.onNodeWithText(tracker.name).performClick()
        compose.onNodeWithText(month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))).assertExists()
        compose.onNodeWithContentDescription(description).assertIsSelected()
        input("g").assertTextContains("-3")
        assertEquals(listOf(old), runBlocking { database.editorDao().values().first() })
        compose.onNodeWithText(tracker.name).performClick()
        compose.runOnIdle { showing = false }
        compose.waitForIdle()
        compose.runOnIdle { showing = true }
        awaitHabit()
        compose.onNodeWithContentDescription("Previous month for ${tracker.name}").assertDoesNotExist()
        compose.onNodeWithContentDescription("Previous month for ${second.name}").assertExists()
        compose.onNodeWithText(tracker.name).performClick()
        compose.onNodeWithContentDescription("Previous month for ${tracker.name}").assertExists()
        compose.runOnIdle { showing = false }
        compose.waitForIdle()
        compose.runOnIdle { showing = true }
        awaitHabit()
        compose.onNodeWithContentDescription("Previous month for ${tracker.name}").assertExists()
    }

    @Test(timeout = 120_000)
    fun collapsedWeekIncludesPreviousMonthWhileExpandedCalendarContainsOnlyItsMonth() {
        val today = LocalDate.of(2026, 10, 1)
        var expanded by mutableStateOf(false)
        compose.setContent {
            HabitFixture {
                val state = remember { HabitCalendarState(YearMonth.from(today).toString()) }
                HabitMonthHeatmap(tracker, number, emptyList(), today, state, expanded, {})
            }
        }
        val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy")
        val week = habitWeekCells(today)
        week.forEach { day ->
            compose.onNodeWithContentDescription("${day.format(formatter)}: No entry").assertExists()
        }
        compose.onNodeWithContentDescription("2 October 2026: No entry").assertIsNotEnabled()
        captureFixture("habits-week-cross-month")
        compose.runOnIdle { expanded = true }
        week.filter { it.month != today.month }.forEach { day ->
            compose.onNodeWithContentDescription("${day.format(formatter)}: No entry").assertDoesNotExist()
        }
        (1..today.lengthOfMonth()).forEach { day ->
            compose.onNodeWithContentDescription("${today.withDayOfMonth(day).format(formatter)}: No entry").assertExists()
        }
    }

    @Test(timeout = 120_000)
    fun entryLabelsUseUnitsAndKeepNamesForUnitlessMeasurements() {
        createDatabase()
        val count = tracker.copy(id = "count", name = "Repetitions", position = 1)
        val journal = tracker.copy(id = "journal", name = "Journal", position = 2)
        val reading = tracker.copy(id = "reading", name = "Reading", position = 3)
        val mood = tracker.copy(id = "mood", name = "Mood", position = 4)
        val fields = listOf(
            number,
            number.copy(id = "count-value", trackerId = count.id, name = "Count", unit = null),
            number.copy(id = "journal-value", trackerId = journal.id, name = "Journal entry", valueType = "TEXT", unit = null),
            number.copy(id = "reading-value", trackerId = reading.id, name = "Read today", valueType = "BOOLEAN", unit = null),
            number.copy(
                id = "mood-value", trackerId = mood.id, name = "Feelings", valueType = "CHOICE",
                unit = null, choiceOptions = "Okay\nGood",
            ),
        )
        runBlocking {
            database.trackerDao().insertTrackers(listOf(tracker, count, journal, reading, mood))
            database.trackerDao().insertFields(fields)
        }
        val vm = lifecycle.own(EditorViewModel(database))
        compose.setContent { HabitFixture { TrackerScreen(vm, fixturePreferences) } }
        awaitHabit()
        input("g").assertIsDisplayed()
        compose.onNodeWithText("Amount (g)").assertDoesNotExist()
        val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        list.performScrollToKey(count.id)
        input("Count").performScrollTo().assertIsDisplayed()
        list.performScrollToKey(journal.id)
        input("Journal entry").performScrollTo().assertIsDisplayed()
        list.performScrollToKey(reading.id)
        val checkboxLabel = compose.onNodeWithText("Read today").performScrollTo()
        val calendar = compose.onNodeWithTag("Habit calendar ${reading.id}").getUnclippedBoundsInRoot()
        assertEquals(calendar.right.value, checkboxLabel.getUnclippedBoundsInRoot().right.value, 0.5f)
        compose.onNode(hasScrollAction()).performScrollToKey(mood.id)
        button("Feelings: Choose...").performScrollTo().assertIsDisplayed()
        assertTextEndsAtRight(compose.onNodeWithText("Feelings: Choose...", useUnmergedTree = true))
        captureFixture("habits-choice-entry-alignment")
    }

    @Test(timeout = 120_000)
    fun incompleteExitOffersKeepEditingOrDiscardWithoutRedWarning() {
        launch(listOf(number))
        edit()
        button("Amount").performClick()
        input("Measurement name").performTextReplacement("")
        compose.onNodeWithContentDescription("Back").performClick()
        assertExitActionsShareRow("Keep editing", "Discard")
        button("Save").assertDoesNotExist()
        compose.onNodeWithText("Complete the measurement name, goal and choices, or close to discard changes.").assertDoesNotExist()
        captureFixture("habits-incomplete-exit")
        button("Keep editing").performClick()
        input("Measurement name").assertTextContains("")
        compose.onNodeWithContentDescription("Close").performClick()
        button("Discard").performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        assertEquals(number, runBlocking { database.editorDao().fieldById(number.id) })
    }

    @Test(timeout = 120_000)
    fun choicePickerGroupsShadesIntoSeparateThreeColumnRows() {
        val choices = HabitChoiceIntensity.entries.flatMap { intensity ->
            val count = when (intensity) {
                HabitChoiceIntensity.LIGHT -> 4
                HabitChoiceIntensity.MEDIUM -> 2
                HabitChoiceIntensity.DARK -> 1
            }
            (1..count).map { HabitChoiceOption("${intensity.name}$it", "${intensity.name.lowercase()} $it", intensity, it - 1) }
        }
        val field = number.copy(name = "Feelings", valueType = "CHOICE", unit = null, choiceOptionsJson = encodeHabitChoices(choices))
        launch(listOf(field))
        button("Feelings: Choose...").performClick()
        fun bounds(label: String, shade: String) = compose.onNodeWithContentDescription("$label, $shade shade")
            .getUnclippedBoundsInRoot()
        val first = bounds("light 1", "light")
        val third = bounds("light 3", "light")
        val fourth = bounds("light 4", "light")
        val medium = bounds("medium 1", "medium")
        val dark = bounds("dark 1", "dark")
        assertEquals(first.top, third.top)
        assertTrue(kotlin.math.abs((third.right - third.left).value - (first.right - first.left).value) <= 1f)
        assertTrue(third.left > first.left)
        assertEquals(first.left, fourth.left)
        assertEquals(first.left, medium.left)
        assertEquals(first.left, dark.left)
        assertTrue(fourth.top >= first.bottom && medium.top >= fourth.bottom && dark.top >= medium.bottom)
        captureFixture("habits-choice-three-columns")
        compose.onNodeWithContentDescription("medium 2, medium shade").performClick()
        awaitSaved { database.editorDao().values().first().singleOrNull()?.choiceOptionId == "MEDIUM2" }
    }

    @Test(timeout = 120_000)
    fun configureAndRecordChoiceField() {
        val field = number.copy(
            id = "mood", name = "Feeling", valueType = "CHOICE", unit = null,
            choiceOptions = "Sad\nOkay\nHappy", choiceLightThrough = 0, choiceDarkFrom = 2,
        )
        launch(listOf(field))
        edit()
        compose.onNodeWithContentDescription("Change habit icon").performClick()
        compose.onAllNodes(hasContentDescription("Set habit icon ", substring = true)).assertCountEquals(60)
        compose.onNodeWithContentDescription("Set habit icon Health").assertDoesNotExist()
        compose.onNodeWithContentDescription("Set habit icon Cleaning").assertDoesNotExist()
        compose.onNodeWithContentDescription("Set habit icon Mood").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Change habit colour").performClick()
        val colours = compose.onAllNodes(hasContentDescription("Set habit colour ", substring = true))
            .assertCountEquals(36).fetchSemanticsNodes().map { it.boundsInRoot }
        val colourRows = colours.groupBy { it.center.y }
        assertEquals(6, colourRows.size)
        colourRows.values.forEach { row ->
            assertEquals(6, row.size)
            row.sortedBy { it.left }.zipWithNext().forEach { (left, right) ->
                assertTrue("Colour buttons must not overlap", left.right <= right.left)
            }
        }
        compose.onNodeWithContentDescription("Set habit colour Blue").performClick()
        button("Save").performClick()
        awaitSaved {
            database.editorDao().trackers().first().single().let { it.iconName == "mood" && it.colourArgb == 0xFF42A5F5L }
        }
        button("Feeling: Choose...").performClick()
        compose.onNodeWithContentDescription("Sad, light shade").assertExists()
        compose.onNodeWithContentDescription("Okay, medium shade").assertExists()
        compose.onNodeWithContentDescription("Happy, dark shade").performClick()
        awaitSaved { database.editorDao().values().first().singleOrNull()?.textValue == "Happy" }

        edit()
        button("Feeling").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Measurement type (locked)")
            .assertTextContains("Choose from a list").assertHasNoClickAction()
        compose.onNode(hasText("fixed after saving", substring = true)).assertDoesNotExist()
        listOf("light", "medium", "dark").forEach { shade ->
            val band = compose.onNodeWithContentDescription("$shade choice drop target")
                .performScrollTo().getUnclippedBoundsInRoot()
            assertTrue("Occupied shade bands must remain clearly visible", band.bottom - band.top >= 8.dp)
        }
        captureFixture("habits-choice-visible-shade-bands")
        val actions = compose.onNodeWithContentDescription("Happy intensity controls")
            .performScrollTo().fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { assertTrue(actions.first { it.label == "Move to Medium" }.action()) }
        compose.onNodeWithContentDescription("Add choice").performScrollTo().performClick()
        compose.onNodeWithContentDescription("light choice 2").performScrollTo().performTextInput("Great")
        button("Done").performClick()
        button("Save").performClick()
        awaitSaved {
            val options = decodeHabitChoices(database.editorDao().fields().first().single())
            options.any { it.label == "Happy" && it.intensity == HabitChoiceIntensity.MEDIUM } &&
                options.any { it.label == "Great" && it.intensity == HabitChoiceIntensity.LIGHT }
        }
        assertEquals("DARK", runBlocking { database.editorDao().values().first().single().choiceIntensity })
        captureFixture("habits-choice-calendar")
        edit()
        button("Archive habit").performScrollTo().performClick()
        button("Archive").performClick()
        awaitSaved { database.editorDao().trackers().first().single().isArchived }
        button("Restore").performScrollTo().performClick()
        awaitSaved { !database.editorDao().trackers().first().single().isArchived }
    }

    @Test(timeout = 120_000)
    fun measurementNavigationHasOnlyHorizontalRulesAndNoInputFill() {
        var opened = false
        compose.setContent {
            HabitFixture {
                Box(Modifier.width(280.dp).background(Color.Magenta)) {
                    HabitSettingValue("Measurement", "Amount", onClick = { opened = true })
                }
            }
        }
        val navigation = button("Amount")
        val pixels = navigation.captureToImage().toPixelMap()
        listOf(2, pixels.width - 3).forEach { x ->
            val side = pixels[x, pixels.height / 2]
            assertEquals(Color.Magenta.red, side.red, 2f / 255)
            assertEquals(Color.Magenta.green, side.green, 2f / 255)
            assertEquals(Color.Magenta.blue, side.blue, 2f / 255)
        }
        assertNotEquals(Color.Magenta, pixels[pixels.width / 2, 1])
        assertNotEquals(Color.Magenta, pixels[pixels.width / 2, pixels.height - 2])
        navigation.performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test(timeout = 120_000)
    fun goalShadingWithoutAGoalStaysEnabledAndKeepsDialogGeometry() {
        var light by mutableStateOf(false)
        val seed = HabitEditorSeed(tracker, listOf(number), creating = false)
        compose.setContent {
            HabitFixture(if (light) VibePalettes.Ocean.light else VibePalettes.Ocean.dark) {
                HabitEditorDialog(seed, {}) { _, _, _, _ -> error("This test must not save the draft") }
            }
        }
        listOf(false, true).forEach { useLight ->
            compose.runOnIdle { light = useLight }
            val palette = if (useLight) VibePalettes.Ocean.light else VibePalettes.Ocean.dark
            val toggle = compose.onNodeWithContentDescription("Use goal shading").assertIsEnabled().assertIsOff()
            val pixels = toggle.captureToImage().toPixelMap()
            var visibleForeground = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (kotlin.math.abs(color.red - palette.textSecondary.red) <= 2f / 255 &&
                    kotlin.math.abs(color.green - palette.textSecondary.green) <= 2f / 255 &&
                    kotlin.math.abs(color.blue - palette.textSecondary.blue) <= 2f / 255
                ) visibleForeground++
            }
            assertTrue("The off switch must have a clearly visible thumb and outline",
                visibleForeground > pixels.width * pixels.height / 50)
            val modal = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Edit habit"))
            val before = modal.getUnclippedBoundsInRoot()
            val save = button("Save").getUnclippedBoundsInRoot()
            toggle.performClick().assertIsOn().assertIsEnabled()
            compose.onNodeWithText("Relative shading until a goal is set.").assertDoesNotExist()
            assertEquals(before, modal.getUnclippedBoundsInRoot())
            assertEquals(save, button("Save").getUnclippedBoundsInRoot())
            toggle.performClick().assertIsOff().assertIsEnabled()
            assertEquals(before, modal.getUnclippedBoundsInRoot())
        }
    }

    @Test(timeout = 120_000)
    fun modalScrollbarOnlyAppearsForOverflowAndSupportsDraggingInBothDirections() {
        var long by mutableStateOf(false)
        compose.setContent {
            HabitFixture {
                HabitGlassDialog("Scroll review", {}) {
                    repeat(if (long) 40 else 1) { Text("Row $it", Modifier.height(48.dp)) }
                }
            }
        }
        compose.onNodeWithTag("Habit dialog scroll indicator").assertDoesNotExist()
        compose.runOnIdle { long = true }
        val indicator = compose.onNodeWithTag("Habit dialog scroll indicator").assertIsDisplayed()
        val start = indicator.getUnclippedBoundsInRoot()
        val viewport = compose.onNode(hasScrollAction())
        fun scrollPosition() = viewport.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        indicator.performTouchInput {
            swipe(center, center + Offset(0f, 200.dp.toPx()))
        }
        val draggedDown = scrollPosition()
        assertTrue("Grabbing the visible thumb must scroll content down", draggedDown > 0f)
        assertTrue(indicator.getUnclippedBoundsInRoot().top > start.top)
        indicator.performTouchInput {
            swipe(center, center - Offset(0f, 200.dp.toPx()))
        }
        assertTrue("The same thumb must scroll content back up", scrollPosition() < draggedDown)
        compose.onNodeWithText("Row 39").performScrollTo()
        val end = indicator.assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("The thumb must follow scrolling", end.top > start.top)
        assertEquals(
            "The thumb height stays constant within one physical pixel",
            with(compose.density) { (start.bottom - start.top).toPx() },
            with(compose.density) { (end.bottom - end.top).toPx() },
            1f,
        )
        compose.onNodeWithTag("Habit dialog scrollbar")
            .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(0f)) }
        compose.onNodeWithText("Row 0").assertIsDisplayed()
        compose.runOnIdle { long = false }
        compose.onNodeWithTag("Habit dialog scroll indicator").assertDoesNotExist()
    }

    @Test(timeout = 120_000)
    fun measurementDropdownsPreserveDraftsAndShowOnlyRelevantFields() {
        createDatabase()
        val seed = HabitEditorSeed(tracker, listOf(number), creating = true)
        compose.setContent {
            HabitFixture {
                HabitEditorDialog(seed, {}) { _, _, _, _ -> error("This test must not save the draft") }
            }
        }
        button("Amount").performClick()
        input("Measurement name").performTextReplacement("Time practised")
        input("Unit (optional)").performTextReplacement("min")
        chooseGoal("Between two values")
        input("Minimum goal").performTextInput("10")
        input("Maximum goal").performTextInput("30")
        compose.onNodeWithContentDescription("Measurement type").performScrollTo().performClick()
        menuOption("Number").assertIsSelected()
        withCiFailureDiagnostics(compose, "habits-measurement-native-back") {
            androidx.test.espresso.Espresso.pressBack()
            compose.onAllNodes(isPopup()).assertCountEquals(0)
        }
        input("Measurement name").assertTextContains("Time practised")
        input("Unit (optional)").assertTextContains("min")
        input("Minimum goal").assertTextContains("10")
        input("Maximum goal").assertTextContains("30")

        compose.onNodeWithContentDescription("Measurement type").performScrollTo().performClick()
        menuOption("Written note").performClick()
        compose.onNodeWithContentDescription("Unit (optional)").assertDoesNotExist()
        compose.onNodeWithContentDescription("Goal (optional)").assertDoesNotExist()
        input("Measurement name").assertTextContains("Time practised")
        compose.onNodeWithContentDescription("Measurement type").performScrollTo().performClick()
        menuOption("Choose from a list").performClick()
        compose.onNodeWithContentDescription("Add choice").performScrollTo().assertExists()
        compose.onNodeWithContentDescription("light choice 1").performScrollTo().performTextReplacement("Short")
        compose.onNodeWithContentDescription("light choice 2").performScrollTo().performTextReplacement("Long")
        compose.onNodeWithContentDescription("Goal (optional)").assertDoesNotExist()
        compose.onNodeWithContentDescription("Measurement type").performScrollTo().performClick()
        menuOption("Yes or no").performClick()
        compose.onNodeWithContentDescription("Add choice").assertDoesNotExist()
        compose.onNodeWithContentDescription("Unit (optional)").assertDoesNotExist()
        compose.onNodeWithContentDescription("Measurement type").performScrollTo().performClick()
        captureFixture("habits-measurement-type-dropdown")
        menuOption("Choose from a list").performClick()
        compose.onNodeWithContentDescription("light choice 1").assertTextContains("Short")
        compose.onNodeWithContentDescription("light choice 2").assertTextContains("Long")
        compose.onNodeWithContentDescription("Measurement type").performScrollTo().performClick()
        menuOption("Number").performClick()
        input("Unit (optional)").assertTextContains("min")
        input("Minimum goal").assertTextContains("10")
        input("Maximum goal").assertTextContains("30")
        chooseGoal("None")
        compose.onNodeWithContentDescription("Minimum goal").assertDoesNotExist()
        compose.onNodeWithContentDescription("Maximum goal").assertDoesNotExist()
        captureFixture("habits-measurement-number-form")
        button("Done").performClick()
        button("Amount").assertDoesNotExist()
        button("Time practised").performClick()
        input("Unit (optional)").assertTextContains("min")
        chooseGoal("Between two values")
        input("Minimum goal").assertTextEquals("")
        assertTrue(runBlocking { database.editorDao().trackers().first().isEmpty() })
    }

    @Test(timeout = 120_000)
    fun measurementMenusRemainUsableWithLargeTextAndReducedMotionInLightTheme() {
        val seed = HabitEditorSeed(tracker, listOf(number), creating = true)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                HabitFixture(VibePalettes.Ocean.light) {
                    CompositionLocalProvider(
                        LocalVibeReducedMotion provides true,
                        LocalVibeMotion provides VibeMotion.resolve(true),
                    ) {
                        HabitEditorDialog(seed, {}) { _, _, _, _ -> error("This test must not save the draft") }
                    }
                }
            }
        }
        button("Amount").performClick()
        input("Measurement name").performScrollTo().performTextReplacement("Time practised")
        input("Unit (optional)").performScrollTo().performTextReplacement("min")
        compose.onNodeWithContentDescription("Measurement type").performScrollTo().performClick()
        menuOption("Number").performScrollTo().assertIsDisplayed().assertIsSelected()
        captureFixture("habits-measurement-dropdown-light-large-text")
        menuOption("Written note").performScrollTo().performClick()
        input("Measurement name").assertTextContains("Time practised")
        compose.onNodeWithContentDescription("Unit (optional)").assertDoesNotExist()
        compose.onNodeWithContentDescription("Measurement type").performScrollTo().performClick()
        menuOption("Number").performScrollTo().performClick()
        input("Unit (optional)").assertTextContains("min")
        chooseGoal("At least")
        input("Goal value").performScrollTo().performTextInput("25")
        button("Done").performClick()
        button("Save").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        assertExitActionsShareRow("Keep editing", "Discard", "Save")
        captureFixture("habits-exit-light-large-text")
    }

    @Test(timeout = 120_000)
    fun creationUsesFullEditorAndSingleModalDraftUntilAtomicSave() {
        createDatabase()
        val vm = lifecycle.own(EditorViewModel(database))
        compose.setContent { HabitFixture { TrackerScreen(vm, fixturePreferences) } }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Create new habit").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Create new habit").performScrollTo().performClick()
        input("Habit name").performTextInput("Protein")
        button("Save").assertIsNotEnabled()
        button("Add measurement").performClick()
        assertEquals("", input("Measurement name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        button("Done").assertIsNotEnabled()
        val type = compose.onNodeWithContentDescription("Measurement type")
        val typeBounds = type.getUnclippedBoundsInRoot()
        assertTrue(typeBounds.bottom - typeBounds.top >= 48.dp)
        assertTrue(typeBounds.bottom <= input("Measurement name").getUnclippedBoundsInRoot().top)
        type.performClick()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        compose.onAllNodes(isPopup()).assertCountEquals(1)
        listOf("Yes or no", "Number", "Written note", "Choose from a list").forEach {
            menuOption(it).assertTextEquals(it)
        }
        menuOption("Yes or no").assertIsSelected()
        menuOption("Choose from a list").performClick()
        compose.onNodeWithText("e.g. Mood", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("", input("Measurement name").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        type.performClick()
        menuOption("Number").performClick()
        compose.onAllNodes(isPopup()).assertCountEquals(0)
        input("Measurement name").performTextReplacement("Grams")
        input("Unit (optional)").performTextInput("g")
        compose.onNodeWithText("Shown beside your entry, e.g. min, g or km.").assertDoesNotExist()
        compose.onNode(
            hasText("None") and hasAnyAncestor(hasContentDescription("Goal (optional)")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        compose.onNodeWithText("Goal value").assertDoesNotExist()
        captureFixture("habits-goal-none")
        chooseGoal("At least")
        input("Goal value").performTextInput("120")
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        captureFixture("habits-create-numeric")
        button("Done").performClick()
        compose.onNodeWithContentDescription("Use goal shading").performScrollTo().performClick()
        assertTrue(runBlocking { database.editorDao().trackers().first().isEmpty() })
        button("Save").performClick()
        awaitSaved { database.editorDao().trackers().first().singleOrNull()?.name == "Protein" }
        val habit = runBlocking { database.editorDao().trackers().first().single() }
        val field = runBlocking { database.editorDao().fields().first().single() }
        assertEquals("GOAL", habit.heatmapMode)
        assertEquals("NUMBER", field.valueType)
        assertEquals("g", field.unit)
        assertEquals(120.0, field.targetValue)
    }

    @Test(timeout = 120_000)
    fun dirtyExitCanResumeOrDiscardAndExistingTypeAndUnitAreLocked() {
        launch(listOf(number))
        edit()
        input("Habit name").performTextReplacement("Unsaved")
        button("Amount").performClick()
        compose.onNodeWithContentDescription("Measurement type (locked)")
            .assertTextContains("Number").assertHasNoClickAction()
        compose.onNodeWithContentDescription("Unit (locked)")
            .assertTextContains("g").assert(!hasSetTextAction()).assertHasNoClickAction()
        compose.onNode(hasText("fixed after saving", substring = true)).assertDoesNotExist()
        compose.onNodeWithContentDescription("Measurement type").assertDoesNotExist()
        captureFixture("habits-measurement-locked-settings")
        chooseGoal("At least")
        input("Goal value").performTextInput("10")
        button("Done").performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Save changes?").assertExists()
        assertExitActionsShareRow("Keep editing", "Discard", "Save")
        compose.onNodeWithContentDescription("Close").performClick()
        input("Habit name").assertTextContains("Unsaved")
        compose.onNodeWithContentDescription("Close").performClick()
        compose.onNodeWithText("Save changes?").assertExists()
        button("Keep editing").performClick()
        input("Habit name").assertTextContains("Unsaved")
        compose.onNodeWithContentDescription("Close").performClick()
        button("Discard").performClick()
        assertEquals(tracker, runBlocking { database.editorDao().trackers().first().single() })
        assertEquals(number, runBlocking { database.editorDao().fields().first().single() })
    }

    @Test(timeout = 120_000)
    fun numericCalendarNavigationNeverBackdatesEntryAndInvalidInputIsVisible() {
        val month = YearMonth.from(LocalDate.now()).minusMonths(1)
        val history = listOf(3 to 0.0, 10 to 25.0, 17 to 50.0, 24 to 100.0).map { (day, value) ->
            val epoch = month.atDay(day).toEpochDay()
            TrackerDailyValueEntity(number.id, epoch, value, null, null, "", epoch)
        }
        launch(listOf(number), values = history)
        input("g").performTextReplacement("0")
        val today = LocalDate.now().toEpochDay()
        awaitSaved { database.editorDao().values().first().singleOrNull { it.epochDay == today }?.numericValue == 0.0 }
        input("g").performTextReplacement("-3")
        compose.onNodeWithText("Enter a number greater than or equal to zero").assertExists()
        assertEquals(0.0, runBlocking { database.editorDao().values().first().single { it.epochDay == today }.numericValue })
        compose.onNodeWithContentDescription("Previous month for Wellbeing").performScrollTo().performClick()
        input("g").performScrollTo().performTextReplacement("12.5")
        awaitSaved { database.editorDao().values().first().singleOrNull { it.epochDay == today }?.numericValue == 12.5 }
        val saved = runBlocking { database.editorDao().values().first() }
        assertEquals(today, saved.single { it.epochDay !in history.map(TrackerDailyValueEntity::epochDay) }.epochDay)
        assertEquals(history.sortedBy { it.epochDay }, saved.filterNot { it.epochDay == today }.sortedBy { it.epochDay })
        compose.onNodeWithText(
            month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")),
        ).assertExists()
        compose.runOnIdle { dismissFixtureKeyboard() }
        history.forEach { value ->
            val date = LocalDate.ofEpochDay(value.epochDay)
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy"))
            compose.onNodeWithContentDescription("$date: ${requireNotNull(value.numericValue).toInt()} g")
                .performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText(tracker.name).performScrollTo()
        captureFixture("habits-numeric-previous-month")
    }

    @Test(timeout = 120_000)
    fun existingHabitOffersOnlyFirstMeasurementAndPreservesExtraHistory() {
        val extra = number.copy(id = "extra", name = "Repetitions", unit = "reps", position = 1)
        val old = TrackerDailyValueEntity(extra.id, LocalDate.now().minusDays(1).toEpochDay(), 7.0, null, null, "", 1)
        launch(listOf(number, extra), values = listOf(old))
        input("g").assertIsDisplayed()
        compose.onNodeWithText("reps").assertDoesNotExist()
        edit()
        button("Amount").assertExists()
        compose.onNodeWithText("Repetitions").assertDoesNotExist()
        compose.onNodeWithText("Add measurement").assertDoesNotExist()
        compose.onNodeWithText("Heatmap measurement", substring = true).assertDoesNotExist()
        captureFixture("habits-single-measurement-settings")
        button("Save").performClick()
        awaitSaved { database.editorDao().fieldById(extra.id)?.isArchived == true }
        assertEquals(number, runBlocking { database.editorDao().fields().first().single { !it.isArchived } })
        assertEquals(listOf(old), runBlocking { database.editorDao().values().first() })
    }

    @Test(timeout = 120_000)
    fun calendarSwipesRespectRecordedRangeWithoutChangingEntryDateOrHistory() {
        val today = LocalDate.now()
        val current = YearMonth.from(today)
        val first = current.minusMonths(2)
        val history = listOf(first, current.minusMonths(1)).mapIndexed { index, month ->
            TrackerDailyValueEntity(number.id, month.atDay(1).toEpochDay(), index + 10.0, null, null, "", 1)
        }
        createDatabase()
        runBlocking {
            val others = listOf("reading", "walking").mapIndexed { index, id ->
                tracker.copy(id = id, name = id.replaceFirstChar(Char::uppercaseChar), position = index + 1)
            }
            database.trackerDao().insertTrackers(listOf(tracker) + others)
            database.trackerDao().insertFields(listOf(number) + others.map {
                number.copy(id = it.id, trackerId = it.id, name = it.name)
            })
            history.forEach { database.trackerDao().upsertValue(it) }
        }
        val vm = lifecycle.own(EditorViewModel(database))
        compose.setContent { HabitFixture { TrackerScreen(vm, fixturePreferences) } }
        awaitHabit()
        fun assertMonth(month: YearMonth) {
            compose.onNode(
                hasText(month.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy"))) and
                    hasAnyAncestor(hasTestTag("Habit calendar ${tracker.id}")),
            ).assertExists()
        }
        fun swipeBack() = compose.onNodeWithTag("Habit calendar ${tracker.id}")
            .performTouchInput { swipeLeft() }
        fun swipeForward() = compose.onNodeWithTag("Habit calendar ${tracker.id}")
            .performTouchInput { swipeRight() }
        compose.onNodeWithContentDescription("Next month for Wellbeing").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous month for Reading").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next month for Reading").assertIsNotEnabled()
        swipeBack()
        assertMonth(current.minusMonths(1))
        swipeBack()
        assertMonth(first)
        compose.onNodeWithContentDescription("Previous month for Wellbeing").assertIsNotEnabled()
        swipeBack()
        assertMonth(first)
        val firstEntry = "${first.atDay(1).format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy"))}: 10 g"
        compose.onNodeWithContentDescription(firstEntry).performTouchInput { click() }.assertIsSelected()
        compose.onNodeWithTag("Habit entry date ${tracker.id}")
            .assertTextEquals(habitCalendarDate(first.atDay(1))).assertIsDisplayed()
        compose.onNodeWithTag("Habit entry value ${tracker.id}").assertTextEquals("10 g").assertIsDisplayed()
        captureFixture("habits-calendar-swipe-oldest")
        swipeForward()
        assertMonth(current.minusMonths(1))
        swipeForward()
        assertMonth(current)
        swipeForward()
        assertMonth(current)
        val list = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
        val beforeScroll = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        compose.onNodeWithTag("Habit calendar ${tracker.id}").performTouchInput { swipeUp() }
        assertTrue(list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > beforeScroll)
        list.performScrollToNode(hasTestTag("Habit calendar ${tracker.id}"))
        assertMonth(current)
        assertEquals(history.sortedBy { it.epochDay },
            runBlocking { database.editorDao().values().first() }.sortedBy { it.epochDay })
        val entry = compose.onNode(
            hasText("g") and hasSetTextAction() and
                hasAnySibling(hasTestTag("Habit calendar ${tracker.id}")),
        )
        entry.performScrollTo()
        swipeBack()
        entry.performScrollTo().performTextReplacement("12.5")
        awaitSaved {
            database.editorDao().values().first().singleOrNull { it.epochDay == today.toEpochDay() }?.numericValue == 12.5
        }
        assertMonth(current.minusMonths(1))
        assertEquals(history.sortedBy { it.epochDay },
            runBlocking { database.editorDao().values().first() }.filterNot { it.epochDay == today.toEpochDay() }.sortedBy { it.epochDay })
    }

    @Test(timeout = 120_000)
    fun compactHabitsKeepNumericAndBooleanInputsInlineAndTwoCalendarsVisible() {
        createDatabase()
        val water = tracker.copy(id = "water", name = "Water", position = 0)
        val reading = tracker.copy(id = "reading", name = "Read", position = 1)
        val amount = number.copy(trackerId = water.id)
        val done = number.copy(id = "done", trackerId = reading.id, name = "Done", valueType = "BOOLEAN", unit = null)
        val today = LocalDate.now()
        val history = ((today.dayOfMonth - 3).coerceAtLeast(1)..today.dayOfMonth).flatMapIndexed { index, day ->
            val epoch = today.withDayOfMonth(day).toEpochDay()
            listOf(
                TrackerDailyValueEntity(amount.id, epoch, listOf(0.0, 25.0, 50.0, 100.0)[index], null, null, "", epoch),
                TrackerDailyValueEntity(done.id, epoch, null, index % 2 == 0, null, "", epoch),
            )
        }
        runBlocking {
            database.trackerDao().insertTrackers(listOf(water, reading))
            database.trackerDao().insertFields(listOf(amount, done))
            history.forEach { database.trackerDao().upsertValue(it) }
        }
        val vm = lifecycle.own(EditorViewModel(database))
        compose.setContent { HabitFixture(VibePalettes.Forest.dark) { TrackerScreen(vm, fixturePreferences) } }
        awaitHabit(water.name)
        awaitHabit(reading.name)
        val numberBounds = input("g").getUnclippedBoundsInRoot()
        val waterTitle = compose.onNodeWithText("Water").getUnclippedBoundsInRoot()
        val checkboxBounds = compose.onNodeWithContentDescription("Done").getUnclippedBoundsInRoot()
        val readTitle = compose.onNodeWithText("Read").getUnclippedBoundsInRoot()
        assertTrue("Water input must share its title row", (waterTitle.top + waterTitle.bottom) / 2 in numberBounds.top..numberBounds.bottom)
        assertTrue("Read checkbox must share its title row", (readTitle.top + readTitle.bottom) / 2 in checkboxBounds.top..checkboxBounds.bottom)
        val viewport = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
            .getUnclippedBoundsInRoot()
        withCiFailureDiagnostics(
            compose, "habits-two-calendar-viewport",
            mapOf(
                "viewport" to compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)),
                "waterTitle" to compose.onNodeWithText("Water"),
                "waterInput" to input("g"),
                "waterCalendar" to compose.onNodeWithTag("Habit calendar ${water.id}"),
                "readTitle" to compose.onNodeWithText("Read"),
                "readInput" to compose.onNodeWithContentDescription("Done"),
                "readCalendar" to compose.onNodeWithTag("Habit calendar ${reading.id}"),
            ),
        ) {
            listOf(water, reading).forEach { habit ->
                val calendar = compose.onNodeWithTag("Habit calendar ${habit.id}").getUnclippedBoundsInRoot()
                assertTrue("Both complete six-row calendars must fit: $calendar in $viewport",
                    calendar.top >= viewport.top && calendar.bottom <= viewport.bottom)
            }
        }
        val add = compose.onNodeWithContentDescription("Create new habit").getUnclippedBoundsInRoot()
        val lastCalendar = compose.onNodeWithTag("Habit calendar ${reading.id}").getUnclippedBoundsInRoot()
        assertTrue("Add must follow the last habit", add.top >= lastCalendar.bottom)
        (1..today.lengthOfMonth()).forEach { day ->
            val date = today.withDayOfMonth(day)
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy"))
            compose.onAllNodes(SemanticsMatcher("calendar date $date") { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                    .any { it.startsWith("$date:") }
            }).assertCountEquals(2)
                .fetchSemanticsNodes().forEach { node ->
                    val cell = compose.onNode(SemanticsMatcher("calendar day ${node.id}") { it.id == node.id })
                        .getUnclippedBoundsInRoot()
                    assertTrue("Calendar dates must precede the in-list Add button: $cell / $add",
                        cell.bottom <= add.top)
                }
        }
        captureFixture("habits-compact-two-calendars")
        input("g").performTextReplacement("12.5")
        awaitSaved {
            database.editorDao().values().first()
                .singleOrNull { it.fieldId == amount.id && it.epochDay == today.toEpochDay() }?.numericValue == 12.5
        }
        compose.runOnIdle { dismissFixtureKeyboard() }
        val previous = requireNotNull(history.single { it.fieldId == done.id && it.epochDay == today.toEpochDay() }.booleanValue)
        compose.onNodeWithContentDescription("Done").performScrollTo().performClick()
        awaitSaved {
            database.editorDao().values().first()
                .singleOrNull { it.fieldId == done.id && it.epochDay == today.toEpochDay() }?.booleanValue == !previous
        }
        assertEquals(
            history.filterNot { it.epochDay == today.toEpochDay() }.sortedWith(compareBy({ it.fieldId }, { it.epochDay })),
            runBlocking { database.editorDao().values().first() }.filterNot { it.epochDay == today.toEpochDay() }
                .sortedWith(compareBy({ it.fieldId }, { it.epochDay })),
        )
        edit(water.name)
        button("Amount").performClick()
        captureFixture("habits-compact-settings-glass")
    }

    @Test(timeout = 120_000)
    fun longTitlesWrapBesideEntryAndRetainDraftAtLargeFont() {
        createDatabase()
        val habit = tracker.copy(name = "Water")
        runBlocking {
            database.trackerDao().insertTrackers(listOf(habit))
            database.trackerDao().insertFields(listOf(number))
        }
        val vm = lifecycle.own(EditorViewModel(database))
        var constrained by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (constrained) 1.6f else 1f)) {
                HabitFixture(VibePalettes.Forest.dark) {
                    Box(
                        Modifier.fillMaxHeight()
                            .then(if (constrained) Modifier.width(320.dp) else Modifier.fillMaxWidth()),
                    ) { TrackerScreen(vm, fixturePreferences) }
                }
            }
        }
        awaitHabit(habit.name)
        input("g").performTextReplacement("12.5")
        awaitSaved { database.editorDao().values().first().singleOrNull()?.numericValue == 12.5 }
        input("g").performTextReplacement("-3")
        val names = listOf(
            "i".repeat(19),
            "Supercalifragilisticexpialidocious",
            "Daily deliberate practice and reflection",
        )
        names.forEach { name ->
            compose.runOnIdle { vm.save(habit.copy(name = name)) }
            awaitHabit(name)
            listOf(false, true).forEach { narrow ->
                compose.runOnIdle { constrained = narrow }
                input("g").assertTextContains("-3")
                compose.onNodeWithText("Enter a number greater than or equal to zero").assertExists()
                val heading = compose.onNodeWithContentDescription(name)
                val layouts = mutableListOf<TextLayoutResult>()
                heading.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals("Two title lines at narrow=$narrow: $name", 2, layouts.single().lineCount)
                val title = heading.getUnclippedBoundsInRoot()
                val field = input("g").getUnclippedBoundsInRoot()
                val calendar = compose.onNodeWithTag("Habit calendar ${habit.id}").getUnclippedBoundsInRoot()
                assertTrue("Entry must stay to the right: $title / $field", title.right < field.left)
                assertTrue("Entry must share the title row: $title / $field",
                    title.top < field.bottom && field.top < title.bottom)
                assertEquals(calendar.right, field.right)
            }
        }
        assertEquals(12.5, runBlocking { database.editorDao().values().first().single().numericValue })
        captureFixture("habits-compact-long-title-large-font")
        input("g").performTextReplacement("8.5")
        awaitSaved { database.editorDao().values().first().singleOrNull()?.numericValue == 8.5 }
        val title = compose.onNodeWithContentDescription(names.last()).getUnclippedBoundsInRoot()
        val field = input("g").getUnclippedBoundsInRoot()
        assertEquals((title.top + title.bottom).value / 2, (field.top + field.bottom).value / 2, 1f)
        assertEquals(names.last(), runBlocking { database.editorDao().trackers().first().single().name })
        assertEquals(number, runBlocking { database.editorDao().fields().first().single() })
    }

    @Test(timeout = 120_000)
    fun goalPanelAnimatesBothGrowthAndShrinkWithinOneModal() {
        launch(listOf(number))
        edit()
        button("Amount").performClick()
        val modal = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Measurement"))
        compose.onNodeWithContentDescription("Goal (optional)").performScrollTo().performClick()
        val initial = modal.fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.autoAdvance = false
        menuOption("At least").performClick()
        compose.mainClock.advanceTimeBy(64)
        val growing = modal.fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.advanceTimeBy(300)
        val expanded = modal.fetchSemanticsNode().boundsInRoot.height
        assertTrue("Modal must grow smoothly: $initial, $growing, $expanded", growing > initial && growing < expanded)
        compose.onNodeWithContentDescription("Goal (optional)").performScrollTo().performClick()
        compose.mainClock.advanceTimeBy(300)
        menuOption("None").performClick()
        compose.mainClock.advanceTimeBy(64)
        val shrinking = modal.fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.advanceTimeBy(300)
        val collapsed = modal.fetchSemanticsNode().boundsInRoot.height
        assertTrue("Modal must shrink smoothly: $expanded, $shrinking, $collapsed", shrinking < expanded && shrinking > collapsed)
        compose.mainClock.autoAdvance = true
        compose.onNode(
            hasText("None") and hasAnyAncestor(hasContentDescription("Goal (optional)")),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        captureFixture("habits-centered-modal")
    }

    @Test(timeout = 120_000)
    fun failedSaveRetainsDraftUntilSuccessfulRetry() {
        createDatabase()
        runBlocking {
            database.trackerDao().insertTrackers(listOf(tracker))
            database.trackerDao().insertFields(listOf(number))
        }
        val vm = lifecycle.own(EditorViewModel(database))
        val seed = HabitEditorSeed(tracker, listOf(number), false)
        var attempts = 0
        compose.setContent {
            VibeCheckTheme {
                var open by remember { mutableStateOf(true) }
                if (open) HabitEditorDialog(seed, { open = false }) { habit, fields, saved, failed ->
                    if (attempts++ == 0) failed("Fixture save rejected")
                    else vm.saveHabitEditor(habit, fields, false, saved) { failed(vm.error.value.orEmpty()) }
                }
            }
        }
        input("Habit name").performTextReplacement("Edited")
        button("Save").performClick()
        compose.onNodeWithText("Fixture save rejected").assertExists()
        input("Habit name").assertTextContains("Edited")
        assertEquals(tracker, runBlocking { database.editorDao().trackers().first().single() })
        button("Save").performClick()
        awaitSaved { database.editorDao().trackers().first().single().name == "Edited" }
    }

    @Test(timeout = 120_000)
    fun longListAtLargeFontKeepsModalMarginsAndActionsReachable() {
        createDatabase()
        val choices = (1..18).map {
            HabitChoiceOption("choice-$it", "Option $it", HabitChoiceIntensity.LIGHT, it - 1)
        }
        val field = number.copy(
            name = "Feeling", valueType = "CHOICE", unit = null,
            choiceOptions = choices.joinToString("\n") { it.label }, choiceOptionsJson = encodeHabitChoices(choices),
        )
        runBlocking {
            database.trackerDao().insertTrackers(listOf(tracker))
            database.trackerDao().insertFields(listOf(field))
        }
        val vm = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.6f)) {
                HabitFixture(VibePalettes.Forest.dark) {
                    CompositionLocalProvider(
                        LocalVibeReducedMotion provides true,
                        LocalVibeMotion provides VibeMotion.resolve(reducedMotion = true),
                    ) {
                        TrackerScreen(vm, fixturePreferences)
                    }
                }
            }
        }
        awaitHabit()
        edit()
        button("Feeling").performClick()
        compose.onNodeWithContentDescription("Add choice").performScrollTo().performClick()
        compose.onNodeWithContentDescription("light choice 19").performScrollTo().performTextInput("Last option")
        button("Done").assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        val modal = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Measurement"))
            .fetchSemanticsNode().boundsInRoot
        val window = compose.onNode(isDialog()).fetchSemanticsNode().boundsInRoot
        assertTrue("Modal must retain margins: $modal inside $window",
            modal.left > window.left && modal.top > window.top && modal.right < window.right && modal.bottom < window.bottom)
        captureFixture("habits-large-font-list-keyboard")
        assertEquals(tracker, runBlocking { database.editorDao().trackers().first().single() })
        assertEquals(field, runBlocking { database.editorDao().fields().first().single() })
    }

    @Test(timeout = 120_000)
    fun permanentlyDeletesEmptyHabit() {
        launch(emptyList())
        edit()
        button("Archive habit").performScrollTo().performClick()
        button("Archive").performClick()
        awaitSaved { database.editorDao().trackers().first().single().isArchived }
        button("Delete").performScrollTo().performClick()
        button("Delete permanently").performClick()
        awaitSaved { database.editorDao().trackers().first().isEmpty() }
    }

    @Test(timeout = 120_000)
    fun deletingChoicesAcrossBucketsAllowsEmptyGroupsWithoutStaleKeys() {
        val options = listOf(
            HabitChoiceOption("light-only", "Light only", HabitChoiceIntensity.LIGHT, 0),
            HabitChoiceOption("medium-only", "Medium only", HabitChoiceIntensity.MEDIUM, 0),
            HabitChoiceOption("dark-a", "Dark A", HabitChoiceIntensity.DARK, 0),
            HabitChoiceOption("dark-b", "Dark B", HabitChoiceIntensity.DARK, 1),
            HabitChoiceOption("dark-c", "Dark C", HabitChoiceIntensity.DARK, 2),
        )
        val field = number.copy(
            name = "Feeling", valueType = "CHOICE", unit = null,
            choiceOptions = options.joinToString("\n") { it.label }, choiceOptionsJson = encodeHabitChoices(options),
        )
        launch(listOf(field))
        edit()
        button("Feeling").performClick()
        listOf("Light only", "Medium only", "Dark A").forEach {
            compose.onNodeWithContentDescription("Remove $it").performScrollTo().performClick()
        }
        compose.onNodeWithText("Light · 0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Medium · 0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Dark · 2").performScrollTo().assertIsDisplayed()
        captureFixture("habits-list-editor")
        button("Done").performClick()
        button("Save").performClick()
        awaitSaved {
            decodeHabitChoices(database.editorDao().fields().first().single()).map { it.id } == listOf("dark-b", "dark-c")
        }
    }

    private fun assertTextEndsAtRight(node: SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        assertEquals("Text must reach the right of its available space", layout.size.width.toFloat(), layout.getLineRight(0), 1f)
    }

    private fun captureFixture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png")
        file.outputStream().use { assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
    }
}

package com.petermathie.vibecheck

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.petermathie.vibecheck.data.local.BodyMeasurementEntity
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.domain.body.BodyweightUnit
import com.petermathie.vibecheck.ui.*
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestName

@android.annotation.SuppressLint("ViewModelConstructorInComposable")
class MeasurementsUiTest {
    private lateinit var database: VibeDatabase
    private lateinit var context: Context
    private lateinit var fixtureDirectory: File
    private lateinit var preferenceName: String
    private val today = LocalDate.now()
    private val zone = ZoneId.systemDefault()
    private var shownMonth = YearMonth.from(today)
    private val picker = BodyPhotoPicker()
    private val deviceConfiguration = DeviceConfigurationRule(restoreAfterTest = false)
    private val testName = TestName()
    private val lifecycle = ComposeRoomLifecycleRule {
        if (::database.isInitialized) database else null
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(deviceConfiguration).around(testName).around(lifecycle)
    private val compose get() = lifecycle.compose
    private val preferences get() = context.getSharedPreferences("settings", 0)
    private fun input() = compose.onNodeWithContentDescription("Bodyweight for selected date")

    @Before
    fun createFixture() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        val runId = InstrumentationRegistry.getArguments().getString("bodyFixtureRunId")
            ?: java.util.UUID.randomUUID().toString()
        require(runId.matches(Regex("[A-Za-z0-9-]{1,64}"))) { "Invalid Body fixture run ID" }
        preferenceName = "body-fixture-$runId-${testName.methodName}"
        fixtureDirectory = File(application.cacheDir, preferenceName)
        val isolatedPreferences = application.getSharedPreferences(preferenceName, 0)
        context = object : ContextWrapper(application) {
            override fun getFilesDir() = fixtureDirectory
            override fun getSharedPreferences(name: String, mode: Int) = isolatedPreferences
        }
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
    }

    @After
    fun removeFixture() {
        assertTrue(ApplicationProvider.getApplicationContext<Context>().deleteSharedPreferences(preferenceName))
        assertTrue(!fixtureDirectory.exists() || fixtureDirectory.deleteRecursively())
    }

    @Test
    fun selectedTodayAutosavesValidInputWithoutNotesOrEntryDialog() {
        showBody()
        compose.onNodeWithContentDescription("${formatDisplayDate(today)}: No entry").assertIsSelected()
        compose.onNodeWithContentDescription("Add photo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add body entry").assertDoesNotExist()
        compose.onNodeWithText("Notes (optional)").assertDoesNotExist()
        input().performClick().performTextInput("78.126")
        waitForWeight(today, 78.126)
        assertEquals("78.126", inputText())
        val saved = activeRows().single()
        assertEquals("", saved.notes)
        assertEquals("kg", saved.unit)
        assertEquals(today, Instant.ofEpochMilli(saved.recordedAt).atZone(zone).toLocalDate())

        selectDay(today.plusDays(1))
        selectDay(today)
        assertEquals("78.13", inputText())
        assertEquals(listOf(saved), activeRows())

        input().performTextReplacement("not a number")
        compose.onNodeWithText("Enter a finite bodyweight greater than zero").assertIsDisplayed()
        assertEquals(listOf(saved), activeRows())
        input().performTextReplacement("0")
        compose.onNodeWithText("Enter a finite bodyweight greater than zero").assertIsDisplayed()
        assertEquals(listOf(saved), activeRows())
    }

    @Test
    fun changingDaysLoadsStoredOrBlankValuesAndClearRetainsLegacyData() {
        val day = today.minusDays(1)
        val older = measurement("older", day, 8, 77.0, notes = "First legacy note")
        val latest = measurement("latest", day, 20, 78.126, notes = "Second legacy note")
        val untouched = measurement("today", today, 12, 80.0)
        val otherMetric = measurement("waist", day, 12, 90.0).copy(metric = "Waist", unit = "cm")
        seed(older, latest, untouched, otherMetric)
        val note = File(fixtureDirectory, "body-notes/${day.toEpochDay()}.txt").apply {
            parentFile!!.mkdirs()
            writeText("Standalone legacy note")
        }
        val photo = File(fixtureDirectory, "progress-photos/123.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
        showBody()
        assertEquals("80.00", inputText())
        selectDay(day)
        assertEquals("78.13", inputText())
        assertEquals(4, activeRows().size)
        input().performTextClearance()
        compose.waitUntil(15_000) { activeRows().none { it.id in setOf("older", "latest") } }
        val retained = dayRows(day)
        assertEquals(setOf(older.copy(isWeightCleared = true), latest.copy(isWeightCleared = true)), retained.toSet())
        assertEquals(setOf(untouched, otherMetric), activeRows().toSet())
        assertEquals("Standalone legacy note", note.readText())
        assertArrayEquals(byteArrayOf(1, 2, 3), photo.readBytes())

        input().performTextInput("81.125")
        waitForWeight(day, 81.125)
        assertEquals(latest.copy(value = 81.125), activeRows().single { it.id == "latest" })
        assertEquals(older.copy(isWeightCleared = true), dayRows(day).single { it.id == "older" })
        selectDay(today.plusDays(1))
        assertEquals("", inputText())
        assertEquals(3, activeRows().size)
    }

    @Test
    fun settingsConvertInputCalendarAndGraphWithoutRewritingHistory() {
        val kilograms = measurement("kg", today, 12, 80.0, notes = "Preserved")
        val pounds = measurement("lb", today.minusDays(1), 12, 176.369809744).copy(unit = "lb")
        seed(kilograms, pounds)
        showBody()
        repeat(3) {
            preferences.edit().putBoolean("lb", true).commit()
            compose.waitUntil(15_000) { inputText() == "176.37" }
            compose.onNodeWithContentDescription("${formatDisplayDate(today)}: 176.37 lb").assertExists()
            compose.onNode(hasText("176.37 lb") and hasAnyAncestor(hasContentDescription("Graph readout")))
                .performScrollTo().assertIsDisplayed()
            preferences.edit().putBoolean("lb", false).commit()
            compose.waitUntil(15_000) { inputText() == "80.00" }
        }
        assertEquals(setOf(kilograms, pounds), activeRows().toSet())

        preferences.edit().putBoolean("lb", true).commit()
        compose.waitUntil(15_000) { inputText() == "176.37" }
        input().performScrollTo().performTextReplacement("180")
        waitForWeight(today, 180.0 / 2.2046226218)
        val edited = activeRows().single { it.id == "kg" }
        assertEquals(kilograms.copy(value = 180.0 / 2.2046226218), edited)
        assertEquals(pounds, activeRows().single { it.id == "lb" })

        preferences.edit().putBoolean("lb", false).commit()
        compose.waitUntil(15_000) { inputText() == "81.65" }
        assertEquals(edited, activeRows().single { it.id == "kg" })
        selectDay(today.minusDays(1))
        assertEquals("80.00", inputText())
        compose.onNodeWithContentDescription("${formatDisplayDate(today.minusDays(1))}: 80.00 kg").assertIsSelected()
        input().performTextReplacement("82")
        waitForWeight(today.minusDays(1), 82.0 * 2.2046226218)
        assertEquals(pounds.copy(value = 82.0 * 2.2046226218), activeRows().single { it.id == "lb" })
    }

    @Test
    fun trendRangesReuseProgressDropdownAndCustomDatesWithoutChangingTheCalendarWriteDay() {
        val monthStart = today.minusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val recentDay = today.minusDays(2)
        val customDay = today.minusMonths(2)
        val recent = measurement("range-recent", recentDay, 12, 80.0)
        val original = listOf(
            measurement("range-outside-year", today.minusYears(2), 12, 70.0),
            measurement("range-year", today.minusMonths(10), 12, 72.0),
            measurement("range-six", today.minusMonths(5), 12, 74.0),
            measurement("range-three", customDay, 12, 76.0),
            measurement("range-before-month", today.minusMonths(1), 0, 77.0).copy(recordedAt = monthStart - 1),
            measurement("range-month-start", today.minusMonths(1), 0, 78.0),
            recent,
            measurement("range-today-end", today, 12, 81.0).copy(
                recordedAt = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1,
            ),
            measurement("range-future", today.plusDays(1), 0, 90.0),
        )
        seed(*original.toTypedArray())
        showBody()

        fun assertTrend(minimum: String, maximum: String): SemanticsNodeInteraction =
            compose.onNodeWithContentDescription("$minimum to $maximum kg", substring = true)
                .performScrollTo().assertIsDisplayed()

        assertTrendRange("1 month")
        assertTrend("78.00", "81.00")
        listOf("3 months" to "76.00", "6 months" to "74.00", "1 year" to "72.00").forEach { (range, minimum) ->
            chooseTrendRange(range)
            assertTrendRange(range)
            assertTrend(minimum, "81.00")
        }
        chooseTrendRange("1 month")
        val graph = assertTrend("78.00", "81.00")
        val previous = graph.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            .first { it.label == "Previous data point" }
        compose.runOnIdle { assertTrue(previous.action()) }
        input().performScrollTo()
        assertEquals("80.00", inputText())
        compose.onNodeWithContentDescription("${formatDisplayDate(recentDay)}: 80.00 kg").assertIsSelected()

        chooseTrendRange("Custom")
        chooseTrendDate("Beginning", customDay)
        chooseTrendDate("End", customDay)
        compose.onNodeWithText("Apply").performClick()
        assertTrendRange("Custom")
        assertTrend("76.00", "76.00")
        input().performScrollTo()
        assertEquals("80.00", inputText())

        chooseTrendRange("Custom")
        chooseTrendDate("Beginning", customDay.plusDays(1))
        compose.onNodeWithText("End must be on or after Beginning.").assertIsDisplayed()
        compose.onNodeWithText("Apply").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        assertTrendRange("Custom")
        assertTrend("76.00", "76.00")
        assertEquals(original.toSet(), activeRows().toSet())

        input().performScrollTo().performTextReplacement("82")
        waitForWeight(recentDay, 82.0)
        assertEquals(original.map { if (it.id == recent.id) it.copy(value = 82.0) else it }.toSet(), activeRows().toSet())
        assertTrend("76.00", "76.00")
    }

    @Test
    fun poundsInputOnAnEmptyFutureDateCreatesCanonicalKilograms() {
        preferences.edit().putBoolean("lb", true).commit()
        showBody()
        val future = today.plusDays(1)
        selectDay(future)
        input().performTextInput("176.369809744")
        waitForWeight(future, 80.0)
        assertEquals("kg", activeRows().single().unit)
        preferences.edit().putBoolean("lb", false).commit()
        compose.waitUntil(15_000) { inputText() == "80.00" }
        compose.onNodeWithContentDescription("${formatDisplayDate(future)}: 80.00 kg").assertIsSelected()
    }

    @Test
    fun rapidClearReentryAndDifferentDayWritesKeepTheirCapturedDatesAndUnits() {
        val viewModel = lifecycle.own(EditorViewModel(database))
        val otherDay = today.minusDays(1)
        val jobs = mutableListOf<Job>()
        compose.runOnIdle {
            jobs += viewModel.setBodyweight(today, 70.0, BodyweightUnit.KILOGRAMS)
            jobs += viewModel.setBodyweight(today, null, BodyweightUnit.KILOGRAMS)
            jobs += viewModel.setBodyweight(otherDay, 90.0, BodyweightUnit.KILOGRAMS)
            jobs += viewModel.setBodyweight(today, 176.369809744, BodyweightUnit.POUNDS)
        }
        runBlocking { jobs.joinAll() }
        assertEquals(1, dayRows(today).size)
        assertEquals(80.0, dayRows(today).single().value, 1e-10)
        assertEquals(90.0, dayRows(otherDay).single().value, 0.0)
        assertTrue(activeRows().all { !it.isWeightCleared })
    }

    @Test
    fun localDayClearUsesDstBoundariesAndDoesNotTouchAdjacentDays() {
        val dstZone = ZoneId.of("America/Toronto")
        val viewModel = lifecycle.own(EditorViewModel(database))
        listOf(LocalDate.of(2026, 3, 8) to 23, LocalDate.of(2026, 11, 1) to 25).forEach { (day, hours) ->
            val start = day.atStartOfDay(dstZone).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(dstZone).toInstant().toEpochMilli()
            assertEquals(hours * 60 * 60 * 1000L, end - start)
            seed(
                BodyMeasurementEntity("$day-before", start - 1, "Bodyweight", 70.0, "kg", "", false),
                BodyMeasurementEntity("$day-first", start, "Bodyweight", 71.0, "kg", "First", false),
                BodyMeasurementEntity("$day-last", end - 1, "Bodyweight", 72.0, "kg", "Last", false),
                BodyMeasurementEntity("$day-after", end, "Bodyweight", 73.0, "kg", "", false),
            )
            runBlocking { viewModel.setBodyweight(day, null, BodyweightUnit.KILOGRAMS, dstZone).join() }
            assertFalse(activeRows().any { it.id in setOf("$day-first", "$day-last") })
            assertTrue(activeRows().any { it.id == "$day-before" })
            assertTrue(activeRows().any { it.id == "$day-after" })
            assertTrue(runBlocking { database.editorDao().bodyweightsForDay(start, end) }.all { it.isWeightCleared })
        }
    }

    @Test
    fun unsupportedImportedUnitsShowAnErrorAndFailedEditsPreserveTheRow() {
        val original = measurement("unsupported", today, 12, 12.0).copy(unit = "stone")
        seed(original)
        val viewModel = showBody()
        compose.onAllNodesWithText("Cannot convert bodyweight unit \"stone\"; expected kg or lb", substring = true)
            .onFirst().assertExists()
        runBlocking { viewModel.setBodyweight(today, 80.0, BodyweightUnit.KILOGRAMS).join() }
        assertTrue(viewModel.error.value.orEmpty().contains("Cannot convert"))
        assertEquals(listOf(original), activeRows())
        input().performTextClearance()
        compose.waitUntil(15_000) { activeRows().isEmpty() }
        assertEquals(original.copy(isWeightCleared = true), dayRows(today).single())
    }

    @Test
    fun compactHeaderRemainsSideBySideAtNarrowWidthAndLargeType() {
        showBody(largeType = true)
        val heading = compose.onNodeWithText("Bodyweight", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val entry = input().fetchSemanticsNode().boundsInRoot
        assertTrue("heading=$heading entry=$entry", heading.right <= entry.left + 0.5f)
        compose.onNodeWithContentDescription("Next month for Bodyweight").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Previous month for Bodyweight").assertIsEnabled()
        compose.onNodeWithContentDescription("Add photo").assertIsDisplayed()
        compose.onNodeWithText("Notes (optional)").assertDoesNotExist()
        chooseTrendRange("6 months")
        assertTrendRange("6 months")
    }

    @Test
    fun sharedCalendarStillDisablesFutureDatesAndBoundsNavigationByDefault() {
        compose.setContent {
            VibeCheckTheme {
                Column {
                    CalendarMonthHeader(shownMonth, shownMonth, shownMonth, "fixture") {}
                    CalendarHeatmapGrid(
                        listOf(today, today.plusDays(1)), today, null,
                        colorForDay = { Color.Gray },
                        descriptionForDay = { it.toString() },
                        onDayClick = {},
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Previous month for fixture").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next month for fixture").assertIsNotEnabled()
        compose.onNodeWithContentDescription(today.plusDays(1).toString()).assertIsNotEnabled()
    }

    @Test
    fun photoPickerUsesCapturedDayWithoutCreatingAWeightOrNote() {
        showBody()
        val photoDay = today.minusDays(1)
        selectDay(photoDay)
        compose.onNodeWithContentDescription("Add photo").performClick()
        assertArrayEquals(arrayOf("image/*"), picker.types)
        selectDay(today)
        val source = File(fixtureDirectory, "synthetic.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        compose.runOnIdle { picker.finish(Uri.fromFile(source)) }
        compose.waitUntil(15_000) { File(fixtureDirectory, "progress-photos").listFiles().orEmpty().any { it.extension == "jpg" } }
        val saved = File(fixtureDirectory, "progress-photos").listFiles().orEmpty().single()
        assertEquals(photoDay, Instant.ofEpochMilli(saved.nameWithoutExtension.toLong()).atZone(zone).toLocalDate())
        assertArrayEquals(source.readBytes(), saved.readBytes())
        assertTrue(activeRows().isEmpty())
        assertFalse(File(fixtureDirectory, "body-notes").exists())
    }

    @Test
    fun cancelledPhotoPickerDoesNotReportSuccessOrWriteFiles() {
        showBody()
        compose.onNodeWithContentDescription("Add photo").performClick()
        compose.runOnIdle { picker.finish(null) }
        compose.onNodeWithText("Photo added").assertDoesNotExist()
        assertFalse(File(fixtureDirectory, "progress-photos").exists())
        assertTrue(activeRows().isEmpty())
    }

    @Test
    fun failedPhotoPickerCopyReportsErrorAndRemovesItsPartialFile() {
        val viewModel = showBody()
        compose.onNodeWithContentDescription("Add photo").performClick()
        compose.runOnIdle { picker.finish(Uri.fromFile(File(fixtureDirectory, "missing.jpg"))) }
        compose.waitUntil(15_000) {
            viewModel.error.value?.startsWith("Could not add photo:") == true
        }
        compose.onNodeWithText("Photo added").assertDoesNotExist()
        assertTrue(File(fixtureDirectory, "progress-photos").listFiles().orEmpty().isEmpty())
        assertTrue(activeRows().isEmpty())
    }

    private fun showBody(largeType: Boolean = false): EditorViewModel {
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides context,
                LocalActivityResultRegistryOwner provides picker,
                LocalDensity provides Density(density.density, if (largeType) 2f else 1f),
            ) {
                VibeCheckTheme {
                    Box(Modifier.size(if (largeType) 320.dp else 411.dp, 780.dp)) {
                        BodyWithFab(viewModel)
                    }
                }
            }
        }
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Bodyweight for selected date").fetchSemanticsNodes().isNotEmpty()
        }
        return viewModel
    }

    private fun inputText() = input().fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun chooseTrendRange(label: String) {
        compose.onNodeWithContentDescription("Bodyweight date range").performScrollTo().performClick()
        compose.onNode(hasText(label) and hasAnyAncestor(isPopup())).performClick()
    }

    private fun assertTrendRange(label: String) {
        compose.onNodeWithContentDescription("Bodyweight date range").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, label))
    }

    private fun chooseTrendDate(field: String, date: LocalDate) {
        compose.onNodeWithContentDescription("Choose $field date").performClick()
        val inCalendar = hasAnyAncestor(hasContentDescription("$field calendar"))
        compose.onNode(hasContentDescription("year", substring = true) and hasClickAction() and inCalendar).performClick()
        compose.onNode(
            hasText("Navigate to year ${date.year}") and hasClickAction() and inCalendar,
        ).performClick()
        compose.onNodeWithText("Choose month").performClick()
        compose.onNodeWithText(date.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())).performClick()
        val bounds = compose.onNodeWithContentDescription("$field calendar").fetchSemanticsNode().boundsInWindow
        val fullDate = date.format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL))
        compose.onAllNodes(hasText(fullDate, substring = true) and hasClickAction() and inCalendar)
            .filterToOne(SemanticsMatcher("Visible date") {
                it.boundsInWindow.width > 0 && it.boundsInWindow.height > 0 && bounds.contains(it.boundsInWindow.center)
            }).performClick()
        compose.onNodeWithText("Selected: ${formatDisplayDate(date)}").assertExists()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Custom date range").assertExists()
    }

    private fun selectDay(day: LocalDate) {
        val targetMonth = YearMonth.from(day)
        while (shownMonth != targetMonth) {
            val next = shownMonth < targetMonth
            compose.onNodeWithContentDescription("${if (next) "Next" else "Previous"} month for Bodyweight")
                .performScrollTo().performClick()
            shownMonth = shownMonth.plusMonths(if (next) 1 else -1)
        }
        compose.onNodeWithContentDescription("${formatDisplayDate(day)}:", substring = true)
            .performScrollTo().performClick()
        compose.waitForIdle()
    }

    private fun measurement(id: String, day: LocalDate, hour: Int, value: Double, notes: String = "") =
        BodyMeasurementEntity(id, day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(), "Bodyweight", value, "kg", notes, false)

    private fun seed(vararg rows: BodyMeasurementEntity) = runBlocking {
        rows.forEach { database.editorDao().measurement(it) }
    }

    private fun activeRows() = runBlocking { database.editorDao().measurements().first() }

    private fun dayRows(day: LocalDate) = runBlocking {
        database.editorDao().bodyweightsForDay(
            day.atStartOfDay(zone).toInstant().toEpochMilli(),
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }

    private fun waitForWeight(day: LocalDate, value: Double) {
        compose.waitUntil(15_000) {
            dayRows(day).singleOrNull { !it.isWeightCleared }?.value?.let { kotlin.math.abs(it - value) < 1e-9 } == true
        }
    }
}

private class BodyPhotoPicker : ActivityResultRegistry(), ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry get() = this
    private var request: Int? = null
    var types: Array<String>? = null

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        request = requestCode
        types = (input as? Array<*>)?.map { requireNotNull(it as? String) }?.toTypedArray()
    }

    fun finish(uri: Uri?) {
        dispatchResult(checkNotNull(request), uri)
        request = null
    }
}

@androidx.compose.runtime.Composable
private fun BodyWithFab(viewModel: EditorViewModel) {
    val host = remember { AppFabHostState(Destination.MEASUREMENTS) }
    CompositionLocalProvider(
        LocalAppFabHost provides host,
        LocalAppFabClearance provides 88.dp,
    ) {
        MeasurementsScreen(viewModel)
        host.action?.takeIf { it.visible }?.let {
            Box(Modifier.fillMaxSize()) {
                AppFloatingAction(it, host, Modifier.align(Alignment.BottomEnd).padding(16.dp))
            }
        }
    }
}

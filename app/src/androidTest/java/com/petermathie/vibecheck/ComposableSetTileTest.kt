package com.petermathie.vibecheck

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.petermathie.vibecheck.data.local.BandEntity
import com.petermathie.vibecheck.domain.programme.*
import com.petermathie.vibecheck.domain.workout.encodeTimingInputs
import com.petermathie.vibecheck.ui.*
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import com.petermathie.vibecheck.ui.theme.VibePalettes
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
class ComposableSetTileTest {
    @get:Rule val compose = createComposeRule()

    private fun assertSameSize(description: String, expected: DpRect, actual: DpRect) {
        assertEquals("$description: width", (expected.right - expected.left).value,
            (actual.right - actual.left).value, 0.01f)
        assertEquals("$description: height", (expected.bottom - expected.top).value,
            (actual.bottom - actual.top).value, 0.01f)
    }

    @Test fun allTwelveCombinationsFitAtOrdinarySize() = exerciseMatrix(411, 1f)
    @Test fun allTwelveCombinationsFitAtNarrowSize() = exerciseMatrix(320, 1f)
    @Test fun allTwelveCombinationsRemainUsableAtDoubleFontScale() = exerciseMatrix(320, 2f)

    @Test
    fun typingCentresTheWholeValueAndUnitAsTheyGrow() {
        var value by mutableStateOf("")
        val palette = VibePalettes.Ocean.dark
        compose.setContent {
            VibeCheckTheme(palette) {
                Surface {
                    Column {
                    MetricValue(value, "", "Centred typing", false, true, width = 60, unit = "kg", onChange = { value = it })
                    MetricValue(value, "", "Centred reference", true, true, width = 60, unit = "kg", onChange = {})
                    }
                }
            }
        }
        val field = compose.onNodeWithContentDescription("Centred typing")
        val evidence = StringBuilder()
        val emptyField = field.getUnclippedBoundsInRoot()
        val emptyUnit = compose.onNode(hasText("kg") and hasAnyAncestor(hasContentDescription("Centred typing")),
            useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("Empty field labels are centred",
            (emptyField.left + emptyField.right).value / 2, (emptyUnit.left + emptyUnit.right).value / 2, 0.5f)
        field.performClick()
        var previousLeft: Int? = null
        var previousRight: Int? = null
        listOf("1", "2", "5").forEach { digit ->
            field.performTextInput(digit)
            val bounds = field.getUnclippedBoundsInRoot()
            val unit = compose.onNode(
                hasText("kg") and hasAnyAncestor(hasContentDescription("Centred typing")), useUnmergedTree = true,
            ).getUnclippedBoundsInRoot()
            val image = field.captureToImage()
            val pixels = image.toPixelMap()
            val inset = with(compose.density) { 4.dp.roundToPx() }
            val end = with(compose.density) { (unit.left - bounds.left - 2.dp).roundToPx() }
            val ink = (inset until end).filter { x ->
                (0 until pixels.height).any { y ->
                    val pixel = pixels[x, y]
                    kotlin.math.abs(pixel.red - palette.textPrimary.red) < 0.02f &&
                        kotlin.math.abs(pixel.green - palette.textPrimary.green) < 0.02f &&
                        kotlin.math.abs(pixel.blue - palette.textPrimary.blue) < 0.02f
                }
            }
            assertTrue("The actual typed numeral is visible", ink.isNotEmpty())
            val unitRight = with(compose.density) { (unit.right - bounds.left).roundToPx() }
            val layouts = mutableListOf<TextLayoutResult>()
            field.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
                it(layouts)
            }
            val numberWidth = layouts.single().let { it.getLineRight(0) - it.getLineLeft(0) } +
                with(compose.density) { 2.dp.roundToPx() }
            val numberLeft = with(compose.density) { (unit.left - bounds.left - 2.dp).roundToPx() } - numberWidth
            val expected = pixels.width / 2f
            // Text advances include the intentional side bearings around tabular numerals.
            val actual = (numberLeft + unitRight) / 2f
            evidence.appendLine("$value: field centre=$expected, value/unit centre=$actual, number=$numberLeft..${numberLeft + numberWidth}, ink=${ink.first()}..${ink.last()}, unitRight=$unitRight")
            java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "centred-typing-geometry.txt")
                .writeText(evidence.toString())
            assertEquals("Typed $value and its unit grow about the field centre, not its right edge",
                expected, actual, with(compose.density) { 1.dp.toPx() })
            previousLeft?.let { assertTrue("Extra digits extend left", ink.first() < it) }
            previousRight?.let { assertTrue("The unit moves right as the centred group grows", unitRight > it) }
            previousLeft = ink.first()
            previousRight = unitRight
            val reference = compose.onNodeWithContentDescription("Centred reference")
            val referenceBounds = reference.getUnclippedBoundsInRoot()
            val referenceUnit = compose.onNode(hasText("kg") and hasAnyAncestor(hasContentDescription("Centred reference")),
                useUnmergedTree = true).getUnclippedBoundsInRoot()
            val referencePixels = reference.captureToImage().toPixelMap()
            val referenceEnd = with(compose.density) { (referenceUnit.left - referenceBounds.left - 2.dp).roundToPx() }
            val referenceInk = (inset until referenceEnd).filter { x ->
                (0 until referencePixels.height).any { y ->
                    val pixel = referencePixels[x, y]
                    kotlin.math.abs(pixel.red - palette.textPrimary.red) < 0.02f &&
                        kotlin.math.abs(pixel.green - palette.textPrimary.green) < 0.02f &&
                        kotlin.math.abs(pixel.blue - palette.textPrimary.blue) < 0.02f
                }
            }
            assertTrue("Reference digits are visible", referenceInk.isNotEmpty())
            assertEquals("Every typed digit remains visible, not scrolled out of its growing viewport",
                (referenceInk.last() - referenceInk.first()).toFloat(), (ink.last() - ink.first()).toFloat(), 2f)
            java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "centred-entry-$value.png")
                .outputStream().use { assertTrue(image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
        java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "centred-typing-geometry.txt")
            .writeText(evidence.toString())
    }

    @Test
    fun completionPreservesTheBandGridAndSelectedColours() {
        var locked by mutableStateOf(false)
        val definitions = listOf(
            BandEntity("yellow", "Yellow", 1.0, 0xFFFFDD44L),
            BandEntity("red", "Red", 2.0, 0xFFEE4444L),
        ) + List(6) { BandEntity("extra-$it", "Extra $it", it + 3.0, 0xFF5085D6L) }
        val selected = listOf("yellow", "extra-5")
        compose.setContent {
            VibeCheckTheme {
                ResistanceRenderer(
                    ResistanceMetric.BANDS, ExerciseInputConfig().withResistance(ResistanceMetric.BANDS),
                    emptyEntryDraft("fixture", 1), "", "kg", definitions, selected, locked, true, "Band fixture",
                    onWeightChange = {}, onBandsChange = { fail("Locked bands must not change") },
                )
            }
        }
        val grid = compose.onNodeWithContentDescription("Resistance grid for Band fixture")
        val before = grid.getUnclippedBoundsInRoot()
        selected.forEach { id ->
            val band = definitions.single { it.id == id }
            val pixels = compose.onNodeWithContentDescription("${band.name} band for Band fixture")
                .captureToImage().toPixelMap()
            assertEquals(Color(band.colourArgb), pixels[pixels.width / 2, pixels.height / 2])
        }
        compose.runOnIdle { locked = true }
        selected.forEach { id ->
            val band = definitions.single { it.id == id }
            val dot = compose.onNodeWithContentDescription("${band.name} band for Band fixture")
            dot.assertIsNotEnabled().assertIsSelected()
            val pixels = dot.captureToImage().toPixelMap()
            assertEquals("Locked ${band.name} keeps its actual colour",
                Color(band.colourArgb), pixels[pixels.width / 2, pixels.height / 2])
        }
        compose.onNodeWithContentDescription("Red band for Band fixture").assertDoesNotExist()
        assertSameSize("Completion must not collapse the band grid", before, grid.getUnclippedBoundsInRoot())
    }

    @Test
    fun numericStatesStayDistinctAndReadableWithoutMovingFieldsOrUnits() {
        var palette by mutableStateOf(VibePalettes.Ocean.dark)
        lateinit var focusManager: androidx.compose.ui.focus.FocusManager
        val states = listOf("Empty", "Populated", "Focused", "Completed")
        compose.setContent {
            VibeCheckTheme(palette) {
                focusManager = LocalFocusManager.current
                Surface(
                    Modifier.width(320.dp).semantics { contentDescription = "Numeric state fixture" },
                    color = palette.surface,
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Workout field states")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            states.forEach { state ->
                                Column {
                                    Text(state, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                                    MetricValue(
                                        if (state == "Empty") "" else "8.5", "", "$state field",
                                        locked = state == "Completed", enabled = true, unit = "RPE",
                                        secondary = true, onChange = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        fun contrast(a: Color, b: Color): Float {
            val first = a.compositeOver(b).luminance()
            val second = b.luminance()
            return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
        }
        fun background(state: String): Color {
            val pixels = compose.onNodeWithContentDescription("$state field").captureToImage().toPixelMap()
            return pixels[with(compose.density) { 4.dp.roundToPx() }, pixels.height / 2]
        }
        val evidence = StringBuilder()
        VibePalettes.presets.flatMap { listOf(it.light, it.dark) }.forEach { theme ->
            compose.runOnIdle { focusManager.clearFocus(); palette = theme }
            val before = states.associateWith {
                compose.onNodeWithContentDescription("$it field").getUnclippedBoundsInRoot()
            }
            val unitBefore = compose.onNode(
                hasText("RPE") and hasAnyAncestor(hasContentDescription("Focused field")), useUnmergedTree = true,
            ).getUnclippedBoundsInRoot()
            assertNotEquals("Empty and populated fields have distinct fills", background("Empty"), background("Populated"))
            assertNotEquals("Completed values have no editable-field fill", background("Populated"), background("Completed"))
            compose.onNodeWithContentDescription("Completed field").assert(hasSetTextAction().not())
            compose.onNodeWithContentDescription("Focused field").performClick().assertIsFocused()
            states.forEach { state ->
                val field = compose.onNodeWithContentDescription("$state field")
                val bounds = field.getUnclippedBoundsInRoot()
                assertEquals("Focus never changes field placement", before.getValue(state), bounds)
                assertEquals("Existing compact height is preserved", 24f, (bounds.bottom - bounds.top).value, 0.5f)
                val unit = compose.onNode(
                    hasText("RPE") and hasAnyAncestor(hasContentDescription("$state field")), useUnmergedTree = true,
                )
                val layouts = mutableListOf<TextLayoutResult>()
                unit.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val ratio = contrast(layouts.single().layoutInput.style.color, background(state))
                assertTrue("${theme.id}/${theme.isDark}: $state unit contrast $ratio", ratio >= 4.5f)
                evidence.appendLine("${theme.id}/${theme.isDark} $state: $bounds; unit contrast=$ratio")
            }
            val focusedPixels = compose.onNodeWithContentDescription("Focused field").captureToImage().toPixelMap()
            val ringContrast = contrast(focusedPixels[0, focusedPixels.height / 2], theme.surface)
            assertTrue("Focus has a visible outline, not only a cursor: $ringContrast", ringContrast >= 3f)
            assertEquals("Focus does not move the trailing unit", unitBefore, compose.onNode(
                hasText("RPE") and hasAnyAncestor(hasContentDescription("Focused field")), useUnmergedTree = true,
            ).getUnclippedBoundsInRoot())
            val image = compose.onNodeWithContentDescription("Numeric state fixture").captureToImage().asAndroidBitmap()
            java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "typography-states-${theme.id}-${if (theme.isDark) "dark" else "light"}.png")
                .outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            evidence.appendLine("Focus ring contrast=$ringContrast")
        }
        compose.runOnIdle { focusManager.clearFocus() }
        java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "typography-states-geometry.txt")
            .writeText(evidence.toString())
    }

    @Test
    fun automaticTimingFieldsGrowAfterValidZeroButNotTransientOrInvalidInput() {
        var draft by mutableStateOf(emptyEntryDraft("fixture", 1).copy(
            tutEntries = encodeTimingInputs(List(8) { "" }),
        ))
        compose.setContent {
            VibeCheckTheme {
                Box(Modifier.width(320.dp)) {
                    MeasurementRenderer(
                        MeasurementMetric.SKILL_HOLD,
                        ExerciseInputConfig().withMeasurement(MeasurementMetric.SKILL_HOLD),
                        draft, "", attemptsFor(draft), false, true, "Fixture set 1", Modifier,
                        onRepsChange = {}, onDraftChange = { draft = it },
                    )
                }
            }
        }
        val first = compose.onNodeWithContentDescription("TUT attempt 1 for Fixture set 1")
        compose.onNodeWithContentDescription("TUT attempt 2 for Fixture set 1").assertDoesNotExist()
        first.performTextInput(".")
        compose.onNodeWithContentDescription("TUT attempt 2 for Fixture set 1").assertDoesNotExist()
        first.performTextReplacement("0")
        compose.onNodeWithContentDescription("TUT total for Fixture set 1").assertTextContains("0 s", substring = true)
        compose.onNodeWithContentDescription("TUT attempt 2 for Fixture set 1").performTextInput("2")
        compose.onNodeWithContentDescription("TUT attempt 3 for Fixture set 1").assertExists()
        compose.onNodeWithContentDescription("TUT attempt 2 for Fixture set 1").performTextClearance()
        compose.onNodeWithContentDescription("TUT attempt 3 for Fixture set 1").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf("0") + List(7) { "" }, attemptsFor(draft)) }
        first.performTextReplacement("bad")
        compose.onNodeWithContentDescription("TUT attempt 2 for Fixture set 1").assertDoesNotExist()
        compose.onNodeWithContentDescription("TUT total for Fixture set 1").assertDoesNotExist()
        val error = compose.onNodeWithContentDescription("Enter a non-negative decimal")
        error.assertTextEquals("Error")
        val errorBounds = error.getUnclippedBoundsInRoot()
        assertTrue("Compact error remains one short line", (errorBounds.bottom - errorBounds.top).value <= 12.5f)
        val errorLayouts = mutableListOf<TextLayoutResult>()
        error.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(errorLayouts) }
        val errorLayout = errorLayouts.single()
        assertEquals("Entire compact error remains visible",
            errorLayout.layoutInput.text.length, errorLayout.getLineEnd(0, visibleEnd = true))
        errorLayout.layoutInput.text.indices.forEach { index ->
            val character = errorLayout.getBoundingBox(index)
            assertTrue("Compact error character $index stays within its field",
                character.left >= -0.5f && character.right <= errorLayout.size.width + 0.5f)
        }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "compact-timing-error.png")
            .outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    @Test
    fun timingTotalStacksAtFourVisibleBoxesWithLabelAboveAccumulatedValue() {
        var draft by mutableStateOf(emptyEntryDraft("fixture", 1).copy(
            tutEntries = encodeTimingInputs(listOf("3", "3")), timeUnderTension = "30",
        ))
        var locked by mutableStateOf(false)
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VibeCheckTheme {
                    Box(Modifier.width(320.dp)) {
                        MeasurementRenderer(
                            MeasurementMetric.SKILL_HOLD,
                            ExerciseInputConfig().withMeasurement(MeasurementMetric.SKILL_HOLD),
                            draft, "", attemptsFor(draft), locked, true, "Fixture set 1", Modifier,
                            onRepsChange = {}, onDraftChange = { draft = it },
                        )
                    }
                }
            }
        }
        val cumulative = compose.onNodeWithContentDescription("TUT total for Fixture set 1")
        val duration = compose.onNodeWithContentDescription("Total Time for Fixture set 1")
        cumulative.assertTextContains("6 s /", substring = true)
        duration.assertTextContains("30")
        val labelBounds = compose.onNodeWithContentDescription("Timing total label for Fixture set 1").getUnclippedBoundsInRoot()
        val cumulativeBounds = cumulative.getUnclippedBoundsInRoot()
        val durationBounds = duration.getUnclippedBoundsInRoot()
        val groupBounds = compose.onNodeWithContentDescription("Timing total for Fixture set 1").getUnclippedBoundsInRoot()
        assertTrue("Fewer than four visible boxes keep their sum to the left", cumulativeBounds.right <= durationBounds.left)
        assertEquals("Side-by-side values share their numeric centre",
            (durationBounds.top + durationBounds.bottom).value / 2,
            (cumulativeBounds.top + cumulativeBounds.bottom).value / 2, 0.5f)
        assertTrue("Total label stays above the accumulated value", labelBounds.bottom <= cumulativeBounds.top)
        assertEquals("Total label centres above the accumulated value",
            (labelBounds.left + labelBounds.right).value / 2,
            (cumulativeBounds.left + cumulativeBounds.right).value / 2, 0.5f)
        assertEquals("Inline total needs only its caption and compact input", 36f,
            (groupBounds.bottom - groupBounds.top).value, 0.5f)
        duration.performTextReplacement("125")
        val layouts = mutableListOf<TextLayoutResult>()
        duration.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val number = layouts.single { it.layoutInput.text.text == "125" }
        assertFalse("Three-digit total fits the actual editor", number.didOverflowWidth)
        assertEquals("Three-digit total remains fully visible", 3, number.getLineEnd(0, visibleEnd = true))
        assertTrue("Three-digit total fits its fixed box", number.getLineRight(0) <= number.size.width + 0.5f)
        duration.performTextReplacement("bad")
        compose.onNodeWithContentDescription("Enter a non-negative decimal").assertTextEquals("Error")
        duration.performTextClearance()
        cumulative.assertTextContains("6 s", substring = true)
        compose.runOnIdle { assertEquals("", draft.timeUnderTension) }
        duration.performTextReplacement("30")
        compose.runOnIdle { assertEquals("30", draft.timeUnderTension) }
        compose.runOnIdle { draft = draft.copy(tutEntries = encodeTimingInputs(List(3) { "3" })) }
        compose.onNodeWithContentDescription("TUT attempt 4 for Fixture set 1").assertExists()
        compose.onNodeWithContentDescription("TUT attempt 5 for Fixture set 1").assertDoesNotExist()
        cumulative.assertTextContains("9 s /", substring = true)
        val upper = cumulative.getUnclippedBoundsInRoot()
        val title = compose.onNodeWithContentDescription("Timing total label for Fixture set 1").getUnclippedBoundsInRoot()
        val stackedBox = duration.getUnclippedBoundsInRoot()
        assertEquals("Total label precedes the accumulated value", title.bottom.value, upper.top.value, 0.5f)
        assertEquals("Four visible boxes stack the accumulated value above total time",
            upper.bottom.value, stackedBox.top.value, 0.5f)
        assertSameSize("Reflow never changes total-box size", durationBounds, stackedBox)
        compose.runOnIdle { locked = true }
        compose.onNodeWithContentDescription("TUT attempt 4 for Fixture set 1").assertDoesNotExist()
        val lockedSum = cumulative.getUnclippedBoundsInRoot()
        val lockedTotal = duration.getUnclippedBoundsInRoot()
        assertTrue("Completion retains the stacked Total geometry without showing another blank",
            lockedSum.bottom <= lockedTotal.top)
        compose.runOnIdle {
            locked = false
            draft = draft.copy(tutEntries = encodeTimingInputs(listOf("")), timeUnderTension = "")
        }
        cumulative.assertDoesNotExist()
        val empty = duration.getUnclippedBoundsInRoot()
        val first = compose.onNodeWithContentDescription("Time Under Tension for Fixture set 1").getUnclippedBoundsInRoot()
        val emptyTitle = compose.onNodeWithContentDescription("Timing total label for Fixture set 1").getUnclippedBoundsInRoot()
        assertEquals("Empty boxes align instead of reserving a numerator row",
            (first.top + first.bottom).value / 2, (empty.top + empty.bottom).value / 2, 0.5f)
        assertEquals("No unused accumulated-value gap precedes an empty total", 2f, (empty.left - first.right).value, 0.5f)
        assertEquals("Empty total label directly precedes its box", emptyTitle.bottom.value, empty.top.value, 0.5f)
        listOf(1f, 2f).forEach { scale ->
            listOf(false, true).forEach { completed ->
                compose.runOnIdle { fontScale = scale; locked = completed }
                val grid = compose.onNodeWithContentDescription("Timing grid for Fixture set 1").getUnclippedBoundsInRoot()
                assertEquals("Empty timing containers reserve two rows, also when locked or font-scaled",
                    48f * scale + 2f, (grid.bottom - grid.top).value, 0.5f)
            }
        }
    }

    @Test
    fun actualEditableAndLockedValuesKeepInlineUnitsWithoutShrinkingOrClipping() {
        var locked by mutableStateOf(false)
        var fontScale by mutableFloatStateOf(1f)
        val values = listOf(
            Triple("32.5", "", "kg"), Triple("45.0", "Total", "s"),
            Triple("12.5", "", "cm"), Triple("8.0", "", "RPE"),
            Triple("20", "+", "lb"), Triple("90", "", "°"),
            Triple("125", "", "kg"), Triple("125", "", "cm"),
            Triple("125", "", "Reps"), Triple("10.0", "", "RPE"),
            Triple("125", "", "s"), Triple("99", "", "s"),
        )
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VibeCheckTheme {
                    Column(Modifier.width(160.dp).verticalScroll(rememberScrollState())) {
                        values.forEachIndexed { index, (value, label, unit) ->
                            MetricValue(
                                value, label, "Metric $index", locked, true, unit = unit,
                                compact = index >= 10,
                                width = if (index == 10) 32 else if (index == 11) 28
                                    else if (label == "Total") 56 else if (unit in listOf("kg", "lb", "cm", "°")) 60 else 64,
                                onChange = {},
                            )
                        }
                    }
                }
            }
        }
        listOf(1f, 2f).forEach { scale ->
            listOf(false, true).forEach { completed ->
                compose.runOnIdle { fontScale = scale; locked = completed }
                values.forEachIndexed { index, (value, label, unit) ->
                    val field = compose.onNodeWithContentDescription("Metric $index").performScrollTo()
                    field.assertTextContains(value)
                    val layouts = mutableListOf<TextLayoutResult>()
                    if (!completed) {
                        field.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
                            it(layouts)
                        }
                        assertTrue(layouts.isNotEmpty())
                        assertFalse("$value overflows the actual editor", layouts.single().didOverflowWidth)
                        assertEquals("Numbers use the larger field text", if (index >= 10) 12f else 15f,
                            layouts.single().layoutInput.style.fontSize.value)
                    }
                    if (label.isNotBlank()) {
                        val labelLayouts = mutableListOf<TextLayoutResult>()
                        compose.onNodeWithContentDescription("Label for Metric $index")
                            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
                            it(labelLayouts)
                        }
                        val layout = labelLayouts.single()
                        assertEquals("Entire '$label' label stays on the visible line", label.length,
                            layout.getLineEnd(0, visibleEnd = true))
                        // Paragraph width includes spare weighted space; inspect actual characters instead.
                        label.indices.forEach { offset ->
                            assertTrue("Label '$label' at $scale font scale (locked=$completed) clips a character",
                                layout.getBoundingBox(offset).right <= layout.size.width + 0.5f)
                        }
                    }
                    if (unit.isNotBlank()) {
                        val unitNode = compose.onNode(
                            hasText(unit) and hasAnyAncestor(hasContentDescription("Metric $index")),
                            useUnmergedTree = true,
                        )
                        val unitBounds = unitNode.getUnclippedBoundsInRoot()
                        val fieldBounds = field.getUnclippedBoundsInRoot()
                        assertTrue(unitBounds.right <= fieldBounds.right + 0.01.dp)
                        assertTrue("Units retain the field's comfortable right inset",
                            fieldBounds.right - unitBounds.right >= (if (index >= 10) 2.dp else 4.dp) - 0.5.dp)
                        val unitLayouts = mutableListOf<TextLayoutResult>()
                        unitNode.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
                            it(unitLayouts)
                        }
                        assertEquals("Units also use larger text", if (index >= 10) 9f else 12f,
                            unitLayouts.single().layoutInput.style.fontSize.value)
                        val unitLayout = unitLayouts.single()
                        assertEquals("The entire inline unit stays on its first line",
                            unit.length, unitLayout.getLineEnd(0, visibleEnd = true))
                        assertTrue("Inline unit $index ($value $unit, scale=$scale, locked=$completed) clips: " +
                            "width=${unitLayout.size.width}, end=${unitLayout.getBoundingBox(unit.lastIndex).right}",
                            unitLayout.getBoundingBox(unit.lastIndex).right <= unitLayout.size.width + 0.5f)
                        if (completed) {
                            val number = compose.onNode(
                                hasText(value) and hasAnyAncestor(hasContentDescription("Metric $index")),
                                useUnmergedTree = true,
                            )
                            val numberBounds = number.getUnclippedBoundsInRoot()
                            assertTrue("Numbers have a comfortable left inset",
                                numberBounds.left - fieldBounds.left >= (if (index >= 10) 2.dp else 4.dp) - 0.5.dp)
                            val numberLayouts = mutableListOf<TextLayoutResult>()
                            number.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
                                it(numberLayouts)
                            }
                            assertTrue("Completed $value fits without horizontal scrolling: " +
                                "glyphEnd=${numberLayouts.single().getBoundingBox(value.lastIndex).right}, " +
                                "viewport=${number.fetchSemanticsNode().boundsInRoot.width}",
                                numberLayouts.single().getBoundingBox(value.lastIndex).right <=
                                    number.fetchSemanticsNode().boundsInRoot.width + 0.5f)
                            if (label.isNotBlank()) {
                                val caption = compose.onNodeWithContentDescription("Label for Metric $index")
                                    .getUnclippedBoundsInRoot()
                                assertTrue("Captions stay above, not inside, the compact numeric box",
                                    caption.bottom <= fieldBounds.top + 0.5.dp)
                            }
                            val contentTop = minOf(numberBounds.top, unitBounds.top)
                            val contentBottom = maxOf(numberBounds.bottom, unitBounds.bottom)
                            assertEquals("Visible content is vertically centred, without an empty label spacer",
                                (fieldBounds.top + fieldBounds.bottom).value / 2,
                                (contentTop + contentBottom).value / 2, 0.5f)
                            fun baseline(node: SemanticsNodeInteraction): Float {
                                val textLayouts = mutableListOf<TextLayoutResult>()
                                node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
                                    it(textLayouts)
                                }
                                return node.fetchSemanticsNode().boundsInRoot.top + textLayouts.single().firstBaseline
                            }
                            assertTrue("Numbers do not overlap fixed trailing units", unitBounds.left >= numberBounds.right)
                            assertEquals("Number and unit are centred together",
                                (fieldBounds.left + fieldBounds.right).value / 2,
                                (numberBounds.left + unitBounds.right).value / 2, 0.5f)
                            assertEquals("Value/unit baseline", baseline(number), baseline(unitNode), 1f)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun valuesAndUnitsStayCentredTogetherAsNumbersGrow() {
        var value by mutableStateOf("1")
        var locked by mutableStateOf(false)
        val fields = listOf(Triple("kg", 60, false), Triple("Reps", 64, false), Triple("s", 32, true))
        compose.setContent {
            VibeCheckTheme {
                Column(Modifier.width(160.dp)) {
                    fields.forEachIndexed { index, (unit, width, compact) ->
                        MetricValue(
                            value, "", "Anchored $index", locked, true,
                            width = width, compact = compact, unit = unit, onChange = {},
                        )
                    }
                }
            }
        }
        listOf(false, true).forEach { completed ->
            compose.runOnIdle { locked = completed }
            val previousUnits = mutableMapOf<Int, DpRect>()
            val previousNumbers = mutableMapOf<Int, DpRect>()
            listOf("1", "12", "125").forEach { digits ->
                compose.runOnIdle { value = digits }
                fields.forEachIndexed { index, (unit, _, _) ->
                    val ancestor = hasAnyAncestor(hasContentDescription("Anchored $index"))
                    val unitBounds = compose.onNode(hasText(unit) and ancestor, useUnmergedTree = true)
                        .getUnclippedBoundsInRoot()
                    previousUnits.put(index, unitBounds)?.let { previous ->
                        assertTrue("The unit moves right as the centred group grows", unitBounds.left > previous.left)
                    }
                    if (completed) {
                        val numberBounds = compose.onNode(hasText(digits) and ancestor, useUnmergedTree = true)
                            .getUnclippedBoundsInRoot()
                        previousNumbers.put(index, numberBounds)?.let { previous ->
                            assertTrue("Extra digits extend left", numberBounds.left < previous.left)
                        }
                        val fieldBounds = compose.onNodeWithContentDescription("Anchored $index").getUnclippedBoundsInRoot()
                        assertEquals("The whole value/unit group stays centred",
                            (fieldBounds.left + fieldBounds.right).value / 2,
                            (numberBounds.left + unitBounds.right).value / 2, 0.5f)
                    } else {
                        val layouts = mutableListOf<TextLayoutResult>()
                        compose.onNodeWithContentDescription("Anchored $index")
                            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) {
                                it(layouts)
                            }
                        assertEquals(TextAlign.Center, layouts.single().layoutInput.style.textAlign)
                        assertFalse("Growing numbers remain fully visible", layouts.single().didOverflowWidth)
                    }
                }
            }
        }
    }

    private fun exerciseMatrix(width: Int, fontScale: Float) {
        val completionGeometry = StringBuilder()
        var resistance by mutableStateOf(ResistanceMetric.BODYWEIGHT)
        var measurement by mutableStateOf(MeasurementMetric.REPETITIONS)
        var locked by mutableStateOf(false)
        var attemptCount by mutableIntStateOf(8)
        var bandCount by mutableIntStateOf(8)
        var draft by mutableStateOf(emptyEntryDraft("fixture", 1).copy(
            performance = "7", rpe = "8", romValue = "45", timeUnderTension = "45",
            tutEntries = encodeTimingInputs(List(8) { (it + 1).toString() }),
        ))
        var bands by mutableStateOf(emptyList<String>())
        val definitions = List(8) { BandEntity("custom-$it", "Band $it", it + 0.5, 0xFF5085D6L) }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                VibeCheckTheme {
                    Column(
                        Modifier.width(width.dp).verticalScroll(rememberScrollState())
                            .semantics { contentDescription = "Tile fixture" },
                    ) {
                        val config = ExerciseInputConfig().withResistance(resistance).withMeasurement(measurement)
                        Text("Variant")
                        Box(Modifier.fillMaxWidth().semantics { contentDescription = "Measured tile" }) {
                        WorkoutSetTile(
                            identity = "Fixture set 1",
                            showResistance = resistance != ResistanceMetric.BODYWEIGHT,
                            minimumMeasurementWidth = measurementMinimumWidth(measurement),
                            resistance = {
                                ResistanceRenderer(
                                    resistance, config, draft, "20", "kg", definitions.take(bandCount), bands,
                                    locked, true, "Fixture set 1",
                                    onWeightChange = {}, onBandsChange = { bands = it },
                                )
                            },
                            measurement = {
                                MeasurementRenderer(
                                    measurement, config, draft, draft.performance,
                                    if (measurement == MeasurementMetric.SKILL_HOLD) List(attemptCount) { (it + 1).toString() } else listOf("7"),
                                    locked, true, "Fixture set 1", Modifier,
                                    onRepsChange = { draft = draft.copy(performance = it) },
                                    onDraftChange = { draft = it },
                                )
                            },
                            rpe = {
                                MetricValue(
                                    draft.rpe, "", "RPE for Fixture set 1", locked, true, width = 64, unit = "RPE", secondary = true,
                                    onChange = { draft = draft.copy(rpe = it) },
                                )
                            },
                            completion = {
                                CompositionLocalProvider(androidx.compose.material3.LocalMinimumInteractiveComponentSize provides 0.dp) {
                                    IconButton(onClick = {}, modifier = Modifier.size(24.dp)
                                        .semantics { contentDescription = "Complete fixture" }) {
                                        Text("✓")
                                    }
                                }
                            },
                            feedback = {},
                        )
                        }
                    }
                }
            }
        }
        ResistanceMetric.entries.forEach { load ->
            MeasurementMetric.entries.forEach { result ->
                compose.runOnIdle {
                    resistance = load; measurement = result; bands = emptyList(); attemptCount = 8; bandCount = 8
                }
                compose.waitForIdle()
                compose.onNodeWithText("Variant").performScrollTo()
                val fixture = compose.onNodeWithContentDescription("Tile fixture").getUnclippedBoundsInRoot()
                val inputs = compose.onAllNodes(hasSetTextAction())
                val rectangles = (0 until inputs.fetchSemanticsNodes().size).map { inputs[it].getUnclippedBoundsInRoot() }
                rectangles.forEach { bounds ->
                    assertTrue("$load/$result: input overflows left", bounds.left >= fixture.left)
                    assertTrue("$load/$result: input overflows right", bounds.right <= fixture.right + 0.01.dp)
                    assertEquals("$load/$result: every numeric box uses the timing height",
                        24f * fontScale, (bounds.bottom - bounds.top).value, 0.5f)
                }
                rectangles.forEachIndexed { index, a ->
                    rectangles.drop(index + 1).forEach { b ->
                        assertTrue("$load/$result: overlapping inputs", a.right <= b.left + 0.01.dp ||
                            b.right <= a.left + 0.01.dp || a.bottom <= b.top + 0.01.dp || b.bottom <= a.top + 0.01.dp)
                    }
                }
                val rpe = compose.onNodeWithContentDescription("RPE for Fixture set 1").getUnclippedBoundsInRoot()
                val tick = compose.onNodeWithContentDescription("Complete fixture").getUnclippedBoundsInRoot()
                if (result == MeasurementMetric.HOLD) {
                    val duration = compose.onNodeWithContentDescription("Time Under Tension for Fixture set 1")
                        .getUnclippedBoundsInRoot()
                    assertEquals("Single-duration fields use the same width as Reps/RPE, not accumulated cells",
                        (rpe.right - rpe.left).value, (duration.right - duration.left).value, 0.5f)
                }
                val columnNames = if (load == ResistanceMetric.BODYWEIGHT) {
                    listOf("Measurement column", "RPE column")
                } else listOf("Resistance column", "Measurement column", "RPE column")
                val columns = columnNames.map {
                    compose.onNodeWithContentDescription("$it for Fixture set 1").getUnclippedBoundsInRoot()
                }
                columns.forEach {
                    assertTrue("$load/$result: column stays inside tile",
                        it.left >= fixture.left && it.right <= fixture.right + 0.01.dp)
                }
                val actionCenter = (rpe.top + rpe.bottom).value / 2
                if (load != ResistanceMetric.BODYWEIGHT && columns.first().top < rpe.bottom && columns.first().bottom > rpe.top) {
                    val resistanceBounds = columns.first()
                    assertEquals("Resistance boxes stay centred against RPE",
                        actionCenter, (resistanceBounds.top + resistanceBounds.bottom).value / 2, 0.5f)
                }
                if (result != MeasurementMetric.SKILL_HOLD &&
                    columns[columnNames.indexOf("Measurement column")].top < rpe.bottom &&
                    columns[columnNames.indexOf("Measurement column")].bottom > rpe.top) {
                    val measurementBounds = columns[columnNames.indexOf("Measurement column")]
                    assertEquals("Measurement boxes stay centred against RPE",
                        actionCenter, (measurementBounds.top + measurementBounds.bottom).value / 2, 0.5f)
                }
                assertTrue(rpe.right <= tick.left)
                assertEquals("$load/$result: tick stays at the right edge", fixture.right.value, tick.right.value, 0.5f)
                assertEquals("$load/$result: controls start at the left edge", fixture.left.value, columns.first().left.value, 0.5f)
                columns.forEach {
                    assertTrue("$load/$result: controls leave room for the right-side tick",
                        (tick.left - it.right).value >= 7.5f)
                }
                columns.zipWithNext().forEach { (a, b) ->
                    if (a.top < b.bottom && a.bottom > b.top) {
                        assertEquals("$load/$result: left-packed groups use consistent spacing",
                            8f, (b.left - a.right).value, 0.5f)
                    } else {
                        assertEquals("$load/$result: wrapped groups restart at the left edge",
                            fixture.left.value, b.left.value, 0.5f)
                    }
                }
                if (load == ResistanceMetric.BODYWEIGHT) {
                    compose.onNodeWithContentDescription("Resistance column for Fixture set 1").assertDoesNotExist()
                    compose.onNodeWithContentDescription("Resistance for Fixture set 1").assertDoesNotExist()
                    compose.onNodeWithText("Bodyweight").assertDoesNotExist()
                }
                if (load == ResistanceMetric.BANDS) {
                    val dots = definitions.indices.map {
                        compose.onNodeWithContentDescription("Band $it band for Fixture set 1").getUnclippedBoundsInRoot()
                    }
                    assertEquals(2, dots.map { it.top }.distinct().size)
                    assertEquals(4, dots.count { it.top == dots.first().top })
                    assertEquals(dots.first().left, dots[4].left)
                    val grid = compose.onNodeWithContentDescription("Resistance grid for Fixture set 1").getUnclippedBoundsInRoot()
                    assertEquals("Band spacing stays unchanged when numeric boxes shrink",
                        72f * fontScale, (grid.bottom - grid.top).value, 0.5f)
                    compose.onNodeWithContentDescription("Band 0 band for Fixture set 1").performScrollTo().performClick()
                    compose.onNodeWithContentDescription("Band 0 band for Fixture set 1").assertIsSelected()
                    compose.onNodeWithContentDescription("Band 7 band for Fixture set 1").performScrollTo().performClick()
                    compose.onNodeWithContentDescription("Band 7 band for Fixture set 1").assertIsSelected()
                    compose.runOnIdle { bandCount = 4 }
                    val singleRowDot = compose.onNodeWithContentDescription("Band 0 band for Fixture set 1")
                        .getUnclippedBoundsInRoot()
                    assertSameSize("Band touch boxes do not change with row count", dots.first(), singleRowDot)
                    compose.runOnIdle { bandCount = 8 }
                }
                if (result == MeasurementMetric.SKILL_HOLD) {
                    compose.onNodeWithContentDescription("TUT attempt 8 for Fixture set 1").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithContentDescription("Total Time for Fixture set 1").assertExists()
                    compose.onNodeWithContentDescription("Open Total Time stopwatch for Fixture set 1").assertDoesNotExist()
                    compose.onNodeWithContentDescription("Add TUT entry for Fixture set 1").assertDoesNotExist()
                    fun timingBounds() = compose.onNodeWithContentDescription("Timing grid for Fixture set 1").getUnclippedBoundsInRoot()
                    fun totalBounds() = compose.onNodeWithContentDescription("Timing total for Fixture set 1").getUnclippedBoundsInRoot()
                    val grid = timingBounds()
                    val total = totalBounds()
                    assertTrue("Total stays beside attempts", total.left >= grid.right)
                    val attemptBoxes = (1..8).map {
                        compose.onNodeWithContentDescription("TUT attempt $it for Fixture set 1").getUnclippedBoundsInRoot()
                    }
                    val boxCenter = (attemptBoxes.minOf { it.top } + attemptBoxes.maxOf { it.bottom }).value / 2
                    assertEquals("Cumulative sum centres against the actual boxes",
                        boxCenter, (total.top + total.bottom).value / 2, 0.5f)
                    if (columns[columnNames.indexOf("Measurement column")].top < rpe.bottom &&
                        columns[columnNames.indexOf("Measurement column")].bottom > rpe.top) {
                        assertEquals("Attempts/RPE share the boxed centre", actionCenter, boxCenter, 0.5f)
                    }
                    attemptBoxes.groupBy { it.top }.values.forEach { row ->
                        row.sortedBy { it.left }.zipWithNext().forEach { (a, b) ->
                            assertEquals("Timing gaps stay compact instead of stretching with spare width",
                                2f, (b.left - a.right).value, 0.5f)
                        }
                    }
                    val attemptRows = attemptBoxes.map { it.top }
                    val fixedCell = compose.onNodeWithContentDescription("TUT attempt 1 for Fixture set 1")
                        .getUnclippedBoundsInRoot()
                    assertEquals("All numeric boxes now share the attempt height",
                        (rpe.bottom - rpe.top).value, (fixedCell.bottom - fixedCell.top).value, 0.5f)
                    val fullSum = compose.onNodeWithContentDescription("Timing total for Fixture set 1")
                        .getUnclippedBoundsInRoot()
                    assertEquals("The cumulative sum adds no vertical height",
                        maxOf(attemptBoxes.maxOf { it.bottom } - attemptBoxes.minOf { it.top }, (total.bottom - total.top)).value,
                        (columns[columnNames.indexOf("Measurement column")].bottom -
                            columns[columnNames.indexOf("Measurement column")].top).value, 0.5f)
                    assertEquals("Gap to cumulative sum matches timing-cell gaps",
                        2f, (fullSum.left - grid.right).value, 0.5f)
                    attemptBoxes.groupBy { it.top }.values.zipWithNext().forEach { (a, b) ->
                        assertEquals("Timing rows retain a small fixed vertical gap",
                            2f, (b.minOf { it.top } - a.maxOf { it.bottom }).value, 0.5f)
                    }
                    (2..8).forEach {
                        val cell = compose.onNodeWithContentDescription("TUT attempt $it for Fixture set 1")
                            .getUnclippedBoundsInRoot()
                        assertSameSize("Timing boxes keep fixed dimensions", fixedCell, cell)
                    }
                    val columnCount = attemptRows.count { it == attemptRows.first() }
                    assertTrue(columnCount in 1..4)
                    compose.runOnIdle { attemptCount = columnCount }
                    val twoRows = timingBounds()
                    assertEquals("Two timing rows retain fixed boxes plus their small gap",
                        (rpe.bottom - rpe.top).value * 2 + 2f, (twoRows.bottom - twoRows.top).value, 0.5f)
                    compose.runOnIdle { attemptCount = 1 }
                    val first = compose.onNodeWithContentDescription("Time Under Tension for Fixture set 1").getUnclippedBoundsInRoot()
                    val add = compose.onNodeWithContentDescription("TUT attempt 2 for Fixture set 1").getUnclippedBoundsInRoot()
                    assertSameSize("One/eight attempts have identical boxes", fixedCell, first)
                    assertSameSize("Automatically offered field uses the same fixed slot", first, add)
                    val plusWrapped = add.top >= first.bottom
                    if (plusWrapped) {
                        assertEquals("Next slot wraps to the same column", first.left.value, add.left.value, 0.01f)
                        assertTrue("Next slot wraps below without resizing", add.top >= first.bottom)
                    } else {
                        assertTrue("The add icon occupies the next attempt slot", add.left >= first.right)
                        assertTrue("The next slot shares the attempt row", add.top < first.bottom && add.bottom > first.top)
                    }
                    val oneRow = timingBounds()
                    assertEquals("Even one timing row reserves room for two compact rows",
                        (rpe.bottom - rpe.top).value * 2f + 2f,
                        (oneRow.bottom - oneRow.top).value, 0.5f)
                    listOf(1, 4, 7).forEach { count ->
                        compose.runOnIdle { attemptCount = count }
                        val plus = compose.onNodeWithContentDescription("TUT attempt ${count + 1} for Fixture set 1")
                            .getUnclippedBoundsInRoot()
                        val partialSum = compose.onNodeWithContentDescription("Timing total for Fixture set 1")
                            .getUnclippedBoundsInRoot()
                        assertTrue("Partial sum follows the timing grid", partialSum.left >= plus.right)
                        val alignedTotal = if (count + 1 < 4) {
                            compose.onNodeWithContentDescription("Total Time for Fixture set 1").getUnclippedBoundsInRoot()
                        } else partialSum
                        assertEquals("Partial Total centres the numeric row or stacked group, not its inline caption",
                            (timingBounds().top + timingBounds().bottom).value / 2,
                            (alignedTotal.top + alignedTotal.bottom).value / 2, 0.5f)
                        val cell = compose.onNodeWithContentDescription(
                            if (count == 1) "Time Under Tension for Fixture set 1" else "TUT attempt 1 for Fixture set 1",
                        ).getUnclippedBoundsInRoot()
                        assertSameSize("Partial counts never resize attempt boxes", fixedCell, cell)
                    }
                }
                val counts = if (result == MeasurementMetric.SKILL_HOLD) listOf(1, 3, 4, 8) else listOf(1)
                counts.forEach { count ->
                    compose.runOnIdle {
                        attemptCount = count
                        bands = listOf(definitions.first().id, definitions.last().id)
                        draft = draft.copy(rpe = if (count == 1) "" else "8")
                    }
                    val before = compose.onNodeWithContentDescription("Measured tile").getUnclippedBoundsInRoot()
                    compose.runOnIdle { locked = true }
                    val completed = compose.onNodeWithContentDescription("Measured tile").getUnclippedBoundsInRoot()
                    assertSameSize("$load/$result, $count attempts: completion retains the tile footprint", before, completed)
                    completionGeometry.appendLine("$load/$result, $count attempts: editing=$before; locked=$completed")
                    compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
                    compose.runOnIdle { locked = false }
                    assertSameSize("$load/$result: unlocking restores the same footprint", before,
                        compose.onNodeWithContentDescription("Measured tile").getUnclippedBoundsInRoot())
                }
            }
        }
        java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "centred-lock-$width-$fontScale-geometry.txt").writeText(completionGeometry.toString())
    }
}

package com.petermathie.vibecheck

import android.content.Context
import java.time.LocalDate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.petermathie.vibecheck.data.local.VibeDatabase
import com.petermathie.vibecheck.ui.EditorViewModel
import com.petermathie.vibecheck.ui.MeasurementsScreen
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

@android.annotation.SuppressLint("ViewModelConstructorInComposable")
class MeasurementsUiTest {
    private lateinit var database: VibeDatabase

    @get:Rule
    val lifecycle = ComposeRoomLifecycleRule {
        if (::database.isInitialized) database else null
    }
    private val compose get() = lifecycle.compose

    private fun scrollUntilVisible(text: String) {
        repeat(8) {
            if (compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()) return
            compose.onRoot().performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        compose.onNodeWithText(text).assertExists()
    }

    @Test
    fun savesOnlyBodyweightAndDisplaysTwoDecimals() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            VibeDatabase::class.java,
        ).build()
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                MeasurementsScreen(viewModel)
            }
        }

        compose.onAllNodesWithText(
            LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy")),
        ).onFirst().assertIsDisplayed()
        compose.onNodeWithContentDescription("Bodyweight").performTextInput("78.126")
        compose.onNodeWithText("Save bodyweight").performClick()
        compose.waitUntil(15_000) {
            runBlocking { database.editorDao().measurements().first().isNotEmpty() }
        }

        val saved = runBlocking { database.editorDao().measurements().first().single() }
        assertEquals("Bodyweight", saved.metric)
        assertEquals(78.126, saved.value, 0.0)
        compose.onNodeWithContentDescription("Bodyweight").performTextClearance()
        compose.onNodeWithContentDescription("Bodyweight").performTextInput("80")
        compose.onNodeWithText("Update bodyweight").performClick()
        compose.waitUntil(15_000) {
            runBlocking { database.editorDao().measurements().first().single().value == 80.0 }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(
            hasContentDescription("Progress chart", substring = true),
        )
        compose.onNodeWithContentDescription("Progress chart", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("80.0 to 80.0 kg", substring = true).assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Bodyweight: 80.00 kg"))
        compose.onAllNodesWithText("Bodyweight: 80.00 kg").onFirst().assertIsDisplayed()
        scrollUntilVisible("Calendar")
        scrollUntilVisible("Photos")
        compose.onNodeWithContentDescription("Reorder Calendar").assertExists()
        compose.onNodeWithContentDescription("Reorder Photos").assertExists()
        compose.onNodeWithText("80.00").assertExists()
    }

    @Test
    fun bodyCardOrderPersistsAcrossRecreation() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            VibeDatabase::class.java,
        ).build()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("body-layout", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        val generation = androidx.compose.runtime.mutableIntStateOf(0)
        val viewModel = lifecycle.own(EditorViewModel(database))
        compose.setContent {
            VibeCheckTheme {
                androidx.compose.runtime.key(generation.intValue) {
                    MeasurementsScreen(viewModel)
                }
            }
        }
        scrollUntilVisible("Photos")
        val actions = compose.onNodeWithContentDescription("Reorder Photos").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        assertTrue(actions.any { it.label == "Move earlier" })
        assertTrue(actions.first { it.label == "Move earlier" }.action())
        compose.waitUntil(15_000) {
            context.getSharedPreferences("body-layout", android.content.Context.MODE_PRIVATE).getString("card-order", "")?.startsWith("photos|") == true
        }
        compose.runOnIdle { generation.intValue++ }
        compose.onNodeWithContentDescription("Reorder Photos").assertExists()
        compose.onNodeWithText("Photos").assertExists()
    }

    @Test
    fun calendarAndPhotosKeepDistinctBoundsAtNarrowWidthAndLargeType() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, VibeDatabase::class.java).build()
        val photoDirectory = File(context.filesDir, "progress-photos").apply { mkdirs() }
        val photo = File(photoDirectory, "${System.currentTimeMillis()}.jpg")
        val bitmap = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        photo.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        context.getSharedPreferences("body-layout", Context.MODE_PRIVATE).edit().clear().commit()
        val fontScale = mutableFloatStateOf(1f)
        val viewModel = lifecycle.own(EditorViewModel(database))
        try {
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.floatValue)) {
                    VibeCheckTheme {
                        Box(Modifier.width(320.dp).height(760.dp)) {
                            MeasurementsScreen(viewModel)
                        }
                    }
                }
            }

            listOf(1f, 1.3f, 2f).forEach { scale ->
                compose.runOnIdle { fontScale.floatValue = scale }
                compose.onNode(hasScrollAction())
                    .performScrollToNode(hasContentDescription("Bodyweight calendar heading"))
                val calendarHeading = compose.onNodeWithContentDescription("Bodyweight calendar heading")
                    .fetchSemanticsNode().boundsInRoot
                val monthControls = compose.onNodeWithContentDescription("Bodyweight calendar month controls")
                    .fetchSemanticsNode().boundsInRoot
                assertTrue(
                    "scale=$scale heading=$calendarHeading controls=$monthControls",
                    calendarHeading.bottom <= monthControls.top + 0.5f,
                )

                compose.onNode(hasScrollAction())
                    .performScrollToNode(hasContentDescription("Bodyweight calendar weekday labels"))
                val weekdayLabels = compose.onNodeWithContentDescription("Bodyweight calendar weekday labels")
                    .fetchSemanticsNode().boundsInRoot
                val firstWeek = compose.onNodeWithContentDescription("Bodyweight calendar week 1")
                    .fetchSemanticsNode().boundsInRoot
                assertTrue(
                    "scale=$scale weekdays=$weekdayLabels firstWeek=$firstWeek",
                    weekdayLabels.bottom <= firstWeek.top + 0.5f,
                )

                compose.onNode(hasScrollAction())
                    .performScrollToNode(hasContentDescription("Progress photos heading"))
                val photosHeading = compose.onNodeWithContentDescription("Progress photos heading")
                    .fetchSemanticsNode().boundsInRoot
                val photosDate = compose.onNodeWithContentDescription("Progress photos date")
                    .fetchSemanticsNode().boundsInRoot
                assertTrue(
                    "scale=$scale heading=$photosHeading date=$photosDate",
                    photosHeading.bottom <= photosDate.top + 0.5f,
                )
                compose.onNode(hasScrollAction())
                    .performScrollToNode(hasContentDescription("Progress photo for selected day"))
                val actions = compose.onNodeWithContentDescription("Progress photos actions")
                    .fetchSemanticsNode().boundsInRoot
                val image = compose.onNodeWithContentDescription("Progress photo for selected day")
                    .fetchSemanticsNode().boundsInRoot
                assertTrue("scale=$scale actions=$actions image=$image", actions.bottom <= image.top + 0.5f)
            }
        } finally {
            photo.delete()
        }
    }
}

package com.petermathie.vibecheck

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.ui.StyleScreen
import com.petermathie.vibecheck.ui.rememberVibePalette
import com.petermathie.vibecheck.data.BackupPreferences
import com.petermathie.vibecheck.ui.theme.LocalVibeReducedMotion
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import com.petermathie.vibecheck.ui.theme.VibePalettes
import com.petermathie.vibecheck.ui.theme.VibeThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class StyleUiTest {
    @get:Rule
    val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("settings", 0)

    @After
    fun clearPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun curatedPalettesSwitchLiveAndLegacyCustomMigratesToOcean() {
        preferences.edit().clear().commit()
        compose.setContent {
            var themeMode by remember { mutableStateOf(VibeThemeMode.SYSTEM) }
            val palette = rememberVibePalette(preferences)
            VibeCheckTheme(palette) {
                Column {
                    Text("Active palette ${palette.id} dark=${palette.isDark}")
                    Text("Theme mode ${themeMode.id}")
                    Text("Reduced motion ${LocalVibeReducedMotion.current}")
                    StyleScreen(
                        palette.id,
                        { preferences.edit().putString("palette", it).apply() },
                        themeMode,
                        {
                            themeMode = it
                            preferences.edit().putString("themeMode", it.id).apply()
                        },
                        onBack = {},
                    )
                }
            }
        }
        val list = compose.onNode(hasScrollToIndexAction())
        VibePalettes.presets.forEach {
            list.performScrollToNode(hasContentDescription("${it.displayName} palette", substring = true))
            compose.onNodeWithContentDescription("${it.displayName} palette", substring = true).assertExists()
        }
        list.performTouchInput { swipeUp() }
        compose.onNodeWithText("Remove demo data").assertDoesNotExist()
        compose.onNodeWithText("Development data").assertDoesNotExist()
        list.performScrollToIndex(0)
        compose.onNodeWithContentDescription("Back to Settings").assertIsDisplayed()
        compose.onNodeWithText("Custom palette").assertDoesNotExist()
        compose.onNodeWithText("Follow system").assertExists()
        compose.onNodeWithText("Dark").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Theme mode dark").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("Active palette ocean dark=true").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Ocean palette, dark preview", substring = true).assertExists()
        compose.onNodeWithContentDescription("Sunset palette", substring = true).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Active palette sunset", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        preferences.edit().putBoolean("reducedMotion", true).apply()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Reduced motion true").fetchSemanticsNodes().isNotEmpty() }

        compose.runOnIdle {
            BackupPreferences.restore(
                JSONObject("""{"palette":"custom","themeMode":"dark","accent":-65281,"background":-16777216,"surface":-15658735}"""),
                preferences,
            )
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Active palette ocean", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("ocean", preferences.getString("palette", null))
        assertFalse(preferences.contains("accent"))
        assertFalse(preferences.contains("background"))
        assertFalse(preferences.contains("surface"))
        assertEquals("dark", preferences.getString("themeMode", null))
    }

    @Test
    fun missingThemeModeFollowsSystemAndExplicitModesArePreserved() {
        preferences.edit().clear().commit()
        var systemDark = false
        compose.setContent {
            systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val palette = rememberVibePalette(preferences)
            Text("Palette dark=${palette.isDark}")
        }
        compose.onNodeWithText("Palette dark=$systemDark").assertExists()

        preferences.edit().putString("themeMode", "dark").apply()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Palette dark=true").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("dark", preferences.getString("themeMode", null))

        preferences.edit().putString("themeMode", "light").apply()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Palette dark=false").fetchSemanticsNodes().isNotEmpty() }
        assertEquals("light", preferences.getString("themeMode", null))
    }
}

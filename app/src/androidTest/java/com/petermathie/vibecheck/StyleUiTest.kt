package com.petermathie.vibecheck

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.petermathie.vibecheck.ui.StyleScreen
import com.petermathie.vibecheck.ui.rememberVibePalette
import com.petermathie.vibecheck.ui.rememberVibeVisualStyle
import com.petermathie.vibecheck.data.BackupPreferences
import com.petermathie.vibecheck.ui.theme.LocalVibeReducedMotion
import com.petermathie.vibecheck.ui.theme.LocalVibeStyleTokens
import com.petermathie.vibecheck.ui.theme.VibeCheckTheme
import com.petermathie.vibecheck.ui.theme.VibePalettes
import com.petermathie.vibecheck.ui.theme.VibeThemeMode
import com.petermathie.vibecheck.ui.theme.VibeVisualStyle
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
            val visualStyle = rememberVibeVisualStyle(preferences)
            VibeCheckTheme(palette, visualStyle) {
                Column {
                    Text("Active palette ${palette.id} dark=${palette.isDark}")
                    Text("Active style ${visualStyle.id}")
                    Text("Theme mode ${themeMode.id}")
                    Text("Reduced motion ${LocalVibeReducedMotion.current}")
                    StyleScreen(
                        visualStyle,
                        { preferences.edit().putString("visualStyle", it.id).apply() },
                        palette.id,
                        { preferences.edit().putString("palette", it).apply() },
                        themeMode,
                        {
                            themeMode = it
                            preferences.edit().putString("themeMode", it.id).apply()
                        },
                        {},
                    )
                }
            }
        }
        VibePalettes.presets.forEach {
            compose.onNodeWithContentDescription("${it.displayName} palette", substring = true).assertExists()
        }
        compose.onNodeWithText("Custom palette").assertDoesNotExist()
        compose.onNodeWithText("Retro Futuristic").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Active style retro_future").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Retro Futuristic visual style preview").assertExists()
        compose.onNodeWithText("System").assertExists()
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
                JSONObject("""{"palette":"custom","themeMode":"dark","visualStyle":"retro_future","accent":-65281,"background":-16777216,"surface":-15658735}"""),
                preferences,
            )
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Active palette ocean", substring = true).fetchSemanticsNodes().isNotEmpty() }
        assertEquals("ocean", preferences.getString("palette", null))
        assertFalse(preferences.contains("accent"))
        assertFalse(preferences.contains("background"))
        assertFalse(preferences.contains("surface"))
        assertEquals("dark", preferences.getString("themeMode", null))
        assertEquals("retro_future", preferences.getString("visualStyle", null))
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

    @Test
    fun visualStyleBackupRoundTripsAndUnknownValuesNormalizeSafely() {
        preferences.edit()
            .putString("visualStyle", VibeVisualStyle.RETRO_FUTURE.id)
            .putString("palette", VibePalettes.Forest.id)
            .commit()
        val source = JSONObject()
            .put("format", "vibe-trainer")
            .put("version", 1)
            .put("tables", JSONObject())
            .toString()
        val attached = BackupPreferences.attach(source, preferences)
        assertEquals("retro_future", JSONObject(attached).getJSONObject("preferences").getString("visualStyle"))

        val validated = BackupPreferences.validate(
            JSONObject(attached)
                .put(
                    "preferences",
                    JSONObject(attached).getJSONObject("preferences").put("visualStyle", "unknown"),
                )
                .toString(),
        )
        assertEquals("standard", validated?.getString("visualStyle"))
        BackupPreferences.restore(validated, preferences)
        assertEquals("standard", preferences.getString("visualStyle", null))
        assertEquals("forest", preferences.getString("palette", null))
    }

    @Test
    fun reducedMotionDisablesRetroScannerSweepLive() {
        preferences.edit()
            .putString("visualStyle", VibeVisualStyle.RETRO_FUTURE.id)
            .putBoolean("reducedMotion", false)
            .commit()
        compose.setContent {
            val style = rememberVibeVisualStyle(preferences)
            VibeCheckTheme(VibePalettes.Mono.dark, style) {
                Text("Scanner sweep ${LocalVibeStyleTokens.current.scannerSweepEnabled}")
            }
        }
        compose.onNodeWithText("Scanner sweep true").assertExists()
        preferences.edit().putBoolean("reducedMotion", true).apply()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Scanner sweep false").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun retroStyleScreenPreviewFitsAt320DpAndTwoHundredPercentFontScale() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                Box(Modifier.width(320.dp)) {
                    VibeCheckTheme(VibePalettes.Mono.dark, VibeVisualStyle.RETRO_FUTURE) {
                        StyleScreen(
                            visualStyle = VibeVisualStyle.RETRO_FUTURE,
                            onVisualStyleChange = {},
                            selectedId = VibePalettes.Mono.id,
                            onSelect = {},
                            themeMode = VibeThemeMode.DARK,
                            onThemeModeChange = {},
                            onRemoveDemo = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Retro Futuristic visual style preview").assertIsDisplayed()
    }
}

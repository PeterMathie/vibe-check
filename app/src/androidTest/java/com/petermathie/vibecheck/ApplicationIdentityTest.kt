package com.petermathie.vibecheck

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationIdentityTest {
    @Test
    fun packageLabelLauncherAndRoomSchemasUseVibeCheckIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.petermathie.vibecheck", context.packageName)
        assertEquals("Vibe Check", context.applicationInfo.loadLabel(context.packageManager).toString())
        assertTrue(context.applicationInfo.icon != 0)
        assertTrue(context.resources.getIdentifier("ic_launcher_round", "mipmap", context.packageName) != 0)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertNotNull(launchIntent)
        assertEquals("com.petermathie.vibecheck.MainActivity", launchIntent?.component?.className)

        val schemaPath = "com.petermathie.vibecheck.data.local.VibeDatabase"
        val schemas = InstrumentationRegistry.getInstrumentation().context.assets.list(schemaPath).orEmpty().toSet()
        assertTrue((2..13).all { "$it.json" in schemas })
    }

    @Test
    fun launcherUsesApprovedHeartBrainArtworkAndThemedMask() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(0xFF050A18.toInt(), context.getColor(R.color.ic_launcher_background))

        val foreground = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_foreground)
        val monochrome = BitmapFactory.decodeResource(context.resources, R.drawable.ic_launcher_monochrome)
        assertEquals(foreground.width, foreground.height)
        assertEquals(monochrome.width, monochrome.height)
        assertEquals(Color.TRANSPARENT, foreground.getPixel(0, 0))
        assertEquals(Color.TRANSPARENT, monochrome.getPixel(0, 0))

        val foregroundPixels = IntArray(foreground.width * foreground.height).also {
            foreground.getPixels(it, 0, foreground.width, 0, 0, foreground.width, foreground.height)
        }
        assertTrue(foregroundPixels.any { Color.alpha(it) > 0 && Color.red(it) > Color.green(it) * 1.4 })
        assertTrue(foregroundPixels.any { Color.alpha(it) > 0 && Color.green(it) > Color.red(it) * 1.4 })
        assertTrue(foregroundPixels.any { Color.alpha(it) > 0 && Color.blue(it) > Color.red(it) * 1.4 })

        val monochromePixels = IntArray(monochrome.width * monochrome.height).also {
            monochrome.getPixels(it, 0, monochrome.width, 0, 0, monochrome.width, monochrome.height)
        }
        assertTrue(monochromePixels.any { Color.alpha(it) > 0 })
        assertTrue(monochromePixels.filter { Color.alpha(it) > 0 }.all {
            Color.red(it) == Color.green(it) && Color.green(it) == Color.blue(it)
        })
    }
}

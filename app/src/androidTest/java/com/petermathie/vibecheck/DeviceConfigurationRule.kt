package com.petermathie.vibecheck

import android.app.Instrumentation
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class DeviceConfigurationRule(
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation(),
) : TestWatcher() {
    private lateinit var baseline: DeviceConfiguration

    override fun starting(description: Description) {
        baseline = readConfiguration()
    }

    override fun finished(description: Description) {
        try {
            restoreConfiguration(baseline)
        } finally {
            assertEquals("Instrumentation test leaked global device configuration", baseline, readConfiguration())
        }
    }

    private fun readConfiguration(): DeviceConfiguration {
        val size = shell("wm size")
        val density = shell("wm density")
        return DeviceConfiguration(
            sizeOverride = size.lineSequence()
                .firstOrNull { it.startsWith("Override size:") }
                ?.substringAfter(':')
                ?.trim(),
            densityOverride = density.lineSequence()
                .firstOrNull { it.startsWith("Override density:") }
                ?.substringAfter(':')
                ?.trim(),
            fontScale = setting("system", "font_scale"),
            accelerometerRotation = setting("system", "accelerometer_rotation"),
            userRotation = setting("system", "user_rotation"),
        )
    }

    private fun restoreConfiguration(configuration: DeviceConfiguration) {
        shell(configuration.sizeOverride?.let { "wm size $it" } ?: "wm size reset")
        shell(configuration.densityOverride?.let { "wm density $it" } ?: "wm density reset")
        restoreSetting("system", "font_scale", configuration.fontScale)
        restoreSetting("system", "accelerometer_rotation", configuration.accelerometerRotation)
        restoreSetting("system", "user_rotation", configuration.userRotation)
    }

    private fun setting(namespace: String, key: String): String? =
        shell("settings get $namespace $key").takeUnless { it.isBlank() || it == "null" }

    private fun restoreSetting(namespace: String, key: String, value: String?) {
        shell(
            if (value == null) {
                "settings delete $namespace $key"
            } else {
                "settings put $namespace $key $value"
            },
        )
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText().trim() }

    private data class DeviceConfiguration(
        val sizeOverride: String?,
        val densityOverride: String?,
        val fontScale: String?,
        val accelerometerRotation: String?,
        val userRotation: String?,
    )
}

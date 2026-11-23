package com.petermathie.vibecheck

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import org.json.JSONArray
import org.json.JSONObject

// Temporary, opt-in CI evidence. Remove with the diagnostic workflow changes before landing.
internal fun withCiFailureDiagnostics(
    compose: ComposeContentTestRule,
    label: String,
    nodes: Map<String, SemanticsNodeInteraction> = emptyMap(),
    block: () -> Unit,
) {
    if (InstrumentationRegistry.getArguments().getString("ciFailureDiagnostics") != "true") {
        block()
        return
    }
    check(Build.VERSION.SDK_INT == 35) { "These temporary diagnostics require the approved API35 CI device" }
    require(label.matches(Regex("[A-Za-z0-9._-]+")))
    CiFailureCapture.capture(compose, "$label-before", nodes)
    try {
        block()
    } catch (failure: Throwable) {
        try {
            CiFailureCapture.capture(compose, "$label-failure", nodes)
        } catch (diagnosticFailure: Throwable) {
            Log.e("CiFailureDiagnostics", "Failure-time capture failed for $label", diagnosticFailure)
            failure.addSuppressed(diagnosticFailure)
        }
        throw failure
    }
}

private object CiFailureCapture {
    private val sequence = AtomicInteger()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val runId: String
        get() = requireNotNull(InstrumentationRegistry.getArguments().getString("ciDiagnosticRunId"))
            .also { require(it.matches(Regex("[0-9]+-[0-9]+"))) }

    fun capture(
        compose: ComposeContentTestRule,
        label: String,
        nodes: Map<String, SemanticsNodeInteraction>,
    ) {
        val prefix = "${sequence.incrementAndGet()}-$label"
        val started = SystemClock.uptimeMillis()
        listOf("window windows", "activity activities", "input").forEach { service ->
            val output = ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("dumpsys $service"),
            ).bufferedReader().use { it.readText() }
            check(output.isNotBlank()) { "Empty dumpsys $service at $label" }
            save("$prefix-${service.substringBefore(' ')}.txt", "text/plain", output.toByteArray())
        }
        val roots = JSONArray()
        instrumentation.runOnMainSync {
            WindowInspector.getGlobalWindowViews().forEach { root ->
                val location = IntArray(2)
                root.getLocationOnScreen(location)
                val visibleFrame = Rect()
                root.getWindowVisibleDisplayFrame(visibleFrame)
                val params = root.layoutParams as? WindowManager.LayoutParams
                val insets = root.rootWindowInsets
                roots.put(JSONObject().apply {
                    put("view", root.toString())
                    put("windowToken", root.windowToken.toString())
                    put("applicationWindowToken", root.applicationWindowToken.toString())
                    put("windowId", root.windowId.toString())
                    put("attached", root.isAttachedToWindow)
                    put("hasWindowFocus", root.hasWindowFocus())
                    put("hasFocus", root.hasFocus())
                    put("layoutRequested", root.isLayoutRequested)
                    put("locationOnScreenPx", JSONArray(location.toList()))
                    put("sizePx", JSONArray(listOf(root.width, root.height)))
                    put("visibleFramePx", visibleFrame.toShortString())
                    put("density", root.resources.displayMetrics.density)
                    put("densityDpi", root.resources.displayMetrics.densityDpi)
                    put("fontScale", root.resources.configuration.fontScale)
                    put("windowType", params?.type)
                    put("windowFlags", params?.flags)
                    put("windowTitle", params?.title?.toString())
                    put("layoutParamsToken", params?.token?.toString())
                    put("softInputMode", params?.softInputMode)
                    put("insetsAvailable", insets != null)
                    if (insets != null) {
                        put("systemBarsPx", insets.getInsets(WindowInsets.Type.systemBars()).toString())
                        put("systemBarsIgnoringVisibilityPx", insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()).toString())
                        put("displayCutoutPx", insets.getInsets(WindowInsets.Type.displayCutout()).toString())
                        put("imePx", insets.getInsets(WindowInsets.Type.ime()).toString())
                        put("imeVisible", insets.isVisible(WindowInsets.Type.ime()))
                    }
                })
            }
        }
        val geometry = JSONObject()
        nodes.forEach { (name, interaction) ->
            try {
                geometry.put(name, nodeGeometry(compose, interaction))
            } catch (unavailable: AssertionError) {
                Log.e("CiFailureDiagnostics", "Node unavailable in $label: $name", unavailable)
                geometry.put(name, JSONObject().put("nodeUnavailable", unavailable.toString()))
            }
        }
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val screenshotUri = try {
            val bytes = ByteArrayOutputStream().use { output ->
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
            save("$prefix.png", "image/png", bytes)
        } finally {
            screenshot.recycle()
        }
        val metadata = JSONObject().apply {
            put("label", label)
            put("runId", runId)
            put("startedUptimeMillis", started)
            put("completedUptimeMillis", SystemClock.uptimeMillis())
            val testFile = requireNotNull(InstrumentationRegistry.getArguments().getString("testFile"))
            require(testFile == "/data/local/tmp/vibe-ci-selectors-$runId.txt")
            put("instrumentationTestFile", testFile)
            put("instrumentationSelectors", File(testFile).readText(Charsets.UTF_8))
            put("composeDensity", compose.density.density)
            put("composeFontScale", compose.density.fontScale)
            put("nativeRoots", roots)
            put("nodes", geometry)
            put("screenshotUri", screenshotUri)
        }
        save("$prefix.json", "application/json", metadata.toString(2).toByteArray())
        Log.i("CiFailureDiagnostics", "CAPTURE_COMPLETE $prefix")
    }

    private fun nodeGeometry(compose: ComposeContentTestRule, interaction: SemanticsNodeInteraction): JSONObject {
        val node = interaction.fetchSemanticsNode()
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.let { action ->
            compose.runOnIdle { check(action(layouts)) { "Text-layout action returned false" } }
        }
        return JSONObject().apply {
            put("semanticsId", node.id)
            put("semantics", node.config.toString())
            put("semanticsTree", interaction.printToString(maxDepth = 6))
            put("boundsInRootPx", rect(node.boundsInRoot))
            put("boundsInWindowPx", rect(node.boundsInWindow))
            put("positionInRootPx", node.positionInRoot.toString())
            put("unclippedSizePx", node.size.toString())
            put("unclippedBoundsDp", interaction.getUnclippedBoundsInRoot().toString())
            put("nodeDensity", node.layoutInfo.density.density)
            put("nodeFontScale", node.layoutInfo.density.fontScale)
            put("textLayouts", JSONArray().apply {
                layouts.forEach { layout ->
                    put(JSONObject().apply {
                        put("text", layout.layoutInput.text.text)
                        put("sizePx", layout.size.toString())
                        put("paragraphWidthPx", layout.multiParagraph.width)
                        put("constraints", layout.layoutInput.constraints.toString())
                        put("textAlign", layout.layoutInput.style.textAlign.toString())
                        put("fontSize", layout.layoutInput.style.fontSize.toString())
                        put("lineHeight", layout.layoutInput.style.lineHeight.toString())
                        put("visualOverflow", layout.hasVisualOverflow)
                        put("lines", JSONArray().apply {
                            repeat(layout.lineCount) { line ->
                                put(JSONObject().apply {
                                    put("leftPx", layout.getLineLeft(line))
                                    put("rightPx", layout.getLineRight(line))
                                    put("topPx", layout.getLineTop(line))
                                    put("bottomPx", layout.getLineBottom(line))
                                })
                            }
                        })
                        put("glyphs", JSONArray().apply {
                            repeat(minOf(layout.layoutInput.text.length, 256)) { offset ->
                                put(JSONObject().put("offset", offset).put("boundsPx", rect(layout.getBoundingBox(offset))))
                            }
                        })
                    })
                }
            })
        }
    }

    private fun rect(value: ComposeRect) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))

    private fun save(name: String, mime: String, bytes: ByteArray): String {
        val resolver = instrumentation.targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/vibe-ci-failure-diagnostics/$runId")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        requireNotNull(resolver.openOutputStream(uri)).use { it.write(bytes) }
        check(resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) == 1)
        return uri.toString()
    }
}

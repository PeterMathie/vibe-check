package com.petermathie.vibecheck.ui.anatomy

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleMapMotionTest {
    @Test
    fun verticalFillLeavesBalancedBreathingRoomWithoutChangingWidth() {
        val bounds = MuscleMapBounds(Rect(192f, 62f, 832f, 1428f), Rect(220f, 250f, 804f, 1300f))
        listOf(353 to 972f, 900 to 420f).forEach { (width, referenceHeight) ->
            val original = muscleMapTransform(
                width.toFloat(), referenceHeight,
                bounds.outline.left, bounds.outline.right, bounds.outline.top, bounds.outline.bottom,
            )
            val height = croppedMuscleMapHeight(bounds, width, referenceHeight) + 84
            val filled = muscleMapTransform(
                width.toFloat(), height.toFloat(),
                bounds.outline.left, bounds.outline.right, bounds.outline.top, bounds.outline.bottom,
                visibleTop = bounds.muscles.top,
                visibleBottom = bounds.muscles.bottom,
                referenceHeight = referenceHeight,
                stretchVertically = true,
            )
            assertEquals(original.scaleX, filled.scaleX, 0.0001f)
            assertEquals(original.offsetX, filled.offsetX, 0.0001f)
            assertEquals(height * 0.05f, filled.offsetY + bounds.muscles.top * filled.scaleY, 0.001f)
            assertEquals(height * 0.95f, filled.offsetY + bounds.muscles.bottom * filled.scaleY, 0.001f)
        }
    }

    @Test
    fun croppingMuscleBoundsPreservesWidthAndHeightConstrainedAnatomyScale() {
        val bounds = MuscleMapBounds(Rect(192f, 62f, 832f, 1428f), Rect(220f, 250f, 804f, 1300f))
        listOf(353 to 972f, 900 to 420f).forEach { (width, referenceHeight) ->
            val original = muscleMapTransform(
                width.toFloat(), referenceHeight,
                bounds.outline.left, bounds.outline.right, bounds.outline.top, bounds.outline.bottom,
            )
            val height = croppedMuscleMapHeight(bounds, width, referenceHeight)
            val cropped = muscleMapTransform(
                width.toFloat(), height.toFloat(),
                bounds.outline.left, bounds.outline.right, bounds.outline.top, bounds.outline.bottom,
                visibleTop = bounds.muscles.top,
                visibleBottom = bounds.muscles.bottom,
                referenceHeight = referenceHeight,
            )
            assertTrue(height < referenceHeight)
            assertEquals(original.scaleX, cropped.scaleX, 0.0001f)
            assertEquals(original.scaleY, cropped.scaleY, 0.0001f)
            assertEquals(original.offsetX, cropped.offsetX, 0.0001f)
            assertTrue(cropped.offsetY + bounds.muscles.top * cropped.scaleY >= -0.01f)
            assertTrue(cropped.offsetY + bounds.muscles.bottom * cropped.scaleY <= height + 0.01f)
        }
    }

    @Test
    fun colourTransitionIsVisibleButFastEnoughForScrubbing() {
        assertTrue(MUSCLE_COLOR_TRANSITION_MILLIS in 30..40)
    }

    @Test
    fun bodyTransformIsTenPercentTallerAndFitsMeasuredBounds() {
        val transform = muscleMapTransform(
            canvasWidth = 560f,
            canvasHeight = 1_000f,
            contentLeft = 200f,
            contentRight = 824f,
            contentTop = 70f,
            contentBottom = 1_420f,
        )
        assertEquals(MUSCLE_MAP_VERTICAL_STRETCH, transform.scaleY / transform.scaleX, 0.001f)
        assertTrue(transform.offsetX + 200f * transform.scaleX >= -0.01f)
        assertTrue(transform.offsetX + 824f * transform.scaleX <= 560.01f)
        val renderedTop = transform.offsetY + 70f * transform.scaleY
        val renderedBottom = transform.offsetY + 1_420f * transform.scaleY
        assertTrue("top=$renderedTop transform=$transform", renderedTop >= -0.1f)
        assertTrue("bottom=$renderedBottom transform=$transform", renderedBottom <= 1_000.1f)
    }

    @Test
    fun heightConstrainedBodyTransformFitsFullAnatomyBounds() {
        val transform = muscleMapTransform(
            canvasWidth = 900f,
            canvasHeight = 420f,
            contentLeft = 200f,
            contentRight = 824f,
            contentTop = 70f,
            contentBottom = 1_420f,
        )
        assertTrue(transform.offsetY + 70f * transform.scaleY >= -0.01f)
        assertTrue(transform.offsetY + 1_420f * transform.scaleY <= 420.01f)
        assertEquals(MUSCLE_MAP_VERTICAL_STRETCH, transform.scaleY / transform.scaleX, 0.001f)
    }
}

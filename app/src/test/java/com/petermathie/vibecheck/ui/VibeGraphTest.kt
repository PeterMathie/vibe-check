package com.petermathie.vibecheck.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.petermathie.vibecheck.ui.components.GraphDomain
import com.petermathie.vibecheck.ui.components.graphDomain
import com.petermathie.vibecheck.ui.components.graphPoint
import com.petermathie.vibecheck.ui.components.graphBarPoint
import com.petermathie.vibecheck.ui.components.graphGridLines
import com.petermathie.vibecheck.ui.components.graphTooltipPlacement
import com.petermathie.vibecheck.ui.components.nearestGraphIndex
import com.petermathie.vibecheck.ui.theme.VibeVisualStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VibeGraphTest {
    @Test
    fun domainIgnoresGapsAndRejectsNoFiniteData() {
        assertEquals(GraphDomain(2.0, 8.0), graphDomain(listOf(Double.NaN, 8.0, 2.0)))
        assertNull(graphDomain(listOf(Double.NaN, Double.POSITIVE_INFINITY)))
    }

    @Test
    fun onePointIsCenteredAndNearestSelectionIsClamped() {
        val point = graphPoint(0, 5.0, 1, 200f, 100f, GraphDomain(0.0, 10.0), 12f)
        assertEquals(100f, point.x)
        assertTrue(point.y in 12f..88f)
        assertEquals(0, nearestGraphIndex(-20f, 200f, 3))
        assertEquals(2, nearestGraphIndex(300f, 200f, 3))
    }

    @Test
    fun fixedDomainPreservesBarChartScale() {
        assertEquals(GraphDomain(0.0, 10.0), graphDomain(listOf(4.0), 0.0..10.0))
        assertEquals(
            Offset(50f, 50f),
            graphBarPoint(0, 5.0, 1, 100f, 100f, GraphDomain(0.0, 10.0), 10f),
        )
    }

    @Test
    fun retroGridAddsMinorLinesWithoutChangingPlotBounds() {
        val standard = graphGridLines(300f, 140f, 12f, VibeVisualStyle.STANDARD)
        val retro = graphGridLines(300f, 140f, 12f, VibeVisualStyle.RETRO_FUTURE)
        assertEquals(9, standard.size)
        assertEquals(34, retro.size)
        assertEquals(10, retro.count { it.major })
        assertTrue(retro.all { line ->
            line.start.x in 12f..288f &&
                line.end.x in 12f..288f &&
                line.start.y in 12f..128f &&
                line.end.y in 12f..128f
        })
    }

    @Test
    fun tooltipTracksPointsAndClampsAtEveryPlotEdge() {
        val viewport = IntSize(300, 140)
        val tooltip = IntSize(100, 48)
        val center = graphTooltipPlacement(Offset(150f, 70f), viewport, tooltip, 6)
        assertEquals(100, center.offset.x)
        assertEquals(16, center.offset.y)
        assertTrue(center.abovePoint)

        val top = graphTooltipPlacement(Offset(150f, 2f), viewport, tooltip, 6)
        assertEquals(8, top.offset.y)
        assertTrue(!top.abovePoint)

        val left = graphTooltipPlacement(Offset(0f, 70f), viewport, tooltip, 6)
        assertEquals(0, left.offset.x)
        val right = graphTooltipPlacement(Offset(300f, 70f), viewport, tooltip, 6)
        assertEquals(200, right.offset.x)

        val bottom = graphTooltipPlacement(Offset(150f, 138f), viewport, tooltip, 6)
        assertEquals(84, bottom.offset.y)
        assertTrue(bottom.abovePoint)
        listOf(center, top, left, right, bottom).forEach {
            assertTrue(it.offset.x in 0..200)
            assertTrue(it.offset.y in 0..92)
        }
    }
}

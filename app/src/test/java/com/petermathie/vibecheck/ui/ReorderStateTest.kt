package com.petermathie.vibecheck.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReorderStateTest {
    @Test
    fun viewportMovementDoesNotAnimatePlacementButStillUpdatesDragGeometry() {
        val state = ReorderState(listOf("first", "second")) { _, _, _ -> }
        assertEquals(0f, state.registerItemBounds("first", 0f, 100f, 100f, 180f, 0), 0f)
        assertEquals(0f, state.registerItemBounds("first", 0f, 60f, 100f, 140f, 0), 0f)
        assertEquals(0f, state.registerItemBounds("first", 0f, 120f, 100f, 200f, 0), 0f)

        state.begin("first", 144f)
        state.dragBy(10f, 48f)
        assertEquals(10f, state.dragOffset("first"), 0f)
        assertEquals(120f, state.dragStartBounds()!!.top, 0f)
    }

    @Test
    fun indexChangesAnimateNeighbourPlacementButNotTheFollowingScroll() {
        val state = ReorderState(listOf("first", "second", "third")) { _, _, _ -> }
        state.registerItemBounds("second", 0f, 180f, 100f, 260f, 1)
        assertEquals(80f, state.registerItemBounds("second", 0f, 100f, 100f, 180f, 0), 0f)
        assertEquals(0f, state.registerItemBounds("second", 0f, 60f, 100f, 140f, 0), 0f)
        assertEquals(-160f, state.registerItemBounds("second", 0f, 220f, 100f, 300f, 2), 0f)
    }

    @Test
    fun reverseMovementNearBoundaryDoesNotOscillateOrder() {
        var persistedMove: Triple<Any, Int, Int>? = null
        val state = ReorderState(listOf("first", "second")) { key, from, to ->
            persistedMove = Triple(key, from, to)
        }

        state.registerItemBounds("first", 0f, 80f)
        state.registerItemBounds("second", 80f, 160f)
        state.begin("first", 20f)
        state.dragBy(110f, 48f)
        assertEquals(listOf("second", "first"), state.ordered(listOf("first", "second")) { it })

        state.registerItemBounds("second", 0f, 80f)
        state.registerItemBounds("first", 80f, 160f)
        state.dragBy(-45f, 48f)
        assertEquals(listOf("second", "first"), state.ordered(listOf("first", "second")) { it })

        state.end()
        assertEquals(Triple("first", 0, 1), persistedMove)
    }

    @Test
    fun grabPointRemainsAnchoredAcrossMultipleCrossingsAndRelayout() {
        val moves = mutableListOf<Triple<Any, Int, Int>>()
        val state = ReorderState(listOf("first", "second", "third")) { key, from, to ->
            moves += Triple(key, from, to)
        }
        state.registerItemBounds("first", 0f, 80f)
        state.registerItemBounds("second", 80f, 160f)
        state.registerItemBounds("third", 160f, 240f)
        state.begin("first", 24f)

        assertEquals(2, state.dragBy(200f, 48f))
        assertEquals(listOf("second", "third", "first"), state.ordered(listOf("first", "second", "third")) { it })
        assertEquals(200f, state.dragOffset("first"), 0.01f)

        state.registerItemBounds("second", 0f, 80f)
        state.registerItemBounds("third", 80f, 160f)
        state.registerItemBounds("first", 160f, 240f)
        val visualGrabPoint = 160f + state.dragOffset("first") + 24f
        assertEquals(224f, visualGrabPoint, 0.01f)

        assertEquals(true, state.end())
        assertEquals(listOf(Triple("first", 0, 2)), moves)
    }

    @Test
    fun cancellationRestoresOrderWithoutPersistence() {
        var persisted = false
        val state = ReorderState(listOf("first", "second")) { _, _, _ -> persisted = true }
        state.registerItemBounds("first", 0f, 80f)
        state.registerItemBounds("second", 80f, 160f)
        state.begin("first", 20f)
        state.dragBy(120f, 48f)

        state.cancel()

        assertEquals(listOf("first", "second"), state.ordered(listOf("first", "second")) { it })
        assertFalse(persisted)
    }

    @Test
    fun edgeScrollVelocityIsBoundedSmoothAndZeroOutsideEdges() {
        assertEquals(0f, edgeAutoScrollVelocity(200f, 0f, 400f, 80f, 600f), 0f)
        assertEquals(-600f, edgeAutoScrollVelocity(0f, 0f, 400f, 80f, 600f), 0.01f)
        assertEquals(600f, edgeAutoScrollVelocity(400f, 0f, 400f, 80f, 600f), 0.01f)
        val nearBottom = edgeAutoScrollVelocity(360f, 0f, 400f, 80f, 600f)
        val atBottom = edgeAutoScrollVelocity(400f, 0f, 400f, 80f, 600f)
        assertEquals(true, nearBottom in 0f..atBottom)
    }

    @Test
    fun grabPointRemainsAnchoredWhenAutoScrollChangesLayoutCoordinates() {
        val state = ReorderState(listOf("first", "second", "third")) { _, _, _ -> }
        state.registerItemBounds("first", 100f, 180f)
        state.registerItemBounds("second", 180f, 260f)
        state.registerItemBounds("third", 260f, 340f)
        state.begin("first", 124f)
        state.dragBy(180f, 48f)

        state.registerItemBounds("first", 120f, 200f)
        val visualGrabPoint = 120f + state.dragOffset("first") + 24f

        assertEquals(304f, visualGrabPoint, 0.01f)
    }
}

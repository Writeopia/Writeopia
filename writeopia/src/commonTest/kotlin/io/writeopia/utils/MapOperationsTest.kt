package io.writeopia.utils

import io.writeopia.sdk.utils.collections.lastKeyOrNull
import io.writeopia.sdk.utils.collections.sortedMutableMapOf
import io.writeopia.sdk.utils.iterables.addElementAfterPosition
import io.writeopia.sdk.utils.iterables.addElementInPosition
import io.writeopia.sdk.utils.iterables.addElementsInPosition
import io.writeopia.sdk.utils.iterables.keyAfter
import io.writeopia.sdk.utils.iterables.keyBefore
import io.writeopia.sdk.utils.iterables.mergeSortedMaps
import io.writeopia.sdk.utils.iterables.nextKey
import io.writeopia.sdk.utils.iterables.previousKey
import io.writeopia.sdk.utils.iterables.removeBy
import io.writeopia.sdk.utils.iterables.removeElementInPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MapOperationsTest {

    private val map = mapOf(0.0 to "title", 1.0 to "a", 1.5 to "b", 2.0 to "c")

    @Test
    fun `the neighbours of a position come from the keys`() {
        assertEquals(1.0, map.previousKey(1.5))
        assertEquals(2.0, map.nextKey(1.5))
        assertEquals(1.5, map.previousKey(1.7))
        assertEquals(2.0, map.nextKey(1.7))
        assertNull(map.previousKey(0.0))
        assertNull(map.nextKey(2.0))
    }

    @Test
    fun `a free position is kept, an occupied one gives half way to the previous key`() {
        assertEquals(1.7, map.keyBefore(1.7))
        assertEquals(1.25, map.keyBefore(1.5))
        assertEquals(-1.0, map.keyBefore(0.0))
    }

    @Test
    fun `the position after a key is half way to the next one`() {
        assertEquals(1.25, map.keyAfter(1.0))
        assertEquals(1.75, map.keyAfter(1.5))
        assertEquals(3.0, map.keyAfter(2.0))
    }

    @Test
    fun `adding at an occupied position moves nothing else`() {
        val result = map.addElementInPosition("new", 1.5)

        assertEquals(listOf(0.0, 1.0, 1.25, 1.5, 2.0), result.keys.toList())
        assertEquals("new", result[1.25])
        assertEquals("b", result[1.5])
    }

    @Test
    fun `adding at a free position uses it`() {
        val result = map.addElementInPosition("new", 5.0)

        assertEquals(listOf(0.0, 1.0, 1.5, 2.0, 5.0), result.keys.toList())
        assertEquals("new", result[5.0])
    }

    @Test
    fun `adding after a position lands between it and the next one`() {
        val result = map.addElementAfterPosition("new", 1.0)

        assertEquals(listOf(0.0, 1.0, 1.25, 1.5, 2.0), result.keys.toList())
        assertEquals("new", result[1.25])
    }

    @Test
    fun `several elements keep their order before the position`() {
        val result = map.addElementsInPosition(listOf("x", "y", "z"), 1.5)

        assertEquals(listOf("title", "a", "x", "y", "z", "b", "c"), result.values.toList())
        assertEquals("b", result[1.5])
    }

    @Test
    fun `removing keeps the keys of the others`() {
        assertEquals(listOf(0.0, 1.0, 2.0), map.removeElementInPosition(1.5).keys.toList())
        assertEquals(listOf(0.0, 1.5, 2.0), map.removeBy { it == "a" }.keys.toList())
        assertEquals(map, map.removeElementInPosition(7.0))
    }

    @Test
    fun `merging adds each element at its key or before the element there`() {
        val result = map.mergeSortedMaps(mapOf(1.0 to "x", 3.0 to "y"))

        assertEquals(listOf("title", "x", "a", "b", "c", "y"), result.values.toList())
        assertEquals("x", result[0.5])
        assertEquals("y", result[3.0])
    }
}

class NavigableKeysTest {

    @Test
    fun `the navigable lookups agree with a scan on sorted and unsorted maps`() {
        val plain = mapOf(2.0 to "c", 0.0 to "title", 1.5 to "b", 1.0 to "a")
        val sorted = sortedMutableMapOf<Double, String>().apply { putAll(plain) }

        listOf(plain, sorted).forEach { map ->
            assertEquals(2.0, map.lastKeyOrNull())
            assertEquals(1.0, map.previousKey(1.5))
            assertEquals(2.0, map.nextKey(1.5))
            assertEquals(1.5, map.previousKey(1.7))
            assertNull(map.previousKey(0.0))
            assertNull(map.nextKey(2.0))
        }
        assertNull(emptyMap<Double, String>().lastKeyOrNull())
    }
}

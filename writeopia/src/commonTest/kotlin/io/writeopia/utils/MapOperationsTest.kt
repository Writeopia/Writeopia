package io.writeopia.utils

import io.writeopia.sdk.utils.collections.lastKeyOrNull
import io.writeopia.sdk.utils.collections.sortedMutableMapOf
import io.writeopia.sdk.utils.iterables.POSITION_GAP
import io.writeopia.sdk.utils.iterables.insertAfter
import io.writeopia.sdk.utils.iterables.insertAll
import io.writeopia.sdk.utils.iterables.insertBefore
import io.writeopia.sdk.utils.iterables.makeRoomAfter
import io.writeopia.sdk.utils.iterables.nextKey
import io.writeopia.sdk.utils.iterables.previousKey
import io.writeopia.sdk.utils.iterables.removeBy
import io.writeopia.sdk.utils.iterables.removeElementInPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Positions are whole numbers [POSITION_GAP] apart when written in order; an insert between
 * two steps takes a whole number between them, and spreads the following steps out when
 * there is none left.
 */
class MapOperationsTest {

    private val gap = POSITION_GAP
    private val half = gap / 2
    private val map = mapOf(0L to "title", gap to "a", gap + half to "b", 2 * gap to "c")

    @Test
    fun `the neighbours of a position come from the keys`() {
        assertEquals(gap, map.previousKey(gap + half))
        assertEquals(2 * gap, map.nextKey(gap + half))
        assertEquals(gap + half, map.previousKey(gap + half + 1))
        assertEquals(2 * gap, map.nextKey(gap + half + 1))
        assertNull(map.previousKey(0L))
        assertNull(map.nextKey(2 * gap))
    }

    @Test
    fun `inserting after the last element goes one gap further`() {
        val result = map.insertAfter("new", 2 * gap)

        assertEquals(3 * gap, result.key)
        assertEquals("new", result.map[3 * gap])
        assertTrue(result.moved.isEmpty())
    }

    @Test
    fun `inserting between two elements takes the middle, nothing else moves`() {
        val result = map.insertAfter("new", gap)

        assertEquals(gap + half / 2, result.key)
        assertEquals(listOf(0L, gap, gap + half / 2, gap + half, 2 * gap), result.map.keys.toList())
        assertTrue(result.moved.isEmpty())
    }

    @Test
    fun `inserting after a step with plenty of room goes one gap further, like writing in order`() {
        val wide = mapOf(0L to "title", 10 * gap to "far")

        val result = wide.insertAfter(listOf("x", "y"), 0L)

        assertEquals(listOf(gap, 2 * gap), result.keys)
    }

    @Test
    fun `inserting before an occupied position takes the middle to the previous element`() {
        val result = map.insertBefore("new", gap + half)

        assertEquals(gap + half / 2, result.key)
        assertEquals("b", result.map[gap + half])
    }

    @Test
    fun `inserting at a free position uses it`() {
        val result = map.insertBefore("new", 5 * gap)

        assertEquals(5 * gap, result.key)
        assertEquals(listOf(0L, gap, gap + half, 2 * gap, 5 * gap), result.map.keys.toList())
    }

    @Test
    fun `inserting before the first element goes one gap below it`() {
        val result = map.insertBefore(listOf("x", "y"), 0L)

        assertEquals(listOf(-2 * gap, -gap), result.keys)
        assertEquals("title", result.map[0L])
    }

    @Test
    fun `several elements keep their order before the position`() {
        val result = map.insertBefore(listOf("x", "y", "z"), gap + half)

        assertEquals(listOf("title", "a", "x", "y", "z", "b", "c"), result.map.values.toList())
        assertEquals("b", result.map[gap + half])
    }

    @Test
    fun `when no whole number is left the following elements are spread out`() {
        val packed = mapOf(0L to "title", 1L to "a", 2L to "b", 3L to "c")

        val result = packed.insertAfter("new", 1L)

        assertEquals(listOf("title", "a", "new", "b", "c"), result.map.values.toList())
        assertEquals("a", result.map[1L])
        // b and c moved out of the way, and are reported so they get saved
        assertEquals(listOf(1L + 2 * gap, 1L + 3 * gap), result.moved)
        assertEquals(1L + gap, result.key)
    }

    @Test
    fun `spreading out moves as few elements as there is room for`() {
        // No room between 10 and 11; 12 is far: only 11 moves
        val packed = sortedMutableMapOf<Long, String>().apply {
            this[10L] = "a"
            this[11L] = "b"
            this[12L + 5 * gap] = "c"
        }

        val moved = packed.makeRoomAfter(10L, 1)

        assertEquals(1, moved.size)
        assertEquals("b", packed[moved.single()])
        assertTrue(moved.single() > 10L && moved.single() < 12L + 5 * gap)
        assertEquals(listOf("a", "b", "c"), packed.values.toList())
    }

    @Test
    fun `inserting before a packed element spreads it out with the ones after`() {
        val packed = mapOf(0L to "title", 1L to "a", 2L to "b")

        val result = packed.insertBefore("new", 2L)

        assertEquals(listOf("title", "a", "new", "b"), result.map.values.toList())
        assertTrue(result.moved.isNotEmpty())
    }

    @Test
    fun `removing keeps the keys of the others`() {
        assertEquals(listOf(0L, gap, 2 * gap), map.removeElementInPosition(gap + half).keys.toList())
        assertEquals(listOf(0L, gap + half, 2 * gap), map.removeBy { it == "a" }.keys.toList())
        assertEquals(map, map.removeElementInPosition(7L))
    }

    @Test
    fun `merging adds each element at its key or before the element there`() {
        val result = map.insertAll(mapOf(gap to "x", 3 * gap to "y"))

        assertEquals(listOf("title", "x", "a", "b", "c", "y"), result.map.values.toList())
        assertEquals("x", result.map[half])
        assertEquals("y", result.map[3 * gap])
    }
}

class NavigableKeysTest {

    @Test
    fun `the navigable lookups agree with a scan on sorted and unsorted maps`() {
        val plain = mapOf(20L to "c", 0L to "title", 15L to "b", 10L to "a")
        val sorted = sortedMutableMapOf<Long, String>().apply { putAll(plain) }

        listOf(plain, sorted).forEach { map ->
            assertEquals(20L, map.lastKeyOrNull())
            assertEquals(10L, map.previousKey(15L))
            assertEquals(20L, map.nextKey(15L))
            assertEquals(15L, map.previousKey(17L))
            assertNull(map.previousKey(0L))
            assertNull(map.nextKey(20L))
        }
        assertNull(emptyMap<Long, String>().lastKeyOrNull())
    }
}

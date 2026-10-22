package io.writeopia.utils

import io.writeopia.sdk.utils.collections.lastKeyOrNull
import io.writeopia.sdk.utils.collections.sortedMutableMapOf
import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.sdk.utils.iterables.insertAfter
import io.writeopia.sdk.utils.iterables.insertAll
import io.writeopia.sdk.utils.iterables.insertBefore
import io.writeopia.sdk.utils.iterables.nextKey
import io.writeopia.sdk.utils.iterables.previousKey
import io.writeopia.sdk.utils.iterables.removeBy
import io.writeopia.sdk.utils.iterables.removeElementInPosition
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Positions are ranks: between any two there is always another, so nothing else ever moves. */
class MapOperationsTest {

    private val r0 = Rank.at(0)
    private val r1 = Rank.at(1)
    private val r2 = Rank.at(2)
    private val r1b = Rank.between(r1, r2)
    private val map = mapOf(r0 to "title", r1 to "a", r1b to "b", r2 to "c")

    @BeforeTest
    fun exactKeys() {
        Rank.jitter = false
    }

    @AfterTest
    fun randomKeys() {
        Rank.jitter = true
    }

    @Test
    fun `the neighbours of a position come from the keys`() {
        assertEquals(r1, map.previousKey(r1b))
        assertEquals(r2, map.nextKey(r1b))
        assertEquals(r1b, map.previousKey(r1b + "1"))
        assertEquals(r2, map.nextKey(r1b + "1"))
        assertNull(map.previousKey(r0))
        assertNull(map.nextKey(r2))
    }

    @Test
    fun `inserting after the last element gets the next integer`() {
        val result = map.insertAfter("new", r2)

        assertEquals(Rank.at(3), result.key)
        assertEquals("new", result.map[Rank.at(3)])
    }

    @Test
    fun `inserting between two elements takes a key between them, nothing else moves`() {
        val result = map.insertAfter("new", r1)

        assertTrue(result.key > r1 && result.key < r1b)
        assertEquals(listOf(r0, r1, result.key, r1b, r2), result.map.keys.toList())
    }

    @Test
    fun `inserting after a step with room before the next takes the next integer`() {
        val wide = mapOf(r0 to "title", Rank.at(10) to "far")

        val result = wide.insertAfter(listOf("x", "y"), r0)

        assertEquals(listOf(r1, r2), result.keys)
    }

    @Test
    fun `inserting before an occupied position goes between it and the previous element`() {
        val result = map.insertBefore("new", r1b)

        assertTrue(result.key > r1 && result.key < r1b)
        assertEquals("b", result.map[r1b])
    }

    @Test
    fun `inserting at a free position uses it`() {
        val free = Rank.at(5)
        val result = map.insertBefore("new", free)

        assertEquals(free, result.key)
        assertEquals(listOf(r0, r1, r1b, r2, free), result.map.keys.toList())
    }

    @Test
    fun `inserting before the first element goes below it`() {
        val result = map.insertBefore(listOf("x", "y"), r0)

        assertEquals(listOf("x", "y", "title", "a", "b", "c"), result.map.values.toList())
        assertEquals("title", result.map[r0])
    }

    @Test
    fun `several elements keep their order before the position`() {
        val result = map.insertBefore(listOf("x", "y", "z"), r1b)

        assertEquals(listOf("title", "a", "x", "y", "z", "b", "c"), result.map.values.toList())
        assertEquals("b", result.map[r1b])
    }

    @Test
    fun `there is always room between two neighbours`() {
        var current = map
        repeat(200) {
            current = current.insertAfter("line $it", r1).map
        }

        assertEquals(204, current.size)
        assertEquals(listOf("title", "a"), current.values.take(2))
        assertEquals(listOf("b", "c"), current.values.toList().takeLast(2))
        assertEquals(current.keys.sorted(), current.keys.toList())
    }

    @Test
    fun `removing keeps the keys of the others`() {
        assertEquals(listOf(r0, r1, r2), map.removeElementInPosition(r1b).keys.toList())
        assertEquals(listOf(r0, r1b, r2), map.removeBy { it == "a" }.keys.toList())
        assertEquals(map, map.removeElementInPosition(Rank.at(7)))
    }

    @Test
    fun `merging adds each element at its key or before the element there`() {
        val result = map.insertAll(mapOf(r1 to "x", Rank.at(3) to "y"))

        assertEquals(listOf("title", "x", "a", "b", "c", "y"), result.map.values.toList())
        assertEquals("y", result.map[Rank.at(3)])
    }
}

class RankTest {

    @BeforeTest
    fun exactKeys() {
        Rank.jitter = false
    }

    @AfterTest
    fun randomKeys() {
        Rank.jitter = true
    }

    @Test
    fun `ranks written in order are consecutive integers`() {
        assertEquals("0001000000", Rank.FIRST)
        assertEquals("0001000001", Rank.after(Rank.FIRST))
        assertEquals(Rank.at(2), Rank.after(Rank.at(1)))
        assertEquals("0000999999", Rank.before(Rank.FIRST))
    }

    @Test
    fun `a rank between two is strictly between them`() {
        val a = Rank.at(1)
        val b = Rank.at(2)
        val middle = Rank.between(a, b)
        assertTrue(a < middle && middle < b)
        assertEquals("0001000001i", middle)

        val closer = Rank.between(middle, b)
        assertTrue(middle < closer && closer < b)

        val back = Rank.between(a, middle)
        assertTrue(a < back && back < middle)
    }

    @Test
    fun `a rank between far apart integers is the next integer`() {
        assertEquals(Rank.at(1), Rank.between(Rank.at(0), Rank.at(5)))
    }

    @Test
    fun `fractions never end with a zero and keep growing room`() {
        var low = Rank.at(0)
        val high = Rank.at(1)
        repeat(500) {
            val next = Rank.between(low, high)
            assertTrue(low < next && next < high, "$low < $next < $high")
            assertTrue(!next.endsWith("0"))
            low = next
        }
    }

    @Test
    fun `jitter keeps the key between its neighbours`() {
        Rank.jitter = true
        repeat(200) {
            val key = Rank.between(Rank.at(1), Rank.at(2))
            assertTrue(Rank.at(1) < key && key < Rank.at(2))
            val tight = Rank.between("0001000001y", "0001000001z")
            assertTrue("0001000001y" < tight && tight < "0001000001z", tight)
        }
    }
}

class NavigableKeysTest {

    @Test
    fun `the navigable lookups agree with a scan on sorted and unsorted maps`() {
        val plain = mapOf("c" to "c", "a" to "title", "bb" to "b", "b" to "a")
        val sorted = sortedMutableMapOf<String, String>().apply { putAll(plain) }

        listOf(plain, sorted).forEach { map ->
            assertEquals("c", map.lastKeyOrNull())
            assertEquals("b", map.previousKey("bb"))
            assertEquals("c", map.nextKey("bb"))
            assertEquals("bb", map.previousKey("bc"))
            assertNull(map.previousKey("a"))
            assertNull(map.nextKey("c"))
        }
        assertNull(emptyMap<String, String>().lastKeyOrNull())
    }
}

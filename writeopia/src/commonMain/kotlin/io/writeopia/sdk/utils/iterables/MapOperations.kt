package io.writeopia.sdk.utils.iterables

import io.writeopia.sdk.utils.collections.higherKeyOrNull
import io.writeopia.sdk.utils.collections.lowerKeyOrNull
import io.writeopia.sdk.utils.collections.sortedMutableMapOf
import io.writeopia.sdk.utils.collections.toSortedMutableMap

/*
 * The keys of a story map are sort keys, not list indexes: a document is the map read in key
 * order, and the key of a step is also its position in the database. The keys are whole
 * numbers, [POSITION_GAP] apart when the document is written in order: a step added at the end
 * goes [POSITION_GAP] after the last one, a step added between two others goes half way between
 * them. Nothing here renumbers the whole document. When two neighbours have no whole number
 * left between them, the few steps after the gap are spread out again ([makeRoomAfter]) and
 * reported, so the caller saves them.
 */

/** The distance between the positions of two steps written one after the other. */
const val POSITION_GAP = 10_000L

/** When steps are spread out again, at least this far apart when there is room for it. */
private const val PREFERRED_ROOM = POSITION_GAP / 8

/** At most this many steps are spread out again at once, when there is room before them. */
private const val MAX_RESPACED = 32

/**
 * Where elements were put: the map, the [keys] of the elements, in order, and the new keys of
 * the elements [moved] to make room for them (their old keys are gone from the map).
 */
class Insertion<T>(
    val map: MutableMap<Long, T>,
    val keys: List<Long>,
    val moved: List<Long>
) {
    /** The key of the first (or only) element. */
    val key: Long get() = keys.first()
}

/** The largest key below [position], if any. O(log n) on a sorted map on JVM. */
fun <T> Map<Long, T>.previousKey(position: Long): Long? = lowerKeyOrNull(position)

/** The smallest key above [position], if any. O(log n) on a sorted map on JVM. */
fun <T> Map<Long, T>.nextKey(position: Long): Long? = higherKeyOrNull(position)

/**
 * Keys for [count] elements between [position] (excluded) and [next] (excluded, none when
 * null), in order. With plenty of room they go [POSITION_GAP] apart from [position], like
 * steps written in order; otherwise they are spread evenly. There must be room for them: see
 * [hasRoomAfter].
 */
private fun keysBetween(position: Long, next: Long?, count: Int): List<Long> {
    if (count == 0) return emptyList()
    val step = when {
        next == null -> POSITION_GAP
        next - position > (count + 1) * POSITION_GAP -> POSITION_GAP
        else -> (next - position) / (count + 1)
    }
    return (1..count).map { index -> position + index * step }
}

/** Whether [count] whole numbers fit between [position] and [next] (none when null). */
private fun hasRoomAfter(position: Long, next: Long?, count: Int): Boolean =
    next == null || (next - position) / (count + 1) >= 1L

/**
 * Spreads out the elements after [anchor] so that [count] new ones fit between [anchor] and
 * the first of them, in place. As few elements as possible move: the ones right after
 * [anchor], until the room between [anchor] and the following key allows at least
 * [PREFERRED_ROOM] between each, or up to [MAX_RESPACED] of them when a smaller room does.
 *
 * @return the new keys of the elements moved, in order; empty when nothing had to move.
 */
fun <T> MutableMap<Long, T>.makeRoomAfter(anchor: Long, count: Int): List<Long> {
    val taken = mutableListOf<Long>()
    var next = higherKeyOrNull(anchor)
    var step: Long

    while (true) {
        val slots = taken.size + count + 1
        if (next == null) {
            step = POSITION_GAP
            break
        }
        step = (next - anchor) / slots
        if (step >= 1L && (step >= PREFERRED_ROOM || taken.size >= MAX_RESPACED)) break
        taken += next
        next = higherKeyOrNull(next)
    }

    if (taken.isEmpty()) return emptyList()

    val elements = taken.map { key -> remove(key)!! }
    val newKeys = taken.indices.map { index -> anchor + (count + index + 1) * step }
    elements.forEachIndexed { index, element -> this[newKeys[index]] = element }
    return newKeys
}

/**
 * Puts [elements], in order, right after the element at [position] ([position] itself need
 * not be a key). The elements after are spread out first when there is no room: see
 * [makeRoomAfter]. The result is sorted by key.
 */
fun <T> Map<Long, T>.insertAfter(elements: List<T>, position: Long): Insertion<T> {
    val map = toSortedMutableMap()
    if (elements.isEmpty()) return Insertion(map, emptyList(), emptyList())

    val moved = if (hasRoomAfter(position, map.higherKeyOrNull(position), elements.size)) {
        emptyList()
    } else {
        map.makeRoomAfter(position, elements.size)
    }
    val keys = keysBetween(position, map.higherKeyOrNull(position), elements.size)
    elements.forEachIndexed { index, element -> map[keys[index]] = element }
    return Insertion(map, keys, moved)
}

/** See [insertAfter]. */
fun <T> Map<Long, T>.insertAfter(element: T, position: Long): Insertion<T> =
    insertAfter(listOf(element), position)

/**
 * Puts [elements], in order, at [position] when it is free (the last of them takes it, the
 * others go before), otherwise right before the element at [position]: between the previous
 * element and that one, spreading the following elements out first when there is no room.
 * The result is sorted by key.
 */
fun <T> Map<Long, T>.insertBefore(elements: List<T>, position: Long): Insertion<T> {
    val map = toSortedMutableMap()
    if (elements.isEmpty()) return Insertion(map, emptyList(), emptyList())

    val before: List<T>
    val lastKeys: List<Long>
    if (map.containsKey(position)) {
        before = elements
        lastKeys = emptyList()
    } else {
        map[position] = elements.last()
        before = elements.dropLast(1)
        lastKeys = listOf(position)
    }
    if (before.isEmpty()) return Insertion(map, lastKeys, emptyList())

    val previous = map.lowerKeyOrNull(position)
    if (previous == null) {
        // Nothing before: the elements go [POSITION_GAP] apart, up to [position]
        val keys = before.indices.map { index -> position - (before.size - index) * POSITION_GAP }
        before.forEachIndexed { index, element -> map[keys[index]] = element }
        return Insertion(map, keys + lastKeys, emptyList())
    }

    val moved = if (hasRoomAfter(previous, position, before.size)) {
        emptyList()
    } else {
        // The element at [position] moves too, with the ones after it
        map.makeRoomAfter(previous, before.size)
    }
    val keys = keysBetween(previous, map.higherKeyOrNull(previous), before.size)
    before.forEachIndexed { index, element -> map[keys[index]] = element }
    return Insertion(map, keys + lastKeys, moved)
}

/** See [insertBefore]. */
fun <T> Map<Long, T>.insertBefore(element: T, position: Long): Insertion<T> =
    insertBefore(listOf(element), position)

/**
 * Adds every element of [newMap] at its key, or before the element already at that key (see
 * [insertBefore]), in key order. The result is sorted by key.
 */
fun <T> Map<Long, T>.insertAll(newMap: Map<Long, T>): Insertion<T> {
    var map = toSortedMutableMap()
    val keys = mutableListOf<Long>()
    val moved = mutableListOf<Long>()
    newMap.entries.sortedBy { entry -> entry.key }.forEach { (position, element) ->
        val insertion = map.insertBefore(element, position)
        map = insertion.map
        keys += insertion.keys
        moved += insertion.moved
    }
    return Insertion(map, keys, moved)
}

/** Removes the element at [position], if any. The other elements keep their keys. */
fun <T> Map<Long, T>.removeElementInPosition(position: Long): MutableMap<Long, T> =
    toSortedMutableMap().apply { remove(position) }

/** Removes the elements matching [predicate]. The other elements keep their keys. */
fun <T> Map<Long, T>.removeBy(predicate: (T) -> Boolean): Map<Long, T> =
    filterNot { (_, element) -> predicate(element) }.toSortedMutableMap()

/** The values transformed, in a new map sorted by key. */
inline fun <T, R> Map<Long, T>.mapValuesSorted(transform: (Map.Entry<Long, T>) -> R): MutableMap<Long, R> {
    val result = sortedMutableMapOf<Long, R>()
    entries.forEach { entry -> result[entry.key] = transform(entry) }
    return result
}

/** This map sorted by key, as a new map. */
fun <T> Map<Long, T>.sortedByKey(): Map<Long, T> =
    sortedMutableMapOf<Long, T>().apply { putAll(this@sortedByKey) }

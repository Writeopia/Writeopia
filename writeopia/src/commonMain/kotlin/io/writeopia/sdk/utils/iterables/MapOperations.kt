package io.writeopia.sdk.utils.iterables

import io.writeopia.sdk.utils.collections.higherKeyOrNull
import io.writeopia.sdk.utils.collections.lowerKeyOrNull
import io.writeopia.sdk.utils.collections.sortedMutableMapOf
import io.writeopia.sdk.utils.collections.toSortedMutableMap

/*
 * The keys of a story map are sort keys, not list indexes: a document is the map read in key
 * order, and the key of a step is also its position in the database. The keys are ranks (see
 * [Rank]): between any two of them there is always another, so adding, moving or removing a
 * step never changes the key of any other step.
 */

/** Where elements were put: the map and the [keys] of the elements, in order. */
class Insertion<T>(
    val map: MutableMap<String, T>,
    val keys: List<String>
) {
    /** The key of the first (or only) element. */
    val key: String get() = keys.first()
}

/** The largest key below [position], if any. O(log n) on a sorted map on JVM. */
fun <T> Map<String, T>.previousKey(position: String): String? = lowerKeyOrNull(position)

/** The smallest key above [position], if any. O(log n) on a sorted map on JVM. */
fun <T> Map<String, T>.nextKey(position: String): String? = higherKeyOrNull(position)

/**
 * Puts [elements], in order, right after the element at [position] ([position] itself need
 * not be a key), before the next one. The result is sorted by key.
 */
fun <T> Map<String, T>.insertAfter(elements: List<T>, position: String): Insertion<T> {
    val map = toSortedMutableMap()
    if (elements.isEmpty()) return Insertion(map, emptyList())

    val keys = Rank.between(position, map.higherKeyOrNull(position), elements.size)
    elements.forEachIndexed { index, element -> map[keys[index]] = element }
    return Insertion(map, keys)
}

/** See [insertAfter]. */
fun <T> Map<String, T>.insertAfter(element: T, position: String): Insertion<T> =
    insertAfter(listOf(element), position)

/**
 * Puts [elements], in order, at [position] when it is free (the last of them takes it, the
 * others go before), otherwise right before the element at [position]: between the previous
 * element and that one. The result is sorted by key.
 */
fun <T> Map<String, T>.insertBefore(elements: List<T>, position: String): Insertion<T> {
    val map = toSortedMutableMap()
    if (elements.isEmpty()) return Insertion(map, emptyList())

    val before: List<T>
    val lastKeys: List<String>
    if (map.containsKey(position)) {
        before = elements
        lastKeys = emptyList()
    } else {
        map[position] = elements.last()
        before = elements.dropLast(1)
        lastKeys = listOf(position)
    }
    if (before.isEmpty()) return Insertion(map, lastKeys)

    val keys = Rank.between(map.lowerKeyOrNull(position), position, before.size)
    before.forEachIndexed { index, element -> map[keys[index]] = element }
    return Insertion(map, keys + lastKeys)
}

/** See [insertBefore]. */
fun <T> Map<String, T>.insertBefore(element: T, position: String): Insertion<T> =
    insertBefore(listOf(element), position)

/**
 * Adds every element of [newMap] at its key, or before the element already at that key (see
 * [insertBefore]), in key order. The result is sorted by key.
 */
fun <T> Map<String, T>.insertAll(newMap: Map<String, T>): Insertion<T> {
    var map = toSortedMutableMap()
    val keys = mutableListOf<String>()
    newMap.entries.sortedBy { entry -> entry.key }.forEach { (position, element) ->
        val insertion = map.insertBefore(element, position)
        map = insertion.map
        keys += insertion.keys
    }
    return Insertion(map, keys)
}

/** Removes the element at [position], if any. The other elements keep their keys. */
fun <T> Map<String, T>.removeElementInPosition(position: String): MutableMap<String, T> =
    toSortedMutableMap().apply { remove(position) }

/** Removes the elements matching [predicate]. The other elements keep their keys. */
fun <T> Map<String, T>.removeBy(predicate: (T) -> Boolean): Map<String, T> =
    filterNot { (_, element) -> predicate(element) }.toSortedMutableMap()

/** The values transformed, in a new map sorted by key. */
inline fun <T, R> Map<String, T>.mapValuesSorted(transform: (Map.Entry<String, T>) -> R): MutableMap<String, R> {
    val result = sortedMutableMapOf<String, R>()
    entries.forEach { entry -> result[entry.key] = transform(entry) }
    return result
}

/** This map sorted by key, as a new map. */
fun <T> Map<String, T>.sortedByKey(): Map<String, T> =
    sortedMutableMapOf<String, T>().apply { putAll(this@sortedByKey) }

package io.writeopia.sdk.utils.iterables

import io.writeopia.sdk.utils.collections.higherKeyOrNull
import io.writeopia.sdk.utils.collections.lowerKeyOrNull
import io.writeopia.sdk.utils.collections.sortedMutableMapOf
import io.writeopia.sdk.utils.collections.toSortedMutableMap

/*
 * The keys of a story map are sort keys, not list indexes: a document is the map read in key
 * order, and the key of a step is also its position in the database. Nothing here renumbers
 * the keys of the other elements. A new element goes to a key between its neighbours (1.5
 * between 1 and 2, say), so the steps around it keep their keys, their links and their rows.
 */

/** The largest key below [position], if any. O(log n) on a sorted map on JVM. */
fun <T> Map<Double, T>.previousKey(position: Double): Double? = lowerKeyOrNull(position)

/** The smallest key above [position], if any. O(log n) on a sorted map on JVM. */
fun <T> Map<Double, T>.nextKey(position: Double): Double? = higherKeyOrNull(position)

/**
 * The key for a new element placed before whatever is at [position]: [position] itself when
 * it is free, otherwise half way between the previous key and [position] (or one below
 * [position] when there is nothing before it).
 */
fun <T> Map<Double, T>.keyBefore(position: Double): Double =
    if (!containsKey(position)) {
        position
    } else {
        val previous = previousKey(position)
        if (previous == null) position - 1.0 else (previous + position) / 2.0
    }

/**
 * The key for a new element placed right after whatever is at [position]: half way between
 * [position] and the next key, or one above [position] when it is the last.
 */
fun <T> Map<Double, T>.keyAfter(position: Double): Double {
    val next = nextKey(position)
    return if (next == null) position + 1.0 else (position + next) / 2.0
}

/**
 * Adds [element] at [position], or right before the element already there (see [keyBefore]).
 * The other elements keep their keys. The result is sorted by key.
 */
fun <T> Map<Double, T>.addElementInPosition(
    element: T,
    position: Double
): MutableMap<Double, T> = toSortedMutableMap().apply { this[keyBefore(position)] = element }

/**
 * Adds [element] right after the element at [position] (see [keyAfter]). The other elements
 * keep their keys. The result is sorted by key.
 */
fun <T> Map<Double, T>.addElementAfterPosition(
    element: T,
    position: Double
): MutableMap<Double, T> = toSortedMutableMap().apply { this[keyAfter(position)] = element }

/**
 * Adds [elements], in order, before the element at [position]: they are spread between the
 * previous key and [position] (which the last of them takes when it is free). The other
 * elements keep their keys. The result is sorted by key.
 */
fun <T> Map<Double, T>.addElementsInPosition(
    elements: Iterable<T>,
    position: Double
): MutableMap<Double, T> = toSortedMutableMap().apply { insertElementsBefore(elements, position) }

/**
 * Inserts [elements], in order, before the element at [position], in place: see
 * [addElementsInPosition]. This map must be sorted.
 *
 * @return the keys the elements got, in order.
 */
fun <T> MutableMap<Double, T>.insertElementsBefore(
    elements: Iterable<T>,
    position: Double
): List<Double> {
    val list = elements.toList()
    if (list.isEmpty()) return emptyList()

    val keys = mutableListOf<Double>()
    val before = if (containsKey(position)) {
        list
    } else {
        this[position] = list.last()
        list.dropLast(1)
    }

    // Each one goes right before [position]: after the one added just before it
    before.forEach { element ->
        val key = keyBefore(position)
        this[key] = element
        keys += key
    }
    if (before.size < list.size) keys += position

    return keys
}

/** Removes the element at [position], if any. The other elements keep their keys. */
fun <T> Map<Double, T>.removeElementInPosition(position: Double): MutableMap<Double, T> =
    toSortedMutableMap().apply { remove(position) }

/** Removes the elements matching [predicate]. The other elements keep their keys. */
fun <T> Map<Double, T>.removeBy(predicate: (T) -> Boolean): Map<Double, T> =
    filterNot { (_, element) -> predicate(element) }.toSortedMutableMap()

/**
 * Adds every element of [newMap] at its key, or before the element already at that key (see
 * [addElementInPosition]), in key order. The elements of this map keep their keys.
 */
fun <T> Map<Double, T>.mergeSortedMaps(newMap: Map<Double, T>): MutableMap<Double, T> =
    toSortedMutableMap().apply { insertAll(newMap) }

/**
 * Inserts every element of [newMap] at its key, or before the element already there, in
 * place: see [mergeSortedMaps]. This map must be sorted.
 *
 * @return the keys the elements got.
 */
fun <T> MutableMap<Double, T>.insertAll(newMap: Map<Double, T>): List<Double> =
    newMap.entries.sortedBy { entry -> entry.key }.map { (position, element) ->
        val key = keyBefore(position)
        this[key] = element
        key
    }

/** The values transformed, in a new map sorted by key. */
inline fun <T, R> Map<Double, T>.mapValuesSorted(transform: (Map.Entry<Double, T>) -> R): MutableMap<Double, R> {
    val result = sortedMutableMapOf<Double, R>()
    entries.forEach { entry -> result[entry.key] = transform(entry) }
    return result
}

/** This map sorted by key, as a new map. */
fun <T> Map<Double, T>.sortedByKey(): Map<Double, T> =
    sortedMutableMapOf<Double, T>().apply { putAll(this@sortedByKey) }

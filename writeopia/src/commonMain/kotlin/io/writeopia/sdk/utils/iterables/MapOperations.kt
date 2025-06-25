package io.writeopia.sdk.utils.iterables

import io.writeopia.sdk.utils.extensions.associateWithPosition
import kotlin.math.min

object MapOperations {

    // Todo: Add unit tests
}

fun <T> Map<Int, T>.mergeSortedMaps(
    newMap: Map<Int, T>,
): Map<Int, T> {
    val mutableList = this.values.toMutableList()

    newMap.entries.sortedBy { it.key }.forEach { (position, value) ->
        mutableList.add(position, value)
    }

    return mutableList.associateWithPosition()
}

fun <T> Map<Int, T>.addElementInPosition(
    element: T,
    position: Int
): Map<Int, T> {
    val mutable = this.values.toMutableList()

    mutable.add(min(position, mutable.lastIndex + 1), element)

    return mutable.associateWithPosition()
}

fun <T> Map<Int, T>.removeElementInPosition(position: Int): Map<Int, T> {
    val mutable = this.values.toMutableList()
    mutable.removeAt(position)
    // Todo: associateWithPosition doesn't need to start in the 0 position here
    return mutable.associateWithPosition()
}

fun <T> Map<Int, T>.removeBy(predicate: (T) -> Boolean): Map<Int, T> =
    this.values
        .filterNot(predicate)
        .associateWithPosition()

fun <T> Map<Int, T>.addElementsInPosition(
    elements: Iterable<T>,
    position: Int
): Map<Int, T> {
    val mutable = this.values.toMutableList()

    elements.reversed().forEach { element ->
        mutable.add(min(position, mutable.lastIndex + 1), element)
    }

    return mutable.associateWithPosition()
}

fun <T> Map<Int, T>.normalizePositions(): Map<Int, T> =
    values.toMutableList().associateWithPosition()

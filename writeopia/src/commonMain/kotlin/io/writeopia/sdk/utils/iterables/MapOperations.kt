package io.writeopia.sdk.utils.iterables

import io.writeopia.sdk.utils.extensions.associateWithPosition
import kotlin.math.min

object MapOperations {

    // Todo: Add unit tests
}

fun <T> Map<Double, T>.mergeSortedMaps(
    newMap: Map<Double, T>,
): Map<Double, T> {
    val mutableList = this.values.toMutableList()

    newMap.entries.sortedBy { it.key }.forEach { (position, value) ->
        mutableList.add(position.toInt(), value)
    }

    return mutableList.associateWithPosition()
}

fun <T> Map<Double, T>.addElementInPosition(
    element: T,
    position: Double
): Map<Double, T> {
    val mutable = this.values.toMutableList()

    mutable.add(min(position.toInt(), mutable.lastIndex + 1), element)

    return mutable.associateWithPosition()
}

fun <T> Map<Double, T>.removeElementInPosition(position: Double): Map<Double, T> {
    val mutable = this.values.toMutableList()
    mutable.removeAt(position.toInt())
    // Todo: associateWithPosition doesn't need to start in the 0 position here
    return mutable.associateWithPosition()
}

fun <T> Map<Double, T>.removeBy(predicate: (T) -> Boolean): Map<Double, T> =
    this.values
        .filterNot(predicate)
        .associateWithPosition()

fun <T> Map<Double, T>.addElementsInPosition(
    elements: Iterable<T>,
    position: Double
): Map<Double, T> {
    val mutable = this.values.toMutableList()

    elements.reversed().forEach { element ->
        mutable.add(min(position.toInt(), mutable.lastIndex + 1), element)
    }

    return mutable.associateWithPosition()
}

fun <T> Map<Double, T>.normalizePositions(): Map<Double, T> =
    values.toMutableList().associateWithPosition()

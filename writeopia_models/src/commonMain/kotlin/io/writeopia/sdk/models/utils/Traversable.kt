package io.writeopia.sdk.models.utils

interface Traversable {
    val id: String
    val parentId: String
}

/**
 * This method creates an adjency list from a traversable Iterable.
 */
fun <T : Traversable> Iterable<T>.toAdjencyList(): Map<T, List<T>> =
    this.associateWith { tOut ->
        this.filter { tIn -> tIn.parentId == tOut.id }
    }

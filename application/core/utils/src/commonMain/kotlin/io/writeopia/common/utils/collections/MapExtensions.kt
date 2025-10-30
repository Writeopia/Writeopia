package io.writeopia.common.utils.collections

import io.writeopia.common.utils.Node
import io.writeopia.common.utils.createNodeTree
import io.writeopia.sdk.models.utils.Traversable

fun <K, V> Map<K, List<V>>.merge(map: Map<K, List<V>>) =
    this.mapValues { (key, dataList) -> dataList + (map[key] ?: emptyList()) }
        .toMutableMap()
        .apply {
            putAll(map.filterKeys { key -> !this.containsKey(key) })
        }

fun <T : Node> Map<String, List<T>>.toNodeTree(
    node: T,
    filterPredicate: (T) -> Boolean = { true }
): T = createNodeTree(this, node, filterPredicate = filterPredicate)

/**
 * Traverses an traversable iterable, filters the nodes and map the items of the traversal
 * with mapFunc
 */
inline fun <reified T : Traversable, U> Iterable<T>.traverse(
    initialId: String,
    targetId: String = "root",
    noinline filterPredicate: ((T) -> Boolean)? = null,
    mapFunc: (T) -> U
): List<U> {
    val traversableMap: Map<String, T> = if (filterPredicate != null) {
        this.filter(filterPredicate)
    } else {
        this
    }.associateBy { item -> item.id }

    var currentTraversable: T? = traversableMap[initialId] ?: return emptyList()
    val resultList = mutableListOf(currentTraversable)

    while (currentTraversable != null && currentTraversable.id != targetId) {
        currentTraversable = traversableMap[currentTraversable.parentId]
        resultList.add(currentTraversable)
    }

    return resultList.reversed().filterNotNull().map(mapFunc)
}

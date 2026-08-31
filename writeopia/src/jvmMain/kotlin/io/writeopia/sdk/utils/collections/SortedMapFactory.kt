package io.writeopia.sdk.utils.collections

actual fun <K : Comparable<K>, V> sortedMutableMapOf(): MutableMap<K, V> = sortedMapOf()

actual fun <K : Comparable<K>, V> Map<K, V>.toSortedMutableMap(): MutableMap<K, V> =
    toSortedMap(naturalOrder())

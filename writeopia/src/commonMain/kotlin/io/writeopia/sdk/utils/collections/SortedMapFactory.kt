package io.writeopia.sdk.utils.collections

/**
 * Creates an empty mutable map that maintains its keys in sorted order.
 * On JVM, this is backed by TreeMap. On other platforms, it uses a custom implementation.
 */
expect fun <K : Comparable<K>, V> sortedMutableMapOf(): MutableMap<K, V>

/**
 * Creates a sorted mutable map from the entries of this map.
 * On JVM, this is backed by TreeMap. On other platforms, it uses a custom implementation.
 */
expect fun <K : Comparable<K>, V> Map<K, V>.toSortedMutableMap(): MutableMap<K, V>

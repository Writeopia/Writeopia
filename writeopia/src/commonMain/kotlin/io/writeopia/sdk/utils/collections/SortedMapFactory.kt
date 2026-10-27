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

/**
 * The largest key, or null when the map is empty. O(log n) on a map made by
 * [sortedMutableMapOf] or [toSortedMutableMap] on JVM; a scan of the keys elsewhere.
 */
expect fun <K : Comparable<K>, V> Map<K, V>.lastKeyOrNull(): K?

/**
 * The largest key strictly below [key], or null. O(log n) on a map made by
 * [sortedMutableMapOf] or [toSortedMutableMap] on JVM; a scan of the keys elsewhere.
 */
expect fun <K : Comparable<K>, V> Map<K, V>.lowerKeyOrNull(key: K): K?

/**
 * The smallest key strictly above [key], or null. O(log n) on a map made by
 * [sortedMutableMapOf] or [toSortedMutableMap] on JVM; a scan of the keys elsewhere.
 */
expect fun <K : Comparable<K>, V> Map<K, V>.higherKeyOrNull(key: K): K?

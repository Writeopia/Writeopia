package io.writeopia.sdk.utils.collections

actual fun <K : Comparable<K>, V> sortedMutableMapOf(): MutableMap<K, V> = sortedMapOf()

actual fun <K : Comparable<K>, V> Map<K, V>.toSortedMutableMap(): MutableMap<K, V> =
    toSortedMap(naturalOrder())

actual fun <K : Comparable<K>, V> Map<K, V>.lastKeyOrNull(): K? =
    if (this is java.util.NavigableMap<K, V>) {
        if (isEmpty()) null else lastKey()
    } else {
        keys.maxOrNull()
    }

actual fun <K : Comparable<K>, V> Map<K, V>.lowerKeyOrNull(key: K): K? =
    if (this is java.util.NavigableMap<K, V>) {
        lowerKey(key)
    } else {
        keys.filter { it < key }.maxOrNull()
    }

actual fun <K : Comparable<K>, V> Map<K, V>.higherKeyOrNull(key: K): K? =
    if (this is java.util.NavigableMap<K, V>) {
        higherKey(key)
    } else {
        keys.filter { it > key }.minOrNull()
    }

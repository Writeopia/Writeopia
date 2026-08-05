package io.writeopia.sdk.utils.collections

actual fun <K : Comparable<K>, V> sortedMutableMapOf(): MutableMap<K, V> = SortedMutableMap()

actual fun <K : Comparable<K>, V> Map<K, V>.toSortedMutableMap(): MutableMap<K, V> =
    SortedMutableMap<K, V>().apply { putAll(this@toSortedMutableMap) }

/**
 * A MutableMap implementation that maintains keys in sorted order.
 * Uses a LinkedHashMap internally and re-sorts on each insertion.
 */
private class SortedMutableMap<K : Comparable<K>, V> : MutableMap<K, V> {
    private val delegate = linkedMapOf<K, V>()

    override val size: Int get() = delegate.size
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> get() = delegate.entries
    override val keys: MutableSet<K> get() = delegate.keys
    override val values: MutableCollection<V> get() = delegate.values

    override fun isEmpty(): Boolean = delegate.isEmpty()
    override fun containsKey(key: K): Boolean = delegate.containsKey(key)
    override fun containsValue(value: V): Boolean = delegate.containsValue(value)
    override fun get(key: K): V? = delegate[key]

    override fun put(key: K, value: V): V? {
        val oldValue = delegate.remove(key)
        // Insert in sorted order
        val sortedEntries = delegate.entries.toMutableList()
        sortedEntries.add(object : MutableMap.MutableEntry<K, V> {
            override val key: K = key
            private var _value: V = value
            override val value: V get() = _value
            override fun setValue(newValue: V): V {
                val old = _value
                _value = newValue
                return old
            }
        })
        sortedEntries.sortBy { it.key }

        delegate.clear()
        sortedEntries.forEach { delegate[it.key] = it.value }

        return oldValue
    }

    override fun putAll(from: Map<out K, V>) {
        from.forEach { (k, v) -> put(k, v) }
    }

    override fun remove(key: K): V? = delegate.remove(key)

    override fun clear() = delegate.clear()
}

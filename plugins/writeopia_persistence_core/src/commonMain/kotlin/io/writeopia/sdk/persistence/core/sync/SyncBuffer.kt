package io.writeopia.sdk.persistence.core.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A buffer that collects items and flushes them after a debounce period.
 * Used for batching sync operations to reduce network calls.
 *
 * @param T The type of items to buffer
 * @param scope The coroutine scope to use for async operations
 * @param debounceMs The debounce duration in milliseconds (default 2000ms)
 * @param maxBatchSize The maximum batch size before forcing a flush (default 50)
 * @param onFlush Callback invoked when items are flushed
 */
class SyncBuffer<T>(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 2000L,
    private val maxBatchSize: Int = 50,
    private val onFlush: suspend (List<T>) -> Unit
) {
    private val buffer = mutableListOf<T>()
    private val mutex = Mutex()
    private var debounceJob: Job? = null

    /**
     * Add a single item to the buffer.
     * Will trigger flush after debounce period or when maxBatchSize is reached.
     */
    suspend fun add(item: T) {
        addAll(listOf(item))
    }

    /**
     * Add multiple items to the buffer.
     * Will trigger flush after debounce period or when maxBatchSize is reached.
     */
    suspend fun addAll(items: List<T>) {
        mutex.withLock {
            buffer.addAll(items)

            // Cancel existing debounce job
            debounceJob?.cancel()

            // Check if we've reached max batch size
            if (buffer.size >= maxBatchSize) {
                flushInternal()
            } else {
                // Schedule a new debounce
                debounceJob = scope.launch {
                    delay(debounceMs)
                    mutex.withLock {
                        if (buffer.isNotEmpty()) {
                            flushInternal()
                        }
                    }
                }
            }
        }
    }

    /**
     * Immediately flush all pending items.
     * Call this when exiting the editor or on app background.
     */
    suspend fun flushNow() {
        mutex.withLock {
            debounceJob?.cancel()
            if (buffer.isNotEmpty()) {
                flushInternal()
            }
        }
    }

    /**
     * Check if there are pending items in the buffer.
     */
    suspend fun hasPendingItems(): Boolean {
        return mutex.withLock { buffer.isNotEmpty() }
    }

    /**
     * Get the current count of pending items.
     */
    suspend fun pendingCount(): Int {
        return mutex.withLock { buffer.size }
    }

    /**
     * Internal flush without locking - caller must hold the mutex.
     */
    private suspend fun flushInternal() {
        val items = buffer.toList()
        buffer.clear()
        onFlush(items)
    }
}

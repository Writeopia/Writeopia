package io.writeopia.ui.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class EventBuffer<T>(scope: CoroutineScope, private val intervalMs: Long = 5L) {

    private val eventChannel = Channel<T>(Channel.UNLIMITED)
    private val _events = MutableSharedFlow<T>(extraBufferCapacity = Int.MAX_VALUE)

    val events: Flow<T> = _events.asSharedFlow()

    init {
        scope.launch {
            while (isActive) {
                val event = withTimeoutOrNull(intervalMs) {
                    eventChannel.receiveCatching().getOrNull()
                }
                if (event != null) {
                    _events.emit(event)
                }
                delay(intervalMs)
            }
        }
    }

    fun send(event: T) {
        eventChannel.trySend(event)
    }
}

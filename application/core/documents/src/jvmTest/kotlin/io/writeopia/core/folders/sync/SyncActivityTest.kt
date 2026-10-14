package io.writeopia.core.folders.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncActivityTest {

    @Test
    fun `it is syncing while a tracked block runs and not after`() = runTest {
        val gate = CompletableDeferred<Unit>()

        val sync = async { SyncActivity.track { gate.await() } }
        testScheduler.runCurrent()
        assertTrue(SyncActivity.isSyncing.value)

        gate.complete(Unit)
        sync.await()
        assertFalse(SyncActivity.isSyncing.value)
    }

    @Test
    fun `it stays syncing until the last overlapping sync finishes`() = runTest {
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()

        val firstSync = async { SyncActivity.track { first.await() } }
        val secondSync = async { SyncActivity.track { second.await() } }
        testScheduler.runCurrent()

        first.complete(Unit)
        firstSync.await()
        assertTrue(SyncActivity.isSyncing.value)

        second.complete(Unit)
        secondSync.await()
        assertFalse(SyncActivity.isSyncing.value)
    }

    @Test
    fun `a failing sync stops the syncing state and rethrows`() = runTest {
        val error = runCatching {
            SyncActivity.track<Unit> { throw IllegalStateException("boom") }
        }.exceptionOrNull()

        assertEquals("boom", error?.message)
        assertFalse(SyncActivity.isSyncing.value)
    }
}

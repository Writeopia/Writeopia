package io.writeopia.core.folders.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentLoadActivityTest {

    @Test
    fun `it is loading while content loads and not after`() = runTest {
        val gate = CompletableDeferred<Unit>()

        val load = async { ContentLoadActivity.track { gate.await() } }
        testScheduler.runCurrent()
        assertTrue(ContentLoadActivity.isLoading.value)

        gate.complete(Unit)
        load.await()
        assertFalse(ContentLoadActivity.isLoading.value)
    }

    @Test
    fun `a content load is not a sync`() = runTest {
        val gate = CompletableDeferred<Unit>()

        val load = async { ContentLoadActivity.track { gate.await() } }
        testScheduler.runCurrent()
        assertFalse(SyncActivity.isSyncing.value)

        gate.complete(Unit)
        load.await()
    }

    @Test
    fun `a failing load stops the loading state and rethrows`() = runTest {
        val error = runCatching {
            ContentLoadActivity.track<Unit> { throw IllegalStateException("boom") }
        }.exceptionOrNull()

        assertEquals("boom", error?.message)
        assertFalse(ContentLoadActivity.isLoading.value)
    }
}

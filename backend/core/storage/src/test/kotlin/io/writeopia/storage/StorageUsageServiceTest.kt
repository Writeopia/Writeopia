package io.writeopia.storage

import io.writeopia.sql.WriteopiaDbBackend
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StorageUsageServiceTest {

    private lateinit var db: WriteopiaDbBackend
    private val userId = "storage-user-${UUID.randomUUID()}"
    private val quota = 1_000L

    @BeforeTest
    fun setUp() {
        db = configureTestPersistence()
    }

    @AfterTest
    fun tearDown() {
        db.userStorageUsageEntityQueries.deleteByUser(userId)
    }

    private fun service(maxAttempts: Int = StorageUsageService.DEFAULT_MAX_ATTEMPTS, now: () -> Long = { 1L }) =
        StorageUsageService(db, quotaBytes = quota, maxAttempts = maxAttempts, now = now)

    @Test
    fun `a user without a row has used nothing, at version zero`() {
        val usage = service().usage(userId)

        assertEquals(StorageUsage(userId, usedBytes = 0, quotaBytes = quota, version = 0), usage)
        assertEquals(quota, usage.freeBytes)
    }

    @Test
    fun `a reservation adds the bytes and moves the version`() {
        val service = service()

        val first = assertIs<StorageChange.Applied>(service.reserve(userId, 300))
        assertEquals(300, first.usage.usedBytes)
        assertEquals(1, first.usage.version)

        val second = assertIs<StorageChange.Applied>(service.reserve(userId, 200))
        assertEquals(500, second.usage.usedBytes)
        assertEquals(2, second.usage.version)

        assertEquals(second.usage, service.usage(userId))
    }

    @Test
    fun `a reservation that would overflow the quota is refused and changes nothing`() {
        val service = service()
        service.reserve(userId, 900)

        val refused = assertIs<StorageChange.QuotaExceeded>(service.reserve(userId, 101))
        assertEquals(900, refused.usage.usedBytes)
        assertEquals(101, refused.requestedBytes)

        // Exactly filling the quota is fine.
        assertIs<StorageChange.Applied>(service.reserve(userId, 100))
        assertEquals(quota, service.usage(userId).usedBytes)
        assertIs<StorageChange.QuotaExceeded>(service.reserve(userId, 1))
    }

    @Test
    fun `a release gives the bytes back and never goes below zero`() {
        val service = service()
        service.reserve(userId, 500)

        val released = assertIs<StorageChange.Applied>(service.release(userId, 200))
        assertEquals(300, released.usage.usedBytes)

        val floored = assertIs<StorageChange.Applied>(service.release(userId, 10_000))
        assertEquals(0, floored.usage.usedBytes)
        assertEquals(3, floored.usage.version)
    }

    @Test
    fun `a row changed between the read and the write is read again and the change retried`() {
        service().usage(userId) // The row exists, so now() below runs only in the write
        var sneaked = false
        // now() runs between reading the row and writing it: the first time, someone else writes.
        val service = service {
            if (!sneaked) {
                sneaked = true
                db.userStorageUsageEntityQueries.updateIfVersion(
                    used_bytes = 100, updated_at = 1, user_id = userId, version = 0
                )
            }
            1L
        }

        val applied = assertIs<StorageChange.Applied>(service.reserve(userId, 50))

        // The other write is kept and ours lands on top of it, with the version moved twice.
        assertEquals(150, applied.usage.usedBytes)
        assertEquals(2, applied.usage.version)
        assertEquals(applied.usage, service.usage(userId))
    }

    @Test
    fun `a change that keeps losing the race gives up as a conflict`() {
        service().usage(userId) // The row exists, so now() below runs only in the write
        var version = 0L
        val service = service(maxAttempts = 3) {
            // Every attempt finds the row moved: nothing of ours ever lands.
            db.userStorageUsageEntityQueries.updateIfVersion(
                used_bytes = 10, updated_at = 1, user_id = userId, version = version
            )
            version++
            1L
        }

        assertEquals(StorageChange.Conflict(attempts = 3), service.reserve(userId, 50))
        assertEquals(3, service.usage(userId).version)
    }

    @Test
    fun `parallel reservations lose no byte and refuse what does not fit`() {
        val service = StorageUsageService(db, quotaBytes = 10_000, maxAttempts = 200)
        val threads = 16
        val perThread = 5
        val bytes = 100L
        val executor = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val results = java.util.Collections.synchronizedList(mutableListOf<StorageChange>())

        repeat(threads) {
            executor.submit {
                start.await()
                repeat(perThread) { results.add(service.reserve(userId, bytes)) }
            }
        }
        start.countDown()
        executor.shutdown()
        assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS))

        val applied = results.count { it is StorageChange.Applied }
        assertEquals(threads * perThread, results.size)
        assertEquals(0, results.count { it is StorageChange.Conflict })
        assertEquals(applied * bytes, service.usage(userId).usedBytes)
        assertEquals(applied.toLong(), service.usage(userId).version)
    }
}

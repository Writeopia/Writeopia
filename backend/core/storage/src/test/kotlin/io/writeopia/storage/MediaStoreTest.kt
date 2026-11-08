package io.writeopia.storage

import io.writeopia.backend.models.ImageFile
import io.writeopia.backend.models.ImageStorageService
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.coroutines.test.runTest
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MediaStoreTest {

    private lateinit var db: WriteopiaDbBackend
    private val userId = "media-user-${UUID.randomUUID()}"

    private class FakeImages : ImageStorageService {
        val stored = mutableMapOf<String, Long>()
        var failNext = false

        override suspend fun uploadImageBytes(
            bytes: ByteArray, contentType: String, fileName: String, userId: String, debugMode: Boolean
        ): String? {
            if (failNext) {
                failNext = false
                return null
            }
            val url = "https://storage.test/bucket/uploads/$userId/${stored.size}-$fileName"
            stored[url] = bytes.size.toLong()
            return url
        }

        override suspend fun deleteImage(url: String, userId: String, debugMode: Boolean): Long? {
            if (!url.contains("/uploads/$userId/")) return null
            return stored.remove(url) ?: 0L
        }
    }

    @BeforeTest
    fun setUp() {
        db = configureTestPersistence()
    }

    @AfterTest
    fun tearDown() {
        db.userStorageUsageEntityQueries.deleteByUser(userId)
    }

    private fun image(size: Int) = ImageFile(ByteArray(size), "image/png", "photo.png")

    @Test
    fun `a stored picture counts its bytes`() = runTest {
        val images = FakeImages()
        val usage = StorageUsageService(db, quotaBytes = 1_000)
        val store = MediaStore(images, usage, debugMode = false)

        val stored = assertIs<MediaStoreResult.Stored>(store.store(image(400), userId))

        assertEquals(400, stored.usage?.usedBytes)
        assertEquals(400, usage.usage(userId).usedBytes)
        assertEquals(1, images.stored.size)
    }

    @Test
    fun `a picture that does not fit is refused before reaching the bucket`() = runTest {
        val images = FakeImages()
        val usage = StorageUsageService(db, quotaBytes = 1_000)
        val store = MediaStore(images, usage, debugMode = false)
        store.store(image(700), userId)

        val refused = assertIs<MediaStoreResult.QuotaExceeded>(store.store(image(301), userId))

        assertEquals(700, refused.usage.usedBytes)
        assertEquals(301, refused.requestedBytes)
        assertEquals(1, images.stored.size)
        assertEquals(700, usage.usage(userId).usedBytes)
    }

    @Test
    fun `a picture the bucket refuses is not counted`() = runTest {
        val images = FakeImages().apply { failNext = true }
        val usage = StorageUsageService(db, quotaBytes = 1_000)
        val store = MediaStore(images, usage, debugMode = false)

        assertEquals(MediaStoreResult.Failed, store.store(image(400), userId))

        assertEquals(0, usage.usage(userId).usedBytes)
        // The reservation and its release both moved the version.
        assertEquals(2, usage.usage(userId).version)
    }

    @Test
    fun `deleting a picture gives its bytes back, once`() = runTest {
        val images = FakeImages()
        val usage = StorageUsageService(db, quotaBytes = 1_000)
        val store = MediaStore(images, usage, debugMode = false)
        val url = assertIs<MediaStoreResult.Stored>(store.store(image(400), userId)).url
        store.store(image(100), userId)

        val deleted = assertIs<MediaDeleteResult.Deleted>(store.delete(url, userId))
        assertEquals(400, deleted.freedBytes)
        assertEquals(100, deleted.usage?.usedBytes)

        // Gone already: nothing more is released.
        val again = assertIs<MediaDeleteResult.Deleted>(store.delete(url, userId))
        assertEquals(0, again.freedBytes)
        assertEquals(100, usage.usage(userId).usedBytes)
    }

    @Test
    fun `another user's picture is not touched`() = runTest {
        val images = FakeImages()
        val usage = StorageUsageService(db, quotaBytes = 1_000)
        val store = MediaStore(images, usage, debugMode = false)
        val url = assertIs<MediaStoreResult.Stored>(store.store(image(400), userId)).url

        assertEquals(MediaDeleteResult.NotOwned, store.delete(url, "someone-else"))
        assertEquals(400, usage.usage(userId).usedBytes)
        assertEquals(1, images.stored.size)
    }

    @Test
    fun `without a database the bucket is used and nothing is counted`() = runTest {
        val images = FakeImages()
        val store = MediaStore(images, usage = null, debugMode = false)

        val stored = assertIs<MediaStoreResult.Stored>(store.store(image(400), userId))
        assertEquals(null, stored.usage)
        assertEquals(MediaDeleteResult.Deleted(400, null), store.delete(stored.url, userId))
    }
}

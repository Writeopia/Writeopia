package io.writeopia.buckets

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaObjectNameTest {

    private val bucket = "writeopia-media"
    private val user = "user-1"

    private fun nameOf(url: String, userId: String = user) =
        GcpBucketImageStorageService.objectNameOf(url, bucket, userId)

    @Test
    fun `a signed path-style URL of the user's folder gives its object`() {
        val url = "https://storage.googleapis.com/$bucket/uploads/$user/1700000000-photo.png" +
            "?X-Goog-Algorithm=GOOG4-RSA-SHA256&X-Goog-Signature=abc"

        assertEquals("uploads/$user/1700000000-photo.png", nameOf(url))
    }

    @Test
    fun `a virtual-hosted URL and encoded names are accepted`() {
        assertEquals(
            "uploads/$user/1-my photo.jpg",
            nameOf("https://$bucket.storage.googleapis.com/uploads/$user/1-my%20photo.jpg")
        )
        assertEquals(
            "uploads/$user/1-my photo.jpg",
            nameOf("https://storage.googleapis.com/$bucket/uploads/$user/1-my%20photo.jpg?sig=1")
        )
    }

    @Test
    fun `another user's folder, another bucket or host is refused`() {
        assertNull(nameOf("https://storage.googleapis.com/$bucket/uploads/user-2/1-photo.png"))
        assertNull(nameOf("https://storage.googleapis.com/$bucket/uploads/${user}x/1-photo.png"))
        assertNull(nameOf("https://storage.googleapis.com/other-bucket/uploads/$user/1-photo.png"))
        assertNull(nameOf("https://evil.test/$bucket/uploads/$user/1-photo.png"))
        assertNull(nameOf("http://storage.googleapis.com/$bucket/uploads/$user/1-photo.png"))
        assertNull(nameOf("https://picsum.photos/200"))
    }

    @Test
    fun `traversal, deeper paths and empty names are refused`() {
        assertNull(nameOf("https://storage.googleapis.com/$bucket/uploads/$user/../user-2/1-photo.png"))
        assertNull(nameOf("https://storage.googleapis.com/$bucket/uploads/$user/..%2Fuser-2%2F1-photo.png"))
        assertNull(nameOf("https://storage.googleapis.com/$bucket/uploads/$user/sub/1-photo.png"))
        assertNull(nameOf("https://storage.googleapis.com/$bucket/uploads/$user/"))
        assertNull(nameOf("https://storage.googleapis.com/$bucket/uploads/$user/.."))
        assertNull(nameOf("not a url"))
        assertNull(nameOf("https://storage.googleapis.com/$bucket/uploads/$user/1-photo.png", userId = ""))
    }
}

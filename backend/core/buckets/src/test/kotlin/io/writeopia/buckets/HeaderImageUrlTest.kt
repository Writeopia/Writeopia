package io.writeopia.buckets

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeaderImageUrlTest {

    private val base = "https://writeopia.io"
    private val id = "32b55974-4cb5-49dc-af56-67498655f904"

    private fun isHeader(url: String, workspaceId: String = "ws") =
        GcpBucketHeaderImageStorage.isHeaderImageUrl(url, base, workspaceId)

    @Test
    fun `a picture stored for the workspace is accepted`() {
        assertTrue(isHeader("$base/header-images/ws/$id.png"))
        assertTrue(isHeader("$base/header-images/ws/$id.jpg"))
        assertTrue(isHeader("$base/header-images/ws/$id.webp"))
        assertTrue(GcpBucketHeaderImageStorage.isHeaderImageUrl("$base/header-images/ws/$id.png", "$base/", "ws"))
    }

    @Test
    fun `a picture of another workspace is refused`() {
        assertFalse(isHeader("$base/header-images/other/$id.png"))
        assertFalse(isHeader("$base/header-images/wsx/$id.png"))
    }

    @Test
    fun `traversal, encoded separators and deeper paths are refused`() {
        assertFalse(isHeader("$base/header-images/ws/../other/$id.png"))
        assertFalse(isHeader("$base/header-images/ws/..%2Fother%2F$id.png"))
        assertFalse(isHeader("$base/header-images/ws/%2E%2E/$id.png"))
        assertFalse(isHeader("$base/header-images/ws/sub/$id.png"))
        assertFalse(isHeader("$base/header-images/ws/$id.png/"))
    }

    @Test
    fun `an empty name, a query or another kind of file is refused`() {
        assertFalse(isHeader("$base/header-images/ws/"))
        assertFalse(isHeader("$base/header-images/ws/$id.png?x=1"))
        assertFalse(isHeader("$base/header-images/ws/$id.png#x"))
        assertFalse(isHeader("$base/header-images/ws/$id.svg"))
        assertFalse(isHeader("$base/header-images/ws/not-an-id.png"))
        assertFalse(isHeader("https://evil.test/header-images/ws/$id.png"))
    }
}

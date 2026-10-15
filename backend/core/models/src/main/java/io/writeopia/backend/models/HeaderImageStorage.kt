package io.writeopia.backend.models

/**
 * Where the header pictures of the documents are kept. Unlike the rest of the media, they are
 * public and served by a CDN: a header lives in the document for as long as the document does,
 * and any member of the workspace opens it, so a signed URL, which expires, doesn't fit.
 */
interface HeaderImageStorage {
    /**
     * Stores a header picture of [workspaceId].
     * @return The public URL of the picture, or null when it couldn't be stored.
     */
    suspend fun store(bytes: ByteArray, contentType: String, workspaceId: String, debugMode: Boolean): String?

    /** Whether [url] is a header picture this storage keeps for [workspaceId]. */
    fun isHeaderOf(url: String, workspaceId: String, debugMode: Boolean): Boolean
}

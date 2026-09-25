package io.writeopia.core.folders.sync

import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.document.Document
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DocumentMergerTest {

    private val merger = DocumentMerger()

    @Test
    fun `backend document without comments should preserve local comments`() {
        val localComments = mapOf(
            "conversation-1" to listOf(
                Comment(id = "comment-1", text = "Local comment")
            )
        )
        val local = document(lastUpdatedAt = 1, comments = localComments)
        val backend = document(lastUpdatedAt = 2, comments = emptyMap())

        val merged = merger.merge(local, backend)

        assertEquals(localComments, merged?.commentConversations)
    }

    @Test
    fun `backend comments should win when newer backend provides them`() {
        val localComments = mapOf(
            "conversation-1" to listOf(
                Comment(id = "comment-1", text = "Local comment")
            )
        )
        val backendComments = mapOf(
            "conversation-2" to listOf(
                Comment(id = "comment-2", text = "Backend comment")
            )
        )
        val local = document(lastUpdatedAt = 1, comments = localComments)
        val backend = document(lastUpdatedAt = 2, comments = backendComments)

        val merged = merger.merge(local, backend)

        assertEquals(backendComments, merged?.commentConversations)
    }

    private fun document(
        lastUpdatedAt: Long,
        comments: Map<String, List<Comment>>,
    ) = Document(
        id = "document-1",
        title = "Document",
        createdAt = Instant.fromEpochMilliseconds(0),
        lastUpdatedAt = Instant.fromEpochMilliseconds(lastUpdatedAt),
        lastSyncedAt = null,
        workspaceId = "workspace-1",
        parentId = "root",
        commentConversations = comments,
    )
}

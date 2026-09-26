package io.writeopia.core.folders.sync

import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DocumentMergerTest {

    private val merger = DocumentMerger()

    @Test
    fun `backend document without comment payload should preserve referenced local comments`() {
        val conversationId = "conversation-1"
        val localComments = commentMap(conversationId, "Local comment")
        val local = document(
            lastUpdatedAt = 1,
            comments = localComments,
            content = mapOf(0.0 to step("step-1", 1, conversationId)),
        )
        val backend = document(
            lastUpdatedAt = 2,
            comments = emptyMap(),
            content = mapOf(0.0 to step("step-1", 2, conversationId)),
        )

        val merged = merger.merge(local, backend)

        assertEquals(localComments, merged?.commentConversations)
    }

    @Test
    fun `newer backend comments should win for the same referenced conversation`() {
        val conversationId = "conversation-1"
        val local = document(
            lastUpdatedAt = 1,
            comments = commentMap(conversationId, "Local comment"),
            content = mapOf(0.0 to step("step-1", 1, conversationId)),
        )
        val backendComments = commentMap(conversationId, "Backend comment")
        val backend = document(
            lastUpdatedAt = 2,
            comments = backendComments,
            content = mapOf(0.0 to step("step-1", 2, conversationId)),
        )

        val merged = merger.merge(local, backend)

        assertEquals(backendComments, merged?.commentConversations)
    }

    @Test
    fun `replies from both copies of a referenced conversation should survive`() {
        val conversationId = "conversation-1"
        val local = document(
            lastUpdatedAt = 2,
            comments = mapOf(
                conversationId to listOf(
                    Comment(id = "shared", text = "Local wins duplicate"),
                    Comment(id = "local-reply", text = "Local reply"),
                )
            ),
            content = mapOf(0.0 to step("step-1", 2, conversationId)),
        )
        val backend = document(
            lastUpdatedAt = 1,
            comments = mapOf(
                conversationId to listOf(
                    Comment(id = "shared", text = "Backend duplicate"),
                    Comment(id = "backend-reply", text = "Backend reply"),
                )
            ),
            content = mapOf(0.0 to step("step-1", 1, conversationId)),
        )

        val merged = merger.merge(local, backend)

        assertEquals(
            listOf(
                Comment(id = "shared", text = "Local wins duplicate"),
                Comment(id = "local-reply", text = "Local reply"),
                Comment(id = "backend-reply", text = "Backend reply"),
            ),
            merged?.commentConversations?.get(conversationId),
        )
    }

    @Test
    fun `distinct referenced conversations from merged content should both survive`() {
        val localId = "conversation-local"
        val backendId = "conversation-backend"
        val localComments = commentMap(localId, "Local comment")
        val backendComments = commentMap(backendId, "Backend comment")
        val local = document(
            lastUpdatedAt = 1,
            comments = localComments,
            content = mapOf(
                0.0 to step(
                    id = "local-parent",
                    updatedAt = 1,
                    children = listOf(step("local-child", 1, localId)),
                ),
            ),
        )
        val backend = document(
            lastUpdatedAt = 2,
            comments = backendComments,
            content = mapOf(1.0 to step("backend-step", 2, backendId)),
        )

        val merged = merger.merge(local, backend)

        assertEquals(localComments + backendComments, merged?.commentConversations)
    }

    @Test
    fun `conversation removed with the winning story step should not be resurrected`() {
        val conversationId = "conversation-1"
        val local = document(
            lastUpdatedAt = 1,
            comments = commentMap(conversationId, "Local comment"),
            content = mapOf(0.0 to step("step-1", 1, conversationId)),
        )
        val backend = document(
            lastUpdatedAt = 2,
            comments = emptyMap(),
            content = mapOf(0.0 to step("step-1", 2)),
        )

        val merged = merger.merge(local, backend)

        assertEquals(emptyMap(), merged?.commentConversations)
    }

    private fun commentMap(id: String, text: String) = mapOf(
        id to listOf(Comment(id = "$id-comment", text = text))
    )

    private fun step(
        id: String,
        updatedAt: Long,
        conversationId: String? = null,
        children: List<StoryStep> = emptyList(),
    ) = StoryStep(
        id = id,
        type = StoryTypes.TEXT.type,
        text = "Text",
        steps = children,
        spans = conversationId?.let { id ->
            setOf(SpanInfo.create(0, 4, Span.COMMENT, id))
        } ?: emptySet(),
        lastUpdatedAt = updatedAt,
    )

    private fun document(
        lastUpdatedAt: Long,
        comments: Map<String, List<Comment>>,
        content: Map<Double, StoryStep>,
    ) = Document(
        id = "document-1",
        title = "Document",
        content = content,
        createdAt = Instant.fromEpochMilliseconds(0),
        lastUpdatedAt = Instant.fromEpochMilliseconds(lastUpdatedAt),
        lastSyncedAt = null,
        workspaceId = "workspace-1",
        parentId = "root",
        commentConversations = comments,
    )
}

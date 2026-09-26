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


    @Test
    fun `newer backend preserves local conversation referenced by retained local story`() {
        val localOnlyComments = listOf(
            Comment(id = "local-comment", text = "Local")
        )
        val backendComments = listOf(
            Comment(id = "backend-comment", text = "Backend")
        )
        val local = document(
            lastUpdatedAt = 1,
            comments = mapOf(
                "local-conversation" to localOnlyComments,
                "shared-conversation" to listOf(
                    Comment(id = "local-shared", text = "Local shared")
                ),
            ),
            content = mapOf(
                0.0 to StoryStep(
                    id = "local-step",
                    type = StoryTypes.TEXT.type,
                    text = "local",
                    spans = setOf(
                        SpanInfo.create(0, 5, Span.COMMENT, "local-conversation")
                    ),
                )
            ),
        )
        val backend = document(
            lastUpdatedAt = 2,
            comments = mapOf(
                "shared-conversation" to backendComments,
            ),
        )

        val merged = merger.merge(local, backend)

        assertEquals(localOnlyComments, merged?.commentConversations?.get("local-conversation"))
        assertEquals(backendComments, merged?.commentConversations?.get("shared-conversation"))
    }

    private fun document(
        lastUpdatedAt: Long,
        comments: Map<String, List<Comment>>,
        content: Map<Double, StoryStep> = emptyMap(),
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

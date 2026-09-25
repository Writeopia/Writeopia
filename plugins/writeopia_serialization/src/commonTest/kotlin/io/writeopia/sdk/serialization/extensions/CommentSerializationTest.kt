@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.serialization.extensions

import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.serialization.data.CommentConversationApi
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.json.writeopiaJson
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class CommentSerializationTest {

    @Test
    fun `comment ids are required in serialized payloads`() {
        assertFailsWith<SerializationException> {
            writeopiaJson.decodeFromString<CommentConversationApi>(
                """{"comments":[{"id":"comment-1","text":"Text"}]}"""
            )
        }

        assertFailsWith<SerializationException> {
            writeopiaJson.decodeFromString<CommentConversationApi>(
                """{"id":"conversation-1","comments":[{"text":"Text"}]}"""
            )
        }

        assertFailsWith<SerializationException> {
            writeopiaJson.decodeFromString<CommentConversationApi>(
                """{"id":"conversation-1"}"""
            )
        }
    }

    @Test
    fun `empty comment conversation is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            CommentConversation(id = "conversation-empty", comments = emptyList())
        }
    }

    @Test
    fun `span extra survives api conversion`() {
        val span = SpanInfo.create(1, 4, Span.COMMENT, "conversation-1")

        assertEquals(span, span.toApi().toModel())
    }

    @Test
    fun `document comments survive json round trip`() {
        val document = Document(
            id = "document-1",
            title = "Document",
            content = mapOf(
                0.0 to StoryStep(
                    id = "step-1",
                    type = StoryTypes.TEXT.type,
                    text = "Commented text",
                    spans = setOf(
                        SpanInfo.create(0, 9, Span.COMMENT, "conversation-1")
                    )
                )
            ),
            commentConversations = mapOf(
                "conversation-1" to listOf(
                    Comment(id = "comment-1", text = "First"),
                    Comment(id = "comment-2", text = "Second")
                )
            ),
            createdAt = Instant.fromEpochMilliseconds(1),
            lastUpdatedAt = Instant.fromEpochMilliseconds(2),
            lastSyncedAt = null,
            workspaceId = "disconnected_user",
            parentId = "root",
        )

        val api = document.toApi()
        val encoded = writeopiaJson.encodeToString(DocumentApi.serializer(), api)
        val decoded = writeopiaJson.decodeFromString(DocumentApi.serializer(), encoded).toModel()

        assertEquals(document.id, decoded.id)
        assertEquals(document.title, decoded.title)
        assertEquals(document.commentConversations, decoded.commentConversations)

        val decodedStep = decoded.content.getValue(0.0)
        assertEquals("step-1", decodedStep.id)
        assertEquals("Commented text", decodedStep.text)
        assertEquals(
            setOf(SpanInfo.create(0, 9, Span.COMMENT, "conversation-1")),
            decodedStep.spans,
        )
    }

    @Test
    fun `old document without comments remains readable`() {
        val json = """
            {
              "id": "old-document",
              "title": "Old",
              "workspaceId": "disconnected_user",
              "content": [],
              "createdAt": 1,
              "lastUpdatedAt": 2,
              "parentId": "root"
            }
        """.trimIndent()

        val decoded = writeopiaJson.decodeFromString(DocumentApi.serializer(), json).toModel()

        assertTrue(decoded.commentConversations.isEmpty())
    }
}

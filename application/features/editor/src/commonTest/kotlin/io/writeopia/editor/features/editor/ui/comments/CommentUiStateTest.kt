package io.writeopia.editor.features.editor.ui.comments

import io.writeopia.sdk.model.story.Selection
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.model.DrawState
import io.writeopia.ui.model.DrawStory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommentUiStateTest {

    @Test
    fun `cursor in commented paragraph exposes the conversation`() {
        val conversation = conversation("conversation-1")
        val state = drawState(
            selection = Selection.fromPosition(0, 1.0),
            spans = setOf(SpanInfo.create(3, 7, Span.COMMENT, conversation.id)),
        )

        val result = resolveCommentUiState(state, mapOf(conversation.id to conversation.comments))

        assertEquals(conversation, result.activeConversation)
        assertEquals(listOf(conversation), result.paragraphConversations)
        assertFalse(result.canCreateComment)
    }

    @Test
    fun `selection outside comments can create a new conversation`() {
        val conversation = conversation("conversation-1")
        val state = drawState(
            selection = Selection(start = 10, end = 14, position = 1.0),
            spans = setOf(SpanInfo.create(3, 7, Span.COMMENT, conversation.id)),
        )

        val result = resolveCommentUiState(state, mapOf(conversation.id to conversation.comments))

        assertEquals(null, result.activeConversation)
        assertEquals(listOf(conversation), result.paragraphConversations)
        assertTrue(result.canCreateComment)
        assertEquals(Selection(start = 10, end = 14, position = 1.0), result.createTarget)
    }

    @Test
    fun `selection adjacent to a comment can create a new conversation`() {
        val conversation = conversation("conversation-1")
        val state = drawState(
            selection = Selection(start = 7, end = 10, position = 1.0),
            spans = setOf(SpanInfo.create(3, 7, Span.COMMENT, conversation.id)),
        )

        val result = resolveCommentUiState(state, mapOf(conversation.id to conversation.comments))

        assertEquals(null, result.activeConversation)
        assertTrue(result.canCreateComment)
    }

    @Test
    fun `cursor at shared comment boundary selects the following conversation`() {
        val first = conversation("conversation-1")
        val second = conversation("conversation-2")
        val state = drawState(
            selection = Selection.fromPosition(7, 1.0),
            spans = setOf(
                SpanInfo.create(3, 7, Span.COMMENT, first.id),
                SpanInfo.create(7, 11, Span.COMMENT, second.id),
            ),
        )

        val result = resolveCommentUiState(
            state,
            mapOf(
                first.id to first.comments,
                second.id to second.comments,
            ),
        )

        assertEquals(second, result.activeConversation)
        assertFalse(result.canCreateComment)
    }

    @Test
    fun `selection overlapping a comment uses that conversation`() {
        val conversation = conversation("conversation-1")
        val state = drawState(
            selection = Selection(start = 4, end = 6, position = 1.0),
            spans = setOf(SpanInfo.create(3, 7, Span.COMMENT, conversation.id)),
        )

        val result = resolveCommentUiState(state, mapOf(conversation.id to conversation.comments))

        assertEquals(conversation, result.activeConversation)
        assertFalse(result.canCreateComment)
    }

    private fun conversation(id: String) = CommentConversation(
        id = id,
        comments = listOf(Comment(id = "$id-comment", text = "Text")),
    )

    private fun drawState(
        selection: Selection,
        spans: Set<SpanInfo>,
    ): DrawState = DrawState(
        stories = listOf(
            DrawStory(
                storyStep = StoryStep(
                    id = "step-1",
                    type = StoryTypes.TEXT.type,
                    text = "A paragraph with enough text",
                    spans = spans,
                ),
                position = 1.0,
                cursor = selection,
            )
        ),
        focus = 1.0,
    )
}

package io.writeopia.editor.features.editor.ui.comments

import io.writeopia.sdk.model.story.Selection
import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.models.span.Span
import io.writeopia.ui.model.DrawState

internal data class CommentUiState(
    val activeConversation: CommentConversation? = null,
    val paragraphConversations: List<CommentConversation> = emptyList(),
    val canCreateComment: Boolean = false,
    val createTarget: Selection? = null,
)

internal fun resolveCommentUiState(
    drawState: DrawState,
    conversations: List<CommentConversation>,
): CommentUiState {
    val activeStory = drawState.stories.firstOrNull { it.cursor != null }
        ?: drawState.focus?.let { focus ->
            drawState.stories.firstOrNull { it.position == focus }
        }
        ?: return CommentUiState()

    val commentSpans = activeStory.storyStep.spans
        .filter { span -> span.span == Span.COMMENT && span.extra != null }
        .sortedWith(compareBy({ it.start }, { it.end }))

    val conversationsById = conversations.associateBy { it.id }
    val paragraphConversations = commentSpans
        .mapNotNull { span -> span.extra }
        .distinct()
        .mapNotNull(conversationsById::get)

    val selection = activeStory.cursor
    val selectedConversation = selection?.let { cursor ->
        val (start, end) = cursor.sortedPositions()
        commentSpans
            .firstOrNull { span ->
                if (start == end) {
                    start >= span.start && start < span.end
                } else {
                    span.start < end && span.end > start
                }
            }
            ?.extra
            ?.let(conversationsById::get)
    }

    val canCreateComment = selection?.let { cursor ->
        val (start, end) = cursor.sortedPositions()
        start != end && selectedConversation == null
    } ?: false
    val activeConversation = if (canCreateComment) {
        null
    } else {
        selectedConversation ?: paragraphConversations.firstOrNull()
    }

    val createTarget = if (canCreateComment && selection != null) {
        val (start, end) = selection.sortedPositions()
        Selection(start = start, end = end, position = selection.position)
    } else {
        null
    }

    return CommentUiState(
        activeConversation = activeConversation,
        paragraphConversations = paragraphConversations,
        canCreateComment = canCreateComment,
        createTarget = createTarget,
    )
}

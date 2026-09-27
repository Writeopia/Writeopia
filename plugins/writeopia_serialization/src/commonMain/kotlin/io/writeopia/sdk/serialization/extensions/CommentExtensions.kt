package io.writeopia.sdk.serialization.extensions

import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.serialization.data.CommentApi
import io.writeopia.sdk.serialization.data.CommentConversationApi

fun Comment.toApi(): CommentApi = CommentApi(
    id = id,
    text = text,
    deleted = deleted,
)

fun CommentApi.toModel(): Comment = Comment(
    id = id,
    text = text,
    deleted = deleted,
)

fun CommentConversation.toApi(): CommentConversationApi = CommentConversationApi(
    id = id,
    comments = comments.map { it.toApi() },
)

fun CommentConversationApi.toModel(): CommentConversation = CommentConversation(
    id = id,
    comments = comments.map { it.toModel() },
)

fun Map<String, List<Comment>>.toApi(): List<CommentConversationApi> =
    map { (conversationId, comments) ->
        CommentConversationApi(
            id = conversationId,
            comments = comments.map { comment -> comment.toApi() },
        )
    }

fun Iterable<CommentConversationApi>.toCommentMap(): Map<String, List<Comment>> {
    val result = mutableMapOf<String, List<Comment>>()

    for (conversation in this) {
        require(conversation.comments.isNotEmpty()) {
            "Comment conversations must contain at least one comment"
        }
        require(conversation.id !in result) {
            "Duplicate comment conversation id: ${conversation.id}"
        }
        result[conversation.id] = conversation.comments.map { comment -> comment.toModel() }
    }

    return result
}

package io.writeopia.sdk.serialization.extensions

import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.serialization.data.CommentApi
import io.writeopia.sdk.serialization.data.CommentConversationApi

fun Comment.toApi(): CommentApi = CommentApi(
    id = id,
    text = text,
)

fun CommentApi.toModel(): Comment = Comment(
    id = id,
    text = text,
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

fun Iterable<CommentConversationApi>.toCommentMap(): Map<String, List<Comment>> =
    associate { conversation ->
        conversation.id to conversation.comments.map { comment -> comment.toModel() }
    }

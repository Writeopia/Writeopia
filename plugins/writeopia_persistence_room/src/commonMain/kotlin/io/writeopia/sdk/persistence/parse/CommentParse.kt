package io.writeopia.sdk.persistence.parse

import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.persistence.entity.comment.CommentEntity

fun Map<String, List<Comment>>.toCommentEntities(documentId: String): List<CommentEntity> =
    flatMap { (conversationId, comments) ->
        comments.mapIndexed { commentPosition, comment ->
            CommentEntity(
                id = comment.id,
                conversationId = conversationId,
                documentId = documentId,
                commentPosition = commentPosition,
                text = comment.text,
                deleted = comment.deleted,
            )
        }
    }

fun CommentEntity.toModel(): Comment =
    Comment(
        id = id,
        text = text,
        deleted = deleted,
    )

fun Iterable<CommentEntity>.toCommentConversations(): Map<String, List<Comment>> =
    groupBy { it.conversationId }
        .mapValues { (_, entities) -> entities.map(CommentEntity::toModel) }

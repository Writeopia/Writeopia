package io.writeopia.sdk.persistence.parse

import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.persistence.entity.comment.CommentEntity

fun List<CommentConversation>.toCommentEntities(documentId: String): List<CommentEntity> =
    flatMapIndexed { conversationPosition, conversation ->
        conversation.comments.mapIndexed { commentPosition, comment ->
            CommentEntity(
                id = comment.id,
                conversationId = conversation.id,
                documentId = documentId,
                conversationPosition = conversationPosition,
                commentPosition = commentPosition,
                text = comment.text,
            )
        }
    }

fun List<CommentEntity>.toCommentConversations(): List<CommentConversation> =
    groupBy { it.conversationId }
        .values
        .sortedBy { entities -> entities.minOf { it.conversationPosition } }
        .map { entities ->
            CommentConversation(
                id = entities.first().conversationId,
                comments = entities
                    .sortedBy { it.commentPosition }
                    .map { entity ->
                        Comment(
                            id = entity.id,
                            text = entity.text,
                        )
                    },
            )
        }

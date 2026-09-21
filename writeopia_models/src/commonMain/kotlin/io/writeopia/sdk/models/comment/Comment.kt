package io.writeopia.sdk.models.comment

import io.writeopia.sdk.models.id.GenerateId

data class Comment(
    val id: String = GenerateId.generate(),
    val text: String,
)

data class CommentConversation(
    val id: String = GenerateId.generate(),
    val comments: List<Comment> = emptyList(),
)

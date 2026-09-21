package io.writeopia.sdk.serialization.data

import kotlinx.serialization.Serializable

@Serializable
data class CommentApi(
    val id: String,
    val text: String = "",
)

@Serializable
data class CommentConversationApi(
    val id: String,
    val comments: List<CommentApi> = emptyList(),
)

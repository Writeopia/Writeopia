@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.serialization.data

import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

@Serializable
data class DocumentApi(
    val id: String = "",
    val title: String = "",
    val workspaceId: String,
    val content: List<StoryStepApi> = emptyList(),
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val lastUpdatedAt: Long = Clock.System.now().toEpochMilliseconds(),
    val isFavorite: Boolean = false,
    val lastSyncedAt: Long? = null,
    val parentId: String? = null,
    val isLocked: Boolean = false,
    val icon: IconApi? = null,
    val deleted: Boolean = false,
    val published: Boolean = false,
    val commentConversations: List<CommentConversationApi>? = null,
) {
    @Deprecated("Use primary constructor with commentConversations.")
    constructor(
        id: String,
        title: String,
        workspaceId: String,
        content: List<StoryStepApi>,
        createdAt: Long,
        lastUpdatedAt: Long,
        isFavorite: Boolean,
        lastSyncedAt: Long?,
        parentId: String?,
        isLocked: Boolean,
        icon: IconApi?,
        deleted: Boolean,
        published: Boolean,
    ) : this(
        id = id,
        title = title,
        workspaceId = workspaceId,
        content = content,
        createdAt = createdAt,
        lastUpdatedAt = lastUpdatedAt,
        isFavorite = isFavorite,
        lastSyncedAt = lastSyncedAt,
        parentId = parentId,
        isLocked = isLocked,
        icon = icon,
        deleted = deleted,
        published = published,
        commentConversations = null,
    )
}

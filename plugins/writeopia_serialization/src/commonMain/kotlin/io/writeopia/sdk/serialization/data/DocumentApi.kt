@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.serialization.data

import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime

@Serializable
class DocumentApi(
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
) {
    var commentConversations: List<CommentConversationApi>? = null
        private set

    constructor(
        id: String = "",
        title: String = "",
        workspaceId: String,
        content: List<StoryStepApi> = emptyList(),
        createdAt: Long = Clock.System.now().toEpochMilliseconds(),
        lastUpdatedAt: Long = Clock.System.now().toEpochMilliseconds(),
        isFavorite: Boolean = false,
        lastSyncedAt: Long? = null,
        parentId: String? = null,
        isLocked: Boolean = false,
        icon: IconApi? = null,
        deleted: Boolean = false,
        published: Boolean = false,
        commentConversations: List<CommentConversationApi>?,
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
    ) {
        this.commentConversations = commentConversations
    }

    operator fun component1(): String = id
    operator fun component2(): String = title
    operator fun component3(): String = workspaceId
    operator fun component4(): List<StoryStepApi> = content
    operator fun component5(): Long = createdAt
    operator fun component6(): Long = lastUpdatedAt
    operator fun component7(): Boolean = isFavorite
    operator fun component8(): Long? = lastSyncedAt
    operator fun component9(): String? = parentId
    operator fun component10(): Boolean = isLocked
    operator fun component11(): IconApi? = icon
    operator fun component12(): Boolean = deleted
    operator fun component13(): Boolean = published

    fun copy(
        id: String = this.id,
        title: String = this.title,
        workspaceId: String = this.workspaceId,
        content: List<StoryStepApi> = this.content,
        createdAt: Long = this.createdAt,
        lastUpdatedAt: Long = this.lastUpdatedAt,
        isFavorite: Boolean = this.isFavorite,
        lastSyncedAt: Long? = this.lastSyncedAt,
        parentId: String? = this.parentId,
        isLocked: Boolean = this.isLocked,
        icon: IconApi? = this.icon,
        deleted: Boolean = this.deleted,
        published: Boolean = this.published,
    ): DocumentApi =
        DocumentApi(
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
            commentConversations = commentConversations,
        )

    fun copy(
        id: String = this.id,
        title: String = this.title,
        workspaceId: String = this.workspaceId,
        content: List<StoryStepApi> = this.content,
        createdAt: Long = this.createdAt,
        lastUpdatedAt: Long = this.lastUpdatedAt,
        isFavorite: Boolean = this.isFavorite,
        lastSyncedAt: Long? = this.lastSyncedAt,
        parentId: String? = this.parentId,
        isLocked: Boolean = this.isLocked,
        icon: IconApi? = this.icon,
        deleted: Boolean = this.deleted,
        published: Boolean = this.published,
        commentConversations: List<CommentConversationApi>?,
    ): DocumentApi =
        DocumentApi(
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
            commentConversations = commentConversations,
        )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DocumentApi) return false

        return id == other.id &&
            title == other.title &&
            workspaceId == other.workspaceId &&
            content == other.content &&
            createdAt == other.createdAt &&
            lastUpdatedAt == other.lastUpdatedAt &&
            isFavorite == other.isFavorite &&
            lastSyncedAt == other.lastSyncedAt &&
            parentId == other.parentId &&
            isLocked == other.isLocked &&
            icon == other.icon &&
            deleted == other.deleted &&
            published == other.published &&
            commentConversations == other.commentConversations
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + workspaceId.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + lastUpdatedAt.hashCode()
        result = 31 * result + isFavorite.hashCode()
        result = 31 * result + (lastSyncedAt?.hashCode() ?: 0)
        result = 31 * result + (parentId?.hashCode() ?: 0)
        result = 31 * result + isLocked.hashCode()
        result = 31 * result + (icon?.hashCode() ?: 0)
        result = 31 * result + deleted.hashCode()
        result = 31 * result + published.hashCode()
        result = 31 * result + (commentConversations?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "DocumentApi(id=$id, title=$title, workspaceId=$workspaceId, content=$content, " +
            "createdAt=$createdAt, lastUpdatedAt=$lastUpdatedAt, isFavorite=$isFavorite, " +
            "lastSyncedAt=$lastSyncedAt, parentId=$parentId, isLocked=$isLocked, icon=$icon, " +
            "deleted=$deleted, published=$published, commentConversations=$commentConversations)"
}

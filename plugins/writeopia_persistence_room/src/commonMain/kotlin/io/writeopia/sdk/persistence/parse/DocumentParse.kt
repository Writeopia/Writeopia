@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.parse

import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.persistence.entity.document.DocumentEntity
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun DocumentEntity.toModel(
    content: Map<String, StoryStep> = emptyMap(),
    commentConversations: Map<String, List<Comment>> = emptyMap(),
    tagIds: Set<String> = emptySet(),
) = Document(
    id = id,
    title = title,
    content = content,
    commentConversations = commentConversations,
    tagIds = tagIds,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    lastUpdatedAt = Instant.fromEpochMilliseconds(lastUpdatedAt),
    workspaceId = workspaceId,
    favorite = favorite,
    parentId = parentId,
    isLocked = isLocked,
    icon = icon?.let { label -> MenuItem.Icon(label, iconTint) },
    lastSyncedAt = lastSyncedAt?.let(Instant::fromEpochMilliseconds),
    deleted = isDeleted
)

fun Document.toEntity() = DocumentEntity(
    id = id,
    title = title,
    createdAt = createdAt.toEpochMilliseconds(),
    lastUpdatedAt = lastUpdatedAt.toEpochMilliseconds(),
    lastSyncedAt = lastSyncedAt?.toEpochMilliseconds(),
    workspaceId = workspaceId,
    favorite = favorite,
    parentId = parentId,
    isLocked = isLocked,
    icon = icon?.label,
    iconTint = icon?.tint,
    isDeleted = deleted
)

@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.parse

import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.persistence.entity.document.DocumentEntity
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

fun DocumentEntity.toModel(
    content: Map<Double, StoryStep> = emptyMap(),
    commentConversations: List<CommentConversation> = emptyList(),
) = Document(
    id = id,
    title = title,
    content = content,
    commentConversations = commentConversations,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    lastUpdatedAt = Instant.fromEpochMilliseconds(lastUpdatedAt),
    workspaceId = workspaceId,
    favorite = favorite,
    parentId = parentId,
    isLocked = isLocked,
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
    isDeleted = deleted
)

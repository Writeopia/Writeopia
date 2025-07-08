package io.writeopia.sdk.persistence.parse

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.persistence.entity.document.DocumentEntity
import kotlinx.datetime.Instant

fun DocumentEntity.toModel(content: Map<Int, StoryStep> = emptyMap()) = Document(
    id = id,
    title = title,
    content = content,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    lastUpdatedAt = Instant.fromEpochMilliseconds(lastUpdatedAt),
    workspaceId = userId,
    favorite = favorite,
    parentId = parentId,
    isLocked = isLocked,
    lastSyncedAt = lastSyncedAt?.let(Instant::fromEpochMilliseconds)
)

fun Document.toEntity() = DocumentEntity(
    id = id,
    title = title,
    createdAt = createdAt.toEpochMilliseconds(),
    lastUpdatedAt = lastUpdatedAt.toEpochMilliseconds(),
    userId = workspaceId,
    favorite = favorite,
    parentId = parentId,
    isLocked = isLocked
)

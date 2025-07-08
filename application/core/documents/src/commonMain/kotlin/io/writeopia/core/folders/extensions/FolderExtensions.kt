package io.writeopia.core.folders.extensions

import io.writeopia.app.sql.FolderEntity
import io.writeopia.common.utils.extensions.toLong
import io.writeopia.sdk.models.document.Folder

fun Folder.toEntity() = FolderEntity(
    id = id,
    parent_id = parentId,
    user_id = workspaceId,
    title = title,
    created_at = createdAt.toEpochMilliseconds(),
    last_updated_at = lastUpdatedAt.toEpochMilliseconds(),
    favorite = favorite.toLong(),
    icon = icon?.label,
    icon_tint = icon?.tint?.toLong(),
    last_synced_at = lastSyncedAt?.toEpochMilliseconds()
)

@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.extensions

import io.writeopia.app.sql.FolderEntity
import io.writeopia.common.utils.extensions.toLong
import io.writeopia.sdk.models.document.Folder
import kotlin.time.ExperimentalTime

fun Folder.toEntity() = FolderEntity(
    id = id,
    parent_id = parentId,
    workspace_id = workspaceId,
    title = title,
    created_at = createdAt.toEpochMilliseconds(),
    last_updated_at = lastUpdatedAt.toEpochMilliseconds(),
    favorite = favorite.toLong(),
    icon = icon?.label,
    icon_tint = icon?.tint?.toLong(),
    last_synced_at = lastSyncedAt?.toEpochMilliseconds()
)

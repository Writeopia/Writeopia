@file:OptIn(ExperimentalTime::class)

package io.writeopia.common.utils.persistence.folder

import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import kotlin.time.ExperimentalTime

import kotlin.time.Instant

fun FolderCommonEntity.toModel(itemCount: Long): Folder =
    Folder(
        id = id,
        parentId = parentId,
        title = title,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        lastUpdatedAt = Instant.fromEpochMilliseconds(lastUpdatedAt),
        workspaceId = workspaceId,
        favorite = favorite,
        // Assuming itemCount is not stored in the entity
        itemCount = itemCount,
        icon = icon.let { MenuItem.Icon(it, iconTint.toInt()) },
    )

fun Folder.toRoomEntity(): FolderCommonEntity =
    FolderCommonEntity(
        id = id,
        parentId = parentId,
        title = title,
        createdAt = createdAt.toEpochMilliseconds(),
        lastUpdatedAt = lastUpdatedAt.toEpochMilliseconds(),
        workspaceId = workspaceId,
        favorite = favorite,
        icon = icon?.label ?: "",
        iconTint = icon?.tint?.toLong() ?: 0L
    )

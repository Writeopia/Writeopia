package io.writeopia.common.utils.persistence.folder

import io.writeopia.sdk.models.document.Folder

import kotlin.time.Instant

fun FolderCommonEntity.toModel(itemCount: Long): Folder {
    return Folder(
        id = id,
        parentId = parentId,
        title = title,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        lastUpdatedAt = Instant.fromEpochMilliseconds(lastUpdatedAt),
        userId = userId,
        favorite = favorite,
        // Assuming itemCount is not stored in the entity
        itemCount = itemCount,
    )
}

fun Folder.toRoomEntity(): FolderCommonEntity {
    return FolderCommonEntity(
        id = id,
        parentId = parentId,
        title = title,
        createdAt = createdAt.toEpochMilliseconds(),
        lastUpdatedAt = lastUpdatedAt.toEpochMilliseconds(),
        userId = userId,
        favorite = favorite
    )
}

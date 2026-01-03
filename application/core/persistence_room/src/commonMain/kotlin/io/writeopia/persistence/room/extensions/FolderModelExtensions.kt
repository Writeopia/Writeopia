package io.writeopia.persistence.room.extensions

import io.writeopia.common.utils.persistence.folder.FolderCommonEntity
import io.writeopia.persistence.room.data.entities.FolderEntity

fun FolderEntity.toCommonEntity(): FolderCommonEntity {
    return FolderCommonEntity(
        id = this.id,
        parentId = this.parentId,
        title = this.title,
        createdAt = this.createdAt,
        lastUpdatedAt = this.lastUpdatedAt,
        workspaceId = this.workspaceId,
        favorite = this.favorite,
        icon = this.icon,
        iconTint = this.iconTint,
    )
}

fun FolderCommonEntity.toEntity(): FolderEntity {
    return FolderEntity(
        id = this.id,
        parentId = this.parentId,
        title = this.title,
        createdAt = this.createdAt,
        lastUpdatedAt = this.lastUpdatedAt,
        workspaceId = this.workspaceId,
        favorite = this.favorite,
        icon = this.icon,
        iconTint = this.iconTint,
    )
}

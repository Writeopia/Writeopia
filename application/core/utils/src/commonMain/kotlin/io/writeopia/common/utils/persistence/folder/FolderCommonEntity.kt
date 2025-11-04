package io.writeopia.common.utils.persistence.folder

data class FolderCommonEntity(
    val id: String,
    val parentId: String,
    val title: String,
    val createdAt: Long,
    val lastUpdatedAt: Long,
    val workspaceId: String,
    val favorite: Boolean = false
)

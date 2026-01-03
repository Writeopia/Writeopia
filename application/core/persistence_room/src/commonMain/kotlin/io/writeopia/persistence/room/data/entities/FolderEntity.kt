package io.writeopia.persistence.room.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

internal const val FOLDER_ENTITY: String = "FOLDER_ENTITY_TABLE"

@Entity(tableName = FOLDER_ENTITY)
class FolderEntity(
    @PrimaryKey @ColumnInfo(name = "folder_id") val id: String,
    @ColumnInfo(name = "parent_id") val parentId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_updated_at") val lastUpdatedAt: Long,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "favorite") val favorite: Boolean = false,
    @ColumnInfo(name = "icon") val icon: String,
    @ColumnInfo(name = "icon_tint") val iconTint: Long,
)

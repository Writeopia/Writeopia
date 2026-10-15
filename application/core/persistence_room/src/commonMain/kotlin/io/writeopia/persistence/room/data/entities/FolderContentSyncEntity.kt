package io.writeopia.persistence.room.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity

internal const val FOLDER_CONTENT_SYNC_ENTITY: String = "FOLDER_CONTENT_SYNC_TABLE"

/**
 * When this device last received the contents (documents and subfolders) of a folder. Kept
 * apart from [FolderEntity]: the root has no folder row, and a folder received inside its
 * parent is in sync itself while its contents have not been fetched yet.
 */
@Entity(tableName = FOLDER_CONTENT_SYNC_ENTITY, primaryKeys = ["folder_id", "workspace_id"])
class FolderContentSyncEntity(
    @ColumnInfo(name = "folder_id") val folderId: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "content_synced_at") val contentSyncedAt: Long,
)

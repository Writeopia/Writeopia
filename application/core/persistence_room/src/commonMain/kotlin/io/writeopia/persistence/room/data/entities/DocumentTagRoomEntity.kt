package io.writeopia.persistence.room.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

internal const val DOCUMENT_TAG_ENTITY: String = "DOCUMENT_TAG_ENTITY_TABLE"

@Entity(
    tableName = DOCUMENT_TAG_ENTITY,
    indices = [Index(value = ["workspace_id"])],
)
class DocumentTagRoomEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "description") val description: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_updated_at") val lastUpdatedAt: Long,
    @ColumnInfo(name = "last_synced_at") val lastSyncedAt: Long?,
    @ColumnInfo(name = "deleted") val deleted: Boolean,
)

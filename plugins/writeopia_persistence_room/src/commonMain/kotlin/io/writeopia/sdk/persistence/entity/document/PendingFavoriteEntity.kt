package io.writeopia.sdk.persistence.entity.document

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

internal const val PENDING_FAVORITE_ENTITY: String = "PENDING_FAVORITE_TABLE"

/**
 * The favorite of a document changed on this device that the backend has not been told about
 * yet. One row per document; it goes once the backend has the change.
 */
@Entity(tableName = PENDING_FAVORITE_ENTITY)
data class PendingFavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "favorite") val favorite: Boolean,
    @ColumnInfo(name = "changed_at") val changedAt: Long
)

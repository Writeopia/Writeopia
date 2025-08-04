package io.writeopia.persistence.room.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workspace_entity")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val lastSyncedAt: Long,
    val icon: String?,
    val iconTint: Int?,
    val selected: Boolean
)

package io.writeopia.sdk.persistence.entity.story

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

internal const val STORY_STEP_TOMBSTONE_ENTITY: String = "STORY_STEP_TOMBSTONE_TABLE"

/**
 * A step deleted on this device that the backend has not been told about yet. A step with a
 * tombstone is never saved again; the tombstone goes once the backend has it.
 */
@Entity(tableName = STORY_STEP_TOMBSTONE_ENTITY)
data class StoryStepTombstoneEntity(
    @PrimaryKey @ColumnInfo(name = "step_id") val stepId: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "workspace_id") val workspaceId: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long
)

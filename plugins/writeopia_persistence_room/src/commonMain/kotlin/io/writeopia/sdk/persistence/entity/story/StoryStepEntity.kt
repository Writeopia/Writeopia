package io.writeopia.sdk.persistence.entity.story

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

internal const val STORY_UNIT_ENTITY: String = "STORY_UNIT_ENTITY_TABLE"

@Entity(tableName = STORY_UNIT_ENTITY)
data class StoryStepEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "localId") val localId: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    @ColumnInfo(name = "url") val url: String? = null,
    @ColumnInfo(name = "path") val path: String? = null,
    @ColumnInfo(name = "text") val text: String? = null,
    @ColumnInfo(name = "checked") val checked: Boolean? = false,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "is_group") val isGroup: Boolean,
    @ColumnInfo(name = "has_inner_steps") val hasInnerSteps: Boolean,
    @ColumnInfo(name = "background_color") val backgroundColor: Int?,
    @ColumnInfo(name = "tags") val tags: String,
    @ColumnInfo(name = "spans") val spans: String,
    @ColumnInfo(name = "link_to_document") val linkToDocument: String? = null
)

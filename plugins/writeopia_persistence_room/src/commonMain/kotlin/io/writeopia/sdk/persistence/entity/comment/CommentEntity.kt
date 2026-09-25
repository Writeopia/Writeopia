package io.writeopia.sdk.persistence.entity.comment

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

internal const val COMMENT_ENTITY: String = "COMMENT_ENTITY_TABLE"

@Entity(tableName = COMMENT_ENTITY)
data class CommentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "comment_position") val commentPosition: Int,
    @ColumnInfo(name = "text") val text: String,
)

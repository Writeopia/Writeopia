package io.writeopia.sdk.persistence.entity.tag

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

internal const val DOCUMENT_TAG_LINK_ENTITY: String = "DOCUMENT_TAG_LINK_TABLE"

/**
 * Which tags of the workspace catalogue a document carries. Deliberately without a foreign key to
 * the document: the document DAO inserts with REPLACE on every metadata save, and a cascade would
 * wipe the links each time.
 */
@Entity(
    tableName = DOCUMENT_TAG_LINK_ENTITY,
    primaryKeys = ["document_id", "tag_id"],
    indices = [Index(value = ["document_id"])],
)
data class DocumentTagLinkEntity(
    @ColumnInfo(name = "document_id") val documentId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)

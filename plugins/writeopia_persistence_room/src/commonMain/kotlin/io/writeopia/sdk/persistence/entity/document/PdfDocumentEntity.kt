@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.entity.document

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.writeopia.sdk.models.CREATED_AT
import io.writeopia.sdk.models.FAVORITE
import io.writeopia.sdk.models.FILE_PATH
import io.writeopia.sdk.models.ICON
import io.writeopia.sdk.models.IS_DELETED
import io.writeopia.sdk.models.LAST_SYNCED_AT
import io.writeopia.sdk.models.LAST_UPDATED_AT
import io.writeopia.sdk.models.PARENT_ID
import io.writeopia.sdk.models.PDF_DOCUMENT_ENTITY
import io.writeopia.sdk.models.TITLE
import io.writeopia.sdk.models.WORKSPACE_ID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Entity(tableName = PDF_DOCUMENT_ENTITY)
data class PdfDocumentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(TITLE) val title: String,
    @ColumnInfo(FILE_PATH) val filePath: String,
    @ColumnInfo(CREATED_AT) val createdAt: Long,
    @ColumnInfo(LAST_UPDATED_AT) val lastUpdatedAt: Long,
    @ColumnInfo(LAST_SYNCED_AT) val lastSyncedAt: Long? = null,
    @ColumnInfo(WORKSPACE_ID) val workspaceId: String,
    @ColumnInfo(FAVORITE) val favorite: Boolean,
    @ColumnInfo(PARENT_ID) val parentId: String,
    @ColumnInfo(ICON) val icon: String? = null,
    @ColumnInfo(IS_DELETED) val isDeleted: Boolean,
) {
    companion object {
        fun createById(
            id: String,
            workspaceId: String,
            parentId: String,
            filePath: String,
            title: String
        ): PdfDocumentEntity {
            val now = Clock.System.now().toEpochMilliseconds()
            return PdfDocumentEntity(
                id,
                title,
                filePath,
                now,
                now,
                null,
                workspaceId,
                favorite = false,
                parentId = parentId,
                isDeleted = false
            )
        }
    }
}

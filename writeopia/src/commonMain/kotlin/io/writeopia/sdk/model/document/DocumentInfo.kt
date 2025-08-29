package io.writeopia.sdk.model.document

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.id.GenerateId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Dto class to keep information about the document
 */
data class DocumentInfo(
    val id: String = GenerateId.generate(),
    val title: String = "",
    val createdAt: Instant,
    val lastUpdatedAt: Instant,
    val lastSyncedAt: Instant?,
    val isLocked: Boolean,
    val parentId: String = "root",
    val icon: MenuItem.Icon? = null,
    val isFavorite: Boolean,
    val userId: String,
    val companyId: String?
) {
    companion object {
        fun empty(): DocumentInfo {
            val now = Clock.System.now()
            return DocumentInfo(
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = null,
                isLocked = false,
                isFavorite = false,
                userId = "",
                companyId = ""
            )
        }
    }
}

fun Document.info(userId: String = "", companyId: String? = null): DocumentInfo = DocumentInfo(
    id = this.id,
    title = this.title,
    createdAt = this.createdAt,
    lastUpdatedAt = this.lastUpdatedAt,
    lastSyncedAt = this.lastSyncedAt,
    parentId = this.parentId,
    icon = this.icon,
    isLocked = this.isLocked,
    isFavorite = favorite,
    userId = userId,
    companyId = companyId
)

fun DocumentInfo.document(userId: String): Document = Document(
    id = this.id,
    title = this.title,
    createdAt = this.createdAt,
    lastUpdatedAt = this.lastUpdatedAt,
    lastSyncedAt = this.lastSyncedAt,
    parentId = this.parentId,
    icon = this.icon,
    isLocked = this.isLocked,
    favorite = this.isFavorite,
    workspaceId = userId
)

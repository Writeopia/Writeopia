@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.models.document

import io.writeopia.sdk.models.id.GenerateId
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Represents a PDF document in the system.
 * Unlike regular [Document]s which contain rich text content,
 * PdfDocuments reference external PDF files.
 */
data class PdfDocument(
    override val id: String = GenerateId.generate(),
    override val title: String = "",
    val filePath: String,
    override val createdAt: Instant,
    override val lastUpdatedAt: Instant,
    val lastSyncedAt: Instant?,
    override val workspaceId: String,
    override val parentId: String,
    override val favorite: Boolean = false,
    override val icon: MenuItem.Icon? = null,
    val deleted: Boolean = false,
) : MenuItem

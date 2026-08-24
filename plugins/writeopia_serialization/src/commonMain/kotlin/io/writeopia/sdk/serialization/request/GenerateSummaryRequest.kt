package io.writeopia.sdk.serialization.request

import kotlinx.serialization.Serializable

@Serializable
data class GenerateSummaryRequest(
    val documents: List<DocumentSyncInfo>,
    val targetFolderId: String,
    val summaryTitle: String? = null,
    val model: String? = null
)

@Serializable
data class DocumentSyncInfo(
    val documentId: String,
    val lastSyncedAt: Long?
)

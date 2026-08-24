package io.writeopia.sdk.serialization.response

import io.writeopia.sdk.serialization.data.DocumentApi
import kotlinx.serialization.Serializable

@Serializable
data class GenerateSummaryResponse(
    val document: DocumentApi? = null,
    val unsyncedDocuments: List<UnsyncedDocumentInfo>? = null,
    val error: String? = null
)

@Serializable
data class UnsyncedDocumentInfo(
    val documentId: String,
    val documentTitle: String,
    val lastUpdatedAt: Long,
    val lastSyncedAt: Long?
)

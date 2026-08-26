package io.writeopia.api.export.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportResult(
    val signedUrl: String,
    val documentCount: Int,
    val folderCount: Int,
    val exportedAt: Long
)

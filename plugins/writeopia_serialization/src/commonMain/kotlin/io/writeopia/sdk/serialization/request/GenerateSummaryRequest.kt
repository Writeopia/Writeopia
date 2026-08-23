package io.writeopia.sdk.serialization.request

import kotlinx.serialization.Serializable

@Serializable
data class GenerateSummaryRequest(
    val documentIds: List<String>,
    val targetFolderId: String,
    val summaryTitle: String? = null,
    val model: String? = null
)

package io.writeopia.sdk.serialization.response

import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.data.FolderApi
import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceDiffResponse(
    val folders: List<FolderApi> = emptyList(),
    val documents: List<DocumentApi> = emptyList()
)

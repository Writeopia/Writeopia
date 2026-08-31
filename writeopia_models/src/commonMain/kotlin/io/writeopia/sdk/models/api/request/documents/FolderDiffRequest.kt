package io.writeopia.sdk.models.api.request.documents

import kotlinx.serialization.Serializable

@Serializable
data class FolderDiffRequest(
    val folderId: String,
    val workspaceId: String,
    val lastFolderSync: Long,
    val orderBy: String = "last_updated_at"
)

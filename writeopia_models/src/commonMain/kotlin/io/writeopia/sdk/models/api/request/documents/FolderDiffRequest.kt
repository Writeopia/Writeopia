package io.writeopia.sdk.models.api.request.documents

import kotlinx.serialization.Serializable

@Serializable
data class FolderDiffRequest(val folderId: String, val lastFolderSync: Long)

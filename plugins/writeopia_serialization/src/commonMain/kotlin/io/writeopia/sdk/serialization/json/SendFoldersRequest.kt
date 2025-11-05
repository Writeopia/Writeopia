package io.writeopia.sdk.serialization.json

import io.writeopia.sdk.serialization.data.FolderApi
import kotlinx.serialization.Serializable

@Serializable
class SendFoldersRequest(val folders: List<FolderApi>, val workspaceId: String)

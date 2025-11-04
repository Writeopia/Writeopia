package io.writeopia.models.interfaces.search

import io.writeopia.sdk.models.document.Folder

interface FolderSearch {

    suspend fun search(query: String, workspaceId: String): List<Folder>

    suspend fun getLastUpdated(): List<Folder>
}

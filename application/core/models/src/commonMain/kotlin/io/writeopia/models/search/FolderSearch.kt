package io.writeopia.models.search

import io.writeopia.sdk.models.document.Folder

interface FolderSearch {

    suspend fun search(query: String): List<Folder>

    suspend fun getLastUpdated(): List<Folder>
}

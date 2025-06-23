package io.writeopia.documents.graph.repository

import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.documents.graph.ItemData
import io.writeopia.sdk.models.utils.toAdjencyList
import io.writeopia.sdk.repository.DocumentRepository

class GraphRepository(
    private val folderRepository: FolderRepository,
    private val documentRepository: DocumentRepository
) {

    suspend fun loadAllDocumentsAsAdjacencyList(userId: String): Map<ItemData, List<ItemData>> {
        val folders = folderRepository.getFoldersForUser(userId)
        val documents = documentRepository.loadDocumentsForUser(userId)

        val root = ItemData(id = "root", title = "", parentId = "", isFolder = true)
        val items =
            folders.map { item ->
                ItemData(
                    item.id,
                    item.title,
                    item.parentId,
                    isFolder = true
                )
            } + documents.map { item ->
                ItemData(
                    item.id,
                    item.title,
                    item.parentId,
                    isFolder = false
                )
            }

        val graph = (items + root).toAdjencyList()
        return graph
    }
}

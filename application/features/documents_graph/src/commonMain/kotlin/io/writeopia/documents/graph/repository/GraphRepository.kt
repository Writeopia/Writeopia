package io.writeopia.documents.graph.repository

import io.writeopia.core.folders.repository.FolderRepository
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

        val items = folders + documents
        val graph = items.map { item -> ItemData(item.id, item.title, item.parentId) }
            .toAdjencyList()
        
        return graph
    }
}

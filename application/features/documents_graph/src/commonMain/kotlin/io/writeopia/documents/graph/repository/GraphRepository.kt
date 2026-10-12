package io.writeopia.documents.graph.repository

import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.core.folders.repository.tag.DocumentTagRepository
import io.writeopia.documents.graph.GraphItemDetails
import io.writeopia.documents.graph.ItemData
import io.writeopia.sdk.models.utils.toAdjencyList
import io.writeopia.sdk.repository.DocumentRepository

class GraphRepository(
    private val folderRepository: FolderRepository,
    private val documentRepository: DocumentRepository,
    private val documentTagRepository: DocumentTagRepository? = null,
) {

    suspend fun loadAllDocumentsAsAdjacencyList(workspaceId: String): Map<ItemData, List<ItemData>> {
        val folders = folderRepository.getFoldersForWorkspace(workspaceId)
        val documents = documentRepository.loadDocumentsWorkspace(workspaceId)

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

    /**
     * The documents grouped by the tags they carry: every tag of the catalogue is a hub under the
     * root, each document hangs from each of its tags, and the documents without a tag hang from
     * the root. Folders don't appear.
     */
    suspend fun loadDocumentsByTagsAsAdjacencyList(workspaceId: String): Map<ItemData, List<ItemData>> {
        val tags = documentTagRepository?.getTagsForWorkspace(workspaceId) ?: emptyList()
        val documents = documentRepository.loadDocumentsWorkspace(workspaceId)

        val root = ItemData(id = ROOT_ID, title = "", parentId = "", isFolder = true)
        val tagItems = tags.associate { tag ->
            tag.id to ItemData(id = tag.id, title = tag.name, parentId = ROOT_ID, isFolder = true, isTag = true)
        }

        val byTag: Map<ItemData, List<ItemData>> = tags.associate { tag ->
            tagItems.getValue(tag.id) to documents
                .filter { document -> tag.id in document.tagIds }
                .map { document -> ItemData(document.id, document.title, parentId = tag.id, isFolder = false) }
        }

        val untagged = documents
            .filter { document -> document.tagIds.none { tagId -> tagId in tagItems } }
            .map { document -> ItemData(document.id, document.title, parentId = ROOT_ID, isFolder = false) }

        return byTag + (root to (tagItems.values.toList() + untagged))
    }

    /** What the clicked node is, a tag, a folder or a document, with what the card shows about it. */
    suspend fun loadDetails(id: String, workspaceId: String): GraphItemDetails? {
        documentTagRepository?.getTagById(id)?.takeIf { !it.deleted }?.let { tag ->
            val documentIds = documentRepository.loadDocumentsWorkspace(workspaceId)
                .filter { document -> tag.id in document.tagIds }
                .map { it.id }
            return GraphItemDetails.TagDetails(
                id = tag.id,
                title = tag.name,
                description = tag.description,
                documentIds = documentIds,
            )
        }

        folderRepository.getFolderById(id)?.let { folder ->
            return GraphItemDetails.FolderDetails(
                id = folder.id,
                title = folder.title,
                folderCount = folderRepository.getFolderByParentId(folder.id, workspaceId).size,
                documentCount = documentRepository.loadDocumentsByParentId(folder.id, workspaceId).size,
            )
        }

        val document = documentRepository.loadDocumentById(id, workspaceId) ?: return null
        val tagNames = documentTagRepository
            ?.let { repository -> document.tagIds.mapNotNull { tagId -> repository.getTagById(tagId)?.name } }
            ?: emptyList()
        val folderName = document.parentId
            .takeIf { it.isNotBlank() && it != ROOT_ID }
            ?.let { folderRepository.getFolderById(it)?.title }

        return GraphItemDetails.DocumentDetails(
            id = document.id,
            title = document.title,
            lastUpdatedAt = document.lastUpdatedAt,
            folderName = folderName,
            tagNames = tagNames,
        )
    }

    companion object {
        const val ROOT_ID = "root"
    }
}

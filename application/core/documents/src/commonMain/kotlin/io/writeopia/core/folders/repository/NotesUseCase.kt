package io.writeopia.core.folders.repository

import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.collections.merge
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.network.notes.NotesApi
import io.writeopia.sdk.persistence.core.sorting.OrderBy
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.models.sorting.OrderBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * UseCase responsible to perform CRUD operations in the Notes (Documents) of the app taking in to
 * consideration the configuration desired in the app.
 */
class NotesUseCase private constructor(
    private val documentRepository: DocumentRepository,
    private val notesConfig: ConfigurationRepository,
    private val folderRepository: FolderRepository,
    private val notesApi: NotesApi? = null
) {

    suspend fun createFolder(name: String, userId: String) {
        folderRepository.createFolder(Folder.fromName(name, userId))
    }

    suspend fun updateFolder(folder: Folder) {
        folderRepository.updateFolder(folder)
    }

    suspend fun updateFolderById(id: String, folderChange: (Folder) -> Folder) {
        folderRepository.getFolderById(id)
            ?.let(folderChange)
            ?.let { newFolder -> folderRepository.updateFolder(newFolder) }
            ?.also { folderRepository.refreshFolders() }
    }

    suspend fun updateDocumentById(id: String, documentChange: (Document) -> Document) {
        documentRepository.loadDocumentById(id)
            ?.let(documentChange)
            ?.let { newDocument -> documentRepository.saveDocumentMetadata(newDocument) }
            ?.also { documentRepository.refreshDocuments() }
    }

    suspend fun moveItem(menuItem: MenuItemUi, parentId: String) {
        when (menuItem) {
            is MenuItemUi.DocumentUi -> {
                documentRepository.moveToFolder(menuItem.documentId, parentId)
            }

            is MenuItemUi.FolderUi -> {
                folderRepository.moveToFolder(menuItem.documentId, parentId)
            }
        }

        folderRepository.setLastUpdated(parentId, Clock.System.now().toEpochMilliseconds())
        folderRepository.refreshFolders()
    }

    suspend fun moveItemsById(ids: Iterable<String>, parentId: String) {
        val items = loadDocumentsByIds(ids).filter { it.id != parentId }

        items.forEach { moveItem(it, parentId) }
        folderRepository.refreshFolders()
    }

    suspend fun loadDocumentsForUserFromDb(userId: String): List<Document> =
        documentRepository.loadDocumentsForUser(userId)

    suspend fun loadDocumentsForUserAfterTimeFromDb(userId: String, time: Instant): List<Document> =
        notesConfig.getOrderPreference(userId)
            .let { orderBy ->
                documentRepository.loadDocumentsForUserAfterTime(
                    orderBy,
                    userId,
                    time
                )
            }

    suspend fun loadFolderForUserAfterTime(userId: String, time: Instant): List<Folder> =
        folderRepository.getFoldersForUserAfterTime(userId, time)

    suspend fun loadFoldersForUser(userId: String): List<Folder> =
        folderRepository.getFoldersForUser(userId)

    private suspend fun loadDocumentsByIds(ids: Iterable<String>): List<MenuItem> {
        val folders = ids.mapNotNull { id -> folderRepository.getFolderById(id) }
        val documents = ids.mapNotNull { id -> documentRepository.loadDocumentById(id) }

        return (folders + documents)
    }

    /**
     * Listen and gets [MenuItem] groups by  parent folder.
     * This will return all folders that this method was called for (using the parentId).
     *
     * @param parentId The id of the folder
     */
    suspend fun listenForMenuItemsByParentId(
        parentId: String,
        userId: String,
    ): Flow<Map<String, List<MenuItem>>> =
        combine(
            listenForFoldersByParentId(parentId),
            listenForDocumentsByParentId(parentId),
            notesConfig.listenOrderPreference(userId)
        ) { folders, documents, orderPreference ->
            val order =
                orderPreference.takeIf { it.isNotEmpty() }?.let(OrderBy.Companion::fromString)
                    ?: OrderBy.UPDATE

            folders.merge(documents).mapValues { (_, menuItems) ->
                menuItems.sortedWithOrderBy(order)
            }
        }

    suspend fun listenForMenuItemsPerFolderId(
        notesNavigation: NotesNavigation,
        userId: String,
    ): Flow<Map<String, List<MenuItem>>> =
        when (notesNavigation) {
            NotesNavigation.Favorites -> listenForMenuItemsByParentId(Folder.ROOT_PATH, userId)

            is NotesNavigation.Folder -> listenForMenuItemsByParentId(notesNavigation.id, userId)

            NotesNavigation.Root -> listenForMenuItemsByParentId(Folder.ROOT_PATH, userId)
        }

    suspend fun stopListeningForMenuItemsByParentId(id: String) {
        folderRepository.stopListeningForFoldersByParentId(id)
        documentRepository.stopListeningForFoldersByParentId(id)
    }

    suspend fun duplicateDocuments(ids: List<String>, userId: String) {
        duplicateNotes(ids, userId)
        duplicateFolders(ids)
    }

    suspend fun saveDocumentDb(document: Document) {
        documentRepository.saveDocument(document)
        documentRepository.refreshDocuments()
    }

    suspend fun saveDocumentCloud(document: Document): Boolean {
        val isSuccess = notesApi?.createDocument(document) ?: false

        if (isSuccess) {
            documentRepository.saveDocument(document.copy(cloudSynced = true))
        }

        documentRepository.refreshDocuments()

        return isSuccess
    }

    suspend fun deleteNotes(ids: Set<String>) {
        documentRepository.deleteDocumentByIds(ids)
        ids.forEach { id ->
            deleteFolderById(id)
        }
    }

    suspend fun deleteFolderById(folderId: String) {
        documentRepository.deleteDocumentByFolder(folderId)
        folderRepository.deleteFolderByParent(folderId)
        folderRepository.deleteFolderById(folderId)
    }

    suspend fun favoriteDocuments(ids: Set<String>) {
        documentRepository.favoriteDocumentByIds(ids)
        folderRepository.favoriteDocumentByIds(ids)
    }

    suspend fun unFavoriteDocuments(ids: Set<String>) {
        documentRepository.unFavoriteDocumentByIds(ids)
        folderRepository.unFavoriteDocumentByIds(ids)
    }

    private suspend fun listenForDocumentsByParentId(
        parentId: String
    ): Flow<Map<String, List<Document>>> =
        documentRepository.listenForDocumentsByParentId(parentId)

    private suspend fun duplicateNotes(ids: List<String>, userId: String) {
        notesConfig.getOrderPreference(userId).let { orderBy ->
            documentRepository.loadDocumentsWithContentByIds(ids, orderBy)
        }.map { document ->
            document.duplicateWithNewIds()
        }.forEach { document ->
            documentRepository.saveDocument(document)
        }

        documentRepository.refreshDocuments()
    }

    private suspend fun duplicateFolders(ids: List<String>) {
        ids.forEach { id -> duplicateFolderRecursively(id) }

        folderRepository.refreshFolders()
    }

    private suspend fun duplicateFolderRecursively(id: String) {
        val folder = folderRepository.getFolderById(id)

        if (folder != null) {
            duplicateAllFoldersInside(folder)
        }
    }

    private suspend fun duplicateAllFoldersInside(folder: Folder) {
        val newFolder = duplicateFolder(folder)
        val folderList = getFolderIdByParentId(folder.id).map { insideFolder ->
            insideFolder.copy(parentId = newFolder.id)
        }

        if (folderList.isNotEmpty()) {
            folderList.forEach { insideFolder -> duplicateAllFoldersInside(insideFolder) }
        }
    }

    private suspend fun getFolderIdByParentId(parentId: String): List<Folder> =
        folderRepository.getFolderByParentId(parentId)

    private suspend fun duplicateFolder(folder: Folder): Folder {
        // Todo: É necessário mudar o parent id da pasta também
        val newFolder = folder.copy(id = GenerateId.generate())

        return run {
            folderRepository.createFolder(newFolder)

            documentRepository.loadDocumentsByParentId(folder.id)
                .map { document ->
                    document.copy(
                        id = GenerateId.generate(),
                        content = document.content.mapValues { (_, storyStep) ->
                            storyStep.copy(id = GenerateId.generate())
                        },
                        parentId = newFolder.id
                    )
                }.forEach { document ->
                    documentRepository.saveDocument(document)
                }

            newFolder
        }
    }

    /**
     * Listen and gets [MenuItem] groups by  parent folder.
     * This will return all folders that this method was called for (using the parentId).
     *
     * @param parentId The id of the folder
     */
    private suspend fun listenForFoldersByParentId(
        parentId: String
    ): Flow<Map<String, List<Folder>>> =
        folderRepository.listenForFoldersByParentId(parentId)

    private suspend fun moveItem(menuItem: MenuItem, parentId: String) {
        when (menuItem) {
            is Folder -> {
                folderRepository.moveToFolder(menuItem.id, parentId)
            }

            is Document -> {
                documentRepository.moveToFolder(menuItem.id, parentId)
            }
        }

        folderRepository.setLastUpdated(parentId, Clock.System.now().toEpochMilliseconds())
    }

    companion object {
        private var instance: NotesUseCase? = null

        fun singleton(
            documentRepository: DocumentRepository,
            notesConfig: ConfigurationRepository,
            folderRepository: FolderRepository,
            notesApi: NotesApi? = null
        ): NotesUseCase =
            instance ?: NotesUseCase(
                documentRepository,
                notesConfig,
                folderRepository,
                notesApi
            ).also {
                instance = it
            }
    }
}

private fun Document.duplicateWithNewIds(): Document =
    this.copy(
        id = GenerateId.generate(),
        content = this.content.mapValues { (_, storyStep) ->
            storyStep.copy(id = GenerateId.generate())
        }
    )

fun List<MenuItem>.sortedWithOrderBy(orderBy: OrderBy): List<MenuItem> =
    when (orderBy) {
        OrderBy.CREATE -> this.sortedByDescending { menuItem -> menuItem.createdAt }
        OrderBy.UPDATE -> this.sortedByDescending { menuItem -> menuItem.lastUpdatedAt }
        OrderBy.NAME -> this.sortedBy { menuItem -> menuItem.title.lowercase() }
    }

package io.writeopia.core.folders.repository.folder

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.common.utils.collections.merge
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.sorting.OrderBy
import io.writeopia.sdk.repository.DocumentRepository
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
    private val authRepository: AuthRepository
) {

    suspend fun createFolder(name: String, workspaceId: String) {
        folderRepository.createFolder(Folder.fromName(name, workspaceId))
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

    suspend fun updateDocumentById(
        id: String,
        workspaceId: String,
        documentChange: (Document) -> Document
    ) {
        documentRepository.loadDocumentById(id, workspaceId)
            ?.let(documentChange)
            ?.let { newDocument ->
                documentRepository.saveDocumentMetadata(newDocument)
            }?.also { documentRepository.refreshDocuments() }
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

    suspend fun moveItemsById(ids: Iterable<String>, workspaceId: String, parentId: String) {
        val items = loadDocumentsByIds(ids, workspaceId).filter { it.id != parentId }

        items.forEach { moveItem(it, parentId) }
        folderRepository.refreshFolders()
    }

    suspend fun loadDocumentsForWorkspaceFromDb(workspaceId: String): List<Document> =
        documentRepository.loadDocumentsWorkspace(workspaceId).also {
            println("loadDocumentsForWorkspaceFromDb")
        }

    suspend fun loadDocumentsForWorkspaceAfterTimeFromDb(
        workspaceId: String,
        userId: String,
        time: Instant
    ): List<Document> =
        notesConfig.getOrderPreference(userId)
            .let { orderBy ->
                documentRepository.loadDocumentsForWorkspace(
                    orderBy,
                    workspaceId,
                    time
                )
            }

    suspend fun loadFolderForUserAfterTime(workspace: String, time: Instant): List<Folder> =
        folderRepository.getFoldersForWorkspaceAfterTime(workspace, time)

    suspend fun loadFoldersForWorkspace(workspaceId: String): List<Folder> =
        folderRepository.getFoldersForWorkspace(workspaceId).also {
            println("loadFoldersForWorkspace")
        }

    private suspend fun loadDocumentsByIds(
        ids: Iterable<String>,
        workspaceId: String,
    ): List<MenuItem> {
        val folders = ids.mapNotNull { id -> folderRepository.getFolderById(id) }
        val documents =
            ids.mapNotNull { id -> documentRepository.loadDocumentById(id, workspaceId) }

        println("loadDocumentsByIds")

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
        workspaceId: String
    ): Flow<Map<String, List<MenuItem>>> =
        combine(
            listenForFoldersByParentId(parentId, workspaceId),
            listenForDocumentsByParentId(parentId, workspaceId),
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
        workspaceId: String
    ): Flow<Map<String, List<MenuItem>>> =
        when (notesNavigation) {
            NotesNavigation.Favorites -> listenForMenuItemsByParentId(
                Folder.ROOT_PATH,
                userId,
                workspaceId
            )

            is NotesNavigation.Folder -> listenForMenuItemsByParentId(
                notesNavigation.id,
                userId,
                workspaceId
            )

            NotesNavigation.Root -> listenForMenuItemsByParentId(
                Folder.ROOT_PATH,
                userId,
                workspaceId
            )
        }

    suspend fun stopListeningForMenuItemsByParentId(id: String, workspaceId: String) {
        folderRepository.stopListeningForFoldersByParentId(id, workspaceId)
        documentRepository.stopListeningForFoldersByParentId(id, workspaceId)
    }

    suspend fun duplicateDocuments(ids: List<String>, userId: String, workspaceId: String) {
        duplicateNotes(ids, userId, workspaceId)
        duplicateFolders(ids, workspaceId)
    }

    suspend fun saveDocumentDb(document: Document) {
        documentRepository.saveDocument(document)
        documentRepository.refreshDocuments()
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
        parentId: String,
        workspaceId: String
    ): Flow<Map<String, List<Document>>> =
        documentRepository.listenForDocumentsByParentId(parentId, workspaceId)

    private suspend fun duplicateNotes(ids: List<String>, userId: String, workspaceId: String) {
        notesConfig.getOrderPreference(userId).let { orderBy ->
            documentRepository.loadDocumentsWithContentByIds(ids, orderBy, workspaceId)
        }.map { document ->
            document.duplicateWithNewIds()
        }.forEach { document ->
            documentRepository.saveDocument(document)
        }

        documentRepository.refreshDocuments()
    }

    private suspend fun duplicateFolders(ids: List<String>, workspaceId: String) {
        ids.forEach { id -> duplicateFolderRecursively(id, workspaceId) }

        folderRepository.refreshFolders()
    }

    private suspend fun duplicateFolderRecursively(id: String, workspaceId: String) {
        val folder = folderRepository.getFolderById(id)

        if (folder != null) {
            duplicateAllFoldersInside(folder, workspaceId)
        }
    }

    private suspend fun duplicateAllFoldersInside(folder: Folder, workspaceId: String) {
        val newFolder = duplicateFolder(folder, workspaceId)
        val folderList = getFolderIdByParentId(folder.id, workspaceId)
            .map { insideFolder ->
                insideFolder.copy(parentId = newFolder.id)
            }

        if (folderList.isNotEmpty()) {
            folderList.forEach { insideFolder ->
                duplicateAllFoldersInside(
                    insideFolder,
                    workspaceId
                )
            }
        }
    }

    private suspend fun getFolderIdByParentId(parentId: String, workspaceId: String): List<Folder> =
        folderRepository.getFolderByParentId(parentId, workspaceId)

    private suspend fun duplicateFolder(folder: Folder, workspaceId: String): Folder {
        // Todo: É necessário mudar o parent id da pasta também
        val newFolder = folder.copy(id = GenerateId.generate())

        return run {
            folderRepository.createFolder(newFolder)

            documentRepository.loadDocumentsByParentId(folder.id, workspaceId)
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
        parentId: String,
        workspaceId: String
    ): Flow<Map<String, List<Folder>>> =
        folderRepository.listenForFoldersByParentId(parentId, workspaceId)

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
            authRepository: AuthRepository
        ): NotesUseCase =
            instance ?: NotesUseCase(
                documentRepository,
                notesConfig,
                folderRepository,
                authRepository
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

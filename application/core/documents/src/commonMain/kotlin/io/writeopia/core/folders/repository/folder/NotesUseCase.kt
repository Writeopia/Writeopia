@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.repository.folder

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
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.ExperimentalTime

/**
 * UseCase responsible to perform CRUD operations in the Notes (Documents) of the app taking in to
 * consideration the configuration desired in the app.
 */
class NotesUseCase internal constructor(
    private val documentRepository: DocumentRepository,
    private val notesConfig: ConfigurationRepository,
    private val folderRepository: FolderRepository,
) {

    suspend fun createFolder(
        name: String,
        workspaceId: String,
        parentId: String,
        icon: MenuItem.Icon? = null
    ): Folder {
        val folder = Folder.fromName(name, workspaceId).copy(parentId = parentId, icon = icon)
        folderRepository.createFolder(folder)
        return folder
    }

    suspend fun updateFolder(folder: Folder) {
        folderRepository.updateFolder(folder)
    }

    suspend fun getFolderById(id: String): Folder? = folderRepository.getFolderById(id)

    suspend fun updateFolderById(id: String, folderChange: (Folder) -> Folder): Folder? {
        val newFolder = folderRepository.getFolderById(id)?.let(folderChange)

        newFolder?.let { folder ->
            folderRepository.updateFolder(folder)
            folderRepository.refreshFolders()
        }

        return newFolder
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

    /** Moves the items into [parentId] and returns them, so the caller can send the move. */
    suspend fun moveItemsById(ids: Iterable<String>, workspaceId: String, parentId: String): List<MenuItem> {
        val items = loadMenuItemsByIds(ids, workspaceId).filter { it.id != parentId }

        items.forEach { moveItem(it, parentId) }
        folderRepository.refreshFolders()
        return items
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

    suspend fun loadDocumentsByIds(
        ids: Iterable<String>,
        workspaceId: String,
    ): List<Document> =
        ids.mapNotNull { id -> documentRepository.loadDocumentById(id, workspaceId) }

    private suspend fun loadFoldersByIds(
        ids: Iterable<String>,
    ): List<Folder> =
        ids.mapNotNull { id -> folderRepository.getFolderById(id) }

    private suspend fun loadMenuItemsByIds(
        ids: Iterable<String>,
        workspaceId: String,
    ): List<MenuItem> {
        val folders = loadFoldersByIds(ids)
        val documents = loadDocumentsByIds(ids, workspaceId)

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
            NotesNavigation.Favorites -> listenForFavorites(userId, workspaceId)

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

            is NotesNavigation.Tag -> listenForDocumentsWithTag(notesNavigation.id, userId, workspaceId)
        }.map { menuMap ->
            menuMap.mapKeys { (key, value) ->
                key.split(":", limit = 2)[0]
            }
        }

    /**
     * The favorite folders and documents of the whole workspace, wherever they are, keyed by
     * [NotesNavigation.Favorites.id]. The root listing comes along, since the side menu is built
     * from it while the favorites are shown.
     */
    private suspend fun listenForFavorites(
        userId: String,
        workspaceId: String
    ): Flow<Map<String, List<MenuItem>>> =
        combine(
            listenForMenuItemsByParentId(Folder.ROOT_PATH, userId, workspaceId),
            folderRepository.listenForFavoriteFolders(workspaceId),
            documentRepository.listenForFavoriteDocuments(workspaceId),
            notesConfig.listenOrderPreference(userId)
        ) { rootItems, folders, documents, orderPreference ->
            val order =
                orderPreference.takeIf { it.isNotEmpty() }?.let(OrderBy.Companion::fromString)
                    ?: OrderBy.UPDATE
            val favorites: List<MenuItem> = folders + documents

            rootItems + (NotesNavigation.Favorites.id to favorites.sortedWithOrderBy(order))
        }

    /**
     * The documents of the workspace carrying [tagId], wherever they are, keyed by the tag id
     * like a folder's contents are keyed by the folder id. Read once per subscription.
     */
    private suspend fun listenForDocumentsWithTag(
        tagId: String,
        userId: String,
        workspaceId: String
    ): Flow<Map<String, List<MenuItem>>> =
        notesConfig.listenOrderPreference(userId).map { orderPreference ->
            val order =
                orderPreference.takeIf { it.isNotEmpty() }?.let(OrderBy.Companion::fromString)
                    ?: OrderBy.UPDATE
            val documents = documentRepository.loadDocumentsWorkspace(workspaceId)
                .filter { document -> tagId in document.tagIds }

            mapOf(tagId to documents.sortedWithOrderBy(order))
        }

    suspend fun stopListeningForMenuItemsByParentId(id: String, workspaceId: String) {
        folderRepository.stopListeningForFoldersByParentId(id, workspaceId)
        documentRepository.stopListeningForFoldersByParentId(id, workspaceId)
    }

    suspend fun duplicateDocuments(ids: List<String>, userId: String, workspaceId: String): List<Document> {
        val duplicatedDocuments = duplicateNotes(ids, userId, workspaceId)
        duplicateFolders(ids, workspaceId)
        return duplicatedDocuments
    }

    suspend fun saveDocumentDb(document: Document) {
        documentRepository.saveDocument(document)
        documentRepository.refreshDocuments()
    }

    /**
     * Soft delete notes: marks as deleted but keeps in database.
     * The notes will be synced to backend and then hard deleted once confirmed.
     */
    suspend fun deleteNotes(ids: Set<String>, workspaceId: String) {
        documentRepository.deleteDocumentByIds(ids, workspaceId)
        ids.forEach { id ->
            deleteFolderById(id, workspaceId)
        }
    }

    /**
     * Soft delete folder: marks folder and contents as deleted but keeps in database.
     */
    suspend fun deleteFolderById(folderId: String, workspaceId: String) {
        val childFolders = folderRepository.getFolderByParentId(folderId, workspaceId)

        // Recursively delete all child folders
        childFolders.forEach { childFolder ->
            deleteFolderById(childFolder.id, workspaceId)
        }

        documentRepository.deleteDocumentByFolder(folderId, workspaceId)
        folderRepository.deleteFolderById(folderId, workspaceId)
    }

    /**
     * Hard delete notes: permanently removes from database.
     * Use this after backend has confirmed the deletion.
     */
    suspend fun hardDeleteNotes(ids: Set<String>, workspaceId: String) {
        documentRepository.hardDeleteDocumentByIds(ids, workspaceId)
        ids.forEach { id ->
            hardDeleteFolderById(id, workspaceId)
        }
    }

    /**
     * Hard delete folder: permanently removes folder and contents from database.
     * Use this after backend has confirmed the deletion.
     */
    suspend fun hardDeleteFolderById(folderId: String, workspaceId: String) {
        val childFolders = folderRepository.getFolderByParentId(folderId, workspaceId)

        // Recursively hard delete all child folders
        childFolders.forEach { childFolder ->
            hardDeleteFolderById(childFolder.id, workspaceId)
        }

        // Hard delete documents in this folder
        val documentIds = documentRepository.loadDocumentsForFolder(folderId, workspaceId)
            .map { it.id }
            .toSet()
        if (documentIds.isNotEmpty()) {
            documentRepository.hardDeleteDocumentByIds(documentIds, workspaceId)
        }

        folderRepository.hardDeleteFolderById(folderId, workspaceId)
    }

    /**
     * Get all soft-deleted documents for a workspace.
     * Use this to find documents that need to be synced to backend for deletion.
     */
    suspend fun getSoftDeletedDocuments(workspaceId: String): List<Document> =
        documentRepository.getSoftDeletedDocuments(workspaceId)

    /**
     * Get all soft-deleted folders for a workspace.
     * Use this to find folders that need to be synced to backend for deletion.
     */
    suspend fun getSoftDeletedFolders(workspaceId: String): List<Folder> =
        folderRepository.getSoftDeletedFolders(workspaceId)

    /**
     * Marks the documents and folders among [ids] as favorites. A folder's favorite is part of
     * the folder; a document's is per user on the backend and travels apart, but the change is
     * this device's all the same: it stamps the document, so the document is outdated until
     * the next sync, which sends the favorite instead of undoing it with the backend's list.
     */
    suspend fun favoriteDocuments(ids: Set<String>, workspaceId: String) {
        setDocumentsFavorite(ids, workspaceId, favorite = true)
        folderRepository.favoriteDocumentByIds(ids)
    }

    /** Unmarks the documents and folders among [ids]. See [favoriteDocuments]. */
    suspend fun unFavoriteDocuments(ids: Set<String>, workspaceId: String) {
        setDocumentsFavorite(ids, workspaceId, favorite = false)
        folderRepository.unFavoriteDocumentByIds(ids)
    }

    private suspend fun setDocumentsFavorite(ids: Set<String>, workspaceId: String, favorite: Boolean) {
        val now = Clock.System.now()
        ids.forEach { id ->
            documentRepository.loadDocumentById(id, workspaceId)?.let { document ->
                documentRepository.saveDocumentMetadata(document.copy(favorite = favorite, lastUpdatedAt = now))
            }
        }
    }

    private suspend fun listenForDocumentsByParentId(
        parentId: String,
        workspaceId: String
    ): Flow<Map<String, List<Document>>> =
        documentRepository.listenForDocumentsByParentId(parentId, workspaceId)

    private suspend fun duplicateNotes(ids: List<String>, userId: String, workspaceId: String): List<Document> {
        val duplicatedDocuments = notesConfig.getOrderPreference(userId).let { orderBy ->
            documentRepository.loadDocumentsWithContentByIds(ids, orderBy, workspaceId)
        }.map { document ->
            document.duplicateWithNewIds()
        }

        duplicatedDocuments.forEach { document ->
            documentRepository.saveDocument(document)
        }

        documentRepository.refreshDocuments()
        return duplicatedDocuments
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
        ): NotesUseCase =
            instance ?: NotesUseCase(
                documentRepository,
                notesConfig,
                folderRepository,
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

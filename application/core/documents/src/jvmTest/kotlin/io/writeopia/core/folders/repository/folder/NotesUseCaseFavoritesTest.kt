@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.repository.folder

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.persistence.core.repository.InMemoryDocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class NotesUseCaseFavoritesTest {

    private val workspaceId = "workspace"
    private val userId = "user"

    @Test
    fun `favorites come from every folder of the workspace, not only the root`() = runBlocking {
        val documentRepository = InMemoryDocumentRepository()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val notesConfig = mockk<ConfigurationRepository>(relaxed = true)

        val nestedFavoriteFolder = Folder.fromName("Nested favorite", workspaceId)
            .copy(id = "nestedFolder", parentId = "parent", favorite = true)
        coEvery { folderRepository.listenForFoldersByParentId(any(), any()) } returns
            MutableStateFlow(emptyMap())
        coEvery { folderRepository.listenForFavoriteFolders(workspaceId) } returns
            flowOf(listOf(nestedFavoriteFolder))
        coEvery { notesConfig.listenOrderPreference(userId) } returns flowOf("")

        documentRepository.saveDocument(document("rootFavorite", Folder.ROOT_PATH, favorite = true))
        documentRepository.saveDocument(document("rootPlain", Folder.ROOT_PATH, favorite = false))
        documentRepository.saveDocument(document("nestedFavorite", "parent", favorite = true))
        documentRepository.saveDocument(document("nestedPlain", "parent", favorite = false))

        val notesUseCase = NotesUseCase.singleton(documentRepository, notesConfig, folderRepository)

        val menuItems = notesUseCase
            .listenForMenuItemsPerFolderId(NotesNavigation.Favorites, userId, workspaceId)
            .first()

        val favoriteIds = menuItems.getValue(NotesNavigation.Favorites.id).map { it.id }.toSet()
        assertEquals(setOf("rootFavorite", "nestedFavorite", "nestedFolder"), favoriteIds)

        // The root listing is still there for the side menu.
        assertEquals(
            setOf("rootFavorite", "rootPlain"),
            menuItems.getValue(Folder.ROOT_PATH).map { it.id }.toSet()
        )
    }

    @Test
    fun `a favorite changed here stamps the document, so the next sync sends it`() = runBlocking {
        val documentRepository = InMemoryDocumentRepository()
        val folderRepository = mockk<FolderRepository>(relaxed = true)
        val synced = Instant.fromEpochMilliseconds(1_000)
        documentRepository.saveDocument(
            document("doc", Folder.ROOT_PATH, favorite = false).copy(lastUpdatedAt = synced, lastSyncedAt = synced)
        )
        val notesUseCase = NotesUseCase(documentRepository, mockk(relaxed = true), folderRepository)

        notesUseCase.favoriteDocuments(setOf("doc"), workspaceId)

        assertTrue(documentRepository.loadDocumentById("doc", workspaceId)!!.favorite)
        assertEquals(listOf("doc"), documentRepository.loadOutdatedDocumentsForWorkspace(workspaceId).map { it.id })
        coVerify { folderRepository.favoriteDocumentByIds(setOf("doc")) }

        notesUseCase.unFavoriteDocuments(setOf("doc"), workspaceId)

        assertFalse(documentRepository.loadDocumentById("doc", workspaceId)!!.favorite)
        coVerify { folderRepository.unFavoriteDocumentByIds(setOf("doc")) }
    }

    private fun document(id: String, parentId: String, favorite: Boolean) = Document(
        id = id,
        title = id,
        createdAt = Instant.DISTANT_PAST,
        lastUpdatedAt = Instant.DISTANT_PAST,
        lastSyncedAt = null,
        workspaceId = workspaceId,
        parentId = parentId,
        favorite = favorite
    )
}

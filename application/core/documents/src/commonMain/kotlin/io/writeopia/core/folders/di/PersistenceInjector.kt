package io.writeopia.core.folders.di

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.repository.folder.BackendSyncFolderRepository
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.core.folders.repository.folder.InMemoryFolderRepository
import io.writeopia.model.PersistenceMode
import io.writeopia.sdk.persistence.core.repository.BackendSyncDocumentRepository
import io.writeopia.sdk.persistence.core.repository.InMemoryDocumentRepository
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.coroutines.CoroutineScope

/**
 * Factory that provides the correct repository implementations based on [PersistenceMode].
 *
 * @param localDocumentRepository The local database document repository (SQLite/Room)
 * @param localFolderRepository The local database folder repository
 * @param documentsApi The API client for backend sync
 * @param authRepository Repository for authentication
 * @param scope CoroutineScope for async operations
 */
class PersistenceInjector(
    private val localDocumentRepository: DocumentRepository,
    private val localFolderRepository: FolderRepository,
    private val documentsApi: DocumentsApi,
    private val authRepository: AuthRepository,
    private val scope: CoroutineScope
) {
    private var backendSyncDocumentRepository: BackendSyncDocumentRepository? = null
    private var backendSyncFolderRepository: BackendSyncFolderRepository? = null

    /**
     * Provides the appropriate document repository based on persistence mode.
     */
    fun provideDocumentRepository(mode: PersistenceMode): DocumentRepository {
        return when (mode) {
            PersistenceMode.LOCAL_DATABASE -> localDocumentRepository
            PersistenceMode.MEMORY_WITH_SYNC -> getOrCreateBackendSyncDocumentRepository()
        }
    }

    /**
     * Provides the appropriate folder repository based on persistence mode.
     */
    fun provideFolderRepository(mode: PersistenceMode): FolderRepository {
        return when (mode) {
            PersistenceMode.LOCAL_DATABASE -> localFolderRepository
            PersistenceMode.MEMORY_WITH_SYNC -> getOrCreateBackendSyncFolderRepository()
        }
    }

    /**
     * Returns the BackendSyncDocumentRepository if in MEMORY_WITH_SYNC mode.
     * Returns null if in LOCAL_DATABASE mode.
     */
    fun getBackendSyncDocumentRepository(mode: PersistenceMode): BackendSyncDocumentRepository? {
        return when (mode) {
            PersistenceMode.LOCAL_DATABASE -> null
            PersistenceMode.MEMORY_WITH_SYNC -> getOrCreateBackendSyncDocumentRepository()
        }
    }

    /**
     * Returns the BackendSyncFolderRepository if in MEMORY_WITH_SYNC mode.
     * Returns null if in LOCAL_DATABASE mode.
     */
    fun getBackendSyncFolderRepository(mode: PersistenceMode): BackendSyncFolderRepository? {
        return when (mode) {
            PersistenceMode.LOCAL_DATABASE -> null
            PersistenceMode.MEMORY_WITH_SYNC -> getOrCreateBackendSyncFolderRepository()
        }
    }

    private fun getOrCreateBackendSyncDocumentRepository(): BackendSyncDocumentRepository {
        return backendSyncDocumentRepository ?: run {
            val inMemoryRepo = InMemoryDocumentRepository()
            val repo = BackendSyncDocumentRepository(
                inMemoryRepository = inMemoryRepo,
                sendDocuments = { documents, workspaceId, token ->
                    documentsApi.sendDocuments(documents, workspaceId, token)
                },
                deleteDocuments = { ids, workspaceId, token ->
                    documentsApi.deleteDocuments(ids, workspaceId, token)
                },
                getAuthToken = { authRepository.getAuthToken() },
                getWorkspaceId = { authRepository.getWorkspace()?.id ?: "" },
                scope = scope
            )
            backendSyncDocumentRepository = repo
            repo
        }
    }

    private fun getOrCreateBackendSyncFolderRepository(): BackendSyncFolderRepository {
        return backendSyncFolderRepository ?: run {
            val inMemoryRepo = InMemoryFolderRepository.singleton()
            val repo = BackendSyncFolderRepository(
                inMemoryRepository = inMemoryRepo,
                sendFolders = { folders, workspaceId, token ->
                    documentsApi.sendFolders(folders, workspaceId, token)
                },
                deleteFolder = { folderId, workspaceId, token ->
                    documentsApi.deleteFolder(folderId, workspaceId, token)
                },
                getAuthToken = { authRepository.getAuthToken() },
                getWorkspaceId = { authRepository.getWorkspace()?.id ?: "" },
                scope = scope
            )
            backendSyncFolderRepository = repo
            repo
        }
    }

    companion object {
        private var instance: PersistenceInjector? = null

        fun initialize(
            localDocumentRepository: DocumentRepository,
            localFolderRepository: FolderRepository,
            documentsApi: DocumentsApi,
            authRepository: AuthRepository,
            scope: CoroutineScope
        ): PersistenceInjector {
            return PersistenceInjector(
                localDocumentRepository,
                localFolderRepository,
                documentsApi,
                authRepository,
                scope
            ).also { instance = it }
        }

        fun singleton(): PersistenceInjector =
            instance ?: throw IllegalStateException("PersistenceInjector not initialized")
    }
}

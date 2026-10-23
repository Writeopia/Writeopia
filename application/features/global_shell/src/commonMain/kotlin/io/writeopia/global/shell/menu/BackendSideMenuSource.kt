package io.writeopia.global.shell.menu

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.repository.MenuItemsRepository
import io.writeopia.core.folders.sync.ContentLoadActivity
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.flow.Flow

/**
 * The side menu of the webapp, which has no database: every folder is fetched from the
 * backend and kept in [MenuItemsRepository], shared with the center menu.
 */
class BackendSideMenuSource(
    private val menuItemsRepository: MenuItemsRepository,
    private val authRepository: AuthRepository,
    private val notesNavigationUseCase: NotesNavigationUseCase,
) : SideMenuSource {

    override fun menuItemsPerFolderId(): Flow<Map<String, List<MenuItem>>> =
        menuItemsRepository.menuItemsPerFolderId

    override suspend fun followNavigation() {
        notesNavigationUseCase.navigationState.collect { navigation ->
            loadFolder(navigation.id)
        }
    }

    override suspend fun expandFolder(folderId: String) {
        ContentLoadActivity.track { loadFolder(folderId) }
    }

    /** Nothing is listened to: the children fetched stay in memory, and expanding fetches again. */
    override fun collapseFolder(folderId: String) {}

    private suspend fun loadFolder(folderId: String) {
        val workspace = authRepository.getWorkspace() ?: return
        // The offline workspace has nothing on the backend.
        if (workspace.id == Workspace.disconnectedWorkspace().id) return

        menuItemsRepository.loadFolderContents(folderId, workspace.id)
    }
}

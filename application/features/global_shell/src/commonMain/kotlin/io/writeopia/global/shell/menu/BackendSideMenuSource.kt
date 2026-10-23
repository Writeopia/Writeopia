package io.writeopia.global.shell.menu

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.repository.MenuItemsRepository
import io.writeopia.core.folders.sync.ContentLoadActivity
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.sdk.models.document.MenuItem
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

    private suspend fun loadFolder(folderId: String) {
        val workspace = authRepository.getWorkspace() ?: return

        menuItemsRepository.loadFolderContents(folderId, workspace.id)
    }
}

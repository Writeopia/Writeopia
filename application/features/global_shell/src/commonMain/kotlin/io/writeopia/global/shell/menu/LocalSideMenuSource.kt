package io.writeopia.global.shell.menu

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.common.utils.NotesNavigation
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.core.folders.sync.ContentLoadActivity
import io.writeopia.core.folders.sync.FolderSync
import io.writeopia.notemenu.data.usecase.NotesNavigationUseCase
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

/**
 * The side menu of the apps with a database: it shows what is on the device and syncs the
 * expanded folders with the backend, which writes their children to the database.
 */
class LocalSideMenuSource(
    private val notesUseCase: NotesUseCase,
    private val authRepository: AuthRepository,
    private val notesNavigationUseCase: NotesNavigationUseCase,
    /** Brings an expanded folder in sync with the backend; null when there is no backend. */
    private val folderSync: FolderSync?,
) : SideMenuSource {

    private val expandedFolderIds = MutableStateFlow(emptySet<String>())

    /** The folder navigated to, plus the children of every folder expanded in the side menu. */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun menuItemsPerFolderId(): Flow<Map<String, List<MenuItem>>> =
        combine(
            authRepository.listenForUser(),
            authRepository.listenForWorkspace(),
            notesNavigationUseCase.navigationState
        ) { user, workspace, notesNavigation ->
            Triple(user, notesNavigation, workspace)
        }.flatMapLatest { (user, notesNavigation, workspace) ->
            combine(
                notesUseCase.listenForMenuItemsPerFolderId(notesNavigation, user.id, workspace.id),
                expandedFolderIds.flatMapLatest { ids ->
                    listenForExpandedFolders(ids, user.id, workspace.id)
                }
            ) { navigated, expanded -> expanded + navigated }
        }

    private suspend fun listenForExpandedFolders(
        folderIds: Set<String>,
        userId: String,
        workspaceId: String
    ): Flow<Map<String, List<MenuItem>>> {
        if (folderIds.isEmpty()) return flowOf(emptyMap())

        val flows = folderIds.map { folderId ->
            notesUseCase.listenForMenuItemsPerFolderId(
                NotesNavigation.Folder(folderId),
                userId,
                workspaceId
            )
        }

        return combine(flows) { maps -> maps.fold(emptyMap()) { acc, map -> acc + map } }
    }

    /** The listing of the database follows the navigation by itself. */
    override suspend fun followNavigation() {}

    /**
     * Shows what is on the device right away, then fetches the children from the backend: a
     * folder received inside its parent has its contents on the backend only until it is
     * opened somewhere.
     */
    override suspend fun expandFolder(folderId: String) {
        expandedFolderIds.value += folderId

        val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()
        syncExpandedFolder(folderId, workspace)
    }

    /** Fetches the children of an expanded folder, like opening the folder does. */
    private suspend fun syncExpandedFolder(folderId: String, workspace: Workspace) {
        val sync = folderSync ?: return
        if (workspace.id == Workspace.disconnectedWorkspace().id) return
        if (!authRepository.isLoggedIn() || authRepository.getUser().tier != Tier.PREMIUM) return

        withContext(Dispatchers.Default) {
            ContentLoadActivity.track {
                // The side menu may expand several folders in a row: no sync is skipped for
                // coming right after another.
                sync.syncFolder(folderId = folderId, workspaceId = workspace.id, force = true)
            }
        }
    }
}

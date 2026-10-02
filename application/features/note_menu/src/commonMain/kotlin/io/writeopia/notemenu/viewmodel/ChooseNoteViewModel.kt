package io.writeopia.notemenu.viewmodel

import io.writeopia.core.configuration.models.NotesArrangement
import io.writeopia.notemenu.ui.dto.NotesUi
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.sdk.models.sorting.OrderBy
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

interface ChooseNoteViewModel : FolderController {
    val hasSelectedNotes: StateFlow<Boolean>

    /** The folder shown (the root, a folder or the favorites), where the items can't be moved to. */
    val currentFolderId: String

    /** True while a folder is part of the selection: folders can't be duplicated nor summarized. */
    val selectionHasFolders: StateFlow<Boolean>

    val userName: StateFlow<UserState<String>>

    /**
     * Title of the folder currently being displayed. Null when in the root of the workspace.
     */
    val currentFolderTitle: StateFlow<String?>

    /**
     * The folder currently being displayed. Null when not inside a folder (root or favorites).
     */
    val currentFolder: StateFlow<Folder?>

    /**
     * The folders from the root down to the one displayed, for the breadcrumb. Empty when not
     * inside a folder (root or favorites).
     */
    val folderPath: StateFlow<List<Folder>>

    val documentsState: StateFlow<ResultData<NotesUi>>

    val menuItemsPerFolderId: StateFlow<Map<String, List<MenuItem>>>

    val menuItemsState: StateFlow<ResultData<List<MenuItem>>>

    val notesArrangement: StateFlow<NotesArrangement>

    val orderByState: StateFlow<OrderBy>

    val editState: StateFlow<Boolean>

    val showSortMenuState: StateFlow<Boolean>

    val showLocalSyncConfigState: StateFlow<ConfigState>

    val syncInProgress: StateFlow<SyncState>

    val titlesToDelete: StateFlow<List<String>>

    val showAddMenuState: StateFlow<Boolean>

    val editFolderState: StateFlow<Folder?>

    val showAiOptionsState: StateFlow<Boolean>

    val showCreateFolderDialogState: StateFlow<Boolean>

    //    fun requestDocuments(force: Boolean)
    fun handleMenuItemTap(id: String): Boolean

    suspend fun requestUser()

    fun showEditMenu()

    fun showSortMenu()

    fun showAddMenu()

    fun hideAddMenu()

    fun cancelEditMenu()

    fun cancelSortMenu()

    fun directoryFilesAsMarkdown(path: String)

    fun directoryFilesAsTxt(path: String)

    fun loadFiles(filePaths: List<ExternalFile>)

    fun onSyncLocallySelected()

    fun configureDirectory()

    fun onWriteLocallySelected()

    fun listArrangementSelected()

    fun gridArrangementSelected()

    fun staggeredGridArrangementSelected()

    fun sortingSelected(orderBy: OrderBy)

    fun copySelectedNotes()

    fun deleteSelectedNotes()

    fun favoriteSelectedNotes()

    fun summarizeDocuments()

    fun showAiOptions()

    fun hideAiOptions()

    fun hideConfigSyncMenu()

    fun pathSelected(path: String)

    fun confirmWorkplacePath()

    fun requestPermissionToDeleteSelection()

    fun cancelDeletion()

    fun syncFolderWithCloud()

    fun newFolder()

    fun showCreateFolderDialog()

    fun hideCreateFolderDialog()

    fun createFolderWithDetails(name: String, icon: MenuItem.Icon?)

    /**
     * Folders the current folder can be moved into: the whole tree of the workspace, except the
     * current folder itself, what is inside it and where it already is.
     */
    suspend fun currentFolderMoveDestinations(): List<FolderDestination>

    fun moveCurrentFolder(parentId: String)
}

sealed interface UserState<T> {
    class Idle<T> : UserState<T>

    class Loading<T> : UserState<T>

    class ConnectedUser<T>(val data: T) : UserState<T>

    class UserNotReturned<T> : UserState<T>

    class DisconnectedUser<T>(val data: T) : UserState<T>
}

fun <T, R> UserState<T>.map(fn: (T) -> R): UserState<R> =
    when (this) {
        is UserState.ConnectedUser -> UserState.ConnectedUser(fn(this.data))
        is UserState.Idle -> UserState.Idle()
        is UserState.Loading -> UserState.Loading()
        is UserState.DisconnectedUser -> UserState.DisconnectedUser(fn(this.data))
        is UserState.UserNotReturned -> UserState.UserNotReturned()
    }

sealed interface SyncState {
    data object LoadingSync : SyncState

    data object LoadingWrite : SyncState

    data object Idle : SyncState
}

sealed interface ConfigState {

    data class Configure(val path: String, val syncRequest: SyncRequest) : ConfigState

    data object Idle : ConfigState
}

enum class SyncRequest {
    WRITE,
    READ_WRITE,
    CONFIGURE
}

fun ConfigState.setPath(func: () -> String): ConfigState =
    when (this) {
        is ConfigState.Configure -> ConfigState.Configure(func(), this.syncRequest)
        ConfigState.Idle -> ConfigState.Idle
    }

fun ConfigState.getPath(): String? =
    when (this) {
        is ConfigState.Configure -> this.path
        ConfigState.Idle -> null
    }

fun ConfigState.getSyncRequest(): SyncRequest? =
    when (this) {
        is ConfigState.Configure -> syncRequest
        ConfigState.Idle -> null
    }

fun StateFlow<NotesArrangement>.toNumberDesktop() =
    map { notesArrangement ->
        when (notesArrangement) {
            NotesArrangement.STAGGERED_GRID -> 0
            NotesArrangement.GRID -> 1
            NotesArrangement.LIST -> 2
        }
    }

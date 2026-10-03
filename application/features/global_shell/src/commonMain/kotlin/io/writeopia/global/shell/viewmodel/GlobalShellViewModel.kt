package io.writeopia.global.shell.viewmodel

import io.writeopia.account.ui.CloudAiUsageState
import io.writeopia.common.utils.download.DownloadState
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.controller.LocalAiConfigController
import io.writeopia.model.AiProvider
import io.writeopia.sdk.models.document.Folder
import io.writeopia.notemenu.viewmodel.FolderController
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface GlobalShellViewModel : FolderController, LocalAiConfigController {
    val sideMenuItems: StateFlow<List<MenuItemUi>>

    val showSideMenuState: StateFlow<Float>

    val highlightItem: StateFlow<String?>

    val menuItemsPerFolderId: StateFlow<Map<String, List<MenuItem>>>

    val editFolderState: StateFlow<Folder?>

    val showSettingsState: StateFlow<Boolean>

    /** The folders from the root down to the one shown, for the breadcrumb of the header. */
    val folderPath: StateFlow<List<Folder>>

    val showSearchDialog: StateFlow<Boolean>

    val workspaceLocalPath: StateFlow<String>

    val userState: StateFlow<WriteopiaUser>

    val showDeleteConfirmation: StateFlow<Boolean>

    val lastWorkspaceSync: StateFlow<ResultData<String>>

    val isAutoSyncEnabled: StateFlow<Boolean>

    val availableWorkspaces: StateFlow<ResultData<List<Workspace>>>

    val workspaceToEdit: Flow<Workspace?>

    val usersOfWorkspaceToEdit: Flow<ResultData<List<String>>>

    val exportWorkspaceState: StateFlow<ResultData<Unit>>

    val logoutInProgress: StateFlow<Boolean>

    val deleteAccountInProgress: StateFlow<Boolean>

    override val localAiSelectedModelState: StateFlow<String>

    override val localAiUrl: StateFlow<String>

    override val modelsForUrl: StateFlow<ResultData<List<String>>>

    override val downloadModelState: StateFlow<ResultData<DownloadState>>

    val cloudAiUsageState: StateFlow<CloudAiUsageState>

    /** The AI the user picked to answer the AI commands, local or cloud. */
    val aiProviderState: StateFlow<AiProvider>

    /** The providers the user can pick right now, in the order shown. No cloud while offline. */
    val aiProviderChoices: StateFlow<List<AiProvider>>

    /** True with a session in an online workspace: the cloud AI and its usage can be reached. */
    val isCloudAiAvailable: StateFlow<Boolean>

    override val autoConfigureState: StateFlow<ResultData<Unit>>

    fun init()

    fun loadCloudAiUsage()

    fun selectAiProvider(provider: AiProvider)

    fun expandFolder(id: String)

    fun toggleSideMenu()

    fun showSettings()

    fun hideSettings()

    fun saveMenuWidth()

    fun moveSideMenu(width: Float)

    fun showSearch()

    fun hideSearch()

    fun changeWorkspaceLocalPath(path: String)

    fun logout(onSuccessSideEffect: () -> Unit)

    fun changeWorkspace(sideEffect: () -> Unit)

    fun dismissDeleteConfirm()

    fun showDeleteConfirm()

    fun syncWorkspace()

    fun toggleAutoSync(enabled: Boolean)

    fun deleteAccount(sideEffect: () -> Unit)

    fun addUserToTeam(userEmail: String)

    fun selectWorkspaceToManage(workspaceId: String)

    fun exportWorkspace(workspaceId: String)

    fun resetExportState()

    override fun changeLocalAiUrl(url: String)

    override fun selectLocalAiModel(model: String)

    override fun retryModels()

    override fun modelToDownload(model: String, onComplete: () -> Unit)

    override fun deleteModel(model: String)

    override fun autoConfigure()
}

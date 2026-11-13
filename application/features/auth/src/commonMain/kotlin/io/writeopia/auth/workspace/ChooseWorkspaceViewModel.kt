@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.LocalAiRepository
import io.writeopia.api.LocalAiApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.auth.core.data.WorkspaceApi
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.tutorials.Tutorials
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlin.sequences.forEach
import kotlin.time.ExperimentalTime

class ChooseWorkspaceViewModel(
    private val authRepository: AuthRepository,
    private val workspaceApi: WorkspaceApi,
    private val configRepository: ConfigurationRepository,
    private val notesUseCase: NotesUseCase,
    private val localAiRepository: LocalAiRepository,
    private val json: Json = writeopiaJson,
) : ViewModel() {

    private val _workspacesState = MutableStateFlow<ResultData<List<Workspace>>>(ResultData.Idle())
    val workspacesState: StateFlow<ResultData<List<Workspace>>> = _workspacesState.asStateFlow()

    private val _createWorkspaceState = MutableStateFlow<ResultData<Unit>>(ResultData.Idle())
    val createWorkspaceState: StateFlow<ResultData<Unit>> = _createWorkspaceState.asStateFlow()

    /**
     * Shows the workspaces saved on the device right away ([ResultData.InProgress]: the list is
     * there while the backend is still being asked) and replaces them with the backend's answer,
     * which is saved for the next time. Without a connection the saved ones stay
     * ([ResultData.Complete]); the error is only for when there is nothing to show.
     */
    fun loadWorkspaces() {
        viewModelScope.launch {
            val userId = getUserId()

            val cached = if (userId != WriteopiaUser.DISCONNECTED) {
                runCatching { authRepository.getCachedWorkspaces(userId) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }

            _workspacesState.value = if (cached.isEmpty()) {
                ResultData.Loading()
            } else {
                ResultData.InProgress(cached)
            }

            _workspacesState.value = try {
                when (val result = workspaceApi.getAvailableWorkspaces()) {
                    is ResultData.Complete -> {
                        if (userId != WriteopiaUser.DISCONNECTED) {
                            runCatching { authRepository.cacheWorkspaces(userId, result.data) }
                        }

                        ResultData.Complete(result.data)
                    }
                    is ResultData.Error -> cachedOrError(cached, result.exception)
                    else -> cachedOrError(cached, null)
                }
            } catch (e: Exception) {
                cachedOrError(cached, e)
            }
        }
    }

    private fun cachedOrError(
        cached: List<Workspace>,
        exception: Exception?
    ): ResultData<List<Workspace>> =
        if (cached.isEmpty()) ResultData.Error(exception) else ResultData.Complete(cached)

    fun chooseWorkspace(workspace: Workspace, sideEffect: () -> Unit) {
        viewModelScope.launch {
            val space = workspace.copy(lastSync = Instant.DISTANT_PAST)

            authRepository.unselectAllWorkspaces()
            authRepository.saveWorkspace(space)

            val userId = getUserId()
            val currentWorkspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()
            val workspaceId = currentWorkspace.id

            if (!configRepository.hasFirstConfiguration(userId)) {
                val isOnlineWorkspace = workspaceId != "disconnected_user"

                val tutorialsInitialized = if (isOnlineWorkspace) {
                    // For online workspaces, tutorials are initialized on the backend
                    // when the workspace is created
                    true
                } else {
                    // For offline mode, create tutorials locally
                    val now = Clock.System.now()

                    Tutorials.allTutorialsDocuments()
                        .map { documentAsJson ->
                            json.decodeFromString<DocumentApi>(documentAsJson)
                                .toModel()
                        }
                        .forEach { document ->
                            notesUseCase.saveDocumentDb(
                                document.copy(
                                    parentId = document.parentId,
                                    workspaceId = workspaceId,
                                    createdAt = now,
                                    lastUpdatedAt = now
                                )
                            )
                        }
                    true
                }

                localAiRepository.saveLocalAiUrl(userId, LocalAiApi.defaultUrl())

                if (tutorialsInitialized) {
                    configRepository.setTutorialNotes(true, userId)
                }
            }

            localAiRepository.refreshConfiguration(userId)

            sideEffect()
        }
    }

    fun createWorkspace(workspaceName: String) {
        viewModelScope.launch {
            _createWorkspaceState.value = ResultData.Loading()
            val result = workspaceApi.createWorkspace(workspaceName)
            _createWorkspaceState.value = result

            if (result is ResultData.Complete) {
                loadWorkspaces()
            }
        }
    }

    fun resetCreateWorkspaceState() {
        _createWorkspaceState.value = ResultData.Idle()
    }

    private suspend fun getUserId(): String = authRepository.getUser().id
}

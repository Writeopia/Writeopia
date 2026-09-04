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

    fun loadWorkspaces() {
        viewModelScope.launch {
            _workspacesState.value = ResultData.Loading()

            try {
                val result = workspaceApi.getAvailableWorkspaces()

                _workspacesState.value = when (result) {
                    is ResultData.Complete -> {
                        ResultData.Complete(result.data)
                    }
                    is ResultData.Error -> {
                        ResultData.Error(result.exception)
                    }
                    else -> {
                        ResultData.Error()
                    }
                }
            } catch (e: Exception) {
                _workspacesState.value = ResultData.Error(e)
            }
        }
    }

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

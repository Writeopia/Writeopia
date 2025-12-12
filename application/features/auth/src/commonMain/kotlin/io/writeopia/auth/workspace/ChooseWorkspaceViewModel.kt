@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.OllamaRepository
import io.writeopia.api.OllamaApi
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
    private val ollamaRepository: OllamaRepository,
    private val json: Json = writeopiaJson,
) : ViewModel() {

    private val _workspacesState = MutableStateFlow<ResultData<List<Workspace>>>(ResultData.Idle())
    val workspacesState: StateFlow<ResultData<List<Workspace>>> = _workspacesState.asStateFlow()

    fun loadWorkspaces() {
        viewModelScope.launch {
            val token = authRepository.getAuthToken()

            if (token != null) {
                _workspacesState.value = ResultData.Loading()
                _workspacesState.value = workspaceApi.getAvailableWorkspaces(token)
            } else {
                _workspacesState.value = ResultData.Complete(
                    listOf(Workspace.disconnectedWorkspace())
                )
            }
        }
    }

    fun chooseWorkspace(workspace: Workspace, sideEffect: () -> Unit) {
        viewModelScope.launch {
            val space = workspace.copy(lastSync = Instant.DISTANT_PAST)

            authRepository.unselectAllWorkspaces()
            authRepository.saveWorkspace(space)

            val userId = getUserId()
            val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()
            val workspaceId = workspace.id

            if (!configRepository.hasFirstConfiguration(userId)) {
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

                ollamaRepository.saveOllamaUrl(userId, OllamaApi.defaultUrl())
                configRepository.setTutorialNotes(true, userId)
            }

            ollamaRepository.refreshConfiguration(userId)

            sideEffect()
        }
    }

    private suspend fun getUserId(): String = authRepository.getUser().id
}

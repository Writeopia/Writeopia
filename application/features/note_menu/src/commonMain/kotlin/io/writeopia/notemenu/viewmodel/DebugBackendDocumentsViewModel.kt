package io.writeopia.notemenu.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.serialization.extensions.toModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DebugBackendDocumentsViewModel(
    private val documentsApi: DocumentsApi,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val expandedFolders = MutableStateFlow(setOf(Folder.ROOT_PATH))
    private val folders = MutableStateFlow<List<Folder>>(emptyList())
    private val documents = MutableStateFlow<List<Document>>(emptyList())

    private val _state = MutableStateFlow<DebugBackendDocumentsState>(DebugBackendDocumentsState.Loading)
    val state: StateFlow<DebugBackendDocumentsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            _state.value = DebugBackendDocumentsState.Loading

            val token = authRepository.getAuthToken()
            val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()

            if (token == null) {
                _state.value = DebugBackendDocumentsState.Error
                return@launch
            }

            when (val result = documentsApi.getWorkspaceContents(workspace.id, token)) {
                is ResultData.Complete -> {
                    folders.value = result.data.folders.map { it.toModel() }
                    documents.value = result.data.documents.map { it.toModel() }
                    publishContent()
                }

                else -> _state.value = DebugBackendDocumentsState.Error
            }
        }
    }

    fun toggleFolder(folderId: String) {
        expandedFolders.value = if (expandedFolders.value.contains(folderId)) {
            expandedFolders.value - folderId
        } else {
            expandedFolders.value + folderId
        }

        publishContent()
    }

    private fun publishContent() {
        val expanded = expandedFolders.value
        _state.value = DebugBackendDocumentsState.Content(
            rows = buildRows(
                parentId = Folder.ROOT_PATH,
                depth = 0,
                expanded = expanded,
                foldersByParent = folders.value.groupBy { it.parentId },
                documentsByParent = documents.value.groupBy { it.parentId },
            )
        )
    }

    private fun buildRows(
        parentId: String,
        depth: Int,
        expanded: Set<String>,
        foldersByParent: Map<String, List<Folder>>,
        documentsByParent: Map<String, List<Document>>,
    ): List<DebugBackendDocumentRow> {
        val childFolders = foldersByParent[parentId].orEmpty().sortedBy { it.title.lowercase() }
        val childDocuments = documentsByParent[parentId].orEmpty().sortedBy { it.title.lowercase() }

        return buildList {
            childFolders.forEach { folder ->
                add(
                    DebugBackendDocumentRow.FolderRow(
                        folder = folder,
                        depth = depth,
                        expanded = expanded.contains(folder.id)
                    )
                )

                if (expanded.contains(folder.id)) {
                    addAll(
                        buildRows(
                            parentId = folder.id,
                            depth = depth + 1,
                            expanded = expanded,
                            foldersByParent = foldersByParent,
                            documentsByParent = documentsByParent
                        )
                    )
                }
            }

            childDocuments.forEach { document ->
                add(DebugBackendDocumentRow.DocumentRow(document = document, depth = depth))
            }
        }
    }
}

sealed interface DebugBackendDocumentsState {
    data object Loading : DebugBackendDocumentsState

    data object Error : DebugBackendDocumentsState

    data class Content(val rows: List<DebugBackendDocumentRow>) : DebugBackendDocumentsState
}

sealed interface DebugBackendDocumentRow {
    val depth: Int
    val item: MenuItem

    data class FolderRow(
        val folder: Folder,
        override val depth: Int,
        val expanded: Boolean
    ) : DebugBackendDocumentRow {
        override val item: MenuItem = folder
    }

    data class DocumentRow(
        val document: Document,
        override val depth: Int
    ) : DebugBackendDocumentRow {
        override val item: MenuItem = document
    }
}

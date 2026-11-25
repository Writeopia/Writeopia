package io.writeopia.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.ai.HeaderImages
import io.writeopia.ai.WorkspaceHeaderImage
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The header pictures page of the workspace settings. */
data class WorkspaceHeaderImagesState(
    val status: Status = Status.LOADING,
    val images: List<WorkspaceHeaderImage> = emptyList(),
    /** The pictures being deleted, drawn dimmed until the backend answers. */
    val deleting: Set<String> = emptySet(),
    val deleteFailed: Boolean = false,
) {
    enum class Status {
        LOADING,
        LOADED,
        FAILED,

        /** Outside an online workspace: the pictures live on the backend only. */
        OFFLINE,
    }
}

/**
 * Lists the header pictures of the workspace and deletes them. A deleted picture leaves the
 * pictures the header menu offers; the documents that use it keep it, since it stays on the CDN.
 */
class WorkspaceHeaderImagesController(
    private val scope: CoroutineScope,
    /** The cloud, when it can answer right now; null outside an online workspace. */
    private val resolveHeaderImages: suspend () -> HeaderImages?,
    private val workspaceId: suspend () -> String?,
) {
    private val _state = MutableStateFlow(WorkspaceHeaderImagesState())
    val state: StateFlow<WorkspaceHeaderImagesState> = _state.asStateFlow()

    /** The pictures deleted here, which a listing that started before may still carry. */
    private val deleted = mutableSetOf<String>()

    fun load() {
        scope.launch {
            val resolved = safely { resolve() }
            if (resolved == null) {
                _state.update { state -> state.copy(status = WorkspaceHeaderImagesState.Status.FAILED) }
                return@launch
            }
            val (source, workspaceId) = resolved

            if (source == null || workspaceId == null) {
                _state.update { state -> state.copy(status = WorkspaceHeaderImagesState.Status.OFFLINE) }
                return@launch
            }

            _state.update { state ->
                // A reload keeps the pictures on screen; only the first load shows the spinner.
                if (state.status == WorkspaceHeaderImagesState.Status.LOADED) {
                    state
                } else {
                    state.copy(status = WorkspaceHeaderImagesState.Status.LOADING)
                }
            }

            val result = safelyResult { source.pictures(workspaceId) }
            _state.update { state ->
                when (result) {
                    is ResultData.Complete -> state.copy(
                        status = WorkspaceHeaderImagesState.Status.LOADED,
                        images = result.data.filterNot { image -> image.id in deleted }
                    )

                    else -> state.copy(status = WorkspaceHeaderImagesState.Status.FAILED)
                }
            }
        }
    }

    fun delete(imageId: String) {
        if (imageId in _state.value.deleting) return

        _state.update { state -> state.copy(deleting = state.deleting + imageId, deleteFailed = false) }

        scope.launch {
            val result = safelyResult {
                val (source, workspaceId) = resolve()
                if (source == null || workspaceId == null) ResultData.Error() else source.delete(workspaceId, imageId)
            }

            _state.update { state ->
                if (result is ResultData.Complete) {
                    deleted.add(imageId)
                    state.copy(
                        images = state.images.filterNot { image -> image.id == imageId },
                        deleting = state.deleting - imageId
                    )
                } else {
                    state.copy(deleting = state.deleting - imageId, deleteFailed = true)
                }
            }
        }
    }

    fun dismissDeleteError() {
        _state.update { state -> state.copy(deleteFailed = false) }
    }

    private suspend fun resolve(): Pair<HeaderImages?, String?> = resolveHeaderImages() to workspaceId()

    /** [block], or null when it throws. Cancellation still goes through. */
    private suspend fun <T> safely(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private suspend fun <T> safelyResult(block: suspend () -> ResultData<T>): ResultData<T> =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ResultData.Error(e)
        }
}

class WorkspaceHeaderImagesViewModel(
    resolveHeaderImages: suspend () -> HeaderImages?,
    workspaceId: suspend () -> String?,
) : ViewModel() {
    private val controller = WorkspaceHeaderImagesController(viewModelScope, resolveHeaderImages, workspaceId)

    val state: StateFlow<WorkspaceHeaderImagesState> = controller.state

    fun load() = controller.load()

    fun delete(imageId: String) = controller.delete(imageId)

    fun dismissDeleteError() = controller.dismissDeleteError()
}

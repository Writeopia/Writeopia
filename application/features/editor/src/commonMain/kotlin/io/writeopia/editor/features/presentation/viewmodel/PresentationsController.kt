package io.writeopia.editor.features.presentation.viewmodel

import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.presentations.PresentationGenerator
import io.writeopia.core.presentations.PresentationsRepository
import io.writeopia.core.presentations.PresentationsStore
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Who makes and keeps the presentations of the document, resolved like the Mac app does: the
 * backend in an online workspace, the local AI (Ollama) elsewhere.
 */
sealed interface PresentationsSource {
    /** The backend generates, parses and keeps the presentations. */
    data class Cloud(val repository: PresentationsRepository, val generator: PresentationGenerator) : PresentationsSource

    /** The local AI writes the Markdown and the app parses and keeps the slides. */
    data class Local(val store: PresentationsStore, val generator: PresentationGenerator) : PresentationsSource

    /** An online workspace without a premium account: the backend would refuse. */
    data object NeedsPremium : PresentationsSource
}

/**
 * The presentations of a document: the ones made before and the making of a new one. The app
 * only shows what the source gives back, like the Mac app.
 */
class PresentationsController(
    private val scope: CoroutineScope,
    private val documentId: () -> String,
    private val authRepository: AuthRepository,
    private val cloud: ((workspaceId: String) -> PresentationsSource.Cloud)?,
    private val local: (suspend (userId: String) -> PresentationsSource.Local?)?,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val _source = MutableStateFlow<PresentationsSource?>(null)
    private val _isAvailable = MutableStateFlow(false)
    private val _showDialog = MutableStateFlow(false)
    private val _premiumRequested = MutableStateFlow(false)
    private val _presentations = MutableStateFlow<List<Presentation>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _isGenerating = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _generated = MutableStateFlow<Presentation?>(null)
    private var generation: Job? = null
    private var workspaceId = ""

    /** True when the button of the side menu should show. */
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()
    val showDialog: StateFlow<Boolean> = _showDialog.asStateFlow()
    val premiumRequested: StateFlow<Boolean> = _premiumRequested.asStateFlow()
    val presentations: StateFlow<List<Presentation>> = _presentations.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    val error: StateFlow<String?> = _error.asStateFlow()

    /** The presentation just made, to be opened; cleared by [consumeGenerated]. */
    val generated: StateFlow<Presentation?> = _generated.asStateFlow()

    init {
        refresh()
    }

    /** Resolves the source again, e.g. after the workspace or the local model changed. */
    fun refresh() {
        scope.launch(dispatcher) {
            val source = resolveSource()
            _source.value = source
            _isAvailable.value = source != null
        }
    }

    private suspend fun resolveSource(): PresentationsSource? {
        val workspace = authRepository.getWorkspace()
        val user = authRepository.getUser()
        workspaceId = workspace?.id ?: Workspace.disconnectedWorkspace().id
        val online = workspace != null && workspace.id != Workspace.disconnectedWorkspace().id
        return when {
            online && cloud != null -> if (user.tier == Tier.PREMIUM) cloud.invoke(workspace.id) else PresentationsSource.NeedsPremium
            local != null -> local.invoke(user.id)
            else -> null
        }
    }

    fun openDialog() {
        if (_source.value == PresentationsSource.NeedsPremium) {
            _premiumRequested.value = true
            return
        }
        _showDialog.value = true
        load()
    }

    fun hideDialog() {
        _showDialog.value = false
    }

    fun dismissPremium() {
        _premiumRequested.value = false
    }

    fun clearError() {
        _error.value = null
    }

    fun consumeGenerated() {
        _generated.value = null
    }

    private fun repository(): PresentationsRepository? =
        when (val source = _source.value) {
            is PresentationsSource.Cloud -> source.repository
            is PresentationsSource.Local -> source.store
            else -> null
        }

    private fun generator(): PresentationGenerator? =
        when (val source = _source.value) {
            is PresentationsSource.Cloud -> source.generator
            is PresentationsSource.Local -> source.generator
            else -> null
        }

    fun load() {
        val repository = repository() ?: return
        scope.launch(dispatcher) {
            _isLoading.value = true
            when (val result = repository.presentations(documentId(), workspaceId)) {
                is ResultData.Complete -> _presentations.value = result.data
                is ResultData.Error -> _error.value = result.exception.message()
                else -> {}
            }
            _isLoading.value = false
        }
    }

    /** Makes a new presentation of the document; it ends up in [generated] and in the list. */
    fun generate() {
        val generator = generator() ?: return
        if (_isGenerating.value) return
        _isGenerating.value = true
        generation = scope.launch(dispatcher) {
            try {
                when (val result = generator.generatePresentation(documentId(), workspaceId)) {
                    is ResultData.Complete -> {
                        _generated.value = result.data
                        load()
                    }
                    is ResultData.Error -> _error.value = result.exception.message()
                    else -> {}
                }
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /** Stops the generation in progress; nothing is kept. */
    fun cancel() {
        generation?.cancel()
        generation = null
    }

    fun delete(presentation: Presentation) {
        val repository = repository() ?: return
        scope.launch(dispatcher) {
            when (val result = repository.deletePresentation(presentation.id, workspaceId)) {
                is ResultData.Complete -> _presentations.value = _presentations.value.filter { it.id != presentation.id }
                is ResultData.Error -> _error.value = result.exception.message()
                else -> {}
            }
        }
    }

    private fun Exception?.message(): String = this?.message?.takeIf { it.isNotBlank() } ?: "Something went wrong. Please try again."
}

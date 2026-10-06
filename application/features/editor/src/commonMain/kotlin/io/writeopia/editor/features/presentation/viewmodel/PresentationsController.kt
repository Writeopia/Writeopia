package io.writeopia.editor.features.presentation.viewmodel

import io.writeopia.ai.AiClients
import io.writeopia.ai.task.AiTaskManager
import io.writeopia.ai.task.AiTaskStatus
import io.writeopia.ai.task.AiTaskType
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.presentations.PresentationException
import io.writeopia.core.presentations.PresentationGenerator
import io.writeopia.core.presentations.PresentationsRepository
import io.writeopia.core.presentations.PresentationsStore
import io.writeopia.model.AiProvider
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.presentation.Presentation
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Who makes and keeps the presentations of the document, resolved like the Mac app does: it
 * follows the AI the user picked, the backend or the local AI (Ollama), each falling back to the
 * other when it isn't ready.
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
 *
 * @param selectedProvider the AI the user picked, see [AiClients.selectedProvider]. The cloud by
 * default, which is all the phones and the web have.
 * @param providerChanges tells when the user picks another provider, so the source follows.
 * @param aiTaskManager runs the generation as an AI task, so it keeps going after the editor is
 * closed and shows like the other AI tasks: the indicator on the desktop, a notification on Android.
 */
class PresentationsController(
    private val scope: CoroutineScope,
    private val documentId: () -> String,
    private val authRepository: AuthRepository,
    private val cloud: ((workspaceId: String) -> PresentationsSource.Cloud)?,
    private val local: (suspend (userId: String) -> PresentationsSource.Local?)?,
    private val selectedProvider: suspend (userId: String) -> AiProvider = { AiProvider.CLOUD },
    private val providerChanges: Flow<AiProvider> = AiClients.providerChanges,
    private val aiTaskManager: AiTaskManager = AiTaskManager.singleton(),
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
    private var generationTaskId: String? = null
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
        // The source follows the workspace: a switch between the open and the private space
        // changes who makes the presentations.
        scope.launch(dispatcher) {
            authRepository.listenForWorkspace()
                .map { it.id }
                .distinctUntilChanged()
                .drop(1)
                .collect { refresh() }
        }
        // And the AI the user picks in the settings
        scope.launch(dispatcher) {
            providerChanges.collect { refresh() }
        }
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

        val cloudSource = when {
            !online || cloud == null -> null
            user.tier == Tier.PREMIUM -> cloud.invoke(workspace.id)
            else -> PresentationsSource.NeedsPremium
        }
        val localSource = local?.invoke(user.id)

        return when (selectedProvider(user.id)) {
            AiProvider.LOCAL -> localSource ?: cloudSource
            AiProvider.CLOUD -> cloudSource ?: localSource
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
        // Claimed atomically: two clicks can't start two generations.
        if (!_isGenerating.compareAndSet(expect = false, update = true)) return

        val documentId = documentId()
        val workspaceId = workspaceId
        val taskId = "presentation-$documentId-${GenerateId.generate()}"
        generationTaskId = taskId

        aiTaskManager.enqueueTask(
            id = taskId,
            type = AiTaskType.PRESENTATION,
            description = "Creating a presentation"
        ) {
            when (val result = generator.generatePresentation(documentId, workspaceId)) {
                is ResultData.Complete -> {
                    _generated.value = result.data
                    load()
                    Result.success(Unit)
                }

                is ResultData.Error -> {
                    val message = result.exception.message()
                    _error.value = message
                    Result.failure(PresentationException(message))
                }

                else -> Result.success(Unit)
            }
        }

        // The task can also end without running, when it's cancelled in the queue, so its status
        // is followed instead of the generation.
        scope.launch(dispatcher) {
            aiTaskManager.tasks.first { tasks ->
                tasks.none { task ->
                    task.id == taskId && (task.status == AiTaskStatus.QUEUED || task.status == AiTaskStatus.RUNNING)
                }
            }
            _isGenerating.value = false
        }
    }

    /** Stops the generation in progress; nothing is kept. */
    fun cancel() {
        generationTaskId?.let(aiTaskManager::cancelTask)
        generationTaskId = null
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

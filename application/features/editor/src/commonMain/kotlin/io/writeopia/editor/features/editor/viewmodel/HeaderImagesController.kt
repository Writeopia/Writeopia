package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.ai.HeaderImages
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The pictures of the header menu: the ones of the workspace, and the one the AI draws for the
 * document, shown to the user to approve or to draw again. Drawing costs tokens of the user, so
 * a picture is only drawn when the user asks, one at a time.
 */
class HeaderImagesController(
    private val scope: CoroutineScope,
    /** The cloud, when it can answer right now; null outside an online workspace. */
    private val resolveHeaderImages: suspend () -> HeaderImages?,
    private val workspaceId: suspend () -> String?,
    private val documentTitle: () -> String,
    private val documentText: () -> String,
    /** Puts an approved picture in the header of the document. */
    private val onApproved: (String) -> Unit,
) {
    private val _state = MutableStateFlow(HeaderImagesState())
    val state: StateFlow<HeaderImagesState> = _state.asStateFlow()

    private var generationJob: Job? = null

    /** Fetches the pictures of the workspace again, each time the header menu opens. */
    fun load() {
        scope.launch {
            val source = resolveHeaderImages()
            val workspaceId = workspaceId()

            if (source == null || workspaceId == null) {
                _state.update { state -> state.copy(canGenerate = false) }
                return@launch
            }

            _state.update { state -> state.copy(canGenerate = true) }

            val result = source.images(workspaceId)
            if (result is ResultData.Complete) {
                // A picture just approved may not be saved on the backend yet: it stays in front.
                _state.update { state ->
                    state.copy(images = state.images.filterNot(result.data::contains) + result.data)
                }
            }
        }
    }

    /** Draws a picture for the document, or a different one in place of the candidate. */
    fun generate() {
        if (_state.value.generation is HeaderImageGeneration.Generating) return

        _state.update { state -> state.copy(generation = HeaderImageGeneration.Generating) }

        generationJob = scope.launch {
            val source = resolveHeaderImages()
            val workspaceId = workspaceId()

            val generation = if (source == null || workspaceId == null) {
                HeaderImageGeneration.Failed(null)
            } else {
                when (val result = source.generate(workspaceId, documentTitle(), documentText())) {
                    is ResultData.Complete -> HeaderImageGeneration.Candidate(result.data)
                    is ResultData.Error -> HeaderImageGeneration.Failed(result.exception?.message)
                    else -> HeaderImageGeneration.Failed(null)
                }
            }

            // Discarded while drawing: the answer is dropped.
            _state.update { state ->
                if (state.generation is HeaderImageGeneration.Generating) state.copy(generation = generation) else state
            }
        }
    }

    /** Puts the candidate in the header and keeps it with the pictures of the workspace. */
    fun approve() {
        val candidate = (_state.value.generation as? HeaderImageGeneration.Candidate)?.url ?: return

        onApproved(candidate)
        _state.update { state ->
            state.copy(
                images = listOf(candidate) + state.images.filterNot { url -> url == candidate },
                generation = HeaderImageGeneration.Idle
            )
        }

        scope.launch {
            val workspaceId = workspaceId() ?: return@launch
            resolveHeaderImages()?.save(workspaceId, candidate)
        }
    }

    /** Drops the candidate, or stops drawing it. */
    fun discard() {
        generationJob?.cancel()
        generationJob = null
        _state.update { state -> state.copy(generation = HeaderImageGeneration.Idle) }
    }
}

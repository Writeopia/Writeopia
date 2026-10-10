package io.writeopia.writingagent.controller

import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.app.dto.writingagent.WritingSuggestionScope
import io.writeopia.app.dto.writingagent.WritingSuggestionsRequest
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.writingagent.actions.WritingActionExecutor
import io.writeopia.writingagent.actions.WritingAgentUi
import io.writeopia.writingagent.api.WritingAgentApi
import io.writeopia.writingagent.model.WritingSuggestion
import io.writeopia.writingagent.model.WritingSuggestionsState
import io.writeopia.writingagent.tracker.SentenceEnds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The writing agent of a document. It watches what the user writes and, each time the number of
 * sentences of the focused paragraph changes, asks the backend which actions the writer wants.
 * When a document opens, it asks once about the whole document (a TL;DR, a conclusion).
 *
 * Only confident suggestions come back; nothing is shown otherwise. The suggestions remember the
 * ID of the paragraph they are about, so an action finds it again after other blocks move.
 */
class WritingAgentController(
    private val scope: CoroutineScope,
    private val api: WritingAgentApi,
    private val storyFlow: StateFlow<StoryState>,
    private val documentIdFlow: Flow<String>,
    private val documentTitle: () -> String,
    private val executor: WritingActionExecutor,
    /** False when nothing should be asked, like offline or signed out. */
    private val isEnabled: suspend () -> Boolean = { true },
    private val debounceMillis: Long = DEBOUNCE_MILLIS,
    private val minimumDocumentLength: Int = MINIMUM_DOCUMENT_LENGTH,
    private val documentTextLimit: Int = DOCUMENT_TEXT_LIMIT,
) {
    private val _state = MutableStateFlow(WritingSuggestionsState.empty())
    val state: StateFlow<WritingSuggestionsState> = _state.asStateFlow()

    private val sentenceEnds = mutableMapOf<String, Int>()
    private var openedDocumentId: String? = null
    private var watching: Job? = null
    private var request: Job? = null
    private var executing: Job? = null

    fun start() {
        if (watching != null) return

        watching = scope.launch {
            launch {
                combine(documentIdFlow, storyFlow) { id, story -> id to story }
                    .collect { (id, story) -> onDocument(id, story) }
            }

            launch {
                storyFlow.collect(::onStoryChanged)
            }
        }
    }

    fun stop() {
        watching?.cancel()
        watching = null
        request?.cancel()
        request = null
    }

    /** Hides the suggestions. They come back with the next sentence. */
    fun dismiss() {
        request?.cancel()
        _state.value = WritingSuggestionsState.empty()
    }

    /** Applies [action] to the paragraph the suggestions are about and drops it from the box. */
    fun execute(action: WritingSuggestionAction, ui: WritingAgentUi) {
        val current = _state.value
        if (current.suggestions.none { it.action == action }) return

        _state.update { state ->
            state.copy(suggestions = state.suggestions.filterNot { it.action == action })
        }

        executing = scope.launch {
            executor.execute(action, current.storyStepId, ui)
        }
    }

    private fun onDocument(id: String, story: StoryState) {
        if (id.isEmpty() || id == openedDocumentId) return

        openedDocumentId = id
        request?.cancel()
        _state.value = WritingSuggestionsState.empty()

        val text = documentText(story)
        if (text.length < minimumDocumentLength) return

        val anchor = story.stories.entries
            .firstOrNull { (_, step) -> step.type == StoryTypes.TITLE.type }
            ?.value
            ?: story.stories.values.firstOrNull()

        request = scope.launch {
            ask(
                storyStepId = anchor?.id,
                request = WritingSuggestionsRequest(
                    scope = WritingSuggestionScope.DOCUMENT_OPENED,
                    documentTitle = documentTitle(),
                    documentText = text,
                )
            )
        }
    }

    private fun onStoryChanged(story: StoryState) {
        val focus = story.focus ?: return
        val step = story.stories[focus] ?: return

        if (step.type == StoryTypes.TITLE.type) return
        val text = step.text ?: return

        val count = SentenceEnds.count(text)
        val previous = sentenceEnds[step.id]
        sentenceEnds[step.id] = count

        // The first sight of a paragraph only records its sentences; a change asks.
        if (previous == null || previous == count || text.isBlank()) return

        scheduleAsk(step.id)

        // The paragraph of the current suggestions may be gone (deleted, or merged).
        val shownId = _state.value.storyStepId
        if (shownId != null && story.stories.values.none { it.id == shownId }) {
            _state.value = WritingSuggestionsState.empty()
        }
    }

    private fun scheduleAsk(storyStepId: String) {
        request?.cancel()
        request = scope.launch {
            delay(debounceMillis)

            val story = storyFlow.value
            val step = story.stories.values.firstOrNull { it.id == storyStepId } ?: return@launch
            val text = step.text?.takeIf { it.isNotBlank() } ?: return@launch

            ask(
                storyStepId = storyStepId,
                request = WritingSuggestionsRequest(
                    scope = WritingSuggestionScope.WRITING,
                    text = text,
                    blockType = StoryTypes.fromNumber(step.type.number).type.name,
                    documentTitle = documentTitle(),
                    documentText = documentText(story),
                )
            )
        }
    }

    private suspend fun ask(storyStepId: String?, request: WritingSuggestionsRequest) {
        if (!isEnabled()) return

        _state.update { it.copy(isLoading = true) }

        when (val result = api.suggestions(request)) {
            is ResultData.Complete -> {
                _state.value = WritingSuggestionsState(
                    storyStepId = storyStepId,
                    scope = request.scope,
                    suggestions = result.data.suggestions.map { dto ->
                        WritingSuggestion(dto.action, dto.probability)
                    },
                    isLoading = false,
                )
            }

            else -> _state.update { it.copy(isLoading = false) }
        }
    }

    private fun documentText(story: StoryState): String =
        story.stories.values
            .mapNotNull(StoryStep::text)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .take(documentTextLimit)

    companion object {
        const val DEBOUNCE_MILLIS = 600L
        const val MINIMUM_DOCUMENT_LENGTH = 200
        const val DOCUMENT_TEXT_LIMIT = 6_000
    }
}

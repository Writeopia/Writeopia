package io.writeopia.writingagent.controller

import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.app.dto.writingagent.WritingSuggestionScope
import io.writeopia.app.dto.writingagent.WritingSuggestionsRequest
import io.writeopia.di.ApiLogger
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
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * The writing agent of a document. It watches what the user writes and, each time the number of
 * sentences of the focused paragraph changes, asks the backend which actions the writer wants.
 * When a document opens, it asks once about the whole document (a TL;DR, a conclusion).
 *
 * Only confident suggestions come back; nothing is shown otherwise. The suggestions remember the
 * ID of the paragraph they are about, so an action finds it again after other blocks move.
 *
 * One suggestion is decided here, without the backend: a document with text but no title is
 * offered a title, until the user writes one, applies it, or dismisses the box.
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
    /** How much text an untitled document needs before a title is offered. */
    private val untitledDocumentMinimumLength: Int = UNTITLED_DOCUMENT_MINIMUM_LENGTH,
    /** Where the agent tells what it asks and what came back. */
    private val log: (String) -> Unit = { message -> ApiLogger.log("$LOG_TAG $message") },
) {
    private val _state = MutableStateFlow(WritingSuggestionsState.empty())
    val state: StateFlow<WritingSuggestionsState> = _state.asStateFlow()

    /** What the backend answered last. [state] is this plus what is decided locally. */
    private var fetched = WritingSuggestionsState.empty()

    /** True while the document has text but no title, and the offer wasn't declined. */
    private var offerDocumentTitle = false

    /** The document whose title offer the user declined, by dismissing or applying it. */
    private var documentTitleDeclinedFor: String? = null

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
        declineDocumentTitle()
        fetched = WritingSuggestionsState.empty()
        publish()
    }

    /** Applies [action] to the paragraph the suggestions are about and drops it from the box. */
    fun execute(action: WritingSuggestionAction, ui: WritingAgentUi) {
        val current = _state.value
        if (current.suggestions.none { it.action == action }) return

        if (action == WritingSuggestionAction.DOCUMENT_TITLE) {
            declineDocumentTitle()
        } else {
            fetched = fetched.copy(suggestions = fetched.suggestions.filterNot { it.action == action })
        }
        publish()

        log("applying $action to ${current.storyStepId}")
        executing = scope.launch {
            executor.execute(action, current.storyStepId, ui)
        }
    }

    private fun declineDocumentTitle() {
        offerDocumentTitle = false
        documentTitleDeclinedFor = openedDocumentId
    }

    /** Shows what the backend answered, with the title offer first when the document needs one. */
    private fun publish() {
        val local = if (offerDocumentTitle) {
            listOf(WritingSuggestion(WritingSuggestionAction.DOCUMENT_TITLE, probability = 1.0))
        } else {
            emptyList()
        }

        _state.value = fetched.copy(suggestions = local + fetched.suggestions)
    }

    /**
     * Offers a title when the document has enough text and its title block is missing or empty.
     * The offer is decided here, not by Jev: the document itself says whether it has a title.
     */
    private fun refreshDocumentTitleOffer(story: StoryState) {
        val titleStep = story.stories.values.firstOrNull { it.type == StoryTypes.TITLE.type }
        val hasTitle = !titleStep?.text.isNullOrBlank()
        val declined = documentTitleDeclinedFor == openedDocumentId
        val longEnough = documentText(story).length >= untitledDocumentMinimumLength

        val offer = !hasTitle && !declined && longEnough

        if (offer != offerDocumentTitle) {
            offerDocumentTitle = offer
            log(if (offer) "document $openedDocumentId has no title: offering one" else "document title offer withdrawn")
            publish()
        }
    }

    private fun onDocument(id: String, story: StoryState) {
        if (id.isEmpty() || id == openedDocumentId) return

        openedDocumentId = id
        request?.cancel()
        fetched = WritingSuggestionsState.empty()
        refreshDocumentTitleOffer(story)
        publish()

        val text = documentText(story)
        if (text.length < minimumDocumentLength) {
            log("document $id opened with ${text.length} chars, under the $minimumDocumentLength minimum: not asking")
            return
        }

        log("document $id opened with ${text.length} chars: asking about the whole document")

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
        refreshDocumentTitleOffer(story)

        val focus = story.focus ?: return
        val step = story.stories[focus] ?: return

        if (step.type == StoryTypes.TITLE.type) return
        val text = step.text ?: return

        val count = SentenceEnds.count(text)
        val previous = sentenceEnds[step.id]
        sentenceEnds[step.id] = count

        // The first sight of a paragraph only records its sentences; a change asks.
        if (previous == null || previous == count || text.isBlank()) return

        log("paragraph ${step.id} went from $previous to $count sentence ends: asking in ${debounceMillis}ms")
        scheduleAsk(step.id)

        // The paragraph of the current suggestions may be gone (deleted, or merged).
        val shownId = fetched.storyStepId
        if (shownId != null && story.stories.values.none { it.id == shownId }) {
            fetched = WritingSuggestionsState.empty()
            publish()
        }
    }

    private fun scheduleAsk(storyStepId: String) {
        request?.cancel()
        request = scope.launch {
            delay(debounceMillis)

            val story = storyFlow.value
            val step = story.stories.values.firstOrNull { it.id == storyStepId }
            if (step == null) {
                log("paragraph $storyStepId is gone: not asking")
                return@launch
            }
            val text = step.text?.takeIf { it.isNotBlank() }
            if (text == null) {
                log("paragraph $storyStepId is blank now: not asking")
                return@launch
            }

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
        if (!isEnabled()) {
            log("${request.scope} request for $storyStepId skipped: the agent is disabled (offline, signed out or locked)")
            return
        }

        log(
            "${request.scope} request for $storyStepId: paragraph of ${request.text.length} chars, " +
                "document of ${request.documentText?.length ?: 0} chars, block ${request.blockType}"
        )
        fetched = fetched.copy(isLoading = true)
        publish()
        val started = TimeSource.Monotonic.markNow()

        when (val result = api.suggestions(request)) {
            is ResultData.Complete -> {
                val suggestions = result.data.suggestions.map { dto ->
                    WritingSuggestion(dto.action, dto.probability)
                }

                log(
                    "${request.scope} answer for $storyStepId in ${started.elapsedNow().inWholeMilliseconds}ms: " +
                        if (suggestions.isEmpty()) {
                            "nothing passed the threshold"
                        } else {
                            suggestions.joinToString { "${it.action} ${(it.probability * 100).toInt()}%" }
                        }
                )

                fetched = WritingSuggestionsState(
                    storyStepId = storyStepId,
                    scope = request.scope,
                    suggestions = suggestions,
                    isLoading = false,
                )
                publish()
            }

            is ResultData.Error -> {
                log(
                    "${request.scope} request for $storyStepId failed in " +
                        "${started.elapsedNow().inWholeMilliseconds}ms: ${result.exception?.message}"
                )
                fetched = fetched.copy(isLoading = false)
                publish()
            }

            else -> {
                fetched = fetched.copy(isLoading = false)
                publish()
            }
        }
    }

    private fun documentText(story: StoryState): String =
        story.stories.values
            .mapNotNull(StoryStep::text)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .take(documentTextLimit)

    companion object {
        const val LOG_TAG = "[WritingAgent]"
        const val DEBOUNCE_MILLIS = 600L
        const val MINIMUM_DOCUMENT_LENGTH = 200
        const val UNTITLED_DOCUMENT_MINIMUM_LENGTH = 80
        const val DOCUMENT_TEXT_LIMIT = 6_000
    }
}

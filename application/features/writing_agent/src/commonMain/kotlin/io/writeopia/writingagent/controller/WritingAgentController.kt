package io.writeopia.writingagent.controller

import io.writeopia.app.dto.writingagent.FolderSuggestionsRequest
import io.writeopia.app.dto.writingagent.TagSuggestionsRequest
import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.app.dto.writingagent.WritingSuggestionScope
import io.writeopia.app.dto.writingagent.WritingSuggestionsRequest
import io.writeopia.di.ApiLogger
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.writingagent.actions.WritingActionExecutor
import io.writeopia.writingagent.actions.WritingAgentUi
import io.writeopia.writingagent.api.WritingAgentApi
import io.writeopia.writingagent.folders.DocumentFolders
import io.writeopia.writingagent.model.FolderDialogState
import io.writeopia.writingagent.model.FolderSuggestion
import io.writeopia.writingagent.model.TagDialogState
import io.writeopia.writingagent.model.TagSuggestion
import io.writeopia.writingagent.model.WritingSuggestion
import io.writeopia.writingagent.model.WritingSuggestionsState
import io.writeopia.writingagent.tags.DocumentTags
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
 *
 * A document that sits in the root folder is also offered a home. The box offers it, decided
 * here like the title; only when the user takes the offer does a dialog open and the backend
 * judge the first workspace folders. The dialog shows the ones that fit, and a new folder named
 * by the AI. Nothing of this is offered to a document that already sits in a folder.
 *
 * A document without tags is offered tags the same way: the box offers it, decided here; when
 * the user takes the offer a dialog opens and the backend judges the workspace catalogue. The
 * dialog shows the tags that fit, pre-selected, and a new tag made up by the AI.
 */
class WritingAgentController(
    private val scope: CoroutineScope,
    private val api: WritingAgentApi,
    private val storyFlow: StateFlow<StoryState>,
    private val documentIdFlow: Flow<String>,
    private val documentTitle: () -> String,
    private val executor: WritingActionExecutor,
    /** Files the document. Null where the app has no folders to offer. */
    private val folders: DocumentFolders? = null,
    /** Tags the document. Null where the app has no tag catalogue. */
    private val tags: DocumentTags? = null,
    /** False when nothing should be asked, like offline or signed out. */
    private val isEnabled: suspend () -> Boolean = { true },
    private val debounceMillis: Long = DEBOUNCE_MILLIS,
    private val minimumDocumentLength: Int = MINIMUM_DOCUMENT_LENGTH,
    private val documentTextLimit: Int = DOCUMENT_TEXT_LIMIT,
    /** How many finished sentences an untitled document needs before a title is offered. */
    private val untitledDocumentMinimumSentences: Int = UNTITLED_DOCUMENT_MINIMUM_SENTENCES,
    /** How many folders are asked about when a root document opens. */
    private val folderCandidateLimit: Int = FolderSuggestionsRequest.MAX_FOLDERS,
    /** How much text a root document needs before a folder is offered. Less than the TL;DR needs. */
    private val folderMinimumDocumentLength: Int = FOLDER_MINIMUM_DOCUMENT_LENGTH,
    /** How many tags of the catalogue are asked about when the user takes the tag offer. */
    private val tagCandidateLimit: Int = TagSuggestionsRequest.MAX_TAGS,
    /** How much text an untagged document needs before tags are offered. */
    private val tagMinimumDocumentLength: Int = TAG_MINIMUM_DOCUMENT_LENGTH,
    /** Where the agent tells what it asks and what came back. */
    private val log: (String) -> Unit = { message -> ApiLogger.log("$LOG_TAG $message") },
    /** Whether the box opens folded to its badge; phones do, to keep the text in view. */
    startCollapsed: Boolean = false,
) {
    private val _state = MutableStateFlow(WritingSuggestionsState.empty())
    val state: StateFlow<WritingSuggestionsState> = _state.asStateFlow()

    private val _collapsed = MutableStateFlow(startCollapsed)

    /**
     * True when the user folded the box down to a small "AI suggestions" badge with a count, to
     * focus on the text. The suggestions keep coming; only the list is hidden. The choice lasts
     * the session, across documents.
     */
    val collapsed: StateFlow<Boolean> = _collapsed.asStateFlow()

    fun toggleCollapsed() {
        _collapsed.value = !_collapsed.value
        log(if (_collapsed.value) "suggestions box collapsed" else "suggestions box expanded")
    }

    /** What the backend answered last. [state] is this plus what is decided locally. */
    private var fetched = WritingSuggestionsState.empty()

    /** True while the document has text but no title, and the offer wasn't declined. */
    private var offerDocumentTitle = false

    /** The document whose title offer the user declined, by dismissing or applying it. */
    private var documentTitleDeclinedFor: String? = null

    /** True while the box offers to file the document: it sits in the root and wasn't declined. */
    private var offerFolder = false

    /** The dialog filing the open document, while it is shown. */
    private var folderDialog: FolderDialogState? = null

    /** The document whose folder offer the user declined, by dismissing the box, moving or filing it. */
    private var foldersDeclinedFor: String? = null

    /** The document already checked for the folder offer, so a growing document is checked once. */
    private var foldersCheckedFor: String? = null
    private var folderRequest: Job? = null

    /** True while the box offers to tag the document: it has no tags and the offer wasn't declined. */
    private var offerTags = false

    /** The dialog tagging the open document, while it is shown. */
    private var tagDialog: TagDialogState? = null

    /** The document whose tag offer the user declined, by dismissing the box or the dialog, or tagging it. */
    private var tagsDeclinedFor: String? = null

    /** The document already offered tags, so a growing document is offered once. */
    private var tagsCheckedFor: String? = null
    private var tagRequest: Job? = null

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
        folderRequest?.cancel()
        folderRequest = null
        tagRequest?.cancel()
        tagRequest = null
    }

    /** Hides the suggestions, the folder and tag offers included. They come back with the next sentence. */
    fun dismiss() {
        request?.cancel()
        folderRequest?.cancel()
        tagRequest?.cancel()
        declineDocumentTitle()
        declineFolder()
        declineTags()
        fetched = WritingSuggestionsState.empty()
        publish()
    }

    /**
     * Takes the folder offer: opens the dialog with a spinner and asks the backend which of the
     * first workspace folders the document belongs to.
     */
    fun openFolderDialog() {
        val documentFolders = folders ?: return
        val documentId = openedDocumentId ?: return
        if (!offerFolder || folderDialog != null) return

        folderDialog = FolderDialogState(isLoading = true)
        publish()

        folderRequest?.cancel()
        folderRequest = scope.launch {
            askFolders(documentFolders, documentId, documentText(storyFlow.value))
        }
    }

    /** Closes the folder dialog without moving the document. The box keeps offering it. */
    fun dismissFolderDialog() {
        folderRequest?.cancel()
        folderDialog = null
        publish()
    }

    /** Moves the document to [folderId], one of the folders the dialog offers, and closes it. */
    fun moveToFolder(folderId: String) {
        val documentFolders = folders ?: return
        val dialog = folderDialog ?: return
        if (dialog.isCreating || dialog.options.none { it.folderId == folderId }) return

        declineFolder()
        publish()

        log("moving document $openedDocumentId to folder $folderId")
        executing = scope.launch { documentFolders.moveTo(folderId) }
    }

    /**
     * Makes a folder named by the AI and moves the document there. The dialog shows a spinner
     * meanwhile and closes once the document moved, or when the AI failed.
     */
    fun createFolder() {
        val documentFolders = folders ?: return
        val dialog = folderDialog ?: return
        if (dialog.isLoading || dialog.isCreating) return

        folderDialog = dialog.copy(isCreating = true)
        publish()

        log("creating a folder for document $openedDocumentId")
        executing = scope.launch {
            documentFolders.createFolderAndMove(documentTitle(), documentText(storyFlow.value))
                .onSuccess { name -> log("document $openedDocumentId moved to the new folder \"$name\"") }
                .onFailure { log("creating a folder for $openedDocumentId failed: ${it.message}") }

            declineFolder()
            publish()
        }
    }

    /** Withdraws the folder offer and its dialog for this document. */
    private fun declineFolder() {
        offerFolder = false
        folderDialog = null
        foldersDeclinedFor = openedDocumentId
    }

    /**
     * Takes the tag offer: opens the dialog with a spinner and asks the backend which tags of
     * the catalogue the document should carry.
     */
    fun openTagDialog() {
        val documentTags = tags ?: return
        val documentId = openedDocumentId ?: return
        if (!offerTags || tagDialog != null) return

        tagDialog = TagDialogState(isLoading = true)
        publish()

        tagRequest?.cancel()
        tagRequest = scope.launch {
            askTags(documentTags, documentId, documentText(storyFlow.value))
        }
    }

    /** Closes the tag dialog without tagging the document. The box keeps offering it. */
    fun dismissTagDialog() {
        tagRequest?.cancel()
        tagDialog = null
        publish()
    }

    /** Keeps or drops one of the offered tags before they are added. */
    fun toggleTag(tagId: String) {
        val dialog = tagDialog ?: return
        if (dialog.isLoading || dialog.isCreating || dialog.options.none { it.tagId == tagId }) return

        tagDialog = dialog.copy(
            selected = if (tagId in dialog.selected) dialog.selected - tagId else dialog.selected + tagId
        )
        publish()
    }

    /** Adds the selected tags to the document, closes the dialog and withdraws the offer. */
    fun applyTags() {
        val documentTags = tags ?: return
        val dialog = tagDialog ?: return
        if (dialog.isLoading || dialog.isCreating) return

        val selected = dialog.selected.filter { id -> dialog.options.any { it.tagId == id } }.toSet()
        declineTags()
        publish()

        if (selected.isEmpty()) return

        log("adding tags ${selected.joinToString()} to document $openedDocumentId")
        executing = scope.launch { documentTags.addTags(selected) }
    }

    /**
     * Makes a tag made up by the AI and adds it to the document. The dialog shows a spinner
     * meanwhile and closes once the tag was added, or when the AI failed.
     */
    fun createTagWithAi() {
        val documentTags = tags ?: return
        val dialog = tagDialog ?: return
        if (dialog.isLoading || dialog.isCreating) return

        tagDialog = dialog.copy(isCreating = true)
        publish()

        log("creating a tag for document $openedDocumentId")
        executing = scope.launch {
            documentTags.createTagWithAi(documentTitle(), documentText(storyFlow.value))
                .onSuccess { tag -> log("document $openedDocumentId tagged with the new tag \"${tag.name}\"") }
                .onFailure { log("creating a tag for $openedDocumentId failed: ${it.message}") }

            declineTags()
            publish()
        }
    }

    /** Withdraws the tag offer and its dialog for this document. */
    private fun declineTags() {
        offerTags = false
        tagDialog = null
        tagsDeclinedFor = openedDocumentId
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

        _state.value = fetched.copy(
            suggestions = local + fetched.suggestions,
            offerFolder = offerFolder,
            folderDialog = folderDialog,
            offerTags = offerTags,
            tagDialog = tagDialog,
        )
    }

    /**
     * Offers a title once the document has a finished sentence and its title block is missing or
     * empty. The offer is decided here, not by Jev: the document itself says whether it has a
     * title.
     */
    private fun refreshDocumentTitleOffer(story: StoryState) {
        val titleStep = story.stories.values.firstOrNull { it.type == StoryTypes.TITLE.type }
        val hasTitle = !titleStep?.text.isNullOrBlank()
        val declined = documentTitleDeclinedFor != null && documentTitleDeclinedFor == openedDocumentId
        val bodyText = story.stories.values
            .filter { it.type != StoryTypes.TITLE.type }
            .mapNotNull(StoryStep::text)
            .joinToString(separator = "\n")
        val enoughSentences = SentenceEnds.count(bodyText) >= untitledDocumentMinimumSentences

        val offer = !hasTitle && !declined && enoughSentences

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
        folderRequest?.cancel()
        tagRequest?.cancel()
        fetched = WritingSuggestionsState.empty()
        offerFolder = false
        folderDialog = null
        offerTags = false
        tagDialog = null
        refreshDocumentTitleOffer(story)
        publish()

        val text = documentText(story)
        considerFolders(id, text)
        considerTags(id, text)

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

    /**
     * Offers a folder once per document, as soon as it has enough text: when it opens, or later
     * while the user writes a document that opened empty. Only a document in the root is
     * offered; the backend is not asked until the user takes the offer.
     */
    private fun considerFolders(documentId: String, text: String) {
        val documentFolders = folders ?: return
        if (foldersCheckedFor == documentId || foldersDeclinedFor == documentId) return
        if (text.length < folderMinimumDocumentLength) return

        foldersCheckedFor = documentId
        folderRequest = scope.launch {
            val currentFolder = documentFolders.currentFolderId()
            if (currentFolder != Folder.ROOT_PATH) {
                log("document $documentId is in folder $currentFolder, not the root: no folder offered")
                return@launch
            }
            if (openedDocumentId != documentId) return@launch

            log("document $documentId is in the root: offering a folder")
            offerFolder = true
            publish()
        }
    }

    /**
     * Offers tags once per document, as soon as it has enough text and carries none: when it
     * opens, or later while the user writes. The backend is not asked until the user takes the
     * offer. An offer is withdrawn when the document gets a tag another way.
     */
    private fun considerTags(documentId: String, text: String) {
        val documentTags = tags ?: return

        if (offerTags && documentTags.currentTagIds().isNotEmpty()) {
            log("document $documentId got tags: tag offer withdrawn")
            offerTags = false
            tagDialog = null
            publish()
            return
        }

        if (tagsCheckedFor == documentId || tagsDeclinedFor == documentId) return
        if (text.length < tagMinimumDocumentLength) return
        if (documentTags.currentTagIds().isNotEmpty()) {
            tagsCheckedFor = documentId
            return
        }

        tagsCheckedFor = documentId
        log("document $documentId has no tags: offering some")
        offerTags = true
        publish()
    }

    /**
     * Fills the open dialog: the tags the backend says the document should carry, among the
     * first tags of the catalogue, all selected. Without tags, offline, or when the backend
     * fails, the dialog only offers a new tag.
     */
    private suspend fun askTags(documentTags: DocumentTags, documentId: String, text: String) {
        val candidates = documentTags.candidates(tagCandidateLimit)
        val started = TimeSource.Monotonic.markNow()

        val matches = when {
            candidates.isEmpty() -> {
                log("workspace of document $documentId has no tags: the dialog offers only a new tag")
                emptyList()
            }

            !isEnabled() -> {
                log("tag request for $documentId skipped: the agent is disabled (offline, signed out or locked)")
                emptyList()
            }

            else -> {
                log(
                    "asking about ${candidates.size} tags for document $documentId with ${text.length} chars: " +
                        candidates.joinToString { it.name }
                )
                val request = TagSuggestionsRequest(
                    documentTitle = documentTitle(),
                    documentText = text,
                    tags = candidates,
                )

                when (val result = api.tagSuggestions(request)) {
                    is ResultData.Complete -> result.data.suggestions.mapNotNull { dto ->
                        candidates.firstOrNull { it.id == dto.tagId }
                            ?.let { tag -> TagSuggestion(tag.id, tag.name, tag.description, dto.probability) }
                    }.also { matches ->
                        log(
                            "tag answer for $documentId in ${started.elapsedNow().inWholeMilliseconds}ms: " +
                                matches.joinToString { "${it.name} ${(it.probability * 100).toInt()}%" }
                                    .ifEmpty { "no tag passed the threshold" }
                        )
                    }

                    is ResultData.Error -> {
                        log(
                            "tag request for $documentId failed in " +
                                "${started.elapsedNow().inWholeMilliseconds}ms: ${result.exception?.message}; " +
                                "the dialog offers only a new tag"
                        )
                        emptyList()
                    }

                    else -> emptyList()
                }
            }
        }

        // The dialog may have been closed, or the document, while the backend answered.
        if (openedDocumentId != documentId || tagDialog == null) return

        tagDialog = TagDialogState(
            isLoading = false,
            options = matches,
            selected = matches.map { it.tagId }.toSet(),
            catalogueIsEmpty = candidates.isEmpty(),
        )
        publish()
    }

    /**
     * Fills the open dialog: the folders the backend says the document belongs to. With the
     * workspace id the backend picks the candidates itself, the folders closest to the document
     * among all of the workspace; the root folders sent along are its fallback. Without folders,
     * offline, or when the backend fails, the dialog only offers a new folder, since making one
     * needs no backend.
     */
    private suspend fun askFolders(documentFolders: DocumentFolders, documentId: String, text: String) {
        val workspaceId = documentFolders.workspaceId()
        val candidates = documentFolders.candidates(folderCandidateLimit)
        val started = TimeSource.Monotonic.markNow()

        val matches = when {
            candidates.isEmpty() && workspaceId.isBlank() -> {
                log("document $documentId has no folder to go to: the dialog offers only a new folder")
                emptyList()
            }

            !isEnabled() -> {
                log("folder request for $documentId skipped: the agent is disabled (offline, signed out or locked)")
                emptyList()
            }

            else -> {
                log(
                    "asking about ${candidates.size} folders for document $documentId with ${text.length} chars: " +
                        candidates.joinToString { it.name }
                )
                val request = FolderSuggestionsRequest(
                    documentTitle = documentTitle(),
                    documentText = text,
                    folders = candidates,
                    workspaceId = workspaceId,
                )

                when (val result = api.folderSuggestions(request)) {
                    is ResultData.Complete -> result.data.suggestions.mapNotNull { dto ->
                        // The backend may answer with a folder the app did not send: it names it.
                        val name = candidates.firstOrNull { it.id == dto.folderId }?.name
                            ?: dto.folderName.takeIf { it.isNotBlank() }
                        name?.let { FolderSuggestion(dto.folderId, it, dto.probability) }
                    }.also { matches ->
                        log(
                            "folder answer for $documentId in ${started.elapsedNow().inWholeMilliseconds}ms: " +
                                matches.joinToString { "${it.name} ${(it.probability * 100).toInt()}%" }
                                    .ifEmpty { "no folder passed the threshold" }
                        )
                    }

                    is ResultData.Error -> {
                        log(
                            "folder request for $documentId failed in " +
                                "${started.elapsedNow().inWholeMilliseconds}ms: ${result.exception?.message}; " +
                                "the dialog offers only a new folder"
                        )
                        emptyList()
                    }

                    else -> emptyList()
                }
            }
        }

        // The dialog may have been closed, or the document, while the backend answered.
        if (openedDocumentId != documentId || folderDialog == null) return

        folderDialog = FolderDialogState(isLoading = false, options = matches)
        publish()
    }

    private fun onStoryChanged(story: StoryState) {
        refreshDocumentTitleOffer(story)
        openedDocumentId?.let { id ->
            val text = documentText(story)
            considerFolders(id, text)
            considerTags(id, text)
        }

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
        const val UNTITLED_DOCUMENT_MINIMUM_SENTENCES = 1
        const val DOCUMENT_TEXT_LIMIT = 6_000
        const val FOLDER_MINIMUM_DOCUMENT_LENGTH = 40
        const val TAG_MINIMUM_DOCUMENT_LENGTH = 40
    }
}

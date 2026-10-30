@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.sdk.utils.iterables.Rank
import androidx.compose.ui.text.buildAnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.writeopia.LocalAiRepository
import io.writeopia.ai.AiClients
import io.writeopia.ai.AiCommand
import io.writeopia.ai.HeaderImages
import io.writeopia.ai.UiConfigurationAiProviderStore
import io.writeopia.ai.task.AiTaskManager
import io.writeopia.ai.task.AiTaskType
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.genai.repository.GenAiRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.sync.ContentLoadActivity
import io.writeopia.core.folders.sync.DocumentSyncService
import io.writeopia.core.presentations.PresentationsStore
import io.writeopia.core.presentations.api.PresentationsApi
import io.writeopia.editor.features.presentation.repository.CloudPresentationsSaver
import io.writeopia.editor.features.presentation.repository.LocalPresentationGenerator
import io.writeopia.editor.features.presentation.viewmodel.PresentationsController
import io.writeopia.editor.features.presentation.viewmodel.PresentationsSource
import io.writeopia.common.utils.file.SaveImage
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.common.utils.toList
import io.writeopia.core.folders.repository.folder.DocumentLoadUseCase
import io.writeopia.core.folders.repository.InDocumentSearchRepository
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.core.folders.repository.tag.DocumentTagRepository
import io.writeopia.editor.features.editor.copy.CopyManager
import io.writeopia.editor.features.search.FindInText
import io.writeopia.editor.model.EditState
import io.writeopia.model.Font
import io.writeopia.models.interfaces.configuration.WorkspaceConfigRepository
import io.writeopia.repository.UiConfigurationRepository
import io.writeopia.requests.ModelsResponse
import io.writeopia.sdk.export.DocumentToJson
import io.writeopia.sdk.export.DocumentToMarkdown
import io.writeopia.sdk.export.DocumentWriter
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.model.story.Selection
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.document.PendingFavorite
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.persistence.core.sync.DocumentSyncManager
import io.writeopia.sdk.persistence.core.tracker.OnUpdateDocumentTracker
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.serialization.request.LinkCardDescriptionRequest
import io.writeopia.sdk.serialization.request.StoryStepSyncRequest
import io.writeopia.sdk.serialization.response.StoryStepSyncResponse
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.ui.model.LinkCardCreation
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sdk.serialization.request.wrapInRequest
import io.writeopia.sdk.sharededition.SharedEditionManager
import io.writeopia.sdk.utils.extensions.noContent
import io.writeopia.editor.di.DrawingSaveEvent
import io.writeopia.ui.backstack.BackstackHandler
import io.writeopia.ui.backstack.BackstackInform
import io.writeopia.ui.keyboard.KeyboardEvent
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawState
import io.writeopia.ui.model.SelectionMetadata
import io.writeopia.ui.utils.Spans
import io.writeopia.writingagent.actions.WriteopiaActionExecutor
import io.writeopia.writingagent.folders.WriteopiaDocumentFolders
import io.writeopia.writingagent.tags.WriteopiaDocumentTags
import io.writeopia.app.dto.writingagent.TagCreateRequest
import io.writeopia.writingagent.api.WritingAgentApi
import io.writeopia.writingagent.controller.WritingAgentController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class NoteEditorKmpViewModel(
    override val writeopiaManager: WriteopiaStateManager,
    private val documentRepository: DocumentRepository,
    private val sharedEditionManager: SharedEditionManager,
    private val parentFolderId: String,
    private val uiConfigurationRepository: UiConfigurationRepository,
    private val documentToMarkdown: DocumentToMarkdown = DocumentToMarkdown,
    private val documentToJson: DocumentToJson = DocumentToJson(),
    private val folderRepository: FolderRepository,
    private val localAiRepository: LocalAiRepository? = null,
    private val genAiRepository: GenAiRepository? = null,
    private val workspaceConfigRepository: WorkspaceConfigRepository,
    private val keyboardEventFlow: Flow<KeyboardEvent>,
    private val copyManager: CopyManager,
    private val authRepository: AuthRepository,
    private val inDocumentSearchRepository: InDocumentSearchRepository,
    private val drawingSaveEvents: SharedFlow<DrawingSaveEvent>? = null,
    private val documentSyncManager: DocumentSyncManager = DocumentSyncManager.singleton(),
    private val documentLoadUseCase: DocumentLoadUseCase? = null,
    private val storyStepSyncApi: (suspend (StoryStepSyncRequest) -> StoryStepSyncResponse)? = null,
    private val documentsApi: DocumentsApi? = null,
    /** Sends the favorites changed here; null where there is no backend. */
    private val documentSyncService: DocumentSyncService? = null,
    private val aiTaskManager: AiTaskManager = AiTaskManager.singleton(),
    /** Picks the AI that answers, local or cloud, following the choice of the user in the settings. */
    private val aiClients: AiClients = AiClients(
        localAiRepository = localAiRepository,
        genAiRepository = genAiRepository,
        authRepository = authRepository,
        providerStore = UiConfigurationAiProviderStore(uiConfigurationRepository),
    ),
    presentationsApi: PresentationsApi? = null,
    presentationsStore: PresentationsStore? = null,
    private val writingAgentApi: WritingAgentApi? = null,
    private val writingAgentStartsCollapsed: Boolean = false,
    /** The workspace tag catalogue. Null where the app has no tags. */
    private val documentTagRepository: DocumentTagRepository? = null,
    /** The header pictures of the cloud; null when it can't answer right now. */
    private val resolveHeaderImages: suspend () -> HeaderImages? = { aiClients.headerImages() },
) : NoteEditorViewModel,
    ViewModel(),
    BackstackInform by writeopiaManager,
    BackstackHandler by writeopiaManager {

    /**
     * The presentations of the document, made by the AI the user picked: the backend in an online
     * workspace, or the local AI, like the Mac app. The phones only have the backend.
     */
    override val presentations: PresentationsController? =
        if (presentationsApi != null || presentationsStore != null) {
            PresentationsController(
                scope = viewModelScope,
                documentId = { writeopiaManager.documentInfo.value.id },
                authRepository = authRepository,
                selectedProvider = aiClients::selectedProvider,
                localStore = presentationsStore,
                // What the cloud sends is kept on the device too, for the search and offline.
                cloud = presentationsApi?.let { api -> cloudSource(api, presentationsStore) },
                local = if (presentationsStore != null && localAiRepository != null) {
                    { userId ->
                        if (localAiRepository.getSelectedModel(userId) == null) {
                            null
                        } else {
                            PresentationsSource.Local(
                                store = presentationsStore,
                                generator = LocalPresentationGenerator(
                                    localAiRepository = localAiRepository,
                                    store = presentationsStore,
                                    userId = { userId },
                                    documentTitle = { writeopiaManager.getDocument().title },
                                    documentMarkdown = { documentToMarkdown.parse(writeopiaManager.getDocument().content) }
                                )
                            )
                        }
                    }
                } else {
                    null
                }
            )
        } else {
            null
        }

    /**
     * The writing agent, which only asks the backend with a session in an online workspace. The
     * text it generates comes from the AI the user picked, like the other AI commands.
     */
    override val writingAgent: WritingAgentController? = writingAgentApi?.let { api ->
        WritingAgentController(
            scope = viewModelScope,
            api = api,
            startCollapsed = writingAgentStartsCollapsed,
            storyFlow = writeopiaManager.currentStory,
            documentIdFlow = writeopiaManager.documentInfo.map { info -> info.id },
            documentTitle = { writeopiaManager.documentInfo.value.title },
            executor = WriteopiaActionExecutor(
                manager = writeopiaManager,
                resolveAi = { aiClients.resolve(authRepository.getUser().id) },
                // Only the cloud draws; without it the picker opens.
                resolveImageAi = { aiClients.imageGenerator() },
                addImage = { path, position -> addImage(path, position) },
                aiTaskManager = aiTaskManager,
                // The same prefix as the editor's own AI tasks, so Escape cancels these too.
                taskIdPrefix = { "editor-${writeopiaManager.documentInfo.value.id}" },
            ),
            // A document created in the root folder is offered a folder when it opens.
            folders = WriteopiaDocumentFolders(
                documentId = { writeopiaManager.documentInfo.value.id },
                documentParentId = { writeopiaManager.getDocument().parentId },
                resolveWorkspaceId = { (authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()).id },
                folderRepository = folderRepository,
                moveDocument = { id, folderId -> documentRepository.moveToFolder(documentId = id, parentId = folderId) },
                resolveAi = { aiClients.resolve(authRepository.getUser().id) },
                aiTaskManager = aiTaskManager,
                taskIdPrefix = { "editor-${writeopiaManager.documentInfo.value.id}" },
            ),
            // A document without tags is offered some from the workspace catalogue.
            tags = documentTagRepository?.let { tagRepository ->
                WriteopiaDocumentTags(
                    documentTagIds = { writeopiaManager.documentTagIds.value },
                    workspaceId = { (authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()).id },
                    tagRepository = tagRepository,
                    api = api,
                    addToDocument = writeopiaManager::addDocumentTag,
                    sendTag = { tag -> sendTagToBackend(tag) },
                )
            },
            isEnabled = { !writeopiaManager.documentInfo.value.isLocked && aiClients.isCloudAiReady() },
        ).also { it.start() }
    }

    /**
     * Sends a tag of the catalogue to the backend right away and marks it synced on success. When
     * it fails, the workspace sync sends it later.
     */
    private suspend fun sendTagToBackend(tag: DocumentTag) {
        val api = documentsApi ?: return
        val repository = documentTagRepository ?: return
        if (tag.workspaceId == Workspace.disconnectedWorkspace().id) return

        if (api.sendTags(listOf(tag), tag.workspaceId) is ResultData.Complete) {
            repository.upsertTag(tag.copy(lastSyncedAt = Clock.System.now()))
        }
    }

    init {
        viewModelScope.launch(Dispatchers.Default) {
            writeopiaManager.linkCardCreated.collect { creation ->
                launch { describeLinkCard(creation) }
            }
        }

        viewModelScope.launch(Dispatchers.Default) {
            keyboardEventFlow
                .onEach { delay(60) }
                .collect { event ->
                    when (event) {
                        KeyboardEvent.LOCAL_SAVE -> {
                            saveDocumentInWorkSpace()
                        }

                        KeyboardEvent.COPY -> {
                            copySelection()
                        }

                        KeyboardEvent.CUT -> {
                            copySelection()
                            deleteSelection()
                        }

                        KeyboardEvent.AI_QUESTION -> {
                            // Keyboard shortcut uses CURSOR mode by default
                            askAiWithMode(AiTargetMode.CURSOR)
                        }

                        KeyboardEvent.CANCEL -> {
                            writeopiaManager.clearSelection()
                            hideSearch()
                            // Cancel any running AI tasks for this document
                            val docId = documentId.value
                            if (docId.isNotEmpty()) {
                                aiTaskManager.cancelTasksByPrefix("editor-$docId")
                            }
                            aiJob?.cancel()
                        }

                        KeyboardEvent.UNDO -> {
                            writeopiaManager.undo()
                        }

                        KeyboardEvent.REDO -> {
                            writeopiaManager.redo()
                        }

                        KeyboardEvent.SEARCH -> {
                            showSearch()
                        }

                        KeyboardEvent.LIST -> {
                            writeopiaManager.addListItem()
                        }

                        else -> {}
                    }
                }
        }

        viewModelScope.launch {
            localAiRepository?.refreshConfiguration(authRepository.getUser().id)
        }

        // Subscribe to drawing save events to update in-memory state
        drawingSaveEvents?.let { events ->
            viewModelScope.launch(Dispatchers.Default) {
                events.collect { event ->
                    handleDrawingSaveEvent(event)
                }
            }
        }
    }

    private fun handleDrawingSaveEvent(event: DrawingSaveEvent) {
        // Only handle events for the current document
        val currentDocId = writeopiaManager.documentInfo.value.id
        if (currentDocId != event.documentId) return

        val existingStory = writeopiaManager.currentStory.value.stories[event.position]

        if (existingStory != null && existingStory.id == event.storyStep.id) {
            // Update existing story
            val stateChange = Action.StoryStateChange(event.storyStep, event.position)
            writeopiaManager.changeStoryState(stateChange)
        } else {
            // Add new story at position
            writeopiaManager.addAtPosition(event.storyStep, event.position)
        }
    }

    private var isDarkTheme: Boolean = true

    private val showSearch = MutableStateFlow(false)
    private val _searchText = MutableStateFlow("")
    private val _currentSearchIndexState = MutableStateFlow(0)
    private val _totalSearchResultsState = MutableStateFlow(0)

    override val showSearchState: StateFlow<Boolean> = showSearch.asStateFlow()
    override val searchText: StateFlow<String> = _searchText.asStateFlow()
    override val currentSearchIndexState: StateFlow<Int> = _currentSearchIndexState.asStateFlow()
    override val totalSearchResultsState: StateFlow<Int> = _totalSearchResultsState.asStateFlow()

    private val hasLinesSelection = writeopiaManager.onEditPositions
        .map { it.isNotEmpty() }

    override val hasSelectedLines: StateFlow<Boolean> =
        combine(
            hasLinesSelection,
            writeopiaManager.textSelectionState
        ) { hasLines, selection ->
            val hasTextSelection = selection.start != selection.end

            hasLines || hasTextSelection
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    override val isWorkspaceOffline: StateFlow<Boolean> =
        authRepository.listenForWorkspace()
            .map { workspace -> workspace.id == Workspace.disconnectedWorkspace().id }
            .stateIn(viewModelScope, SharingStarted.Lazily, false)

//    val selectionOfText = writeopiaManager.

    private val findsOfSearch: Flow<Set<String>> =
        combine(writeopiaManager.documentInfo, searchText) { info, query ->
            info.id to query
        }.map { (documentId, query) ->
            inDocumentSearchRepository.searchInDocument(query, documentId)
        }

    /**
     * This property defines if the document should be edited (you can write in it, for example)
     */
    override val isEditable: StateFlow<Boolean> = writeopiaManager
        .documentInfo
        .map { info -> !info.isLocked }
        .stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(), initialValue = false)

    private val _showGlobalMenu = MutableStateFlow(false)
    override val showGlobalMenu = _showGlobalMenu.asStateFlow()

    override val selectionMetadataState: StateFlow<Set<SelectionMetadata>> =
        writeopiaManager.selectionMetadataState.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            emptySet()
        )

    override val commentConversations: StateFlow<Map<String, List<Comment>>> =
        writeopiaManager.commentConversations

    @OptIn(ExperimentalCoroutinesApi::class)
    private val aiConfigState = authRepository.listenForUser()
        .flatMapConcat { user ->
            localAiRepository?.listenForConfiguration(user.id) ?: MutableStateFlow(null)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val currentModel: Flow<String> = aiConfigState.map { config ->
        config?.selectedModel ?: "No model selected"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val models: StateFlow<List<String>> = aiConfigState
        .filterNotNull()
        .flatMapLatest { config ->
            localAiRepository?.listenToModels(config.url)
                ?: MutableStateFlow(ResultData.Complete(ModelsResponse(emptyList())))
        }.map {
            when (it) {
                is ResultData.Complete -> it.data.models.map { model -> model.model }
                else -> emptyList()
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _editHeader = MutableStateFlow(false)
    override val editHeader = _editHeader.asStateFlow()

    override val currentTitle by lazy {
        writeopiaManager.currentDocument.filterNotNull().map { document ->
            document.title
        }.stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(), initialValue = "")
    }

    private val _shouldGoToNextScreen = MutableStateFlow(false)
    override val shouldGoToNextScreen = _shouldGoToNextScreen.asStateFlow()

    // The document's folder once moved from the editor: the loaded document still has the old one.
    @Volatile
    private var movedToFolderId: String? = null

    override val isEditState: StateFlow<EditState> by lazy {
        writeopiaManager.onEditPositions.map { set ->
            when {
                set.isNotEmpty() -> EditState.SELECTED_TEXT

                else -> EditState.TEXT
            }
        }.stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = EditState.TEXT
        )
    }

    private val story: StateFlow<StoryState> = writeopiaManager.currentStory
    override val scrollToPosition = writeopiaManager.scrollToPosition

    private val _loadingState = MutableStateFlow(false)
    override val loadingState: StateFlow<Boolean> = _loadingState.asStateFlow()

    private val documentId: StateFlow<String> by lazy {
        writeopiaManager.documentInfo
            .map { it.id }
            .filterNotNull()
            .stateIn(viewModelScope, SharingStarted.Lazily, "")
    }

    private val _sideMenuTabState = MutableStateFlow(SideMenuTab.NONE)
    override val sideMenuTabState: StateFlow<SideMenuTab> = _sideMenuTabState.asStateFlow()

    private val _showPublishDialog = MutableStateFlow(false)
    override val showPublishDialog: StateFlow<Boolean> = _showPublishDialog.asStateFlow()

    override val workspaceTags: StateFlow<List<DocumentTag>> =
        writeopiaManager.workspaceIdFlow
            .flatMapLatest { workspaceId ->
                documentTagRepository?.listenForTags(workspaceId) ?: flowOf(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    override val documentTags: StateFlow<List<DocumentTag>> =
        combine(writeopiaManager.documentTagIds, workspaceTags) { ids, tags ->
            tags.filter { tag -> tag.id in ids }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _showTagsDialog = MutableStateFlow(false)
    override val showTagsDialog: StateFlow<Boolean> = _showTagsDialog.asStateFlow()

    private val _showAddTagDialog = MutableStateFlow(false)
    override val showAddTagDialog: StateFlow<Boolean> = _showAddTagDialog.asStateFlow()

    private val _canSuggestTagWithAi = MutableStateFlow(false)
    override val canSuggestTagWithAi: StateFlow<Boolean> = _canSuggestTagWithAi.asStateFlow()

    private val _aiTagDraft = MutableStateFlow<AiTagDraft>(AiTagDraft.Idle)
    override val aiTagDraft: StateFlow<AiTagDraft> = _aiTagDraft.asStateFlow()

    override fun showTagsDialog() {
        _showTagsDialog.value = true
    }

    override fun hideTagsDialog() {
        _showTagsDialog.value = false
    }

    override fun showAddTagDialog() {
        _aiTagDraft.value = AiTagDraft.Idle
        _showAddTagDialog.value = true
        viewModelScope.launch(Dispatchers.Default) {
            _canSuggestTagWithAi.value = writingAgentApi != null && aiClients.isCloudAiReady()
        }
    }

    override fun hideAddTagDialog() {
        _showAddTagDialog.value = false
        _aiTagDraft.value = AiTagDraft.Idle
    }

    override fun addTagToDocument(tagId: String) {
        writeopiaManager.addDocumentTag(tagId)
        _showAddTagDialog.value = false
    }

    override fun removeTagFromDocument(tagId: String) {
        writeopiaManager.removeDocumentTag(tagId)
    }

    override fun createTag(name: String, description: String) {
        val repository = documentTagRepository ?: return
        if (name.isBlank()) return

        viewModelScope.launch(Dispatchers.Default) {
            val workspaceId = (authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()).id
            val tag = DocumentTag.create(name, description, workspaceId)

            repository.upsertTag(tag)
            writeopiaManager.addDocumentTag(tag.id)
            hideAddTagDialog()
            sendTagToBackend(tag)
        }
    }

    override fun suggestTagWithAi() {
        val api = writingAgentApi ?: return
        if (_aiTagDraft.value is AiTagDraft.Loading) return

        _aiTagDraft.value = AiTagDraft.Loading
        viewModelScope.launch(Dispatchers.Default) {
            val request = TagCreateRequest(
                documentTitle = writeopiaManager.documentInfo.value.title,
                documentText = writeopiaManager.getDocumentText().take(WritingAgentController.DOCUMENT_TEXT_LIMIT),
                existingTagNames = workspaceTags.value.map { it.name },
            )

            val draft = when (val result = api.createTag(request)) {
                is ResultData.Complete ->
                    if (result.data.error == null && result.data.name.isNotBlank()) {
                        AiTagDraft.Ready(result.data.name, result.data.description)
                    } else {
                        AiTagDraft.Failed
                    }

                else -> AiTagDraft.Failed
            }

            if (_aiTagDraft.value is AiTagDraft.Loading) _aiTagDraft.value = draft
        }
    }

    override fun clearAiTagDraft() {
        _aiTagDraft.value = AiTagDraft.Idle
    }

    private val _isDocumentPublished = MutableStateFlow(false)
    override val isDocumentPublished: StateFlow<Boolean> = _isDocumentPublished.asStateFlow()

    private val _publishLoading = MutableStateFlow(false)
    override val publishLoading: StateFlow<Boolean> = _publishLoading.asStateFlow()

    private val _showPremiumDialog = MutableStateFlow(false)
    override val showPremiumDialog: StateFlow<Boolean> = _showPremiumDialog.asStateFlow()

    /**
     * This property defines if the document is favorite
     */
    override val notFavorite: StateFlow<Boolean> = writeopiaManager
        .documentInfo
        .map { info -> !info.isFavorite }
        .stateIn(viewModelScope, started = SharingStarted.WhileSubscribed(), initialValue = false)

    /**
     * An icon saved elsewhere (the side menu, a sync) reaches the open editor. The first value
     * is the one the document was loaded with; only later changes are taken.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun listenForSavedIcon() {
        viewModelScope.launch(Dispatchers.Default) {
            documentId
                .filter { id -> id.isNotEmpty() }
                .flatMapLatest { id ->
                    documentRepository.listenForDocumentInfoById(id)
                        .filterNotNull()
                        .map { info -> info.icon }
                        .distinctUntilChanged()
                        .drop(1)
                }
                .collect(writeopiaManager::setDocumentIcon)
        }
    }

    override val documentIcon: StateFlow<MenuItem.Icon?> = writeopiaManager
        .documentInfo
        .map { info -> info.icon }
        .distinctUntilChanged()
        .stateIn(viewModelScope, started = SharingStarted.Eagerly, initialValue = null)

    // Here, after [documentId] and [documentIcon]: an init block runs in the order of the
    // class, and the listener starts on another thread right away.
    init {
        listenForSavedIcon()
    }

    private var aiJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override val toDrawWithDecoration: StateFlow<DrawState> by lazy {
        val toDraw = combine(
            writeopiaManager.toDraw,
            findsOfSearch,
            searchText,
            showSearch,
            _currentSearchIndexState
        ) { drawState, finds, query, showSearch, currentSearchIndex ->
            if (finds.isEmpty() && showSearch) return@combine drawState.copy(focus = null)
            if (finds.isEmpty()) return@combine drawState

            val mutableStories = drawState.stories.toMutableList()
            val activeFindPosition =
                if (finds.size > currentSearchIndex) finds.elementAt(currentSearchIndex) else null
            _totalSearchResultsState.value = finds.size

            if (activeFindPosition != null) {
                val index = mutableStories.indexOfFirst { it.position == activeFindPosition }
                if (index != -1) writeopiaManager.scrollToPosition(index)
            }

            finds.forEach { position ->
                val realPosition = mutableStories.indexOfFirst { it.position == position }
                if (realPosition == -1) return@forEach
                val toDraw = mutableStories[realPosition]
                val story = toDraw.storyStep

                val findSpans = FindInText.findInText(story.text ?: "", query)
                    .map { (start, end) ->
                        val span = if (position == activeFindPosition) {
                            Span.HIGHLIGHT_GREEN
                        } else {
                            Span.HIGHLIGHT_YELLOW
                        }
                        SpanInfo.create(start, end, span)
                    }

                mutableStories[realPosition] =
                    toDraw.copy(
                        storyStep = story.copy(
                            spans = story.spans + findSpans,
                            localId = GenerateId.generate()
                        )
                    )
            }

            drawState.copy(stories = mutableStories, focus = null)
        }

        // The icon of the editor's own state: a choice made here shows before it is saved.
        combine(toDraw, documentIcon) { drawState, icon ->
            val imageVector = icon
                ?.label
                ?.let(WrIcons::fromName)

            val tint = icon?.tint

            val newStories = drawState.stories
                .map { drawStory ->
                    if (drawStory.storyStep.type == StoryTypes.TITLE.type) {
                        val extraInfo = mutableMapOf<String, Any>()

                        if (imageVector != null) {
                            extraInfo["imageVector"] = imageVector
                        }

                        if (tint != null) {
                            extraInfo["imageVectorTint"] = tint
                        }

                        drawStory.copy(extraInfo = extraInfo)
                    } else {
                        drawStory
                    }
                }

            drawState.copy(stories = newStories)
        }.stateIn(viewModelScope, SharingStarted.Lazily, DrawState(emptyList()))
    }

    private val _documentToShareInfo = MutableStateFlow<ShareDocument?>(null)
    override val documentToShareInfo: StateFlow<ShareDocument?> = _documentToShareInfo.asStateFlow()

    override fun changeSideMenu(tab: SideMenuTab) {
        _sideMenuTabState.value = tab
    }

    override fun deleteSelection() {
        writeopiaManager.deleteSelection()
    }

    override fun setTheme(isDarkTheme: Boolean) {
        this.isDarkTheme = isDarkTheme
    }

    override fun handleBackAction(navigateBack: () -> Unit) {
        when {
            showGlobalMenu.value -> {
                _showGlobalMenu.value = false
            }

            editHeader.value -> {
                _editHeader.value = false
            }

            else -> {
                removeNoteIfEmpty(navigateBack)
            }
        }
    }

    override fun onHeaderClick() {
        if (!isEditable.value) return
        _editHeader.value = true
        loadHeaderImages()
    }

    private val headerImagesController = HeaderImagesController(
        scope = viewModelScope,
        resolveHeaderImages = resolveHeaderImages,
        workspaceId = { authRepository.getWorkspace()?.id },
        documentTitle = { writeopiaManager.documentInfo.value.title },
        documentText = { writeopiaManager.getDocumentText().take(WritingAgentController.DOCUMENT_TEXT_LIMIT) },
        onApproved = ::onHeaderImageSelection,
    )

    override val headerImages: StateFlow<HeaderImagesState> = headerImagesController.state

    /** The pictures of the workspace, fetched again each time the header menu opens. */
    private fun loadHeaderImages() {
        headerImagesController.load()
    }

    override fun generateHeaderImage() {
        if (!isEditable.value) return
        headerImagesController.generate()
    }

    override fun approveHeaderImage() {
        if (!isEditable.value) return
        headerImagesController.approve()
    }

    override fun discardHeaderImage() {
        headerImagesController.discard()
    }

    /** A picture in the header replaces its color: the header has one or the other. */
    override fun onHeaderImageSelection(url: String?) {
        if (!isEditable.value) return

        writeopiaManager.currentStory.value.stories[Rank.at(0)]?.let { storyStep ->
            val action = Action.StoryStateChange(
                storyStep = storyStep.copy(
                    url = url,
                    decoration = storyStep.decoration.copy(
                        backgroundColor = if (url != null) null else storyStep.decoration.backgroundColor
                    )
                ),
                position = Rank.at(0),
                preserveFocus = true
            )
            writeopiaManager.changeStoryState(action)
        }
    }

    override fun createNewDocument(documentId: String, title: String) {
        if (writeopiaManager.isInitialized()) {
            return
        }

        writeopiaManager.newDocument(documentId, title, parentFolder = parentFolderId)

        // Use global sync manager for syncing - continues even after ViewModel is cleared
        documentSyncManager.registerForDbSync(
            documentId = documentId,
            documentEditionFlow = writeopiaManager.documentEditionState,
            workspaceIdFlow = writeopiaManager.workspaceIdFlow,
            commentConversationsFlow = writeopiaManager.commentConversations,
            documentTagIdsFlow = writeopiaManager.documentTagIds,
            documentTracker = OnUpdateDocumentTracker(documentRepository)
        )

        // Also register for backend sync if the API is available and the workspace is connected
        viewModelScope.launch(Dispatchers.Default) {
            if (!isWorkspaceDisconnected()) {
                storyStepSyncApi?.let { syncApi ->
                    documentSyncManager.registerForBackendSync(
                        documentId = documentId,
                        documentEditionFlow = writeopiaManager.documentEditionState,
                        workspaceIdFlow = writeopiaManager.workspaceIdFlow,
                        syncApi = syncApi,
                        onServerUpdate = { steps, _ -> applyServerLinkCards(documentId, steps) }
                    )
                }
            }
        }

//        writeopiaManager.liveSync(sharedEditionManager)
    }

    /**
     * Opens the document: the copy in the database is shown right away, then the backend copy
     * is fetched and merged into it. [ContentLoadActivity] holds while both run, so the side
     * menu shows a spinner by its folders until the content is final.
     */
    override fun loadDocument(documentId: String) {
        if (writeopiaManager.isInitialized()) return

        viewModelScope.launch(Dispatchers.Default) {
            ContentLoadActivity.track { openDocument(documentId) }
        }
    }

    private suspend fun openDocument(documentId: String) {
        val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()
        val isDisconnected = workspace.id == Workspace.disconnectedWorkspace().id

        // Step 1: Load from local database immediately (fast path)
        val localDocument = documentRepository.loadDocumentById(documentId, workspace.id)

        if (localDocument != null) {
            writeopiaManager.loadDocument(localDocument)
            registerForSync(documentId, isDisconnected)
            // Set initial published state from local document
            _isDocumentPublished.value = localDocument.published
        }

        // Step 2: If online, fetch from backend in background and merge
        if (!isDisconnected && documentLoadUseCase != null) {
            documentLoadUseCase.fetchAndMergeFromBackend(
                documentId = documentId,
                workspaceId = workspace.id,
                onMergeComplete = { mergedDocument ->
                    // Update the document in the manager with merged content
                    writeopiaManager.updateDocument(mergedDocument)
                    // Update published state from merged document
                    _isDocumentPublished.value = mergedDocument.published

                    // Register for sync if this is the first load (backend-only document)
                    if (localDocument == null) {
                        registerForSync(documentId, isDisconnected)
                    }
                }
            )
        }

        // Step 3: Fetch document metadata from API if online. A failure here (airplane
        // mode, backend down) must not take the editor down: the local document is shown.
        if (!isDisconnected && documentsApi != null) {
            try {
                // Fetch favorite status from backend
                val docResult = documentsApi.getDocumentById(documentId, workspace.id)
                if (docResult is ResultData.Complete) {
                    writeopiaManager.setFavorite(docResult.data.favorite)
                }

                // Fetch published status
                val publishedResult = documentsApi.isDocumentPublished(documentId, workspace.id)
                if (publishedResult is ResultData.Complete) {
                    _isDocumentPublished.value = publishedResult.data
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("error loading document metadata: ${e.message}")
            }
        }
    }

    private fun registerForSync(documentId: String, isDisconnected: Boolean) {
        // Use global sync manager for syncing - continues even after ViewModel is cleared
        documentSyncManager.registerForDbSync(
            documentId = documentId,
            documentEditionFlow = writeopiaManager.documentEditionState,
            workspaceIdFlow = writeopiaManager.workspaceIdFlow,
            commentConversationsFlow = writeopiaManager.commentConversations,
            documentTagIdsFlow = writeopiaManager.documentTagIds,
            documentTracker = OnUpdateDocumentTracker(
                documentRepository,
                onStoryStepUpdate = { storyStep, position ->
                    inDocumentSearchRepository.insertForFts(storyStep, documentId, position)
                },
                onDocumentUpdate = { doc ->
                    doc.content
                        .forEach { (position, storyStep) ->
                            inDocumentSearchRepository.insertForFts(
                                storyStep,
                                documentId,
                                position
                            )
                        }
                }
            )
        )

        // Also register for backend sync if the API is available and the workspace is connected
        if (!isDisconnected) {
            storyStepSyncApi?.let { syncApi ->
                documentSyncManager.registerForBackendSync(
                    documentId = documentId,
                    documentEditionFlow = writeopiaManager.documentEditionState,
                    workspaceIdFlow = writeopiaManager.workspaceIdFlow,
                    syncApi = syncApi,
                    onServerUpdate = { steps, _ -> applyServerLinkCards(documentId, steps) }
                )
            }
        }
    }

    /**
     * Asks the backend to describe the page of a link card that was just pasted. The card is
     * already in the editor; this only fills in its description. The request runs as an AI
     * task, so the tasks panel shows it while it is pending and says when it fails.
     *
     * The backend saves the description in the card first; then the document is synced from
     * the time of the request on, which brings the described card to the editor and the local
     * database.
     */
    private suspend fun describeLinkCard(creation: LinkCardCreation) {
        val api = documentsApi ?: return
        val syncApi = storyStepSyncApi ?: return
        if (isWorkspaceDisconnected()) return

        val workspaceId = authRepository.getWorkspace()?.id ?: return
        val documentId = writeopiaManager.documentInfo.value.id
        val url = creation.storyStep.url ?: return

        aiTaskManager.enqueueTask(
            id = "editor-$documentId-link-${creation.storyStep.id}",
            type = AiTaskType.LINK_DESCRIPTION,
            description = "Describing $url..."
        ) {
            val requestTimestamp = Clock.System.now().toEpochMilliseconds()

            val described = api.describeLinkCard(
                documentId = documentId,
                workspaceId = workspaceId,
                request = LinkCardDescriptionRequest(
                    storyStep = creation.storyStep.toApi(creation.position),
                    position = creation.position,
                    requestTimestamp = requestTimestamp
                )
            )

            if (described !is ResultData.Complete) {
                return@enqueueTask Result.failure(Exception("The link could not be described"))
            }

            // The backend has the description now: a sync from just before the request returns it.
            val response = syncApi(
                StoryStepSyncRequest(
                    documentId = documentId,
                    workspaceId = workspaceId,
                    lastSyncTimestamp = requestTimestamp - 1,
                    requestTimestamp = Clock.System.now().toEpochMilliseconds(),
                    changes = emptyList(),
                    deletions = emptyList()
                )
            )

            val synced = response.updatedSteps.map { stepApi -> stepApi.position to stepApi.toModel() }
            applyServerLinkCards(documentId, synced)

            Result.success(Unit)
        }
    }

    /**
     * Puts the link cards the backend described into the open document and the local database.
     * Only cards that still have no description here are touched: anything else the user may
     * be editing is left alone.
     */
    private suspend fun applyServerLinkCards(documentId: String, steps: List<Pair<String, StoryStep>>) {
        val stories = writeopiaManager.currentStory.value.stories

        steps.forEach { (_, serverStep) ->
            if (serverStep.type.number != StoryTypes.LINK_CARD.type.number) return@forEach
            if (serverStep.text.isNullOrBlank()) return@forEach

            val local = stories.entries.firstOrNull { (_, step) -> step.id == serverStep.id }
                ?: return@forEach
            if (!local.value.text.isNullOrBlank()) return@forEach

            val described = local.value.copy(
                text = serverStep.text,
                url = serverStep.url ?: local.value.url,
                lastUpdatedAt = serverStep.lastUpdatedAt
            )

            if (writeopiaManager.applyServerStoryStep(described)) {
                documentRepository.saveStoryStep(described, local.key, documentId)
            }
        }
    }

    private suspend fun isWorkspaceDisconnected(): Boolean {
        val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()
        return workspace.id == Workspace.disconnectedWorkspace().id
    }

    override fun onHeaderColorSelection(color: Int?) {
        if (!isEditable.value) return

        writeopiaManager.currentStory.value.stories[Rank.at(0)]?.let { storyStep ->
            // A color, or none, takes the place of the picture of the header.
            val action = Action.StoryStateChange(
                storyStep = storyStep.copy(
                    url = null,
                    decoration = storyStep.decoration.copy(
                        backgroundColor = color
                    )
                ),
                position = Rank.at(0),
                preserveFocus = true
            )
            writeopiaManager.changeStoryState(action)
        }
    }

    override fun onAddCheckListClick() {
        if (!isEditable.value) return
        writeopiaManager.onCheckItemClicked()
    }

    override fun onAddListItemClick() {
        if (!isEditable.value) return
        writeopiaManager.addListItem()
    }

    override fun onAddCodeBlockClick() {
        if (!isEditable.value) return
        writeopiaManager.onCodeBlockClicked()
    }

    override fun toggleHighLightBlock() {
        if (!isEditable.value) return
        writeopiaManager.toggleHighLightBlock()
    }

    override fun toggleCardBlock() {
        if (!isEditable.value) return
        writeopiaManager.toggleCardBlock()
    }

    override fun onHeaderEditionCancel() {
        _editHeader.value = false
    }

    override fun onMoreOptionsClick() {
        _showGlobalMenu.value = !_showGlobalMenu.value
    }

    override fun shareDocumentInJson() {
        shareDocument(::documentToJson, "application/json")
    }

    override fun shareDocumentInMarkdown() {
        shareDocument(::documentToMd, "plain/text")
    }

    override fun onViewModelCleared() {
        // Cancel any running AI tasks for this document
        val docId = documentId.value
        if (docId.isNotEmpty()) {
            aiTaskManager.cancelTasksByPrefix("editor-$docId")
        }
        aiJob?.cancel()
        writingAgent?.stop()
        writeopiaManager.onClear()
    }

    override fun onCleared() {
        onViewModelCleared()
        super.onCleared()
    }

    override fun clearSelections() {
        writeopiaManager.clearSelection()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override val fontFamily: StateFlow<Font> by lazy {
        authRepository.listenForUser().flatMapLatest { user ->
            uiConfigurationRepository.listenForUiConfiguration(user.id, viewModelScope)
        }.filterNotNull()
            .map { it.font }
            .stateIn(viewModelScope, SharingStarted.Lazily, Font.SYSTEM)
    }

    override fun onAddSpanClick(span: Span) {
        viewModelScope.launch(Dispatchers.Default) {
            writeopiaManager.toggleSpan(span)
        }
    }

    override fun createComment(text: String): CommentConversation? =
        writeopiaManager.createComment(text)

    override fun createComment(text: String, target: Selection): CommentConversation? =
        writeopiaManager.createComment(text, target)

    override fun addComment(conversationId: String, text: String): Comment? =
        writeopiaManager.addComment(conversationId, text)

    override fun getCommentConversationAtCursor(): CommentConversation? =
        writeopiaManager.getCommentConversationAtCursor()

    override fun getCommentConversationAtSelection(): CommentConversation? =
        writeopiaManager.getCommentConversationAtSelection()

    override fun deleteComment(conversationId: String, commentId: String): Boolean =
        writeopiaManager.deleteComment(conversationId, commentId)

    override fun deleteCommentConversation(conversationId: String): Boolean =
        writeopiaManager.deleteCommentConversation(conversationId)

    override fun toggleEditable() {
        writeopiaManager.toggleLockDocument()
    }

    // Saved with the document's metadata, like the lock: the title draws it once it is saved.
    override fun changeDocumentIcon(label: String, tint: Int) {
        writeopiaManager.changeDocumentIcon(MenuItem.Icon(label, tint))
    }

    override fun removeDocumentIcon() {
        writeopiaManager.changeDocumentIcon(null)
    }

    /** The favorite request in flight, so the next one goes after it with the latest state. */
    private var favoriteSyncJob: Job? = null

    override fun toggleFavorite() {
        writeopiaManager.toggleFavoriteDocument()

        val info = writeopiaManager.documentInfo.value
        val previous = favoriteSyncJob
        favoriteSyncJob = viewModelScope.launch(Dispatchers.Default) {
            // The record first, whatever happens to the screen: the sync takes it from there.
            withContext(NonCancellable) { recordFavoriteChange(info.id, info.isFavorite) }
            // One request at a time, or two quick toggles could reach the backend in the wrong
            // order. The record is the user's last word either way.
            previous?.join()
            sendPendingFavorites()
        }
    }

    /**
     * The backend keeps the favorite documents per user, behind their own endpoint: the
     * favorite saved with the document's metadata doesn't reach it. The change is recorded for
     * the sync ([PendingFavorite]) and sent right away; what can't be sent stays recorded, and
     * the next sync sends it instead of taking the backend's favorite for the document.
     */
    private suspend fun recordFavoriteChange(documentId: String, favorite: Boolean) {
        val workspace = authRepository.getWorkspace() ?: return
        if (workspace.id == Workspace.disconnectedWorkspace().id) return

        documentRepository.savePendingFavorites(
            listOf(PendingFavorite(documentId, workspace.id, favorite, Clock.System.now().toEpochMilliseconds()))
        )
    }

    private suspend fun sendPendingFavorites() {
        val syncService = documentSyncService ?: return
        if (!authRepository.isLoggedIn()) return
        if (authRepository.getUser().tier != Tier.PREMIUM) return

        val workspace = authRepository.getWorkspace() ?: return
        syncService.sendPendingFavorites(workspace.id)
    }

    override fun changeFontFamily(font: Font) {
        viewModelScope.launch {
            uiConfigurationRepository.updateConfiguration(authRepository.getUser().id) { config ->
                config.copy(font = font)
            }
        }
    }

    override fun addImage(imagePath: String, position: String?) {
        viewModelScope.launch(Dispatchers.Default) {
            val path = workspaceConfigRepository
                .loadWorkspacePath(authRepository.getUser().id)
                ?.let { workspace ->
                    SaveImage.saveLocally(
                        imagePath,
                        "$workspace/images"
                    )
                } ?: imagePath

            writeopiaManager.addImage(path, position)
        }
    }

    override fun exportMarkdown(path: String) {
        viewModelScope.launch(Dispatchers.Default) {
            writeDocument(path, documentToMarkdown)
        }
    }

    override fun exportJson(path: String) {
        viewModelScope.launch(Dispatchers.Default) {
            writeDocument(path, documentToJson)
        }
    }

    override suspend fun workspaceFolders(): List<Folder> {
        val workspace = authRepository.getWorkspace() ?: Workspace.disconnectedWorkspace()
        return folderRepository.getFoldersForWorkspace(workspace.id)
    }

    override fun documentFolderId(): String = movedToFolderId ?: writeopiaManager.getDocument().parentId

    override fun moveToFolder(folderId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            documentRepository.moveToFolder(documentId = documentId.value, parentId = folderId)
            // Only once saved: a failed move leaves the folder the document is still in.
            movedToFolderId = folderId
        }
    }

    override fun askAiWithMode(targetMode: AiTargetMode) {
        documentAi(targetMode, AiCommand.PROMPT)
    }

    override fun aiSummary(targetMode: AiTargetMode) {
        documentAi(targetMode, AiCommand.SUMMARY)
    }

    override fun aiActionPoints(targetMode: AiTargetMode) {
        documentAi(targetMode, AiCommand.ACTION_POINTS)
    }

    override fun aiFaq(targetMode: AiTargetMode) {
        documentAi(targetMode, AiCommand.FAQ)
    }

    override fun aiTags(targetMode: AiTargetMode) {
        documentAi(targetMode, AiCommand.TAGS)
    }

    override fun aiSection(position: String) {
        val sectionText = writeopiaManager.getStory(position)?.text ?: return

        val docId = documentId.value
        val taskId = "editor-$docId-${GenerateId.generate()}"
        val answerPosition = writeopiaManager.positionAfter(position)

        aiTaskManager.enqueueTask(
            id = taskId,
            type = AiTaskType.TEXT_GENERATION,
            description = "Generating section..."
        ) {
            val ai = aiClients.resolve(authRepository.getUser().id)

            if (ai == null) {
                PromptService.noAiAvailableAt(answerPosition, writeopiaManager)
                return@enqueueTask Result.failure(Exception(PromptService.NO_AI_MESSAGE))
            }

            val prompt =
                """
                Create a document section for a document.
                The document is:
                ```
                ${writeopiaManager.getDocumentText()}
                ```

                Use the language of the text. Do not add titles. Create contect for this section: $sectionText
                """

            PromptService.prompt(
                prompt = prompt,
                writeopiaManager = writeopiaManager,
                streamFn = { text -> ai.stream(AiCommand.PROMPT, text) },
                promptPosition = answerPosition
            )
            Result.success(Unit)
        }
    }

    override fun aiCommand(command: String) {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return

        val docId = documentId.value
        val taskId = "editor-$docId-${GenerateId.generate()}"

        aiTaskManager.enqueueTask(
            id = taskId,
            type = AiTaskType.TEXT_GENERATION,
            description = "Running command..."
        ) {
            val ai = aiClients.resolve(authRepository.getUser().id)

            if (ai == null) {
                PromptService.noAiAvailable(AiCommandService.answerTarget(writeopiaManager), writeopiaManager)
                return@enqueueTask Result.failure(Exception(PromptService.NO_AI_MESSAGE))
            }

            AiCommandService.run(trimmed, writeopiaManager, ai)
        }
    }

    /**
     * Runs [command] over the text [targetMode] points to, with the AI the user picked. The answer
     * streams into the document right after that text.
     */
    private fun documentAi(targetMode: AiTargetMode, command: AiCommand) {
        val docId = documentId.value
        val taskId = "editor-$docId-${GenerateId.generate()}"

        aiTaskManager.enqueueTask(
            id = taskId,
            type = AiTaskType.TEXT_GENERATION,
            description = "Generating text..."
        ) {
            val ai = aiClients.resolve(authRepository.getUser().id)

            if (ai == null) {
                PromptService.noAiAvailable(targetMode, writeopiaManager)
                return@enqueueTask Result.failure(Exception(PromptService.NO_AI_MESSAGE))
            }

            PromptService.documentPrompt(
                targetMode = targetMode,
                writeopiaManager = writeopiaManager,
                streamFn = { text -> ai.stream(command, text) }
            )
            Result.success(Unit)
        }
    }

    override fun addPage() {
        viewModelScope.launch(Dispatchers.Default) {
            writeopiaManager.addLinkToDocument()
        }
    }

    override fun copySelection() {
        if (!writeopiaManager.isOnSelection) return

        val lineBreak = buildAnnotatedString { append("\n") }

        val annotatedString = writeopiaManager.getSelectedStories()
            .filter { storyStep -> storyStep.text != null }
            .map { storyStep ->
                val text = storyStep.text ?: ""

                Spans.createStringWithSpans(text, storyStep.spans, isDarkTheme)
            }.reduce { acc, annotatedString ->
                acc + lineBreak + annotatedString
            }

        copyManager.copy(annotatedString)
    }

    override fun cutSelection() {
        copySelection()
        deleteSelection()
    }

    override fun deleteDocument(onDeleted: () -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            val document = writeopiaManager.getDocument()
            // The editor stays open until the document is gone: leaving it clears this
            // ViewModel, which would cancel the deletion.
            try {
                documentRepository.deleteDocument(document, document.workspaceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("error deleting document: ${e.message}")
                return@launch
            }

            withContext(Dispatchers.Main) {
                onDeleted()
            }
        }
    }

    override fun receiveExternalFile(files: List<ExternalFile>, position: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val newFiles = workspaceConfigRepository
                .loadWorkspacePath(authRepository.getUser().id)
                ?.let { workspace ->
                    files.map { file ->
                        if (writeopiaManager.supportedImageFiles.contains(file.extension)) {
                            val newPath = SaveImage.saveLocally(file.fullPath, "$workspace/images")

                            file.copy(fullPath = newPath)
                        } else {
                            file
                        }
                    }
                } ?: files

            writeopiaManager.receiveExternalFiles(newFiles, position)
        }
    }

    override fun selectModel(model: String) {
        if (localAiRepository != null) {
            viewModelScope.launch {
                val userId = authRepository.getUser().id

                localAiRepository.saveLocalAiSelectedModel(userId, model)
                localAiRepository.refreshConfiguration(userId)
            }
        }
    }

    override fun showSearch() {
        showSearch.value = true
    }

    override fun hideSearch() {
        viewModelScope.launch {
            showSearch.value = false
            delay(100)
            _searchText.value = ""
            _currentSearchIndexState.value = 0
        }
    }

    override fun searchInDocument(query: String) {
        viewModelScope.launch {
            _searchText.value = query
            val finds = findsOfSearch.first().toList().sorted()
            if (finds.isEmpty()) {
                _currentSearchIndexState.value = 0
                _totalSearchResultsState.value = 0
                return@launch
            }

            val currentPosition = writeopiaManager.currentStory.value.focus
                ?: writeopiaManager.currentStory.value.selection.position
            val newIndex = finds.indexOfFirst { it >= currentPosition }

            _currentSearchIndexState.value = if (newIndex != -1) newIndex else 0
        }
    }

    override fun previousSearchResult() {
        viewModelScope.launch {
            val total = findsOfSearch.first().size
            if (total == 0) return@launch

            val currentIndex = _currentSearchIndexState.value
            _currentSearchIndexState.value = (currentIndex - 1 + total) % total
        }
    }

    override fun nextSearchResult() {
        viewModelScope.launch {
            val total = findsOfSearch.first().size
            if (total == 0) return@launch

            val currentIndex = _currentSearchIndexState.value
            _currentSearchIndexState.value = (currentIndex + 1) % total
        }
    }

    override fun titleClick(tag: Tag) {
        viewModelScope.launch(Dispatchers.Default) {
            writeopiaManager.addTitle(tag)
        }
    }

    private fun saveDocumentInWorkSpace() {
        viewModelScope.launch(Dispatchers.Default) {
            _loadingState.value = true

            val document = writeopiaManager.getDocument()
            val path = workspaceConfigRepository.loadWorkspacePath(authRepository.getUser().id)

            // Todo: When path is null, the user should be asked to configure it.
            if (path != null) {
                documentToJson.writeDocument(
                    document = document,
                    path = path,
                    writeConfigFile = true,
                )
            }

            // Some delay so users can see a loading state
            delay(150)
            _loadingState.value = false
        }
    }

    private fun writeDocument(path: String, writer: DocumentWriter) {
        writer.writeDocuments(
            documents = listOf(writeopiaManager.getDocument()),
            path = path,
            writeConfigFile = false,
            usePath = false
        )
    }

    private fun documentToJson(document: Document, json: Json = writeopiaJson): String {
        val request = document.toApi().wrapInRequest()
        return json.encodeToString(request)
    }

    private fun documentToMd(document: Document): String =
        DocumentToMarkdown.parse(document.content)

    private fun shareDocument(infoParse: (Document) -> String, type: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val document: Document = writeopiaManager.currentDocument
                .stateIn(this)
                .value ?: return@launch

            val documentTitle = document.title.replace(" ", "_")

            val newContent = document.copy(content = document.content)
            val stringDocument = infoParse(newContent)

            _documentToShareInfo.emit(ShareDocument(stringDocument, documentTitle, type))
        }
    }

    private fun removeNoteIfEmpty(onComplete: () -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            val document = writeopiaManager.currentDocument.stateIn(this).value

            if (document != null && story.value.stories.noContent()) {
                documentRepository.deleteDocument(document, document.workspaceId)
            }

            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }

    override fun showPublishDialog() {
        viewModelScope.launch(Dispatchers.Default) {
            // Check if user is on free tier
            val user = authRepository.getUser()
            if (user.tier != Tier.PREMIUM) {
                _showPremiumDialog.value = true
                return@launch
            }

            _showPublishDialog.value = true
            // Fetch current publish status from server
            val docId = documentId.value
            if (docId.isNotEmpty() && documentsApi != null) {
                val workspaceId = authRepository.getWorkspace()?.id ?: return@launch
                if (workspaceId == Workspace.disconnectedWorkspace().id) return@launch
                val result = documentsApi.isDocumentPublished(docId, workspaceId)
                if (result is ResultData.Complete) {
                    _isDocumentPublished.value = result.data
                }
            }
        }
    }

    override fun hidePublishDialog() {
        _showPublishDialog.value = false
    }

    override fun hidePremiumDialog() {
        _showPremiumDialog.value = false
    }

    override fun publishDocument() {
        viewModelScope.launch(Dispatchers.Default) {
            _publishLoading.value = true
            try {
                val docId = documentId.value
                if (docId.isNotEmpty() && documentsApi != null) {
                    val workspaceId = authRepository.getWorkspace()?.id ?: return@launch
                    if (workspaceId == Workspace.disconnectedWorkspace().id) return@launch
                    val result = documentsApi.publishDocument(docId, workspaceId)
                    if (result is ResultData.Complete) {
                        _isDocumentPublished.value = true
                    }
                }
            } finally {
                _publishLoading.value = false
            }
        }
    }

    override fun unpublishDocument() {
        viewModelScope.launch(Dispatchers.Default) {
            _publishLoading.value = true
            try {
                val docId = documentId.value
                if (docId.isNotEmpty() && documentsApi != null) {
                    val workspaceId = authRepository.getWorkspace()?.id ?: return@launch
                    if (workspaceId == Workspace.disconnectedWorkspace().id) return@launch
                    val result = documentsApi.unpublishDocument(docId, workspaceId)
                    if (result is ResultData.Complete) {
                        _isDocumentPublished.value = false
                    }
                }
            } finally {
                _publishLoading.value = false
            }
        }
    }

    override fun copyPublishLink() {
        val docId = documentId.value
        if (docId.isNotEmpty()) {
            val url = "https://app.writeopia.io/site/$docId"
            copyManager.copyText(url)
        }
    }

    override fun onAddSpreadsheetClick(columnCount: Int) {
        if (!isEditable.value) return
        writeopiaManager.addSpreadsheet(columnCount)
    }
}

/** The cloud source; what it sends is also kept in [store], when the device has one. */
private fun cloudSource(
    api: PresentationsApi,
    store: PresentationsStore?
): (String) -> PresentationsSource.Cloud {
    val source = if (store != null) {
        val saver = CloudPresentationsSaver(api, store)
        PresentationsSource.Cloud(saver, saver)
    } else {
        PresentationsSource.Cloud(api, api)
    }
    return { _ -> source }
}

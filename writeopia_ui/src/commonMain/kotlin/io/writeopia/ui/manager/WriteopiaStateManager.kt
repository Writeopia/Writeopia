@file:OptIn(ExperimentalTime::class)

package io.writeopia.ui.manager

import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.sdk.manager.DocumentTracker
import io.writeopia.sdk.manager.InTextMarkdownHandler
import io.writeopia.sdk.manager.StoryStepSyncTracker
import io.writeopia.sdk.manager.UnsupportedCommentConversationsException
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.manager.fixMove
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.model.document.DocumentInfo
import io.writeopia.sdk.model.document.document
import io.writeopia.sdk.model.document.info
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.model.story.Selection
import io.writeopia.sdk.model.story.StoryState
import io.writeopia.sdk.models.command.CommandInfo
import io.writeopia.sdk.models.command.CommandTrigger
import io.writeopia.sdk.models.command.TypeInfo
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.comment.CommentConversation
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.files.ExternalFile
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.normalization.builder.StepsMapNormalizationBuilder
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.repository.UserRepository
import io.writeopia.sdk.sharededition.SharedEditionManager
import io.writeopia.sdk.utils.alias.UnitsNormalizationMap
import io.writeopia.sdk.utils.collections.lastKeyOrNull
import io.writeopia.sdk.utils.collections.toSortedMutableMap
import io.writeopia.sdk.utils.NextPositionCalculator
import io.writeopia.sdk.utils.extensions.toEditState
import io.writeopia.sdk.utils.iterables.mapValuesSorted
import io.writeopia.sdk.utils.iterables.nextKey
import io.writeopia.sdk.utils.iterables.previousKey
import io.writeopia.ui.backstack.BackstackHandler
import io.writeopia.ui.backstack.BackstackInform
import io.writeopia.ui.backstack.SnapshotBackstackManager
import io.writeopia.ui.edition.TextCommandHandler
import io.writeopia.ui.extensions.toSelectionMetadata
import io.writeopia.ui.keyboard.KeyboardEvent
import io.writeopia.ui.model.DrawState
import io.writeopia.ui.model.DrawStory
import io.writeopia.ui.model.LinkCardCreation
import io.writeopia.ui.image.ImageUploader
import io.writeopia.ui.model.SelectionInfo
import io.writeopia.ui.model.SelectionMetadata
import io.writeopia.ui.model.TextInput
import io.writeopia.ui.utils.LinkDetector
import io.writeopia.ui.modifiers.StepsModifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.time.ExperimentalTime

/**
 * This is the entry class of the framework. It follows the Controller pattern, redirecting all the
 * call to another class responsible for the part of the SDK requested.
 */
class WriteopiaStateManager(
    private val stepsNormalizer: UnitsNormalizationMap =
        StepsMapNormalizationBuilder.reduceNormalizations {
            defaultNormalizers()
        },
    private val dispatcher: CoroutineDispatcher,
    private val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext),
    private val backStackManager: SnapshotBackstackManager,
    private val userRepository: UserRepository?,
    private val writeopiaManager: WriteopiaManager,
    val selectionState: StateFlow<Boolean>,
    private val keyboardEventFlow: Flow<KeyboardEvent>,
    private val documentRepository: DocumentRepository? = null,
    val supportedImageFiles: Set<String> = setOf("jpg", "jpeg", "png"),
    private val drawStateModify: (List<DrawStory>, String) -> (List<DrawStory>) = StepsModifier::modify,
    private val permanentTypes: Set<Int> = setOf(StoryTypes.TITLE.type.number),
    private val listTypes: Set<Int> = setOf(
        StoryTypes.CHECK_ITEM.type.number,
        StoryTypes.UNORDERED_LIST_ITEM.type.number,
    ),
    private val inTextMarkdownHandler: InTextMarkdownHandler? = InTextMarkdownHandler,
    private val imageUploader: ImageUploader? = null,
    private val textSelectionActiveState: MutableStateFlow<Boolean> = MutableStateFlow(false),
    private val bulkStyleParseHandler: BulkStyleParseHandler = BulkStyleParseHandler()
) : BackstackHandler, BackstackInform by backStackManager {

    private val selectionBuffer: EventBuffer<Pair<Boolean, String>> = EventBuffer(coroutineScope)

    init {
        coroutineScope.launch {
            selectionBuffer.events.collect { (selected, position) ->
                selected(selected, position)
            }
        }

        coroutineScope.launch {
            keyboardEventFlow
                .onEach { delay(60) }
                .collect { event ->
                    if (isEditable) {
                        when (event) {
                            KeyboardEvent.DELETE -> {
                                if (_onEditPositions.value.isNotEmpty()) {
                                    deleteSelection()
                                }
                            }

                            KeyboardEvent.SELECT_ALL -> {
                                selectAll()
                            }

                            KeyboardEvent.BOLD -> {
                                toggleSpan(Span.BOLD)
                            }

                            KeyboardEvent.ITALIC -> {
                                toggleSpan(Span.ITALIC)
                            }

                            KeyboardEvent.UNDERLINE -> {
                                toggleSpan(Span.UNDERLINE)
                            }

                            KeyboardEvent.LINK -> {
                                addLinkToDocument()
                            }

                            KeyboardEvent.BOX -> {
                                toggleHighLightBlock()
                            }

                            KeyboardEvent.CANCEL -> {
                                cancelSuggestions()
                            }

                            KeyboardEvent.ACCEPT_AI -> {
                                acceptSuggestions()
                            }

                            KeyboardEvent.EQUATION -> {
                                // Add method that changes based on type and changes either the current
                                // position or the selected ones
                                getCurrentStory()?.let { story ->
                                    val position = currentPosition() ?: return@let

                                    changeStoryState(
                                        Action.StoryStateChange(
                                            storyStep = story.copy(
                                                type = StoryTypes.EQUATION.type
                                            ),
                                            position
                                        )
                                    )
                                }
                            }

                            KeyboardEvent.SHIFT_ARROW_UP -> {
                                extendSelectionUp()
                            }

                            KeyboardEvent.SHIFT_ARROW_DOWN -> {
                                extendSelectionDown()
                            }

                            else -> {}
                        }
                    }
                }
        }
    }

    private var lastLineBreak: LineBreakCommand? = null

    private val commandHandler = TextCommandHandler.defaultCommands(this)

    private var lastStateChange: Action.StoryStateChange? = null

    private val initialContent: Map<String, StoryStep> =
        mapOf(Rank.at(0) to StoryStep(text = "", type = StoryTypes.TITLE.type))

    private var localUserId: String? = null

    private val dragPosition = MutableStateFlow(Rank.NONE)
    private val isDragging = MutableStateFlow(false)

    private val dragRealPosition = combine(
        dragPosition,
        isDragging
    ) { position, isDragging ->
        if (isDragging) position else Rank.NONE
    }

    private val _scrollToPosition: MutableStateFlow<Int?> = MutableStateFlow(null)
    val scrollToPosition: StateFlow<Int?> = _scrollToPosition.asStateFlow()

    /**
     * The current story in focus
     */
    private val _currentStory: MutableStateFlow<StoryState> = MutableStateFlow(
        StoryState(stories = initialContent, lastEdit = LastEdit.Nothing)
    )

    private val _documentInfo: MutableStateFlow<DocumentInfo> =
        MutableStateFlow(DocumentInfo.empty())

    private val _commentConversations =
        MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
    private val commentConversationArchive =
        MutableStateFlow<Map<String, List<Comment>>>(emptyMap())
    val commentConversations: StateFlow<Map<String, List<Comment>>> =
        _commentConversations.asStateFlow()

    /** The ids of the tags of the open document, from the workspace catalogue. */
    private val _documentTagIds = MutableStateFlow<Set<String>>(emptySet())
    val documentTagIds: StateFlow<Set<String>> = _documentTagIds.asStateFlow()

    private val isEditable: Boolean
        get() = !_documentInfo.value.isLocked

    val documentInfo: StateFlow<DocumentInfo> = _documentInfo.asStateFlow()

    private val _onEditPositions = MutableStateFlow(setOf<String>())
    val onEditPositions = _onEditPositions.asStateFlow()

    private val _linkCardCreated = MutableSharedFlow<LinkCardCreation>(extraBufferCapacity = 16)

    /**
     * The link cards created by pasting an address in an empty line. The app listens to this to
     * ask the backend for a description of each page.
     */
    val linkCardCreated: SharedFlow<LinkCardCreation> = _linkCardCreated.asSharedFlow()

    /**
     * Tracks the anchor position for keyboard-based multi-line selection.
     * This is the position from which the selection extends when using shift+arrows.
     * It's null when the selection was made with mouse (unknown keyboard position).
     */
    private var keyboardSelectionAnchor: String? = null

    private var sharedEditionManager: SharedEditionManager? = null

    val currentStory: StateFlow<StoryState> = _currentStory.asStateFlow()

    val textSelectionState: Flow<Selection> =
        _currentStory.map { storyState ->
            storyState.selection
        }

    val currentDocument: StateFlow<Document?> =
        combine(
            _documentInfo,
            _currentStory,
            _commentConversations,
            _documentTagIds,
        ) { info, state, conversations, tagIds ->
            parseDocument(info, state, conversations, tagIds)
        }.stateIn(coroutineScope, SharingStarted.Lazily, null)

    /**
     * Flow that emits the current document edition state, combining story state and document info.
     * This can be used by external sync managers to track document changes.
     */
    val documentEditionState: Flow<Pair<StoryState, DocumentInfo>> =
        combine(currentStory, _documentInfo, ::Pair)

    /**
     * Flow that emits the current workspace ID.
     * This can be used by external sync managers along with [documentEditionState].
     */
    val workspaceIdFlow: Flow<String> =
        userRepository?.listenForWorkspace()?.map { workspace ->
            workspace.id
        } ?: MutableStateFlow(Workspace.disconnectedWorkspace().id)

    val toDraw: Flow<DrawState> =
        combine(
            _onEditPositions,
            currentStory,
            dragRealPosition
        ) { positions, storyState, dragPosition ->
            val focus = storyState.focus

            val toDrawStories = storyState.stories
                .mapValues { (position, storyStep) ->
                    DrawStory(
                        storyStep = storyStep,
                        cursor = storyState.selection.takeIf { it.position == position },
                        isSelected = positions.contains(position),
                        position = position
                    )
                }
                .values
                .toList() // Already sorted by position - map maintains sorted key order
                .let { drawStories -> drawStateModify(drawStories, dragPosition).drop(1) }

            DrawState(toDrawStories, focus)
        }

    private var initialized = false

    val selectionMetadataState: Flow<Set<SelectionMetadata>> =
        combine(_currentStory, _onEditPositions) { storyState, onEditPositions ->
            val result = mutableSetOf<SelectionMetadata>()

            val findMetadata = { storyStep: StoryStep ->
                val (selectStart, selectEnd) = (storyState.selection.start to storyState.selection.end)
                val fromType = SelectionMetadata.fromStoryType(storyStep.type.number)

                if (fromType != null) {
                    result.add(fromType)
                }

                storyStep.tags.forEach { tagInfo ->
                    when (tagInfo.tag) {
                        Tag.HIGH_LIGHT_BLOCK -> {
                            result.add(SelectionMetadata.BOX)
                        }

                        Tag.CARD_BLOCK -> {
                            result.add(SelectionMetadata.CARD)
                        }

                        Tag.H1 -> {
                            result.add(SelectionMetadata.TITLE)
                        }

                        Tag.H2 -> {
                            result.add(SelectionMetadata.SUBTITLE)
                        }

                        Tag.H3 -> {
                            result.add(SelectionMetadata.HEADING)
                        }

                        else -> {}
                    }
                }

                storyStep.spans.filter { span ->
                    span.isInside(selectStart) || span.isInside(selectEnd)
                }.forEach { span ->
                    span.span.toSelectionMetadata()?.let(result::add)
                }
            }

            if (!onEditPositions.isNotEmpty()) {
                val selectionPos = storyState.selection.position
                val step = storyState.stories[selectionPos]

                if (step != null) {
                    findMetadata(step)
                }
            } else {
                onEditPositions.mapNotNull(::getStory).forEach(findMetadata)
            }

            result
        }

    val isOnSelection: Boolean
        get() = _onEditPositions.value.isNotEmpty()

    /**
     * Saves the document automatically as it is changed. It uses the [DocumentTracker] passed
     * in the constructor of [WriteopiaStateManager]
     */
    fun saveOnStoryChanges(documentTracker: DocumentTracker) {
        coroutineScope.launch(dispatcher) {
            try {
                documentTracker.saveOnStoryChanges(
                    documentEditionState,
                    userRepository?.listenForWorkspace()?.map { workspace ->
                        workspace.id
                    } ?: MutableStateFlow(Workspace.disconnectedWorkspace().id),
                    commentConversations,
                    documentTagIds,
                )
            } catch (error: UnsupportedCommentConversationsException) {
                println(
                    "Document sync stopped for ${_documentInfo.value.id}: ${error.message}"
                )
            }
        }
    }

    /**
     * Syncs StorySteps with the backend using the provided [StoryStepSyncTracker].
     * This method starts a coroutine that listens to document changes and syncs
     * them with the backend using a buffered approach.
     *
     * @param syncTracker The tracker responsible for syncing StorySteps with the backend.
     */
    fun syncStoryStepsWithBackend(syncTracker: StoryStepSyncTracker) {
        coroutineScope.launch(dispatcher) {
            syncTracker.syncStorySteps(
                documentEditionFlow = documentEditionState,
                workspaceIdFlow = workspaceIdFlow
            )
        }
    }

    fun getDocument(): Document =
        parseDocument(
            _documentInfo.value,
            _currentStory.value,
            _commentConversations.value,
            _documentTagIds.value,
        )

    /** Adds a tag of the workspace catalogue to the document. The tracker saves the change. */
    fun addDocumentTag(tagId: String) {
        if (!isEditable || tagId.isBlank()) return
        _documentTagIds.update { ids -> ids + tagId }
    }

    fun removeDocumentTag(tagId: String) {
        if (!isEditable) return
        _documentTagIds.update { ids -> ids - tagId }
    }

    fun setDocumentTagIds(tagIds: Set<String>) {
        if (!isEditable) return
        _documentTagIds.value = tagIds
    }

    fun liveSync(sharedEditionManager: SharedEditionManager) {
        coroutineScope.launch(dispatcher) {
            sharedEditionManager.startLiveEdition(
                inFlow = documentEditionState,
                outFlow = _currentStory,
            )
        }
    }

    fun isInitialized(): Boolean = initialized

    /**
     * Creates a new story. Use this when you wouldn't like to load a documented previously saved.
     *
     * @param documentId the id of the document that will be created
     * @param title the title of the document
     */
    fun newDocument(
        documentId: String = GenerateId.generate(),
        title: String = "",
        parentFolder: String = "root",
        forceRestart: Boolean = false
    ) {
        if (isInitialized() && !forceRestart) return

        initialized = true
        val (documentInfo, storyState) = writeopiaManager.newDocument(
            documentId,
            title,
            parentFolder = parentFolder
        )

        val withNextPositions = storyState.copy(
            stories = NextPositionCalculator.calculate(storyState.stories)
        )

        backStackManager.addState(withNextPositions)

        _documentInfo.value = documentInfo
        _currentStory.value = withNextPositions
        _commentConversations.value = emptyMap()
        commentConversationArchive.value = emptyMap()
        _documentTagIds.value = emptySet()
    }

    /**
     * Initializes a document passed as a parameter. This method should be used when you would like
     * to load a document from a database and start editing it, instead of creating something new.
     *
     * @param document [Document]
     */
    fun loadDocument(document: Document) {
        if (isInitialized()) return

        initialized = true

        val stories = document.content
        val state =
            StoryState(stepsNormalizer(stories.toEditState()), LastEdit.Nothing, null)

        _currentStory.value = state
        backStackManager.addState(state)
        val normalized = stepsNormalizer(stories.toEditState())
        val withNextPositions = NextPositionCalculator.calculate(normalized)

        _currentStory.value = StoryState(withNextPositions, LastEdit.Nothing)
        _documentInfo.value = document.info()
        _commentConversations.value = document.commentConversations
        replaceCommentConversationArchive(document.commentConversations)
        _documentTagIds.value = document.tagIds
    }

    /**
     * Updates a document that was already loaded. This should be used when the document
     * content has been updated (e.g., from a backend sync) and needs to be refreshed in the UI.
     *
     * @param document [Document] the updated document
     */
    fun updateDocument(document: Document) {
        val stories = document.content
        val normalized = stepsNormalizer(stories.toEditState())
        val withNextPositions = NextPositionCalculator.calculate(normalized)

        _currentStory.value = StoryState(withNextPositions, LastEdit.Nothing)
        _documentInfo.value = document.info()
        backStackManager.addState(_currentStory.value)
        _commentConversations.value = document.commentConversations
        replaceCommentConversationArchive(document.commentConversations)
        _documentTagIds.value = document.tagIds
    }

    /**
     * Merges two [StoryStep] into a group. This can be used to merge two images into a message
     * group or any other kind of group.
     *
     * @param info [Action.Merge]
     */
    fun mergeRequest(info: Action.Merge) {
        if (!isEditable) return

        if (isOnSelection) {
            clearSelection()
        }

        _currentStory.value = writeopiaManager.mergeRequest(info, _currentStory.value)

        // Todo: Add to backstack
    }

    /**
     * A request to move a content to a position.
     *
     * @param move [Action.Move]
     */
    fun moveRequest(move: Action.Move) {
        if (!isEditable) return
        val fixedMove = move.fixMove()

        backStackManager.addState(_currentStory.value)

        if (_onEditPositions.value.contains(fixedMove.positionFrom)) {
            val bulkMove = Action.BulkMove(
                storyStep = selectedStories(),
                positionFrom = _onEditPositions.value,
                positionTo = fixedMove.positionTo
            )

            _currentStory.value = writeopiaManager.moveRequest(bulkMove, _currentStory.value)
        } else {
            _currentStory.value = writeopiaManager
                .moveRequest(fixedMove, _currentStory.value)
                .copy(focus = fixedMove.positionTo)
        }

        clearSelection()
    }

    fun onEquationEdition(position: String) {
        getStory(position)?.let { storyStep ->
            changeStoryState(
                stateChange = Action.StoryStateChange(
                    storyStep.copy(type = StoryTypes.TEXT.type),
                    position
                )
            )
        }
    }

    /**
     * At the moment it is only possible to check items not inside groups. Todo: Fix it!
     *
     * @param stateChange [Action.StoryStateChange]
     */
    fun changeStoryState(stateChange: Action.StoryStateChange, trackIt: Boolean = true) {
        if (!isEditable) return
        changeStoryStateAndTrackIt(stateChange, trackIt)
    }

    fun trackState() {
        backStackManager.addState(_currentStory.value)
    }

    /**
     * Click lister when user clicks in the menu to add a check item
     */
    fun onCheckItemClicked() {
        if (!isEditable) return
        val onEdit = _onEditPositions.value

        if (onEdit.isNotEmpty()) {
            toggleStateForStories(onEdit, StoryTypes.CHECK_ITEM)
        } else {
            changeCurrentStoryType(StoryTypes.CHECK_ITEM)
        }
    }

    /**
     * Click lister when user clicks in the menu to add a list item
     */
    fun addListItem() {
        if (!isEditable) return
        val onEdit = _onEditPositions.value

        if (onEdit.isNotEmpty()) {
            toggleStateForStories(onEdit, StoryTypes.UNORDERED_LIST_ITEM)
        } else {
            changeCurrentStoryType(StoryTypes.UNORDERED_LIST_ITEM)
        }
    }

    fun toggleHighLightBlock() {
        if (!isEditable) return
        val onEdit = _onEditPositions.value

        if (onEdit.isNotEmpty()) {
            toggleTagForStories(onEdit, TagInfo(Tag.HIGH_LIGHT_BLOCK))
        } else {
            currentFocus()?.first?.let { currentPosition ->
                toggleTagForStories(setOf(currentPosition), TagInfo(Tag.HIGH_LIGHT_BLOCK))
            }
        }
    }

    fun toggleCardBlock() {
        if (!isEditable) return
        val onEdit = _onEditPositions.value

        if (onEdit.isNotEmpty()) {
            toggleTagForStories(onEdit, TagInfo(Tag.CARD_BLOCK))
        } else {
            currentFocus()?.first?.let { currentPosition ->
                toggleTagForStories(setOf(currentPosition), TagInfo(Tag.CARD_BLOCK))
            }
        }
    }

    fun cancelSuggestions() {
        _currentStory.value =
            writeopiaManager.removeBy(_currentStory.value) { storyStep ->
                storyStep.tags.contains(TagInfo(Tag.AI_SUGGESTION))
            }
    }

    fun acceptSuggestions() {
        val changedSteps = mutableListOf<Pair<String, StoryStep>>()

        val newStories = getStories().mapValuesSorted { (position, storyStep) ->
            if (storyStep.tags.contains(TagInfo(Tag.AI_SUGGESTION))) {
                val updatedStep = storyStep.copy(
                    tags = storyStep.tags.filterNot { tagInfo ->
                        tagInfo.tag == Tag.AI_SUGGESTION || tagInfo.tag == Tag.FIRST_AI_SUGGESTION
                    }.toSet(),
                    ephemeral = false
                )
                changedSteps.add(position to updatedStep)
                updatedStep
            } else {
                storyStep
            }
        }

        _currentStory.value =
            _currentStory.value.copy(stories = newStories, lastEdit = LastEdit.BulkEdition(changedSteps))
    }

    fun toggleTagForPosition(position: String, tag: TagInfo, commandInfo: CommandInfo? = null) {
        if (!isEditable) return
        val story = getStory(position)
        val storyText = story?.text

        val newState = if (commandInfo != null && storyText != null) {
            val commandText = commandInfo.command.commandText.trim()

            val newText = if (
                commandInfo.commandTrigger == CommandTrigger.WRITTEN &&
                storyText.trimStart().startsWith(commandText)
            ) {
                // The command leaves the line with the space after it ("/box " of "/box text").
                storyText.trimStart().removePrefix(commandText).removePrefix(" ")
            } else {
                storyText
            }

            val mutable = currentStory.value.stories.toSortedMutableMap()
            mutable[position] = story.copy(text = newText)
            mutable
        } else {
            currentStory.value.stories
        }

        toggleTagForStories(setOf(position), tag, newState)
    }

    /**
     * Click listener when user clicks in the menu to add a code block
     */
    fun onCodeBlockClicked() {
        if (!isEditable) return
        val onEdit = _onEditPositions.value

        if (onEdit.isNotEmpty()) {
            toggleStateForStories(onEdit, StoryTypes.CODE_BLOCK)
        } else {
            changeCurrentStoryType(StoryTypes.CODE_BLOCK)
        }
    }

    fun toggleCollapseItem(position: String) {
        val state = _currentStory.value
        val isCollapsed = currentStory.value
            .stories[position]
            ?.tags
            ?.any { it.tag == Tag.COLLAPSED } == true

        _currentStory.value = if (isCollapsed) {
            writeopiaManager.expandItem(state, position)
        } else {
            writeopiaManager.collapseItem(state, position)
        }
    }

    /**
     * At the moment it is only possible to check items not inside groups. Todo: Fix it!
     *
     * @param stateChange [Action.StoryStateChange]
     */
    private fun changeStoryText(stateChange: Action.StoryStateChange) {
        if (!isEditable) return
        backStackManager.addTextState(_currentStory.value, stateChange.position)

        val step = stateChange.storyStep

        val newState = if (inTextMarkdownHandler != null) {
            stateChange.copy(storyStep = inTextMarkdownHandler.handleMarkdown(step))
        } else {
            stateChange
        }

        changeStoryStateAndTrackIt(newState, trackIt = false)
    }

    /**
     * Changes the story type. The type of a messages changes without changing the content of it.
     * Commands normally change the type of a message. From a message to a unordered list item, for
     * example.
     *
     * @param position Double
     * @param typeInfo [TypeInfo]
     * @param commandInfo [CommandInfo]
     */
    fun changeStoryType(position: String, typeInfo: TypeInfo, commandInfo: CommandInfo?) {
        if (!isEditable) return
        if (isOnSelection) {
            clearSelection()
        }

        _currentStory.value =
            writeopiaManager.changeStoryType(position, typeInfo, commandInfo, _currentStory.value)

        if (listTypes.contains(typeInfo.storyType.number)) {
            coroutineScope.launch(dispatcher) {
                val nextPosition = positionAfter(position)
                val newState = writeopiaManager.generateSuggestionsList(
                    storyState = { _currentStory.value },
                    storyType = typeInfo.storyType,
                    position = nextPosition,
                    context = getDocumentText(),
                    userId = getUserId(),
                )

                _currentStory.value = newState
            }
        }
    }

    fun removeTags(position: String) {
        if (!isEditable) return
        _currentStory.value =
            writeopiaManager.removeTags(position, _currentStory.value)
    }

    /**
     * Creates a line break. When a line break happens, the line it divided into two [StoryStep]s
     * of the same, if possible, or the next line will be a Message.
     *
     * @param lineBreak [Action.LineBreak]
     * @param processCommands If true, processes markdown commands (like ###, -, etc.) on each
     *                        new line after splitting. Useful when accepting AI responses.
     */
    fun onLineBreak(lineBreak: Action.LineBreak, processCommands: Boolean = false) {
        if (!isEditable) return
        val lastBreak = lastLineBreak

        val now = Clock.System.now().toEpochMilliseconds()

        if (lastBreak != null &&
            lastBreak.text == lineBreak.storyStep.text &&
            lastBreak.position == lineBreak.position &&
            (now - lastBreak.time.toEpochMilliseconds() < 100)
        ) {
            return
        }

        lastLineBreak = LineBreakCommand(
            text = lineBreak.storyStep.text ?: "",
            position = lineBreak.position,
            time = Clock.System.now()
        )

        if (isOnSelection) {
            clearSelection()
        }

        coroutineScope.launch(dispatcher) {
            breakLine(lineBreak, processCommands)
        }
    }

    /**
     * Splits the line of [lineBreak] by the line breaks of its text, in the current state, and
     * reads the Markdown of the lines (headings, lists, checks, inline marks) when
     * [processCommands].
     *
     * @return the steps the split changed or created, by position, as they are in the state.
     */
    private fun breakLine(
        lineBreak: Action.LineBreak,
        processCommands: Boolean
    ): List<Pair<String, StoryStep>> {
        val state = _currentStory.value
        val story = getStory(position = lineBreak.position)

        val expanded: StoryState = if (story?.tags?.any { it.tag.isTitle() } == true) {
            writeopiaManager.expandItem(state, lineBreak.position)
        } else {
            state
        }

        val (_, newState) = writeopiaManager.onLineBreak(lineBreak, expanded)
        // Todo: Fix this when the inner position are completed
        //  backStackManager.addAction(BackstackAction.Add(newStory, newPosition))

        // First apply the line break state
        _currentStory.value = newState.copy(selection = Selection.start())
        var edited = newState.lastEdit.editedSteps()

        // Process markdown commands on each newly created line
        if (processCommands) {
            val parseResult = bulkStyleParseHandler.processMarkdown(
                lastEdit = newState.lastEdit,
                currentStories = { _currentStory.value.stories },
                onCommandProcess = { pos, step, text ->
                    commandHandler.handleCommand(text, step, pos)
                }
            )

            parseResult?.let { result ->
                _currentStory.value = _currentStory.value.copy(
                    stories = result.stories,
                    lastEdit = result.lastEdit ?: _currentStory.value.lastEdit
                )
                result.lastEdit?.let { lastEdit -> edited = lastEdit.editedSteps() }
            }
        }

        cleanupOrphanCommentConversations()
        _scrollToPosition.value = -1

        return edited
    }

    fun onFocusChange(position: String, hasFocus: Boolean) {
        if (!hasFocus) return
        val story = currentStory.value

        // The edit stays: the trackers read only the latest state, so replacing the edit with
        // Nothing would lose it when they are still saving the one before (a line break moves
        // the focus right after it). Saving it again is harmless. The selection follows the
        // focus so the manager knows which line is active.
        _currentStory.value = story.copy(
            focus = position,
            selection = story.selection.copy(position = position)
        )
    }

    fun scrollToPosition(position: Int) {
        _scrollToPosition.value = position
    }

    private fun selected(isSelected: Boolean, position: String) {
        if (!isEditable) return
        if (_currentStory.value.stories[position] != null) {
            // Reset keyboard selection anchor when selection is modified via mouse/drag
            keyboardSelectionAnchor = null
            if (isSelected) {
                _onEditPositions.value += position
            } else {
                _onEditPositions.value -= position
            }
        }
    }

    /**
     * Add a [StoryStep] of a position into the selection list. Selected content can be used to
     * perform bulk actions, like bulk edition and bulk deletion.
     */
    fun onSelected(isSelected: Boolean, position: String) {
        if (!isEditable) return
        selectionBuffer.send(isSelected to position)
    }

    fun onSectionSelected(position: String) {
        if (!isEditable) return
        // Reset keyboard selection anchor when selection is modified via section select
        keyboardSelectionAnchor = null
        val isSelected = _onEditPositions.value.contains(position)
        val stories = getStories()

        val lastPosition = getStories().asSequence()
            .filter { (posi, _) -> posi > position }
            .find { (_, story) -> story.tags.any { it.tag.isTitle() } }
            ?.key
            ?: lastPosition()

        val sortedPositions = stories.keys.filter { it in position..lastPosition }.toSet()

        if (isSelected) {
            _onEditPositions.value -= sortedPositions
        } else {
            _onEditPositions.value += sortedPositions
        }
    }

    fun toggleSelection(position: String) {
        if (!isEditable) return
        onSelected(!_onEditPositions.value.contains(position), position)
    }

    /**
     * A click at the end of the document. The focus should be moved to the first [StoryStep] that
     * can receive the focus, from last to first.
     */
    fun clickAtTheEnd() {
        val stories = _currentStory.value.stories
        val lastPosition = lastPosition()
        val lastContentStory = stories[lastPosition]

        val newState = if (lastContentStory?.type == StoryTypes.TEXT.type) {
            val newStoriesState = stories.toSortedMutableMap().apply {
                this[lastPosition] = lastContentStory.copyNewLocalId()
            }
            val cursor = lastContentStory.text?.length ?: 0

            _currentStory.value.copy(
                focus = lastPosition,
                stories = newStoriesState,
                selection = Selection.fromPosition(
                    cursorPosition = cursor,
                    stepPosition = lastPosition
                )
            )
        } else {
            val newPosition = Rank.after(lastPosition)
            val newLastMessage = StoryStep(
                type = StoryTypes.TEXT.type,
                previousPosition = lastPosition
            )

            // Update the last content story to point to the new story
            val updatedStories = stories.toSortedMutableMap().apply {
                lastContentStory?.let { lastStory ->
                    this[lastPosition] = lastStory.copy(nextPosition = newPosition)
                }
                this[newPosition] = newLastMessage
            }

            val cursor = newLastMessage.text?.length ?: 0

            StoryState(
                updatedStories,
                LastEdit.LineEdition(newPosition, newLastMessage),
                newPosition,
                selection = Selection.fromPosition(
                    cursorPosition = cursor,
                    stepPosition = newPosition
                )
            )
        }

        _currentStory.value = newState
    }

    /**
     * Undo the last action.
     */
    override fun undo() {
        if (!isEditable) return
        if (!backStackManager.canUndo.value) return

        coroutineScope.launch(dispatcher) {
            clearSelection()

            backStackManager.previousState(_currentStory.value)?.let { state ->
                val stories = state.stories.mapValuesSorted { (_, story) ->
                    story.copy(localId = GenerateId.generate())
                }
                _currentStory.value = state.copy(stories = stories)
                restoreCommentConversationsForCurrentStory()
            }
        }
    }

    /**
     * Redo the last undone action.
     */
    override fun redo() {
        if (!isEditable) return
        if (!backStackManager.canRedo.value) return

        coroutineScope.launch(dispatcher) {
            clearSelection()

            backStackManager.nextState()?.let { state ->
                val stories = state.stories.mapValuesSorted { (_, story) ->
                    story.copy(localId = GenerateId.generate())
                }
                _currentStory.value = state.copy(stories = stories)
                restoreCommentConversationsForCurrentStory()
            }
        }
    }

    /**
     * Deletes a [StoryStep]
     *
     * @param deleteStory [Action.DeleteStory]
     */
    fun onDelete(deleteStory: Action.DeleteStory) {
        if (!isEditable) return
        backStackManager.addState(_currentStory.value)

        coroutineScope.launch(dispatcher) {
            writeopiaManager.onDelete(
                deleteStory,
                _currentStory.value,
                _documentInfo.value.id
            )?.let { newState ->
                _currentStory.value = newState
                cleanupOrphanCommentConversations()
            }
        }
    }

    fun onErase(eraseStory: Action.EraseStory) {
        if (!isEditable) return
        coroutineScope.launch(dispatcher) {
            val previousInfo = writeopiaManager.previousTextStory(
                _currentStory.value.stories,
                eraseStory.position
            )
            val previousStory = previousInfo?.first
            val newFocus = previousInfo?.second

            val state = writeopiaManager.onErase(eraseStory, _currentStory.value).let { state ->
                if (previousStory != null && newFocus != null) {
                    state.copy(
                        selection = Selection.fromPosition(
                            previousStory.text?.length ?: 0,
                            newFocus
                        )
                    )
                } else {
                    state
                }
            }

            backStackManager.addState(_currentStory.value)
            _currentStory.value = state
            cleanupOrphanCommentConversations()
        }
    }

    /**
     * Deletes the whole selection. All [StoryStep] in the selection will be deleted.
     */
    fun deleteSelection() {
        if (!isEditable) return
        coroutineScope.launch(dispatcher) {
            val oldStories = _currentStory.value.stories
            val (newStories, toDeleteStories) = writeopiaManager.bulkDelete(
                _onEditPositions.value,
                oldStories
            )

            _onEditPositions.value = emptySet()

            // Collect deleted IDs
            val deletedIds = toDeleteStories.values.map { it.id }

            // Find updated stories (those whose content changed, e.g., position references)
            val updatedSteps = newStories.filter { (position, story) ->
                oldStories[position] != story
            }.map { (position, story) -> position to story }

            backStackManager.addState(_currentStory.value)
            val state = _currentStory.value.copy(
                stories = newStories,
                lastEdit = LastEdit.BulkDeleteEdition(deletedIds, updatedSteps)
            )
            _currentStory.value = state
            cleanupOrphanCommentConversations()
        }
    }

    fun onDragHover(position: String) {
        dragPosition.value = position
    }

    fun onDragStart() {
        isDragging.value = true
    }

    fun onDragStop() {
        coroutineScope.launch {
            // It is necessary to delay the stop dragging event to wait for the move request to
            // be received.
            delay(100)
            isDragging.value = false
        }
    }

    /**
     * Clears the [WriteopiaStateManager]. Use this in the onCleared of your ViewModel.
     */
    fun onClear() {
        coroutineScope.launch {
            sharedEditionManager?.stopLiveEdition()
        }.invokeOnCompletion {
            coroutineScope.cancel()
        }
    }

    fun moveToNext(cursor: Int, position: Int = 1) {
        val focusPosition = currentFocus()?.let { (position, _) -> position } ?: Rank.FIRST
        nextFocusOrCreate(minOf(focusPosition, lastPosition()), cursor)
    }

    fun moveToPrevious(cursor: Int, positions: Int = 1) {
        val focusPosition = currentFocus()?.let { (position, _) -> position } ?: Rank.FIRST
        previousFocus(focusPosition, cursor)
    }

    fun toggleLockDocument() {
        val info = _documentInfo.value

        _documentInfo.value = info.copy(isLocked = !info.isLocked)
        _currentStory.value = currentStory.value.copy(lastEdit = LastEdit.Metadata)
    }

    fun toggleFavoriteDocument() {
        val info = _documentInfo.value

        _documentInfo.value = info.copy(isFavorite = !info.isFavorite)
        _currentStory.value = currentStory.value.copy(lastEdit = LastEdit.Metadata)
    }

    fun setFavorite(isFavorite: Boolean) {
        val info = _documentInfo.value
        if (info.isFavorite != isFavorite) {
            _documentInfo.value = info.copy(isFavorite = isFavorite)
        }
    }

    fun createComment(text: String): CommentConversation? =
        createComment(text, _currentStory.value.selection)

    fun createComment(text: String, selection: Selection): CommentConversation? {
        if (!isEditable) return null

        val state = _currentStory.value
        val (start, end) = selection.sortedPositions()
        if (start == end) return null

        val story = state.stories[selection.position] ?: return null
        val textLength = story.text?.length ?: return null
        if (start < 0 || end > textLength) return null

        val comment = Comment(text = text)
        val conversation = CommentConversation(comments = listOf(comment))
        _currentStory.value = writeopiaManager.addSpan(
            state,
            selection.position,
            SpanInfo.create(start, end, Span.COMMENT, conversation.id)
        )
        _commentConversations.update { conversations ->
            conversations + (conversation.id to conversation.comments)
        }
        rememberCommentConversations(mapOf(conversation.id to conversation.comments))
        return conversation
    }

    fun addComment(conversationId: String, text: String): Comment? {
        if (!isEditable) return null

        val comment = Comment(text = text)
        var updatedComments: List<Comment>? = null
        _commentConversations.update { conversations ->
            updatedComments = null
            val comments = conversations[conversationId] ?: return@update conversations
            val nextComments = comments + comment
            updatedComments = nextComments
            conversations + (conversationId to nextComments)
        }

        val updated = updatedComments ?: return null
        rememberCommentConversations(mapOf(conversationId to updated))
        return comment
    }

    fun getCommentConversationAtCursor(): CommentConversation? {
        val state = _currentStory.value
        val selection = state.selection
        val story = state.stories[selection.position] ?: return null

        val conversationId = story.spans
            .asSequence()
            .filter { span ->
                span.span == Span.COMMENT &&
                    selection.start >= span.start &&
                    selection.start < span.end
            }
            .sortedWith(compareBy<SpanInfo> { it.size() }.thenBy { it.start })
            .mapNotNull { it.extra }
            .firstOrNull()
            ?: return null

        return _commentConversations.value[conversationId]
            ?.filterNot { comment -> comment.deleted }
            ?.takeIf { comments -> comments.isNotEmpty() }
            ?.let { comments ->
                CommentConversation(id = conversationId, comments = comments)
            }
    }

    fun getCommentConversationAtSelection(): CommentConversation? {
        val state = _currentStory.value
        val selection = state.selection
        val story = state.stories[selection.position] ?: return null
        val (start, end) = selection.sortedPositions()

        val conversationId = story.spans
            .asSequence()
            .filter { span ->
                span.span == Span.COMMENT &&
                    if (start == end) {
                        span.isInside(start)
                    } else {
                        span.start < end && span.end > start
                    }
            }
            .sortedWith(compareBy<SpanInfo> { it.start }.thenBy { it.end })
            .mapNotNull { it.extra }
            .firstOrNull()
            ?: return null

        return _commentConversations.value[conversationId]
            ?.filterNot { comment -> comment.deleted }
            ?.takeIf { comments -> comments.isNotEmpty() }
            ?.let { comments ->
                CommentConversation(id = conversationId, comments = comments)
            }
    }

    fun deleteComment(conversationId: String, commentId: String): Boolean {
        if (!isEditable) return false

        var updatedComments: List<Comment>? = null
        var removedConversation = false
        var foundComment = false
        _commentConversations.update { conversations ->
            updatedComments = null
            removedConversation = false
            foundComment = false

            val comments = conversations[conversationId] ?: return@update conversations
            if (comments.none { comment -> comment.id == commentId && !comment.deleted }) {
                return@update conversations
            }

            foundComment = true
            val hasActiveReply = comments.any { comment ->
                comment.id != commentId && !comment.deleted
            }
            if (hasActiveReply) {
                val commentsWithTombstone = comments.map { comment ->
                    if (comment.id == commentId) comment.copy(deleted = true) else comment
                }
                updatedComments = commentsWithTombstone
                conversations + (conversationId to commentsWithTombstone)
            } else {
                removedConversation = true
                conversations - conversationId
            }
        }

        if (!foundComment) return false
        updatedComments?.let { comments ->
            rememberCommentConversations(mapOf(conversationId to comments))
        }
        if (removedConversation) {
            removeCommentSpans(conversationId)
            commentConversationArchive.update { archived -> archived - conversationId }
        }
        return true
    }

    fun deleteCommentConversation(conversationId: String): Boolean {
        if (!isEditable) return false

        var removed = false
        _commentConversations.update { conversations ->
            removed = conversations.containsKey(conversationId)
            if (removed) {
                conversations - conversationId
            } else {
                conversations
            }
        }
        if (!removed) return false

        removeCommentSpans(conversationId)
        commentConversationArchive.update { archived -> archived - conversationId }
        return true
    }

    private fun removeCommentSpans(conversationId: String) {
        val state = _currentStory.value
        val changedSteps = mutableListOf<Pair<String, StoryStep>>()
        val stories = state.stories.mapValuesSorted { (position, story) ->
            val spans = story.spans.filterNot { span ->
                span.span == Span.COMMENT && span.extra == conversationId
            }.toSet()

            if (spans != story.spans) {
                story.copy(
                    localId = GenerateId.generate(),
                    spans = spans
                ).also { changedSteps += position to it }
            } else {
                story
            }
        }

        if (changedSteps.isNotEmpty()) {
            _currentStory.value = state.copy(
                stories = stories,
                lastEdit = LastEdit.BulkEdition(changedSteps)
            )
        }
    }

    private fun cleanupOrphanCommentConversations() {
        val referencedConversationIds = referencedCommentConversationIds()
        var removedConversations: Map<String, List<Comment>> = emptyMap()
        _commentConversations.update { conversations ->
            removedConversations = conversations.filterKeys { it !in referencedConversationIds }
            conversations.filterKeys { it in referencedConversationIds }
        }
        if (removedConversations.isNotEmpty()) {
            rememberCommentConversations(removedConversations)
        }
    }

    private fun referencedCommentConversationIds(): Set<String> =
        _currentStory.value.stories.values
            .asSequence()
            .flatMap { it.spans.asSequence() }
            .filter { it.span == Span.COMMENT }
            .mapNotNull { it.extra }
            .toSet()

    private fun replaceCommentConversationArchive(conversations: Map<String, List<Comment>>) {
        commentConversationArchive.value = conversations
    }

    private fun rememberCommentConversations(conversations: Map<String, List<Comment>>) {
        commentConversationArchive.update { archived ->
            archived + conversations
        }
    }

    private fun restoreCommentConversationsForCurrentStory() {
        val referencedConversationIds = referencedCommentConversationIds()
        val currentById = _commentConversations.value
        val archivedById = commentConversationArchive.value
        val restored = referencedConversationIds.mapNotNull { conversationId ->
            (currentById[conversationId] ?: archivedById[conversationId])?.let { comments ->
                conversationId to comments
            }
        }.toMap()

        val missingConversationIds = referencedConversationIds - restored.keys
        if (missingConversationIds.isNotEmpty()) {
            val state = _currentStory.value
            val changedSteps = mutableListOf<Pair<String, StoryStep>>()
            val stories = state.stories.mapValuesSorted { (position, story) ->
                val spans = story.spans.filterNot { span ->
                    span.span == Span.COMMENT && span.extra in missingConversationIds
                }.toSet()

                if (spans != story.spans) {
                    story.copy(
                        localId = GenerateId.generate(),
                        spans = spans,
                    ).also { changedSteps += position to it }
                } else {
                    story
                }
            }
            if (changedSteps.isNotEmpty()) {
                _currentStory.value = state.copy(
                    stories = stories,
                    lastEdit = LastEdit.BulkEdition(changedSteps),
                )
            }
        }

        _commentConversations.update { current ->
            if (current == restored) current else restored
        }
    }

    fun toggleSpan(span: Span, extra: String? = null) {
        if (isEditable) {
            val onEdit = _onEditPositions.value

            if (onEdit.isNotEmpty()) {
                _currentStory.value =
                    writeopiaManager.addSpanToStories(_currentStory.value, onEdit, span, extra)
            } else {
                val selection = currentStory.value.selection
                val (start, end) = selection.sortedPositions()

                _currentStory.value = writeopiaManager.addSpan(
                    _currentStory.value,
                    selection.position,
                    SpanInfo.create(start, end, span, extra)
                )
            }
        }
    }

    fun onLinkSet(link: String) {
        toggleSpan(Span.LINK, link)
    }

    /**
     * Calculates the target position for inserting content, protecting the title from being replaced.
     * Returns Pair of (targetPosition, useInsertMode).
     */
    private fun getTitleProtectedPosition(
        pos: String,
        story: StoryStep?,
        explicitPosition: Boolean
    ): Pair<String, Boolean> {
        val isOnTitle = story?.type == StoryTypes.TITLE.type
        return if (isOnTitle && !explicitPosition) {
            // Right after the title: before the story that follows it
            Pair(positionAfter(pos), true)
        } else {
            Pair(pos, false)
        }
    }

    fun addImage(imagePath: String, position: String? = null) {
        if (!isEditable) return
        (position ?: currentPosition())?.let { pos ->
            val story = getStory(pos)

            if (story != null) {
                val (targetPosition, useInsertMode) = getTitleProtectedPosition(
                    pos,
                    story,
                    explicitPosition = position != null
                )

                // Check if we should upload to cloud
                coroutineScope.launch(dispatcher) {
                    val shouldUpload = imageUploader?.isAuthenticated() == true

                    if (shouldUpload) {
                        // Insert image with loading state immediately
                        val loadingStep = StoryStep(
                            type = StoryTypes.IMAGE.type,
                            path = imagePath,
                            ephemeral = true,
                            loading = true
                        )

                        val imagePosition = if (useInsertMode || position != null) {
                            addAtPosition(loadingStep, targetPosition)
                        } else {
                            changeStoryStateAndTrackIt(
                                Action.StoryStateChange(
                                    story.copy(
                                        type = StoryTypes.IMAGE.type,
                                        path = imagePath,
                                        ephemeral = true,
                                        loading = true
                                    ),
                                    targetPosition
                                )
                            )
                            targetPosition
                        }

                        // Upload in background
                        val result = imageUploader!!.uploadImage(imagePath)

                        // Replace with final image
                        val currentStory = getStory(imagePosition)
                        if (currentStory != null) {
                            val finalStep = when (result) {
                                is io.writeopia.sdk.models.utils.ResultData.Complete -> currentStory.copy(
                                    url = result.data,
                                    path = null,
                                    ephemeral = false,
                                    loading = false
                                )
                                else -> currentStory.copy(
                                    url = null,
                                    path = imagePath,
                                    ephemeral = false,
                                    loading = false
                                )
                            }

                            changeStoryStateAndTrackIt(
                                Action.StoryStateChange(finalStep, imagePosition)
                            )
                        }
                    } else {
                        // No auth - use local path directly (existing behavior)
                        if (useInsertMode || position != null) {
                            addAtPosition(
                                StoryStep(type = StoryTypes.IMAGE.type, path = imagePath),
                                targetPosition
                            )
                        } else {
                            changeStoryStateAndTrackIt(
                                Action.StoryStateChange(
                                    story.copy(type = StoryTypes.IMAGE.type, path = imagePath),
                                    targetPosition
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Adds a story at [position], or right before the story there. The stories after are
     * spread out when there is no room for it.
     *
     * @return the position the story got, [position] when the document can't be edited.
     */
    fun addAtPosition(storyStep: StoryStep, position: String): String {
        if (!isEditable) return position
        _currentStory.value = writeopiaManager.addAtPosition(
            _currentStory.value,
            storyStep,
            position
        )
        return positionOf(storyStep.id) ?: position
    }

    /** The position of the story with [id], if it is in the document. */
    private fun positionOf(id: String): String? =
        _currentStory.value.stories.entries.firstOrNull { (_, step) -> step.id == id }?.key

    /**
     * Adds a story right after the one at [position], half way to the next story. No other
     * story moves.
     *
     * @return the position the story got.
     */
    fun addAfterPosition(storyStep: StoryStep, position: String): String {
        if (!isEditable) return position

        _currentStory.value = writeopiaManager.addAfterPosition(
            _currentStory.value,
            storyStep,
            position
        )

        return positionOf(storyStep.id) ?: position
    }

    /**
     * Puts a loading story at [position], or right before the story there. The story at
     * [position] is kept when it is a loading one already.
     *
     * @return the position of the loading story, where the answer should go.
     */
    fun loadingAtPosition(position: String): String {
        if (StoryTypes.LOADING.type == getStory(position)?.type) return position

        return addAtPosition(
            StoryStep(type = StoryTypes.LOADING.type, ephemeral = true),
            position = position
        )
    }

    /**
     * Where a story added "right after" [position] should be asked for: the position of the
     * story that follows it, which [addAtPosition] puts the new story before, or a free
     * position after the last story.
     */
    fun positionAfter(position: String): String = getStories().nextKey(position) ?: Rank.after(position)

    /**
     * Takes the stories at [positions] out of the document and leaves one skeleton in the place
     * of the first, holding their text, while the AI rewrites them. The state before is kept in
     * the back stack, so the change can be undone.
     *
     * @return the position of the skeleton, or null when nothing was replaced.
     */
    fun replaceWithSkeleton(positions: Set<String>): String? {
        if (!isEditable || positions.isEmpty()) return null

        val stories = _currentStory.value.stories
        val sorted = positions.sorted().filter { position -> stories.containsKey(position) }
        val first = sorted.firstOrNull() ?: return null
        val text = sorted.mapNotNull { position -> stories[position]?.text }.joinToString("\n")

        backStackManager.addState(_currentStory.value)
        _onEditPositions.value = emptySet()

        val (remaining, deleted) = writeopiaManager.bulkDelete(sorted, stories)
        // The skeleton takes the place of the first replaced story, between the neighbours of
        // the replaced range, so the text that takes its place later lands between them.
        val skeleton = StoryStep(
            type = StoryTypes.SKELETON.type,
            text = text,
            ephemeral = true,
        )

        val withSkeleton = remaining.toSortedMutableMap().apply { this[first] = skeleton }
        _currentStory.value = _currentStory.value.copy(
            stories = NextPositionCalculator.relinkAround(withSkeleton, first),
            lastEdit = LastEdit.BulkDeleteEdition(deleted.values.map { it.id }, emptyList())
        )
        cleanupOrphanCommentConversations()

        return first
    }

    /**
     * Puts [text] where the skeleton of [replaceWithSkeleton] is, as regular content: its lines
     * become stories and its Markdown marks (lists, headings, checks) are applied, like an
     * accepted AI answer. Nothing happens when the skeleton is gone. Undo goes back to before
     * the skeleton, since [replaceWithSkeleton] kept that state.
     */
    fun replaceSkeletonWithText(position: String, text: String) {
        if (!isEditable) return
        val skeleton = getStory(position) ?: return
        if (skeleton.type != StoryTypes.SKELETON.type) return

        val step = skeleton.copy(
            type = StoryTypes.TEXT.type,
            text = text,
            ephemeral = false,
            localId = GenerateId.generate()
        )
        val styled = inTextMarkdownHandler?.handleMarkdown(step) ?: step

        changeStoryState(Action.StoryStateChange(styled, position), trackIt = false)

        // Typed text goes through changeStoryText, which tracks every change; here the state
        // before the skeleton is the only one to go back to.
        if (text.contains("\n")) {
            onLineBreak(Action.LineBreak(styled, position), processCommands = true)
        } else {
            commandHandler.handleCommand(text, styled, position)
        }
    }

    /**
     * Starts rewriting the region of the document from [from] up to [toExclusive] (to the end
     * when null): every story in it goes away, replaced by [stories] (typically a skeleton and
     * the blocks the rewrite keeps, which keep their ids). The state before is kept in the back
     * stack, so the rewrite can be undone, and the stories that went away are deleted.
     *
     * @return the region to pass to [updateRegion], whose end may be past [toExclusive] when
     * the stories after it had to be spread out to make room; null when the region holds
     * nothing and nothing was done.
     */
    fun beginRegionRewrite(from: String, toExclusive: String?, stories: List<StoryStep>): EditRegion? {
        if (!isEditable) return null

        val current = _currentStory.value
        val inside = current.stories.filterKeys { position -> position.inRegion(from, toExclusive) }
        if (inside.isEmpty()) return null

        backStackManager.addState(current)
        _onEditPositions.value = emptySet()

        val keptIds = stories.map { story -> story.id }.toSet()
        val deletedIds = inside.values
            .filter { story -> !story.ephemeral && story.id !in keptIds }
            .map { story -> story.id }

        val (merged, end) = spreadInRegion(current.stories - inside.keys, from, toExclusive, stories)
        _currentStory.value = current.copy(
            stories = NextPositionCalculator.calculate(merged),
            lastEdit = LastEdit.BulkDeleteEdition(deletedIds, emptyList())
        )
        cleanupOrphanCommentConversations()

        return EditRegion(from, end)
    }

    /**
     * Replaces the region of [beginRegionRewrite] with [stories], in order, spread over the
     * region. While not [done] the change is not saved (the answer is still coming); when done,
     * the stories get their links to their neighbours and the document is saved whole.
     *
     * @return the region for the next call: see [beginRegionRewrite].
     */
    fun updateRegion(region: EditRegion, stories: List<StoryStep>, done: Boolean): EditRegion {
        if (!isEditable) return region

        val (from, toExclusive) = region
        val current = _currentStory.value
        val outside = current.stories.filterKeys { position -> !position.inRegion(from, toExclusive) }
        // Sorted and linked: the map is drawn in its own order
        val (spread, end) = spreadInRegion(outside, from, toExclusive, stories)
        val merged = NextPositionCalculator.calculate(spread)

        _currentStory.value = if (done) {
            current.copy(stories = merged, lastEdit = LastEdit.Whole)
        } else {
            current.copy(stories = merged, lastEdit = LastEdit.Nothing)
        }

        return EditRegion(from, end)
    }

    private fun String.inRegion(from: String, toExclusive: String?): Boolean =
        this >= from && (toExclusive == null || this < toExclusive)

    /**
     * [outside] (the document without the region) plus [stories] at positions inside the
     * region: the first exactly at [from], the others between it and [toExclusive].
     *
     * @return the map and the end of the region (exclusive), null when it runs to the end.
     */
    private fun spreadInRegion(
        outside: Map<String, StoryStep>,
        from: String,
        toExclusive: String?,
        stories: List<StoryStep>
    ): Pair<Map<String, StoryStep>, String?> {
        val map = outside.toSortedMutableMap()
        if (stories.isEmpty()) return map to toExclusive

        val end = map.nextKey(from)
        map[from] = stories.first()
        Rank.between(from, end, stories.size - 1).forEachIndexed { index, key -> map[key] = stories[index + 1] }

        return map to end
    }

    fun removeAtPosition(position: String) {
        if (!isEditable) return
        _currentStory.value = writeopiaManager.removeAtPosition(
            _currentStory.value,
            position
        )
    }

    fun handleTextInput(
        input: TextInput,
        position: String,
        lineBreakByContent: Boolean,
        trackIt: Boolean = true,
        processCommands: Boolean = false
    ) {
        if (!isEditable) return

        // Track when text is being selected (start != end) for disabling drag selection
        textSelectionActiveState.value = input.start != input.end

        val text = input.text
        val step = _currentStory.value.stories[position] ?: return

        if (lineBreakByContent && text.contains("\n")) {
            val newStep = step.copy(text = text, spans = input.spans)
            onLineBreak(Action.LineBreak(newStep, position), processCommands = processCommands)
        } else if (isLinkPastedInEmptyText(step, text)) {
            createLinkCard(step, position, text.trim())
        } else {
            val newText = text.replace("\n", "")
            val newStep = step.copy(text = newText, spans = input.spans)

            // The command removes its text from the line in the state, which still holds the
            // previous keystroke ("[" when "[]" was typed). It needs the whole command to remove it.
            if (commandHandler.isExactCommand(text, step)) {
                _currentStory.value = _currentStory.value.copy(
                    stories = _currentStory.value.stories.toSortedMutableMap().apply { this[position] = newStep }
                )
            }

            val handled = commandHandler.handleCommand(text, newStep, position)

            if (!handled) {
                changeStoryText(
                    Action.StoryStateChange(
                        newStep,
                        position,
                        selectionStart = input.start,
                        selectionEnd = input.end,
                    )
                )
            }
        }
    }

    private fun isLinkPastedInEmptyText(step: StoryStep, text: String): Boolean =
        step.type.number == StoryTypes.TEXT.type.number &&
            LinkDetector.isPastedLink(previousText = step.text, newText = text)

    /**
     * Turns an empty line into a link card for [url]. The card has no description yet: the app
     * asks the backend for one when [linkCardCreated] emits. The change is one synchronous
     * state update, made by [WriteopiaManager.createLinkCard].
     */
    private fun createLinkCard(step: StoryStep, position: String, url: String) {
        val current = _currentStory.value
        val newState = writeopiaManager.createLinkCard(position, url, current)
        val linkCard = newState.stories[position]
            ?.takeIf { it.type.number == StoryTypes.LINK_CARD.type.number }
            ?: return

        backStackManager.addState(current)
        _currentStory.value = newState
        cleanupOrphanCommentConversations()
        _scrollToPosition.value = -1

        println("LinkCard: created at $position for $url, cursor moved to ${newState.focus}")
        _linkCardCreated.tryEmit(LinkCardCreation(linkCard, position))
    }

    /**
     * Applies a [StoryStep] that the backend changed to the open document, in the position its id
     * has here. Used for the description of a link card, which the AI writes after the card is
     * synced. Nothing happens when the step is not in the document anymore.
     *
     * @return true when the step was applied.
     */
    fun applyServerStoryStep(storyStep: StoryStep): Boolean {
        val entry = _currentStory.value.stories.entries.firstOrNull { (_, step) ->
            step.id == storyStep.id
        } ?: return false

        val (position, localStep) = entry
        val updated = storyStep.copy(
            localId = GenerateId.generate(),
            dbPosition = localStep.dbPosition,
            nextPosition = localStep.nextPosition,
            previousPosition = localStep.previousPosition
        )

        changeStoryState(Action.StoryStateChange(updated, position, preserveFocus = true), trackIt = false)

        return true
    }

    /**
     * Cancels the current selection.
     */
    fun clearSelection() {
        _onEditPositions.value = emptySet()
        keyboardSelectionAnchor = null
    }

    /**
     * Extends the multi-line selection upward using keyboard.
     * Only works when at least one line is selected.
     * Returns true if the event was handled, false if normal OS behavior should occur.
     */
    fun extendSelectionUp(): Boolean {
        val currentSelection = _onEditPositions.value
        if (currentSelection.isEmpty()) return false

        val stories = getStories()
        val minPosition = currentSelection.min()
        val maxPosition = currentSelection.max()

        // If anchor is unknown (mouse selection), set it to the bottom of selection
        val anchor = keyboardSelectionAnchor ?: maxPosition
        keyboardSelectionAnchor = anchor

        return if (anchor == maxPosition) {
            // Extending upward from anchor at bottom: add position above current min
            val previousPosition = stories[minPosition]?.previousPosition
            if (previousPosition != null && stories.previousKey(previousPosition) != null) { // Never the first story (the title)
                _onEditPositions.value = currentSelection + previousPosition
                true
            } else {
                false
            }
        } else {
            // Anchor is at top, contracting from bottom
            if (maxPosition > anchor) {
                _onEditPositions.value = currentSelection - maxPosition
                true
            } else {
                false
            }
        }
    }

    /**
     * Extends the multi-line selection downward using keyboard.
     * Only works when at least one line is selected.
     * Returns true if the event was handled, false if normal OS behavior should occur.
     */
    fun extendSelectionDown(): Boolean {
        val currentSelection = _onEditPositions.value
        if (currentSelection.isEmpty()) return false

        val stories = getStories()
        val minPosition = currentSelection.min()
        val maxPosition = currentSelection.max()

        // If anchor is unknown (mouse selection), set it to the top of selection
        val anchor = keyboardSelectionAnchor ?: minPosition
        keyboardSelectionAnchor = anchor

        return if (anchor == minPosition) {
            // Extending downward from anchor at top: add position below current max
            val nextPosition = stories[maxPosition]?.nextPosition
            if (nextPosition != null) {
                _onEditPositions.value = currentSelection + nextPosition
                true
            } else {
                false
            }
        } else {
            // Anchor is at bottom, contracting from top
            if (minPosition < anchor) {
                _onEditPositions.value = currentSelection - minPosition
                true
            } else {
                false
            }
        }
    }

    /** Adds the images among [files] right after the story at [position], in order. */
    fun receiveExternalFiles(files: List<ExternalFile>, position: String) {
        files
            .filter { file -> supportedImageFiles.contains(file.extension) }
            .reversed()
            .forEach { (filePath, _) ->
                addImage(filePath, positionAfter(position))
            }
    }

    fun getDocumentText() = currentStory.value
        .stories
        .values
        .filter { storyStep -> !storyStep.text.isNullOrBlank() }
        .joinToString(separator = "\n") { story ->
            story.text ?: ""
        }

    /**
     * Return a list of consecutive selections, with start and end position and the merged text
     * that is selected
     */
    private fun getSelectionInfo(): List<SelectionInfo> {
        val selected = _onEditPositions.value

        return if (selected.isNotEmpty()) {
            // TODO: Fix this to accept multiple clusters of selection!
            val from = selected.min()
            val to = selected.max()
            val join = selectedStories().mapNotNull { it.text }.joinToString(separator = "\n")

            listOf(SelectionInfo(from, to, join))
        } else {
            emptyList()
        }
    }

    fun getNextPosition(): String? =
        if (isOnSelection) {
            val maxSelected = _onEditPositions.value.maxOrNull() ?: return null
            getStory(maxSelected)?.nextPosition
        } else {
            val currentPos = currentStory.value.selection.position
            getStory(currentPos)?.nextPosition
        }

    fun positionAfterSelection(): String? =
        if (isOnSelection) getNextPosition() else null

    fun lastPosition(): String = getStories().lastKeyOrNull() ?: Rank.FIRST

    /**
     * Creates a new document and replaces the story at [position] with a link to it. Without a
     * position, the last selected story is used.
     */
    suspend fun addLinkToDocument(position: String? = null) {
        if (!isEditable) return
        if (documentRepository == null) return

        val lastSelection = position ?: _onEditPositions.value.maxOrNull() ?: return

        val text = getStories()[lastSelection]?.text?.let {
            it.take(max(it.length, 30))
        } ?: ""

        val (documentInfo, state) = writeopiaManager.newDocument(
            parentFolder = getDocument().parentId
        )

        val stories = state.stories.mapValues { (_, story) ->
            if (story.type == StoryTypes.TITLE.type) {
                story.copy(text = text)
            } else {
                story
            }
        }

        val newDocument =
            documentInfo.document(
                userRepository?.getWorkspace()?.id ?: ""
            ).copy(content = stories, title = text)

        documentRepository.saveDocument(newDocument)
        documentRepository.refreshDocuments()

        _currentStory.value = writeopiaManager.addDocumentLink(
            storyState = _currentStory.value,
            position = lastSelection,
            documentId = newDocument.id,
            text = text
        )
    }

    fun getSelectedStories(): List<StoryStep> {
        val stories = getStories()
        return _onEditPositions.value
            .sorted()
            .mapNotNull { position ->
                stories[position]
            }
    }

    private fun getSelectedStoriesWithPosition(): List<Pair<String, StoryStep>> {
        val stories = getStories()
        return _onEditPositions.value
            .sorted()
            .mapNotNull { position ->
                val story = stories[position]
                if (story != null) position to story else null
            }
    }

    /**
     * This method returns the current text being selected. It can bet the result of a selection of
     * multiple lines, a selection inside a line or just the text of the file that it being edited.
     */
    fun getCurrentText(): String? = getCurrentSelectionText() ?: currentLineText()

    fun getCurrentSelectionText(): String? =
        getSelectionInfo().takeIf { it.isNotEmpty() }
            ?.first()
            ?.text

    /**
     * Accepts the AI answer at [position]: its text stays, as regular lines read as Markdown,
     * and the answer itself goes away for good. The lines are new steps and the answer is
     * deleted like any other step, with a tombstone, so no save nor sync brings it back once
     * accepted. The deletion and the lines go to the trackers in one edit.
     */
    fun acceptStoryStep(position: String) {
        if (!isEditable) return
        val answer = getStory(position) ?: return
        val text = answer.text ?: ""

        backStackManager.addState(_currentStory.value)

        coroutineScope.launch(dispatcher) {
            val current = _currentStory.value
            if (current.stories[position]?.id != answer.id) return@launch

            // The text takes the answer's place as a new step: the answer's id is deleted below,
            // and a step with it would never be saved again.
            val accepted = answer.copy(
                id = GenerateId.generate(),
                localId = GenerateId.generate(),
                type = StoryTypes.TEXT.type,
                ephemeral = false,
            )
            // Not saved on its own: the edit below carries it, with the deletion of the answer.
            _currentStory.value = current.copy(
                stories = current.stories.toSortedMutableMap().apply { this[position] = accepted },
                lastEdit = LastEdit.Nothing,
            )

            val edited = if (text.contains("\n")) {
                breakLine(Action.LineBreak(accepted, position), processCommands = true)
            } else {
                commandHandler.handleCommand(text, accepted, position)
                listOf(position to accepted)
            }

            // The lines as they are now: the commands may have changed their type and text.
            val stories = _currentStory.value.stories
            val lines = (edited.map { (editedPosition, _) -> editedPosition } + position)
                .distinct()
                .sorted()
                .mapNotNull { linePosition -> stories[linePosition]?.let { step -> linePosition to step } }

            _currentStory.value = _currentStory.value.copy(
                // An answer still ephemeral (its stream never ended) was never saved: nothing to delete.
                lastEdit = if (answer.ephemeral) {
                    LastEdit.BulkEdition(lines)
                } else {
                    LastEdit.BulkDeleteEdition(listOf(answer.id), lines)
                }
            )
        }
    }

    fun addTitle(tag: Tag) {
        if (!isEditable) return

        if (isOnSelection) {
            // One bulk edition, so the tracker saves every selected line. Emitting one line
            // edition per step loses lines: the state flow keeps only the latest one while a
            // save is running.
            val change = getSelectedStoriesWithPosition().map { (position, storyStep) ->
                Action.StoryStateChange(titledStory(storyStep, tag), position)
            }
            if (change.isEmpty()) return

            trackState()
            val current = _currentStory.value
            _currentStory.value = writeopiaManager.bulkChangeStoryState(current, change)
                .copy(focus = current.focus, selection = current.selection)
            // The lines changed outside changeStoryState: the same single change must apply again.
            lastStateChange = null
        } else {
            val position = currentPosition() ?: return
            val currentStory = getStory(position)

            currentStory?.let { storyStep ->
                changeStoryState(Action.StoryStateChange(titledStory(storyStep, tag), position))
            }
        }
    }

    private fun titledStory(storyStep: StoryStep, tag: Tag): StoryStep {
        val shouldRemove = storyStep.tags.any { it.tag == tag }

        val newTags = storyStep.tags
            .filterNotTo(mutableSetOf()) { it.tag.isTitle() }
            .apply {
                if (!shouldRemove) {
                    add(TagInfo(tag))
                }
            }

        return storyStep.copy(tags = newTags)
    }

    private suspend fun getUserId(): String =
        userRepository?.getUser()?.id ?: WriteopiaUser.disconnectedUser().id

    /**
     * Moves the focus to the next available [StoryStep] if it can't find a step to focus, it
     * creates a new [StoryStep] at the end of the document. The cursor is positioned in the same
     * place that is was in the previous line.
     *
     * @param position Double
     * @param cursor Int
     */
    private fun nextFocusOrCreate(position: String, cursor: Int) {
        if (!isEditable) return
        coroutineScope.launch(dispatcher) {
            _currentStory.value =
                writeopiaManager.nextFocus(position, cursor, _currentStory.value)
        }
    }

    private fun currentLineText(): String? {
        val selection = currentStory.value.selection
        val (start, end) = selection.sortedPositions()

        return when {
            start == end -> getStory(selection.position)?.text

            else -> {
                val text = getStory(selection.position)?.text

                text?.subSequence(
                    start,
                    min(text.length, end)
                )
                    ?.toString()
                    ?: text
            }
        }
    }

    /**
     * Move the focus to the previous line that accepts text edition.
     * The cursor is positioned in the same place that is was in the previous line.
     */
    private fun previousFocus(position: String, cursor: Int) {
        coroutineScope.launch(dispatcher) {
            writeopiaManager.previousTextStory(getStories(), position)
                ?.let { (step, newPosition) ->
                    val storyState = _currentStory.value
                    val mutable = storyState.stories.toSortedMutableMap()

                    mutable[newPosition] = step

                    _currentStory.value = storyState.copy(
                        focus = newPosition,
                        selection = Selection.fromLastLine(cursor, newPosition),
                        stories = mutable
                    )
                }
        }
    }

    private fun toggleStateForStories(onEdit: Set<String>, storyTypes: StoryTypes) {
        if (!isEditable) return
        val currentStories = currentStory.value.stories

        trackState()

        val change = onEdit.map { position -> position to currentStories[position] }
            .filter { (_, story) -> story != null && !permanentTypes.contains(story.type.number) }
            .map { (position, story) ->
                val newType = if (story?.type == storyTypes.type) {
                    StoryTypes.TEXT
                } else {
                    storyTypes
                }

                position to TypeInfo(newType.type)
            }

        _currentStory.value =
            writeopiaManager.bulkChangeStoryType(_currentStory.value, change = change)
    }

    private fun toggleTagForStories(
        onEdit: Set<String>,
        tag: TagInfo,
        currentStories: Map<String, StoryStep> = currentStory.value.stories
    ) {
        if (!isEditable) return
        trackState()

        val change = onEdit.map { position -> position to currentStories[position] }
            .filter { (_, story) -> story != null && !permanentTypes.contains(story.type.number) }
            .map { (position, story) ->
                val currentTags = story!!.tags
                val newTags = if (currentTags.contains(tag)) {
                    currentTags.filterNot { it.tag == tag.tag }.toSet()
                } else {
                    currentTags + tag
                }

                Action.StoryStateChange(story.copy(tags = newTags), position)
            }

        _currentStory.value = writeopiaManager.bulkChangeStoryState(_currentStory.value, change)
    }

    private fun changeCurrentStoryType(storyTypes: StoryTypes) {
        if (!isEditable) return
        changeCurrentStoryState { storyStep ->
            val newType = if (storyStep.type == storyTypes.type) {
                StoryTypes.TEXT.type
            } else {
                storyTypes.type
            }

            if (!permanentTypes.contains(storyStep.type.number)) {
                storyStep.copy(type = newType)
            } else {
                storyStep
            }
        }
    }

    private fun changeCurrentStoryState(stateChange: (StoryStep) -> StoryStep) {
        if (!isEditable) return
        val currentStepEntry = currentFocus()

        if (currentStepEntry != null) {
            changeStoryState(
                Action.StoryStateChange(
                    stateChange(currentStepEntry.second),
                    currentStepEntry.first
                )
            )
        }
    }

    private fun currentFocus(): Pair<String, StoryStep>? {
        val currentFocus = currentStory.value.focus

        return _currentStory.value
            .stories
            .entries
            .find { (position, _) -> position == currentFocus }
            ?.let { (position, step) ->
                position to step
            }
    }

    private fun changeStoryStateAndTrackIt(
        stateChange: Action.StoryStateChange,
        trackIt: Boolean = true
    ) {
        if (!isEditable) return
        if (lastStateChange == stateChange) return
        lastStateChange = stateChange

        val currentFocusPosition = _currentStory.value.focus
        val currentSelectionPosition = _currentStory.value.selection.position

        writeopiaManager.changeStoryState(stateChange, _currentStory.value).let { state ->
            if (trackIt) {
                backStackManager.addState(_currentStory.value)
            }

            val newSelectionPosition = if (stateChange.preserveFocus) {
                currentSelectionPosition
            } else {
                stateChange.position
            }

            val newFocus = if (stateChange.preserveFocus) {
                currentFocusPosition
            } else {
                state.focus
            }

            _currentStory.value = state.copy(
                focus = newFocus,
                selection = Selection(
                    stateChange.selectionStart ?: state.selection.start,
                    stateChange.selectionEnd ?: state.selection.end,
                    newSelectionPosition
                )
            )
            cleanupOrphanCommentConversations()
        }
    }

    fun getStory(position: String): StoryStep? = _currentStory.value.stories[position]

    /**
     * Creates a new spreadsheet with one initial row containing the specified number of empty cells.
     * If the cursor is on the title, the spreadsheet is added after the title instead of replacing it.
     *
     * @param columnCount The number of columns in the spreadsheet
     */
    fun addSpreadsheet(columnCount: Int) {
        if (!isEditable) return

        val position = currentPosition() ?: return
        val currentStep = _currentStory.value.stories[position]

        val (targetPosition, insertMode) = getTitleProtectedPosition(
            position,
            currentStep,
            explicitPosition = false
        )

        backStackManager.addState(_currentStory.value)
        _currentStory.value = writeopiaManager.createSpreadsheet(
            _currentStory.value,
            targetPosition,
            columnCount,
            insertMode = insertMode
        )
    }

    /**
     * Inserts an empty spreadsheet at [position], pushing the stories from there down.
     *
     * @return The ID of the new spreadsheet, or null when the document can't be edited.
     */
    fun insertSpreadsheet(position: String, columnCount: Int, rowCount: Int = 3): String? {
        if (!isEditable) return null

        backStackManager.addState(_currentStory.value)
        val before = getStories().values.mapTo(mutableSetOf()) { step -> step.id }
        val newState = writeopiaManager.createSpreadsheet(
            _currentStory.value,
            position,
            columnCount,
            rowCount = rowCount,
            insertMode = true
        )
        _currentStory.value = newState

        return newState.stories.values
            .firstOrNull { step -> step.type == StoryTypes.SPREADSHEET.type && step.id !in before }
            ?.id
    }

    /**
     * Updates the text of a specific cell within a spreadsheet.
     *
     * @param spreadsheetId The ID of the spreadsheet StoryStep
     * @param rowIndex The index of the row (0-based)
     * @param cellIndex The index of the cell within the row (0-based)
     * @param newText The new text for the cell
     */
    fun updateSpreadsheetCell(spreadsheetId: String, rowIndex: Int, cellIndex: Int, newText: String) {
        if (!isEditable) return

        _currentStory.value = writeopiaManager.updateSpreadsheetCell(
            _currentStory.value,
            spreadsheetId,
            rowIndex,
            cellIndex,
            newText
        )
    }

    /**
     * Adds a new row to a spreadsheet with the same number of columns as existing rows.
     *
     * @param spreadsheetId The ID of the spreadsheet StoryStep
     */
    fun addSpreadsheetRow(spreadsheetId: String) {
        if (!isEditable) return

        backStackManager.addState(_currentStory.value)
        _currentStory.value = writeopiaManager.addSpreadsheetRow(
            _currentStory.value,
            spreadsheetId
        )
    }

    /**
     * Adds a new column to a spreadsheet by adding a cell to each row.
     *
     * @param spreadsheetId The ID of the spreadsheet StoryStep
     */
    fun addSpreadsheetColumn(spreadsheetId: String) {
        if (!isEditable) return

        backStackManager.addState(_currentStory.value)
        _currentStory.value = writeopiaManager.addSpreadsheetColumn(
            _currentStory.value,
            spreadsheetId
        )
    }

    /**
     * Updates the width of a specific column in a spreadsheet.
     *
     * @param spreadsheetId The ID of the spreadsheet StoryStep
     * @param columnIndex The index of the column to update (0-based)
     * @param newWidth The new width for the column in dp
     */
    fun updateSpreadsheetColumnWidth(spreadsheetId: String, columnIndex: Int, newWidth: Int) {
        if (!isEditable) return

        _currentStory.value = writeopiaManager.updateSpreadsheetColumnWidth(
            _currentStory.value,
            spreadsheetId,
            columnIndex,
            newWidth
        )
    }

    fun deleteSpreadsheetRow(spreadsheetId: String, rowIndex: Int) {
        if (!isEditable) return
        backStackManager.addState(_currentStory.value)
        _currentStory.value = writeopiaManager.deleteSpreadsheetRow(
            _currentStory.value,
            spreadsheetId,
            rowIndex
        )
    }

    fun deleteSpreadsheetColumn(spreadsheetId: String, columnIndex: Int) {
        if (!isEditable) return
        backStackManager.addState(_currentStory.value)
        _currentStory.value = writeopiaManager.deleteSpreadsheetColumn(
            _currentStory.value,
            spreadsheetId,
            columnIndex
        )
    }

    fun addSpreadsheetRowAt(spreadsheetId: String, rowIndex: Int) {
        if (!isEditable) return
        backStackManager.addState(_currentStory.value)
        _currentStory.value = writeopiaManager.addSpreadsheetRowAt(
            _currentStory.value,
            spreadsheetId,
            rowIndex
        )
    }

    fun addSpreadsheetColumnAt(spreadsheetId: String, columnIndex: Int) {
        if (!isEditable) return
        backStackManager.addState(_currentStory.value)
        _currentStory.value = writeopiaManager.addSpreadsheetColumnAt(
            _currentStory.value,
            spreadsheetId,
            columnIndex
        )
    }

    fun moveSpreadsheetRow(spreadsheetId: String, fromIndex: Int, toIndex: Int) {
        if (!isEditable) return
        backStackManager.addState(_currentStory.value)
        _currentStory.value = writeopiaManager.moveSpreadsheetRow(
            _currentStory.value,
            spreadsheetId,
            fromIndex,
            toIndex
        )
    }

    fun moveSpreadsheetColumn(spreadsheetId: String, fromIndex: Int, toIndex: Int) {
        if (!isEditable) return
        backStackManager.addState(_currentStory.value)
        _currentStory.value = writeopiaManager.moveSpreadsheetColumn(
            _currentStory.value,
            spreadsheetId,
            fromIndex,
            toIndex
        )
    }

    /**
     * Disables drag selection box (e.g., during column resizing in spreadsheet).
     */
    fun disableDragSelection() {
        textSelectionActiveState.value = true
    }

    /**
     * Re-enables drag selection box.
     */
    fun enableDragSelection() {
        textSelectionActiveState.value = false
    }

    private fun getStories() = _currentStory.value.stories

    private fun currentPosition(): String? =
        _currentStory.value.focus ?: _currentStory.value.selection.position

    private fun getCurrentStory(): StoryStep? = currentPosition()?.let(::getStory)

    private fun selectAll() {
        keyboardSelectionAnchor = null
        _onEditPositions.value = getStories().keys - setOf(Rank.at(0))
    }

    private fun parseDocument(
        info: DocumentInfo,
        state: StoryState,
        conversations: Map<String, List<Comment>>,
        tagIds: Set<String> = emptySet(),
    ): Document {
        val titleFromContent = state.stories.values.firstOrNull { storyStep ->
            // Todo: Change the type of change to allow different types. The client code should decide what is a title
            // It is also interesting to inv
            storyStep.type == StoryTypes.TITLE.type
        }?.text

        return Document(
            id = info.id,
            title = titleFromContent ?: info.title,
            content = state.stories,
            createdAt = info.createdAt,
            lastUpdatedAt = info.lastUpdatedAt,
            lastSyncedAt = info.lastSyncedAt,
            workspaceId = localUserId ?: "disconnected_user",
            parentId = info.parentId,
            isLocked = info.isLocked,
            icon = info.icon,
            commentConversations = conversations,
            tagIds = tagIds,
        )
    }

    private fun selectedStories(): List<StoryStep> = _onEditPositions.value.mapNotNull { getStory(it) }

    companion object {
        fun create(
            writeopiaManager: WriteopiaManager,
            dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
            documentRepository: DocumentRepository? = null,
            selectionState: StateFlow<Boolean> = MutableStateFlow(false),
            keyboardEventFlow: Flow<KeyboardEvent?> = MutableStateFlow(null),
            stepsNormalizer: UnitsNormalizationMap =
                StepsMapNormalizationBuilder.reduceNormalizations {
                    defaultNormalizers()
                },
            coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext),
            backStackManager: SnapshotBackstackManager = SnapshotBackstackManager(),
            userRepository: UserRepository? = null,
            imageUploader: ImageUploader? = null,
            textSelectionActiveState: MutableStateFlow<Boolean> = MutableStateFlow(false)
        ) = WriteopiaStateManager(
            stepsNormalizer,
            dispatcher,
            coroutineScope,
            backStackManager,
            userRepository,
            writeopiaManager,
            selectionState,
            keyboardEventFlow.filterNotNull(),
            documentRepository,
            setOf("jpg", "jpeg", "png"),
            StepsModifier::modify,
            imageUploader = imageUploader,
            textSelectionActiveState = textSelectionActiveState
        )
    }
}

/**
 * A region of the document being rewritten: from [from] (inclusive) up to [toExclusive]
 * (exclusive), or to the end of the document when null.
 */
data class EditRegion(val from: String, val toExclusive: String?)

private class LineBreakCommand(val text: String, val position: String, val time: Instant)

/** The steps an edit changed or created, by position; none for the edits that carry no steps. */
private fun LastEdit.editedSteps(): List<Pair<String, StoryStep>> =
    when (this) {
        is LastEdit.LineEdition -> listOf(position to storyStep)
        is LastEdit.InfoEdition -> listOf(position to storyStep)
        is LastEdit.LineBreakEdition -> listOf(originalStep, newStep)
        is LastEdit.BulkEdition -> steps
        is LastEdit.EraseEdition -> listOf(updatedStep)
        is LastEdit.BulkDeleteEdition -> updatedSteps
        is LastEdit.DeleteEdition, LastEdit.Metadata, LastEdit.Nothing, LastEdit.Whole -> emptyList()
    }

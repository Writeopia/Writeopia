package io.writeopia.ui.manager

import io.writeopia.sdk.manager.DocumentTracker
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
import io.writeopia.sdk.normalization.builder.StepsMapNormalizationBuilder
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.repository.UserRepository
import io.writeopia.sdk.sharededition.SharedEditionManager
import io.writeopia.sdk.utils.alias.UnitsNormalizationMap
import io.writeopia.sdk.utils.extensions.toEditState
import io.writeopia.ui.backstack.BackstackHandler
import io.writeopia.ui.backstack.BackstackInform
import io.writeopia.ui.backstack.SnapshotBackstackManager
import io.writeopia.ui.edition.TextCommandHandler
import io.writeopia.ui.keyboard.KeyboardEvent
import io.writeopia.ui.model.DrawState
import io.writeopia.ui.model.DrawStory
import io.writeopia.ui.model.SelectionInfo
import io.writeopia.ui.model.TextInput
import io.writeopia.ui.modifiers.StepsModifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.max
import kotlin.math.min

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
    private val drawStateModify: (List<DrawStory>, Int) -> (List<DrawStory>) = StepsModifier::modify,
    private val permanentTypes: Set<Int> = setOf(StoryTypes.TITLE.type.number),
    private val listTypes: Set<Int> = setOf(
        StoryTypes.CHECK_ITEM.type.number,
        StoryTypes.UNORDERED_LIST_ITEM.type.number,
    )
) : BackstackHandler, BackstackInform by backStackManager {

    private val selectionBuffer: EventBuffer<Pair<Boolean, Int>> = EventBuffer(coroutineScope)

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
                                if (_positionsOnEdit.value.isNotEmpty()) {
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
                                    val position = currentPosition()

                                    if (position != null) {
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

    private val initialContent: Map<Int, StoryStep> =
        mapOf(0 to StoryStep(text = "", type = StoryTypes.TITLE.type))

    private var localUserId: String? = null

    private val _dragPosition = MutableStateFlow(-1)
    private val _isDragging = MutableStateFlow(false)

    private val dragRealPosition = combine(_dragPosition, _isDragging) { position, isDragging ->
        if (isDragging) position else -1
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

    private val isEditable: Boolean
        get() = !_documentInfo.value.isLocked

    val documentInfo: StateFlow<DocumentInfo> = _documentInfo.asStateFlow()

    private val _positionsOnEdit = MutableStateFlow(setOf<Int>())
    val onEditPositions = _positionsOnEdit.asStateFlow()

    private var sharedEditionManager: SharedEditionManager? = null

    val currentStory: StateFlow<StoryState> = _currentStory.asStateFlow()

    val currentDocument: StateFlow<Document?> =
        combine(_documentInfo, _currentStory) { info, state ->
            parseDocument(info, state)
        }.stateIn(coroutineScope, SharingStarted.Lazily, null)

    private val _documentEditionState: Flow<Pair<StoryState, DocumentInfo>> =
        combine(currentStory, _documentInfo, ::Pair)

    val toDraw: Flow<DrawState> =
        combine(
            _positionsOnEdit,
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
                .toList()
                .let { drawStories -> drawStateModify(drawStories, dragPosition).drop(1) }

            DrawState(toDrawStories, focus)
        }

    private var _initialized = false

    val isOnSelection: Boolean
        get() = _positionsOnEdit.value.isNotEmpty()

    /**
     * Saves the document automatically as it is changed. It uses the [DocumentTracker] passed
     * in the constructor of [WriteopiaStateManager]
     */
    fun saveOnStoryChanges(documentTracker: DocumentTracker) {
        coroutineScope.launch(dispatcher) {
            documentTracker.saveOnStoryChanges(_documentEditionState, getUserId())
        }
    }

    fun getDocument(): Document =
        parseDocument(_documentInfo.value, _currentStory.value)

    fun liveSync(sharedEditionManager: SharedEditionManager) {
        coroutineScope.launch(dispatcher) {
            sharedEditionManager.startLiveEdition(
                inFlow = _documentEditionState,
                outFlow = _currentStory,
            )
        }
    }

    fun isInitialized(): Boolean = _initialized

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

        _initialized = true
        val (documentInfo, storyState) = writeopiaManager.newDocument(
            documentId,
            title,
            parentFolder = parentFolder
        )

        backStackManager.addState(storyState)

        _documentInfo.value = documentInfo
        _currentStory.value = storyState
    }

    /**
     * Initializes a document passed as a parameter. This method should be used when you would like
     * to load a document from a database and start editing it, instead of creating something new.
     *
     * @param document [Document]
     */
    fun loadDocument(document: Document) {
        if (isInitialized()) return

        _initialized = true

        val stories = document.content
        val state =
            StoryState(stepsNormalizer(stories.toEditState()), LastEdit.Nothing, null)

        _currentStory.value = state
        backStackManager.addState(state)
        val normalized = stepsNormalizer(stories.toEditState())

        _currentStory.value = StoryState(normalized, LastEdit.Nothing)
        _documentInfo.value = document.info()
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

        if (_positionsOnEdit.value.contains(fixedMove.positionFrom)) {
            val bulkMove = Action.BulkMove(
                storyStep = selectedStories(),
                positionFrom = _positionsOnEdit.value,
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
        val onEdit = _positionsOnEdit.value

        if (onEdit.isNotEmpty()) {
            toggleStateForStories(onEdit, StoryTypes.CHECK_ITEM)
        } else {
            changeCurrentStoryType(StoryTypes.CHECK_ITEM)
        }
    }

    /**
     * Click lister when user clicks in the menu to add a list item
     */
    fun onListItemClicked() {
        if (!isEditable) return
        val onEdit = _positionsOnEdit.value

        if (onEdit.isNotEmpty()) {
            toggleStateForStories(onEdit, StoryTypes.UNORDERED_LIST_ITEM)
        } else {
            changeCurrentStoryType(StoryTypes.UNORDERED_LIST_ITEM)
        }
    }

    fun toggleHighLightBlock() {
        if (!isEditable) return
        val onEdit = _positionsOnEdit.value

        if (onEdit.isNotEmpty()) {
            toggleTagForStories(onEdit, TagInfo(Tag.HIGH_LIGHT_BLOCK))
        } else {
            currentFocus()?.first?.let { currentPosition ->
                toggleTagForStories(setOf(currentPosition), TagInfo(Tag.HIGH_LIGHT_BLOCK))
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
        val newStories = getStories().mapValues { (_, storyStep) ->
            if (storyStep.tags.contains(TagInfo(Tag.AI_SUGGESTION))) {
                storyStep.copy(
                    tags = storyStep.tags.filterNot { tagInfo ->
                        tagInfo.tag == Tag.AI_SUGGESTION || tagInfo.tag == Tag.FIRST_AI_SUGGESTION
                    }.toSet(),
                    ephemeral = false
                )
            } else {
                storyStep
            }
        }

        _currentStory.value =
            _currentStory.value.copy(stories = newStories, lastEdit = LastEdit.Whole)
    }

    fun toggleTagForPosition(position: Int, tag: TagInfo, commandInfo: CommandInfo? = null) {
        if (!isEditable) return
        val story = getStory(position)
        val storyText = story?.text

        val newState = if (commandInfo != null && storyText != null) {
            val commandText = commandInfo.command.commandText.trim()

            val newText = if (
                commandInfo.commandTrigger == CommandTrigger.WRITTEN &&
                story.text?.trim()?.startsWith(commandText.trim()) == true
            ) {
                storyText.subSequence(commandText.length, storyText.length).toString()
            } else {
                storyText
            }

            val mutable = currentStory.value.stories.toMutableMap()
            mutable[position] = story.copy(text = newText)
            mutable
        } else {
            currentStory.value.stories
        }

        toggleTagForStories(setOf(position), tag, newState)
    }

    /**
     * Click lister when user clicks in the menu to add a code block
     */
    fun onCodeBlockClicked() {
        // Todo: This change needs to take into account that code block is a multi line block and then
        // it needs to be have the relation N -> 1 and 1 -> N when transforming.
//        changeCurrentStoryType(StoryTypes.CODE_BLOCK)
    }

    fun toggleCollapseItem(position: Int) {
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

        changeStoryStateAndTrackIt(stateChange, trackIt = false)
    }

    /**
     * Changes the story type. The type of a messages changes without changing the content of it.
     * Commands normally change the type of a message. From a message to a unordered list item, for
     * example.
     *
     * @param position Int
     * @param typeInfo [TypeInfo]
     * @param commandInfo [CommandInfo]
     */
    fun changeStoryType(position: Int, typeInfo: TypeInfo, commandInfo: CommandInfo?) {
        if (!isEditable) return
        if (isOnSelection) {
            clearSelection()
        }

        _currentStory.value =
            writeopiaManager.changeStoryType(position, typeInfo, commandInfo, _currentStory.value)

        if (listTypes.contains(typeInfo.storyType.number)) {
            coroutineScope.launch {
                val newState = writeopiaManager.generateSuggestionsList(
                    storyState = { _currentStory.value },
                    storyType = typeInfo.storyType,
                    position = position + 1,
                    context = getDocumentText(),
                    userId = getUserId(),
                )

                _currentStory.value = newState
            }
        }
    }

    fun removeTags(position: Int) {
        if (!isEditable) return
        _currentStory.value =
            writeopiaManager.removeTags(position, _currentStory.value)
    }

    /**
     * Creates a line break. When a line break happens, the line it divided into two [StoryStep]s
     * of the same, if possible, or the next line will be a Message.
     *
     * @param lineBreak [Action.LineBreak]
     */
    fun onLineBreak(lineBreak: Action.LineBreak) {
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
            val state = _currentStory.value
            val story = getStory(position = lineBreak.position)

            val expanded: StoryState = if (story?.tags?.any { it.tag.isTitle() } == true) {
                writeopiaManager.expandItem(state, lineBreak.position)
            } else {
                state
            }

            writeopiaManager.onLineBreak(lineBreak, expanded).let { (newPosition, newState) ->
                // Todo: Fix this when the inner position are completed
                //  backStackManager.addAction(BackstackAction.Add(newStory, newPosition))
                _currentStory.value = newState.copy(selection = Selection.start())
                _scrollToPosition.value = newPosition
            }
        }
    }

    fun onFocusChange(position: Int, hasFocus: Boolean) {
        if (!hasFocus) return
        val story = currentStory.value

        _currentStory.value = story.copy(focus = position)
    }

    private fun selected(isSelected: Boolean, position: Int) {
        if (!isEditable) return
        if (_currentStory.value.stories[position] != null) {
            if (isSelected) {
                _positionsOnEdit.value += position
            } else {
                _positionsOnEdit.value -= position
            }
        }
    }

    /**
     * Add a [StoryStep] of a position into the selection list. Selected content can be used to
     * perform bulk actions, like bulk edition and bulk deletion.
     */
    fun onSelected(isSelected: Boolean, position: Int) {
        if (!isEditable) return
        selectionBuffer.send(isSelected to position)
    }

    fun onSectionSelected(position: Int) {
        if (!isEditable) return
        val isSelected = _positionsOnEdit.value.contains(position)
        val stories = getStories()

        val lastPosition = getStories().asSequence()
            .filter { (posi, _) -> posi > position }
            .find { (_, story) -> story.tags.any { it.tag.isTitle() } }
            ?.key
            ?: (stories.size - 1)

        val newSelected = buildSet {
            for (i in position..lastPosition) {
                add(i)
            }
        }

        if (isSelected) {
            _positionsOnEdit.value -= newSelected
        } else {
            _positionsOnEdit.value += newSelected
        }
    }

    fun toggleSelection(position: Int) {
        if (!isEditable) return
        onSelected(!_positionsOnEdit.value.contains(position), position)
    }

    /**
     * A click at the end of the document. The focus should be moved to the first [StoryStep] that
     * can receive the focus, from last to first.
     */
    fun clickAtTheEnd() {
        val stories = _currentStory.value.stories
        val lastPosition = stories.size - 1
        val lastContentStory = stories[lastPosition]

        val newState = if (lastContentStory?.type == StoryTypes.TEXT.type) {
            val newStoriesState = stories.toMutableMap().apply {
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
            val newLastMessage = StoryStep(type = StoryTypes.TEXT.type)
            val newStories = stories + mapOf(stories.size to newLastMessage)
            val cursor = newLastMessage.text?.length ?: 0

            StoryState(
                newStories,
                LastEdit.Whole,
                lastPosition,
                selection = Selection.fromPosition(
                    cursorPosition = cursor,
                    stepPosition = lastPosition
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
                val stories = state.stories.mapValues { (_, story) ->
                    story.copy(localId = GenerateId.generate())
                }
                _currentStory.value = state.copy(stories = stories)
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
                val stories = state.stories.mapValues { (_, story) ->
                    story.copy(localId = GenerateId.generate())
                }
                _currentStory.value = state.copy(stories = stories)
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
            writeopiaManager.onDelete(deleteStory, _currentStory.value)?.let { newState ->
                _currentStory.value = newState
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
        }
    }

    /**
     * Deletes the whole selection. All [StoryStep] in the selection will be deleted.
     */
    fun deleteSelection() {
        if (!isEditable) return
        coroutineScope.launch(dispatcher) {
            val (newStories, _) = writeopiaManager.bulkDelete(
                _positionsOnEdit.value,
                _currentStory.value.stories
            )

            _positionsOnEdit.value = emptySet()

            backStackManager.addState(_currentStory.value)
            val state = _currentStory.value.copy(stories = newStories, lastEdit = LastEdit.Whole)
            _currentStory.value = state
        }
    }

    fun onDragHover(position: Int) {
        _dragPosition.value = position
    }

    fun onDragStart() {
        _isDragging.value = true
    }

    fun onDragStop() {
        coroutineScope.launch {
            // It is necessary to delay the stop dragging event to wait for the move request to
            // be received.
            delay(100)
            _isDragging.value = false
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

    fun moveToNext(cursor: Int, positions: Int = 1) {
        val lastIndex = _currentStory.value.stories.size - 1

        val focusPosition = currentFocus()?.let { (position, _) -> position } ?: 0
        nextFocusOrCreate(min(focusPosition + positions, lastIndex), cursor)
    }

    fun moveToPrevious(cursor: Int, positions: Int = 1) {
        val focusPosition = currentFocus()?.let { (position, _) -> position } ?: 0
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

    fun toggleSpan(span: Span) {
        if (isEditable) {
            val onEdit = _positionsOnEdit.value

            if (onEdit.isNotEmpty()) {
                _currentStory.value =
                    writeopiaManager.addSpanToStories(_currentStory.value, onEdit, span)
            } else {
                val selection = currentStory.value.selection

                val (start, end) = selection.sortedPositions()

                _currentStory.value = writeopiaManager.addSpan(
                    _currentStory.value,
                    selection.position,
                    SpanInfo.create(start, end, span)
                )
            }
        }
    }

    fun addImage(imagePath: String, position: Int? = null) {
        if (!isEditable) return
        (position ?: currentPosition())?.let { pos ->
            val story = getStory(pos)

            if (story != null) {
                if (position == null) {
                    val stateChange = Action.StoryStateChange(
                        story.copy(type = StoryTypes.IMAGE.type, path = imagePath),
                        pos
                    )

                    changeStoryStateAndTrackIt(stateChange)
                } else {
                    addAtPosition(StoryStep(type = StoryTypes.IMAGE.type, path = imagePath), pos)
                }
            }
        }
    }

    /**
     * Adds a story in a position.
     */
    private fun addAtPosition(storyStep: StoryStep, position: Int) {
        if (!isEditable) return
        _currentStory.value = writeopiaManager.addAtPosition(
            _currentStory.value,
            storyStep,
            position
        )
    }

    fun loadingAtPosition(position: Int) {
        if (StoryTypes.LOADING.type == getStory(position)?.type) return

        addAtPosition(
            StoryStep(type = StoryTypes.LOADING.type, ephemeral = true),
            position = position
        )
    }

    fun removeAtPosition(position: Int) {
        if (!isEditable) return
        _currentStory.value = writeopiaManager.removeAtPosition(
            _currentStory.value,
            position
        )
    }

    fun handleTextInput(
        input: TextInput,
        position: Int,
        lineBreakByContent: Boolean,
        trackIt: Boolean = true
    ) {
        if (!isEditable) return
        val text = input.text
        val step = _currentStory.value.stories[position] ?: return

        if (lineBreakByContent && text.contains("\n")) {
            val newStep = step.copy(text = text)
            onLineBreak(Action.LineBreak(newStep, position))
        } else {
            val newText = text.replace("\n", "")
            val newStep = step.copy(text = newText, spans = input.spans)
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

    /**
     * Cancels the current selection.
     */
    fun clearSelection() {
        _positionsOnEdit.value = emptySet()
    }

    fun receiveExternalFiles(files: List<ExternalFile>, position: Int) {
        files
            .filter { file -> supportedImageFiles.contains(file.extension) }
            .forEach { (filePath, _) ->
                addImage(filePath, position)
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
        val selected = _positionsOnEdit.value

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

    fun getNextPosition(): Int? =
        if (isOnSelection) {
            getSelectionInfo().firstOrNull()?.to?.plus(1)
        } else {
            currentStory.value.selection.position + 1
        }

    fun positionAfterSelection(): Int? =
        if (isOnSelection) getSelectionInfo().firstOrNull()?.to?.plus(1) else null

    fun lastPosition(): Int = getStories().size

    suspend fun addLinkToDocument() {
        if (!isEditable) return
        if (documentRepository == null) return

        val lastSelection = _positionsOnEdit.value.max()

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

        val userId = getUserId()

        val newDocument =
            documentInfo.document(getUserId()).copy(content = stories, title = text)

        documentRepository.saveDocument(newDocument, userId)
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
        return _positionsOnEdit.value
            .sorted()
            .mapNotNull { position ->
                stories[position]
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

    fun acceptStoryStep(position: Int) {
        getStory(position)?.let { storyStep ->
            val text = storyStep.text

            changeStoryState(
                Action.StoryStateChange(
                    storyStep.copy(type = StoryTypes.TEXT.type),
                    position
                ),
                trackIt = false
            )
            handleTextInput(
                TextInput(text ?: ""),
                position,
                lineBreakByContent = true,
                trackIt = false
            )
            trackState()
        }
    }

    private suspend fun getUserId(): String =
        userRepository?.getUser()?.id ?: WriteopiaUser.disconnectedUser().id

    /**
     * Moves the focus to the next available [StoryStep] if it can't find a step to focus, it
     * creates a new [StoryStep] at the end of the document. The cursor is positioned in the same
     * place that is was in the previous line.
     *
     * @param position Int
     * @param cursor Int
     */
    private fun nextFocusOrCreate(position: Int, cursor: Int) {
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
    private fun previousFocus(position: Int, cursor: Int) {
        coroutineScope.launch(dispatcher) {
            writeopiaManager.previousTextStory(getStories(), position)
                ?.let { (step, newPosition) ->
                    val storyState = _currentStory.value
                    val mutable = storyState.stories.toMutableMap()

                    mutable[newPosition] = step

                    _currentStory.value = storyState.copy(
                        focus = newPosition,
                        selection = Selection.fromPosition(cursor, newPosition),
                        stories = mutable
                    )
                }
        }
    }

    private fun toggleStateForStories(onEdit: Set<Int>, storyTypes: StoryTypes) {
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
        onEdit: Set<Int>,
        tag: TagInfo,
        currentStories: Map<Int, StoryStep> = currentStory.value.stories
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

    private fun currentFocus(): Pair<Int, StoryStep>? {
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

        writeopiaManager.changeStoryState(stateChange, _currentStory.value).let { state ->
            if (trackIt) {
                backStackManager.addState(_currentStory.value)
            }

            _currentStory.value = state.copy(
                selection = Selection(
                    stateChange.selectionStart ?: 0,
                    stateChange.selectionEnd ?: 0,
                    stateChange.position
                )
            )
        }
    }

    fun getStory(position: Int): StoryStep? = _currentStory.value.stories[position]

    private fun getStories() = _currentStory.value.stories

    private fun currentPosition() = _currentStory.value.focus

    private fun getCurrentStory(): StoryStep? = currentPosition()?.let(::getStory)

    private fun selectAll() {
        _positionsOnEdit.value = getStories().keys - setOf(0)
    }

    private fun parseDocument(info: DocumentInfo, state: StoryState): Document {
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
            userId = localUserId ?: "disconnected_user",
            parentId = info.parentId,
            isLocked = info.isLocked,
            icon = info.icon
        )
    }

    private fun selectedStories(): List<StoryStep> = _positionsOnEdit.value.mapNotNull(::getStory)

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
            StepsModifier::modify
        )
    }
}

private class LineBreakCommand(val text: String, val position: Int, val time: Instant)

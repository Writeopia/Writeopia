package io.writeopia.ui.drawer.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.models.user.MentionUser
import io.writeopia.ui.drawer.SimpleTextDrawer
import io.writeopia.ui.drawer.factory.EndOfText
import io.writeopia.ui.drawer.factory.isEnterKey
import io.writeopia.ui.extensions.toTextRange
import io.writeopia.ui.model.DrawInfo
import io.writeopia.ui.model.EmptyErase
import io.writeopia.ui.model.TextInput
import io.writeopia.ui.spellcheck.SpellChecker
import io.writeopia.ui.spellcheck.SpellCheckVisualTransformation
import io.writeopia.ui.utils.AboveAnchorPositionProvider
import io.writeopia.ui.utils.Spans
import io.writeopia.ui.utils.defaultTextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.abs

/**
 * Simple message drawer intended to be used as a component for more complex drawers.
 * This class contains the logic of the basic message of the SDK. As many other drawers need some
 * text in it this Drawer can be used instead of duplicating this text logic.
 */
class TextDrawer(
    private val modifier: Modifier = Modifier,
    private val isDarkTheme: Boolean,
    private val aiExplanation: String,
    private val onKeyEvent: (KeyEvent, TextFieldValue, StoryStep, String, EmptyErase, Int, EndOfText) -> Boolean =
        { _, _, _, _, _, _, _ -> false },
    private val textStyle: @Composable (StoryStep) -> TextStyle = { defaultTextStyle(it) },
    private val onTextEdit: (TextInput, String, Boolean) -> Unit = { _, _, _ -> },
    private val lineBreakByContent: Boolean = true,
    private val enabled: Boolean = true,
    private val emptyErase: EmptyErase = EmptyErase.CHANGE_TYPE,
    override var onFocusChanged: (String, FocusState) -> Unit = { _, _ -> },
    private val selectionState: StateFlow<Boolean>,
    private val onSelectionLister: (String) -> Unit,
    private val textToolbox: @Composable (Boolean) -> Unit = {},
    private val slashCommands: List<SlashCommand> = defaultSlashCommands,
    private val slashCommandsEnabled: Boolean = true,
    private val mentionsEnabled: Boolean = true,
    private val mentionUsersState: StateFlow<List<MentionUser>> = MutableStateFlow(emptyList()),
    private val onMentionSearch: (String) -> Unit = {},
    private val onMentionDismiss: () -> Unit = {},
    private val spellChecker: SpellChecker? = null
) : SimpleTextDrawer {

    @Composable
    override fun Text(
        step: StoryStep,
        drawInfo: DrawInfo,
        interactionSource: MutableInteractionSource,
        focusRequester: FocusRequester?,
        decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit
    ) {
        var spans by remember {
            mutableStateOf(step.spans)
        }

        val isSuggestion = step.tags.contains(TagInfo(Tag.FIRST_AI_SUGGESTION))
        val primaryColor = MaterialTheme.colorScheme.primary

        var inputText by remember {
            val text = step.text

            mutableStateOf(
                TextFieldValue(
                    Spans.createStringWithSpans(text, spans, isDarkTheme, primaryColor),
                    selection = drawInfo.selection?.toTextRange(text ?: "")
                        ?: TextRange.Zero
                )
            )
        }

        var previousInputText by remember {
            mutableStateOf(inputText)
        }

        var textLayoutResult by remember {
            mutableStateOf<TextLayoutResult?>(null)
        }

        // The field keeps its selection after the focus moves to another step, so the link
        // popup must look at the focus too, not only at where the cursor was left.
        var isFocused by remember { mutableStateOf(false) }

        // Spell checking state
        var misspelledRanges by remember { mutableStateOf<List<IntRange>>(emptyList()) }

        // Debounced spell checking
        LaunchedEffect(inputText.text) {
            if (spellChecker?.isAvailable() == true) {
                delay(500) // Debounce
                misspelledRanges = spellChecker.checkSpelling(inputText.text)
            }
        }

        // Recalculate selection for last-line positioning when layout becomes available
        LaunchedEffect(textLayoutResult, drawInfo.selection) {
            val selection = drawInfo.selection
            if (selection != null && selection.fromEnd && textLayoutResult != null) {
                val newRange = selection.toTextRange(step.text ?: "", textLayoutResult)
                if (inputText.selection != newRange) {
                    val updatedInputText = inputText.copy(selection = newRange)
                    inputText = updatedInputText
                    previousInputText = updatedInputText
                }
            }
        }

        val cursorLine by remember {
            derivedStateOf {
                textLayoutResult?.getLineForOffset(inputText.selection.end)
            }
        }

        // Slash command popup state
        var showSlashCommandPopup by remember { mutableStateOf(false) }
        var slashCommandFilter by remember { mutableStateOf("") }
        var slashStartPosition by remember { mutableIntStateOf(-1) }

        // User mention popup state
        val mentionUsers by mentionUsersState.collectAsState()
        var showMentionPopup by remember { mutableStateOf(false) }
        var mentionFilter by remember { mutableStateOf("") }
        var mentionStartPosition by remember { mutableIntStateOf(-1) }
        var selectedMentionIndex by remember { mutableIntStateOf(0) }

        val filteredMentionUsers = remember(mentionUsers, mentionFilter) {
            filterMentionUsers(mentionUsers, mentionFilter)
        }

        val selectMention: (MentionUser) -> Unit = { user ->
            val currentText = inputText.text
            val safeStart = mentionStartPosition.coerceIn(0, currentText.length)
            val cursorPos = inputText.selection.start.coerceIn(safeStart, currentText.length)
            val beforeMention = currentText.take(safeStart)
            val afterCursor = currentText.substring(cursorPos)
            val mentionText = "@${user.username}"
            val insertedText = "$mentionText "
            val newText = beforeMention + insertedText + afterCursor
            val newCursorPos = safeStart + insertedText.length

            val recalculatedSpans = Spans.recalculateSpans(
                spans = spans,
                oldText = currentText,
                newText = newText,
                oldSelectionStart = safeStart,
                oldSelectionEnd = cursorPos,
                newSelectionStart = newCursorPos,
            )
            val mentionSpan = SpanInfo.create(
                start = safeStart,
                end = safeStart + mentionText.length,
                span = Span.MENTION,
                extra = user.username,
            )
            val updatedSpans = recalculatedSpans + mentionSpan
            spans = updatedSpans

            val updatedFieldValue = TextFieldValue(
                annotatedString = Spans.createStringWithSpans(newText, updatedSpans, isDarkTheme, primaryColor),
                selection = TextRange(newCursorPos),
            )
            inputText = updatedFieldValue
            previousInputText = updatedFieldValue

            showMentionPopup = false
            mentionStartPosition = -1
            mentionFilter = ""
            selectedMentionIndex = 0
            onMentionDismiss()

            onTextEdit(
                TextInput(newText, newCursorPos, newCursorPos, updatedSpans),
                drawInfo.position,
                lineBreakByContent,
            )
        }

        val selection by remember {
            derivedStateOf {
                inputText.selection
            }
        }

        val hasSelection = selection.start != selection.end

        val selectedLink by remember {
            derivedStateOf {
                if (!isFocused) {
                    null
                } else {
                    spans
                        .filter { spanInfo -> spanInfo.span == Span.LINK }
                        .firstOrNull { (start, end, _, _) ->
                            selection.start in start..end
                        }
                        ?.extra
                }
            }
        }

        val realPosition by remember {
            derivedStateOf {
                val lineStart = textLayoutResult?.multiParagraph?.getLineStart(cursorLine ?: 0)
                inputText.selection.end - (lineStart ?: 0)
            }
        }
        val isInLastLine by remember {
            derivedStateOf {
                val lineCount = textLayoutResult?.multiParagraph?.lineCount

                when {
                    lineCount == 1 -> EndOfText.SINGLE_LINE

                    cursorLine == 0 -> EndOfText.FIRST_LINE

                    (lineCount?.minus(1)) == cursorLine -> EndOfText.LAST_LINE

                    else -> EndOfText.UNKNOWN
                }
            }
        }

        val selectionState by selectionState.collectAsState()

        if (drawInfo.hasFocus()) {
            LaunchedEffect(step.localId) {
                focusRequester?.requestFocus()
            }
        }

        val coroutineScope = rememberCoroutineScope()

        Box {
            Row(horizontalArrangement = Arrangement.Center) {
                if (isSuggestion) {
                    BasicTextField(
                        state = TextFieldState(aiExplanation),
                        textStyle = textStyle(step).copy(fontStyle = FontStyle.Normal)
                    )
                }

                BasicTextField(
                    modifier = modifier
                        .let { modifierLet ->
                            if (focusRequester != null) {
                                modifierLet.focusRequester(focusRequester)
                            } else {
                                modifierLet
                            }
                        }
                        .onPreviewKeyEvent { keyEvent ->
                            if (showMentionPopup && filteredMentionUsers.isNotEmpty()) {
                                when {
                                    keyEvent.key == Key.DirectionDown -> {
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            selectedMentionIndex =
                                                (selectedMentionIndex + 1) % filteredMentionUsers.size
                                        }
                                        return@onPreviewKeyEvent true
                                    }

                                    keyEvent.key == Key.DirectionUp -> {
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            selectedMentionIndex = if (selectedMentionIndex <= 0) {
                                                filteredMentionUsers.lastIndex
                                            } else {
                                                selectedMentionIndex - 1
                                            }
                                        }
                                        return@onPreviewKeyEvent true
                                    }

                                    keyEvent.key.isEnterKey() -> {
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            val index = selectedMentionIndex.coerceIn(
                                                0,
                                                filteredMentionUsers.lastIndex
                                            )
                                            selectMention(filteredMentionUsers[index])
                                        }
                                        return@onPreviewKeyEvent true
                                    }

                                    keyEvent.key == Key.Escape -> {
                                        if (keyEvent.type == KeyEventType.KeyDown) {
                                            showMentionPopup = false
                                            mentionStartPosition = -1
                                            mentionFilter = ""
                                            selectedMentionIndex = 0
                                            onMentionDismiss()
                                        }
                                        return@onPreviewKeyEvent true
                                    }
                                }
                            }

                            onKeyEvent(
                                keyEvent,
                                inputText,
                                step,
                                drawInfo.position,
                                emptyErase,
                                realPosition,
                                isInLastLine
                            )
                        }
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
                            if (!focusState.isFocused && showMentionPopup) {
                                showMentionPopup = false
                                mentionStartPosition = -1
                                mentionFilter = ""
                                selectedMentionIndex = 0
                                onMentionDismiss()
                            }
                            onFocusChanged(drawInfo.position, focusState)
                        }
                        .testTag("MessageDrawer_${drawInfo.position}")
                        .let { modifierLet ->
                            if (selectionState) {
                                modifierLet.clickable { onSelectionLister(drawInfo.position) }
                            } else {
                                modifierLet
                            }
                        },
                    value = inputText,
                    enabled = !selectionState && !drawInfo.selectMode && enabled,
                    onTextLayout = {
                        textLayoutResult = it
                    },
                    onValueChange = { value ->
                        val previousValue = previousInputText
                        previousInputText = value

                        val start = value.selection.start
                        val end = value.selection.end
                        val previousSelection = previousValue.selection
                        val sizeDifference = value.text.length - previousValue.text.length

                        if (abs(sizeDifference) > 0 || value.text != previousValue.text) {
                            spans = Spans.recalculateSpans(
                                spans = spans,
                                oldText = previousValue.text,
                                newText = value.text,
                                oldSelectionStart = previousSelection.start,
                                oldSelectionEnd = previousSelection.end,
                                newSelectionStart = value.selection.start,
                            )
                        }

                        // Detect slash command
                        if (slashCommandsEnabled) {
                            val text = value.text
                            val cursorPos = value.selection.start

                            if (sizeDifference > 0) {
                                // Character was added
                                val addedChar =
                                    if (cursorPos > 0) text.getOrNull(cursorPos - 1) else null

                                if (addedChar == '/') {
                                    // Check if '/' is at the start of the line or after a space
                                    val charBefore =
                                        if (cursorPos > 1) text.getOrNull(cursorPos - 2) else null
                                    if (charBefore == null || charBefore == ' ' || charBefore == '\n') {
                                        showSlashCommandPopup = true
                                        slashStartPosition = cursorPos - 1
                                        slashCommandFilter = ""
                                    }
                                } else if (showSlashCommandPopup && slashStartPosition >= 0) {
                                    // Update filter with text after '/'
                                    val filterText =
                                        text.substring(slashStartPosition + 1, cursorPos)
                                    if (filterText.contains(' ') || filterText.contains('\n')) {
                                        // Space or newline typed, close popup
                                        showSlashCommandPopup = false
                                        slashStartPosition = -1
                                        slashCommandFilter = ""
                                    } else {
                                        slashCommandFilter = filterText
                                    }
                                }
                            } else if (sizeDifference < 0 && showSlashCommandPopup) {
                                // Character was deleted
                                if (cursorPos <= slashStartPosition) {
                                    // Deleted the '/' or before it
                                    showSlashCommandPopup = false
                                    slashStartPosition = -1
                                    slashCommandFilter = ""
                                } else {
                                    // Update filter
                                    slashCommandFilter =
                                        text.substring(slashStartPosition + 1, cursorPos)
                                }
                            }
                        }

                        // Detect user mention ('@')
                        if (mentionsEnabled && onMentionSearch != null) {
                            val mentionResult = detectMentionState(
                                text = value.text,
                                cursorPos = value.selection.start,
                                sizeDifference = sizeDifference,
                                isPopupCurrentlyShown = showMentionPopup,
                                currentStartPosition = mentionStartPosition,
                            )
                            when (mentionResult) {
                                is MentionDetectionResult.Active -> {
                                    showMentionPopup = true
                                    mentionStartPosition = mentionResult.startPosition
                                    mentionFilter = mentionResult.filter
                                    selectedMentionIndex = 0
                                    onMentionSearch(mentionResult.filter)
                                }

                                MentionDetectionResult.Dismissed -> {
                                    showMentionPopup = false
                                    mentionStartPosition = -1
                                    mentionFilter = ""
                                    selectedMentionIndex = 0
                                    onMentionDismiss()
                                }

                                MentionDetectionResult.Unchanged -> Unit
                            }
                        }

                        val edit = {
                            val updatedInputText = value.copy(
                                Spans.createStringWithSpans(value.text.replace("\n", ""), spans, isDarkTheme, primaryColor)
                            )
                            inputText = updatedInputText
                            if (previousInputText == value) {
                                previousInputText = updatedInputText
                            }
                        }

                        if (!showSlashCommandPopup) {
                            onTextEdit(
                                TextInput(value.text, start, end, spans),
                                drawInfo.position,
                                lineBreakByContent,
                            )
                        }

                        if (start == 0 || end == 0) {
                            coroutineScope.launch {
                                // Delay to avoid jumping to previous line too soon when erasing text
                                delay(70)
                                edit()
                            }
                        } else {
                            edit()
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    textStyle = textStyle(step),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    interactionSource = interactionSource,
                    decorationBox = decorationBox,
                    visualTransformation = if (misspelledRanges.isNotEmpty()) {
                        SpellCheckVisualTransformation(misspelledRanges)
                    } else {
                        VisualTransformation.None
                    },
                )
            }

            textToolbox(hasSelection)

            if (selectedLink != null) {
                Popup(popupPositionProvider = AboveAnchorPositionProvider) {
                    LinkHandler(selectedLink ?: "")
                }
            }

            if (showSlashCommandPopup) {
                SlashCommandPopup(
                    slashCommandFilter = slashCommandFilter,
                    slashCommands = slashCommands,
                    inputText = inputText,
                    slashStartPosition = slashStartPosition,
                    spans = spans,
                    position = drawInfo.position,
                    lineBreakByContent = lineBreakByContent,
                    onTextEdit = onTextEdit,
                    onInputTextChange = {
                        inputText = it
                        previousInputText = it
                    },
                    onDismiss = {
                        showSlashCommandPopup = false
                        slashStartPosition = -1
                        slashCommandFilter = ""
                    }
                )
            }

            val popupTargetIndex = when {
                filteredMentionUsers.isEmpty() -> 0
                else -> selectedMentionIndex.coerceIn(0, filteredMentionUsers.lastIndex)
            }

            MentionPopup(
                expanded = showMentionPopup && filteredMentionUsers.isNotEmpty(),
                users = filteredMentionUsers,
                selectedIndex = popupTargetIndex,
                onUserSelected = selectMention,
                onDismiss = {
                    showMentionPopup = false
                    mentionStartPosition = -1
                    mentionFilter = ""
                    selectedMentionIndex = 0
                    onMentionDismiss()
                },
            )
        }
    }
}

@Composable
private fun SlashCommandPopup(
    slashCommandFilter: String,
    slashCommands: List<SlashCommand>,
    inputText: TextFieldValue,
    slashStartPosition: Int,
    spans: Set<SpanInfo>,
    position: String,
    lineBreakByContent: Boolean,
    onTextEdit: (TextInput, String, Boolean) -> Unit,
    onInputTextChange: (TextFieldValue) -> Unit,
    onDismiss: () -> Unit
) {
    Popup(
        offset = IntOffset(0, 24),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = false)
    ) {
        SlashCommandPopup(
            filter = slashCommandFilter,
            commands = slashCommands,
            onCommandSelected = { command ->
                val textToInsert = command.action(position)

                if (textToInsert != null) {
                    val currentText = inputText.text
                    val cursorPos = inputText.selection.start

                    // Remove the "/" and any filter text, then insert the command result
                    val beforeSlash = currentText.take(slashStartPosition)
                    val afterCursor = currentText.substring(cursorPos)
                    val newText = beforeSlash + textToInsert + afterCursor
                    val newCursorPos = slashStartPosition + textToInsert.length

                    onInputTextChange(
                        TextFieldValue(
                            text = newText,
                            selection = TextRange(newCursorPos)
                        )
                    )

                    onTextEdit(
                        TextInput(newText, newCursorPos, newCursorPos, spans),
                        position,
                        lineBreakByContent,
                    )
                } else {
                    // Action handled everything, just clear the slash command text
                    val currentText = inputText.text
                    val cursorPos = inputText.selection.start

                    val beforeSlash = currentText.take(slashStartPosition)
                    val afterCursor = currentText.substring(cursorPos)
                    val newText = beforeSlash + afterCursor

                    onInputTextChange(
                        TextFieldValue(
                            text = newText,
                            selection = TextRange(slashStartPosition)
                        )
                    )

                    onTextEdit(
                        TextInput(newText, slashStartPosition, slashStartPosition, spans),
                        position,
                        lineBreakByContent,
                    )
                }

                onDismiss()
            }
        )
    }
}

@Composable
private fun LinkHandler(link: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val text = buildAnnotatedString {
                val fixedLink = link.takeIf { it.startsWith("http") } ?: "https://$link"

                withLink(link = LinkAnnotation.Url(url = fixedLink)) {
                    append(link)
                }
            }

            BasicText(
                text,
                style = TextStyle.Default.copy(color = MaterialTheme.colorScheme.onBackground),
            )
        }
    }
}

internal sealed interface MentionDetectionResult {
    data object Unchanged : MentionDetectionResult

    data class Active(val startPosition: Int, val filter: String) : MentionDetectionResult

    data object Dismissed : MentionDetectionResult
}

internal fun detectMentionState(
    text: String,
    cursorPos: Int,
    sizeDifference: Int,
    isPopupCurrentlyShown: Boolean,
    currentStartPosition: Int,
): MentionDetectionResult {
    val isCharacterAdded = sizeDifference > 0
    val isCharacterDeleted = sizeDifference < 0
    val hasNoRelevantTextChange = !isCharacterAdded && !isCharacterDeleted

    if (hasNoRelevantTextChange) {
        return MentionDetectionResult.Unchanged
    }

    val safeCursorPos = cursorPos.coerceIn(0, text.length)
    val textBeforeCursor = text.take(safeCursorPos)
    val lastAtIndex = textBeforeCursor.lastIndexOf('@')
    val hasAtBeforeCursor = lastAtIndex >= 0

    if (!hasAtBeforeCursor) {
        return if (isPopupCurrentlyShown) {
            MentionDetectionResult.Dismissed
        } else {
            MentionDetectionResult.Unchanged
        }
    }

    val charBeforeAt = if (lastAtIndex > 0) text.getOrNull(lastAtIndex - 1) else null
    val isAtStartOrAfterWhitespace =
        charBeforeAt == null || charBeforeAt == ' ' || charBeforeAt == '\n'

    if (!isAtStartOrAfterWhitespace) {
        return if (isPopupCurrentlyShown) {
            MentionDetectionResult.Dismissed
        } else {
            MentionDetectionResult.Unchanged
        }
    }

    val filterText = text.substring(lastAtIndex + 1, safeCursorPos)
    val hasWhitespaceInFilter = filterText.contains(' ') || filterText.contains('\n')

    if (hasWhitespaceInFilter) {
        return if (isPopupCurrentlyShown) {
            MentionDetectionResult.Dismissed
        } else {
            MentionDetectionResult.Unchanged
        }
    }

    return MentionDetectionResult.Active(
        startPosition = lastAtIndex,
        filter = filterText,
    )
}

@Preview
@Composable
fun DesktopMessageDrawerPreview() {
    TextDrawer(
        isDarkTheme = true,
        aiExplanation = "",
        selectionState = MutableStateFlow(false),
        onSelectionLister = {},
    ).Text(
        step = StoryStep(text = "Some text", type = StoryTypes.TEXT.type),
        drawInfo = DrawInfo(),
        interactionSource = remember { MutableInteractionSource() },
        focusRequester = FocusRequester(),
        decorationBox = @Composable { innerTextField -> innerTextField() }
    )
}

package io.writeopia.ui.drawer.content

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.ui.drawer.SimpleTextDrawer
import io.writeopia.ui.drawer.factory.EndOfText
import io.writeopia.ui.extensions.toTextRange
import io.writeopia.ui.model.DrawInfo
import io.writeopia.ui.model.EmptyErase
import io.writeopia.ui.model.TextInput
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
    private val onKeyEvent: (KeyEvent, TextFieldValue, StoryStep, Int, EmptyErase, Int, EndOfText) -> Boolean =
        { _, _, _, _, _, _, _ -> false },
    private val textStyle: @Composable (StoryStep) -> TextStyle = { defaultTextStyle(it) },
    private val onTextEdit: (TextInput, Int, Boolean) -> Unit = { _, _, _ -> },
    private val lineBreakByContent: Boolean = true,
    private val enabled: Boolean = true,
    private val emptyErase: EmptyErase = EmptyErase.CHANGE_TYPE,
    override var onFocusChanged: (Int, FocusState) -> Unit = { _, _ -> },
    private val selectionState: StateFlow<Boolean>,
    private val onSelectionLister: (Int) -> Unit,
    private val textToolbox: @Composable (Boolean) -> Unit = {}
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

        var inputText by remember {
            val text = step.text

            mutableStateOf(
                TextFieldValue(
                    Spans.createStringWithSpans(text, spans, isDarkTheme),
                    selection = drawInfo.selection?.toTextRange(text ?: "")
                        ?: TextRange.Zero
                )
            )
        }

        var textLayoutResult by remember {
            mutableStateOf<TextLayoutResult?>(null)
        }
        val cursorLine by remember {
            derivedStateOf {
                textLayoutResult?.getLineForOffset(inputText.selection.end)
            }
        }

        val selection by remember {
            derivedStateOf {
                inputText.selection
            }
        }

        val hasSelection = selection.start != selection.end

        val selectedLink by remember {
            derivedStateOf {
                spans
                    .filter { spanInfo -> spanInfo.span == Span.LINK }
                    .firstOrNull { (start, end, _, _) ->
                        selection.start >= start && selection.start <= end
                    }
                    ?.extra
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
                        val start = value.selection.start
                        val end = value.selection.end
                        val previousStart = inputText.selection.start

                        val sizeDifference = value.text.length - inputText.text.length

                        if (abs(sizeDifference) > 0) {
                            spans = Spans.recalculateSpans(spans, previousStart, sizeDifference)
                        }

                        val edit = {
                            inputText = value.copy(
                                Spans.createStringWithSpans(
                                    value.text.replace("\n", ""),
                                    spans,
                                    isDarkTheme
                                )
                            )
                        }

                        onTextEdit(
                            TextInput(value.text, start, end, spans),
                            drawInfo.position,
                            lineBreakByContent,
                        )

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
                )
            }

            textToolbox(hasSelection)

            Popup(offset = IntOffset(0, -20)) {
                AnimatedVisibility(
                    visible = selectedLink != null,
                    enter = fadeIn(animationSpec = tween(durationMillis = 150)),
                    exit = fadeOut(animationSpec = tween(durationMillis = 150))
                ) {
                    LinkHandler(selectedLink ?: "")
                }
            }
        }
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

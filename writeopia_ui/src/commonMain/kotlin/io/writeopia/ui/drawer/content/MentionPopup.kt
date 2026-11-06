package io.writeopia.ui.drawer.content

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.user.MentionUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.ui.drawer.factory.isEnterKey
import io.writeopia.ui.utils.Spans
import kotlinx.coroutines.flow.StateFlow

private val MIN_MENTION_DROPDOWN_WIDTH = 112.dp

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
    val hasNoRelevantTextChange = sizeDifference == 0

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

internal class MentionHandler(
    val isPopupShown: Boolean,
    val isLoading: Boolean,
    val users: List<MentionUser>,
    val selectedIndex: Int,
    val onKeyEvent: (KeyEvent) -> Boolean,
    val onTextChange: (text: String, cursorPos: Int, sizeDifference: Int) -> Unit,
    val onSelectUser: (MentionUser) -> Unit,
    val onDismiss: () -> Unit,
)

@Composable
internal fun rememberMentionHandler(
    mentionsEnabled: Boolean,
    mentionUsersState: StateFlow<ResultData<List<MentionUser>>>,
    onMentionSearch: (String) -> Unit,
    onMentionDismiss: () -> Unit,
    inputText: TextFieldValue,
    spans: Set<SpanInfo>,
    isDarkTheme: Boolean,
    primaryColor: Color,
    onApplyMention: (newFieldValue: TextFieldValue, updatedSpans: Set<SpanInfo>) -> Unit,
): MentionHandler {
    val mentionResultState by mentionUsersState.collectAsState()
    val isMentionLoading =
        mentionResultState is ResultData.Loading || mentionResultState is ResultData.InProgress
    val mentionUsers = (mentionResultState as? ResultData.Complete)?.data ?: emptyList()

    var showMentionPopup by remember { mutableStateOf(false) }
    var mentionFilter by remember { mutableStateOf("") }
    var mentionStartPosition by remember { mutableIntStateOf(-1) }
    var selectedMentionIndex by remember { mutableIntStateOf(0) }

    fun dismiss() {
        showMentionPopup = false
        mentionStartPosition = -1
        mentionFilter = ""
        selectedMentionIndex = 0
        onMentionDismiss()
    }

    fun select(user: MentionUser) {
        val currentText = inputText.text
        val safeStart = mentionStartPosition.coerceIn(0, currentText.length)
        val cursorPos = inputText.selection.start.coerceIn(safeStart, currentText.length)
        val beforeMention = currentText.take(safeStart)
        val afterCursor = currentText.substring(cursorPos)
        val mentionText = "@${user.username}"
        val newText = beforeMention + mentionText + afterCursor
        val newCursorPos = safeStart + mentionText.length

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

        val updatedFieldValue = TextFieldValue(
            annotatedString = Spans.createStringWithSpans(
                newText,
                updatedSpans,
                isDarkTheme,
                primaryColor
            ),
            selection = TextRange(newCursorPos),
        )

        dismiss()
        onApplyMention(updatedFieldValue, updatedSpans)
    }

    val handleKeyEvent: (KeyEvent) -> Boolean = { keyEvent ->
        if (showMentionPopup && (isMentionLoading || mentionUsers.isNotEmpty())) {
            when {
                keyEvent.key == Key.DirectionDown && mentionUsers.isNotEmpty() -> {
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        selectedMentionIndex = (selectedMentionIndex + 1) % mentionUsers.size
                    }
                    true
                }

                keyEvent.key == Key.DirectionUp && mentionUsers.isNotEmpty() -> {
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        selectedMentionIndex = if (selectedMentionIndex <= 0) {
                            mentionUsers.lastIndex
                        } else {
                            selectedMentionIndex - 1
                        }
                    }
                    true
                }

                keyEvent.key.isEnterKey() && mentionUsers.isNotEmpty() -> {
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        val index = selectedMentionIndex.coerceIn(0, mentionUsers.lastIndex)
                        select(mentionUsers[index])
                    }
                    true
                }

                keyEvent.key == Key.Escape -> {
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        dismiss()
                    }
                    true
                }

                else -> false
            }
        } else {
            false
        }
    }

    val handleTextChange: (String, Int, Int) -> Unit = { text, cursorPos, sizeDifference ->
        if (mentionsEnabled) {
            val mentionResult = detectMentionState(
                text = text,
                cursorPos = cursorPos,
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
                    dismiss()
                }

                MentionDetectionResult.Unchanged -> Unit
            }
        }
    }

    val targetIndex = when {
        mentionUsers.isEmpty() -> 0
        else -> selectedMentionIndex.coerceIn(0, mentionUsers.lastIndex)
    }

    return MentionHandler(
        isPopupShown = showMentionPopup,
        isLoading = isMentionLoading,
        users = mentionUsers,
        selectedIndex = targetIndex,
        onKeyEvent = handleKeyEvent,
        onTextChange = handleTextChange,
        onSelectUser = ::select,
        onDismiss = ::dismiss,
    )
}

@Composable
fun MentionPopup(
    expanded: Boolean,
    isLoading: Boolean = false,
    users: List<MentionUser>,
    selectedIndex: Int = 0,
    onUserSelected: (MentionUser) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DropdownMenu(
        expanded = expanded && (isLoading || users.isNotEmpty()),
        onDismissRequest = onDismiss,
        modifier = modifier.requiredWidthIn(
            min = MIN_MENTION_DROPDOWN_WIDTH,
            max = Dp.Unspecified,
        ),
        properties = PopupProperties(focusable = false),
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .requiredWidthIn(min = MIN_MENTION_DROPDOWN_WIDTH, max = Dp.Unspecified)
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
        } else {
            users.forEachIndexed { index, user ->
                DropdownMenuItem(
                    modifier = Modifier
                        .requiredWidthIn(min = MIN_MENTION_DROPDOWN_WIDTH, max = Dp.Unspecified)
                        .background(
                            if (index == selectedIndex) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
                            },
                        ),
                    text = { MentionUserItem(user) },
                    onClick = { onUserSelected(user) },
                )
            }
        }
    }
}

@Composable
private fun MentionUserItem(user: MentionUser) {
    Column {
        Text(
            text = user.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = "@${user.username}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

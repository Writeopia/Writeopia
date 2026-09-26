package io.writeopia.editor.features.editor.ui.comments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.model.story.Selection

internal const val COMMENT_BUTTON_TAG = "CommentButton"
internal const val COMMENT_PANEL_TAG = "CommentPanel"
internal const val COMMENT_INPUT_TAG = "CommentInput"

@Composable
internal fun CommentThreadOverlay(
    uiState: CommentUiState,
    editable: Boolean,
    onCreateComment: (String, Selection) -> Boolean,
    onReply: (String, String) -> Boolean,
    onDeleteComment: (String, String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val conversation = uiState.activeConversation
    var expanded by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var createTarget by remember { mutableStateOf<Selection?>(null) }
    val visible =
        conversation != null || (editable && (uiState.canCreateComment || createTarget != null))

    LaunchedEffect(visible, conversation?.id) {
        expanded = false
        if (!visible || conversation != null) {
            createTarget = null
        }
        draft = ""
    }

    if (!visible) return

    Column(
        modifier = modifier.widthIn(max = 340.dp),
        horizontalAlignment = Alignment.End,
    ) {
        FilledTonalButton(
            modifier = Modifier.testTag(COMMENT_BUTTON_TAG),
            onClick = {
                if (!expanded && conversation == null) {
                    createTarget = uiState.createTarget ?: createTarget
                }
                expanded = !expanded
            },
        ) {
            val label = when {
                conversation == null -> WrStrings.addComment()
                uiState.paragraphConversations.size > 1 ->
                    WrStrings.comments(uiState.paragraphConversations.size)
                else -> WrStrings.comment()
            }
            Text(label)
        }

        if (!expanded) return@Column

        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(COMMENT_PANEL_TAG),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 4.dp,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (conversation != null) {
                    conversation.comments.forEach { comment ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = comment.text,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (editable) {
                                TextButton(
                                    onClick = {
                                        onDeleteComment(conversation.id, comment.id)
                                    },
                                ) {
                                    Text(WrStrings.delete())
                                }
                            }
                        }
                    }

                    if (editable) {
                        TextButton(
                            onClick = {
                                onDeleteConversation(conversation.id)
                                expanded = false
                                draft = ""
                            },
                        ) {
                            Text(WrStrings.deleteThread())
                        }
                    }
                }

                if (editable) {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(COMMENT_INPUT_TAG),
                        value = draft,
                        onValueChange = { draft = it },
                        label = {
                            Text(if (conversation == null) WrStrings.comment() else WrStrings.reply())
                        },
                        minLines = 2,
                        maxLines = 5,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            enabled = draft.isNotBlank(),
                            onClick = {
                                val text = draft.trim()
                                val saved = if (conversation == null) {
                                    val target = createTarget ?: uiState.createTarget
                                    target != null && onCreateComment(text, target)
                                } else {
                                    onReply(conversation.id, text)
                                }
                                if (saved) {
                                    draft = ""
                                    createTarget = null
                                }
                            },
                        ) {
                            Text(if (conversation == null) WrStrings.add() else WrStrings.reply())
                        }
                    }
                }
            }
        }
    }
}

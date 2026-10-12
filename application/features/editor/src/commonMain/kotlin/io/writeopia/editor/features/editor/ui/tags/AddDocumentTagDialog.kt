package io.writeopia.editor.features.editor.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.editor.features.editor.viewmodel.AiTagDraft
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.document.DocumentTag

const val ADD_DOCUMENT_TAG_DIALOG_TAG = "AddDocumentTagDialog"

/**
 * Adds a tag to the document: one of the catalogue, searched by name, or a new one with a name
 * and an explanation, written by the user or proposed by the cloud AI from the document and
 * confirmed by the user.
 */
@Composable
fun AddDocumentTagDialog(
    workspaceTags: List<DocumentTag>,
    documentTagIds: Set<String>,
    canSuggestWithAi: Boolean,
    aiDraft: AiTagDraft,
    onPickTag: (String) -> Unit,
    onCreateTag: (name: String, description: String) -> Unit,
    onSuggestWithAi: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    // The AI's proposal fills the form, for the user to edit before confirming.
    LaunchedEffect(aiDraft) {
        if (aiDraft is AiTagDraft.Ready) {
            name = aiDraft.name
            description = aiDraft.description
        }
    }

    val available = workspaceTags.filter { tag -> tag.id !in documentTagIds }
    val matches = if (query.isBlank()) available else available.filter { tag -> tag.name.contains(query, ignoreCase = true) }
    val isAsking = aiDraft is AiTagDraft.Loading

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier.widthIn(max = 460.dp).fillMaxWidth().testTag(ADD_DOCUMENT_TAG_DIALOG_TAG),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = WrStrings.documentTagAddDialogTitle(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (available.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = WrStrings.documentTagAddExisting(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().testTag("$ADD_DOCUMENT_TAG_DIALOG_TAG.search"),
                        singleLine = true,
                        placeholder = { Text(WrStrings.documentTagAddSearch()) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (matches.isEmpty()) {
                        Text(
                            text = WrStrings.documentTagAddNoMatch(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(modifier = Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                            matches.forEach { tag ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onPickTag(tag.id) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("$ADD_DOCUMENT_TAG_DIALOG_TAG.pick.${tag.id}")
                                ) {
                                    Text(
                                        text = tag.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (tag.description.isNotBlank()) {
                                        Text(
                                            text = tag.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = WrStrings.documentTagAddNew(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().testTag("$ADD_DOCUMENT_TAG_DIALOG_TAG.name"),
                    singleLine = true,
                    enabled = !isAsking,
                    label = { Text(WrStrings.documentTagName()) },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth().testTag("$ADD_DOCUMENT_TAG_DIALOG_TAG.description"),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isAsking,
                    label = { Text(WrStrings.documentTagDescription()) },
                )

                if (aiDraft is AiTagDraft.Failed) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = WrStrings.documentTagAiFailed(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (canSuggestWithAi) {
                        TextButton(
                            onClick = onSuggestWithAi,
                            enabled = !isAsking,
                            modifier = Modifier.testTag("$ADD_DOCUMENT_TAG_DIALOG_TAG.ai")
                        ) {
                            if (isAsking) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = WrStrings.documentTagCreatingWithAi())
                            } else {
                                Text(text = WrStrings.documentTagCreateWithAi())
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("$ADD_DOCUMENT_TAG_DIALOG_TAG.cancel")) {
                        Text(text = WrStrings.documentTagCancel())
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onCreateTag(name.trim(), description.trim()) },
                        enabled = name.isNotBlank() && !isAsking,
                        modifier = Modifier.testTag("$ADD_DOCUMENT_TAG_DIALOG_TAG.create")
                    ) {
                        Text(text = WrStrings.documentTagCreate())
                    }
                }
            }
        }
    }
}

package io.writeopia.editor.features.editor.ui.tags

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.editor.features.editor.viewmodel.AiTagDraft
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.document.DocumentTag

const val ADD_DOCUMENT_TAG_DIALOG_TAG = "AddDocumentTagDialog"

private const val PICK_TAB = 0
private const val NEW_TAB = 1

/**
 * Adds a tag to the document: one of the catalogue, searched by name, or a new one with a name
 * and an explanation, written by the user or proposed by the cloud AI from the document and
 * confirmed by the user. Each way has a tab of its own.
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
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier.widthIn(max = 460.dp).fillMaxWidth().testTag(ADD_DOCUMENT_TAG_DIALOG_TAG),
            shape = RoundedCornerShape(16.dp),
        ) {
            AddDocumentTagContent(
                workspaceTags = workspaceTags,
                documentTagIds = documentTagIds,
                canSuggestWithAi = canSuggestWithAi,
                aiDraft = aiDraft,
                onPickTag = onPickTag,
                onCreateTag = onCreateTag,
                onSuggestWithAi = onSuggestWithAi,
                onDismiss = onDismiss,
                stackActions = false,
                listMaxHeight = 280.dp,
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
            )
        }
    }
}

/**
 * [AddDocumentTagDialog] in a bottom sheet, for phones: more room for the list and the form,
 * and the AI suggestion on a line of its own, above Cancel and Create.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDocumentTagSheet(
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier.testTag(ADD_DOCUMENT_TAG_DIALOG_TAG),
    ) {
        AddDocumentTagContent(
            workspaceTags = workspaceTags,
            documentTagIds = documentTagIds,
            canSuggestWithAi = canSuggestWithAi,
            aiDraft = aiDraft,
            onPickTag = onPickTag,
            onCreateTag = onCreateTag,
            onSuggestWithAi = onSuggestWithAi,
            onDismiss = onDismiss,
            stackActions = true,
            listMaxHeight = 400.dp,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun AddDocumentTagContent(
    workspaceTags: List<DocumentTag>,
    documentTagIds: Set<String>,
    canSuggestWithAi: Boolean,
    aiDraft: AiTagDraft,
    onPickTag: (String) -> Unit,
    onCreateTag: (name: String, description: String) -> Unit,
    onSuggestWithAi: () -> Unit,
    onDismiss: () -> Unit,
    stackActions: Boolean,
    listMaxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val available = workspaceTags.filter { tag -> tag.id !in documentTagIds }

    // With nothing to pick, the form is what the user came for.
    var selectedTab by rememberSaveable { mutableIntStateOf(if (available.isEmpty()) NEW_TAB else PICK_TAB) }
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

    Column(modifier = modifier) {
        Text(
            text = WrStrings.documentTagAddDialogTitle(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Tab(
                selected = selectedTab == PICK_TAB,
                onClick = { selectedTab = PICK_TAB },
                modifier = Modifier.testTag("$ADD_DOCUMENT_TAG_DIALOG_TAG.pickTab"),
                text = { Text(WrStrings.documentTagAddExisting(), style = MaterialTheme.typography.labelLarge) }
            )
            Tab(
                selected = selectedTab == NEW_TAB,
                onClick = { selectedTab = NEW_TAB },
                modifier = Modifier.testTag("$ADD_DOCUMENT_TAG_DIALOG_TAG.newTab"),
                text = { Text(WrStrings.documentTagAddNew(), style = MaterialTheme.typography.labelLarge) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedTab) {
            PICK_TAB -> PickTagTab(
                available = available,
                query = query,
                onQueryChange = { query = it },
                onPickTag = onPickTag,
                onDismiss = onDismiss,
                listMaxHeight = listMaxHeight,
            )

            else -> NewTagTab(
                name = name,
                onNameChange = { name = it },
                description = description,
                onDescriptionChange = { description = it },
                canSuggestWithAi = canSuggestWithAi,
                aiDraft = aiDraft,
                onCreateTag = onCreateTag,
                onSuggestWithAi = onSuggestWithAi,
                onDismiss = onDismiss,
                stackActions = stackActions,
            )
        }
    }
}

@Composable
private fun PickTagTab(
    available: List<DocumentTag>,
    query: String,
    onQueryChange: (String) -> Unit,
    onPickTag: (String) -> Unit,
    onDismiss: () -> Unit,
    listMaxHeight: Dp,
) {
    val matches = if (query.isBlank()) available else available.filter { tag -> tag.name.contains(query, ignoreCase = true) }

    if (available.isEmpty()) {
        Text(
            text = WrStrings.documentTagAddEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().testTag("$ADD_DOCUMENT_TAG_DIALOG_TAG.search"),
            singleLine = true,
            placeholder = { Text(WrStrings.documentTagAddSearch()) },
            shape = MaterialTheme.shapes.large,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (matches.isEmpty()) {
            Text(
                text = WrStrings.documentTagAddNoMatch(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(modifier = Modifier.heightIn(max = listMaxHeight).verticalScroll(rememberScrollState())) {
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
    }

    Spacer(modifier = Modifier.height(16.dp))

    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.weight(1f))
        CancelButton(onDismiss)
    }
}

@Composable
private fun NewTagTab(
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    canSuggestWithAi: Boolean,
    aiDraft: AiTagDraft,
    onCreateTag: (name: String, description: String) -> Unit,
    onSuggestWithAi: () -> Unit,
    onDismiss: () -> Unit,
    stackActions: Boolean,
) {
    val isAsking = aiDraft is AiTagDraft.Loading

    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        modifier = Modifier.fillMaxWidth().testTag("$ADD_DOCUMENT_TAG_DIALOG_TAG.name"),
        singleLine = true,
        enabled = !isAsking,
        label = { Text(WrStrings.documentTagName()) },
        shape = MaterialTheme.shapes.large,
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = description,
        onValueChange = onDescriptionChange,
        modifier = Modifier.fillMaxWidth().testTag("$ADD_DOCUMENT_TAG_DIALOG_TAG.description"),
        minLines = 2,
        maxLines = 4,
        enabled = !isAsking,
        label = { Text(WrStrings.documentTagDescription()) },
        shape = MaterialTheme.shapes.large,
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

    val suggestButton = @Composable {
        if (canSuggestWithAi) SuggestWithAiButton(isAsking = isAsking, onClick = onSuggestWithAi)
    }
    val createButton = @Composable {
        Button(
            onClick = { onCreateTag(name.trim(), description.trim()) },
            enabled = name.isNotBlank() && !isAsking,
            modifier = Modifier.testTag("$ADD_DOCUMENT_TAG_DIALOG_TAG.create")
        ) {
            Text(text = WrStrings.documentTagCreate())
        }
    }

    if (stackActions) {
        suggestButton()
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(modifier = Modifier.weight(1f))
            CancelButton(onDismiss)
            Spacer(modifier = Modifier.width(8.dp))
            createButton()
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            suggestButton()
            Spacer(modifier = Modifier.weight(1f))
            CancelButton(onDismiss)
            Spacer(modifier = Modifier.width(8.dp))
            createButton()
        }
    }
}

@Composable
private fun SuggestWithAiButton(isAsking: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
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

@Composable
private fun CancelButton(onDismiss: () -> Unit) {
    TextButton(onClick = onDismiss, modifier = Modifier.testTag("$ADD_DOCUMENT_TAG_DIALOG_TAG.cancel")) {
        Text(text = WrStrings.documentTagCancel())
    }
}

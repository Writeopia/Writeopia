package io.writeopia.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.writeopia.account.di.AccountMenuKmpInjector
import io.writeopia.account.viewmodel.WorkspaceTagsController
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.theme.WriteopiaTheme

const val SETTINGS_TAGS_TAG = "SettingsTags"

/** The tags page, with its view model. */
@Composable
fun SettingsTagsRoute(modifier: Modifier = Modifier) {
    val viewModel = AccountMenuKmpInjector.singleton().provideWorkspaceTagsViewModel()
    val tags by viewModel.tags.collectAsState(emptyList())

    SettingsTagsScreen(
        tags = tags,
        onCreate = viewModel::createTag,
        onEdit = viewModel::editTag,
        onDelete = viewModel::deleteTag,
        modifier = modifier,
    )
}

/** What the tag form is open for. */
private sealed interface TagForm {
    data object New : TagForm

    data class Edit(val tag: DocumentTag) : TagForm
}

/**
 * The tag catalogue of the workspace: every tag with its explanation, to edit or delete, and a
 * button for a new one. Deleting asks first. It doesn't scroll: the page around it does.
 */
@Composable
fun SettingsTagsScreen(
    tags: List<DocumentTag>,
    onCreate: (name: String, description: String) -> Unit,
    onEdit: (id: String, name: String, description: String) -> Unit,
    onDelete: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var form by remember { mutableStateOf<TagForm?>(null) }
    var toDelete by remember { mutableStateOf<DocumentTag?>(null) }

    Column(modifier = modifier.fillMaxWidth().testTag(SETTINGS_TAGS_TAG)) {
        Text(
            text = WrStrings.settingsTagsDescription(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = { form = TagForm.New }, modifier = Modifier.testTag("$SETTINGS_TAGS_TAG.new")) {
            Icon(imageVector = WrIcons.add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(WrStrings.settingsTagNew())
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (tags.isEmpty()) {
            Text(
                text = WrStrings.settingsTagsEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    TagRow(
                        tag = tag,
                        onEdit = { form = TagForm.Edit(tag) },
                        onDelete = { toDelete = tag },
                    )
                }
            }
        }
    }

    form?.let { current ->
        val editing = (current as? TagForm.Edit)?.tag

        TagFormDialog(
            tag = editing,
            isNameTaken = { name -> WorkspaceTagsController.isNameTaken(tags, name, exceptId = editing?.id) },
            onConfirm = { name, description ->
                if (editing == null) onCreate(name, description) else onEdit(editing.id, name, description)
                form = null
            },
            onDismiss = { form = null }
        )
    }

    toDelete?.let { tag ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(WrStrings.settingsTagDeleteTitle()) },
            text = { Text(WrStrings.settingsTagDeleteMessage(tag.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(tag.id)
                        toDelete = null
                    },
                    modifier = Modifier.testTag("$SETTINGS_TAGS_TAG.confirmDelete")
                ) {
                    Text(WrStrings.delete(), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { toDelete = null }) {
                    Text(WrStrings.cancel())
                }
            }
        )
    }
}

@Composable
private fun TagRow(tag: DocumentTag, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(WriteopiaTheme.colorScheme.optionsSelector)
            .clickable(onClick = onEdit)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
            .testTag("$SETTINGS_TAGS_TAG.tag.${tag.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = WrIcons.tag,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (tag.description.isNotBlank()) {
                Text(
                    text = tag.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        RowIcon(
            icon = WrIcons.edit,
            description = WrStrings.settingsTagEdit(),
            onClick = onEdit,
            testTag = "$SETTINGS_TAGS_TAG.edit.${tag.id}"
        )
        RowIcon(
            icon = WrIcons.delete,
            description = WrStrings.delete(),
            onClick = onDelete,
            testTag = "$SETTINGS_TAGS_TAG.delete.${tag.id}"
        )
    }
}

@Composable
private fun RowIcon(icon: ImageVector, description: String, onClick: () -> Unit, testTag: String) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(10.dp)
            .size(18.dp)
            .testTag(testTag)
    )
}

/** The name and the explanation of a new tag, or of [tag] being edited. */
@Composable
private fun TagFormDialog(
    tag: DocumentTag?,
    isNameTaken: (String) -> Boolean,
    onConfirm: (name: String, description: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(tag?.id) { mutableStateOf(tag?.name.orEmpty()) }
    var description by remember(tag?.id) { mutableStateOf(tag?.description.orEmpty()) }
    val taken = isNameTaken(name)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("$SETTINGS_TAGS_TAG.form"),
        title = { Text(if (tag == null) WrStrings.settingsTagNew() else WrStrings.settingsTagEdit()) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().testTag("$SETTINGS_TAGS_TAG.form.name"),
                    singleLine = true,
                    isError = taken,
                    label = { Text(WrStrings.documentTagName()) },
                    supportingText = if (taken) {
                        { Text(WrStrings.settingsTagNameTaken()) }
                    } else {
                        null
                    },
                    shape = MaterialTheme.shapes.large,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth().testTag("$SETTINGS_TAGS_TAG.form.description"),
                    minLines = 2,
                    maxLines = 4,
                    label = { Text(WrStrings.documentTagDescription()) },
                    shape = MaterialTheme.shapes.large,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), description.trim()) },
                enabled = name.isNotBlank() && !taken,
                modifier = Modifier.testTag("$SETTINGS_TAGS_TAG.form.confirm")
            ) {
                Text(if (tag == null) WrStrings.documentTagCreate() else WrStrings.save())
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(WrStrings.cancel())
            }
        }
    )
}

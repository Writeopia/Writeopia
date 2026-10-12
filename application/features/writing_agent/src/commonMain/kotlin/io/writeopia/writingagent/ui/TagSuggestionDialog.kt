package io.writeopia.writingagent.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.writingagent.controller.WritingAgentController
import io.writeopia.writingagent.model.TagSuggestion

const val TAG_SUGGESTION_DIALOG_TAG = "TagSuggestionDialog"

/**
 * The dialog that tags a document without tags. It spins while the backend ranks the catalogue,
 * then lists the tags that fit, selected, to add at once, and offers a new tag made up by the AI.
 * Hidden while the controller has no dialog to show.
 */
@Composable
fun TagSuggestionDialog(controller: WritingAgentController) {
    val state by controller.state.collectAsState()
    val dialog = state.tagDialog ?: return
    val canAdd = !dialog.isLoading && !dialog.isCreating && dialog.selected.isNotEmpty()

    AlertDialog(
        onDismissRequest = { if (!dialog.isCreating) controller.dismissTagDialog() },
        modifier = Modifier.testTag(TAG_SUGGESTION_DIALOG_TAG),
        icon = {
            Icon(
                imageVector = WrIcons.ai,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(text = WrStrings.writingTagDialogTitle()) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = WrStrings.writingTagDialogBody(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    dialog.isCreating -> Progress(WrStrings.writingTagDialogCreating())

                    dialog.isLoading -> Progress(WrStrings.writingTagDialogLoading())

                    dialog.catalogueIsEmpty -> Text(
                        text = WrStrings.writingTagDialogNoCatalogue(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    dialog.options.isEmpty() -> Text(
                        text = WrStrings.writingTagDialogNone(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> dialog.options.forEach { tag ->
                        TagOption(
                            tag = tag,
                            selected = tag.tagId in dialog.selected,
                            onToggle = { controller.toggleTag(tag.tagId) },
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                if (!dialog.isLoading && !dialog.isCreating) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = controller::createTagWithAi,
                        modifier = Modifier.testTag("$TAG_SUGGESTION_DIALOG_TAG.newTag")
                    ) {
                        Text(text = WrStrings.writingTagDialogNewTag())
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = controller::applyTags,
                enabled = canAdd,
                modifier = Modifier.testTag("$TAG_SUGGESTION_DIALOG_TAG.add")
            ) {
                Text(text = WrStrings.writingTagDialogAdd())
            }
        },
        dismissButton = {
            TextButton(
                onClick = controller::dismissTagDialog,
                enabled = !dialog.isCreating,
                modifier = Modifier.testTag("$TAG_SUGGESTION_DIALOG_TAG.notNow")
            ) {
                Text(text = WrStrings.writingTagDialogNotNow())
            }
        }
    )
}

@Composable
private fun Progress(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TagOption(tag: TagSuggestion, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .testTag("$TAG_SUGGESTION_DIALOG_TAG.option.${tag.tagId}")
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() })

        Column(modifier = Modifier.weight(1f)) {
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

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "${(tag.probability * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

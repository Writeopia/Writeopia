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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.models.document.DocumentTag

const val DOCUMENT_TAGS_DIALOG_TAG = "DocumentTagsDialog"

/** The tags of the document, each with its explanation and a way to take it off the document. */
@Composable
fun DocumentTagsDialog(
    tags: List<DocumentTag>,
    isEditable: Boolean,
    onRemove: (String) -> Unit,
    onAddTag: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier.widthIn(max = 440.dp).fillMaxWidth().testTag(DOCUMENT_TAGS_DIALOG_TAG),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = WrStrings.documentTagsDialogTitle(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (tags.isEmpty()) {
                    Text(
                        text = WrStrings.documentTagsDialogEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())
                    ) {
                        tags.forEach { tag ->
                            TagRow(tag = tag, removable = isEditable, onRemove = { onRemove(tag.id) })
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    if (isEditable) {
                        TextButton(
                            onClick = onAddTag,
                            modifier = Modifier.testTag("$DOCUMENT_TAGS_DIALOG_TAG.add")
                        ) {
                            Text(text = WrStrings.documentTagsAdd())
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("$DOCUMENT_TAGS_DIALOG_TAG.close")) {
                        Text(text = WrStrings.close())
                    }
                }
            }
        }
    }
}

@Composable
private fun TagRow(tag: DocumentTag, removable: Boolean, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("$DOCUMENT_TAGS_DIALOG_TAG.row.${tag.id}"),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tag.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (tag.description.isNotBlank()) {
                Text(
                    text = tag.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (removable) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = WrIcons.close,
                contentDescription = WrStrings.documentTagsRemove(),
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
                    .padding(3.dp)
                    .testTag("$DOCUMENT_TAGS_DIALOG_TAG.remove.${tag.id}"),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

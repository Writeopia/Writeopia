@file:OptIn(ExperimentalTime::class)

package io.writeopia.documents.graph.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.documents.graph.GraphItemDetails
import io.writeopia.resources.WrStrings
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

const val GRAPH_ITEM_CARD_TAG = "GraphItemCard"

/**
 * The card at the bottom left of the graph: what the clicked node is, a few facts about it and a
 * button that goes to it: the editor, the folder, or the screen listing the documents of a tag.
 */
@Composable
fun GraphItemCard(
    details: GraphItemDetails,
    onOpenDocument: (id: String, title: String) -> Unit,
    onOpenFolder: (id: String) -> Unit,
    onOpenTag: (id: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.widthIn(min = 220.dp, max = 320.dp).testTag(GRAPH_ITEM_CARD_TAG),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = kindLabel(details),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = details.title.ifBlank { WrStrings.untitled() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = WrIcons.close,
                    contentDescription = WrStrings.close(),
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(3.dp)
                        .testTag("$GRAPH_ITEM_CARD_TAG.close"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                when (details) {
                    is GraphItemDetails.DocumentDetails -> {
                        Fact(WrStrings.graphCardUpdated(formatDate(details.lastUpdatedAt)))
                        Fact(details.folderName?.let { WrStrings.graphCardInFolder(it) } ?: WrStrings.graphCardAtRoot())
                        Fact(
                            if (details.tagNames.isEmpty()) {
                                WrStrings.graphCardNoTags()
                            } else {
                                WrStrings.graphCardTags(details.tagNames.joinToString(", "))
                            }
                        )
                    }

                    is GraphItemDetails.FolderDetails ->
                        Fact(WrStrings.graphCardFolderContents(details.folderCount, details.documentCount))

                    is GraphItemDetails.TagDetails -> {
                        if (details.description.isNotBlank()) Fact(details.description)
                        Fact(WrStrings.graphCardDocumentsCount(details.documentIds.size))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    when (details) {
                        is GraphItemDetails.DocumentDetails -> onOpenDocument(details.id, details.title)
                        is GraphItemDetails.FolderDetails -> onOpenFolder(details.id)
                        is GraphItemDetails.TagDetails -> onOpenTag(details.id)
                    }
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().testTag("$GRAPH_ITEM_CARD_TAG.open")
            ) {
                Text(
                    text = when (details) {
                        is GraphItemDetails.DocumentDetails -> WrStrings.graphCardOpenDocument()
                        is GraphItemDetails.FolderDetails -> WrStrings.graphCardOpenFolder()
                        is GraphItemDetails.TagDetails -> WrStrings.graphCardShowDocuments()
                    }
                )
            }
        }
    }
}

@Composable
private fun kindLabel(details: GraphItemDetails): String =
    when (details) {
        is GraphItemDetails.DocumentDetails -> WrStrings.graphCardKindDocument()
        is GraphItemDetails.FolderDetails -> WrStrings.graphCardKindFolder()
        is GraphItemDetails.TagDetails -> WrStrings.graphCardKindTag()
    }

@Composable
private fun Fact(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}

private fun formatDate(instant: Instant): String {
    val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val month = date.month.ordinal + 1
    return "${date.year}-${month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
}

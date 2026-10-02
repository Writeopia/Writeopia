@file:OptIn(ExperimentalTime::class)

package io.writeopia.editor.features.presentation.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.editor.features.presentation.viewmodel.PresentationsController
import io.writeopia.sdk.models.presentation.Presentation
import kotlin.time.ExperimentalTime

/**
 * The presentations of the document, like the sheet of the Mac app: the ones made before, each
 * opening in its own window, and a button that asks the AI for a new one.
 */
@Composable
fun PresentationsDialog(
    controller: PresentationsController,
    onOpen: (Presentation) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presentations by controller.presentations.collectAsState()
    val isLoading by controller.isLoading.collectAsState()
    val isGenerating by controller.isGenerating.collectAsState()
    val error by controller.error.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier.widthIn(max = 440.dp).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
                Text(
                    text = "Presentations",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))

                when {
                    isLoading && presentations.isEmpty() -> Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    }

                    presentations.isEmpty() -> Text(
                        text = "No presentations yet. Create one and the AI writes the slides from this document.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(presentations, key = { it.id }) { presentation ->
                            PresentationRow(
                                presentation = presentation,
                                onOpen = { onOpen(presentation) },
                                onDelete = { controller.delete(presentation) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }

                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "The AI is writing the slides…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = controller::cancel) { Text("Cancel") }
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    controller.clearError()
                                    controller.generate()
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = WrIcons.ai,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Create new presentation",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Done") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresentationRow(presentation: Presentation, onOpen: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = WrIcons.play,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = presentation.title.ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Text(
                text = "${presentation.slides.size} slides",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = WrIcons.delete,
            contentDescription = "Delete presentation",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onDelete)
                .padding(6.dp)
                .size(18.dp)
        )
    }
}

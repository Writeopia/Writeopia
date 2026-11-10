package io.writeopia.editor.features.editor.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.editor.features.editor.viewmodel.DrawingOptionsState
import io.writeopia.resources.WrStrings
import io.writeopia.ui.icons.WrSdkIcons

/**
 * The options of a drawing, opened by the "..." of its preview: for now, converting it to an
 * equation. The dialog follows the conversion (progress, failure with a retry) and the caller
 * closes it when the equation is in the document.
 */
@Composable
fun DrawingOptionsDialog(
    state: DrawingOptionsState,
    onConvertToEquation: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp).width(320.dp)
            ) {
                Text(
                    text = WrStrings.drawingOptionsTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (state.phase) {
                    DrawingOptionsState.Phase.CONVERTING -> ConvertingRow()

                    DrawingOptionsState.Phase.UNAVAILABLE -> Text(
                        text = WrStrings.equationCloudAiUnavailable(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    DrawingOptionsState.Phase.FAILED -> {
                        Text(
                            text = state.error ?: WrStrings.drawingConvertFailed(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OptionRow(
                            title = WrStrings.drawingConvertRetry(),
                            explanation = WrStrings.drawingConvertExplanation(),
                            onClick = onConvertToEquation
                        )
                    }

                    DrawingOptionsState.Phase.IDLE -> OptionRow(
                        title = WrStrings.drawingConvertToEquation(),
                        explanation = WrStrings.drawingConvertExplanation(),
                        onClick = onConvertToEquation
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = WrStrings.cancel(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.End)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClick = onDismiss)
                        .padding(6.dp)
                )
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, explanation: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(8.dp)
            .testTag("convertDrawingToEquation"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = WrSdkIcons.ai,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConvertingRow() {
    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = WrStrings.drawingConverting(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

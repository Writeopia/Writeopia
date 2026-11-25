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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.editor.features.editor.viewmodel.EquationEditionState
import io.writeopia.resources.WrStrings

/** Edits the LaTeX of an equation step. Save renders it with the backend before closing. */
@Composable
fun EquationEditDialog(
    state: EquationEditionState,
    onLatexChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp).width(360.dp)) {
                Text(
                    text = WrStrings.equationEditTitle(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = state.latex,
                    onValueChange = onLatexChange,
                    modifier = Modifier.fillMaxWidth().testTag("equationLatex"),
                    placeholder = { Text(WrStrings.equationLatexHint()) },
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                    minLines = 2,
                    maxLines = 6,
                    isError = state.error != null,
                    enabled = !state.rendering
                )

                if (state.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = WrStrings.cancel(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(MaterialTheme.shapes.medium)
                            .clickable(onClick = onDismiss)
                            .padding(6.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (state.rendering) {
                        Row(modifier = Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = WrStrings.equationRendering(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    } else {
                        Text(
                            text = WrStrings.save(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(MaterialTheme.shapes.medium)
                                .clickable(enabled = state.latex.isNotBlank(), onClick = onSave)
                                .padding(6.dp)
                                .testTag("equationSave")
                        )
                    }
                }
            }
        }
    }
}

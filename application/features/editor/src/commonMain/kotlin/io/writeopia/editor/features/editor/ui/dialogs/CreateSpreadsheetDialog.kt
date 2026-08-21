package io.writeopia.editor.features.editor.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.commonui.buttons.CommonButton
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun CreateSpreadsheetDialog(
    onDismissRequest: () -> Unit,
    onCreate: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var columnCountText by remember { mutableStateOf("3") }
    val columnCount = columnCountText.toIntOrNull() ?: 3
    val isValid = columnCount in 1..20

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = modifier
                .widthIn(max = 350.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = "Create Spreadsheet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Number of columns (1-20):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = columnCountText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            columnCountText = newValue
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = !isValid && columnCountText.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (!isValid && columnCountText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Please enter a number between 1 and 20",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val isEnabledState = remember { MutableStateFlow(true) }

                    CommonButton(
                        text = "Cancel",
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        isEnabledState = isEnabledState,
                        clickListener = onDismissRequest
                    )

                    CommonButton(
                        text = "Create",
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        isEnabledState = remember { MutableStateFlow(isValid) },
                        clickListener = {
                            if (isValid) {
                                onCreate(columnCount)
                            }
                        }
                    )
                }
            }
        }
    }
}

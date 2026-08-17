package io.writeopia.notemenu.ui.screen.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.commonui.IconsPicker
import io.writeopia.sdk.models.document.MenuItem

@Composable
fun CreateFolderDialog(
    onDismissRequest: () -> Unit,
    onCreate: (String, MenuItem.Icon?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = modifier) {
            Column(modifier = Modifier.padding(20.dp).width(400.dp)) {
                var folderName by remember { mutableStateOf("") }
                var selectedIcon by remember { mutableStateOf<String?>(null) }
                var selectedTint by remember { mutableIntStateOf(0) }

                Text("Create Folder")

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider(color = Color.Gray)

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Select icon (optional)")

                Spacer(modifier = Modifier.height(8.dp))

                IconsPicker(
                    modifier = Modifier.height(150.dp),
                    iconSelect = { icon, tint ->
                        selectedIcon = icon
                        selectedTint = tint
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row {
                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            val icon = selectedIcon?.let { iconLabel ->
                                MenuItem.Icon(label = iconLabel, tint = selectedTint)
                            }
                            val name = folderName.takeIf { it.isNotBlank() } ?: "Untitled"
                            onCreate(name, icon)
                            onDismissRequest()
                        }
                    ) {
                        Text("Create")
                    }
                }
            }
        }
    }
}

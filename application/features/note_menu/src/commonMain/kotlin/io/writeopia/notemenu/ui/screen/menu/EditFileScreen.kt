@file:OptIn(ExperimentalTime::class)

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.sdk.models.document.Folder
import kotlin.time.ExperimentalTime

@Composable
fun EditFileScreen(
    folderEdit: Folder,
    onDismissRequest: () -> Unit,
    editFolder: (Folder) -> Unit,
    deleteFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = modifier) {
            Column(modifier = Modifier.padding(20.dp).width(400.dp)) {
                var fileText by remember {
                    mutableStateOf(folderEdit.title)
                }

                Text("Update Folder")

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider(color = Color.Gray)

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = fileText,
                    onValueChange = { title ->
                        fileText = title
                        editFolder(folderEdit.copy(title = title.takeIf { it.isNotEmpty() } ?: " "))
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row {
                    TextButton(onClick = {
                        deleteFolder(folderEdit.id)
                    }) {
                        Text("Delete folder")
                    }

                    TextButton(onClick = onDismissRequest) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

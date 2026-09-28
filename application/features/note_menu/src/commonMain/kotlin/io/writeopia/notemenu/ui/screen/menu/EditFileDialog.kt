@file:OptIn(ExperimentalTime::class)

package io.writeopia.notemenu.ui.screen.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.commonui.IconsPicker
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import kotlin.time.ExperimentalTime

/**
 * Edits the name, the icon and the color of the icon of a folder. It can also delete it.
 */
@Composable
fun EditFileDialog(
    folderEdit: Folder,
    onDismissRequest: () -> Unit,
    editFolder: (Folder) -> Unit,
    deleteFolder: (String) -> Unit,
    modifier: Modifier = Modifier,
    colorSize: Dp = 12.dp,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(modifier = modifier) {
            Column(modifier = Modifier.padding(20.dp).width(400.dp)) {
                var fileText by remember {
                    mutableStateOf(folderEdit.title)
                }
                var icon by remember {
                    mutableStateOf(folderEdit.icon)
                }

                Text("Edit folder", style = MaterialTheme.typography.titleMedium)

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider(color = Color.Gray)

                Spacer(modifier = Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier.size(28.dp),
                        imageVector = icon?.label?.let(WrIcons::fromName) ?: WrIcons.folder,
                        contentDescription = null,
                        tint = icon?.tint?.let(::Color) ?: MaterialTheme.colorScheme.onBackground,
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    OutlinedTextField(
                        value = fileText,
                        onValueChange = { title ->
                            fileText = title
                        },
                        label = { Text("Folder name") },
                        singleLine = true,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Icon", style = MaterialTheme.typography.labelLarge)

                IconsPicker(
                    modifier = Modifier.height(150.dp),
                    initialIconName = folderEdit.icon?.label,
                    initialTint = folderEdit.icon?.tint,
                    colorSize = colorSize,
                    iconSelect = { iconName, tint ->
                        icon = MenuItem.Icon(label = iconName, tint = tint)
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row {
                    TextButton(onClick = {
                        deleteFolder(folderEdit.id)
                    }) {
                        Text("Delete folder", color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.weight(1F))

                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel")
                    }

                    TextButton(onClick = {
                        editFolder(
                            folderEdit.copy(
                                title = fileText.takeIf { it.isNotEmpty() } ?: " ",
                                icon = icon
                            )
                        )
                        onDismissRequest()
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

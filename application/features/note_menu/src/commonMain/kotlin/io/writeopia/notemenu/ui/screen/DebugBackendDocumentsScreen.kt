package io.writeopia.notemenu.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.notemenu.viewmodel.DebugBackendDocumentRow
import io.writeopia.notemenu.viewmodel.DebugBackendDocumentsState
import io.writeopia.notemenu.viewmodel.DebugBackendDocumentsViewModel

@Composable
internal fun DebugBackendDocumentsScreen(
    viewModel: DebugBackendDocumentsViewModel,
    navigateToDocument: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Backend documents",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.weight(1F))

            IconButton(onClick = viewModel::refresh) {
                Icon(
                    imageVector = WrIcons.sync,
                    contentDescription = "Sync backend documents",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        when (val currentState = state) {
            DebugBackendDocumentsState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Could not load backend documents.",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            DebugBackendDocumentsState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is DebugBackendDocumentsState.Content -> {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(bottom = 16.dp)) {
                    items(currentState.rows, key = { row -> row.item.id }) { row ->
                        DebugBackendDocumentListRow(
                            row = row,
                            toggleFolder = viewModel::toggleFolder,
                            navigateToDocument = navigateToDocument
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DebugBackendDocumentListRow(
    row: DebugBackendDocumentRow,
    toggleFolder: (String) -> Unit,
    navigateToDocument: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontalPadding = 24.dp + (row.depth * 20).dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                when (row) {
                    is DebugBackendDocumentRow.DocumentRow ->
                        navigateToDocument(row.document.id, row.document.title)

                    is DebugBackendDocumentRow.FolderRow ->
                        toggleFolder(row.folder.id)
                }
            }
            .padding(start = horizontalPadding, end = 24.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val icon = when (row) {
            is DebugBackendDocumentRow.DocumentRow -> WrIcons.file
            is DebugBackendDocumentRow.FolderRow ->
                if (row.expanded) WrIcons.smallArrowDown else WrIcons.smallArrowRight
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = row.item.title.ifBlank { "Untitled" },
            modifier = Modifier.padding(start = 12.dp),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

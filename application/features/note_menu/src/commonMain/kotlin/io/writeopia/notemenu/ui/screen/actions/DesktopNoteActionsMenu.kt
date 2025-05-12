package io.writeopia.notemenu.ui.screen.actions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.notemenu.ui.screen.configuration.modifier.icon
import io.writeopia.notemenu.viewmodel.SyncState
import io.writeopia.resources.WrStrings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun DesktopNoteActionsMenu(
    modifier: Modifier = Modifier,
    showExtraOptions: StateFlow<Boolean>,
    showExtraOptionsRequest: () -> Unit,
    hideExtraOptionsRequest: () -> Unit,
    exportAsMarkdownClick: () -> Unit,
    exportAsTxtClick: () -> Unit,
    importClick: () -> Unit,
    syncInProgressState: StateFlow<SyncState>,
    onSyncLocallySelected: () -> Unit,
    onWriteLocallySelected: () -> Unit,
    onForceGraphSelected: () -> Unit,
    configureSelfHostedBackend: () -> Unit = {}
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val showSyncLoading by syncInProgressState.collectAsState()

        Icon(
            imageVector = WrIcons.chart,
            contentDescription = "Chart",
            modifier = Modifier.icon(onForceGraphSelected)
                .padding(2.dp)
                .testTag("onForceGraphSelected"),
            tint = MaterialTheme.colorScheme.onBackground
        )

        LoadingBox(showSyncLoading == SyncState.LoadingWrite) {
            Icon(
                imageVector = WrIcons.save,
                contentDescription = "Save",
                modifier = Modifier.icon(onWriteLocallySelected)
                    .padding(2.dp)
                    .testTag("writeWorkspaceLocally"),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        LoadingBox(showSyncLoading == SyncState.LoadingSync) {
            Icon(
                imageVector = WrIcons.sync,
                contentDescription = "Sync",
                modifier = Modifier.icon(onSyncLocallySelected)
                    .padding(2.dp)
                    .testTag("syncWorkspaceLocally"),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }

        Icon(
            imageVector = WrIcons.cloud,
            contentDescription = "Self-hosted Backend",
            modifier = Modifier.icon(configureSelfHostedBackend)
                .padding(2.dp)
                .testTag("configureSelfHostedBackend"),
            tint = MaterialTheme.colorScheme.onBackground
        )

        MoreOptions(
            showExtraOptions,
            showExtraOptionsRequest,
            hideExtraOptionsRequest,
            exportAsMarkdownClick,
            exportAsTxtClick,
            importClick
        )
    }
}

@Composable
private fun MoreOptions(
    showExtraOptions: StateFlow<Boolean>,
    showExtraOptionsRequest: () -> Unit,
    hideExtraOptionsRequest: () -> Unit,
    exportAsMarkdownClick: () -> Unit,
    exportAsTxtClick: () -> Unit,
    importClick: () -> Unit,
) {
    Box {
        Icon(
            imageVector = WrIcons.moreVert,
            contentDescription = "More options",
            modifier = Modifier.icon(showExtraOptionsRequest).padding(2.dp),
            tint = MaterialTheme.colorScheme.onBackground
        )

        val showExtra by showExtraOptions.collectAsState()

        DropdownMenu(expanded = showExtra, onDismissRequest = hideExtraOptionsRequest) {
            val iconTintColor = MaterialTheme.colorScheme.onBackground

            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = WrIcons.exportFile,
                        contentDescription = "Export file",
                        tint = iconTintColor
                    )
                },
                onClick = exportAsMarkdownClick,
                text = {
                    Text(WrStrings.exportMarkdown(), color = MaterialTheme.colorScheme.onBackground)
                }
            )

            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = WrIcons.exportFile,
                        contentDescription = "Export file",
                        tint = iconTintColor
                    )
                },
                onClick = exportAsTxtClick,
                text = {
                    Text(WrStrings.exportAsTxt(), color = MaterialTheme.colorScheme.onBackground)
                }
            )

            DropdownMenuItem(
                leadingIcon = {
                    Icon(
                        imageVector = WrIcons.fileDownload,
                        contentDescription = WrStrings.importFile(),
                        tint = iconTintColor
                    )
                },
                onClick = importClick,
                text = {
                    Text(WrStrings.importFile(), color = MaterialTheme.colorScheme.onBackground)
                }
            )
        }
    }
}

@Composable
private fun LoadingBox(showLoading: Boolean, content: @Composable () -> Unit) {
    Box(modifier = Modifier.size(38.dp)) {
        if (showLoading) {
            CircularProgressIndicator(
                modifier = Modifier.then(Modifier.size(34.dp).padding(8.dp))
                    .align(Alignment.Center),
                strokeWidth = 2.dp
            )
        } else {
            content()
        }
    }
}

@Preview
@Composable
private fun DesktopNoteActionsMenuPreview() {
    DesktopNoteActionsMenu(
        modifier = Modifier,
        showExtraOptions = MutableStateFlow(true),
        showExtraOptionsRequest = {},
        hideExtraOptionsRequest = {},
        exportAsMarkdownClick = {},
        importClick = {},
        syncInProgressState = MutableStateFlow(SyncState.Idle),
        onSyncLocallySelected = {},
        onWriteLocallySelected = {},
        exportAsTxtClick = {},
        onForceGraphSelected = {}
    )
}

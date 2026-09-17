package io.writeopia.update.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import io.writeopia.resources.WrStrings
import io.writeopia.update.di.DesktopUpdateInjection
import io.writeopia.update.viewmodel.DesktopUpdateUiState
import io.writeopia.update.viewmodel.DesktopUpdateViewModel

/**
 * Displays update UI driven by [DesktopUpdateViewModel].
 */
@Composable
fun BoxScope.DesktopUpdatePrompt(
    viewModel: DesktopUpdateViewModel = DesktopUpdateInjection.provideViewModel()
) {
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val updateCheckFailedMessage = WrStrings.updateCheckFailed()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (uiState == DesktopUpdateUiState.CheckFailed) {
            snackbarHostState.showSnackbar(updateCheckFailedMessage)
            viewModel.dismissCheckFailure()
        }
    }

    val available = uiState as? DesktopUpdateUiState.UpdateAvailable
    if (available != null) {
        DesktopUpdateDialog(
            update = available.update,
            openFailed = available.openFailed,
            onDownload = {
                try {
                    uriHandler.openUri(available.update.downloadUrl)
                    viewModel.onDownloadOpened()
                } catch (error: Exception) {
                    println(
                        "Failed to open desktop update URL ${available.update.downloadUrl}: " +
                            (error.message ?: error::class.simpleName)
                    )
                    error.printStackTrace()
                    viewModel.onDownloadOpenFailed()
                }
            },
            onDismiss = viewModel::dismissUpdate
        )
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}

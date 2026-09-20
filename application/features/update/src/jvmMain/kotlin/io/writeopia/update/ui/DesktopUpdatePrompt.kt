package io.writeopia.update.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import io.writeopia.commonui.snackbar.WriteopiaSnackbar
import io.writeopia.resources.WrStrings
import io.writeopia.update.di.DesktopUpdateInjection
import io.writeopia.update.viewmodel.DesktopUpdateUiState
import io.writeopia.update.viewmodel.DesktopUpdateViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val UPDATE_AVAILABLE_SNACKBAR_DURATION_MS = 10_000L

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
    val downloadUpdateLabel = WrStrings.downloadUpdate()
    val newVersionAvailableLabel = WrStrings.newVersionAvailable()
    val updateOpenFailedLabel = WrStrings.updateOpenFailed()
    val uiState by viewModel.uiState.collectAsState()
    val showErrorSnackbar by viewModel.showErrorSnackbar.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkForUpdate()
    }

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is DesktopUpdateUiState.UpdateAvailable -> {
                val message = buildString {
                    append(newVersionAvailableLabel)
                    append(" ")
                    append(state.update.latestVersion)

                    if (state.openFailed) {
                        append(". ")
                        append(updateOpenFailedLabel)
                    }
                }

                val timeoutJob = launch {
                    delay(UPDATE_AVAILABLE_SNACKBAR_DURATION_MS)
                    snackbarHostState.currentSnackbarData?.dismiss()
                }

                val result = snackbarHostState.showSnackbar(
                    message = message,
                    actionLabel = downloadUpdateLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Indefinite
                )

                timeoutJob.cancel()

                when (result) {
                    SnackbarResult.ActionPerformed -> {
                        try {
                            uriHandler.openUri(state.update.downloadUrl)
                            viewModel.onDownloadOpened()
                        } catch (_: Exception) {
                            viewModel.onDownloadOpenFailed()
                        }
                    }

                    SnackbarResult.Dismissed -> viewModel.dismissUpdate()
                }
            }

            DesktopUpdateUiState.CheckFailed -> {
                if (showErrorSnackbar) {
                    snackbarHostState.showSnackbar(updateCheckFailedMessage)
                }
                viewModel.dismissCheckFailure()
            }

            DesktopUpdateUiState.Checking, DesktopUpdateUiState.Idle -> Unit
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    ) { data ->
        if (data.visuals.actionLabel != null) {
            WriteopiaSnackbar(snackbarData = data)
        } else {
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

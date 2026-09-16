package io.writeopia.update.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import io.writeopia.resources.WrStrings
import io.writeopia.update.DesktopUpdateChecker
import io.writeopia.update.di.DesktopUpdateInjection
import io.writeopia.update.model.DesktopUpdate
import io.writeopia.update.model.DesktopUpdateCheckResult

/**
 * Checks for a newer desktop version and owns all UI related to the update prompt.
 */
@Composable
fun BoxScope.DesktopUpdatePrompt(
    checker: DesktopUpdateChecker = remember { DesktopUpdateInjection.provideChecker() }
) {
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val updateCheckFailedMessage = WrStrings.updateCheckFailed()

    var availableUpdate by remember { mutableStateOf<DesktopUpdate?>(null) }
    var updateOpenFailed by remember { mutableStateOf(false) }

    LaunchedEffect(checker) {
        when (val result = checker.checkForUpdate()) {
            is DesktopUpdateCheckResult.UpdateAvailable -> {
                updateOpenFailed = false
                availableUpdate = result.update
            }

            DesktopUpdateCheckResult.NoUpdate -> Unit

            is DesktopUpdateCheckResult.Failure -> {
                snackbarHostState.showSnackbar(updateCheckFailedMessage)
            }
        }
    }

    availableUpdate?.let { update ->
        DesktopUpdateDialog(
            update = update,
            openFailed = updateOpenFailed,
            onDownload = {
                try {
                    uriHandler.openUri(update.downloadUrl)
                    updateOpenFailed = false
                    availableUpdate = null
                } catch (error: Exception) {
                    println(
                        "Failed to open desktop update URL ${update.downloadUrl}: " +
                            (error.message ?: error::class.simpleName)
                    )
                    error.printStackTrace()
                    updateOpenFailed = true
                }
            },
            onDismiss = {
                updateOpenFailed = false
                availableUpdate = null
            }
        )
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}

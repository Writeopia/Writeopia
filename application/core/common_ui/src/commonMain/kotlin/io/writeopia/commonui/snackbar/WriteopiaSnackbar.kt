package io.writeopia.commonui.snackbar

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * App-wide styled [Snackbar]. Keeps a single, consistent look (colors matching
 * the AI task indicator) across every screen, while message, action and
 * duration stay configurable through [SnackbarData]/[androidx.compose.material3.SnackbarHostState].
 */
@Composable
fun WriteopiaSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier
) {
    Snackbar(
        snackbarData = snackbarData,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        actionColor = MaterialTheme.colorScheme.primary,
        dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

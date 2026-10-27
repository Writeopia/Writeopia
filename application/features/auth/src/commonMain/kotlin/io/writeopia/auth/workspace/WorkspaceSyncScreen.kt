package io.writeopia.auth.workspace

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.resources.WrStrings

/**
 * Shown after the login while the whole workspace is brought to this device. The person can
 * wait, or go to the documents and let the sync finish in the background. [DONE] is not drawn:
 * the caller leaves the screen as soon as the sync ends.
 */
@Composable
fun BoxScope.WorkspaceSyncScreen(
    state: WorkspaceSyncState,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(horizontal = 24.dp)
            .widthIn(max = 400.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (state) {
            WorkspaceSyncState.SYNCING -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)

                Spacer(modifier = Modifier.height(24.dp))

                BasicText(
                    text = WrStrings.workspaceSyncTitle(),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    ),
                )

                Spacer(modifier = Modifier.height(8.dp))

                BasicText(
                    text = WrStrings.workspaceSyncMessage(),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    ),
                )

                Spacer(modifier = Modifier.height(24.dp))

                CommonButton(
                    text = WrStrings.workspaceSyncInBackground(),
                    clickListener = onContinue
                )
            }

            WorkspaceSyncState.FAILED -> {
                BasicText(
                    text = WrStrings.workspaceSyncFailed(),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    ),
                )

                Spacer(modifier = Modifier.height(16.dp))

                CommonButton(
                    text = WrStrings.workspaceSyncContinue(),
                    clickListener = onContinue
                )
            }

            WorkspaceSyncState.DONE -> {}
        }
    }
}

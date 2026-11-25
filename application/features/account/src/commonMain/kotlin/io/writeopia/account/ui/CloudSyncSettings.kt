@file:OptIn(ExperimentalTime::class)

package io.writeopia.account.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.writeopia.commonui.buttons.CommonButton
import io.writeopia.core.folders.di.CloudSyncInjection
import io.writeopia.core.folders.sync.cloud.CloudSyncManager
import io.writeopia.core.folders.sync.cloud.CloudSyncStatus
import io.writeopia.resources.WrStrings
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The sync of the private space with the user's own cloud storage (Google Drive on desktop):
 * connect, sync now, disconnect. Nothing where the platform has none.
 */
@Composable
fun CloudSyncSettingsRoute(
    modifier: Modifier = Modifier,
    manager: CloudSyncManager = CloudSyncInjection.singleton().manager,
) {
    val status by manager.status.collectAsState()
    if (!status.available) return

    val scope = rememberCoroutineScope()
    var connecting by remember { mutableStateOf(false) }

    CloudSyncSettings(
        status = status,
        connecting = connecting,
        onConnect = {
            connecting = true
            scope.launch {
                manager.connect()
                connecting = false
            }
        },
        onSyncNow = manager::requestSync,
        onDisconnect = { scope.launch { manager.disconnect() } },
        modifier = modifier,
    )
}

@Composable
fun CloudSyncSettings(
    status: CloudSyncStatus,
    connecting: Boolean,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            WrStrings.cloudSyncTitle(status.providerName),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            WrStrings.cloudSyncDescription(status.providerName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        val stateText = when {
            status.needsReconnect -> WrStrings.cloudSyncReconnect(status.providerName)
            !status.connected -> null
            status.syncing -> WrStrings.cloudSyncSyncing()
            status.error != null -> WrStrings.cloudSyncError(status.error.orEmpty())
            status.lastSync != null -> WrStrings.cloudSyncLastSync(formatTime(status.lastSync!!))
            else -> WrStrings.cloudSyncNotSynced()
        }
        if (stateText != null) {
            Text(
                stateText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.error != null || status.needsReconnect) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                modifier = Modifier.testTag("CloudSync.state")
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Column(modifier = Modifier.width(IntrinsicSize.Max)) {
            if (!status.connected || status.needsReconnect) {
                CommonButton(
                    text = if (connecting) WrStrings.cloudSyncConnecting() else WrStrings.cloudSyncConnect(),
                    modifier = Modifier.fillMaxWidth().testTag("CloudSync.connect"),
                ) {
                    if (!connecting) onConnect()
                }
            } else {
                CommonButton(
                    text = WrStrings.cloudSyncSyncNow(),
                    modifier = Modifier.fillMaxWidth().testTag("CloudSync.syncNow"),
                ) {
                    onSyncNow()
                }

                Spacer(modifier = Modifier.height(4.dp))

                CommonButton(
                    text = WrStrings.cloudSyncDisconnect(),
                    modifier = Modifier.fillMaxWidth().testTag("CloudSync.disconnect"),
                ) {
                    onDisconnect()
                }
            }
        }
    }
}

private fun formatTime(instant: Instant): String {
    val time = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val date = "${time.year}-${pad(time.month.ordinal + 1)}-${pad(time.day)}"
    return "$date ${pad(time.hour)}:${pad(time.minute)}"
}

private fun pad(value: Int) = value.toString().padStart(2, '0')

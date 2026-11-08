package io.writeopia.account.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.date.formatBytes
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.serialization.response.StorageUsageResponse
import kotlinx.coroutines.flow.StateFlow

/** The storage of the user on the backend, as the settings show it. */
sealed class StorageUsageState {
    data object Loading : StorageUsageState()

    data class Success(val usage: StorageUsageResponse) : StorageUsageState()

    data class Error(val message: String) : StorageUsageState()
}

/**
 * How much of the storage the user has used: the bytes, the quota and a bar. Shared by the
 * desktop dialog and the phones' account screen. Loads itself when first shown.
 */
@Composable
fun StorageUsageSection(
    usageState: StateFlow<StorageUsageState>,
    loadUsage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by usageState.collectAsState()

    LaunchedEffect(Unit) {
        loadUsage()
    }

    Column(modifier = modifier) {
        Text(
            WrStrings.storage(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (val current = state) {
            is StorageUsageState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        WrStrings.loadingStorage(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            is StorageUsageState.Error -> {
                Text(
                    current.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            is StorageUsageState.Success -> {
                StorageUsageContent(current.usage)
            }
        }
    }
}

@Composable
private fun StorageUsageContent(usage: StorageUsageResponse) {
    val progress = if (usage.quotaBytes > 0) {
        (usage.usedBytes.toFloat() / usage.quotaBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column {
        Text(
            "${formatBytes(usage.usedBytes)} / ${formatBytes(usage.quotaBytes)}",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            WrStrings.storageUsedDescription(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (progress > 0.9f) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
            trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )

        if (progress >= 1f) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                WrStrings.storageFull(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

package io.writeopia.account.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.genai.model.AiUsageResponse
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

sealed class CloudAiUsageState {
    data object Loading : CloudAiUsageState()
    data class Success(val usage: AiUsageResponse) : CloudAiUsageState()
    data class Error(val message: String) : CloudAiUsageState()
}

@Composable
fun SettingsCloudAiScreen(
    usageState: StateFlow<CloudAiUsageState>,
    modifier: Modifier = Modifier,
) {
    val state by usageState.collectAsState()

    Column(
        modifier = modifier.fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = WrStrings.cloudAiUsage(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (val currentState = state) {
            is CloudAiUsageState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = WrStrings.loadingUsage(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            is CloudAiUsageState.Error -> {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = currentState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            is CloudAiUsageState.Success -> {
                UsageContent(currentState.usage)
            }
        }
    }
}

@Composable
private fun UsageContent(usage: AiUsageResponse) {
    val periodLabel = formatPeriodLabel(usage.periodStart, usage.periodEnd)

    Column {
        // Period header
        Text(
            text = periodLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Total tokens card (highlighted)
        UsageCard(
            title = WrStrings.totalTokens(),
            value = formatNumber(usage.totalTokens),
            icon = WrIcons.zap,
            isHighlighted = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Breakdown row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            UsageCard(
                title = WrStrings.inputTokens(),
                value = formatNumber(usage.totalInputTokens),
                icon = WrIcons.file,
                modifier = Modifier.weight(1f)
            )

            UsageCard(
                title = WrStrings.outputTokens(),
                value = formatNumber(usage.totalOutputTokens),
                icon = WrIcons.exportFile,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Request count
        UsageCard(
            title = WrStrings.requests(),
            value = formatNumber(usage.requestCount),
            icon = WrIcons.ai
        )

        if (usage.requestCount == 0L) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = WrStrings.noUsageData(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun UsageCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isHighlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isHighlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        WriteopiaTheme.colorScheme.optionsSelector
    }

    val contentColor = if (isHighlighted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onBackground
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = contentColor
        )
    }
}

private fun formatNumber(value: Long): String {
    return when {
        value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000.0)
        value >= 1_000 -> String.format("%.1fK", value / 1_000.0)
        else -> value.toString()
    }
}

private fun formatPeriodLabel(startMillis: Long, endMillis: Long): String {
    return try {
        val startInstant = Instant.fromEpochMilliseconds(startMillis)
        val localDateTime = startInstant.toLocalDateTime(TimeZone.currentSystemDefault())
        val monthName = localDateTime.month.name.lowercase().replaceFirstChar { it.uppercase() }
        "$monthName ${localDateTime.year}"
    } catch (e: Exception) {
        "Current Period"
    }
}

package io.writeopia.account.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import io.writeopia.common.utils.date.formatCompactNumber
import io.writeopia.common.utils.date.formatMonthYear
import io.writeopia.sdk.serialization.response.AiUsageResponse
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

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
    val periodLabel = formatMonthYear(usage.periodStart)
    val usedTokens = usage.totalTokens
    val quotaTokens = usage.quota
    val usageProgress = if (quotaTokens > 0) {
        (usedTokens.toFloat() / quotaTokens.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = usageProgress,
        animationSpec = tween(durationMillis = 800)
    )

    Column {
        // Period header
        Text(
            text = periodLabel,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Usage card with progress bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(WriteopiaTheme.colorScheme.optionsSelector)
                .padding(20.dp)
        ) {
            // Token count: "X / Y tokens used"
            Text(
                text = "${formatCompactNumber(usedTokens)} / ${formatCompactNumber(quotaTokens)}",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = WrStrings.tokensUsed(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (usageProgress > 0.9f) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Percentage
            Text(
                text = "${(usageProgress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

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

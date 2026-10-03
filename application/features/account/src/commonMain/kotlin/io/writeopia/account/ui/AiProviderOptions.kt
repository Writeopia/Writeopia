package io.writeopia.account.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.model.AiProvider
import io.writeopia.resources.WrStrings
import io.writeopia.theme.WriteopiaTheme
import kotlinx.coroutines.flow.StateFlow

const val AI_PROVIDER_OPTION_TEST_TAG = "settings.ai.provider"

/**
 * Lets the user pick who answers the AI commands, like the "Run AI with" picker of the Mac app.
 *
 * @param choicesState the providers the user can pick right now, in the order shown. Empty on a
 * phone that is signed out: only the hint to sign in is shown then.
 * @param localAiModelState the model of the local AI, blank until one is picked. Null when this
 * app has no local AI.
 * @param isOnline whether a session exists, so the cloud AI can answer.
 */
@Composable
fun AiProviderOptions(
    selectedProviderState: StateFlow<AiProvider>,
    choicesState: StateFlow<List<AiProvider>>,
    localAiModelState: StateFlow<String>?,
    isOnline: Boolean,
    selectProvider: (AiProvider) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected by selectedProviderState.collectAsState()
    val choices by choicesState.collectAsState()
    val localAiModel = localAiModelState?.collectAsState()?.value ?: ""

    Column(modifier = modifier) {
        Text(
            text = WrStrings.runAiWith(),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (choices.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                choices.forEachIndexed { index, provider ->
                    if (index > 0) Spacer(modifier = Modifier.width(10.dp))

                    ProviderOption(
                        modifier = Modifier.weight(1F),
                        title = provider.title(),
                        icon = provider.icon(),
                        isSelected = provider == selected,
                        onClick = { selectProvider(provider) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        Text(
            text = providerHint(
                selected = selected,
                choices = choices,
                localAiConfigured = localAiModel.isNotBlank(),
                isOnline = isOnline
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ProviderOption(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, Color.Transparent)
    }
    val tint = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        WriteopiaTheme.colorScheme.tintLight
    }

    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(WriteopiaTheme.colorScheme.optionsSelector)
            .border(border, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .semantics { testTag = "$AI_PROVIDER_OPTION_TEST_TAG.$title" }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun AiProvider.title(): String =
    when (this) {
        AiProvider.CLOUD -> WrStrings.cloudAi()
        AiProvider.LOCAL -> WrStrings.localAi()
    }

private fun AiProvider.icon(): ImageVector =
    when (this) {
        AiProvider.CLOUD -> WrIcons.cloudSync
        AiProvider.LOCAL -> WrIcons.download
    }

/** Says who really answers: the picked provider, or the one it falls back to. */
@Composable
private fun providerHint(
    selected: AiProvider,
    choices: List<AiProvider>,
    localAiConfigured: Boolean,
    isOnline: Boolean,
): String =
    when {
        // Nothing to pick: a signed out phone
        choices.isEmpty() -> WrStrings.aiProviderCloudOfflineOnlyHint()
        selected == AiProvider.CLOUD -> WrStrings.aiProviderCloudHint()
        localAiConfigured -> WrStrings.aiProviderLocalHint()
        isOnline -> WrStrings.aiProviderLocalUnconfiguredHint()
        else -> WrStrings.aiProviderLocalUnconfiguredOfflineHint()
    }

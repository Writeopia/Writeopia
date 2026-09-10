package io.writeopia.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.writeopia.common.utils.icons.WrIcons
import io.writeopia.model.LocalAiWizardState
import io.writeopia.model.ProviderInfo
import io.writeopia.model.WizardErrorType
import io.writeopia.resources.WrStrings
import io.writeopia.sdk.serialization.response.LocalAiAutoConfigResponse
import io.writeopia.sdk.serialization.response.ModelTier
import kotlinx.coroutines.flow.StateFlow

@Composable
fun LocalAiWizardDialog(
    wizardState: StateFlow<LocalAiWizardState>,
    onClose: () -> Unit,
    onSelectProviderAndModel: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by wizardState.collectAsState()

    if (state !is LocalAiWizardState.Closed) {
        Dialog(onDismissRequest = { if (state !is LocalAiWizardState.Downloading) onClose() }) {
            Card(modifier = modifier, shape = MaterialTheme.shapes.large) {
                Column(
                    modifier = Modifier
                        .padding(start = 30.dp, end = 30.dp, bottom = 16.dp, top = 24.dp)
                        .width(420.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (val currentState = state) {
                        is LocalAiWizardState.DetectingProviders -> {
                            DetectingProvidersContent()
                        }
                        is LocalAiWizardState.SelectingConfiguration -> {
                            SelectingConfigurationContent(
                                config = currentState.config,
                                availableProviders = currentState.availableProviders,
                                onSelectProviderAndModel = onSelectProviderAndModel,
                                onCancel = onClose
                            )
                        }
                        is LocalAiWizardState.Downloading -> {
                            DownloadingContent(
                                modelName = currentState.modelName
                            )
                        }
                        is LocalAiWizardState.Success -> {
                            SuccessContent(onClose = onClose)
                        }
                        is LocalAiWizardState.Error -> {
                            ErrorContent(
                                errorType = currentState.errorType,
                                customMessage = currentState.customMessage,
                                onClose = onClose
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectingProvidersContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 20.dp)
    ) {
        Text(
            WrStrings.detectingLocalAi(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        CircularProgressIndicator()
    }
}

@Composable
private fun SelectingConfigurationContent(
    config: LocalAiAutoConfigResponse,
    availableProviders: List<ProviderInfo>,
    onSelectProviderAndModel: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var selectedProviderUrl by remember {
        mutableStateOf(availableProviders.firstOrNull { it.isAvailable }?.url)
    }
    var selectedTierIndex by remember { mutableStateOf(config.defaultTierIndex) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            WrStrings.configureLocalAi(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Provider selection
        Text(
            WrStrings.selectProvider(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        availableProviders.forEach { provider ->
            ProviderItem(
                provider = provider,
                isSelected = selectedProviderUrl == provider.url,
                onSelect = { if (provider.isAvailable) selectedProviderUrl = provider.url }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Model tier selection
        Text(
            WrStrings.selectModelTier(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(200.dp)
        ) {
            itemsIndexed(config.modelTiers) { index, tier ->
                ModelTierItem(
                    tier = tier,
                    isSelected = selectedTierIndex == index,
                    isRecommended = index == config.defaultTierIndex,
                    onSelect = { selectedTierIndex = index }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action buttons
        Row {
            Text(
                WrStrings.cancel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onCancel)
                    .padding(6.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                WrStrings.downloadAndConfigure(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (selectedProviderUrl != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                },
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(enabled = selectedProviderUrl != null) {
                        selectedProviderUrl?.let { url ->
                            val modelName = config.modelTiers[selectedTierIndex].modelName
                            onSelectProviderAndModel(url, modelName)
                        }
                    }
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun ProviderItem(
    provider: ProviderInfo,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = provider.isAvailable, onClick = onSelect)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect,
            enabled = provider.isAvailable
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                provider.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (provider.isAvailable) {
                    MaterialTheme.colorScheme.onBackground
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                }
            )
            Text(
                if (provider.isAvailable) {
                    WrStrings.providerAvailable()
                } else {
                    WrStrings.providerNotDetected()
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (provider.isAvailable) {
                    Color(0xFF4CAF50)
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                }
            )
        }
    }
}

@Composable
private fun ModelTierItem(
    tier: ModelTier,
    isSelected: Boolean,
    isRecommended: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onSelect
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tier.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Medium
                )
                if (isRecommended) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "(${WrStrings.recommended()})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                tier.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            Text(
                tier.modelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun DownloadingContent(
    modelName: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 20.dp)
    ) {
        Text(
            WrStrings.downloadingModel(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            modelName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SuccessContent(
    onClose: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 20.dp)
    ) {
        Icon(
            imageVector = WrIcons.check,
            contentDescription = "Success",
            tint = Color(0xFF4CAF50),
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            WrStrings.localAiConfigured(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            WrStrings.done(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClose)
                .padding(6.dp)
        )
    }
}

@Composable
private fun ErrorContent(
    errorType: WizardErrorType,
    customMessage: String?,
    onClose: () -> Unit
) {
    val errorMessage = customMessage ?: when (errorType) {
        WizardErrorType.NO_PROVIDER_DETECTED -> WrStrings.errorNoProviderDetected()
        WizardErrorType.FETCH_CONFIG_FAILED -> WrStrings.errorFetchConfig()
        WizardErrorType.DOWNLOAD_FAILED -> WrStrings.errorDownloadModel()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 20.dp)
    ) {
        Icon(
            imageVector = WrIcons.close,
            contentDescription = "Error",
            tint = Color.Red,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            WrStrings.configurationFailed(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            WrStrings.close(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClose)
                .padding(6.dp)
        )
    }
}

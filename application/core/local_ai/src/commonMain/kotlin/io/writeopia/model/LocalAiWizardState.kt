package io.writeopia.model

import io.writeopia.sdk.serialization.response.LocalAiAutoConfigResponse

sealed class LocalAiWizardState {

    data object Closed : LocalAiWizardState()

    data object DetectingProviders : LocalAiWizardState()

    data class SelectingConfiguration(
        val config: LocalAiAutoConfigResponse,
        val availableProviders: List<ProviderInfo>
    ) : LocalAiWizardState()

    data class Error(val errorType: WizardErrorType, val customMessage: String? = null) : LocalAiWizardState()
}

enum class WizardErrorType {
    NO_PROVIDER_DETECTED,
    FETCH_CONFIG_FAILED,
    DOWNLOAD_FAILED
}

data class ProviderInfo(
    val name: String,
    val url: String,
    val isAvailable: Boolean
)

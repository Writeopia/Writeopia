package io.writeopia.ai

import io.writeopia.model.AiProvider
import io.writeopia.repository.UiConfigurationRepository

/** Where the provider the user picked is kept. */
interface AiProviderStore {
    suspend fun selectedProvider(userId: String): AiProvider?

    suspend fun saveProvider(userId: String, provider: AiProvider)
}

/** Keeps the choice with the other preferences of the user. */
class UiConfigurationAiProviderStore(
    private val uiConfigurationRepository: UiConfigurationRepository
) : AiProviderStore {

    override suspend fun selectedProvider(userId: String): AiProvider? =
        uiConfigurationRepository.getUiConfigurationEntity(userId)?.aiProvider

    override suspend fun saveProvider(userId: String, provider: AiProvider) {
        uiConfigurationRepository.updateConfiguration(userId) { config ->
            config.copy(aiProvider = provider)
        }
    }
}

/** A store that forgets everything, for tests and previews. */
class InMemoryAiProviderStore(private var provider: AiProvider? = null) : AiProviderStore {
    override suspend fun selectedProvider(userId: String): AiProvider? = provider

    override suspend fun saveProvider(userId: String, provider: AiProvider) {
        this.provider = provider
    }
}

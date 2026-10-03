package io.writeopia.ai

import io.writeopia.LocalAiRepository
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.genai.repository.GenAiRepository
import io.writeopia.model.AiProvider
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Holds the AI clients of the app and picks the one that answers, following the provider the user
 * selected in the settings, like the Mac app does.
 *
 * - [AiProvider.LOCAL] answers when a model is picked for the local AI. It falls back to the cloud.
 * - [AiProvider.CLOUD] answers in an online workspace with a session. It falls back to the local AI.
 *
 * Nothing answers when neither is ready, and [resolve] returns null.
 */
class AiClients(
    private val localAiRepository: LocalAiRepository?,
    private val genAiRepository: GenAiRepository?,
    private val authRepository: AuthRepository,
    private val providerStore: AiProviderStore,
) {

    /** The providers this app can offer. The phones and the web only have the cloud. */
    val availableProviders: List<AiProvider> =
        buildList {
            if (localAiRepository != null) add(AiProvider.LOCAL)
            add(AiProvider.CLOUD)
        }

    /** The provider used until the user picks one: the local AI wherever it exists. */
    val defaultProvider: AiProvider =
        if (localAiRepository != null) AiProvider.LOCAL else AiProvider.CLOUD

    val hasLocalAi: Boolean get() = localAiRepository != null

    /**
     * The providers the user can pick right now: the cloud only with a session in an online
     * workspace, like the Mac app offline. Empty on a phone that is signed out.
     */
    suspend fun offeredProviders(): List<AiProvider> {
        val cloudReady = isCloudAiReady()

        return availableProviders.filter { provider -> provider != AiProvider.CLOUD || cloudReady }
    }

    /** The provider in use: the saved one when it can be offered right now, else the default. */
    suspend fun selectedProvider(userId: String): AiProvider {
        val offered = offeredProviders()

        return providerStore.selectedProvider(userId)
            ?.takeIf { provider -> provider in offered }
            ?: offered.firstOrNull()
            ?: defaultProvider
    }

    suspend fun selectProvider(userId: String, provider: AiProvider) {
        providerStore.saveProvider(userId, provider)
        _providerChanges.emit(provider)
    }

    /** True when the local AI has a server and a model, so it can answer right now. */
    suspend fun isLocalAiReady(userId: String): Boolean = localAi(userId) != null

    /** True when the cloud AI can answer: a session in an online workspace. */
    suspend fun isCloudAiReady(): Boolean {
        if (!authRepository.isLoggedIn()) return false
        val workspaceId = authRepository.getWorkspace()?.id ?: return false

        return workspaceId != Workspace.disconnectedWorkspace().id
    }

    /** The AI that answers for [userId], or null when no provider is ready. */
    suspend fun resolve(userId: String): ResolvedAi? {
        val local = localAi(userId)
        val cloud = if (isCloudAiReady()) ResolvedAi.Cloud(genAiRepository) else null

        return when (selectedProvider(userId)) {
            AiProvider.LOCAL -> local ?: cloud
            AiProvider.CLOUD -> cloud ?: local
        }
    }

    private suspend fun localAi(userId: String): ResolvedAi.Local? {
        val repository = localAiRepository ?: return null
        val url = repository.getConfiguredUrl(userId)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val model = repository.getSelectedModel(userId)?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        return ResolvedAi.Local(repository, url, model)
    }

    companion object {
        private val _providerChanges = MutableSharedFlow<AiProvider>(extraBufferCapacity = 1)

        /**
         * Emits whenever the user picks a provider, from any screen. The settings and the editor
         * have their own [AiClients], so the choice is shared here for whoever has to react.
         */
        val providerChanges: SharedFlow<AiProvider> = _providerChanges.asSharedFlow()
    }
}

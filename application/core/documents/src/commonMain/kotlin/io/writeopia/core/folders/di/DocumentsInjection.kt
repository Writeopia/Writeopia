package io.writeopia.core.folders.di

import io.ktor.client.HttpClient
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.core.folders.api.DocumentsApi
import io.writeopia.core.folders.api.SelfHostedBackendManager
import io.writeopia.core.folders.sync.DocumentConflictHandler
import io.writeopia.core.folders.sync.DocumentsSync
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Class for injecting documents-related dependencies
 */
class DocumentsInjection(
    private val httpClient: HttpClient,
    private val documentRepository: DocumentRepository,
    private val configurationRepository: ConfigurationRepository,
    private val baseUrl: String = "https://writeopia.io"
) {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var selfHostedBackendManager: SelfHostedBackendManager? = null
    private var documentsApi: DocumentsApi? = null
    private var documentsSync: DocumentsSync? = null

    private val _isSyncEnabled = MutableStateFlow(false)
    val isSyncEnabled = _isSyncEnabled.asStateFlow()

    init {
        coroutineScope.launch {
            val userId = "disconnected_user" // This should be replaced with actual user ID
            val selfHostedUrl = configurationRepository.loadSelfHostedBackendUrl(userId)

            if (selfHostedUrl != null) {
                provideSelfHostedBackendManager().setBackendUrl(selfHostedUrl)
                _isSyncEnabled.value = true
            }
        }
    }

    fun provideSelfHostedBackendManager(): SelfHostedBackendManager {
        return selfHostedBackendManager ?: SelfHostedBackendManager(httpClient).also {
            selfHostedBackendManager = it
        }
    }

    fun provideDocumentsApi(): DocumentsApi {
        return documentsApi ?: DocumentsApi(
            client = httpClient,
            baseUrl = baseUrl,
            selfHostedBackendManager = provideSelfHostedBackendManager()
        ).also {
            documentsApi = it
        }
    }

    fun provideDocumentsSync(): DocumentsSync {
        return documentsSync ?: DocumentsSync(
            documentRepository = documentRepository,
            documentsApi = provideDocumentsApi(),
            documentConflictHandler = DocumentConflictHandler(documentRepository),
            selfHostedBackendManager = provideSelfHostedBackendManager()
        ).also {
            documentsSync = it
        }
    }

    /**
     * Connects to a self-hosted backend
     */
    suspend fun connectToSelfHostedBackend(url: String): Boolean {
        val manager = provideSelfHostedBackendManager()
        val result = manager.testConnection(url)

        if (result is io.writeopia.common.utils.ResultData.Complete && result.data) {
            val userId = "disconnected_user" // This should be replaced with actual user ID
            configurationRepository.saveSelfHostedBackendUrl(url, userId)
            _isSyncEnabled.value = true
            return true
        }

        return false
    }

    /**
     * Disconnects from the current self-hosted backend
     */
    suspend fun disconnectFromSelfHostedBackend() {
        val userId = "disconnected_user" // This should be replaced with actual user ID
        configurationRepository.clearSelfHostedBackendUrl(userId)
        provideSelfHostedBackendManager().disconnect()
        _isSyncEnabled.value = false
    }
}

package io.writeopia.core.folders.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import io.writeopia.common.utils.ResultData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages connections to self-hosted backends
 */
class SelfHostedBackendManager(
    private val client: HttpClient
) {
    private val _connectionState = MutableStateFlow<SelfHostedConnectionState>(SelfHostedConnectionState.NotConnected)
    val connectionState: StateFlow<SelfHostedConnectionState> = _connectionState.asStateFlow()

    /**
     * Tests the connection to a self-hosted backend
     */
    suspend fun testConnection(url: String): ResultData<Boolean> {
        return try {
            _connectionState.value = SelfHostedConnectionState.Connecting

            val response = client.get("$url/api/self-hosted/status")

            if (response.status.isSuccess()) {
                val statusResponse = response.body<Map<String, String>>()

                if (statusResponse["status"] == "running") {
                    _connectionState.value = SelfHostedConnectionState.Connected(url)
                    ResultData.Complete(true)
                } else {
                    _connectionState.value = SelfHostedConnectionState.Error("Backend is not running properly")
                    ResultData.Error("Backend is not running properly")
                }
            } else {
                _connectionState.value = SelfHostedConnectionState.Error("Could not connect to backend")
                ResultData.Error("Could not connect to backend")
            }
        } catch (e: Exception) {
            _connectionState.value = SelfHostedConnectionState.Error(e.message ?: "Unknown error")
            ResultData.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * Sets the current backend URL for the app
     */
    fun setBackendUrl(url: String) {
        _connectionState.value = SelfHostedConnectionState.Connected(url)
    }

    /**
     * Disconnects from the current backend
     */
    fun disconnect() {
        _connectionState.value = SelfHostedConnectionState.NotConnected
    }
}

sealed class SelfHostedConnectionState {
    data object NotConnected : SelfHostedConnectionState()
    data object Connecting : SelfHostedConnectionState()
    data class Connected(val url: String) : SelfHostedConnectionState()
    data class Error(val message: String) : SelfHostedConnectionState()
}

package io.writeopia.core.folders.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.common.utils.ResultData
import io.writeopia.sdk.models.api.request.documents.FolderDiffRequest
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Instant

class DocumentsApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val selfHostedBackendManager: SelfHostedBackendManager? = null
) {

    private suspend fun getEffectiveBaseUrl(): String {
        // If there's a connected self-hosted backend, use that URL instead
        val connectionState = selfHostedBackendManager?.connectionState?.firstOrNull()
        return if (connectionState is SelfHostedConnectionState.Connected) {
            connectionState.url
        } else {
            baseUrl
        }
    }

    suspend fun getNewDocuments(folderId: String, lastSync: Instant): ResultData<List<Document>> {
        val effectiveBaseUrl = getEffectiveBaseUrl()
        val response = client.post("$effectiveBaseUrl/api/document/folder/diff") {
            contentType(ContentType.Application.Json)
            setBody(FolderDiffRequest(folderId, lastSync.toEpochMilliseconds()))
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(response.body<List<DocumentApi>>().map { it.toModel() })
        } else {
            println("getNewDocuments status: ${response.status.value}")
            ResultData.Error()
        }
    }

    suspend fun sendDocuments(documents: List<Document>): ResultData<Unit> {
        val effectiveBaseUrl = getEffectiveBaseUrl()
        val response = client.post("$effectiveBaseUrl/api/document") {
            contentType(ContentType.Application.Json)
            setBody(documents.map { it.toApi() })
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            println("sendDocuments status: ${response.status.value}")
            ResultData.Error()
        }
    }
}

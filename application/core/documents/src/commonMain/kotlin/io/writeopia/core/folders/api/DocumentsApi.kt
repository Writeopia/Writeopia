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
import kotlinx.datetime.Instant

class DocumentsApi(private val client: HttpClient, private val baseUrl: String) {

    suspend fun getNewDocuments(folderId: String, lastSync: Instant): ResultData<List<Document>> {
        val response = client.post("$baseUrl/api/document/folder/diff") {
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
        val response = client.post("$baseUrl/api/document") {
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

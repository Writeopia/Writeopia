package io.writeopia.core.folders.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.sdk.models.api.request.documents.FolderDiffRequest
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.json.SendDocumentsRequest
import io.writeopia.sdk.serialization.json.SendFoldersRequest
import io.writeopia.sdk.serialization.request.WorkspaceDiffRequest
import io.writeopia.sdk.serialization.request.WorkspaceDiffResponse
import kotlinx.datetime.Instant

class DocumentsApi(private val client: HttpClient, private val baseUrl: String) {

    suspend fun getFolderNewDocuments(
        folderId: String,
        workspaceId: String,
        lastSync: Instant,
        token: String
    ): ResultData<List<Document>> {
        val response = client.post("$baseUrl/api/document/folder/diff") {
            contentType(ContentType.Application.Json)
            setBody(FolderDiffRequest(folderId, workspaceId, lastSync.toEpochMilliseconds()))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(response.body<List<DocumentApi>>().map { it.toModel() })
        } else {
            ResultData.Error()
        }
    }

    suspend fun getWorkspaceNewData(
        workspaceId: String,
        lastSync: Instant,
        token: String
    ): ResultData<Pair<List<Document>, List<Folder>>> {
        val url = "$baseUrl/api/workspace/diff"
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(WorkspaceDiffRequest(workspaceId, lastSync.toEpochMilliseconds()))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            val (foldersApi, documentsApi) = response.body<WorkspaceDiffResponse>()
            val documents = documentsApi.map { it.toModel() }
            val folders = foldersApi.map { it.toModel() }

            ResultData.Complete(documents to folders)
        } else {
            println("response error: $response")
            ResultData.Error()
        }
    }

    suspend fun sendDocuments(
        documents: List<Document>,
        workspaceId: String,
        token: String
    ): ResultData<Unit> {
        val response = client.post("$baseUrl/api/workspace/document") {
            contentType(ContentType.Application.Json)
            setBody(SendDocumentsRequest(documents.map { it.toApi() }, workspaceId))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            println("error sending documents: $response")
            ResultData.Error()
        }
    }

    suspend fun sendFolders(
        folders: List<Folder>,
        workspaceId: String,
        token: String
    ): ResultData<Unit> {
        val response = client.post("$baseUrl/api/workspace/folder") {
            contentType(ContentType.Application.Json)
            setBody(SendFoldersRequest(folders.map { it.toApi() }, workspaceId))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            println("error sending folders: $response")
            ResultData.Error()
        }
    }
}

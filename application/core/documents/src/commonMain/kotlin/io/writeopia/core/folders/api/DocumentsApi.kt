@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import io.writeopia.sdk.serialization.data.FolderApi
import io.writeopia.sdk.serialization.data.IconApi
import io.writeopia.sdk.serialization.request.UpdateFolderRequest
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.json.SendDocumentsRequest
import io.writeopia.sdk.serialization.json.SendFoldersRequest
import io.writeopia.sdk.serialization.request.CloneDocumentsRequest
import io.writeopia.sdk.serialization.request.CreateFolderRequest
import io.writeopia.sdk.serialization.request.DeleteDocumentsRequest
import io.writeopia.sdk.serialization.request.EventDiffRequest
import io.writeopia.sdk.serialization.request.FavoriteDocumentRequest
import io.writeopia.sdk.serialization.request.MoveDocumentRequest
import io.writeopia.sdk.serialization.request.MoveFolderRequest
import io.writeopia.sdk.serialization.request.WorkspaceDiffRequest
import io.writeopia.sdk.serialization.response.EventDiffResponse
import io.writeopia.sdk.serialization.response.FolderContentResponse
import io.writeopia.sdk.serialization.response.WorkspaceDiffResponse
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DocumentsApi(private val client: HttpClient, private val baseUrl: String) {

    suspend fun getFolderNewData(
        folderId: String,
        workspaceId: String,
        lastSync: Instant,
        token: String,
        orderBy: String = "last_updated_at"
    ): ResultData<FolderContentResponse> {
        val response = client.post("$baseUrl/api/docs/workspace/document/folder/diff") {
            contentType(ContentType.Application.Json)
            setBody(FolderDiffRequest(folderId, workspaceId, lastSync.toEpochMilliseconds(), orderBy))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(response.body<FolderContentResponse>())
        } else {
            println("getFolderNewData failed. response: $response")
            ResultData.Error()
        }
    }

    suspend fun getWorkspaceNewData(
        workspaceId: String,
        lastSync: Instant,
        token: String,
        orderBy: String = "last_updated_at"
    ): ResultData<Pair<List<Document>, List<Folder>>> {
        val url = "$baseUrl/api/docs/workspace/diff"
        val response = client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(WorkspaceDiffRequest(workspaceId, lastSync.toEpochMilliseconds(), orderBy))
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
        val response = client.post("$baseUrl/api/docs/workspace/document") {
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
        val response = client.post("$baseUrl/api/docs/workspace/folder") {
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

    suspend fun createFolder(
        parentFolderId: String,
        title: String,
        workspaceId: String,
        icon: IconApi? = null,
        token: String
    ): ResultData<Folder> {
        val response = client.post("$baseUrl/api/docs/workspace/$workspaceId/folder/$parentFolderId/create") {
            contentType(ContentType.Application.Json)
            setBody(CreateFolderRequest(title, icon))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(response.body<FolderApi>().toModel())
        } else {
            println("error creating folder: $response")
            ResultData.Error()
        }
    }

    suspend fun updateFolder(
        folderId: String,
        workspaceId: String,
        title: String? = null,
        icon: IconApi? = null,
        favorite: Boolean? = null,
        token: String
    ): ResultData<Folder> {
        val response = client.put("$baseUrl/api/docs/workspace/$workspaceId/folder/$folderId") {
            contentType(ContentType.Application.Json)
            setBody(UpdateFolderRequest(title = title, icon = icon, favorite = favorite))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(response.body<FolderApi>().toModel())
        } else {
            println("error updating folder: $response")
            ResultData.Error()
        }
    }

    suspend fun getFolderContents(
        folderId: String,
        workspaceId: String,
        token: String
    ): ResultData<FolderContentResponse> {
        val response = client.get("$baseUrl/api/docs/workspace/$workspaceId/folder/$folderId/contents") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(response.body<FolderContentResponse>())
        } else {
            println("error getting folder contents: $response")
            ResultData.Error()
        }
    }

    suspend fun deleteFolder(
        folderId: String,
        workspaceId: String,
        token: String
    ): ResultData<Unit> {
        val response = client.delete("$baseUrl/api/docs/workspace/$workspaceId/folder/$folderId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            println("error deleting folder: $response")
            ResultData.Error()
        }
    }

    suspend fun deleteDocuments(
        documentIds: List<String>,
        workspaceId: String,
        token: String
    ): ResultData<Unit> {
        val response = client.post("$baseUrl/api/docs/workspace/$workspaceId/document/delete") {
            contentType(ContentType.Application.Json)
            setBody(DeleteDocumentsRequest(documentIds))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            println("error deleting documents: $response")
            ResultData.Error()
        }
    }

    suspend fun moveFolder(
        folderId: String,
        targetParentId: String,
        workspaceId: String,
        token: String
    ): ResultData<Unit> {
        val response = client.post("$baseUrl/api/docs/workspace/$workspaceId/folder/$folderId/move") {
            contentType(ContentType.Application.Json)
            setBody(MoveFolderRequest(targetParentId))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            println("error moving folder: $response")
            ResultData.Error()
        }
    }

    suspend fun favoriteDocument(
        documentId: String,
        favorite: Boolean,
        workspaceId: String,
        token: String
    ): ResultData<Unit> {
        val response = client.post("$baseUrl/api/docs/workspace/$workspaceId/document/$documentId/favorite") {
            contentType(ContentType.Application.Json)
            setBody(FavoriteDocumentRequest(favorite))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            println("error favoriting document: $response")
            ResultData.Error()
        }
    }

    suspend fun cloneDocuments(
        documentIds: List<String>,
        workspaceId: String,
        token: String
    ): ResultData<List<Document>> {
        val response = client.post("$baseUrl/api/docs/workspace/$workspaceId/document/clone") {
            contentType(ContentType.Application.Json)
            setBody(CloneDocumentsRequest(documentIds))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(response.body<List<DocumentApi>>().map { it.toModel() })
        } else {
            println("error cloning documents: $response")
            ResultData.Error()
        }
    }

    suspend fun getUserFavorites(
        workspaceId: String,
        token: String
    ): ResultData<List<String>> {
        val response = client.get("$baseUrl/api/docs/workspace/$workspaceId/user/favorites") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(response.body<List<String>>())
        } else {
            println("error getting user favorites: $response")
            ResultData.Error()
        }
    }

    suspend fun getDocumentById(
        documentId: String,
        workspaceId: String,
        token: String
    ): ResultData<Document> {
        val response = client.get("$baseUrl/api/docs/workspace/$workspaceId/document/$documentId") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(response.body<DocumentApi>().toModel())
        } else {
            println("error getting document by id: $response")
            ResultData.Error()
        }
    }

    suspend fun getPublishedDocument(documentId: String): ResultData<Document> {
        val response = client.get("$baseUrl/api/site/$documentId")

        return if (response.status.isSuccess()) {
            ResultData.Complete(response.body<DocumentApi>().toModel())
        } else {
            println("error getting published document: $response")
            ResultData.Error()
        }
    }

    suspend fun publishDocument(
        documentId: String,
        workspaceId: String,
        token: String
    ): ResultData<Unit> {
        val response = client.post("$baseUrl/api/docs/workspace/$workspaceId/document/$documentId/publish") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            println("error publishing document: $response")
            ResultData.Error()
        }
    }

    suspend fun unpublishDocument(
        documentId: String,
        workspaceId: String,
        token: String
    ): ResultData<Unit> {
        val response = client.post("$baseUrl/api/docs/workspace/$workspaceId/document/$documentId/unpublish") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            println("error unpublishing document: $response")
            ResultData.Error()
        }
    }

    suspend fun isDocumentPublished(
        documentId: String,
        workspaceId: String,
        token: String
    ): ResultData<Boolean> {
        val response = client.get("$baseUrl/api/docs/workspace/$workspaceId/document/$documentId/published") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            val body = response.body<Map<String, Boolean>>()
            ResultData.Complete(body["published"] ?: false)
        } else {
            println("error checking document published status: $response")
            ResultData.Error()
        }
    }

    suspend fun getEventsDiff(
        workspaceId: String,
        lastEventSync: Long,
        token: String
    ): ResultData<EventDiffResponse> {
        val response = client.post("$baseUrl/api/docs/workspace/events/diff") {
            contentType(ContentType.Application.Json)
            setBody(EventDiffRequest(workspaceId, lastEventSync))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(response.body<EventDiffResponse>())
        } else {
            println("error getting events diff: $response")
            ResultData.Error()
        }
    }

    suspend fun moveDocument(
        documentId: String,
        targetParentId: String,
        workspaceId: String,
        token: String
    ): ResultData<Unit> {
        val response = client.post("$baseUrl/api/docs/workspace/$workspaceId/document/$documentId/move") {
            contentType(ContentType.Application.Json)
            setBody(MoveDocumentRequest(targetParentId))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            println("error moving document: $response")
            ResultData.Error()
        }
    }
}

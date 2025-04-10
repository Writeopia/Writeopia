package io.writeopia.core.folders.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import io.writeopia.common.utils.ResultData
import io.writeopia.sdk.models.api.request.documents.FolderDiffRequest
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.serialization.extensions.toApi
import kotlinx.datetime.Instant

class DocumentsApi(private val client: HttpClient) {

    suspend fun getNewDocuments(folderId: String, lastSync: Instant): ResultData<List<Document>> {
        val response = client.post("/api/document/folder/diff") {
            setBody(FolderDiffRequest(folderId, lastSync.toEpochMilliseconds()))
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(response.body())
        } else {
            ResultData.Error()
        }
    }

    suspend fun sendDocuments(documents: List<Document>): ResultData<Unit> {
        val response = client.post("/api/document") {
            setBody(documents.map { it.toApi() })
        }

        return if (response.status.isSuccess()) ResultData.Complete(Unit) else ResultData.Error()
    }
}


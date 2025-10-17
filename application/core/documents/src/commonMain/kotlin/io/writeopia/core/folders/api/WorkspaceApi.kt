package io.writeopia.core.folders.api

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.app.requests.AddUserToWorkspaceRequest
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Role

class WorkspaceApi(private val client: HttpClient, private val baseUrl: String) {

    suspend fun addUserToWorkspace(
        workspaceId: String,
        userEmail: String,
        token: String
    ): ResultData<Unit> {
        val response = client.post("$baseUrl/api/document/folder/diff") {
            contentType(ContentType.Application.Json)
            setBody(AddUserToWorkspaceRequest(userEmail, workspaceId, Role.EDITOR.value))

            header(HttpHeaders.Authorization, "Bearer $token")
        }

        return if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            ResultData.Error()
        }
    }
}

package io.writeopia.core.folders.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
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
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.serialization.data.WorkspaceApi
import io.writeopia.sdk.serialization.data.toModel

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

    suspend fun getAvailableWorkspaces(token: String): ResultData<List<Workspace>> {
        return try {
            val response = client.get("$baseUrl/api/workspace/user") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body<List<WorkspaceApi>>()

            ResultData.Complete(response.map { workspaceApi -> workspaceApi.toModel() })
        } catch (e: Exception) {
            ResultData.Error(e)
        }
    }

    suspend fun usersOfWorkspace(
        workspaceId: String,
        token: String
    ): ResultData<List<String>> {
        return try {
            val response = client.get("$baseUrl/api/user/workspace/${workspaceId}") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            ResultData.Complete(response.body())
        } catch (e: Exception) {
            ResultData.Error(e)
        }
    }

}

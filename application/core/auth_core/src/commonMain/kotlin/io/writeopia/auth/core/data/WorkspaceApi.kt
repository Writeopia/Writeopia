package io.writeopia.auth.core.data

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
import io.writeopia.app.dto.WorkspaceUserApi
import io.writeopia.app.requests.AddUserToWorkspaceRequest
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Role
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.serialization.data.WorkspaceApi
import io.writeopia.sdk.serialization.data.toModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock

class WorkspaceApi(private val client: HttpClient, private val baseUrl: String) {

    private val workspaceUsersCache = MutableStateFlow<ResultData<List<String>>>(ResultData.Idle())

    suspend fun addUserToWorkspace(
        workspaceId: String,
        userEmail: String,
        token: String
    ): ResultData<Unit> {
        val cache = workspaceUsersCache.value
        workspaceUsersCache.value = ResultData.Loading()

        val response = client.post("$baseUrl/api/workspace/user") {
            contentType(ContentType.Application.Json)
            setBody(AddUserToWorkspaceRequest(userEmail, workspaceId, Role.EDITOR.value))

            header(HttpHeaders.Authorization, "Bearer $token")
        }

        workspaceUsersCache.value = cache

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

            val now = Clock.System.now()

            ResultData.Complete(
                response.map { workspaceApi -> workspaceApi.toModel(now) }
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ResultData.Error(e)
        }
    }

    suspend fun getUsersOfWorkspace(
        workspaceId: String,
        token: String,
        forceRefresh: Boolean = false
    ): StateFlow<ResultData<List<String>>> {
        val cache = workspaceUsersCache.value

        if (!forceRefresh && cache is ResultData.Complete) {
            return workspaceUsersCache
        }

        try {
            workspaceUsersCache.value = ResultData.Loading()

            val response = client.get("$baseUrl/api/user/workspaces/$workspaceId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            val users = response.body<List<WorkspaceUserApi>>().map { it.name }

            workspaceUsersCache.value = ResultData.Complete(users)
        } catch (e: Exception) {
            if (cache !is ResultData.Complete) {
                workspaceUsersCache.value = ResultData.Error(e)
            }
        }

        return workspaceUsersCache
    }

    suspend fun refreshUsersInWorkspace(workspaceId: String, token: String) {
        val cache = workspaceUsersCache.value

        try {
            workspaceUsersCache.value = ResultData.Loading()

            val response = client.get("$baseUrl/api/user/workspaces/$workspaceId") {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            val users = response.body<List<WorkspaceUserApi>>().map { it.name }

            workspaceUsersCache.value = ResultData.Complete(users)
        } catch (e: Exception) {
            if (cache !is ResultData.Complete) {
                workspaceUsersCache.value = ResultData.Error(e)
            }
        }
    }
}

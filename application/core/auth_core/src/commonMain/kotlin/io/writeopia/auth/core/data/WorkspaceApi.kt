@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.core.data

import io.ktor.client.HttpClient
import io.writeopia.auth.core.exceptions.LastAdminException
import io.writeopia.auth.core.exceptions.UserAlreadyInWorkspaceException
import io.writeopia.auth.core.exceptions.UserNotFoundException
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.writeopia.app.dto.PaginatedUserSearchResponse
import io.writeopia.app.dto.PaginatedWorkspaceUsersResponse
import io.writeopia.app.dto.WorkspaceUserApi
import io.writeopia.app.requests.AddUserToWorkspaceRequest
import io.writeopia.app.requests.CreateWorkspaceRequest
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Role
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.serialization.data.WorkspaceApi
import io.writeopia.sdk.serialization.data.toModel
import io.writeopia.sdk.serialization.request.WorkspaceRoleChangeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.ExperimentalTime

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

        return when {
            response.status.isSuccess() -> ResultData.Complete(Unit)
            response.status == HttpStatusCode.Conflict ->
                ResultData.Error(UserAlreadyInWorkspaceException())
            response.status == HttpStatusCode.NotFound ->
                ResultData.Error(UserNotFoundException())
            else -> ResultData.Error()
        }
    }

    suspend fun getAvailableWorkspaces(token: String): ResultData<List<Workspace>> = try {
        val workspaces = client.get("$baseUrl/api/workspace/user") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<List<WorkspaceApi>>()

        // Use default lastSync (DISTANT_PAST) so new workspaces will fetch all data on first sync.
        // The actual lastSync should be updated from server timestamps after successful syncs.
        ResultData.Complete(
            workspaces.map { workspaceApi -> workspaceApi.toModel() }
        )
    } catch (e: Exception) {
        e.printStackTrace()
        ResultData.Error(e)
    }

    suspend fun createWorkspace(workspaceName: String, token: String): ResultData<Unit> = try {
        val response = client.post("$baseUrl/api/workspace/create") {
            contentType(ContentType.Application.Json)
            setBody(CreateWorkspaceRequest(workspaceName))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            ResultData.Error()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        ResultData.Error(e)
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

            val response = client.get("$baseUrl/api/workspaces/$workspaceId/users") {
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

    suspend fun getUsersOfWorkspacePaginated(
        workspaceId: String,
        page: Int,
        pageSize: Int,
        token: String
    ): ResultData<PaginatedWorkspaceUsersResponse> = try {
        val response = client.get("$baseUrl/api/workspace/$workspaceId/users/paginated") {
            url {
                parameters.append("page", page.toString())
                parameters.append("pageSize", pageSize.toString())
            }
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        if (response.status.isSuccess()) {
            ResultData.Complete(response.body<PaginatedWorkspaceUsersResponse>())
        } else {
            ResultData.Error()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        ResultData.Error(e)
    }

    suspend fun searchUsers(
        workspaceId: String,
        emailQuery: String,
        page: Int,
        pageSize: Int,
        token: String
    ): ResultData<PaginatedUserSearchResponse> = try {
        val response = client.get("$baseUrl/api/workspace/$workspaceId/users/search") {
            url {
                parameters.append("email", emailQuery)
                parameters.append("page", page.toString())
                parameters.append("pageSize", pageSize.toString())
            }
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        if (response.status.isSuccess()) {
            ResultData.Complete(response.body<PaginatedUserSearchResponse>())
        } else {
            ResultData.Error()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        ResultData.Error(e)
    }

    suspend fun addUserToWorkspaceWithRole(
        workspaceId: String,
        userEmail: String,
        role: Role,
        token: String
    ): ResultData<Unit> {
        val cache = workspaceUsersCache.value
        workspaceUsersCache.value = ResultData.Loading()

        val response = client.post("$baseUrl/api/workspace/user") {
            contentType(ContentType.Application.Json)
            setBody(AddUserToWorkspaceRequest(userEmail, workspaceId, role.value))

            header(HttpHeaders.Authorization, "Bearer $token")
        }

        workspaceUsersCache.value = cache

        return when {
            response.status.isSuccess() -> ResultData.Complete(Unit)
            response.status == HttpStatusCode.Conflict ->
                ResultData.Error(UserAlreadyInWorkspaceException())
            response.status == HttpStatusCode.NotFound ->
                ResultData.Error(UserNotFoundException())
            else -> ResultData.Error()
        }
    }

    suspend fun changeUserRole(
        workspaceId: String,
        userId: String,
        newRole: Role,
        token: String
    ): ResultData<Unit> = try {
        val response = client.put("$baseUrl/api/workspace/role") {
            contentType(ContentType.Application.Json)
            setBody(WorkspaceRoleChangeRequest(workspaceId, userId, newRole.value))
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        when {
            response.status.isSuccess() -> ResultData.Complete(Unit)
            response.status == HttpStatusCode.Conflict -> ResultData.Error(LastAdminException())
            else -> ResultData.Error()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        ResultData.Error(e)
    }

    suspend fun exportWorkspace(
        workspaceId: String,
        token: String
    ): ResultData<Unit> = try {
        val response = client.post("$baseUrl/api/workspace/$workspaceId/export") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            ResultData.Error()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        ResultData.Error(e)
    }

    suspend fun initializeTutorials(
        workspaceId: String,
        token: String
    ): ResultData<Unit> = try {
        val response = client.post("$baseUrl/api/docs/workspace/$workspaceId/tutorials/initialize") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

        if (response.status.isSuccess()) {
            ResultData.Complete(Unit)
        } else {
            ResultData.Error()
        }
    } catch (e: Exception) {
        e.printStackTrace()
        ResultData.Error(e)
    }
}

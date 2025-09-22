package io.writeopia.api.core.auth.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.writeopia.api.core.auth.WorkspaceService
import io.writeopia.api.core.auth.models.AddUserToWorkspaceRequest
import io.writeopia.api.core.auth.models.ManageUserRequest
import io.writeopia.api.core.auth.repository.disableUserByEmail
import io.writeopia.api.core.auth.repository.enableUserByEmail
import io.writeopia.api.core.auth.repository.listWorkspaces
import io.writeopia.sdk.serialization.data.toApi
import io.writeopia.sql.WriteopiaDbBackend

fun Route.adminProtectedRoute(
    apiKey: String?,
    writeopiaDb: WriteopiaDbBackend,
    debugMode: Boolean = false,
) {
    route("/admin") {
        post<ManageUserRequest>("/enable-user") { request ->
            val providedKey = if (debugMode) "debug" else call.request.header("X-Admin-Key")
            adminUserFn(apiKey, providedKey, debugMode) {
                writeopiaDb.enableUserByEmail(request.email)
                call.respond(HttpStatusCode.OK, "User enabled")
            }
        }

        post<ManageUserRequest>("/disable-user") { request ->
            val providedKey = if (debugMode) "debug" else call.request.header("X-Admin-Key")
            adminUserFn(apiKey, providedKey, debugMode) {
                writeopiaDb.disableUserByEmail(request.email)
                call.respond(HttpStatusCode.OK, "User disabled")
            }
        }

        get("/workspace") {
            val providedKey = if (debugMode) "debug" else call.request.header("X-Admin-Key")
            adminUserFn(apiKey, providedKey, debugMode) {
                val workspaces = writeopiaDb.listWorkspaces().map { it.toApi() }
                call.respond(HttpStatusCode.OK, workspaces)
            }
        }

        get("/workspace/user/{userEmail}") {
            val providedKey = if (debugMode) "debug" else call.request.header("X-Admin-Key")
            adminUserFn(apiKey, providedKey, debugMode) {
                val userEmail = call.pathParameters["userEmail"]
                    ?: throw IllegalArgumentException("User email is required")

                val workspaces = WorkspaceService.getWorkspacesByUserEmail(userEmail, writeopiaDb)
                    .map { it.toApi() }

                if (workspaces.isNotEmpty()) {
                    call.respond(HttpStatusCode.OK, workspaces)
                } else {
                    call.respond(HttpStatusCode.NotFound, "No workspaces found for user")
                }
            }
        }

        post<AddUserToWorkspaceRequest>("workspace/user") { request ->
            val providedKey = if (debugMode) "debug" else call.request.header("X-Admin-Key")

            adminUserFn(apiKey, providedKey, debugMode) {
                val (userEmail, workspaceId, role) = request
                val result = WorkspaceService.addUserToWorkspaceAdmin(
                    userEmail,
                    workspaceId,
                    role,
                    writeopiaDb
                )

                if (result) {
                    call.respond(HttpStatusCode.OK, "User added to workspace")
                } else {
                    call.respond(HttpStatusCode.NotFound, "Not added")
                }
            }
        }

        delete("/api/workspace/{workspaceId}/user/{userId}") {
            val providedKey = if (debugMode) "debug" else call.request.header("X-Admin-Key")

            adminUserFn(apiKey, providedKey, debugMode) {
                val workspaceId = call.parameters["workspaceId"] ?: ""
                val userToDelete = call.parameters["userId"] ?: ""

                if (workspaceId.isEmpty() || userToDelete.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid request")
                }

                val result = WorkspaceService.removeUserFromWorkspace(
                    userId = userToDelete,
                    workspaceId = workspaceId,
                    writeopiaDb = writeopiaDb
                )

                if (result) {
                    call.respond(HttpStatusCode.OK, "User added to workspace")
                } else {
                    call.respond(HttpStatusCode.NotFound, "Not added")
                }
            }
        }

        delete("/api/workspace/{workspaceId}/user/email/{email}") {
            val providedKey = if (debugMode) "debug" else call.request.header("X-Admin-Key")

            adminUserFn(apiKey, providedKey, debugMode) {
                val workspaceId = call.parameters["workspaceId"] ?: ""
                val emailToDelete = call.parameters["email"] ?: ""

                if (workspaceId.isEmpty() || emailToDelete.isEmpty()) {
                    call.respond(HttpStatusCode.BadRequest, "Invalid request")
                }

                val result = WorkspaceService.removeUserFromWorkspaceByEmail(
                    userEmail = emailToDelete,
                    workspaceId = workspaceId,
                    writeopiaDb = writeopiaDb
                )

                if (result) {
                    call.respond(HttpStatusCode.OK, "User added to workspace")
                } else {
                    call.respond(HttpStatusCode.NotFound, "Not added")
                }
            }
        }
    }
}

suspend fun RoutingContext.adminUserFn(
    apiKey: String?,
    providedKey: String?,
    debugMode: Boolean = false,
    func: suspend () -> Unit
) {
    if (providedKey != apiKey && !debugMode) {
        call.respond(HttpStatusCode.Unauthorized, "Invalid admin key")
    } else {
        func()
    }
}

package io.writeopia.api.core.auth.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.writeopia.api.core.auth.repository.changeWorkspaceName
import io.writeopia.api.core.auth.repository.changeWorkspaceRoleForUser
import io.writeopia.api.core.auth.repository.listWorkspaces
import io.writeopia.api.core.auth.service.WorkspaceService
import io.writeopia.api.core.auth.utils.runIfAdmin
import io.writeopia.app.mapping.toApi
import io.writeopia.app.requests.AddUserToWorkspaceRequest
import io.writeopia.sdk.serialization.data.toApi
import io.writeopia.sdk.serialization.request.WorkspaceNameChangeRequest
import io.writeopia.sdk.serialization.request.WorkspaceRoleChangeRequest
import io.writeopia.sql.WriteopiaDbBackend

fun Routing.workspaceRoute(
    apiKey: String?,
    writeopiaDb: WriteopiaDbBackend,
    debugMode: Boolean = false
) {
    get("/api/workspace") {
        val providedKey = if (debugMode) "debug" else call.request.header("X-Admin-Key")
        adminUserFn(apiKey, providedKey, debugMode) {
            val workspaces = writeopiaDb.listWorkspaces().map { it.toApi() }
            call.respond(HttpStatusCode.OK, workspaces)
        }
    }

    get("/api/workspace/user/email/{userEmail}") {
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

    authenticate("auth-jwt", optional = debugMode) {
        get("/api/workspace/user") {
            val userId = getUserId() ?: ""

            val workspaces = WorkspaceService.getWorkspacesByUserId(userId, writeopiaDb)
                .map { it.toApi() }

            if (workspaces.isNotEmpty()) {
                call.respond(HttpStatusCode.OK, workspaces)
            } else {
                call.respond(HttpStatusCode.NotFound, "No workspaces found for user")
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        get("/api/workspace/{workspaceId}/user/{userEmail}") {
            val userId = getUserId() ?: ""
            val workspaceId = call.pathParameters["workspaceId"]
                ?: throw IllegalArgumentException("Workspace id is required")
            val userEmail = call.pathParameters["userEmail"]
                ?: throw IllegalArgumentException("User email is required")

            runIfAdmin(userId, workspaceId, writeopiaDb, debugMode) {
                val user = WorkspaceService.getUserInWorkspace(
                    workspaceId = workspaceId,
                    userEmail = userEmail,
                    writeopiaDb = writeopiaDb
                )

                if (user != null) {
                    call.respond(HttpStatusCode.OK, user.toApi())
                } else {
                    call.respond(HttpStatusCode.NotFound, "User not found")
                }
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        get("/api/user/workspaces/{workspaceId}") {
            val currentUserId = getUserId() ?: ""
            val workspaceId = call.pathParameters["workspaceId"]
                ?: throw IllegalArgumentException("Workspace id is required")

            runIfAdmin(currentUserId, workspaceId, writeopiaDb, debugMode) {
                val workspaces = WorkspaceService
                    .getUsersInWorkspace(workspaceId, writeopiaDb)
                    .map { workspaceUser -> workspaceUser.toApi() }

                if (workspaces.isNotEmpty()) {
                    call.respond(HttpStatusCode.OK, workspaces)
                } else {
                    call.respond(HttpStatusCode.NotFound, "No users found for workspace")
                }
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post<AddUserToWorkspaceRequest>("/api/workspace/user") { request ->
            println("adding user to workspace")
            val userId = getUserId() ?: ""
            val (userEmail, workspaceId, role) = request

            runIfAdmin(userId, workspaceId, writeopiaDb, debugMode) {
                val result = WorkspaceService.addUserToWorkspaceSecure(
                    workspaceOwnerId = userId,
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
    }

    post<AddUserToWorkspaceRequest>("/admin/workspace/user") { request ->
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

    authenticate("auth-jwt", optional = debugMode) {
        put<WorkspaceNameChangeRequest>("/api/workspace/name") { nameChange ->
            val userId = getUserId() ?: ""
            val (workspaceId, newName) = nameChange

            runIfAdmin(userId, workspaceId, writeopiaDb, debugMode) {
                writeopiaDb.changeWorkspaceName(
                    workspaceId = workspaceId,
                    newName = newName
                )

                call.respond(status = HttpStatusCode.OK, "Name changed")
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        put<WorkspaceRoleChangeRequest>("/api/workspace/role") { roleChange ->
            val userId = getUserId() ?: ""
            val (workspaceId, changeRoleUserId, newRole) = roleChange

            runIfAdmin(userId, workspaceId, writeopiaDb, debugMode) {
                writeopiaDb.changeWorkspaceRoleForUser(
                    workspaceId = workspaceId,
                    userId = changeRoleUserId,
                    newRole = newRole
                )

                call.respond(status = HttpStatusCode.OK, "Name changed")
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        delete("/api/workspace/{workspaceId}/user/{userId}") {
            val userId = getUserId() ?: ""

            val workspaceId = call.parameters["workspaceId"] ?: ""
            val userToDelete = call.parameters["userId"] ?: ""

            if (workspaceId.isEmpty() || userToDelete.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "Invalid request")
            }

            val result = WorkspaceService.removeUserFromWorkspaceSecure(
                workspaceOwnerId = userId,
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

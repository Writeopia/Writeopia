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
import io.writeopia.api.core.auth.models.AddUserResult
import io.writeopia.api.core.auth.repository.changeWorkspaceName
import io.writeopia.api.core.auth.repository.changeWorkspaceRoleForUser
import io.writeopia.api.core.auth.repository.countAdminsInWorkspace
import io.writeopia.api.core.auth.repository.getUserRoleInWorkspace
import io.writeopia.api.core.auth.repository.listWorkspaces
import io.writeopia.api.core.auth.repository.searchUsersByEmail
import io.writeopia.api.core.auth.service.WorkspaceService
import io.writeopia.api.documents.documents.TutorialsService
import io.writeopia.api.core.auth.utils.runIfAdmin
import io.writeopia.app.dto.PaginatedUserSearchResponse
import io.writeopia.app.dto.PaginatedWorkspaceUsersResponse
import io.writeopia.app.dto.SearchUserApi
import io.writeopia.app.mapping.toApi
import io.writeopia.app.requests.AddUserToWorkspaceRequest
import io.writeopia.app.requests.CreateWorkspaceRequest
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.workspace.Role
import io.writeopia.backend.models.ServerResponse
import io.writeopia.sdk.serialization.data.toApi
import io.writeopia.sdk.serialization.request.WorkspaceNameChangeRequest
import io.writeopia.sdk.serialization.request.WorkspaceRoleChangeRequest
import io.writeopia.sql.WriteopiaDbBackend
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("WorkspaceRouting")

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
                .map { workspace ->
                    val count = writeopiaDb.documentEntityQueries
                        .countByWorkspaceId(workspace.id)
                        .executeAsOne()
                    workspace.toApi(documentCount = count.toInt())
                }

            if (workspaces.isNotEmpty()) {
                call.respond(HttpStatusCode.OK, workspaces)
            } else {
                call.respond(HttpStatusCode.NotFound,
                    ServerResponse("No workspaces found for user")
                )
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        get("/api/workspace/user") {
            val userId = getUserId() ?: ""

            val workspaces = WorkspaceService.getWorkspacesByUserId(userId, writeopiaDb)
                .map { workspace ->
                    val count = writeopiaDb.documentEntityQueries
                        .countByWorkspaceId(workspace.id)
                        .executeAsOne()
                    workspace.toApi(documentCount = count.toInt())
                }

            call.respond(HttpStatusCode.OK, workspaces)
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post<CreateWorkspaceRequest>("/api/workspace/create") { request ->
            val userId = getUserId() ?: ""
            val (workspaceName) = request

            val workspaceId = GenerateId.generate()
            WorkspaceService.createWorkspace(workspaceId, workspaceName, writeopiaDb)
            WorkspaceService.addUserToWorkspaceByUserId(
                userId = userId,
                workspaceId = workspaceId,
                role = Role.ADMIN.value,
                writeopiaDb = writeopiaDb
            )

            // Initialize tutorial documents for the new workspace
            TutorialsService.initializeTutorialsForUser(
                userId = userId,
                workspaceId = workspaceId,
                writeopiaDb = writeopiaDb
            )

            call.respond(HttpStatusCode.Created, ServerResponse("Workspace created"))
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
                    call.respond(HttpStatusCode.NotFound, ServerResponse("User not found"))
                }
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        get("/api/workspace/{workspaceId}/users") {
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
                    call.respond(HttpStatusCode.NotFound, ServerResponse("No users found for workspace"))
                }
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        get("/api/workspace/{workspaceId}/users/paginated") {
            val currentUserId = getUserId() ?: ""
            val workspaceId = call.pathParameters["workspaceId"]
                ?: throw IllegalArgumentException("Workspace id is required")
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

            runIfAdmin(currentUserId, workspaceId, writeopiaDb, debugMode) {
                val paginatedUsers = WorkspaceService
                    .getUsersInWorkspacePaginated(workspaceId, page, pageSize, writeopiaDb)

                val response = PaginatedWorkspaceUsersResponse(
                    users = paginatedUsers.users.map { it.toApi() },
                    page = paginatedUsers.page,
                    pageSize = paginatedUsers.pageSize,
                    totalCount = paginatedUsers.totalCount,
                    totalPages = paginatedUsers.totalPages,
                    hasNextPage = paginatedUsers.hasNextPage
                )

                call.respond(HttpStatusCode.OK, response)
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        get("/api/workspace/{workspaceId}/users/search") {
            val currentUserId = getUserId() ?: ""
            val workspaceId = call.pathParameters["workspaceId"]
                ?: throw IllegalArgumentException("Workspace id is required")
            val emailQuery = call.request.queryParameters["email"] ?: ""
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

            if (emailQuery.length < 2) {
                call.respond(HttpStatusCode.BadRequest, ServerResponse("Email query must be at least 2 characters"))
                return@get
            }

            runIfAdmin(currentUserId, workspaceId, writeopiaDb, debugMode) {
                val offset = ((page - 1) * pageSize).toLong()
                val limit = (pageSize + 1).toLong() // Request one extra to check if there's a next page

                val results = writeopiaDb.searchUsersByEmail(emailQuery, limit, offset)

                val hasNextPage = results.size > pageSize
                val users = results.take(pageSize).map { user ->
                    SearchUserApi(
                        id = user.id,
                        name = user.name,
                        email = user.email
                    )
                }

                val response = PaginatedUserSearchResponse(
                    users = users,
                    page = page,
                    pageSize = pageSize,
                    hasNextPage = hasNextPage
                )

                call.respond(HttpStatusCode.OK, response)
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

                when (result) {
                    AddUserResult.SUCCESS -> {
                        call.respond(HttpStatusCode.OK, ServerResponse("User added to workspace"))
                    }
                    AddUserResult.USER_NOT_FOUND -> {
                        call.respond(HttpStatusCode.NotFound, ServerResponse("User not found"))
                    }
                    AddUserResult.USER_ALREADY_IN_WORKSPACE -> {
                        call.respond(HttpStatusCode.Conflict, ServerResponse("User is already in this workspace"))
                    }
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
                call.respond(HttpStatusCode.OK, ServerResponse("User added to workspace"))
            } else {
                call.respond(HttpStatusCode.NotFound, ServerResponse("Not added"))
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

                call.respond(status = HttpStatusCode.OK, ServerResponse("Name changed"))
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        put<WorkspaceRoleChangeRequest>("/api/workspace/role") { roleChange ->
            val userId = getUserId() ?: ""
            val (workspaceId, changeRoleUserId, newRole) = roleChange

            runIfAdmin(userId, workspaceId, writeopiaDb, debugMode) {
                // Check if this change would leave the workspace without admins
                val currentRole = writeopiaDb.getUserRoleInWorkspace(workspaceId, changeRoleUserId)
                val isCurrentlyAdmin = currentRole?.equals(Role.ADMIN.value, ignoreCase = true) == true
                val isChangingToNonAdmin = !newRole.equals(Role.ADMIN.value, ignoreCase = true)

                if (isCurrentlyAdmin && isChangingToNonAdmin) {
                    val adminCount = writeopiaDb.countAdminsInWorkspace(workspaceId)
                    if (adminCount <= 1) {
                        call.respond(
                            status = HttpStatusCode.Conflict,
                            ServerResponse("Cannot change role: workspace must have at least one admin")
                        )
                        return@runIfAdmin
                    }
                }

                writeopiaDb.changeWorkspaceRoleForUser(
                    workspaceId = workspaceId,
                    userId = changeRoleUserId,
                    newRole = newRole
                )

                call.respond(status = HttpStatusCode.OK, ServerResponse("Role changed"))
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        delete("/api/workspace/{workspaceId}/user/{userId}") {
            val userId = getUserId() ?: ""

            val workspaceId = call.parameters["workspaceId"] ?: ""
            val userToDelete = call.parameters["userId"] ?: ""

            if (workspaceId.isEmpty() || userToDelete.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ServerResponse("Invalid request"))
            }

            val result = WorkspaceService.removeUserFromWorkspaceSecure(
                workspaceOwnerId = userId,
                userId = userToDelete,
                workspaceId = workspaceId,
                writeopiaDb = writeopiaDb
            )

            if (result) {
                call.respond(HttpStatusCode.OK, ServerResponse("User removed from workspace"))
            } else {
                call.respond(HttpStatusCode.NotFound, ServerResponse("User not removed"))
            }
        }
    }

    delete("/api/workspace/{workspaceId}/user/{userId}") {
        val providedKey = if (debugMode) "debug" else call.request.header("X-Admin-Key")

        adminUserFn(apiKey, providedKey, debugMode) {
            val workspaceId = call.parameters["workspaceId"] ?: ""
            val userToDelete = call.parameters["userId"] ?: ""

            if (workspaceId.isEmpty() || userToDelete.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ServerResponse("Invalid request"))
            }

            val result = WorkspaceService.removeUserFromWorkspace(
                userId = userToDelete,
                workspaceId = workspaceId,
                writeopiaDb = writeopiaDb
            )

            if (result) {
                call.respond(HttpStatusCode.OK, ServerResponse("User removed from workspace"))
            } else {
                call.respond(HttpStatusCode.NotFound, ServerResponse("User not removed"))
            }
        }
    }

    delete("/api/workspace/{workspaceId}/user/email/{email}") {
        val providedKey = if (debugMode) "debug" else call.request.header("X-Admin-Key")

        adminUserFn(apiKey, providedKey, debugMode) {
            val workspaceId = call.parameters["workspaceId"] ?: ""
            val emailToDelete = call.parameters["email"] ?: ""

            if (workspaceId.isEmpty() || emailToDelete.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, ServerResponse("Invalid request"))
            }

            val result = WorkspaceService.removeUserFromWorkspaceByEmail(
                userEmail = emailToDelete,
                workspaceId = workspaceId,
                writeopiaDb = writeopiaDb
            )

            if (result) {
                call.respond(HttpStatusCode.OK, ServerResponse("User removed from workspace"))
            } else {
                call.respond(HttpStatusCode.NotFound, ServerResponse("User not removed"))
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        post("/api/workspace/{workspaceId}/export") {
            logger.info("[Export] ========== EXPORT REQUEST RECEIVED ==========")
            logger.info("[Export] debugMode: $debugMode")

            val authHeader = call.request.header("Authorization")
            logger.info("[Export] Authorization header present: ${authHeader != null}")
            logger.info("[Export] Authorization header length: ${authHeader?.length ?: 0}")

            val userId = getUserId()
            logger.info("[Export] getUserId() returned: ${userId ?: "NULL"}")

            val userIdOrEmpty = userId ?: ""
            val workspaceId = call.pathParameters["workspaceId"]
                ?: throw IllegalArgumentException("Workspace id is required")

            logger.info("[Export] userId: '$userIdOrEmpty', workspaceId: '$workspaceId'")
            logger.info("[Export] Calling runIfAdmin...")

            runIfAdmin(userIdOrEmpty, workspaceId, writeopiaDb, debugMode) {
                logger.info("[Export] Inside runIfAdmin block - user IS admin!")
                logger.info("[Export] Triggering export...")
                val result = WorkspaceService.triggerWorkspaceExport(
                    userId = userIdOrEmpty,
                    workspaceId = workspaceId,
                    writeopiaDb = writeopiaDb
                )

                if (result) {
                    logger.info("[Export] Export triggered successfully")
                    call.respond(HttpStatusCode.Accepted, ServerResponse("Export job started"))
                } else {
                    logger.error("[Export] Failed to trigger export")
                    call.respond(HttpStatusCode.InternalServerError, ServerResponse("Failed to start export"))
                }
            }
            logger.info("[Export] ========== EXPORT REQUEST COMPLETED ==========")
        }
    }
}

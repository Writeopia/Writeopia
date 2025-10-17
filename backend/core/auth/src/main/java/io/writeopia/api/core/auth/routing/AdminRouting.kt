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
import io.writeopia.api.core.auth.service.WorkspaceService
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

package io.writeopia.api.core.auth.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.writeopia.api.core.auth.repository.disableUserByEmail
import io.writeopia.api.core.auth.repository.enableUserByEmail
import io.writeopia.sql.WriteopiaDbBackend

fun Route.adminProtectedRoute(apiKey: String, writeopiaDb: WriteopiaDbBackend) {
    route("/admin") {
        post("/enable-user/{userEmail}") {
            val providedKey = call.request.header("X-Admin-Key")
            adminUserFn(apiKey, providedKey, writeopiaDb::enableUserByEmail)
        }

        post("/disable-user/{userEmail}") {
            val providedKey = call.request.header("X-Admin-Key")
            adminUserFn(apiKey, providedKey, writeopiaDb::disableUserByEmail)
        }
    }
}

suspend fun RoutingContext.adminUserFn(
    apiKey: String,
    providedKey: String?,
    func: suspend (String) -> Unit
) {
    if (providedKey != apiKey) {
        call.respond(HttpStatusCode.Unauthorized, "Invalid admin key")
    } else {
        val userEmail = call.parameters["userEmail"]
            ?: return call.respond(HttpStatusCode.BadRequest)

        func(userEmail)

        call.respond(HttpStatusCode.OK, "User $userEmail enabled")
    }
}

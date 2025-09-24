package io.writeopia.api.core.auth.utils

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import io.writeopia.api.core.auth.repository.isUserInWorkspace
import io.writeopia.sql.WriteopiaDbBackend

suspend fun RoutingContext.runIfMember(
    userId: String,
    workspaceId: String,
    writeopiaDb: WriteopiaDbBackend,
    block: suspend () -> Unit
) {
    val shouldContinue = writeopiaDb.isUserInWorkspace(userId, workspaceId)

    if (shouldContinue) {
        block()
    }

    this.call.respond(HttpStatusCode.Forbidden)
}

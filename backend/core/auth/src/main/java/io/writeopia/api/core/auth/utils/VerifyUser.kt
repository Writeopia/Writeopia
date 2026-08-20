package io.writeopia.api.core.auth.utils

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import io.writeopia.api.core.auth.repository.isUserAdminInWorkspace
import io.writeopia.api.core.auth.repository.isUserInWorkspace
import io.writeopia.sql.WriteopiaDbBackend
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("VerifyUser")

suspend fun RoutingContext.runIfMember(
    userId: String,
    workspaceId: String,
    writeopiaDb: WriteopiaDbBackend,
    debug: Boolean = false,
    block: suspend () -> Unit
) {
    val shouldContinue = writeopiaDb.isUserInWorkspace(userId, workspaceId)
    logger.info("[runIfMember] userId: $userId, workspaceId: $workspaceId, isMember: $shouldContinue, debug: $debug")

    if (shouldContinue || debug) {
        block()
        return
    }

    logger.warn("[runIfMember] Access denied - user is not a member")
    this.call.respond(HttpStatusCode.Forbidden)
}

suspend fun RoutingContext.runIfAdmin(
    userId: String,
    workspaceId: String,
    writeopiaDb: WriteopiaDbBackend,
    debug: Boolean = false,
    block: suspend () -> Unit
) {
    val shouldContinue = writeopiaDb.isUserAdminInWorkspace(userId, workspaceId)
    logger.info("[runIfAdmin] userId: $userId, workspaceId: $workspaceId, isAdmin: $shouldContinue, debug: $debug")

    if (shouldContinue || debug) {
        block()
        return
    }

    logger.warn("[runIfAdmin] Access denied - user is not an admin")
    this.call.respond(HttpStatusCode.Forbidden)
}

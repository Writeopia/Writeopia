package io.writeopia.api.core.auth.utils

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import io.writeopia.api.core.auth.repository.getUserTier
import io.writeopia.api.core.auth.repository.isUserAdminInWorkspace
import io.writeopia.api.core.auth.repository.isUserInWorkspace
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sql.WriteopiaDbBackend

suspend fun RoutingContext.runIfMember(
    userId: String,
    workspaceId: String,
    writeopiaDb: WriteopiaDbBackend,
    debug: Boolean = false,
    block: suspend () -> Unit
) {
    val shouldContinue = writeopiaDb.isUserInWorkspace(userId, workspaceId)

    if (shouldContinue || debug) {
        block()
    }

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

    if (shouldContinue || debug) {
        block()
    }

    this.call.respond(HttpStatusCode.Forbidden)
}

/**
 * Runs the block only if the user is a member of the workspace AND has a premium tier.
 * This is used to gate sync-related endpoints to premium users only.
 */
suspend fun RoutingContext.runIfPremiumMember(
    userId: String,
    workspaceId: String,
    writeopiaDb: WriteopiaDbBackend,
    debug: Boolean = false,
    block: suspend () -> Unit
) {
    val isMember = writeopiaDb.isUserInWorkspace(userId, workspaceId)
    val tier = writeopiaDb.getUserTier(userId)
    val isPremium = tier == Tier.PREMIUM

    if ((isMember && isPremium) || debug) {
        block()
        return
    }

    if (!isMember) {
        this.call.respond(HttpStatusCode.Forbidden, "User is not a member of the workspace")
    } else {
        this.call.respond(HttpStatusCode.PaymentRequired, "Premium subscription required for sync features")
    }
}

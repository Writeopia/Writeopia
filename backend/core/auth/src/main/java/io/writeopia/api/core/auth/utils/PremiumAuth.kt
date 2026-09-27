package io.writeopia.api.core.auth.utils

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sql.WriteopiaDbBackend
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("PremiumAuth")

const val PREMIUM_REQUIRED_MESSAGE = "This feature requires a premium subscription"

/**
 * Whether the user's account is in the PREMIUM tier. Unknown users are treated as not premium.
 */
fun WriteopiaDbBackend.isPremiumUser(userId: String): Boolean =
    userEntityQueries.selectAccountTypeById(userId).executeAsOneOrNull() == Tier.PREMIUM.tierName()

/**
 * Get the userId from the API Gateway header and check that the user is in the PREMIUM tier.
 * Responds with 401 if the user is not authenticated and 403 if the user is not premium.
 * In debug mode the premium check is skipped.
 *
 * @return userId if authenticated and premium, null otherwise (and a response was already sent)
 */
suspend fun ApplicationCall.requirePremiumUserId(
    writeopiaDb: WriteopiaDbBackend,
    debugMode: Boolean = false
): String? {
    val userId = getUserIdFromApiGateway(debugMode)
    if (userId.isNullOrEmpty()) {
        respond(HttpStatusCode.Unauthorized, "Authentication required")
        return null
    }

    if (debugMode || writeopiaDb.isPremiumUser(userId)) return userId

    logger.info("Request denied - user {} is not premium", userId)
    respond(HttpStatusCode.Forbidden, PREMIUM_REQUIRED_MESSAGE)
    return null
}

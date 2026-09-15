package io.writeopia.api.core.auth.utils

import com.auth0.jwt.JWT
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.writeopia.backend.models.ServerResponse
import org.slf4j.LoggerFactory
import kotlin.random.Random

private val logger = LoggerFactory.getLogger("ApiGatewayAuth")

/**
 * Extract userId from X-Forwarded-Authorization header (from API Gateway).
 * API Gateway already validated the JWT, so we just decode it to extract the userId claim.
 *
 * @param debugMode If true, reads userId from cookie for testing without API Gateway
 * @return userId if found, null otherwise
 */
fun ApplicationCall.getUserIdFromApiGateway(debugMode: Boolean = false): String? {
    // In debug mode, extract userId from the access token cookie for testing
    val forwardedAuth = request.headers["X-Forwarded-Authorization"]
    val token = if (forwardedAuth?.startsWith("Bearer ", ignoreCase = true) == true) {
        forwardedAuth.substring(7).trim()
    } else {
        forwardedAuth
    }
    return try {
        val decodedJWT = JWT.decode(token)
        decodedJWT.getClaim("userId").asString()
    } catch (e: Exception) {
        if (debugMode) return Random.nextInt().toString() else null
    }
}

/**
 * Get userId from API Gateway header or respond with 401 Unauthorized.
 * Use this in endpoints that require authentication.
 *
 * @param debugMode If true, reads userId from cookie for testing without API Gateway
 * @return userId if authenticated, null if not (and sends 401 response)
 */
suspend fun ApplicationCall.requireUserId(debugMode: Boolean = false): String? {
    val userId = getUserIdFromApiGateway(debugMode)
    if (userId.isNullOrEmpty()) {
        respond(
            HttpStatusCode.Unauthorized,
            ServerResponse("Authentication required")
        )
        return null
    }
    return userId
}

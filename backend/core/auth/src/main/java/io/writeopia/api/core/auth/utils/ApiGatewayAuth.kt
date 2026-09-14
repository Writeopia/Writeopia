package io.writeopia.api.core.auth.utils

import com.auth0.jwt.JWT
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.writeopia.backend.models.ServerResponse
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ApiGatewayAuth")

/**
 * Extract userId from X-Forwarded-Authorization header (from API Gateway).
 * API Gateway already validated the JWT, so we just decode it to extract the userId claim.
 *
 * @return userId if found, null otherwise
 */
fun ApplicationCall.getUserIdFromApiGateway(): String? {
    val forwardedAuth = request.headers["X-Forwarded-Authorization"] ?: return null

    val token = if (forwardedAuth.startsWith("Bearer ", ignoreCase = true)) {
        forwardedAuth.substring(7).trim()
    } else {
        forwardedAuth
    }

    return try {
        // Decode without verifying (API Gateway already verified it)
        val decodedJWT = JWT.decode(token)
        decodedJWT.getClaim("userId").asString()
    } catch (e: Exception) {
        logger.error("Failed to decode JWT from X-Forwarded-Authorization: ${e.message}")
        null
    }
}

/**
 * Get userId from API Gateway header or respond with 401 Unauthorized.
 * Use this in endpoints that require authentication.
 *
 * @return userId if authenticated, null if not (and sends 401 response)
 */
suspend fun ApplicationCall.requireUserId(): String? {
    val userId = getUserIdFromApiGateway()
    if (userId.isNullOrEmpty()) {
        respond(
            HttpStatusCode.Unauthorized,
            ServerResponse("Authentication required")
        )
        return null
    }
    return userId
}

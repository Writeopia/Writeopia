package io.writeopia.api.core.auth.routing

import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.writeopia.api.core.auth.hash.HashUtils
import io.writeopia.api.core.auth.models.toApi
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.service.RefreshTokenService
import io.writeopia.connection.logger
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sql.WriteopiaDbBackend
import kotlinx.serialization.Serializable

/**
 * Session status response for web clients using HttpOnly cookies.
 */
@Serializable
data class SessionStatusResponse(
    val authenticated: Boolean,
    val userId: String? = null,
    val expiresAt: Long? = null
)

private const val COOKIE_ACCESS_TOKEN = "writeopia_access"
private const val COOKIE_REFRESH_TOKEN = "writeopia_refresh"
private const val COOKIE_SESSION_META = "writeopia_session"

/**
 * Web-specific authentication routes that use HttpOnly secure cookies.
 *
 * These routes are designed for browser-based clients where storing tokens
 * in JavaScript-accessible storage (localStorage) poses XSS risks.
 *
 * Security features:
 * - HttpOnly: Cookies cannot be accessed by JavaScript
 * - Secure: Cookies only sent over HTTPS (in production)
 * - SameSite=Strict: CSRF protection
 * - Path=/api: Cookies only sent to API endpoints
 */
fun Routing.cookieAuthRoute(writeopiaDb: WriteopiaDbBackend, debugMode: Boolean = false) {

    val secureCookies = !debugMode // Use secure cookies in production only

    post("/api/auth/login/web") {
        try {
            val credentials = call.receive<LoginRequest>()
            val user = writeopiaDb.getUserByEmail(credentials.email)

            if (user != null) {
                val hash = user.password
                val salt = user.salt

                val isVerified = HashUtils.verifyPassword(
                    inputPassword = credentials.password,
                    storedHashBase64 = hash,
                    storedSaltBase64 = salt
                )

                if (isVerified) {
                    if (user.enabled || debugMode) {
                        val tokenPair = with(RefreshTokenService) {
                            writeopiaDb.generateAndStoreTokens(user.id)
                        }

                        // Calculate expiry (15 minutes from now)
                        val accessTokenExpiry = System.currentTimeMillis() + (15 * 60 * 1000)

                        // Set HttpOnly cookies
                        setAuthCookies(
                            accessToken = tokenPair.accessToken,
                            refreshToken = tokenPair.refreshToken,
                            userId = user.id,
                            accessTokenExpiry = accessTokenExpiry,
                            secureCookies = secureCookies
                        )

                        // Return user info (no tokens in response body)
                        call.respond(
                            HttpStatusCode.OK,
                            AuthResponse(
                                accessToken = null, // Tokens are in cookies
                                refreshToken = null,
                                writeopiaUser = user.toApi(),
                                enabled = true
                            )
                        )
                    } else {
                        call.respond(
                            HttpStatusCode.OK,
                            AuthResponse(
                                accessToken = null,
                                refreshToken = null,
                                writeopiaUser = user.toApi(),
                                enabled = false
                            )
                        )
                    }
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                }
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
            }
        } catch (e: Exception) {
            logger.error("Web login error: ${e.message}")
            throw e
        }
    }

    post("/api/auth/refresh/web") {
        try {
            // Get refresh token from cookie
            val refreshToken = call.request.cookies[COOKIE_REFRESH_TOKEN]

            if (refreshToken == null) {
                call.respond(HttpStatusCode.Unauthorized, "No refresh token")
                return@post
            }

            val tokenPair = with(RefreshTokenService) {
                writeopiaDb.validateAndRotate(refreshToken)
            }

            if (tokenPair != null) {
                val accessTokenExpiry = System.currentTimeMillis() + (15 * 60 * 1000)

                // Extract userId from the session metadata cookie
                val existingMeta = call.request.cookies[COOKIE_SESSION_META]
                val userId = existingMeta?.split(":")?.firstOrNull() ?: ""

                setAuthCookies(
                    accessToken = tokenPair.accessToken,
                    refreshToken = tokenPair.refreshToken,
                    userId = userId,
                    accessTokenExpiry = accessTokenExpiry,
                    secureCookies = secureCookies
                )

                call.respond(HttpStatusCode.OK, "Token refreshed")
            } else {
                // Clear cookies on invalid refresh token
                clearAuthCookies(secureCookies)
                call.respond(HttpStatusCode.Unauthorized, "Invalid or expired refresh token")
            }
        } catch (e: ContentTransformationException) {
            logger.warn("Web token refresh bad request: ${e.message}")
            call.respond(HttpStatusCode.BadRequest, "Invalid request")
        } catch (e: Exception) {
            logger.error("Web token refresh error: ${e.message}")
            call.respond(HttpStatusCode.InternalServerError, "Token refresh failed")
        }
    }

    get("/api/auth/session/status") {
        val accessToken = call.request.cookies[COOKIE_ACCESS_TOKEN]
        val sessionMeta = call.request.cookies[COOKIE_SESSION_META]

        if (accessToken != null && sessionMeta != null) {
            val parts = sessionMeta.split(":")
            val userId = parts.getOrNull(0)
            val expiresAt = parts.getOrNull(1)?.toLongOrNull()

            call.respond(
                HttpStatusCode.OK,
                SessionStatusResponse(
                    authenticated = true,
                    userId = userId,
                    expiresAt = expiresAt
                )
            )
        } else {
            call.respond(
                HttpStatusCode.OK,
                SessionStatusResponse(authenticated = false)
            )
        }
    }

    post("/api/auth/logout/web") {
        try {
            // Get refresh token to revoke it server-side
            val refreshToken = call.request.cookies[COOKIE_REFRESH_TOKEN]

            if (refreshToken != null) {
                with(RefreshTokenService) {
                    writeopiaDb.revokeToken(refreshToken)
                }
            }

            // Clear all auth cookies
            clearAuthCookies(secureCookies)

            call.respond(HttpStatusCode.OK, "Logged out successfully")
        } catch (e: Exception) {
            logger.error("Web logout error: ${e.message}")
            // Still clear cookies even on error
            clearAuthCookies(secureCookies)
            call.respond(HttpStatusCode.OK, "Logged out")
        }
    }
}

/**
 * Sets authentication cookies with proper security attributes.
 */
private fun RoutingContext.setAuthCookies(
    accessToken: String,
    refreshToken: String,
    userId: String,
    accessTokenExpiry: Long,
    secureCookies: Boolean
) {
    call.response.cookies.append(
        Cookie(
            name = COOKIE_ACCESS_TOKEN,
            value = accessToken,
            maxAge = 15 * 60, // 15 minutes
            path = "/api",
            secure = secureCookies,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Strict")
        )
    )

    call.response.cookies.append(
        Cookie(
            name = COOKIE_REFRESH_TOKEN,
            value = refreshToken,
            maxAge = 7 * 24 * 60 * 60, // 7 days
            path = "/api/auth", // Only sent to auth endpoints
            secure = secureCookies,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Strict")
        )
    )

    // Non-HttpOnly cookie with session metadata for frontend auth status checks
    call.response.cookies.append(
        Cookie(
            name = COOKIE_SESSION_META,
            value = "$userId:$accessTokenExpiry",
            maxAge = 15 * 60,
            path = "/",
            secure = secureCookies,
            httpOnly = false, // Accessible by JavaScript
            extensions = mapOf("SameSite" to "Strict")
        )
    )
}

/**
 * Clears all authentication cookies.
 */
private fun RoutingContext.clearAuthCookies(secureCookies: Boolean) {
    call.response.cookies.append(
        Cookie(
            name = COOKIE_ACCESS_TOKEN,
            value = "",
            maxAge = 0,
            path = "/api",
            secure = secureCookies,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Strict")
        )
    )

    call.response.cookies.append(
        Cookie(
            name = COOKIE_REFRESH_TOKEN,
            value = "",
            maxAge = 0,
            path = "/api/auth",
            secure = secureCookies,
            httpOnly = true,
            extensions = mapOf("SameSite" to "Strict")
        )
    )

    call.response.cookies.append(
        Cookie(
            name = COOKIE_SESSION_META,
            value = "",
            maxAge = 0,
            path = "/",
            secure = secureCookies,
            httpOnly = false,
            extensions = mapOf("SameSite" to "Strict")
        )
    )
}

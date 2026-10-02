package io.writeopia.api.core.auth.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.writeopia.api.core.auth.models.GoogleLoginResult
import io.writeopia.api.core.auth.models.toApi
import io.writeopia.api.core.auth.service.GoogleAuthService
import io.writeopia.api.core.auth.service.GoogleCodeExchanger
import io.writeopia.api.core.auth.service.GoogleTokenVerifier
import io.writeopia.api.core.auth.utils.isUniqueViolation
import io.writeopia.connection.logger
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.GoogleLoginRequest
import io.writeopia.sql.WriteopiaDbBackend

private const val ACCESS_COOKIE_TTL_MS = 15 * 60 * 1000L

/**
 * "Sign in with Google" routes. Both accept a [GoogleLoginRequest] carrying either a Google ID
 * token (Android / iOS SDKs) or an authorization code (desktop loopback and web popup flows).
 *
 * - `POST /api/auth/login/google` responds like `/api/auth/login`: tokens in the body.
 * - `POST /api/auth/login/google/web` responds like `/api/auth/login/web`: tokens in HttpOnly
 *   cookies, none in the body.
 *
 * The provisioning lambdas are the same ones passed to [authRoute]; see its docs for why they
 * are injected.
 */
fun Routing.googleAuthRoute(
    writeopiaDb: WriteopiaDbBackend,
    debugMode: Boolean = false,
    verifier: GoogleTokenVerifier,
    exchanger: GoogleCodeExchanger,
    provisionWorkspaceForNewUser: (
        writeopiaDb: WriteopiaDbBackend,
        workspaceId: String,
        workspaceName: String,
        userId: String
    ) -> Unit,
    onWorkspaceProvisioned: (userId: String, workspaceId: String) -> Unit = { _, _ -> }
) {
    val secureCookies = !debugMode

    suspend fun RoutingContext.handle(useCookies: Boolean) {
        val request = try {
            call.receive<GoogleLoginRequest>()
        } catch (e: ContentTransformationException) {
            logger.warn("Google login bad request: ${e.message}")
            call.respond(HttpStatusCode.BadRequest, "Invalid request body")
            return
        }

        val result = try {
            GoogleAuthService.login(
                writeopiaDb,
                request,
                verifier,
                exchanger,
                provisionWorkspaceForNewUser,
                onWorkspaceProvisioned
            )
        } catch (e: Exception) {
            if (e.isUniqueViolation()) {
                // Two first-logins for the same Google account raced; the other one won.
                logger.info("Google login conflict: ${e.message}")
                call.respond(HttpStatusCode.Conflict, "Account already being created")
                return
            }
            logger.error("Google login error: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, "Google login failed")
            return
        }

        when (result) {
            is GoogleLoginResult.Success -> {
                if (useCookies) {
                    setAuthCookies(
                        accessToken = result.tokenPair.accessToken,
                        refreshToken = result.tokenPair.refreshToken,
                        userId = result.user.id,
                        accessTokenExpiry = System.currentTimeMillis() + ACCESS_COOKIE_TTL_MS,
                        secureCookies = secureCookies
                    )
                }
                call.respond(
                    HttpStatusCode.OK,
                    AuthResponse(
                        accessToken = if (useCookies) null else result.tokenPair.accessToken,
                        refreshToken = if (useCookies) null else result.tokenPair.refreshToken,
                        writeopiaUser = result.user.toApi(),
                        enabled = true
                    )
                )
            }

            // Forbidden (not Unauthorized) so the client can tell this apart from an invalid
            // token and route the user to the account-deletion screen, as the login route does.
            GoogleLoginResult.DeletionPending ->
                call.respond(HttpStatusCode.Forbidden, "Account is being deleted")

            is GoogleLoginResult.InvalidToken ->
                call.respond(HttpStatusCode.Unauthorized, "Invalid Google token")

            is GoogleLoginResult.BadRequest ->
                call.respond(HttpStatusCode.BadRequest, result.reason)
        }
    }

    post("/api/auth/login/google") { handle(useCookies = false) }

    post("/api/auth/login/google/web") { handle(useCookies = true) }
}

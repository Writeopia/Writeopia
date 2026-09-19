package io.writeopia.api.core.auth.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.writeopia.api.core.auth.hash.HashUtils
import io.writeopia.api.core.auth.models.toApi
import io.writeopia.api.core.auth.repository.deleteUserById
import io.writeopia.api.core.auth.repository.getEnabledUserByEmail
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.userExistsByUsernameOrEmail
import io.writeopia.api.core.auth.repository.getUserById
import io.writeopia.api.core.auth.repository.getWorkspaceById
import io.writeopia.api.core.auth.repository.updateConfirmationCode
import io.writeopia.api.core.auth.service.AuthService
import io.writeopia.api.core.auth.service.EmailService
import io.writeopia.api.core.auth.service.RefreshTokenService
import io.writeopia.api.core.auth.service.WorkspaceService
import io.writeopia.api.core.auth.utils.JwtConfig
import io.writeopia.api.core.auth.utils.getUserIdFromApiGateway
import io.writeopia.connection.logger
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.DeleteAccountResponse
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sdk.serialization.data.auth.RefreshTokenRequest
import io.writeopia.sdk.serialization.data.auth.RegisterResponse
import io.writeopia.sdk.serialization.data.auth.ResetPasswordRequest
import io.writeopia.sdk.serialization.data.auth.TokenRefreshResponse
import io.writeopia.sdk.serialization.data.toApi
import io.writeopia.sql.WriteopiaDbBackend
import java.sql.SQLException


fun Routing.authRoute(writeopiaDb: WriteopiaDbBackend, debugMode: Boolean = false) {
    post("/api/auth/login") {
        try {
            val credentials = call.receive<LoginRequest>()
            // Always get user by email first to check if they exist but are unconfirmed
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
                        call.respond(
                            HttpStatusCode.OK,
                            AuthResponse(
                                accessToken = tokenPair.accessToken,
                                refreshToken = tokenPair.refreshToken,
                                writeopiaUser = user.toApi(),
                                enabled = true
                            )
                        )
                    } else {
                        // User exists but email not confirmed
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
            throw e
        }
    }

    post("/api/auth/refresh") {
        try {
            val request = call.receive<RefreshTokenRequest>()
            val tokenPair = with(RefreshTokenService) {
                writeopiaDb.validateAndRotate(request.refreshToken)
            }

            if (tokenPair != null) {
                call.respond(
                    HttpStatusCode.OK,
                    TokenRefreshResponse(
                        accessToken = tokenPair.accessToken,
                        refreshToken = tokenPair.refreshToken
                    )
                )
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Invalid or expired refresh token")
            }
        } catch (e: ContentTransformationException) {
            logger.warn("Token refresh bad request: ${e.message}")
            call.respond(HttpStatusCode.BadRequest, "Invalid request body")
        } catch (e: Exception) {
            logger.error("Token refresh error: ${e.message}")
            call.respond(HttpStatusCode.InternalServerError, "Token refresh failed")
        }
    }

    post("/api/auth/logout") {
        try {
            val request = call.receive<RefreshTokenRequest>()
            val revoked = with(RefreshTokenService) {
                writeopiaDb.revokeToken(request.refreshToken)
            }

            if (revoked) {
                call.respond(HttpStatusCode.OK, "Logged out successfully")
            } else {
                call.respond(HttpStatusCode.BadRequest, "Invalid token")
            }
        } catch (e: ContentTransformationException) {
            logger.warn("Logout bad request: ${e.message}")
            call.respond(HttpStatusCode.BadRequest, "Invalid request body")
        } catch (e: Exception) {
            logger.error("Logout error: ${e.message}")
            call.respond(HttpStatusCode.InternalServerError, "Logout failed")
        }
    }

    post("/api/auth/logout-all") {
        val userId = call.getUserIdFromApiGateway(debugMode) ?: run {
            call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            return@post
        }

        with(RefreshTokenService) {
            writeopiaDb.revokeAllUserTokens(userId)
        }
        call.respond(HttpStatusCode.OK, "All sessions logged out")
    }

    post("/api/auth/register") {
        try {
            logger.info("register request received")
            val request = call.receive<RegisterRequest>()
            // since we are not allowing email probing and we don't need user data in this case
            if (writeopiaDb.userExistsByUsernameOrEmail(username = request.username, email = request.email)) {
                logger.info("register request - user or workspace already exist")
                call.respond(HttpStatusCode.Conflict, "Not Created")
                return@post
            }

            val confirmationCode = EmailService.generateConfirmationCode()
            val codeExpiry = EmailService.getCodeExpiry()
            val workspaceId = GenerateId.generate()

            // Run user creation, confirmation code, workspace, and membership in one atomic transaction
            val wUser = writeopiaDb.transactionWithResult {
                
                val user = AuthService.createUser(writeopiaDb, request, enabled = false)

                writeopiaDb.updateConfirmationCode(request.email, confirmationCode, codeExpiry)
                
                WorkspaceService.createWorkspace(
                    workspaceId = workspaceId,
                    workspaceName = request.workspaceName,
                    writeopiaDb = writeopiaDb
                )

                val created = WorkspaceService.addUserToWorkspaceAdmin(
                    request.email,
                    workspaceId,
                    "ADMIN",
                    writeopiaDb
                )

                if (!created) {
                    error("Failed to associate user with workspace")
                }

                user
            }

            EmailService.sendConfirmationEmail(
                toEmail = request.email,
                code = confirmationCode,
                userName = request.name
            )

            call.respond(
                HttpStatusCode.Created,
                RegisterResponse(
                    writeopiaUser = wUser.toApi(),
                    emailConfirmationRequired = true
                ),
            )
        } catch (e: Exception) {
            /*
            If we want to solve the concurrency issue between `.userExistsByUsernameOrEmail` and `.createUser`,
            which fools the server into throwing "HttpStatusCode.InternalServerError" instead of "HttpStatusCode.Conflict",
            and we are not doing any locking on read.
            This is enough to solve that.
            */
            if (e.isUniqueViolation()) {
                logger.info("register request - user or workspace already exist: ${e.message}")
                call.respond(HttpStatusCode.Conflict, "Not Created")
                return@post
            }
            e.printStackTrace()
            logger.info("register request error message: ${e.message}")
            call.respond(HttpStatusCode.InternalServerError, "Unknown error")
        }
    }

    delete("/api/auth/account") {
        val userId = call.getUserIdFromApiGateway(debugMode) ?: run {
            call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            return@delete
        }

        val rowsAffected = writeopiaDb.deleteUserById(id = userId)
        if (rowsAffected > 0) {
            call.respond(HttpStatusCode.OK, DeleteAccountResponse(true))
        } else {
            call.respond(HttpStatusCode.NotFound, "User not found")
        }
    }

    put("/api/auth/password/reset") {
        val userId = call.getUserIdFromApiGateway(debugMode) ?: run {
            call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            return@put
        }

        val request = call.receive<ResetPasswordRequest>()
        val user = writeopiaDb.getUserById(userId)

        if (user != null) {
            AuthService.resetPassword(writeopiaDb, user, request.newPassword)
            call.respond(HttpStatusCode.OK)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    get("/api/auth/user/current") {
        val userId = call.getUserIdFromApiGateway(debugMode) ?: run {
            call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            return@get
        }

        val user = writeopiaDb.getUserById(userId)

        if (user != null) {
            call.respond(HttpStatusCode.OK, user.toApi())
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    get("/api/auth/hello-auth") {
        val userId = call.getUserIdFromApiGateway(debugMode) ?: run {
            call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            return@get
        }

        val principal = call.principal<JWTPrincipal>()
        val username = principal!!.payload.getClaim("username").asString()
        val expiresAt = principal.expiresAt?.time?.minus(System.currentTimeMillis())
        call.respondText("Hello, $username! Token is expired at $expiresAt ms.")
    }
}

fun RoutingContext.getUserId(): String? {
    val principal = call.principal<JWTPrincipal>()

    if (principal == null) {
        logger.warn("principal is null")
    }

    return principal?.payload?.getClaim("userId")?.asString()
}


private const val SQLSTATE_UNIQUE_VIOLATION = "23505"

private fun Throwable.isUniqueViolation(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is SQLException && current.sqlState == SQLSTATE_UNIQUE_VIOLATION) {
            return true
        }

        current = current.cause
    }
    return false
}

package io.writeopia.api.core.auth.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
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
import io.writeopia.api.core.auth.repository.getUserByUsernameOrEmail
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
            val identifier = credentials.identifier.trim()
            val lookupIdentifier = if (identifier.contains('@')) identifier.lowercase() else identifier
            // Always get user by email or username first to check if they exist but are unconfirmed
            val user = writeopiaDb.getUserByUsernameOrEmail(lookupIdentifier)

            // Equalize verification timing against unknown identifiers
            val hash = user?.password ?: HashUtils.DUMMY_HASH_BASE64
            val salt = user?.salt ?: HashUtils.DUMMY_SALT_BASE64

            val isVerified = HashUtils.verifyPassword(
                inputPassword = credentials.password,
                storedHashBase64 = hash,
                storedSaltBase64 = salt
            )
            
            val invalidCredentials = user == null || !isVerified
            
            if (invalidCredentials) {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                return@post
            } 

            val requiresEmailConfirmation = !user.enabled && !debugMode

            if (requiresEmailConfirmation) {
                call.respond(
                    HttpStatusCode.OK,
                    AuthResponse(
                        accessToken = null,
                        refreshToken = null,
                        writeopiaUser = user.toApi(),
                        enabled = false
                    )
                )
                return@post
            } 

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
            
        } catch (e: ContentTransformationException) {
            // broken/unparseable JSON
            logger.warn("Login bad request")
            call.respond(HttpStatusCode.BadRequest, "Invalid request body")
        } catch (e: Exception) {
            logger.error("Login internal error: ${e.message}", e)
            call.respond(HttpStatusCode.InternalServerError, "Login failed")
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
            val rawRequest = call.receive<RegisterRequest>()
            val request = rawRequest.copy(
                email = rawRequest.email.trim().lowercase()
            )
            request.validate()
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
        } catch (e: IllegalArgumentException) {
            logger.warn("register request validation failed: ${e.message}")
            call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid request")
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

private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

private fun RegisterRequest.validate() {
    require(name.isNotBlank()) { "Name cannot be blank" }

    require(workspaceName.isNotBlank()) { "Workspace name cannot be blank" }
    require(workspaceName.length in 3..30) {
        "Workspace name must be 3-30 characters"
    }

    require(username.length in 3..30) {
        "Username must be 3-30 characters"
    }
    require(username.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
        "Username can only contain letters, numbers, '-' and '_'"
    }

    require(password.length >= 8) { "Password must be at least 8 characters" }

    require(EMAIL_REGEX.matches(email)) { "Invalid email address format" }
}

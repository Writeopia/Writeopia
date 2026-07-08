package io.writeopia.api.core.auth.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
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
import io.writeopia.api.core.auth.repository.getUserById
import io.writeopia.api.core.auth.repository.getWorkspaceById
import io.writeopia.api.core.auth.repository.updateConfirmationCode
import io.writeopia.api.core.auth.service.AuthService
import io.writeopia.api.core.auth.service.EmailService
import io.writeopia.api.core.auth.service.WorkspaceService
import io.writeopia.api.core.auth.utils.JwtConfig
import io.writeopia.connection.logger
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.DeleteAccountResponse
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sdk.serialization.data.auth.ResetPasswordRequest
import io.writeopia.sdk.serialization.data.toApi
import io.writeopia.sql.WriteopiaDbBackend


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
                        val token = JwtConfig.generateToken(user.id)
                        call.respond(
                            HttpStatusCode.OK,
                            AuthResponse(token, user.toApi(), enabled = true)
                        )
                    } else {
                        // User exists but email not confirmed
                        call.respond(
                            HttpStatusCode.OK,
                            AuthResponse(null, user.toApi(), enabled = false)
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

    post("/api/auth/register") {
        try {
            logger.info("register request received")
            val request = call.receive<RegisterRequest>()
            val existingUser = writeopiaDb.getUserByEmail(request.email)

            if (existingUser == null) {
                // Create user with enabled = false (always requires email confirmation)
                val wUser = AuthService.createUser(writeopiaDb, request, enabled = false)

                // Generate confirmation code and send email
                val confirmationCode = EmailService.generateConfirmationCode()
                val codeExpiry = EmailService.getCodeExpiry()
                writeopiaDb.updateConfirmationCode(request.email, confirmationCode, codeExpiry)

                EmailService.sendConfirmationEmail(
                    toEmail = request.email,
                    code = confirmationCode,
                    userName = request.name
                )

                val workspaceId = GenerateId.generate()
                // Every user has its own workspace.
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

                if (created) {
                    call.respond(
                        HttpStatusCode.Created,
                        AuthResponse(null, wUser.toApi(), enabled = false),
                    )
                } else {
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        AuthResponse(null, wUser.toApi(), enabled = false),
                    )
                }
            } else {
                logger.info("register request - user or workspace already exist")
                call.respond(HttpStatusCode.Conflict, "Not Created")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            logger.info("register request error message: ${e.message}")
            call.respond(HttpStatusCode.InternalServerError, "Unknown error")
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        delete("/api/auth/account") {
            val userId = getUserId()

            if (userId != null) {
                writeopiaDb.deleteUserById(id = userId)
                call.respond(DeleteAccountResponse(true))
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        put("/api/auth/password/reset") {
            val request = call.receive<ResetPasswordRequest>()
            val userId = getUserId()
            val user = userId?.let(writeopiaDb::getUserById)

            if (user != null) {
                AuthService.resetPassword(writeopiaDb, user, request.newPassword)
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        get("/api/auth/user/current") {
            val userId = getUserId()

            val user = userId?.let(writeopiaDb::getUserById)

            if (user != null) {
                call.respond(HttpStatusCode.OK, user.toApi())
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        get("/api/auth/hello-auth") {
            val principal = call.principal<JWTPrincipal>()
            val username = principal!!.payload.getClaim("username").asString()
            val expiresAt = principal.expiresAt?.time?.minus(System.currentTimeMillis())
            call.respondText("Hello, $username! Token is expired at $expiresAt ms.")
        }
    }
}

fun RoutingContext.getUserId(): String? {
    val principal = call.principal<JWTPrincipal>()

    if (principal == null) {
        logger.warn("principal is null")
    }

    return principal?.payload?.getClaim("userId")?.asString()
}

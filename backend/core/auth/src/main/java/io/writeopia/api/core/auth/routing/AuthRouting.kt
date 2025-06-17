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
import io.writeopia.api.core.auth.AuthService
import io.writeopia.api.core.auth.JwtConfig
import io.writeopia.api.core.auth.hash.HashUtils
import io.writeopia.api.core.auth.models.toApi
import io.writeopia.api.core.auth.repository.deleteUserById
import io.writeopia.api.core.auth.repository.getEnabledUserByEmail
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.getUserById
import io.writeopia.connection.logger
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.DeleteAccountResponse
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.sdk.serialization.data.auth.ResetPasswordRequest
import io.writeopia.sdk.serialization.data.toApi
import io.writeopia.sql.WriteopiaDbBackend


fun Routing.authRoute(writeopiaDb: WriteopiaDbBackend, debugMode: Boolean = false) {
    post("/api/login") {
        try {
            val credentials = call.receive<LoginRequest>()
            val user = if (debugMode) {
                writeopiaDb.getUserByEmail(credentials.email)
            } else {
                writeopiaDb.getEnabledUserByEmail(credentials.email)
            }

            if (user != null) {
                val hash = user.password
                val salt = user.salt

                val isVerified = HashUtils.verifyPassword(
                    inputPassword = credentials.password,
                    storedHashBase64 = hash,
                    storedSaltBase64 = salt
                )

                if (isVerified) {
                    val token = JwtConfig.generateToken(user.id)
                    call.respond(HttpStatusCode.OK, AuthResponse(token, user.toApi()))
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

    post("/api/register") {
        try {
            logger.info("register request received")
            val request = call.receive<RegisterRequest>()
            val user = writeopiaDb.getUserByEmail(request.email)

            if (user == null) {
                val wUser = AuthService.createUser(writeopiaDb, request, enabled = debugMode)

                call.respond(HttpStatusCode.Created, AuthResponse(null, wUser.toApi()))
            } else {
                logger.info("register request - user already exists")
                call.respond(HttpStatusCode.Conflict, "Not Created")
            }
        } catch (e: Exception) {
            logger.info("register request error message: ${e.message}")
            call.respond(HttpStatusCode.InternalServerError, "Unknown error")
        }
    }

    authenticate("auth-jwt", optional = debugMode) {
        delete("/api/account") {
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
        put("/api/password/reset") {
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
        get("/api/user/current") {
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
        get("/api/hello-auth") {
            val principal = call.principal<JWTPrincipal>()
            val username = principal!!.payload.getClaim("username").asString()
            val expiresAt = principal.expiresAt?.time?.minus(System.currentTimeMillis())
            call.respondText("Hello, $username! Token is expired at $expiresAt ms.")
        }
    }
}

fun RoutingContext.getUserId(): String? {
    val principal = call.principal<JWTPrincipal>()
    return principal?.payload?.getClaim("userId")?.asString()
}

//suspend fun ApplicationCall.withAuth(
//    byPass: Boolean = false,
//    func: suspend () -> Unit
//) {
//    if (byPass) return func()
//
//    val token = request.headers.run {
//        this["X-Forwarded-Authorization"] ?: this["Authorization"]
//    }
//
//    val idToken = token?.replace("Bearer ", "")
//        ?: return unAuthorized("The token was not correctly parsed")
//
//    return try {
//        FirebaseAuth.getInstance().verifyIdToken(idToken)
//        func()
//    } catch (e: FirebaseAuthException) {
//        application.log.info("Unauthorized: ${e.message}")
//        unAuthorized(e.message ?: "Auth failed")
//    }
//}
//
//private suspend fun ApplicationCall.unAuthorized(message: String = "Auth failed") =
//    respond(HttpStatusCode.Unauthorized, message)

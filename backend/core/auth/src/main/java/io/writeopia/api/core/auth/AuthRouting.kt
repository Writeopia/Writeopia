package io.writeopia.api.core.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.writeopia.api.core.auth.repository.deleteUserById
import io.writeopia.sdk.serialization.data.auth.LoginRequest
import io.writeopia.sdk.serialization.data.auth.RegisterRequest
import io.writeopia.api.core.auth.repository.getUser
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.insertUser
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.serialization.data.auth.AuthResponse
import io.writeopia.sdk.serialization.data.auth.DeleteAccountResponse
import io.writeopia.sdk.serialization.data.toApi
import io.writeopia.sql.WriteopiaDbBackend
import java.util.UUID

fun Routing.authRoute(writeopiaDb: WriteopiaDbBackend) {
    post("api/login") {
        try {
            val credentials = call.receive<LoginRequest>()
            val user = writeopiaDb.getUser(credentials.email, credentials.password)

            if (user != null) {
                val token = JwtConfig.generateToken(user.id)
                call.respond(HttpStatusCode.OK, AuthResponse(token, user.toApi()))
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
            }
        } catch (e: Exception) {
            throw e
        }
    }

    post("api/register") {
        try {
            val (name, email, password) = call.receive<RegisterRequest>()

            val user = writeopiaDb.getUserByEmail(email)
            if (user == null) {
                val id = UUID.randomUUID().toString()

                writeopiaDb.insertUser(id, name, email, password)
                val wUser = WriteopiaUser(
                    id = id,
                    name = name,
                    email = email,
                    password = password
                )

                call.respond(HttpStatusCode.Created, AuthResponse(null, wUser.toApi()))
            } else {
                call.respond(HttpStatusCode.Conflict, "Not Created")
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.InternalServerError, "Unknown error")
        }

    }

    authenticate("auth-jwt") {
        delete("api/account") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asString()

            if (userId != null) {
                writeopiaDb.deleteUserById(id = userId)
                call.respond(DeleteAccountResponse(true))
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }
    }

    authenticate("auth-jwt") {
        get("api/hello-auth") {
            val principal = call.principal<JWTPrincipal>()
            val username = principal!!.payload.getClaim("username").asString()
            val expiresAt = principal.expiresAt?.time?.minus(System.currentTimeMillis())
            call.respondText("Hello, $username! Token is expired at $expiresAt ms.")
        }
    }
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

package io.writeopia.api.core.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.writeopia.sdk.serialization.data.LoginRequest
import io.writeopia.sdk.serialization.data.RegisterRequest
import io.writeopia.api.core.auth.repository.getUser
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.insertUser
import io.writeopia.sdk.serialization.data.AuthResponse
import io.writeopia.sql.WriteopiaDbBackend
import java.util.UUID

fun Routing.authRoute(writeopiaDb: WriteopiaDbBackend) {
    post("api/login") {
        val credentials = call.receive<LoginRequest>()
        val user = writeopiaDb.getUser(credentials.email, credentials.password)

        if (user != null) {
            val token = JwtConfig.generateToken(user.id)
            call.respond(HttpStatusCode.OK, AuthResponse(token))
        } else {
            call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
        }
    }

    post("api/register") {
        try {
            println("received register!")
            val (name, email, password) = call.receive<RegisterRequest>()

            val user = writeopiaDb.getUserByEmail(email)
            if (user == null) {
                val id = UUID.randomUUID().toString()
                val token = JwtConfig.generateToken(id)

                writeopiaDb.insertUser(id, name, email, password)

                println("created!")
                call.respond(HttpStatusCode.Created, AuthResponse(token))
            } else {
                println("User already in db!")
                call.respond(HttpStatusCode.Conflict, "Not Created")
            }
        } catch (e: Exception) {
            println("exception: ${e.message}")
            e.printStackTrace()
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

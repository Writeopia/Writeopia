package io.writeopia.api.core.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.writeopia.api.core.auth.model.LoginRequest
import io.writeopia.api.core.auth.repository.getUser
import io.writeopia.sql.WriteopiaDbBackend

fun Routing.authRoute(writeopiaDb: WriteopiaDbBackend) {
    post("api/login") {
        val credentials = call.receive<LoginRequest>()
        val user = writeopiaDb.getUser(credentials.email, credentials.password)

        if (user != null) {
            val token = JwtConfig.generateToken(user.id)
            call.respond(mapOf("token" to token))
        } else {
            call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
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

suspend fun ApplicationCall.withAuth(
    byPass: Boolean = false,
    func: suspend () -> Unit
) {
    if (byPass) return func()

    val token = request.headers.run {
        this["X-Forwarded-Authorization"] ?: this["Authorization"]
    }

    val idToken = token?.replace("Bearer ", "")
        ?: return unAuthorized("The token was not correctly parsed")

    return try {
        FirebaseAuth.getInstance().verifyIdToken(idToken)
        func()
    } catch (e: FirebaseAuthException) {
        application.log.info("Unauthorized: ${e.message}")
        unAuthorized(e.message ?: "Auth failed")
    }
}

private suspend fun ApplicationCall.unAuthorized(message: String = "Auth failed") =
    respond(HttpStatusCode.Unauthorized, message)

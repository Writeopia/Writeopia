package io.writeopia.api.core.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.response.respond

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

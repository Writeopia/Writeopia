package io.writeopia.api.core.auth.utils

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond

fun Application.installAuth() {
    authentication {
        jwt("auth-jwt") {
            realm = "writeopia"

            verifier(JwtConfig.accessVerifier)

            validate { credential ->
                val userId = credential.payload.getClaim("userId").asString()
                val tokenType = credential.payload.getClaim("type").asString()

                if (!userId.isNullOrEmpty() && tokenType == "access") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            challenge { defaultScheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, "Token is not valid or has expired")
            }
        }
    }
}

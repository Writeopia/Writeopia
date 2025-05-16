package io.writeopia.api.core.auth

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt

fun Application.installAuth() {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "writeopia"

            verifier(JwtConfig.verifier)

            validate { credential ->
                if (credential.payload.getClaim("username").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

//            challenge { defaultScheme, realm ->
//                call.respond(HttpStatusCode.Unauthorized, message ="Token is not valid or has expired")
//            }
        }
    }
}

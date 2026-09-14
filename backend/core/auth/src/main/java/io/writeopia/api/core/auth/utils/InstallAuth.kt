package io.writeopia.api.core.auth.utils

import io.ktor.http.HttpStatusCode
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.auth.authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.response.respond

private const val COOKIE_ACCESS_TOKEN = "writeopia_access"

fun Application.installAuth() {
    authentication {
        jwt("auth-jwt") {
            realm = "writeopia"

            verifier(JwtConfig.accessVerifier)

            // Support both Bearer token in Authorization header AND HttpOnly cookie
            authHeader { call ->
                // First check X-Forwarded-Authorization (from API Gateway with user's original token)
                val forwardedAuth = call.request.headers["X-Forwarded-Authorization"]
                if (!forwardedAuth.isNullOrEmpty()) {
                    // Parse "Bearer <token>" format
                    if (forwardedAuth.startsWith("Bearer ", ignoreCase = true)) {
                        val token = forwardedAuth.substring(7).trim()
                        return@authHeader HttpAuthHeader.Single("Bearer", token)
                    }
                }

                // Then try the standard Authorization header (for old endpoint/backward compatibility)
                val authHeader = call.request.parseAuthorizationHeader()
                if (authHeader != null) {
                    return@authHeader authHeader
                }

                // Fall back to HttpOnly cookie for web clients
                val cookieToken = call.request.cookies[COOKIE_ACCESS_TOKEN]
                if (!cookieToken.isNullOrEmpty()) {
                    return@authHeader HttpAuthHeader.Single("Bearer", cookieToken)
                }

                null
            }

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

package io.writeopia.api.core.auth.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.time.Instant

object JwtConfig {
    private val secret = System.getenv("JWT_SECRET")
    private const val ISSUER = "writeopia"
    private const val AUDIENCE = "writeopia-app"
    private const val VALIDITY_IN_MS = 36_000_00 * 24L

    private val algorithm = Algorithm.HMAC256(secret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .build()

    fun generateToken(userId: String): String =
        JWT.create()
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("userId", userId)
//            .withExpiresAt(Instant.now().plusMillis(VALIDITY_IN_MS))
            .sign(algorithm)
}

package io.writeopia.api.core.auth.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.time.Instant

object JwtConfig {
    private val accessSecret = System.getenv("JWT_SECRET")
    private val refreshSecret = System.getenv("JWT_REFRESH_SECRET") ?: accessSecret

    private const val ISSUER = "writeopia"
    private const val AUDIENCE = "writeopia-app"

    private const val ACCESS_TOKEN_VALIDITY_MS = 15 * 60 * 1000L // 15 minutes
    private const val REFRESH_TOKEN_VALIDITY_MS = 30L * 24 * 60 * 60 * 1000L // 30 days

    private const val TOKEN_TYPE_CLAIM = "type"
    private const val TOKEN_TYPE_ACCESS = "access"
    private const val TOKEN_TYPE_REFRESH = "refresh"
    private const val TOKEN_ID_CLAIM = "jti"

    private val accessAlgorithm = Algorithm.HMAC256(accessSecret)
    private val refreshAlgorithm = Algorithm.HMAC256(refreshSecret)

    val accessVerifier: JWTVerifier = JWT.require(accessAlgorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withClaim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS)
        .build()

    val refreshVerifier: JWTVerifier = JWT.require(refreshAlgorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withClaim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_REFRESH)
        .build()

    fun generateAccessToken(userId: String): String =
        JWT.create()
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("userId", userId)
            .withClaim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS)
            .withExpiresAt(Instant.now().plusMillis(ACCESS_TOKEN_VALIDITY_MS))
            .sign(accessAlgorithm)

    fun generateRefreshToken(userId: String, tokenId: String): String =
        JWT.create()
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("userId", userId)
            .withClaim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_REFRESH)
            .withClaim(TOKEN_ID_CLAIM, tokenId)
            .withExpiresAt(Instant.now().plusMillis(REFRESH_TOKEN_VALIDITY_MS))
            .sign(refreshAlgorithm)

    fun getRefreshTokenExpiry(): Instant = Instant.now().plusMillis(REFRESH_TOKEN_VALIDITY_MS)

    fun extractTokenId(refreshToken: String): String? = try {
        val jwt = refreshVerifier.verify(refreshToken)
        jwt.getClaim(TOKEN_ID_CLAIM).asString()
    } catch (e: Exception) {
        null
    }

    fun extractUserId(token: String, isRefreshToken: Boolean = false): String? = try {
        val verifier = if (isRefreshToken) refreshVerifier else accessVerifier
        val jwt = verifier.verify(token)
        jwt.getClaim("userId").asString()
    } catch (e: Exception) {
        null
    }
}

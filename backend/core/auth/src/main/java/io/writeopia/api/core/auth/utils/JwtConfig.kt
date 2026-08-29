package io.writeopia.api.core.auth.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

object JwtConfig {
    // RSA keys for asymmetric signing
    // Private key: only needed by the service that CREATES tokens
    // Public key: shared with all services that VERIFY tokens
    private val privateKey: RSAPrivateKey? = System.getenv("JWT_PRIVATE_KEY")?.let { loadPrivateKey(it) }
    private val publicKey: RSAPublicKey = loadPublicKey(
        System.getenv("JWT_PUBLIC_KEY")
            ?: throw IllegalStateException("JWT_PUBLIC_KEY environment variable is required")
    )

    private const val ISSUER = "writeopia"
    private const val AUDIENCE = "writeopia-app"

    private const val ACCESS_TOKEN_VALIDITY_MS = 15 * 60 * 1000L // 15 minutes
    private const val REFRESH_TOKEN_VALIDITY_MS = 30L * 24 * 60 * 60 * 1000L // 30 days

    private const val TOKEN_TYPE_CLAIM = "type"
    private const val TOKEN_TYPE_ACCESS = "access"
    private const val TOKEN_TYPE_REFRESH = "refresh"
    private const val TOKEN_ID_CLAIM = "jti"

    // RSA256: public key for verification, private key for signing
    // Services that only verify tokens can pass null for private key
    private val accessAlgorithm = Algorithm.RSA256(publicKey, privateKey)
    private val refreshAlgorithm = Algorithm.RSA256(publicKey, privateKey)

    private fun loadPrivateKey(pem: String): RSAPrivateKey {
        val keyContent = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.getDecoder().decode(keyContent)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(keySpec) as RSAPrivateKey
    }

    private fun loadPublicKey(pem: String): RSAPublicKey {
        val keyContent = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val keyBytes = Base64.getDecoder().decode(keyContent)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec) as RSAPublicKey
    }

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

    fun generateAccessToken(userId: String): String {
        requireNotNull(privateKey) {
            "JWT_PRIVATE_KEY is required to sign tokens. This service can only verify tokens."
        }
        return JWT.create()
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("userId", userId)
            .withClaim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS)
            .withExpiresAt(Instant.now().plusMillis(ACCESS_TOKEN_VALIDITY_MS))
            .sign(accessAlgorithm)
    }

    fun generateRefreshToken(userId: String, tokenId: String): String {
        requireNotNull(privateKey) {
            "JWT_PRIVATE_KEY is required to sign tokens. This service can only verify tokens."
        }
        return JWT.create()
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("userId", userId)
            .withClaim(TOKEN_TYPE_CLAIM, TOKEN_TYPE_REFRESH)
            .withClaim(TOKEN_ID_CLAIM, tokenId)
            .withExpiresAt(Instant.now().plusMillis(REFRESH_TOKEN_VALIDITY_MS))
            .sign(refreshAlgorithm)
    }

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

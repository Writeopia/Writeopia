@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.core.auth.service

import io.writeopia.api.core.auth.hash.HashUtils
import io.writeopia.api.core.auth.hash.toBase64
import io.writeopia.api.core.auth.utils.JwtConfig
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class TokenPair(
    val accessToken: String,
    val refreshToken: String
)

object RefreshTokenService {

    fun WriteopiaDbBackend.generateAndStoreTokens(userId: String): TokenPair {
        val tokenId = UUID.randomUUID().toString()
        val accessToken = JwtConfig.generateAccessToken(userId)
        val refreshToken = JwtConfig.generateRefreshToken(userId, tokenId)

        val tokenHash = hashToken(refreshToken)
        val expiresAt = JwtConfig.getRefreshTokenExpiry().toEpochMilli()
        val createdAt = Clock.System.now().toEpochMilliseconds()

        refreshTokenEntityQueries.insertRefreshToken(
            id = tokenId,
            user_id = userId,
            token_hash = tokenHash,
            expires_at = expiresAt,
            created_at = createdAt,
            revoked = false
        )

        return TokenPair(accessToken, refreshToken)
    }

    fun WriteopiaDbBackend.validateAndRotate(refreshToken: String): TokenPair? {
        logger.debug("validateAndRotate called")

        val tokenId = JwtConfig.extractTokenId(refreshToken)
        if (tokenId == null) {
            logger.warn("Token refresh FAILED: Could not extract tokenId from JWT")
            return null
        }
        logger.debug("Token refresh - tokenId: $tokenId")

        val userId = JwtConfig.extractUserId(refreshToken, isRefreshToken = true)
        if (userId == null) {
            logger.warn("Token refresh FAILED: Could not extract userId from JWT")
            return null
        }
        logger.debug("Token refresh - userId: $userId")

        val currentTime = Clock.System.now().toEpochMilliseconds()

        // Validate outside transaction
        val storedToken = refreshTokenEntityQueries
            .selectRefreshTokenByIdIncludingRevoked(tokenId)
            .executeAsOneOrNull()

        if (storedToken == null) {
            logger.warn("Token refresh FAILED: Token not found in database (tokenId: $tokenId)")
            return null
        }
        logger.debug("Token refresh - found in DB: expires_at=${storedToken.expires_at}, revoked=${storedToken.revoked}")

        if (storedToken.user_id != userId) {
            logger.warn("Token refresh FAILED: User ID mismatch (JWT: $userId, DB: ${storedToken.user_id})")
            return null
        }

        val computedHash = hashToken(refreshToken)
        if (computedHash != storedToken.token_hash) {
            logger.warn("Token refresh FAILED: Token hash mismatch")
            return null
        }

        if (storedToken.expires_at <= currentTime) {
            val expiredAgo = currentTime - storedToken.expires_at
            logger.warn("Token refresh FAILED: Token expired ${expiredAgo}ms ago (expires_at: ${storedToken.expires_at}, now: $currentTime)")
            return null
        }
        logger.debug("Token refresh - expires in ${storedToken.expires_at - currentTime}ms")

        // If already revoked, this is a replay attack
        if (storedToken.revoked) {
            logger.warn("Token refresh FAILED: Token was revoked - revoking all user tokens (possible replay attack)")
            refreshTokenEntityQueries.revokeAllUserRefreshTokens(userId)
            return null
        }

        logger.debug("Token refresh - all checks passed, rotating token...")

        // Minimal transaction: re-check state, revoke, and insert
        val db = this
        return transactionWithResult {
            // Re-check token is still active (handles race condition)
            val currentToken = refreshTokenEntityQueries
                .selectRefreshTokenById(tokenId, currentTime)
                .executeAsOneOrNull()

            if (currentToken == null) {
                logger.warn("Token refresh FAILED: Token no longer valid in transaction (race condition or just expired)")
                return@transactionWithResult null
            }

            logger.info("Token refresh SUCCESS: Rotating token for user $userId")
            refreshTokenEntityQueries.revokeRefreshToken(tokenId)
            db.generateAndStoreTokens(userId)
        }
    }

    fun WriteopiaDbBackend.revokeAllUserTokens(userId: String) {
        refreshTokenEntityQueries.revokeAllUserRefreshTokens(userId)
    }

    fun WriteopiaDbBackend.revokeToken(refreshToken: String): Boolean {
        val tokenId = JwtConfig.extractTokenId(refreshToken) ?: return false
        refreshTokenEntityQueries.revokeRefreshToken(tokenId)
        return true
    }

    fun WriteopiaDbBackend.cleanupExpiredTokens() {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        refreshTokenEntityQueries.deleteExpiredTokens(currentTime)
    }

    private fun hashToken(token: String): String {
        val salt = "writeopia-refresh-token-salt".toByteArray()
        return HashUtils.hashPassword(token, salt).toBase64()
    }
}

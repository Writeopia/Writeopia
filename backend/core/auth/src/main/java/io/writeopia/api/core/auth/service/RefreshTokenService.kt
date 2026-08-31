@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.core.auth.service

import io.writeopia.api.core.auth.hash.HashUtils
import io.writeopia.api.core.auth.hash.toBase64
import io.writeopia.api.core.auth.utils.JwtConfig
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
        val tokenId = JwtConfig.extractTokenId(refreshToken) ?: return null
        val userId = JwtConfig.extractUserId(refreshToken, isRefreshToken = true) ?: return null
        val currentTime = Clock.System.now().toEpochMilliseconds()

        // Validate outside transaction
        val storedToken = refreshTokenEntityQueries
            .selectRefreshTokenByIdIncludingRevoked(tokenId)
            .executeAsOneOrNull() ?: return null

        if (storedToken.user_id != userId) return null
        if (hashToken(refreshToken) != storedToken.token_hash) return null
        if (storedToken.expires_at <= currentTime) return null

        // If already revoked, this is a replay attack
        if (storedToken.revoked) {
            refreshTokenEntityQueries.revokeAllUserRefreshTokens(userId)
            return null
        }

        // Minimal transaction: re-check state, revoke, and insert
        val db = this
        return transactionWithResult {
            // Re-check token is still active (handles race condition)
            val currentToken = refreshTokenEntityQueries
                .selectRefreshTokenById(tokenId, currentTime)
                .executeAsOneOrNull() ?: return@transactionWithResult null

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

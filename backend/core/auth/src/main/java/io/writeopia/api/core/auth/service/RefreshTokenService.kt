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

data class RefreshTokenData(
    val id: String,
    val userId: String,
    val tokenHash: String,
    val expiresAt: Long,
    val createdAt: Long,
    val revoked: Boolean
)

object RefreshTokenService {

    fun generateAndStoreTokens(
        writeopiaDb: WriteopiaDbBackend,
        userId: String
    ): TokenPair {
        val tokenId = UUID.randomUUID().toString()
        val accessToken = JwtConfig.generateAccessToken(userId)
        val refreshToken = JwtConfig.generateRefreshToken(userId, tokenId)

        val tokenHash = hashToken(refreshToken)
        val expiresAt = JwtConfig.getRefreshTokenExpiry().toEpochMilli()
        val createdAt = Clock.System.now().toEpochMilliseconds()

        writeopiaDb.refreshTokenEntityQueries.insertRefreshToken(
            id = tokenId,
            user_id = userId,
            token_hash = tokenHash,
            expires_at = expiresAt,
            created_at = createdAt,
            revoked = false
        )

        return TokenPair(accessToken, refreshToken)
    }

    fun validateAndRotate(
        writeopiaDb: WriteopiaDbBackend,
        refreshToken: String
    ): TokenPair? {
        val tokenId = JwtConfig.extractTokenId(refreshToken) ?: return null
        val userId = JwtConfig.extractUserId(refreshToken, isRefreshToken = true) ?: return null

        val currentTime = Clock.System.now().toEpochMilliseconds()
        val storedToken = writeopiaDb.refreshTokenEntityQueries
            .selectRefreshTokenById(tokenId, currentTime)
            .executeAsOneOrNull() ?: return null

        if (storedToken.user_id != userId) {
            return null
        }

        val providedHash = hashToken(refreshToken)
        if (providedHash != storedToken.token_hash) {
            return null
        }

        writeopiaDb.refreshTokenEntityQueries.revokeRefreshToken(tokenId)

        return generateAndStoreTokens(writeopiaDb, userId)
    }

    fun revokeAllUserTokens(writeopiaDb: WriteopiaDbBackend, userId: String) {
        writeopiaDb.refreshTokenEntityQueries.revokeAllUserRefreshTokens(userId)
    }

    fun revokeToken(writeopiaDb: WriteopiaDbBackend, refreshToken: String): Boolean {
        val tokenId = JwtConfig.extractTokenId(refreshToken) ?: return false
        writeopiaDb.refreshTokenEntityQueries.revokeRefreshToken(tokenId)
        return true
    }

    fun cleanupExpiredTokens(writeopiaDb: WriteopiaDbBackend) {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        writeopiaDb.refreshTokenEntityQueries.deleteExpiredTokens(currentTime)
    }

    private fun hashToken(token: String): String {
        val salt = "writeopia-refresh-token-salt".toByteArray()
        return HashUtils.hashPassword(token, salt).toBase64()
    }
}

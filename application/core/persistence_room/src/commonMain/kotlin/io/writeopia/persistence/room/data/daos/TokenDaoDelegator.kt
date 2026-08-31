package io.writeopia.persistence.room.data.daos

import io.writeopia.common.utils.persistence.daos.TokenCommonDao
import io.writeopia.common.utils.persistence.daos.TokenDetails
import io.writeopia.persistence.room.data.entities.TokenEntity

class TokenDaoDelegator(private val tokenDao: TokenDao): TokenCommonDao {
    override suspend fun getTokenByUserId(userId: String): String? =
        tokenDao.getTokenByUserId(userId)?.accessToken

    override suspend fun saveTokens(
        userId: String,
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long?
    ) {
        tokenDao.insertToken(
            TokenEntity(
                userId = userId,
                accessToken = accessToken,
                refreshToken = refreshToken,
                accessTokenExpiresAt = expiresAt
            )
        )
    }

    override suspend fun getTokenDetails(userId: String): TokenDetails? =
        tokenDao.getTokenByUserId(userId)?.let { entity ->
            TokenDetails(
                accessToken = entity.accessToken,
                refreshToken = entity.refreshToken,
                accessTokenExpiresAt = entity.accessTokenExpiresAt
            )
        }

    override suspend fun deleteToken(userId: String) {
        tokenDao.deleteToken(userId)
    }
}

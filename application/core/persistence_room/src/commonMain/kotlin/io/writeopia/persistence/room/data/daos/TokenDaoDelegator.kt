package io.writeopia.persistence.room.data.daos

import io.writeopia.common.utils.persistence.daos.TokenCommonDao
import io.writeopia.persistence.room.data.entities.TokenEntity

class TokenDaoDelegator(private val tokenDao: TokenDao): TokenCommonDao {
    override suspend fun getTokenByUserId(userId: String): String? =
        tokenDao.getTokenByUserId(userId)?.token

    override suspend fun saveToken(token: String, userId: String) {
        tokenDao.insertToken(TokenEntity(userId, token))
    }
}

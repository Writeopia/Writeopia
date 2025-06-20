package io.writeopia.persistence.room.data.daos

import io.writeopia.common.utils.persistence.daos.TokenCommonDao

class TokenDaoDelegator(private val tokenDao: TokenDao): TokenCommonDao {
    override suspend fun getTokenByUserId(userId: String): String? =
        tokenDao.getTokenByUserId(userId)?.token

    override suspend fun saveToken(token: String, userId: String) {
        tokenDao.insertToken(userId, token)
    }
}

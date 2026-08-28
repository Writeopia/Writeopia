package io.writeopia.common.utils.persistence.daos

data class TokenDetails(
    val accessToken: String,
    val refreshToken: String?,
    val accessTokenExpiresAt: Long?
)

interface TokenCommonDao {

    suspend fun getTokenByUserId(userId: String): String?

    suspend fun saveTokens(
        userId: String,
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long?
    )

    suspend fun getTokenDetails(userId: String): TokenDetails?

    suspend fun deleteToken(userId: String)
}

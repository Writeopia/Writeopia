package io.writeopia.auth.core.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.writeopia.auth.core.manager.TokenData

/**
 * Secure token storage for Android using EncryptedSharedPreferences.
 *
 * Uses Android Keystore-backed encryption with AES256-GCM scheme.
 * Tokens are encrypted at rest and can only be decrypted by this app.
 */
class SecureTokenStorage(context: Context) {

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /**
     * Saves tokens securely with encryption.
     */
    fun saveTokens(
        userId: String,
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long?
    ) {
        encryptedPrefs.edit().apply {
            putString(keyAccessToken(userId), accessToken)
            if (refreshToken != null) {
                putString(keyRefreshToken(userId), refreshToken)
            } else {
                remove(keyRefreshToken(userId))
            }
            if (expiresAt != null) {
                putLong(keyExpiresAt(userId), expiresAt)
            } else {
                remove(keyExpiresAt(userId))
            }
            apply()
        }
    }

    /**
     * Retrieves the access token for a given user.
     */
    fun getAccessToken(userId: String): String? =
        encryptedPrefs.getString(keyAccessToken(userId), null)

    /**
     * Retrieves the refresh token for a given user.
     */
    fun getRefreshToken(userId: String): String? =
        encryptedPrefs.getString(keyRefreshToken(userId), null)

    /**
     * Retrieves complete token data for a given user.
     */
    fun getTokenData(userId: String): TokenData? {
        val accessToken = getAccessToken(userId) ?: return null
        return TokenData(
            accessToken = accessToken,
            refreshToken = getRefreshToken(userId),
            accessTokenExpiresAt = getExpiresAt(userId)
        )
    }

    /**
     * Retrieves the token expiration timestamp for a given user.
     */
    fun getExpiresAt(userId: String): Long? {
        val expiresAt = encryptedPrefs.getLong(keyExpiresAt(userId), -1L)
        return if (expiresAt == -1L) null else expiresAt
    }

    /**
     * Clears all tokens for a given user.
     */
    fun clearTokens(userId: String) {
        encryptedPrefs.edit().apply {
            remove(keyAccessToken(userId))
            remove(keyRefreshToken(userId))
            remove(keyExpiresAt(userId))
            apply()
        }
    }

    private fun keyAccessToken(userId: String) = "access_token_$userId"

    private fun keyRefreshToken(userId: String) = "refresh_token_$userId"

    private fun keyExpiresAt(userId: String) = "expires_at_$userId"

    companion object {
        private const val PREFS_FILE_NAME = "writeopia_secure_tokens"
    }
}

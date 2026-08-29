package io.writeopia.auth.core.repository

import io.writeopia.auth.core.manager.TokenData
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Secure token storage for Desktop/JVM using AES-256-GCM encryption.
 *
 * Tokens are encrypted before storage using a key derived from:
 * - Machine-specific identifiers (MAC address, hostname)
 * - A randomly generated salt (stored with the encrypted data)
 *
 * Storage format: salt:iv:ciphertext (all base64 encoded)
 *
 * Security features:
 * - AES-256-GCM provides authenticated encryption
 * - PBKDF2 key derivation with 100,000 iterations
 * - Unique IV for each encryption operation
 * - Tokens are not recoverable on a different machine
 */
object EncryptedTokenStorage {

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KEY_ALGORITHM = "AES"
    private const val KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val KEY_LENGTH = 256
    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128
    private const val PBKDF2_ITERATIONS = 100_000
    private const val SALT_LENGTH = 16

    private const val PREF_ACCESS_TOKEN_PREFIX = "encrypted_access_token_"
    private const val PREF_REFRESH_TOKEN_PREFIX = "encrypted_refresh_token_"
    private const val PREF_EXPIRES_AT_PREFIX = "encrypted_expires_at_"
    private const val PREF_MIGRATION_COMPLETED = "migration_completed"

    private val prefs: Preferences by lazy {
        Preferences.userNodeForPackage(EncryptedTokenStorage::class.java)
    }

    private val secureRandom = SecureRandom()

    /**
     * Saves tokens with encryption.
     */
    fun saveTokens(
        userId: String,
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long?
    ) {
        val machineKey = getMachineSpecificKey()

        prefs.put(PREF_ACCESS_TOKEN_PREFIX + userId, encrypt(accessToken, machineKey))

        if (refreshToken != null) {
            prefs.put(PREF_REFRESH_TOKEN_PREFIX + userId, encrypt(refreshToken, machineKey))
        } else {
            prefs.remove(PREF_REFRESH_TOKEN_PREFIX + userId)
        }

        if (expiresAt != null) {
            prefs.put(PREF_EXPIRES_AT_PREFIX + userId, encrypt(expiresAt.toString(), machineKey))
        } else {
            prefs.remove(PREF_EXPIRES_AT_PREFIX + userId)
        }

        prefs.flush()
    }

    /**
     * Retrieves the access token for a given user.
     */
    fun getAccessToken(userId: String): String? {
        val encrypted = prefs.get(PREF_ACCESS_TOKEN_PREFIX + userId, null) ?: return null
        return try {
            decrypt(encrypted, getMachineSpecificKey())
        } catch (e: Exception) {
            // Decryption failed (possibly different machine)
            null
        }
    }

    /**
     * Retrieves the refresh token for a given user.
     */
    fun getRefreshToken(userId: String): String? {
        val encrypted = prefs.get(PREF_REFRESH_TOKEN_PREFIX + userId, null) ?: return null
        return try {
            decrypt(encrypted, getMachineSpecificKey())
        } catch (e: Exception) {
            null
        }
    }

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
        val encrypted = prefs.get(PREF_EXPIRES_AT_PREFIX + userId, null) ?: return null
        return try {
            decrypt(encrypted, getMachineSpecificKey())?.toLongOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Clears all tokens for a given user.
     */
    fun clearTokens(userId: String) {
        prefs.remove(PREF_ACCESS_TOKEN_PREFIX + userId)
        prefs.remove(PREF_REFRESH_TOKEN_PREFIX + userId)
        prefs.remove(PREF_EXPIRES_AT_PREFIX + userId)
        prefs.flush()
    }

    /**
     * Checks if tokens exist for a given user (for migration purposes).
     */
    fun hasTokens(userId: String): Boolean {
        return prefs.get(PREF_ACCESS_TOKEN_PREFIX + userId, null) != null
    }

    /**
     * Sets a flag indicating migration has been completed.
     */
    fun setMigrationCompleted() {
        prefs.putBoolean(PREF_MIGRATION_COMPLETED, true)
        prefs.flush()
    }

    /**
     * Checks if migration from legacy storage has been completed.
     */
    fun isMigrationCompleted(): Boolean {
        return prefs.getBoolean(PREF_MIGRATION_COMPLETED, false)
    }

    /**
     * Encrypts plaintext using AES-256-GCM.
     * Returns format: salt:iv:ciphertext (all base64 encoded)
     */
    private fun encrypt(plaintext: String, machineKey: String): String {
        // Generate random salt for key derivation
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)

        // Derive key from machine key + salt
        val secretKey = deriveKey(machineKey, salt)

        // Generate random IV
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        // Encrypt
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Combine: salt:iv:ciphertext
        val encoder = Base64.getEncoder()
        return "${encoder.encodeToString(salt)}:${encoder.encodeToString(iv)}:${encoder.encodeToString(ciphertext)}"
    }

    /**
     * Decrypts ciphertext using AES-256-GCM.
     * Expects format: salt:iv:ciphertext (all base64 encoded)
     */
    private fun decrypt(encrypted: String, machineKey: String): String? {
        val parts = encrypted.split(":")
        if (parts.size != 3) return null

        val decoder = Base64.getDecoder()
        val salt = decoder.decode(parts[0])
        val iv = decoder.decode(parts[1])
        val ciphertext = decoder.decode(parts[2])

        // Derive key from machine key + salt
        val secretKey = deriveKey(machineKey, salt)

        // Decrypt
        val cipher = Cipher.getInstance(ALGORITHM)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
        val plaintext = cipher.doFinal(ciphertext)

        return String(plaintext, Charsets.UTF_8)
    }

    /**
     * Derives an AES-256 key from the machine key and salt using PBKDF2.
     */
    private fun deriveKey(machineKey: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM)
        val spec = PBEKeySpec(machineKey.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, KEY_ALGORITHM)
    }

    /**
     * Generates a machine-specific key based on hardware identifiers.
     *
     * Uses:
     * - MAC address of the first network interface
     * - Hostname
     * - User home directory path
     *
     * This ensures tokens encrypted on one machine cannot be decrypted on another.
     */
    private fun getMachineSpecificKey(): String {
        val components = mutableListOf<String>()

        // MAC address
        try {
            val localHost = InetAddress.getLocalHost()
            val networkInterface = NetworkInterface.getByInetAddress(localHost)
            networkInterface?.hardwareAddress?.let { mac ->
                components.add(mac.joinToString("-") { "%02X".format(it) })
            }
        } catch (e: Exception) {
            // MAC address not available
        }

        // Hostname
        try {
            components.add(InetAddress.getLocalHost().hostName)
        } catch (e: Exception) {
            components.add("unknown-host")
        }

        // User home directory (as additional entropy)
        components.add(System.getProperty("user.home", "/unknown"))

        // User name
        components.add(System.getProperty("user.name", "unknown"))

        return components.joinToString("|")
    }
}

@file:OptIn(ExperimentalForeignApi::class)

package io.writeopia.auth.core.repository

import io.writeopia.auth.core.manager.TokenData
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Secure token storage for iOS using Keychain Services.
 *
 * Uses kSecAttrAccessibleWhenUnlockedThisDeviceOnly for security:
 * - Tokens can only be accessed when the device is unlocked
 * - Tokens are not backed up to iCloud or transferred to new devices
 * - Tokens are encrypted with device-specific keys
 */
object KeychainTokenStorage {

    private const val SERVICE_NAME = "io.writeopia.auth"
    private const val KEY_ACCESS_TOKEN_PREFIX = "access_token_"
    private const val KEY_REFRESH_TOKEN_PREFIX = "refresh_token_"
    private const val KEY_EXPIRES_AT_PREFIX = "expires_at_"

    /**
     * Saves tokens securely in the iOS Keychain.
     */
    fun saveTokens(
        userId: String,
        accessToken: String,
        refreshToken: String?,
        expiresAt: Long?
    ) {
        setItem(KEY_ACCESS_TOKEN_PREFIX + userId, accessToken)
        if (refreshToken != null) {
            setItem(KEY_REFRESH_TOKEN_PREFIX + userId, refreshToken)
        } else {
            deleteItem(KEY_REFRESH_TOKEN_PREFIX + userId)
        }
        if (expiresAt != null) {
            setItem(KEY_EXPIRES_AT_PREFIX + userId, expiresAt.toString())
        } else {
            deleteItem(KEY_EXPIRES_AT_PREFIX + userId)
        }
    }

    /**
     * Retrieves the access token for a given user.
     */
    fun getAccessToken(userId: String): String? =
        getItem(KEY_ACCESS_TOKEN_PREFIX + userId)

    /**
     * Retrieves the refresh token for a given user.
     */
    fun getRefreshToken(userId: String): String? =
        getItem(KEY_REFRESH_TOKEN_PREFIX + userId)

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
    fun getExpiresAt(userId: String): Long? =
        getItem(KEY_EXPIRES_AT_PREFIX + userId)?.toLongOrNull()

    /**
     * Clears all tokens for a given user.
     */
    fun clearTokens(userId: String) {
        deleteItem(KEY_ACCESS_TOKEN_PREFIX + userId)
        deleteItem(KEY_REFRESH_TOKEN_PREFIX + userId)
        deleteItem(KEY_EXPIRES_AT_PREFIX + userId)
    }

    private fun setItem(key: String, value: String) {
        val valueData = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return

        // First, try to delete any existing item
        deleteItem(key)

        // Then add the new item
        memScoped {
            val query = CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                5,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr
            )

            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, CFBridgingRetain(SERVICE_NAME as NSString))
            CFDictionarySetValue(query, kSecAttrAccount, CFBridgingRetain(key as NSString))
            CFDictionarySetValue(query, kSecValueData, CFBridgingRetain(valueData))
            CFDictionarySetValue(query, kSecAttrAccessible, kSecAttrAccessibleWhenUnlockedThisDeviceOnly)

            SecItemAdd(query, null)
        }
    }

    private fun getItem(key: String): String? {
        memScoped {
            val query = CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                5,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr
            )

            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, CFBridgingRetain(SERVICE_NAME as NSString))
            CFDictionarySetValue(query, kSecAttrAccount, CFBridgingRetain(key as NSString))
            CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)

            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)

            return if (status == errSecSuccess && result.value != null) {
                val data = CFBridgingRelease(result.value) as? NSData
                data?.let {
                    NSString.create(data = it, encoding = NSUTF8StringEncoding) as? String
                }
            } else {
                null
            }
        }
    }

    private fun deleteItem(key: String) {
        memScoped {
            val query = CFDictionaryCreateMutable(
                kCFAllocatorDefault,
                3,
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr
            )

            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, CFBridgingRetain(SERVICE_NAME as NSString))
            CFDictionarySetValue(query, kSecAttrAccount, CFBridgingRetain(key as NSString))

            SecItemDelete(query)
        }
    }
}

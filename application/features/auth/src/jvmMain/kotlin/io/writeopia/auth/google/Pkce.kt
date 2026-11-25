package io.writeopia.auth.google

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** PKCE (RFC 7636) helpers for the desktop authorization code flow. */
internal object Pkce {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    /** 64 random bytes, base64url encoded: 86 characters, within the 43..128 allowed range. */
    fun verifier(): String = encoder.encodeToString(ByteArray(64).also(random::nextBytes))

    /** S256 challenge: base64url(sha256(ascii(verifier))). */
    fun challenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return encoder.encodeToString(digest)
    }

    fun state(): String = encoder.encodeToString(ByteArray(32).also(random::nextBytes))
}

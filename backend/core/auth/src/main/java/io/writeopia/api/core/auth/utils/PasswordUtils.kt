package io.writeopia.api.core.auth.utils

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

object PasswordUtils {
    @OptIn(ExperimentalEncodingApi::class)
    fun ByteArray.toBase64(): String = Base64.encode(this)

    @OptIn(ExperimentalEncodingApi::class)
    fun String.fromBase64(): ByteArray = Base64.decode(this)

    private fun generateSalt(size: Int = 16): ByteArray = Random.Default.nextBytes(size)

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun hashPasswordPBKDF2(password: String, salt: ByteArray = generateSalt()): Password {
        val pass = CryptographyProvider.Default
            .get(PBKDF2)
            .secretDerivation(
                digest = SHA512,
                iterations = 100_000,
                outputSize = BinarySize.Companion.run { 64.bytes },
                salt = salt
            ).deriveSecret(password.toByteArray())
            .toHexString()

        return Password(pass, salt.toBase64())
    }

    suspend fun hashPasswordPBKDF2(password: String, salt: String): Password {
        val byteSalt = salt.fromBase64()
        return hashPasswordPBKDF2(password, byteSalt)
    }
}

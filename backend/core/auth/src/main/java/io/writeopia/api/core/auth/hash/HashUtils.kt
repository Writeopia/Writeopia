package io.writeopia.api.core.auth.hash

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

const val ITERATIONS = 100_000
const val KEY_LENGTH = 512
const val SALT_LENGTH = 16

object HashUtils {
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun hashPassword(password: String, salt: ByteArray): ByteArray {
        val keySpec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        return keyFactory.generateSecret(keySpec).encoded
    }

    fun verifyPassword(
        inputPassword: String,
        storedSaltBase64: String,
        storedHashBase64: String
    ): Boolean {
        val salt = storedSaltBase64.base64ToBytes()
        val storedHash = storedHashBase64.base64ToBytes()
        val inputHash = hashPassword(inputPassword, salt)
        return inputHash contentEquals storedHash
    }
}

fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

fun String.base64ToBytes(): ByteArray = Base64.getDecoder().decode(this)

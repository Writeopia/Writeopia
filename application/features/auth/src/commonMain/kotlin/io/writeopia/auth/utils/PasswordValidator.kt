package io.writeopia.auth.utils

enum class PasswordStrength {
    NONE,
    WEAK,
    MEDIUM,
    STRONG
}

data class PasswordValidationResult(
    val strength: PasswordStrength,
    val hasMinLength: Boolean,
    val hasSpecialChar: Boolean
)

object PasswordValidator {

    private const val MIN_LENGTH = 8

    private val specialCharRegex = Regex("[!@#\$%^&*()_+\\-=\\[\\]{}|;':\",./<>?`~\\\\]")

    fun validate(password: String): PasswordValidationResult {
        val hasMinLength = password.length >= MIN_LENGTH
        val hasSpecialChar = specialCharRegex.containsMatchIn(password)

        val strength = when {
            password.isEmpty() -> PasswordStrength.NONE
            hasMinLength && hasSpecialChar -> PasswordStrength.STRONG
            hasMinLength || hasSpecialChar -> PasswordStrength.MEDIUM
            else -> PasswordStrength.WEAK
        }

        return PasswordValidationResult(
            strength = strength,
            hasMinLength = hasMinLength,
            hasSpecialChar = hasSpecialChar
        )
    }
}

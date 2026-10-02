package io.writeopia.api.core.auth.service

/**
 * Derives a username for accounts created through Google sign-in, where the user never types
 * one. The result always satisfies the registration rules: 3..30 chars of `[a-z0-9_-]`.
 */
object GoogleUsernameGenerator {
    private const val MIN_LENGTH = 3
    private const val MAX_LENGTH = 30
    private const val FALLBACK = "user"
    private const val MAX_ATTEMPTS = 50

    fun baseUsername(email: String): String {
        val cleaned = email
            .substringBefore('@')
            .lowercase()
            .filter { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }

        val padded = if (cleaned.length < MIN_LENGTH) cleaned + FALLBACK else cleaned
        return padded.take(MAX_LENGTH)
    }

    /**
     * @param exists Returns true when the candidate is already taken. The suffix grows (`-2`,
     * `-3`, ...) while keeping the total within [MAX_LENGTH]; after [MAX_ATTEMPTS] a random
     * suffix is used so a pathological collision run can't loop forever.
     */
    fun generate(email: String, exists: (String) -> Boolean): String {
        val base = baseUsername(email)
        if (!exists(base)) return base

        for (attempt in 2..(MAX_ATTEMPTS + 1)) {
            val candidate = withSuffix(base, "-$attempt")
            if (!exists(candidate)) return candidate
        }

        return withSuffix(base, "-" + (100_000..999_999).random())
    }

    private fun withSuffix(base: String, suffix: String): String =
        base.take(MAX_LENGTH - suffix.length) + suffix
}

package io.writeopia.api.core.auth.repository

import io.writeopia.sql.WriteopiaDbBackend

data class EmailVerificationData(
    val email: String,
    val code: String,
    val codeExpiry: Long,
    val attempts: Long,
    val createdAt: Long,
)

fun WriteopiaDbBackend.upsertEmailVerification(
    email: String,
    code: String,
    codeExpiry: Long,
    createdAt: Long,
) {
    this.emailVerificationQueries.upsertEmailVerification(email, code, codeExpiry, createdAt)
}

fun WriteopiaDbBackend.getEmailVerification(email: String): EmailVerificationData? =
    this.emailVerificationQueries
        .selectEmailVerification(email)
        .executeAsOneOrNull()
        ?.let { row ->
            EmailVerificationData(
                email = row.email,
                code = row.code,
                codeExpiry = row.code_expiry,
                attempts = row.attempts,
                createdAt = row.created_at,
            )
        }

fun WriteopiaDbBackend.incrementEmailVerificationAttempts(email: String) {
    this.emailVerificationQueries.incrementEmailVerificationAttempts(email)
}

fun WriteopiaDbBackend.deleteEmailVerification(email: String) {
    this.emailVerificationQueries.deleteEmailVerification(email)
}

fun WriteopiaDbBackend.deleteExpiredEmailVerifications(now: Long) {
    this.emailVerificationQueries.deleteExpiredEmailVerifications(now)
}

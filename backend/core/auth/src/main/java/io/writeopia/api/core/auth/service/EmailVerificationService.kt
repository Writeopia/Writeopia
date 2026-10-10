package io.writeopia.api.core.auth.service

import io.writeopia.api.core.auth.repository.deleteExpiredEmailVerifications
import io.writeopia.api.core.auth.repository.getEmailVerification
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.incrementEmailVerificationAttempts
import io.writeopia.api.core.auth.repository.upsertEmailVerification
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend
import java.security.MessageDigest
import kotlin.time.Clock

/**
 * Proves ownership of an email address before an account is created for it.
 *
 * Sign-up is: [requestCode] (emails a 6-digit code) -> [checkCode] (lets the client move on to
 * the account details form) -> `/api/auth/register`, which calls [checkCode] again and only then
 * creates the user. No `user_entity` row exists until the email has been verified.
 */
object EmailVerificationService {
    /** Failed code checks allowed per issued code. After that a new code must be requested. */
    const val MAX_ATTEMPTS = 5

    /** Minimum time between two codes sent to the same address, to avoid mail flooding. */
    const val RESEND_COOLDOWN_MS = 30_000L

    sealed interface RequestOutcome {
        data object Sent : RequestOutcome

        /** An account already uses this email. Nothing is sent. */
        data object AlreadyRegistered : RequestOutcome

        /** A code was sent to this email moments ago. Nothing new is sent. */
        data object Throttled : RequestOutcome

        data object SendFailed : RequestOutcome
    }

    suspend fun requestCode(
        writeopiaDb: WriteopiaDbBackend,
        email: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): RequestOutcome {
        if (writeopiaDb.getUserByEmail(email) != null) {
            return RequestOutcome.AlreadyRegistered
        }

        val existing = writeopiaDb.getEmailVerification(email)
        if (existing != null && now - existing.createdAt < RESEND_COOLDOWN_MS) {
            return RequestOutcome.Throttled
        }

        // Opportunistic cleanup, so abandoned sign-ups don't pile up.
        writeopiaDb.deleteExpiredEmailVerifications(now)

        val code = EmailService.generateConfirmationCode()
        writeopiaDb.upsertEmailVerification(
            email = email,
            code = code,
            codeExpiry = EmailService.getCodeExpiry(),
            createdAt = now,
        )

        val sent = EmailService.sendRegistrationCodeEmail(toEmail = email, code = code)
        return if (sent) RequestOutcome.Sent else RequestOutcome.SendFailed
    }

    /**
     * True if [code] is the current, unexpired code for [email] and the attempt budget isn't
     * spent. A wrong code consumes one attempt. A correct code is not consumed here: the
     * register endpoint deletes it in the same transaction that creates the account.
     */
    fun checkCode(
        writeopiaDb: WriteopiaDbBackend,
        email: String,
        code: String,
        now: Long = Clock.System.now().toEpochMilliseconds(),
    ): Boolean {
        val verification = writeopiaDb.getEmailVerification(email) ?: return false

        if (verification.attempts >= MAX_ATTEMPTS) {
            logger.warn("Registration code attempts exhausted for: $email")
            return false
        }

        val matches = MessageDigest.isEqual(
            verification.code.toByteArray(),
            code.trim().toByteArray()
        )

        if (!matches) {
            writeopiaDb.incrementEmailVerificationAttempts(email)
            return false
        }

        return verification.codeExpiry > now
    }
}

@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.core.email

import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

object EmailService {
    fun generateConfirmationCode(): String {
        return Random.nextInt(100000, 999999).toString()
    }

    fun getCodeExpiry(): Long {
        return Clock.System.now().plus(15.minutes).toEpochMilliseconds()
    }

    suspend fun sendConfirmationEmail(
        toEmail: String,
        code: String,
        userName: String
    ): Boolean =
        mailgunSender.send(
            toEmail = toEmail,
            subject = "Confirm your Writeopia email",
            text = buildEmailText(userName, code),
            html = buildEmailHtml(userName, code),
        )

    private fun buildEmailText(userName: String, code: String): String {
        return """
            Hi $userName,

            Welcome to Writeopia! Please confirm your email address by entering the following code:

            $code

            This code will expire in 15 minutes.

            If you didn't create an account with Writeopia, you can safely ignore this email.

            Best regards,
            The Writeopia Team
        """.trimIndent()
    }

    private fun buildEmailHtml(userName: String, code: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .code { font-size: 32px; font-weight: bold; letter-spacing: 8px; text-align: center; background: #f5f5f5; padding: 20px; border-radius: 8px; margin: 20px 0; }
                    .footer { color: #666; font-size: 14px; margin-top: 30px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Welcome to Writeopia!</h1>
                    <p>Hi $userName,</p>
                    <p>Please confirm your email address by entering the following code:</p>
                    <div class="code">$code</div>
                    <p>This code will expire in 15 minutes.</p>
                    <p class="footer">If you didn't create an account with Writeopia, you can safely ignore this email.</p>
                    <p class="footer">Best regards,<br>The Writeopia Team</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Sent before an account exists, to prove the person signing up owns [toEmail]. There is no
     * name to greet yet, since the account details are only collected after verification.
     */
    suspend fun sendRegistrationCodeEmail(toEmail: String, code: String): Boolean =
        mailgunSender.send(
            toEmail = toEmail,
            subject = "Your Writeopia verification code",
            text = buildRegistrationCodeEmailText(code),
            html = buildRegistrationCodeEmailHtml(code),
        )

    private fun buildRegistrationCodeEmailText(code: String): String {
        return """
            Hi,

            Welcome to Writeopia! Enter the following code to verify your email address and continue creating your account:

            $code

            This code will expire in 15 minutes.

            If you didn't try to create a Writeopia account, you can safely ignore this email.

            Best regards,
            The Writeopia Team
        """.trimIndent()
    }

    private fun buildRegistrationCodeEmailHtml(code: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .code { font-size: 32px; font-weight: bold; letter-spacing: 8px; text-align: center; background: #f5f5f5; padding: 20px; border-radius: 8px; margin: 20px 0; }
                    .footer { color: #666; font-size: 14px; margin-top: 30px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Welcome to Writeopia!</h1>
                    <p>Enter the following code to verify your email address and continue creating your account:</p>
                    <div class="code">$code</div>
                    <p>This code will expire in 15 minutes.</p>
                    <p class="footer">If you didn't try to create a Writeopia account, you can safely ignore this email.</p>
                    <p class="footer">Best regards,<br>The Writeopia Team</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    suspend fun sendPasswordResetEmail(
        toEmail: String,
        code: String,
        userName: String
    ): Boolean =
        mailgunSender.send(
            toEmail = toEmail,
            subject = "Reset your Writeopia password",
            text = buildPasswordResetEmailText(userName, code),
            html = buildPasswordResetEmailHtml(userName, code),
        )

    private fun buildPasswordResetEmailText(userName: String, code: String): String {
        return """
            Hi $userName,

            We received a request to reset your Writeopia password. Enter the following code to reset your password:

            $code

            This code will expire in 15 minutes.

            If you didn't request a password reset, you can safely ignore this email. Your password will not be changed.

            Best regards,
            The Writeopia Team
        """.trimIndent()
    }

    private fun buildPasswordResetEmailHtml(userName: String, code: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .code { font-size: 32px; font-weight: bold; letter-spacing: 8px; text-align: center; background: #f5f5f5; padding: 20px; border-radius: 8px; margin: 20px 0; }
                    .footer { color: #666; font-size: 14px; margin-top: 30px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Reset Your Password</h1>
                    <p>Hi $userName,</p>
                    <p>We received a request to reset your Writeopia password. Enter the following code to reset your password:</p>
                    <div class="code">$code</div>
                    <p>This code will expire in 15 minutes.</p>
                    <p class="footer">If you didn't request a password reset, you can safely ignore this email. Your password will not be changed.</p>
                    <p class="footer">Best regards,<br>The Writeopia Team</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Sent only after the account-deletion saga has confirmed the user's data (workspaces,
     * documents, media) is gone AND the user_entity row itself has been hard-deleted - see
     * AccountDeletionService.checkAndFinalize. Routed through the account-deletion-finalized
     * Pub/Sub topic (transactional outbox + Debezium) rather than called in-process right
     * after the delete, specifically so a crash or Mailgun failure here doesn't silently drop
     * the email - Pub/Sub redelivers the finalized event until this call succeeds.
     */
    suspend fun sendAccountDeletionCompleteEmail(
        toEmail: String,
        userName: String
    ): Boolean =
        mailgunSender.send(
            toEmail = toEmail,
            subject = "Your Writeopia account has been deleted",
            text = buildAccountDeletionEmailText(userName),
            html = buildAccountDeletionEmailHtml(userName),
        )

    private fun buildAccountDeletionEmailText(userName: String): String {
        return """
            Hi $userName,

            Your Writeopia account and all associated data - workspaces, documents, and
            uploaded media - have been permanently deleted, as requested.

            If you didn't request this, please contact us immediately.

            Best regards,
            The Writeopia Team
        """.trimIndent()
    }

    private fun buildAccountDeletionEmailHtml(userName: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .footer { color: #666; font-size: 14px; margin-top: 30px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>Account Deleted</h1>
                    <p>Hi $userName,</p>
                    <p>Your Writeopia account and all associated data - workspaces, documents, and uploaded media - have been permanently deleted, as requested.</p>
                    <p>If you didn't request this, please contact us immediately.</p>
                    <p class="footer">Best regards,<br>The Writeopia Team</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }
}

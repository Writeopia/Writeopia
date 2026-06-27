@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.core.auth.service

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.writeopia.connection.logger
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

object EmailService {
    private val client = HttpClient()

    private val mailgunApiKey: String?
        get() = System.getenv("MAILGUN_API_KEY")

    private val mailgunDomain: String?
        get() = System.getenv("MAILGUN_DOMAIN")

    private val mailgunFromEmail: String
        get() = System.getenv("MAILGUN_FROM_EMAIL") ?: "noreply@writeopia.io"

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
    ): Boolean {
        val apiKey = mailgunApiKey
        val domain = mailgunDomain

        if (apiKey == null || domain == null) {
            logger.warn("Mailgun not configured. MAILGUN_API_KEY or MAILGUN_DOMAIN missing.")
            logger.info("Confirmation code for $toEmail: $code")
            return true
        }

        return try {
            val response = client.submitForm(
                url = "https://api.mailgun.net/v3/$domain/messages",
                formParameters = Parameters.build {
                    append("from", "Writeopia <$mailgunFromEmail>")
                    append("to", toEmail)
                    append("subject", "Confirm your Writeopia email")
                    append("text", buildEmailText(userName, code))
                    append("html", buildEmailHtml(userName, code))
                }
            ) {
                header("Authorization", "Basic ${java.util.Base64.getEncoder().encodeToString("api:$apiKey".toByteArray())}")
            }

            if (response.status == HttpStatusCode.OK) {
                logger.info("Confirmation email sent to $toEmail")
                true
            } else {
                logger.error("Failed to send confirmation email to $toEmail: ${response.status}")
                false
            }
        } catch (e: Exception) {
            logger.error("Error sending confirmation email to $toEmail: ${e.message}")
            e.printStackTrace()
            false
        }
    }

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
}

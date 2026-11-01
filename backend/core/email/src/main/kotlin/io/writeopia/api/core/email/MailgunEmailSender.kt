package io.writeopia.api.core.email

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.writeopia.connection.logger
import java.util.Base64

internal class MailgunEmailSender(
    private val client: HttpClient = HttpClient(),
    private val apiKey: String? = System.getenv("MAILGUN_API_KEY"),
    private val domain: String? = System.getenv("MAILGUN_DOMAIN"),
    private val fromEmail: String = System.getenv("MAILGUN_FROM_EMAIL") ?: "noreply@writeopia.io",
) {
    suspend fun send(toEmail: String, subject: String, text: String, html: String): Boolean {
        if (apiKey == null || domain == null) {
            logger.warn("Mailgun not configured. MAILGUN_API_KEY or MAILGUN_DOMAIN missing.")
            logger.info("Email not sent to $toEmail. Subject: $subject\n$text")
            return true
        }

        return try {
            val response = client.submitForm(
                url = "https://api.eu.mailgun.net/v3/$domain/messages",
                formParameters = Parameters.build {
                    append("from", "Writeopia <$fromEmail>")
                    append("to", toEmail)
                    append("subject", subject)
                    append("text", text)
                    append("html", html)
                }
            ) {
                header("Authorization", "Basic ${Base64.getEncoder().encodeToString("api:$apiKey".toByteArray())}")
            }

            if (response.status == HttpStatusCode.OK) {
                logger.info("Email sent to $toEmail: $subject")
                true
            } else {
                logger.error("Failed to send email to $toEmail: ${response.status}")
                false
            }
        } catch (e: Exception) {
            logger.error("Error sending email to $toEmail: ${e.message}")
            e.printStackTrace()
            false
        }
    }
}

internal val mailgunSender = MailgunEmailSender()

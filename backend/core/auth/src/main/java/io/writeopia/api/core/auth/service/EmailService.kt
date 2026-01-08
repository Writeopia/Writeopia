package io.writeopia.api.core.auth.service

object EmailService {
    fun sendVerificationEmail(email: String, token: String) {
        val url = "https://your-domain.com/api/verify?token=$token"
        // Use an HTTP client (like Ktor Client) to POST to SendGrid's /v3/mail/send
        // or use their SDK.
    }
}

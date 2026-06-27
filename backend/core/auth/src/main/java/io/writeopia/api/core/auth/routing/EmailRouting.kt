package io.writeopia.api.core.auth.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.writeopia.api.core.auth.repository.clearConfirmationCode
import io.writeopia.api.core.auth.repository.enableUserByEmail
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.isCodeValid
import io.writeopia.api.core.auth.repository.updateConfirmationCode
import io.writeopia.api.core.auth.service.EmailService
import io.writeopia.connection.logger
import io.writeopia.sdk.serialization.data.auth.EmailConfirmRequest
import io.writeopia.sdk.serialization.data.auth.EmailConfirmResponse
import io.writeopia.sdk.serialization.data.auth.EmailResendRequest
import io.writeopia.sql.WriteopiaDbBackend

fun Routing.emailRoute(writeopiaDb: WriteopiaDbBackend) {
    post("/api/auth/email/confirm") {
        try {
            val request = call.receive<EmailConfirmRequest>()
            logger.info("Email confirmation request for: ${request.email}")

            val isValid = writeopiaDb.isCodeValid(request.email, request.code)

            if (isValid) {
                writeopiaDb.enableUserByEmail(request.email)
                writeopiaDb.clearConfirmationCode(request.email)

                logger.info("Email confirmed successfully for: ${request.email}")
                call.respond(
                    HttpStatusCode.OK,
                    EmailConfirmResponse(success = true, message = "Email confirmed successfully")
                )
            } else {
                logger.warn("Invalid or expired confirmation code for: ${request.email}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    EmailConfirmResponse(success = false, message = "Invalid or expired confirmation code")
                )
            }
        } catch (e: Exception) {
            logger.error("Error during email confirmation: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                EmailConfirmResponse(success = false, message = "An error occurred")
            )
        }
    }

    post("/api/auth/email/resend") {
        try {
            val request = call.receive<EmailResendRequest>()
            logger.info("Resend confirmation email request for: ${request.email}")

            val user = writeopiaDb.getUserByEmail(request.email)

            if (user == null) {
                logger.warn("User not found for email: ${request.email}")
                call.respond(
                    HttpStatusCode.NotFound,
                    EmailConfirmResponse(success = false, message = "User not found")
                )
                return@post
            }

            if (user.enabled) {
                logger.info("User already confirmed: ${request.email}")
                call.respond(
                    HttpStatusCode.OK,
                    EmailConfirmResponse(success = true, message = "Email already confirmed")
                )
                return@post
            }

            val newCode = EmailService.generateConfirmationCode()
            val newExpiry = EmailService.getCodeExpiry()

            writeopiaDb.updateConfirmationCode(request.email, newCode, newExpiry)

            val emailSent = EmailService.sendConfirmationEmail(
                toEmail = request.email,
                code = newCode,
                userName = user.name
            )

            if (emailSent) {
                logger.info("Confirmation email resent to: ${request.email}")
                call.respond(
                    HttpStatusCode.OK,
                    EmailConfirmResponse(success = true, message = "Confirmation email sent")
                )
            } else {
                logger.error("Failed to resend confirmation email to: ${request.email}")
                call.respond(
                    HttpStatusCode.InternalServerError,
                    EmailConfirmResponse(success = false, message = "Failed to send email")
                )
            }
        } catch (e: Exception) {
            logger.error("Error resending confirmation email: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                EmailConfirmResponse(success = false, message = "An error occurred")
            )
        }
    }
}

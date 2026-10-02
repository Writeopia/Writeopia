package io.writeopia.api.core.auth.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.writeopia.api.core.auth.models.UserStatus
import io.writeopia.api.core.auth.models.toApi
import io.writeopia.api.core.auth.repository.confirmEmailIfNotPendingDeletion
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.isCodeValid
import io.writeopia.api.core.auth.repository.updateConfirmationCode
import io.writeopia.api.core.auth.service.EmailService
import io.writeopia.api.core.auth.service.RefreshTokenService
import io.writeopia.connection.logger
import io.writeopia.sdk.serialization.data.auth.AuthResponse
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
                val confirmed = writeopiaDb.confirmEmailIfNotPendingDeletion(request.email)

                if (!confirmed) {
                    logger.warn("Email confirmation rejected, account is being deleted: ${request.email}")
                    call.respond(
                        HttpStatusCode.Conflict,
                        EmailConfirmResponse(success = false, message = "Account is being deleted")
                    )
                    return@post
                }

                val user = writeopiaDb.getUserByEmail(request.email)

                if (user != null) {
                    val tokenPair = with(RefreshTokenService) {
                        writeopiaDb.generateAndStoreTokens(user.id)
                    }
                    logger.info("Email confirmed successfully for: ${request.email}")
                    call.respond(
                        HttpStatusCode.OK,
                        AuthResponse(
                            accessToken = tokenPair.accessToken,
                            refreshToken = tokenPair.refreshToken,
                            writeopiaUser = user.toApi(),
                            enabled = true
                        )
                    )
                } else {
                    logger.error("User not found after email confirmation: ${request.email}")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        EmailConfirmResponse(success = false, message = "User not found")
                    )
                }
            } else {
                logger.warn("Invalid or expired confirmation code for: ${request.email}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    EmailConfirmResponse(
                        success = false,
                        message = "Invalid or expired confirmation code"
                    )
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

            // Unknown, already active and deletion-pending accounts all receive the same generic
            // response as an eligible account, so the endpoint cannot be used to enumerate
            // accounts or probe their activation state. The real outcome is only logged.
            when {
                user == null -> {
                    logger.warn("User not found for email: ${request.email}")
                    call.respond(HttpStatusCode.OK, resendGenericResponse)
                    return@post
                }

                user.status == UserStatus.ACTIVE -> {
                    logger.info("User already confirmed: ${request.email}")
                    call.respond(HttpStatusCode.OK, resendGenericResponse)
                    return@post
                }

                user.status == UserStatus.DELETION_PENDING -> {
                    logger.warn("Resend confirmation skipped, account is being deleted: ${request.email}")
                    call.respond(HttpStatusCode.OK, resendGenericResponse)
                    return@post
                }
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
                call.respond(HttpStatusCode.OK, resendGenericResponse)
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

/**
 * Response returned by `/api/auth/email/resend` regardless of whether the email belongs to an
 * unknown, pending, active or deletion-pending account. Keeping it identical prevents account
 * enumeration (CWE-203).
 */
internal val resendGenericResponse = EmailConfirmResponse(
    success = true,
    message = "If an account with this email needs confirmation, a confirmation email has been sent"
)

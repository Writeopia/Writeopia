package io.writeopia.api.core.auth.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.writeopia.api.core.auth.repository.clearConfirmationCode
import io.writeopia.api.core.auth.repository.getUserByEmail
import io.writeopia.api.core.auth.repository.isCodeValid
import io.writeopia.api.core.auth.repository.updateConfirmationCode
import io.writeopia.api.core.auth.service.AuthService
import io.writeopia.api.core.auth.service.EmailService
import io.writeopia.connection.logger
import io.writeopia.sdk.serialization.data.auth.ForgotPasswordRequest
import io.writeopia.sdk.serialization.data.auth.ForgotPasswordResponse
import io.writeopia.sdk.serialization.data.auth.PasswordResetWithCodeRequest
import io.writeopia.sdk.serialization.data.auth.PasswordVerifyCodeRequest
import io.writeopia.sql.WriteopiaDbBackend

fun Routing.passwordResetRoute(writeopiaDb: WriteopiaDbBackend) {
    post("/api/auth/password/forgot") {
        try {
            val request = call.receive<ForgotPasswordRequest>()
            logger.info("Password reset request for: ${request.email}")

            val user = writeopiaDb.getUserByEmail(request.email)

            if (user != null && user.enabled) {
                val code = EmailService.generateConfirmationCode()
                val codeExpiry = EmailService.getCodeExpiry()

                writeopiaDb.updateConfirmationCode(request.email, code, codeExpiry)

                EmailService.sendPasswordResetEmail(
                    toEmail = request.email,
                    code = code,
                    userName = user.name
                )

                logger.info("Password reset code sent to: ${request.email}")
            } else {
                // For security, don't reveal if user exists or not
                logger.info("Password reset requested for non-existent or disabled user: ${request.email}")
            }

            // Always return success for security (don't reveal if email exists)
            call.respond(
                HttpStatusCode.OK,
                ForgotPasswordResponse(success = true, message = "If the email exists, a reset code has been sent")
            )
        } catch (e: Exception) {
            logger.error("Error during password reset request: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ForgotPasswordResponse(success = false, message = "An error occurred")
            )
        }
    }

    post("/api/auth/password/verify-code") {
        try {
            val request = call.receive<PasswordVerifyCodeRequest>()
            logger.info("Password reset code verification for: ${request.email}")

            val isValid = writeopiaDb.isCodeValid(request.email, request.code)

            if (isValid) {
                logger.info("Password reset code verified successfully for: ${request.email}")
                // Note: We do NOT clear the code here, it's still needed for the final reset
                call.respond(
                    HttpStatusCode.OK,
                    ForgotPasswordResponse(success = true, message = "Code verified successfully")
                )
            } else {
                logger.warn("Invalid or expired password reset code for: ${request.email}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    ForgotPasswordResponse(success = false, message = "Invalid or expired code")
                )
            }
        } catch (e: Exception) {
            logger.error("Error during password reset code verification: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ForgotPasswordResponse(success = false, message = "An error occurred")
            )
        }
    }

    post("/api/auth/password/reset-with-code") {
        try {
            val request = call.receive<PasswordResetWithCodeRequest>()
            logger.info("Password reset with code for: ${request.email}")

            // Re-validate the code for security
            val isValid = writeopiaDb.isCodeValid(request.email, request.code)

            if (!isValid) {
                logger.warn("Invalid or expired code during password reset for: ${request.email}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    ForgotPasswordResponse(success = false, message = "Invalid or expired code")
                )
                return@post
            }

            val user = writeopiaDb.getUserByEmail(request.email)

            if (user == null) {
                logger.warn("User not found during password reset: ${request.email}")
                call.respond(
                    HttpStatusCode.NotFound,
                    ForgotPasswordResponse(success = false, message = "User not found")
                )
                return@post
            }

            // Reset the password
            AuthService.resetPassword(writeopiaDb, user, request.newPassword)

            // Clear the code after successful reset
            writeopiaDb.clearConfirmationCode(request.email)

            logger.info("Password reset successfully for: ${request.email}")
            call.respond(
                HttpStatusCode.OK,
                ForgotPasswordResponse(success = true, message = "Password reset successfully")
            )
        } catch (e: Exception) {
            logger.error("Error during password reset: ${e.message}")
            e.printStackTrace()
            call.respond(
                HttpStatusCode.InternalServerError,
                ForgotPasswordResponse(success = false, message = "An error occurred")
            )
        }
    }
}

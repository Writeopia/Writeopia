package io.writeopia.sdk.serialization.data.auth

import kotlinx.serialization.Serializable

/**
 * @property verificationCode The code sent to [email] by `/api/auth/register/email/send`.
 *           Registration is rejected without a valid one, so accounts are only created for
 *           verified emails. Nullable only so older clients get a clear 400 instead of a
 *           deserialization error.
 * @property acceptedLegalVersion The version of the Terms of Service and Privacy Policy the user
 *           accepted (`LegalDocumentResponse.version`). Registration is rejected unless it is the current
 *           one, and the acceptance is stored with the new user.
 */
@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val username: String,
    val workspaceName: String,
    val password: String,
    val verificationCode: String? = null,
    val acceptedLegalVersion: String? = null,
)

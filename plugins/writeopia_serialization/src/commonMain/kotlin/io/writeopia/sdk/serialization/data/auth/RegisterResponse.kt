package io.writeopia.sdk.serialization.data.auth

import io.writeopia.sdk.serialization.data.WriteopiaUserApi
import kotlinx.serialization.Serializable

/**
 * Response returned after successful user registration.
 *
 * @property writeopiaUser The newly created user's information.
 * @property emailConfirmationRequired If true, the client should navigate to email confirmation
 *           flow before proceeding. The email is now verified before registration, so the
 *           backend returns false and the session tokens below.
 * @property accessToken Session access token for the new, already active, account.
 * @property refreshToken Session refresh token for the new, already active, account.
 */
@Serializable
data class RegisterResponse(
    val writeopiaUser: WriteopiaUserApi,
    val emailConfirmationRequired: Boolean,
    val accessToken: String? = null,
    val refreshToken: String? = null,
)

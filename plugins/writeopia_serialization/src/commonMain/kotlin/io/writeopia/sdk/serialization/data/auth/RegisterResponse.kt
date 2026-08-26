package io.writeopia.sdk.serialization.data.auth

import io.writeopia.sdk.serialization.data.WriteopiaUserApi
import kotlinx.serialization.Serializable

/**
 * Response returned after successful user registration.
 *
 * @property writeopiaUser The newly created user's information.
 * @property emailConfirmationRequired If true, the client should navigate to email confirmation
 *           flow before proceeding. If false, the user can proceed directly to workspace selection.
 */
@Serializable
data class RegisterResponse(
    val writeopiaUser: WriteopiaUserApi,
    val emailConfirmationRequired: Boolean
)

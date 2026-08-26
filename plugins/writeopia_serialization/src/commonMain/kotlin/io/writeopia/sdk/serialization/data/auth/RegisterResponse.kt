package io.writeopia.sdk.serialization.data.auth

import io.writeopia.sdk.serialization.data.WriteopiaUserApi
import kotlinx.serialization.Serializable

@Serializable
data class RegisterResponse(
    val writeopiaUser: WriteopiaUserApi,
    val emailConfirmationRequired: Boolean
)

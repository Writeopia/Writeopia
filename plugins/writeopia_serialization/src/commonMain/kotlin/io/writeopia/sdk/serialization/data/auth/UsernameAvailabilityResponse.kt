package io.writeopia.sdk.serialization.data.auth

import kotlinx.serialization.Serializable

/**
 * Answer of `/api/auth/username/available`: whether a new account can use the username.
 */
@Serializable
data class UsernameAvailabilityResponse(val available: Boolean)

package io.writeopia.api.core.auth.models

import kotlinx.serialization.Serializable

@Serializable
data class ManageUserRequest(val email: String)

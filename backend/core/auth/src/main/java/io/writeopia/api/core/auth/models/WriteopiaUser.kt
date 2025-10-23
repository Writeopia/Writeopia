package io.writeopia.api.core.auth.models;

import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.serialization.data.WriteopiaUserApi

data class WriteopiaBeUser(
    val id: String,
    val email: String,
    val name: String,
    val password: String,
    val salt: String,
    val workspace: String,
    val enabled: Boolean,
    val tier: Tier = Tier.FREE
) {
    companion object {
        const val DISCONNECTED = "disconnected_user"

        fun disconnectedUser(): WriteopiaBeUser =
            WriteopiaBeUser(id = "disconnected_user", "", "", "", "", "", enabled = false)
    }
}


fun WriteopiaBeUser.toApi() =
    WriteopiaUserApi(
        id = id,
        email = email,
        name = name
    )

fun WriteopiaUserApi.toModel() =
    WriteopiaUser(
        id = id,
        email = email,
        name = name,
    )

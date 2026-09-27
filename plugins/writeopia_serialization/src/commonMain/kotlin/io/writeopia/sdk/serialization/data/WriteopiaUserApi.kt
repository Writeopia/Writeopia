package io.writeopia.sdk.serialization.data

import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.user.WriteopiaUser
import kotlinx.serialization.Serializable

@Serializable
data class WriteopiaUserApi(
    val id: String,
    val email: String,
    val name: String,
    /** Plan of the user: "FREE" or "PREMIUM". Missing in responses of older backends. */
    val tier: String = Tier.FREE.name,
)

fun WriteopiaUserApi.toModel(): WriteopiaUser =
    WriteopiaUser(
        id = this.id,
        email = this.email,
        name = this.name,
        tier = Tier.fromName(this.tier),
    )

fun WriteopiaUser.toApi(): WriteopiaUserApi =
    WriteopiaUserApi(
        id = this.id,
        email = this.email,
        name = this.name,
        tier = this.tier.name,
    )

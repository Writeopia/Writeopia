package io.writeopia.sdk.serialization.data

import io.writeopia.sdk.models.user.WriteopiaUser
import kotlinx.serialization.Serializable

@Serializable
data class WriteopiaUserApi(
    val id: String,
    val email: String,
    val password: String,
    val name: String
)

fun WriteopiaUserApi.toModel(): WriteopiaUser =
    WriteopiaUser(
        id = this.id,
        email = this.email,
        password = this.password,
        name = this.name
    )

fun WriteopiaUser.toApi(): WriteopiaUserApi =
    WriteopiaUserApi(
        id = this.id,
        email = this.email,
        password = this.password,
        name = this.name
    )


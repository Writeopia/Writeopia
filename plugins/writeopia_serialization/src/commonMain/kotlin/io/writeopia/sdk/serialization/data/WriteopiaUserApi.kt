package io.writeopia.sdk.serialization.data

import io.writeopia.sdk.models.user.WriteopiaUser
import kotlinx.serialization.Serializable

@Serializable
data class WriteopiaUserApi(
    val id: String,
    val email: String,
    val name: String,
//    val company: String,
)

fun WriteopiaUserApi.toModel(): WriteopiaUser =
    WriteopiaUser(
        id = this.id,
        email = this.email,
        name = this.name,
        company = ""
//        company = this.company
    )

fun WriteopiaUser.toApi(): WriteopiaUserApi =
    WriteopiaUserApi(
        id = this.id,
        email = this.email,
        name = this.name,
//        company = this.company
    )

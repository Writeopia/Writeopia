package io.writeopia.app.dto

import kotlinx.serialization.Serializable

@Serializable
data class SearchUserApi(
    val id: String,
    val name: String,
    val username: String,
)


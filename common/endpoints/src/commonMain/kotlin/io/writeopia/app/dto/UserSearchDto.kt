package io.writeopia.app.dto

import kotlinx.serialization.Serializable

@Serializable
data class SearchUserApi(
    val id: String,
    val name: String,
    val email: String,
)

@Serializable
data class PaginatedUserSearchResponse(
    val users: List<SearchUserApi>,
    val page: Int,
    val pageSize: Int,
    val hasNextPage: Boolean,
)

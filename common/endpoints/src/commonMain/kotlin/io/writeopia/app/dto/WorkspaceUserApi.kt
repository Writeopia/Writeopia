package io.writeopia.app.dto

import kotlinx.serialization.Serializable

@Serializable
data class WorkspaceUserApi(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
)

@Serializable
data class PaginatedWorkspaceUsersResponse(
    val users: List<WorkspaceUserApi>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val totalPages: Int,
    val hasNextPage: Boolean,
)

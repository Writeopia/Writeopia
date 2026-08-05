package io.writeopia.models.user

data class WorkspaceUser(
    val id: String,
    val email: String,
    val name: String,
    val role: String,
)

data class PaginatedWorkspaceUsers(
    val users: List<WorkspaceUser>,
    val page: Int,
    val pageSize: Int,
    val totalCount: Int,
    val totalPages: Int,
    val hasNextPage: Boolean,
)

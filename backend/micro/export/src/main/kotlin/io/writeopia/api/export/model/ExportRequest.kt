package io.writeopia.api.export.model

data class ExportRequest(
    val workspaceId: String,
    val userId: String,
    val userEmail: String,
    val userName: String
)

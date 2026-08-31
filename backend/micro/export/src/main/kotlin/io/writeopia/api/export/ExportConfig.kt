package io.writeopia.api.export

import io.writeopia.api.export.model.ExportRequest

object ExportConfig {

    fun loadFromEnvironment(): ExportRequest {
        val workspaceId = System.getenv("EXPORT_WORKSPACE_ID")
            ?: throw IllegalStateException("EXPORT_WORKSPACE_ID environment variable is required")
        val userId = System.getenv("EXPORT_USER_ID")
            ?: throw IllegalStateException("EXPORT_USER_ID environment variable is required")
        val userEmail = System.getenv("EXPORT_USER_EMAIL")
            ?: throw IllegalStateException("EXPORT_USER_EMAIL environment variable is required")
        val userName = System.getenv("EXPORT_USER_NAME") ?: "User"

        return ExportRequest(
            workspaceId = workspaceId,
            userId = userId,
            userEmail = userEmail,
            userName = userName
        )
    }

    fun exportsBucketName(): String =
        System.getenv("EXPORTS_BUCKET_NAME")
            ?: throw IllegalStateException("EXPORTS_BUCKET_NAME environment variable is required")
}

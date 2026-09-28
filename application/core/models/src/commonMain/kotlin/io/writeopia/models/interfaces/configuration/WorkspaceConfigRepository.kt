package io.writeopia.models.interfaces.configuration

interface WorkspaceConfigRepository {

    suspend fun loadWorkspacePath(userId: String): String?

    suspend fun saveWorkspacePath(path: String, userId: String)
}

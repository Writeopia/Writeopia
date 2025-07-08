package io.writeopia.sqldelight.dao

import io.writeopia.common.utils.extensions.toBoolean
import io.writeopia.common.utils.extensions.toLong
import io.writeopia.sdk.models.Workspace
import io.writeopia.sql.WriteopiaDb
import kotlinx.datetime.Instant

class WorkspaceSqlDelightDao(private val database: WriteopiaDb?) {

    suspend fun insert(workspace: Workspace) {
        database?.workspace_entityQueries
            ?.insert(
                id = workspace.id,
                user_id = workspace.userId,
                name = workspace.name,
                last_synced_at = workspace.lastSync.toEpochMilliseconds(),
                icon = null,
                icon_tint = null,
                selected = workspace.selected.toLong()
            )
    }

    suspend fun getById(id: String): Workspace? =
        database?.workspace_entityQueries
            ?.getWorkspaceById(id)
            ?.executeAsOneOrNull()
            ?.let { entity ->
                Workspace(
                    id = entity.id,
                    userId = entity.user_id,
                    name = entity.name,
                    lastSync = Instant.fromEpochMilliseconds(entity.last_synced_at),
                    selected = entity.selected.toBoolean()
                )
            }
}

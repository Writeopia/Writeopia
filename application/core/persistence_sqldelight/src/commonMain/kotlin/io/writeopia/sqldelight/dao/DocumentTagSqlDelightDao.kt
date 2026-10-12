@file:OptIn(ExperimentalTime::class)

package io.writeopia.sqldelight.dao

import io.writeopia.app.sql.DocumentTagEntity
import io.writeopia.app.sql.DocumentTagEntityQueries
import io.writeopia.common.utils.persistence.daos.DocumentTagCommonDao
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.sql.WriteopiaDb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The workspace tag catalogue on SQLDelight. The listened workspaces are re-read after every write,
 * the same way the folder DAO publishes its changes.
 */
class DocumentTagSqlDelightDao(database: WriteopiaDb?) : DocumentTagCommonDao {

    private val queries: DocumentTagEntityQueries? = database?.documentTagEntityQueries

    private val tagsState = MutableStateFlow<Map<String, List<DocumentTag>>>(emptyMap())
    private val listenedWorkspaces = mutableSetOf<String>()

    override suspend fun upsertTag(tag: DocumentTag) {
        queries?.insert(
            id = tag.id,
            workspace_id = tag.workspaceId,
            name = tag.name,
            description = tag.description,
            created_at = tag.createdAt.toEpochMilliseconds(),
            last_updated_at = tag.lastUpdatedAt.toEpochMilliseconds(),
            last_synced_at = tag.lastSyncedAt?.toEpochMilliseconds(),
            deleted = if (tag.deleted) 1 else 0,
        )
        refresh()
    }

    override suspend fun getTagById(id: String): DocumentTag? =
        queries?.selectById(id)?.executeAsOneOrNull()?.toModel()

    override suspend fun getTagsForWorkspace(workspaceId: String): List<DocumentTag> =
        queries?.selectByWorkspace(workspaceId)?.executeAsList()?.map { it.toModel() } ?: emptyList()

    override suspend fun getAllTagsForWorkspace(workspaceId: String): List<DocumentTag> =
        queries?.selectAllByWorkspace(workspaceId)?.executeAsList()?.map { it.toModel() } ?: emptyList()

    override fun listenForTagsByWorkspace(workspaceId: String): Flow<List<DocumentTag>> {
        listenedWorkspaces.add(workspaceId)
        tagsState.value = tagsState.value + (workspaceId to currentTags(workspaceId))

        return tagsState.map { byWorkspace -> byWorkspace[workspaceId] ?: emptyList() }
    }

    override suspend fun softDeleteById(id: String, workspaceId: String, lastUpdatedAt: Long) {
        queries?.softDelete(lastUpdatedAt, id, workspaceId)
        refresh()
    }

    override suspend fun moveToWorkspace(oldWorkspaceId: String, newWorkspaceId: String) {
        queries?.moveToWorkspace(newWorkspaceId, oldWorkspaceId)
        refresh()
    }

    private fun currentTags(workspaceId: String): List<DocumentTag> =
        queries?.selectByWorkspace(workspaceId)?.executeAsList()?.map { it.toModel() } ?: emptyList()

    private fun refresh() {
        tagsState.value = listenedWorkspaces.associateWith(::currentTags)
    }

    private fun DocumentTagEntity.toModel() = DocumentTag(
        id = id,
        workspaceId = workspace_id,
        name = name,
        description = description,
        createdAt = Instant.fromEpochMilliseconds(created_at),
        lastUpdatedAt = Instant.fromEpochMilliseconds(last_updated_at),
        lastSyncedAt = last_synced_at?.let(Instant::fromEpochMilliseconds),
        deleted = deleted == 1L,
    )
}

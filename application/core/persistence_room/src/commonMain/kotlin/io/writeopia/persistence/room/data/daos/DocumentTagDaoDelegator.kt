@file:OptIn(ExperimentalTime::class)

package io.writeopia.persistence.room.data.daos

import io.writeopia.common.utils.persistence.daos.DocumentTagCommonDao
import io.writeopia.persistence.room.data.entities.DocumentTagRoomEntity
import io.writeopia.sdk.models.document.DocumentTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DocumentTagDaoDelegator(
    private val delegate: DocumentTagRoomDao
) : DocumentTagCommonDao {

    override suspend fun upsertTag(tag: DocumentTag) {
        delegate.upsertTag(tag.toRoomEntity())
    }

    override suspend fun getTagById(id: String): DocumentTag? = delegate.getTagById(id)?.toModel()

    override suspend fun getTagsForWorkspace(workspaceId: String): List<DocumentTag> =
        delegate.getTagsByWorkspaceId(workspaceId).map { it.toModel() }

    override suspend fun getAllTagsForWorkspace(workspaceId: String): List<DocumentTag> =
        delegate.getAllTagsByWorkspaceId(workspaceId).map { it.toModel() }

    override fun listenForTagsByWorkspace(workspaceId: String): Flow<List<DocumentTag>> =
        delegate.listenForTagsByWorkspaceId(workspaceId).map { list -> list.map { it.toModel() } }

    override suspend fun softDeleteById(id: String, workspaceId: String, lastUpdatedAt: Long) {
        delegate.softDeleteById(id, workspaceId, lastUpdatedAt)
    }

    override suspend fun moveToWorkspace(oldWorkspaceId: String, newWorkspaceId: String) {
        delegate.moveToWorkspace(oldWorkspaceId, newWorkspaceId)
    }

    private fun DocumentTag.toRoomEntity() = DocumentTagRoomEntity(
        id = id,
        workspaceId = workspaceId,
        name = name,
        description = description,
        createdAt = createdAt.toEpochMilliseconds(),
        lastUpdatedAt = lastUpdatedAt.toEpochMilliseconds(),
        lastSyncedAt = lastSyncedAt?.toEpochMilliseconds(),
        deleted = deleted,
    )

    private fun DocumentTagRoomEntity.toModel() = DocumentTag(
        id = id,
        workspaceId = workspaceId,
        name = name,
        description = description,
        createdAt = Instant.fromEpochMilliseconds(createdAt),
        lastUpdatedAt = Instant.fromEpochMilliseconds(lastUpdatedAt),
        lastSyncedAt = lastSyncedAt?.let(Instant::fromEpochMilliseconds),
        deleted = deleted,
    )
}

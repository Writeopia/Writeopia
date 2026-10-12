@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.repository.tag

import io.writeopia.common.utils.persistence.daos.DocumentTagCommonDao
import io.writeopia.sdk.models.document.DocumentTag
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The catalogue on a local database, Room or SQLDelight, through the common DAO. */
class DaoDocumentTagRepository(
    private val dao: DocumentTagCommonDao
) : DocumentTagRepository {

    override fun listenForTags(workspaceId: String): Flow<List<DocumentTag>> =
        dao.listenForTagsByWorkspace(workspaceId)

    override suspend fun getTagsForWorkspace(workspaceId: String): List<DocumentTag> =
        dao.getTagsForWorkspace(workspaceId)

    override suspend fun getAllTagsForWorkspace(workspaceId: String): List<DocumentTag> =
        dao.getAllTagsForWorkspace(workspaceId)

    override suspend fun getTagById(id: String): DocumentTag? = dao.getTagById(id)

    override suspend fun upsertTag(tag: DocumentTag) {
        dao.upsertTag(tag)
    }

    override suspend fun softDeleteTag(id: String, workspaceId: String) {
        dao.softDeleteById(id, workspaceId, Clock.System.now().toEpochMilliseconds())
    }

    override suspend fun moveTagsToWorkspace(oldWorkspaceId: String, newWorkspaceId: String) {
        dao.moveToWorkspace(oldWorkspaceId, newWorkspaceId)
    }
}

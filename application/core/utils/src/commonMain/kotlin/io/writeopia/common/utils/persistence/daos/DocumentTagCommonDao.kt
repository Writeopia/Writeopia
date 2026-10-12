package io.writeopia.common.utils.persistence.daos

import io.writeopia.sdk.models.document.DocumentTag
import kotlinx.coroutines.flow.Flow

/** The workspace tag catalogue, as the app sees it across Room and SQLDelight. */
interface DocumentTagCommonDao {
    suspend fun upsertTag(tag: DocumentTag)

    suspend fun getTagById(id: String): DocumentTag?

    /** The tags of the workspace that are not tombstones, by name. */
    suspend fun getTagsForWorkspace(workspaceId: String): List<DocumentTag>

    /** Every tag of the workspace, tombstones included: what the sync compares. */
    suspend fun getAllTagsForWorkspace(workspaceId: String): List<DocumentTag>

    fun listenForTagsByWorkspace(workspaceId: String): Flow<List<DocumentTag>>

    suspend fun softDeleteById(id: String, workspaceId: String, lastUpdatedAt: Long)

    suspend fun moveToWorkspace(oldWorkspaceId: String, newWorkspaceId: String)
}

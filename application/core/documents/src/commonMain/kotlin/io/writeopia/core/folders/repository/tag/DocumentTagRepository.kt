package io.writeopia.core.folders.repository.tag

import io.writeopia.sdk.models.document.DocumentTag
import kotlinx.coroutines.flow.Flow

/** The workspace tag catalogue: the tags documents can carry, with their explanations. */
interface DocumentTagRepository {

    /** The live, non-deleted tags of the workspace, by name. */
    fun listenForTags(workspaceId: String): Flow<List<DocumentTag>>

    /** The non-deleted tags of the workspace, by name. */
    suspend fun getTagsForWorkspace(workspaceId: String): List<DocumentTag>

    /** Every tag of the workspace, tombstones included: what the sync compares and sends. */
    suspend fun getAllTagsForWorkspace(workspaceId: String): List<DocumentTag>

    suspend fun getTagById(id: String): DocumentTag?

    suspend fun upsertTag(tag: DocumentTag)

    /** Marks the tag deleted and bumps its update time, so the removal syncs like an edit. */
    suspend fun softDeleteTag(id: String, workspaceId: String)

    /** The tags changed since they were last sent to the backend, tombstones included. */
    suspend fun localOutdatedTags(workspaceId: String): List<DocumentTag> =
        getAllTagsForWorkspace(workspaceId).filter { tag -> tag.isOutdated }

    /** Moves the catalogue of the offline workspace into the one the user signed into. */
    suspend fun moveTagsToWorkspace(oldWorkspaceId: String, newWorkspaceId: String)
}

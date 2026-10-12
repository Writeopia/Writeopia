@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.repository.tag

import io.writeopia.sdk.models.document.DocumentTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The catalogue kept in memory, for the web app, which has no local database: the tags arrive
 * with the sync and live for the session.
 */
class InMemoryDocumentTagRepository : DocumentTagRepository {

    private val tags = MutableStateFlow<Map<String, DocumentTag>>(emptyMap())

    override fun listenForTags(workspaceId: String): Flow<List<DocumentTag>> =
        tags.map { byId -> live(byId.values, workspaceId) }

    override suspend fun getTagsForWorkspace(workspaceId: String): List<DocumentTag> =
        live(tags.value.values, workspaceId)

    override suspend fun getAllTagsForWorkspace(workspaceId: String): List<DocumentTag> =
        tags.value.values.filter { tag -> tag.workspaceId == workspaceId }.sortedBy { it.name }

    override suspend fun getTagById(id: String): DocumentTag? = tags.value[id]

    override suspend fun upsertTag(tag: DocumentTag) {
        tags.value = tags.value + (tag.id to tag)
    }

    override suspend fun softDeleteTag(id: String, workspaceId: String) {
        val tag = tags.value[id]?.takeIf { it.workspaceId == workspaceId } ?: return
        upsertTag(tag.copy(deleted = true, lastUpdatedAt = Clock.System.now()))
    }

    override suspend fun moveTagsToWorkspace(oldWorkspaceId: String, newWorkspaceId: String) {
        tags.value = tags.value.mapValues { (_, tag) ->
            if (tag.workspaceId == oldWorkspaceId) tag.copy(workspaceId = newWorkspaceId) else tag
        }
    }

    private fun live(all: Collection<DocumentTag>, workspaceId: String): List<DocumentTag> =
        all.filter { tag -> tag.workspaceId == workspaceId && !tag.deleted }.sortedBy { it.name }

    companion object {
        private val instance by lazy { InMemoryDocumentTagRepository() }

        fun singleton(): InMemoryDocumentTagRepository = instance
    }
}

package io.writeopia.claudecode

import io.writeopia.core.folders.repository.tag.DocumentTagRepository
import io.writeopia.sdk.models.document.DocumentTag
import kotlin.time.Clock

/** A tag of the workspace catalogue, as Claude Code sees it. */
data class LiveTag(val id: String, val name: String, val description: String = "")

/** The tag catalogue of the workspace, which Claude Code can read and add to. */
interface LiveTags {
    /** The tags of the workspace, deleted ones left out. */
    suspend fun list(): List<LiveTag>

    /** The tag named [name] (any capitalization), made when there is none. True when it was made. */
    suspend fun findOrCreate(name: String): Pair<LiveTag, Boolean>
}

/**
 * The tags of the app's database, like the ones made in the settings: the workspace sync sends
 * them to the backend.
 */
class RepositoryLiveTags(
    private val tagRepository: DocumentTagRepository,
    private val workspaceId: suspend () -> String,
) : LiveTags {

    override suspend fun list(): List<LiveTag> =
        tagRepository.getTagsForWorkspace(workspaceId())
            .filter { tag -> !tag.deleted }
            .map { tag -> LiveTag(tag.id, tag.name, tag.description) }

    override suspend fun findOrCreate(name: String): Pair<LiveTag, Boolean> {
        list().firstOrNull { tag -> tag.name.equals(name, ignoreCase = true) }?.let { return it to false }

        val now = Clock.System.now()
        val tag = DocumentTag(workspaceId = workspaceId(), name = name, createdAt = now, lastUpdatedAt = now)
        tagRepository.upsertTag(tag)
        return LiveTag(tag.id, tag.name) to true
    }
}

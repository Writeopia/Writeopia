package io.writeopia.api.ai.repository

import io.writeopia.sdk.serialization.response.HeaderImageApi
import io.writeopia.sql.WriteopiaDbBackend
import java.util.UUID
import kotlin.time.Clock

/** The header pictures of [workspaceId], the newest first. */
fun WriteopiaDbBackend.getHeaderImages(workspaceId: String): List<HeaderImageApi> =
    workspaceHeaderImageEntityQueries.selectByWorkspace(workspaceId)
        .executeAsList()
        .map { entity -> HeaderImageApi(id = entity.id, url = entity.url, createdAt = entity.created_at) }

/**
 * Keeps [url] with the header pictures of [workspaceId]. Approving the same picture twice, even
 * at the same time from two devices, keeps it once: the unique index on (workspace_id, url)
 * drops the second insert, and the picture as stored is returned.
 */
fun WriteopiaDbBackend.saveHeaderImage(workspaceId: String, url: String, userId: String): HeaderImageApi {
    workspaceHeaderImageEntityQueries.insert(
        id = UUID.randomUUID().toString(),
        workspace_id = workspaceId,
        url = url,
        created_by = userId,
        created_at = Clock.System.now().toEpochMilliseconds()
    )

    val stored = workspaceHeaderImageEntityQueries.selectByWorkspaceAndUrl(workspaceId, url).executeAsOne()
    return HeaderImageApi(id = stored.id, url = stored.url, createdAt = stored.created_at)
}

/**
 * Whether [userId] is a member of [workspaceId], like `isUserInWorkspace` of the workspaces: a
 * workspace being deleted takes no new pictures.
 */
fun WriteopiaDbBackend.isMemberOfWorkspace(userId: String, workspaceId: String): Boolean =
    workspaceEntityQueries.getWorkspacesByUserId(userId)
        .executeAsList()
        .any { entity -> entity.workspace_id == workspaceId && entity.workspace_status != "DELETION_PENDING" }

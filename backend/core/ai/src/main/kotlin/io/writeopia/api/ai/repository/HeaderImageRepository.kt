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
 * Keeps [url] with the header pictures of [workspaceId]. Approving the same picture twice keeps
 * it once.
 */
fun WriteopiaDbBackend.saveHeaderImage(workspaceId: String, url: String, userId: String): HeaderImageApi =
    transactionWithResult {
        val existing = workspaceHeaderImageEntityQueries.selectByWorkspaceAndUrl(workspaceId, url).executeAsOneOrNull()

        if (existing != null) {
            HeaderImageApi(id = existing.id, url = existing.url, createdAt = existing.created_at)
        } else {
            val image = HeaderImageApi(
                id = UUID.randomUUID().toString(),
                url = url,
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
            workspaceHeaderImageEntityQueries.insert(
                id = image.id,
                workspace_id = workspaceId,
                url = image.url,
                created_by = userId,
                created_at = image.createdAt
            )
            image
        }
    }

/**
 * Whether [userId] is a member of [workspaceId], like `isUserInWorkspace` of the workspaces: a
 * workspace being deleted takes no new pictures.
 */
fun WriteopiaDbBackend.isMemberOfWorkspace(userId: String, workspaceId: String): Boolean =
    workspaceEntityQueries.getWorkspacesByUserId(userId)
        .executeAsList()
        .any { entity -> entity.workspace_id == workspaceId && entity.workspace_status != "DELETION_PENDING" }

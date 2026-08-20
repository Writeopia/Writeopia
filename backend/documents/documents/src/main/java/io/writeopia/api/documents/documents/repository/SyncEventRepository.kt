package io.writeopia.api.documents.documents.repository

import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sql.Sync_event
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Event types for sync operations.
 */
object SyncEventType {
    const val DELETE_DOCUMENT = "DELETE_DOCUMENT"
    const val DELETE_FOLDER = "DELETE_FOLDER"
    const val MOVE_DOCUMENT = "MOVE_DOCUMENT"
    const val MOVE_FOLDER = "MOVE_FOLDER"
}

/**
 * Creates a sync event for tracking delete/move operations.
 */
@OptIn(ExperimentalTime::class)
fun WriteopiaDbBackend.createSyncEvent(
    workspaceId: String,
    eventType: String,
    entityId: String,
    userId: String,
    oldParentId: String? = null,
    newParentId: String? = null
) {
    syncEventEntityQueries.insert(
        id = GenerateId.generate(),
        workspace_id = workspaceId,
        event_type = eventType,
        entity_id = entityId,
        old_parent_id = oldParentId,
        new_parent_id = newParentId,
        created_at = Clock.System.now().toEpochMilliseconds(),
        user_id = userId
    )
}

/**
 * Gets all sync events for a workspace after the given timestamp.
 */
fun WriteopiaDbBackend.getSyncEventsAfterTime(
    workspaceId: String,
    afterTime: Long
): List<Sync_event> {
    return syncEventEntityQueries.selectByWorkspaceAfterTime(workspaceId, afterTime).executeAsList()
}

/**
 * Deletes sync events older than the given timestamp.
 * Useful for cleanup of old events.
 */
fun WriteopiaDbBackend.deleteOldSyncEvents(beforeTime: Long) {
    syncEventEntityQueries.deleteOldEvents(beforeTime)
}

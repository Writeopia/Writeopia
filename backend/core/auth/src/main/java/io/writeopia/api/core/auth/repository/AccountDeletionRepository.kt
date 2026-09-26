package io.writeopia.api.core.auth.repository

import io.writeopia.api.core.auth.models.AccountDeletion
import io.writeopia.sql.Account_deletion
import io.writeopia.sql.WriteopiaDbBackend

private fun Account_deletion.toModel(): AccountDeletion =
    AccountDeletion(
        userId = user_id,
        userEmail = user_email,
        userName = user_name,
        status = status,
        requestedAt = requested_at,
        workspacesCompletedAt = workspaces_completed_at,
        mediaCompletedAt = media_completed_at,
        completedAt = completed_at,
    )

/**
 * Idempotent and atomic: `ON CONFLICT (user_id) DO NOTHING` (see AccountDeletion.sq) means this
 * affects a row ONLY the first time it's called for a given user - a racing or repeated call
 * affects 0 rows. Non-suspend so it can be called (and its result branched on) inside a
 * writeopiaDb.transaction {} block - see AccountDeletionService.requestDeletion, which only
 * inserts the paired outbox event when this returns true.
 */
fun WriteopiaDbBackend.insertAccountDeletion(
    userId: String,
    userEmail: String,
    userName: String,
    requestedAt: Long,
): Boolean =
    this.accountDeletionQueries.insert(
        user_id = userId,
        user_email = userEmail,
        user_name = userName,
        status = AccountDeletion.STATUS_REQUESTED,
        requested_at = requestedAt,
        workspaces_completed_at = null,
        media_completed_at = null,
        completed_at = null,
    ).value > 0

// Non-suspend: for use inside a writeopiaDb.transaction {} block. Payload is a JSON string
// (see AccountDeletionEventPayload) - Debezium's outbox-event-router forwards it verbatim
// as the Pub/Sub message body, so no further encoding happens here.
fun WriteopiaDbBackend.insertOutboxEvent(
    id: String,
    aggregateType: String,
    aggregateId: String,
    eventType: String,
    topic: String,
    payload: String,
    createdAt: Long,
) {
    this.outboxEventQueries.insert(
        id = id,
        aggregate_type = aggregateType,
        aggregate_id = aggregateId,
        event_type = eventType,
        topic = topic,
        payload = payload,
        created_at = createdAt,
    )
}

fun WriteopiaDbBackend.getAccountDeletionByUserId(userId: String): AccountDeletion? =
    this.accountDeletionQueries.selectByUserId(userId).executeAsOneOrNull()?.toModel()

/** Idempotent: no-op if this leg was already marked complete. Returns true if this call changed it. */
fun WriteopiaDbBackend.setAccountDeletionWorkspacesCompleted(userId: String, completedAt: Long): Boolean =
    this.accountDeletionQueries.setWorkspacesCompleted(completedAt, userId).value > 0

fun WriteopiaDbBackend.setAccountDeletionMediaCompleted(userId: String, completedAt: Long): Boolean =
    this.accountDeletionQueries.setMediaCompleted(completedAt, userId).value > 0

/**
 * Guarded conditional UPDATE: flips status to COMPLETED only if both legs are done and it
 * hasn't already been flipped. Safe under concurrent callers (the two completion push
 * endpoints racing) - at most one caller's call returns true, any other returns false and
 * must no-op rather than proceed to finalize again.
 */
fun WriteopiaDbBackend.tryFinalizeAccountDeletion(userId: String, completedAt: Long): Boolean =
    this.accountDeletionQueries.tryFinalize(completedAt, userId).value > 0

fun WriteopiaDbBackend.selectStuckAccountDeletions(requestedBefore: Long): List<AccountDeletion> =
    this.accountDeletionQueries.selectStuck(requestedBefore).executeAsList().map { it.toModel() }

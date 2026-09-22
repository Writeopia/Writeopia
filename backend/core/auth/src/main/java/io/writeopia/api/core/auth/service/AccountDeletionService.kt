@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.core.auth.service

import io.writeopia.api.core.auth.dto.AccountDeletionEventPayload
import io.writeopia.api.core.auth.models.ACCOUNT_DELETION_AGGREGATE_TYPE
import io.writeopia.api.core.auth.models.AccountDeletion
import io.writeopia.api.core.auth.models.AccountDeletionEventTypes
import io.writeopia.api.core.auth.models.AccountDeletionTopics
import io.writeopia.api.core.auth.repository.getAccountDeletionByUserId
import io.writeopia.api.core.auth.repository.getUserById
import io.writeopia.api.core.auth.repository.insertAccountDeletion
import io.writeopia.api.core.auth.repository.insertOutboxEvent
import io.writeopia.api.core.auth.repository.setAccountDeletionMediaCompleted
import io.writeopia.api.core.auth.repository.setAccountDeletionWorkspacesCompleted
import io.writeopia.api.core.auth.repository.tryFinalizeAccountDeletion
import io.writeopia.connection.logger
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Orchestrates the auth side of the account-deletion saga: kicking it off, receiving the two
 * completion legs (documents, media), guarded finalization (hard-deleting the user only once
 * both legs are done), and weekly reconciliation. See the plan/PR description for the full
 * event-flow design; in short:
 *   - requestDeletion: user_entity.status -> DELETION_PENDING, plus an outbox_event insert,
 *     atomically (Debezium picks up the outbox row and publishes account-deletion-requested).
 *   - handleWorkspacesCompleted / handleMediaCompleted: record each leg, then try to finalize.
 *   - checkAndFinalize: hard-deletes user_entity and inserts the finalized outbox_event
 *     atomically, guarded so it only ever runs once even if both completion events race.
 *   - reconcileStuckDeletions: the weekly sweep, delegated to AccountDeletionReconciliationService
 *     (a separate, periodic/batch concern from the rest of this per-request saga flow).
 *
 * There is no generated "deletion id" anywhere here - a user has at most one deletion, ever
 * (account_deletion.user_id is the primary key), so userId is the correlation id throughout.
 */
object AccountDeletionService {

    /**
     * Starts the saga for [userId], or returns the already-in-flight/completed deletion if one
     * exists. Atomic and idempotent by construction, not by catching a race: the status flip,
     * the account_deletion insert, and the outbox_event insert all happen in one transaction,
     * and the account_deletion insert is itself an `ON CONFLICT (user_id) DO NOTHING` (see
     * AccountDeletionRepository.insertAccountDeletion) - so a racing duplicate call simply
     * inserts nothing (0 affected rows) and the transaction skips the outbox_event insert too,
     * rather than throwing. No exception-based retry, no window where a race could double-fire
     * the outbox event.
     */
    fun requestDeletion(
        userId: String,
        writeopiaDb: WriteopiaDbBackend,
    ): AccountDeletion? {
        val user = writeopiaDb.getUserById(userId) ?: return null
        val now = Clock.System.now().toEpochMilliseconds()
        val payload = writeopiaJson.encodeToString(
            AccountDeletionEventPayload.serializer(),
            AccountDeletionEventPayload(userId = userId)
        )

        writeopiaDb.transaction {
            writeopiaDb.userEntityQueries.setStatusPendingDeletion(userId)

            val isNewRequest = writeopiaDb.insertAccountDeletion(
                userId = userId,
                userEmail = user.email,
                userName = user.name,
                requestedAt = now,
            )

            if (isNewRequest) {
                writeopiaDb.insertOutboxEvent(
                    id = GenerateId.generate(),
                    aggregateType = ACCOUNT_DELETION_AGGREGATE_TYPE,
                    aggregateId = userId,
                    eventType = AccountDeletionEventTypes.REQUESTED,
                    topic = AccountDeletionTopics.REQUESTED,
                    payload = payload,
                    createdAt = now,
                )
            }
        }

        return writeopiaDb.getAccountDeletionByUserId(userId)
    }

    fun handleWorkspacesCompleted(
        userId: String,
        writeopiaDb: WriteopiaDbBackend
    ) {
        writeopiaDb.setAccountDeletionWorkspacesCompleted(
            userId,
            Clock.System.now().toEpochMilliseconds()
        )
        checkAndFinalize(userId, writeopiaDb)
    }

    fun handleMediaCompleted(
        userId: String,
        writeopiaDb: WriteopiaDbBackend,
    ) {
        writeopiaDb.setAccountDeletionMediaCompleted(
            userId,
            Clock.System.now().toEpochMilliseconds()
        )
        checkAndFinalize(userId, writeopiaDb)
    }

    /**
     * Guarded finalize: only actually deletes the user + inserts the finalized outbox event if
     * this call is the one that flips status to COMPLETED (tryFinalizeAccountDeletion's guarded
     * UPDATE). Safe to call redundantly - a losing caller (both legs already finalized by
     * someone else, or not both legs done yet) just returns.
     */
    fun checkAndFinalize(
        userId: String,
        writeopiaDb: WriteopiaDbBackend,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val won = writeopiaDb.tryFinalizeAccountDeletion(userId, now)
        if (!won) return

        val deletion = writeopiaDb.getAccountDeletionByUserId(userId) ?: run {
            logger.error("[AccountDeletion] tryFinalize won for missing deletion, user $userId")
            return
        }

        val payload = writeopiaJson.encodeToString(
            AccountDeletionEventPayload.serializer(),
            AccountDeletionEventPayload(userId = deletion.userId)
        )

        writeopiaDb.transaction {
            // Cascades refresh_token_entity via its existing FK. Everything else (workspaces,
            // documents, story steps, favorites, media) was already torn down by the
            // documents/media legs before either completion event could have fired.
            writeopiaDb.userEntityQueries.deleteUser(deletion.userId)
            writeopiaDb.insertOutboxEvent(
                id = GenerateId.generate(),
                aggregateType = ACCOUNT_DELETION_AGGREGATE_TYPE,
                aggregateId = deletion.userId,
                eventType = AccountDeletionEventTypes.FINALIZED,
                topic = AccountDeletionTopics.FINALIZED,
                payload = payload,
                createdAt = now,
            )
        }
    }

    /**
     * Weekly sweep for account-deletion sagas stuck past a grace period. Delegates to
     * AccountDeletionReconciliationService, which lives outside this object since it's a
     * periodic, batch concern (triggered by Cloud Scheduler) rather than part of the
     * per-request saga flow the rest of this object handles - kept reachable from here too so
     * callers (e.g. the routing layer) don't need to know about that split.
     */
    fun reconcileStuckDeletions(writeopiaDb: WriteopiaDbBackend): Int =
        AccountDeletionReconciliationService.reconcileStuckDeletions(writeopiaDb)
}

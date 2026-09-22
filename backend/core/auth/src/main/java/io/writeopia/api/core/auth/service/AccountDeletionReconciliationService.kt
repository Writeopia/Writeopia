@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.core.auth.service

import io.writeopia.api.core.auth.dto.AccountDeletionEventPayload
import io.writeopia.api.core.auth.models.ACCOUNT_DELETION_AGGREGATE_TYPE
import io.writeopia.api.core.auth.models.AccountDeletionEventTypes
import io.writeopia.api.core.auth.models.AccountDeletionTopics
import io.writeopia.api.core.auth.repository.insertOutboxEvent
import io.writeopia.api.core.auth.repository.selectStuckAccountDeletions
import io.writeopia.connection.logger
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime

/**
 * Weekly sweep for account-deletion sagas stuck past a grace period - a separate, periodic,
 * batch concern (triggered by Cloud Scheduler) from the per-request saga flow
 * AccountDeletionService handles, so it lives in its own object rather than mixed into that
 * one. AccountDeletionService.reconcileStuckDeletions delegates here.
 */
object AccountDeletionReconciliationService {
    private val GRACE_PERIOD = 24.hours

    /**
     * For deletions stuck past the grace period, either nudge a stuck leg (re-insert a fresh
     * AccountDeletionRequested outbox event - documents/media's processing is idempotent, so
     * redelivery is harmless) or finalize if both legs actually completed but the finalize
     * step never ran (e.g. a crash between the two). Returns the number of deletions it acted
     * on, for logging.
     */
    fun reconcileStuckDeletions(writeopiaDb: WriteopiaDbBackend): Int {
        val cutoff = Clock.System.now().toEpochMilliseconds() - GRACE_PERIOD.inWholeMilliseconds
        val stuck = writeopiaDb.selectStuckAccountDeletions(cutoff)

        stuck.forEach { deletion ->
            if (deletion.workspacesCompletedAt != null && deletion.mediaCompletedAt != null) {
                AccountDeletionService.checkAndFinalize(deletion.userId, writeopiaDb)
            } else {
                val payload = writeopiaJson.encodeToString(
                    AccountDeletionEventPayload.serializer(),
                    AccountDeletionEventPayload(userId = deletion.userId)
                )
                writeopiaDb.insertOutboxEvent(
                    id = GenerateId.generate(),
                    aggregateType = ACCOUNT_DELETION_AGGREGATE_TYPE,
                    aggregateId = deletion.userId,
                    eventType = AccountDeletionEventTypes.REQUESTED,
                    topic = AccountDeletionTopics.REQUESTED,
                    payload = payload,
                    createdAt = Clock.System.now().toEpochMilliseconds(),
                )
                logger.warn("[AccountDeletion] reconciliation nudged stuck deletion for user ${deletion.userId}")
            }
        }

        return stuck.size
    }
}

@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents.service

import io.writeopia.api.core.auth.dto.AccountDeletionEventPayload
import io.writeopia.api.core.auth.models.AccountDeletionTopics
import io.writeopia.api.core.workspaces.repository.countAdminsInWorkspace
import io.writeopia.api.core.workspaces.repository.getUserRoleInWorkspace
import io.writeopia.api.core.workspaces.repository.setWorkspaceStatusDeletionPending
import io.writeopia.api.core.workspaces.service.WorkspaceService
import io.writeopia.api.documents.documents.repository.AccountDeletionWorkspaceAction
import io.writeopia.api.documents.documents.repository.AccountDeletionWorkspaceRow
import io.writeopia.api.documents.documents.repository.getAccountDeletionWorkspaceRows
import io.writeopia.api.documents.documents.repository.getIncompleteAccountDeletionWorkspaceRows
import io.writeopia.api.documents.documents.repository.hardDeleteWorkspaceContents
import io.writeopia.api.documents.documents.repository.insertAccountDeletionWorkspace
import io.writeopia.api.documents.documents.repository.markAccountDeletionWorkspaceCompleted
import io.writeopia.api.documents.documents.repository.removeUserFromWorkspaceContents
import io.writeopia.api.documents.documents.repository.sweepRemainingUserAccountData
import io.writeopia.connection.logger
import io.writeopia.pubsub.PubsubPublisher
import io.writeopia.sdk.models.workspace.Role
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Documents-side handler for the account-deletion saga's account-deletion-requested event.
 * See the plan/PR for the full design; in short: enumerate the user's workspaces once
 * (idempotently - a redelivered event resumes rather than re-enumerates), tear each one down
 * (or just remove the user's membership, per the sole-admin rule), then publish the
 * completion event. The caller (AccountDeletionEventsRouting) only acks the inbound Pub/Sub
 * message after this function returns successfully - any exception here must propagate so the
 * message stays unacked and Pub/Sub redelivers.
 *
 * [handleAccountDeletionRequested] takes its publish function as a parameter (defaulting to
 * PubsubPublisher::publish) rather than calling that object directly, so tests can substitute
 * a fake without touching real Pub/Sub.
 */
object AccountDeletionWorkspaceService {

    suspend fun handleAccountDeletionRequested(
        userId: String,
        writeopiaDb: WriteopiaDbBackend,
        debugMode: Boolean = false,
        publish: suspend (topicId: String, orderingKey: String, payload: String, debugMode: Boolean) -> Unit =
            PubsubPublisher::publish,
    ) {
        logger.info("[AccountDeletion] documents workspace teardown started for user $userId")

        if (writeopiaDb.getAccountDeletionWorkspaceRows(userId).isEmpty()) {
            enumerateWorkspaces(userId, writeopiaDb)
        }

        writeopiaDb.getIncompleteAccountDeletionWorkspaceRows(userId).forEach { row ->
            processRow(row, writeopiaDb)
        }

        if (writeopiaDb.getIncompleteAccountDeletionWorkspaceRows(userId).isNotEmpty()) {
            // Shouldn't happen (processRow either completes a row or throws), but guard
            // against publishing a false completion signal if it somehow does.
            logger.error("[AccountDeletion] rows still incomplete for user $userId after processing")
            error("account_deletion_workspace rows still incomplete for user $userId")
        }

        writeopiaDb.sweepRemainingUserAccountData(userId)

        val payload = writeopiaJson.encodeToString(
            AccountDeletionEventPayload.serializer(),
            AccountDeletionEventPayload(userId = userId)
        )

        publish(AccountDeletionTopics.WORKSPACES_COMPLETED, userId, payload, debugMode)

        logger.info(
            "[AccountDeletion] outbox event ${AccountDeletionTopics.WORKSPACES_COMPLETED} " +
                "published for user $userId"
        )
    }

    private fun enumerateWorkspaces(userId: String, writeopiaDb: WriteopiaDbBackend) {
        val workspaces = WorkspaceService.getWorkspacesByUserId(userId, writeopiaDb)

        workspaces.forEach { workspace ->
            val role = writeopiaDb.getUserRoleInWorkspace(workspace.id, userId)
            val isSoleAdmin = role.equals(Role.ADMIN.value, ignoreCase = true) &&
                writeopiaDb.countAdminsInWorkspace(workspace.id) <= 1

            val action = if (isSoleAdmin) {
                AccountDeletionWorkspaceAction.DELETE_WORKSPACE
            } else {
                AccountDeletionWorkspaceAction.REMOVE_MEMBERSHIP
            }

            writeopiaDb.insertAccountDeletionWorkspace(userId, workspace.id, action)
        }
    }

    private suspend fun processRow(
        row: AccountDeletionWorkspaceRow,
        writeopiaDb: WriteopiaDbBackend
    ) {
        when (row.action) {
            AccountDeletionWorkspaceAction.DELETE_WORKSPACE -> {
                // Step 1: standalone, commits immediately - unblocks the write-guard
                // (runIfMember/runIfAdmin) for the whole duration of step 2, however long it
                // takes, instead of only becoming visible the instant the workspace vanishes.
                writeopiaDb.setWorkspaceStatusDeletionPending(row.workspaceId)

                // Step 2: one atomic transaction. A crash here leaves everything as it was
                // (nothing partially deleted) and completed=false, so a retry just re-runs
                // this whole block from scratch - hardDeleteWorkspaceContents deleting
                // already-gone rows is a no-op, not an error.
                writeopiaDb.transaction {
                    writeopiaDb.hardDeleteWorkspaceContents(row.workspaceId)
                    writeopiaDb.markAccountDeletionWorkspaceCompleted(
                        row.userId,
                        row.workspaceId,
                        Clock.System.now().toEpochMilliseconds(),
                    )
                }
            }

            AccountDeletionWorkspaceAction.REMOVE_MEMBERSHIP -> {
                writeopiaDb.transaction {
                    writeopiaDb.removeUserFromWorkspaceContents(row.userId, row.workspaceId)
                    writeopiaDb.markAccountDeletionWorkspaceCompleted(
                        row.userId,
                        row.workspaceId,
                        Clock.System.now().toEpochMilliseconds(),
                    )
                }
            }

            else -> logger.error("[AccountDeletion] unknown action '${row.action}' for workspace ${row.workspaceId}")
        }
    }
}

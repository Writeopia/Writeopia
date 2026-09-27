package io.writeopia.api.core.auth.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.post
import io.writeopia.api.core.auth.dto.AccountDeletionEventPayload
import io.writeopia.api.core.auth.repository.getAccountDeletionByUserId
import io.writeopia.api.core.auth.service.AccountDeletionService
import io.writeopia.api.core.auth.service.EmailService
import io.writeopia.connection.logger
import io.writeopia.pubsub.decodePubsubPushData
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sql.WriteopiaDbBackend

/**
 * Internal Pub/Sub-push and Cloud-Scheduler-triggered endpoints for the account-deletion
 * saga's auth side. Mounted only in micro/auth (not gateway - see routing/AuthRouting.kt's
 * `authRoute` doc and the plan/PR: gateway wires routes in-process and isn't what's fronted
 * by Pub/Sub push or Cloud Scheduler in production).
 *
 * No auth check in this file: every route here is only reachable because Cloud Run IAM
 * (roles/run.invoker, restricted to the dedicated pubsub-invoker service account - see
 * pubsub.tf in the infra repo) already rejects any request without a valid Google-signed
 * token from that identity before it reaches this code, including the audience check (the
 * token's aud must match this service's URL) - for both Pub/Sub push and the Cloud Scheduler
 * job, which authenticate the same way. Re-verifying in application code would just duplicate
 * what the infrastructure already guarantees.
 */
fun Routing.accountDeletionEventsRoute(writeopiaDb: WriteopiaDbBackend) {
    post("/api/auth/internal/events/workspaces-deleted") {
        val payload = receivePayload()
        AccountDeletionService.handleWorkspacesCompleted(payload.userId, writeopiaDb)
        call.respond(HttpStatusCode.OK)
    }

    post("/api/auth/internal/events/media-deleted") {
        val payload = receivePayload()
        AccountDeletionService.handleMediaCompleted(payload.userId, writeopiaDb)
        call.respond(HttpStatusCode.OK)
    }

    post("/api/auth/internal/events/account-deletion-finalized") {
        val payload = receivePayload()
        val deletion = writeopiaDb.getAccountDeletionByUserId(payload.userId)

        if (deletion == null) {
            logger.error("[AccountDeletion] finalized event for unknown user ${payload.userId}")
            // Ack anyway - retrying can't fix a missing row, and NACKing would just loop forever.
            call.respond(HttpStatusCode.OK)
            return@post
        }

        val sent =
            EmailService.sendAccountDeletionCompleteEmail(deletion.userEmail, deletion.userName)
        if (sent) {
            call.respond(HttpStatusCode.OK)
        } else {
            // Non-200 leaves the message unacked so Pub/Sub retries the email send.
            call.respond(HttpStatusCode.InternalServerError)
        }
    }

    post("/api/auth/internal/reconciliation/account-deletion") {
        val count = AccountDeletionService.reconcileStuckDeletions(writeopiaDb)
        logger.info("[AccountDeletion] reconciliation acted on $count stuck deletion(s)")
        call.respond(HttpStatusCode.OK)
    }
}

private suspend fun RoutingContext.receivePayload(): AccountDeletionEventPayload {
    val rawPayload = decodePubsubPushData(call.receiveText())
    return writeopiaJson.decodeFromString(AccountDeletionEventPayload.serializer(), rawPayload)
}

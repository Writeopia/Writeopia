package io.writeopia.api.documents.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.writeopia.api.core.auth.dto.AccountDeletionEventPayload
import io.writeopia.api.documents.documents.service.AccountDeletionWorkspaceService
import io.writeopia.connection.logger
import io.writeopia.pubsub.decodePubsubPushData
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sql.WriteopiaDbBackend

/**
 * Internal Pub/Sub-push endpoint for the documents side of the account-deletion saga.
 * Subscribes (alongside media's own equivalent endpoint - Pub/Sub fan-out, two independent
 * subscriptions on the same topic) to account-deletion-requested. See
 * AccountDeletionWorkspaceService for the actual teardown logic.
 *
 * Mounted only in micro/documents, matching accountDeletionEventsRoute's placement in
 * micro/auth - not the gateway.
 *
 * No auth check here: this endpoint is only reachable at all because Cloud Run IAM
 * (roles/run.invoker, restricted to the dedicated pubsub-invoker service account - see
 * pubsub.tf in the infra repo) already rejects any request without a valid Google-signed
 * token from that identity before it reaches this code, including the audience check
 * (the token's aud must match this service's URL). A second verification here would just
 * duplicate what the infrastructure already guarantees.
 */
fun Routing.accountDeletionEventsRoute(writeopiaDb: WriteopiaDbBackend, debugMode: Boolean = false) {
    post("/api/docs/internal/events/account-deletion-requested") {
        val rawPayload = decodePubsubPushData(call.receiveText())
        val payload = writeopiaJson.decodeFromString(AccountDeletionEventPayload.serializer(), rawPayload)

        logger.info(
            "[AccountDeletion] documents service received account-deletion-requested " +
                "for user ${payload.userId}"
        )

        // Only acks (200) once teardown AND the completion publish both succeed - any
        // exception here leaves the message unacked so Pub/Sub redelivers it, and reprocessing
        // is safe (see AccountDeletionWorkspaceService's doc).
        AccountDeletionWorkspaceService.handleAccountDeletionRequested(
            userId = payload.userId,
            writeopiaDb = writeopiaDb,
            debugMode = debugMode,
        )

        logger.info(
            "[AccountDeletion] documents service finished handling account-deletion-requested " +
                "for user ${payload.userId}"
        )
        call.respond(HttpStatusCode.OK)
    }
}

package io.writeopia.api.media.routing

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import io.writeopia.api.core.auth.dto.AccountDeletionEventPayload
import io.writeopia.api.media.service.AccountDeletionMediaService
import io.writeopia.pubsub.decodePubsubPushData
import io.writeopia.sdk.serialization.json.writeopiaJson

/**
 * Internal Pub/Sub-push endpoint for the media side of the account-deletion saga.
 * Subscribes (alongside documents' own equivalent endpoint - Pub/Sub fan-out, two
 * independent subscriptions on the same topic) to account-deletion-requested. See
 * AccountDeletionMediaService for the actual GCS cleanup.
 *
 * No auth check here: this endpoint is only reachable at all because Cloud Run IAM
 * (roles/run.invoker, restricted to the dedicated pubsub-invoker service account - see
 * pubsub.tf in the infra repo) already rejects any request without a valid Google-signed
 * token from that identity before it reaches this code, including the audience check
 * (the token's aud must match this service's URL). A second verification here would just
 * duplicate what the infrastructure already guarantees.
 */
fun Routing.accountDeletionEventsRoute(debugMode: Boolean = false) {
    post("/api/media/internal/events/account-deletion-requested") {
        val rawPayload = decodePubsubPushData(call.receiveText())
        val payload =
            writeopiaJson.decodeFromString(AccountDeletionEventPayload.serializer(), rawPayload)

        AccountDeletionMediaService.handleAccountDeletionRequested(
            userId = payload.userId,
            debugMode = debugMode,
        )
        call.respond(HttpStatusCode.OK)
    }
}

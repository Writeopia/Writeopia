package io.writeopia.pubsub

import kotlinx.serialization.Serializable

/**
 * Wire payload for AccountDeletionRequested / completion / finalized events (Pub/Sub message
 * body, JSON) - shared across the auth, documents, and media modules. Not a domain model,
 * so it lives with the Pub/Sub wire types rather than in a service model.
 */
@Serializable
data class AccountDeletionEventPayload(
    val userId: String,
)

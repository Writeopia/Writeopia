package io.writeopia.api.core.auth.dto

import kotlinx.serialization.Serializable

/**
 * Wire payload for AccountDeletionRequested / completion / finalized events (Pub/Sub message
 * body, JSON) - shared across the auth, documents, and media modules. Not a domain model, so
 * it lives in dto rather than models.
 */
@Serializable
data class AccountDeletionEventPayload(
    val userId: String,
)

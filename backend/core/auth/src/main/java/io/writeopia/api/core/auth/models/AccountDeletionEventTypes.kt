package io.writeopia.api.core.auth.models

/** Outbox event type names for the account-deletion saga's Debezium-guarded transitions. */
object AccountDeletionEventTypes {
    const val REQUESTED = "AccountDeletionRequested"
    const val FINALIZED = "AccountDeletionFinalized"
}

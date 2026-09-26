package io.writeopia.api.core.auth.models

/** Pub/Sub topic ids for the account-deletion saga. Must match pubsub.tf in the infra repo. */
object AccountDeletionTopics {
    const val REQUESTED = "account-deletion-requested"
    const val WORKSPACES_COMPLETED = "account-deletion-workspaces-completed"
    const val MEDIA_COMPLETED = "account-deletion-media-completed"
    const val FINALIZED = "account-deletion-finalized"
}

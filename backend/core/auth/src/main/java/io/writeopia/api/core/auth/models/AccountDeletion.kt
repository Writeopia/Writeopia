package io.writeopia.api.core.auth.models

/** Keyed by userId, not a separate generated id - there is at most one deletion ever per user. */
data class AccountDeletion(
    val userId: String,
    val userEmail: String,
    val userName: String,
    val status: String,
    val requestedAt: Long,
    val workspacesCompletedAt: Long?,
    val mediaCompletedAt: Long?,
    val completedAt: Long?,
) {
    companion object {
        const val STATUS_REQUESTED = "REQUESTED"
        const val STATUS_COMPLETED = "COMPLETED"
    }
}

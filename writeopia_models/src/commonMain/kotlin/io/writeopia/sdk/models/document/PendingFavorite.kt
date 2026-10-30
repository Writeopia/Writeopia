package io.writeopia.sdk.models.document

/**
 * The favorite of a document changed on this device that the backend has not been told about
 * yet.
 *
 * The favorite is per user on the backend and travels apart from the document, so a change
 * made here is sent on its own, not with the document. While this record is here, no sync
 * takes the backend's favorite for the document, and the change is sent again until the
 * backend has it. One record per document: a new change replaces the older one.
 *
 * @param changedAt Epoch milliseconds of the change. A send that began before a newer change
 * doesn't drop the newer record when it ends.
 */
data class PendingFavorite(
    val documentId: String,
    val workspaceId: String,
    val favorite: Boolean,
    val changedAt: Long
)

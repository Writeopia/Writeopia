@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import kotlin.time.ExperimentalTime

internal const val SYNC_LOG_TAG = "[FolderSync]"

internal fun syncLog(message: String) {
    println("$SYNC_LOG_TAG $message")
}

internal fun Document.syncDescription(): String =
    "Document(id=$id, title='$title', parentId=$parentId, workspaceId=$workspaceId, " +
        "lastUpdatedAt=$lastUpdatedAt, lastSyncedAt=$lastSyncedAt, deleted=$deleted, " +
        "favorite=$favorite, contentSize=${content.size})"

internal fun Folder.syncDescription(): String =
    "Folder(id=$id, title='$title', parentId=$parentId, workspaceId=$workspaceId, " +
        "lastUpdatedAt=$lastUpdatedAt, lastSyncedAt=$lastSyncedAt, deleted=$deleted, " +
        "itemCount=$itemCount)"

internal fun logDocuments(label: String, documents: List<Document>) {
    syncLog("$label: ${documents.size}")
    documents.forEach { syncLog("    ${it.syncDescription()}") }
}

internal fun logFolders(label: String, folders: List<Folder>) {
    syncLog("$label: ${folders.size}")
    folders.forEach { syncLog("    ${it.syncDescription()}") }
}

package io.writeopia.core.folders.sync

/**
 * What a screen brings in sync with the backend when it opens: the folder it shows, through
 * [FolderSync], or the whole workspace, through [WorkspaceSync]. Sending is the same either
 * way; only what is received differs.
 */
enum class SyncScope {
    FOLDER,
    WORKSPACE
}

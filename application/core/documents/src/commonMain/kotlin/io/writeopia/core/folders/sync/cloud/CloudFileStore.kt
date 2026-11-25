package io.writeopia.core.folders.sync.cloud

/**
 * A flat folder of files in the user's own cloud storage (Google Drive's hidden app data folder),
 * where the offline workspace is kept in sync. Only this app sees it.
 *
 * Nothing here is conditional: the store can't refuse a write made over a newer one, so
 * [CloudWorkspaceSync] never has two devices write the same file.
 */
interface CloudFileStore {

    /** Every file of the folder, with its version. */
    suspend fun list(): List<CloudFile>

    suspend fun download(fileId: String): String

    suspend fun create(name: String, content: String): CloudFile

    suspend fun update(fileId: String, content: String): CloudFile

    suspend fun delete(fileId: String)
}

/**
 * @param version changes whenever the content does, so a file seen before is downloaded again
 * only when it changed.
 */
data class CloudFile(val id: String, val name: String, val version: String)

/** The cloud storage could not be reached, or refused the request. */
class CloudStoreException(message: String, val unauthorized: Boolean = false) : Exception(message)

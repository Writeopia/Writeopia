package io.writeopia.core.folders.sync.cloud

import io.writeopia.sdk.serialization.data.FolderApi
import kotlinx.serialization.Serializable

/**
 * The files of the cloud folder. Each device writes only its own, named with its id, so two
 * devices never write the same file and nothing written is ever lost:
 *
 * - `doc.<device>.<documentId>.json`: a document as that device has it, with all its steps.
 * - `folders.<device>.json`: every folder of that device.
 * - `deleted.<device>.json`: what was deleted on that device, kept for good so that no copy left
 *   in another device's files ever brings it back.
 *
 * The device id never holds a dot; the document id is everything after it.
 */
sealed interface CloudSyncFile {
    val deviceId: String

    data class DocumentFile(override val deviceId: String, val documentId: String) : CloudSyncFile

    data class FoldersFile(override val deviceId: String) : CloudSyncFile

    data class DeletionsFile(override val deviceId: String) : CloudSyncFile

    companion object {
        private const val DOCUMENT = "doc."
        private const val FOLDERS = "folders."
        private const val DELETED = "deleted."
        private const val EXTENSION = ".json"

        fun documentName(deviceId: String, documentId: String) = "$DOCUMENT$deviceId.$documentId$EXTENSION"

        fun foldersName(deviceId: String) = "$FOLDERS$deviceId$EXTENSION"

        fun deletionsName(deviceId: String) = "$DELETED$deviceId$EXTENSION"

        /** What a file of the folder holds; null for a name this app doesn't write. */
        fun parse(name: String): CloudSyncFile? {
            if (!name.endsWith(EXTENSION)) return null
            val base = name.removeSuffix(EXTENSION)

            return when {
                base.startsWith(DOCUMENT) -> {
                    val rest = base.removePrefix(DOCUMENT)
                    val deviceId = rest.substringBefore('.', missingDelimiterValue = "")
                    val documentId = rest.substringAfter('.', missingDelimiterValue = "")
                    if (deviceId.isEmpty() || documentId.isEmpty()) null else DocumentFile(deviceId, documentId)
                }

                base.startsWith(FOLDERS) -> base.removePrefix(FOLDERS).takeIf(::isDeviceId)?.let(::FoldersFile)

                base.startsWith(DELETED) -> base.removePrefix(DELETED).takeIf(::isDeviceId)?.let(::DeletionsFile)

                else -> null
            }
        }

        private fun isDeviceId(value: String) = value.isNotEmpty() && '.' !in value
    }
}

/** What a device deleted: ids to the time they were deleted, in milliseconds. */
@Serializable
data class CloudDeletions(
    val steps: Map<String, Long> = emptyMap(),
    val documents: Map<String, Long> = emptyMap(),
    val folders: Map<String, Long> = emptyMap(),
) {
    operator fun plus(other: CloudDeletions) = CloudDeletions(
        steps = steps + other.steps,
        documents = documents + other.documents,
        folders = folders + other.folders,
    )

    fun isEmpty() = steps.isEmpty() && documents.isEmpty() && folders.isEmpty()
}

@Serializable
data class CloudFolders(val folders: List<FolderApi> = emptyList())

package io.writeopia.sdk.import.json

import io.writeopia.sdk.import.files.KmpFileReader
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.data.FolderApi
import io.writeopia.sdk.serialization.extensions.toModel
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sdk.serialization.storage.WorkspaceStorageConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import kotlinx.serialization.json.Json

class WriteopiaJsonParser(
    private val kmpFileReader: KmpFileReader = KmpFileReader,
    private val json: Json = writeopiaJson
) {

    fun readDocuments(files: List<String>): Flow<Document> =
        kmpFileReader.readObjects<DocumentApi>(files, json)
            .map { documentApi -> documentApi.toModel() }

    fun readAllFolders(path: String): Flow<Folder> =
        kmpFileReader.readDirectory<FolderApi>(path, json) { fileName ->
            fileName.contains(".wrfolder")
        }.map { folderApi -> folderApi.toModel() }

    fun readAllDocuments(path: String): Flow<Document> =
        kmpFileReader.readDirectory<DocumentApi>(path, json) { fileName ->
            !fileName.contains(".wrfolder")
        }.map { documentApi -> documentApi.toModel() }

    fun lastUpdatesById(path: String): Instant? =
        kmpFileReader.readObject<WorkspaceStorageConfig>(
            "$path/writeopia_config_file.json",
            json
        )?.lastUpdateTable
            ?.let(Instant::fromEpochMilliseconds)
}

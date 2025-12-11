package io.writeopia.sdk.import.files

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File

actual object KmpFileReader {

    @OptIn(ExperimentalSerializationApi::class)
    actual inline fun <reified T> readObject(filePath: String, json: Json): T? =
        File(filePath).takeIf { file -> file.exists() }?.inputStream()?.use((json::decodeFromStream))

    actual inline fun <reified T> readObjects(filePaths: List<String>, json: Json): Flow<T> =
        filePaths.asFlow()
            .map(::File)
            .readAllFiles(json)

    actual inline fun <reified T> readDirectory(
        directoryPath: String,
        json: Json,
        crossinline predicate: (String) -> Boolean
    ): Flow<T> = File(directoryPath).walk()
        .filter { file -> file.name != "writeopia_config_file.json" && predicate(file.name) }
        .asFlow()
        .readAllFiles(json)

    @OptIn(ExperimentalSerializationApi::class)
    inline fun <reified T> Flow<File>.readAllFiles(json: Json): Flow<T> =
        filter { file -> file.extension == "json" }
            .map { file -> file.inputStream().use(json::decodeFromStream) }
}

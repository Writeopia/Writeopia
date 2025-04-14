package io.writeopia.sdk.import.files

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

actual object KmpFileReader {

    actual inline fun <reified T> readObjects(filePaths: List<String>, json: Json): Flow<T> =
        flow { }

    actual inline fun <reified T> readObject(
        filePath: String,
        json: Json
    ): T? {
        TODO("Not yet implemented")
    }

    actual inline fun <reified T> readDirectory(
        directoryPath: String,
        json: Json,
        crossinline predicate: (String) -> Boolean
    ): Flow<T> = flow {  }
}

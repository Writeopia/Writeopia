package io.writeopia.sdk.export

import io.writeopia.sdk.export.files.KmpFileWriter
import io.writeopia.sdk.export.files.name
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.json.writeopiaJsonPretty
import io.writeopia.sdk.serialization.storage.WorkspaceStorageConfig
import io.writeopia.sdk.utils.files.useKmp
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class DocumentToJson(private val json: Json = writeopiaJsonPretty) : DocumentWriter {

    override fun writeDocuments(
        documents: List<Document>,
        path: String,
        writeConfigFile: Boolean,
        usePath: Boolean
    ) {
        write(documents, path, writeConfigFile, usePath = usePath)
    }

    override fun writeDocument(document: Document, path: String, writeConfigFile: Boolean) {
        write(listOf(document), path, writeConfigFile, usePath = true)
    }

    private fun write(
        documents: List<Document>,
        path: String,
        writeConfigFile: Boolean,
        usePath: Boolean
    ) {
        if (documents.isEmpty()) return

        documents.forEach { document ->
            KmpFileWriter(
                if (usePath) {
                    name(document, path, ".json")
                } else {
                    path.takeIf { it.contains(".json") } ?: "$path.json"
                }
            ).useKmp { writer ->
                writer.writeObject(document.toApi(), json)
            }
        }

        if (writeConfigFile) {
            KmpFileWriter("$path/${DocumentWriter.CONFIG_FILE_NAME}.json")
                .useKmp { writer ->
                    writer.writeObject(
                        WorkspaceStorageConfig(
                            lastUpdateTable = Clock.System.now().toEpochMilliseconds()
                        ),
                        json
                    )
                }
        }
    }
}

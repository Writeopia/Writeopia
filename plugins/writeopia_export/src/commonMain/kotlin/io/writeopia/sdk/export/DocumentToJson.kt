@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.export

import io.writeopia.sdk.export.files.KmpFileWriter
import io.writeopia.sdk.export.files.name
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.serialization.extensions.toApi
import io.writeopia.sdk.serialization.json.writeopiaJsonPretty
import io.writeopia.sdk.serialization.storage.WorkspaceStorageConfig
import io.writeopia.sdk.utils.files.useKmp
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime

class DocumentToJson(private val json: Json = writeopiaJsonPretty) : DocumentWriter {

    override fun writeDocuments(
        documents: List<MenuItem>,
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
        menuItems: List<MenuItem>,
        path: String,
        writeConfigFile: Boolean,
        usePath: Boolean
    ) {
        if (menuItems.isEmpty()) return

        menuItems.forEach { menuItem ->
            KmpFileWriter(
                if (usePath) {
                    name(menuItem, path, ".json")
                } else {
                    path.takeIf { it.contains(".json") } ?: "$path.json"
                }
            ).useKmp { writer ->
                when (menuItem) {
                    is Folder -> {
                        writer.writeObject(menuItem.toApi(), json)
                    }

                    is Document -> {
                        writer.writeObject(menuItem.toApi(), json)
                    }
                }
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

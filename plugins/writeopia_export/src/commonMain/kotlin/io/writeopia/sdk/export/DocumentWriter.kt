package io.writeopia.sdk.export

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.MenuItem

interface DocumentWriter {
    fun writeDocuments(
        documents: List<MenuItem>,
        path: String,
        writeConfigFile: Boolean = true,
        usePath: Boolean
    )

    fun writeDocument(document: Document, path: String, writeConfigFile: Boolean = true)

    companion object {
        const val CONFIG_FILE_NAME = "writeopia_config_file"
    }
}

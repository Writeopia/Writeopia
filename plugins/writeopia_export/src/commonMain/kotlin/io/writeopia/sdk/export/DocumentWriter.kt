package io.writeopia.sdk.export

import io.writeopia.sdk.models.document.Document

interface DocumentWriter {
    fun writeDocuments(documents: List<Document>, path: String, writeConfigFile: Boolean = true)

    fun writeDocument(document: Document, path: String, writeConfigFile: Boolean = true)

    companion object {
        const val CONFIG_FILE_NAME = "writeopia_config_file"
    }
}

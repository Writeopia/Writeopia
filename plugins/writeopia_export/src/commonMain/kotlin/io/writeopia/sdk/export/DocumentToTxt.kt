package io.writeopia.sdk.export

import io.writeopia.sdk.export.files.KmpFileWriter
import io.writeopia.sdk.export.files.name
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.utils.files.useKmp

object DocumentToTxt : DocumentWriter {

    override fun writeDocuments(
        documents: List<MenuItem>,
        path: String,
        writeConfigFile: Boolean,
        usePath: Boolean
    ) {
        documents
            .filterIsInstance<Document>()
            .forEach { document ->
                KmpFileWriter(
                    if (usePath) {
                        name(document, path, ".txt")
                    } else {
                        path.takeIf { it.contains(".txt") } ?: "$path.txt"
                    }
                ).useKmp { writer ->
                    writeToWriter(
                        content = document.content,
                        kmpFileWriter = writer
                    )
                }
            }
    }

    override fun writeDocument(document: Document, path: String, writeConfigFile: Boolean) {}

    private fun writeToWriter(
        content: Map<Int, StoryStep>,
        kmpFileWriter: KmpFileWriter,
    ) {
        content.asSequence()
            .mapNotNull { (_, story) -> parseStep(story) }
            .forEach(kmpFileWriter::writeLine)
    }

    private fun parseStep(storyStep: StoryStep): String? =
        when (storyStep.type.number) {
            StoryTypes.CHECK_ITEM.type.number -> storyStep.text?.let { "[] $it" }

            StoryTypes.UNORDERED_LIST_ITEM.type.number -> storyStep.text?.let { "- $it" }

            else -> storyStep.text
        }
}

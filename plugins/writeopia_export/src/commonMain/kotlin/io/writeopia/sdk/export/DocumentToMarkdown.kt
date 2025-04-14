package io.writeopia.sdk.export

import io.writeopia.sdk.export.files.KmpFileWriter
import io.writeopia.sdk.export.files.name
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.utils.files.useKmp

/**
 * This class parses a document as a Map<Int, [StoryStep]> to a String following the Markdown
 * syntax.
 */
object DocumentToMarkdown : DocumentWriter {

    override fun writeDocuments(
        documents: List<MenuItem>,
        path: String,
        addHashTable: Boolean,
        usePath: Boolean,
    ) {
        documents
            .filterIsInstance<Document>()
            .forEach { document ->
                KmpFileWriter(
                    if (usePath) {
                        name(document, path, ".md")
                    } else {
                        path.takeIf { it.contains(".md") } ?: "$path.md"
                    }
                )
                    .useKmp { writer ->
                        writeToWriter(
                            content = document.content,
                            kmpFileWriter = writer
                        )
                    }
            }
    }

    override fun writeDocument(document: Document, path: String, writeConfigFile: Boolean) {
        TODO("Not yet implemented")
    }

    fun parse(
        document: Map<Int, StoryStep>,
        parseStep: (StoryStep) -> Pair<ContentAdd, String?> = ::parseStep,
        prettyPrint: Boolean = false,
    ): String =
        buildString {
            writeTo(document, parseStep, prettyPrint) { line ->
                if (line != null) {
                    appendLine(line)
                } else {
                    appendLine()
                }
            }
        }

    private fun writeToWriter(
        content: Map<Int, StoryStep>,
        kmpFileWriter: KmpFileWriter,
        parseStep: (StoryStep) -> Pair<ContentAdd, String?> = ::parseStep,
        prettyPrint: Boolean = false
    ) {
        writeTo(content, parseStep, prettyPrint, kmpFileWriter::writeLine)
    }

    private fun writeTo(
        content: Map<Int, StoryStep>,
        parseStep: (StoryStep) -> Pair<ContentAdd, String?> = ::parseStep,
        prettyPrint: Boolean = false,
        writeFn: (String?) -> Unit
    ) {
        content
            .asSequence()
            .map { (_, storyStep) -> parseStep(storyStep) }
            .filter { (_, contextText) -> contextText != null }
            .let { contentList ->
                contentList.forEach { (contentAdd, contextText) ->
                    if (prettyPrint && contentAdd.isEmptyLineBefore()) {
                        writeFn(null)
                    }

                    writeFn(contextText)

                    if (prettyPrint && contentAdd.isEmptyLineAfter()) {
                        writeFn(null)
                    }
                }
            }
    }

    private fun parseStep(storyStep: StoryStep): Pair<ContentAdd, String?> =
        when (storyStep.type.number) {
            StoryTypes.TITLE.type.number -> ContentAdd.NOTHING to "# ${storyStep.text}"

            StoryTypes.TEXT.type.number -> parseTextWithTags(storyStep)

            StoryTypes.CHECK_ITEM.type.number -> {
                val (content, text) = parseTextWithTags(storyStep)

                content to "[] $text"
            }

            StoryTypes.UNORDERED_LIST_ITEM.type.number -> {
                val (content, text) = parseTextWithTags(storyStep)

                content to "- $text"
            }

            else -> ContentAdd.NOTHING to storyStep.text
        }

    private fun parseTextWithTags(storyStep: StoryStep): Pair<ContentAdd, String?> =
        when {
            storyStep.tags.contains(TagInfo(Tag.H1)) ->
                ContentAdd.EMPTY_LINE_BEFORE_AND_AFTER to "# ${storyStep.text}"

            storyStep.tags.contains(TagInfo(Tag.H2)) ->
                ContentAdd.EMPTY_LINE_BEFORE_AND_AFTER to "## ${storyStep.text}"

            storyStep.tags.contains(TagInfo(Tag.H3)) ->
                ContentAdd.EMPTY_LINE_BEFORE_AND_AFTER to "### ${storyStep.text}"

            storyStep.tags.contains(TagInfo(Tag.H4)) ->
                ContentAdd.EMPTY_LINE_BEFORE_AND_AFTER to "#### ${storyStep.text}"

            else -> ContentAdd.NOTHING to storyStep.text
        }
}

enum class ContentAdd {
    NOTHING,
    EMPTY_LINE_BEFORE,
    EMPTY_LINE_AFTER,
    EMPTY_LINE_BEFORE_AND_AFTER;

    fun isEmptyLineBefore() = this == EMPTY_LINE_BEFORE || this == EMPTY_LINE_BEFORE_AND_AFTER

    fun isEmptyLineAfter() = this == EMPTY_LINE_AFTER || this == EMPTY_LINE_BEFORE_AND_AFTER
}

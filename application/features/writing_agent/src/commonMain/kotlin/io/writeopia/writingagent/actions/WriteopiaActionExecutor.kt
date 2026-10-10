package io.writeopia.writingagent.actions

import io.writeopia.ai.AiCommand
import io.writeopia.ai.AiStreaming
import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.writingagent.actions.AiTextWriter.finalText

/**
 * Applies the suggestions to the document through the [WriteopiaStateManager]. Blocks are
 * inserted right below the paragraph the suggestion is about, except the heading, which goes
 * above it, the TL;DR, which goes under the title, and the conclusion, which goes at the end.
 *
 * The actions that need text, like the TL;DR, use the AI the user picked, through [resolveAi].
 * Without one, they say so in the document, like the editor's AI menu does.
 */
class WriteopiaActionExecutor(
    private val manager: WriteopiaStateManager,
    private val resolveAi: suspend () -> AiStreaming?,
    private val addImage: (path: String, position: Double) -> Unit = { path, position ->
        manager.addImage(path, position)
    },
) : WritingActionExecutor {

    override suspend fun execute(action: WritingSuggestionAction, storyStepId: String?, ui: WritingAgentUi) {
        val position = positionOf(storyStepId) ?: manager.lastPosition()
        val below = position + 1

        when (action) {
            WritingSuggestionAction.CODE_BLOCK -> insert(StoryTypes.CODE_BLOCK, below)

            WritingSuggestionAction.LIST -> insert(StoryTypes.UNORDERED_LIST_ITEM, below)

            WritingSuggestionAction.CHECK_LIST -> insert(StoryTypes.CHECK_ITEM, below)

            WritingSuggestionAction.NEW_DOCUMENT_LINK -> {
                insert(StoryTypes.TEXT, below)
                manager.addLinkToDocument(below)
            }

            WritingSuggestionAction.IMAGE -> ui.pickImage { path ->
                val current = positionOf(storyStepId) ?: manager.lastPosition()
                addImage(path, current + 1)
            }

            WritingSuggestionAction.DRAWING -> ui.openDrawing()

            WritingSuggestionAction.SPREADSHEET -> spreadsheet(storyStepId, below)

            WritingSuggestionAction.TITLE -> heading(storyStepId, position)

            WritingSuggestionAction.CALLOUT -> manager.toggleTagForPosition(position, TagInfo(Tag.HIGH_LIGHT_BLOCK))

            WritingSuggestionAction.TLDR -> generateAt(afterTitle(), TLDR_PROMPT)

            WritingSuggestionAction.CONCLUSION -> generateAt(manager.lastPosition() + 1, CONCLUSION_PROMPT)
        }
    }

    private fun positionOf(storyStepId: String?): Double? {
        if (storyStepId == null) return null
        return manager.currentStory.value.stories.entries.firstOrNull { (_, step) -> step.id == storyStepId }?.key
    }

    private fun afterTitle(): Double {
        val stories = manager.currentStory.value.stories
        val titlePosition = stories.entries.firstOrNull { (_, step) -> step.type == StoryTypes.TITLE.type }?.key
        return (titlePosition ?: -1.0) + 1
    }

    private fun insert(type: StoryTypes, position: Double) {
        manager.trackState()
        manager.addAtPosition(StoryStep(type = type.type, text = ""), position)
    }

    private suspend fun generateAt(position: Double, instructions: String) {
        val ai = resolveAi()

        if (ai == null) {
            AiTextWriter.noAiAt(manager, position)
            return
        }

        val prompt = "$instructions\n```\n${manager.getDocumentText()}\n```"
        AiTextWriter.streamAt(manager, position, ai.stream(AiCommand.PROMPT, prompt))
    }

    private suspend fun heading(storyStepId: String?, position: Double) {
        val paragraph = manager.getStory(position)?.text ?: ""
        val ai = resolveAi()
        val title = ai
            ?.stream(AiCommand.PROMPT, "$HEADING_PROMPT\n```\n$paragraph\n```")
            ?.finalText()
            ?.lines()
            ?.firstOrNull()
            ?.trim('#', ' ', '"', '*')
            ?: ""

        // The document may have changed while the AI answered.
        val current = positionOf(storyStepId) ?: position
        manager.trackState()
        manager.addAtPosition(
            StoryStep(type = StoryTypes.TEXT.type, text = title, tags = setOf(TagInfo(Tag.H2))),
            current
        )
    }

    private suspend fun spreadsheet(storyStepId: String?, position: Double) {
        val paragraph = manager.getStory(position - 1)?.text ?: ""
        val rows = resolveAi()
            ?.stream(AiCommand.PROMPT, "$TABLE_PROMPT\n```\n$paragraph\n```")
            ?.finalText()
            ?.let(MarkdownTable::parse)
            .orEmpty()

        val columnCount = rows.maxOfOrNull { it.size } ?: DEFAULT_COLUMNS
        val rowCount = rows.size.takeIf { it > 0 } ?: DEFAULT_ROWS

        val current = positionOf(storyStepId)?.let { it + 1 } ?: position
        val spreadsheetId = manager.insertSpreadsheet(current, columnCount, rowCount) ?: return

        rows.forEachIndexed { rowIndex, cells ->
            cells.forEachIndexed { cellIndex, text ->
                if (text.isNotEmpty()) {
                    manager.updateSpreadsheetCell(spreadsheetId, rowIndex, cellIndex, text)
                }
            }
        }
    }

    companion object {
        private const val DEFAULT_COLUMNS = 3
        private const val DEFAULT_ROWS = 3

        const val TLDR_PROMPT =
            "Write a TL;DR for the following document: at most three sentences with its key " +
                "points. Answer only with the TL;DR, without a title. Use the language of the text."

        const val CONCLUSION_PROMPT =
            "Write a conclusion for the following document: one or two paragraphs that wrap up " +
                "its ideas. Answer only with the conclusion, without a title. Use the language " +
                "of the text."

        const val HEADING_PROMPT =
            "Write a short heading, of at most eight words, for the following paragraph. Answer " +
                "only with the heading: no quotes, no Markdown, no punctuation at the end. Use " +
                "the language of the text."

        const val TABLE_PROMPT =
            "Turn the information of the following paragraph into a table. Answer only with a " +
                "Markdown table: a header row, a separator row and the data rows. Nothing else. " +
                "Use the language of the text."
    }
}

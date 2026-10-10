package io.writeopia.writingagent.actions

import io.writeopia.ai.AiCommand
import io.writeopia.ai.AiStreaming
import io.writeopia.ai.task.AiTaskManager
import io.writeopia.ai.task.AiTaskType
import io.writeopia.app.dto.writingagent.WritingSuggestionAction
import io.writeopia.di.ApiLogger
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryType
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.writingagent.actions.AiTextWriter.finalTextOrError

/**
 * Applies the suggestions to the document through the [WriteopiaStateManager]. Blocks are
 * inserted right below the paragraph the suggestion is about, except the section heading, which
 * goes above it, the TL;DR, which goes under the title, and the conclusion, which goes at the
 * end. The document title is written into the title block itself.
 *
 * The actions that need text, like the TL;DR, use the AI the user picked, through [resolveAi].
 * They run as tasks of [aiTaskManager], so the editor's task indicator shows their progress and
 * why they failed, like the editor's AI menu does. Without a task manager they run inline.
 */
class WriteopiaActionExecutor(
    private val manager: WriteopiaStateManager,
    private val resolveAi: suspend () -> AiStreaming?,
    private val addImage: (path: String, position: Double) -> Unit = { path, position ->
        manager.addImage(path, position)
    },
    private val aiTaskManager: AiTaskManager? = null,
    /** The prefix of the task IDs, so the editor can cancel them with its own. */
    private val taskIdPrefix: () -> String = { "writing-agent" },
    private val log: (String) -> Unit = { message -> ApiLogger.log("[WritingAgent] $message") },
) : WritingActionExecutor {

    override suspend fun execute(action: WritingSuggestionAction, storyStepId: String?, ui: WritingAgentUi) {
        val position = positionOf(storyStepId) ?: manager.lastPosition()
        val below = position + 1

        log("executing $action for $storyStepId at position $position")

        when (action) {
            WritingSuggestionAction.CODE_BLOCK -> aiTask("Writing the code snippet...") {
                codeSnippet(storyStepId, position)
            }

            WritingSuggestionAction.LIST -> aiTask("Writing the list...") {
                listItems(storyStepId, position, StoryTypes.UNORDERED_LIST_ITEM, LIST_PROMPT)
            }

            WritingSuggestionAction.CHECK_LIST -> aiTask("Writing the checklist...") {
                listItems(storyStepId, position, StoryTypes.CHECK_ITEM, CHECKLIST_PROMPT)
            }

            WritingSuggestionAction.NEW_DOCUMENT_LINK -> {
                insert(StoryTypes.TEXT, below)
                manager.addLinkToDocument(below)
            }

            WritingSuggestionAction.IMAGE -> ui.pickImage { path ->
                val current = positionOf(storyStepId) ?: manager.lastPosition()
                addImage(path, current + 1)
            }

            WritingSuggestionAction.DRAWING -> ui.openDrawing()

            WritingSuggestionAction.SPREADSHEET -> aiTask("Filling the table...") {
                spreadsheet(storyStepId, below)
            }

            WritingSuggestionAction.SECTION_HEADING -> aiTask("Writing a section heading...") {
                heading(storyStepId, position)
            }

            WritingSuggestionAction.DOCUMENT_TITLE -> aiTask("Writing the document title...") {
                documentTitle()
            }

            WritingSuggestionAction.CALLOUT -> manager.toggleTagForPosition(position, TagInfo(Tag.HIGH_LIGHT_BLOCK))

            WritingSuggestionAction.TLDR -> aiTask("Writing a TL;DR...") {
                // A card under the title, starting with "TLDR: ", like a hand-written one.
                generateAt(
                    position = afterTitle(),
                    instructions = TLDR_PROMPT,
                    type = StoryTypes.TEXT.type,
                    tags = setOf(TagInfo(Tag.CARD_BLOCK)),
                    prefix = TLDR_PREFIX,
                )
            }

            WritingSuggestionAction.CONCLUSION -> aiTask("Writing a conclusion...") {
                generateAt(manager.lastPosition() + 1, CONCLUSION_PROMPT)
            }
        }
    }

    /**
     * Runs [block] as a task the editor shows, or inline without a task manager. A failure shows
     * its message in the task indicator, so the user learns why nothing changed.
     */
    private suspend fun aiTask(description: String, block: suspend () -> Result<Unit>) {
        val taskManager = aiTaskManager

        if (taskManager == null) {
            block().onFailure { log("$description failed: ${it.message}") }
            return
        }

        taskManager.enqueueTask(
            id = "${taskIdPrefix()}-${GenerateId.generate()}",
            type = AiTaskType.TEXT_GENERATION,
            description = description
        ) {
            block().onFailure { log("$description failed: ${it.message}") }
        }
    }

    /** The AI the user picked, or the failure that explains there is none. */
    private suspend fun aiOrFailure(): Result<AiStreaming> =
        resolveAi()?.let { Result.success(it) }
            ?: Result.failure(NoAiException())

    class NoAiException : Exception(AiTextWriter.NO_AI_MESSAGE)

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

    private suspend fun generateAt(
        position: Double,
        instructions: String,
        type: StoryType = StoryTypes.AI_ANSWER.type,
        tags: Set<TagInfo> = emptySet(),
        prefix: String = "",
    ): Result<Unit> {
        val ai = aiOrFailure().getOrElse { failure ->
            AiTextWriter.noAiAt(manager, position)
            return Result.failure(failure)
        }

        val prompt = "$instructions\n```\n${manager.getDocumentText()}\n```"
        AiTextWriter.streamAt(
            manager = manager,
            position = position,
            answers = ai.stream(AiCommand.PROMPT, prompt),
            type = type,
            tags = tags,
            prefix = prefix,
        )
        return Result.success(Unit)
    }

    /** One line of text from the AI, cleaned of the Markdown and quotes it tends to add. */
    private suspend fun askOneLine(instructions: String, text: String): Result<String> =
        aiOrFailure().mapCatching { ai ->
            ai.stream(AiCommand.PROMPT, "$instructions\n```\n$text\n```")
                .finalTextOrError()
                .getOrThrow()
                .lines()
                .first { it.isNotBlank() }
                .trim('#', ' ', '"', '*', '.')
        }

    /**
     * Asks the AI for the items the paragraph calls for and inserts them below it, one block
     * each, as [type]. Without an AI, one empty item is inserted so the click still does
     * something, and the failure says why there is no content.
     */
    private suspend fun listItems(
        storyStepId: String?,
        position: Double,
        type: StoryTypes,
        instructions: String,
    ): Result<Unit> {
        val paragraph = manager.getStory(position)?.text ?: ""
        val context = "Title: ${manager.getDocument().title}\nParagraph: $paragraph"

        val items = aiOrFailure().mapCatching { ai ->
            ai.stream(AiCommand.PROMPT, "$instructions\n```\n$context\n```")
                .finalTextOrError()
                .getOrThrow()
                .let(MarkdownList::parse)
                .take(MAX_LIST_ITEMS)
                .ifEmpty { throw Exception("The AI answered no items") }
        }

        // The document may have changed while the AI answered.
        val below = (positionOf(storyStepId) ?: position) + 1
        val texts = items.getOrDefault(listOf(""))

        log("inserting ${texts.size} ${type.name} items below $storyStepId")
        manager.trackState()
        texts.reversed().forEach { text ->
            manager.addAtPosition(StoryStep(type = type.type, text = text), below)
        }

        return items.map { }
    }

    /**
     * Asks the AI for the code the paragraph calls for and inserts it as one code block below
     * the paragraph. Without an AI an empty code block is inserted, and the failure says why.
     */
    private suspend fun codeSnippet(storyStepId: String?, position: Double): Result<Unit> {
        val paragraph = manager.getStory(position)?.text ?: ""
        val context = "Title: ${manager.getDocument().title}\nParagraph: $paragraph\n\nDocument:\n${manager.getDocumentText()}"

        val code = aiOrFailure().mapCatching { ai ->
            ai.stream(AiCommand.PROMPT, "$CODE_PROMPT\n```\n$context\n```")
                .finalTextOrError()
                .getOrThrow()
                .let(MarkdownCode::parse)
                .ifBlank { throw Exception("The AI answered no code") }
        }

        // The document may have changed while the AI answered.
        val below = (positionOf(storyStepId) ?: position) + 1

        log("inserting a code block of ${code.getOrNull()?.lines()?.size ?: 0} lines below $storyStepId")
        manager.trackState()
        manager.addAtPosition(StoryStep(type = StoryTypes.CODE_BLOCK.type, text = code.getOrDefault("")), below)

        return code.map { }
    }

    private suspend fun heading(storyStepId: String?, position: Double): Result<Unit> {
        val paragraph = manager.getStory(position)?.text ?: ""

        return askOneLine(HEADING_PROMPT, paragraph).map { title ->
            // The document may have changed while the AI answered.
            val current = positionOf(storyStepId) ?: position
            manager.trackState()
            manager.addAtPosition(
                StoryStep(type = StoryTypes.TEXT.type, text = title, tags = setOf(TagInfo(Tag.H2))),
                current
            )
        }
    }

    /**
     * Asks the AI for a title and writes it into the title block, the one drawn in the header.
     * A document without a title block gets one at the top.
     */
    private suspend fun documentTitle(): Result<Unit> =
        askOneLine(DOCUMENT_TITLE_PROMPT, manager.getDocumentText()).map { title ->
            log("the AI titled the document \"$title\"")
            val titleEntry = manager.currentStory.value.stories.entries
                .firstOrNull { (_, step) -> step.type == StoryTypes.TITLE.type }

            manager.trackState()

            if (titleEntry == null) {
                manager.addAtPosition(StoryStep(type = StoryTypes.TITLE.type, text = title), 0.0)
            } else {
                // The drawers reload their text when the localId changes, like undo does.
                manager.changeStoryState(
                    Action.StoryStateChange(
                        titleEntry.value.copy(text = title, localId = GenerateId.generate()),
                        titleEntry.key
                    )
                )
            }
        }

    private suspend fun spreadsheet(storyStepId: String?, position: Double): Result<Unit> {
        val paragraph = manager.getStory(position - 1)?.text ?: ""
        val answer = aiOrFailure().mapCatching { ai ->
            ai.stream(AiCommand.PROMPT, "$TABLE_PROMPT\n```\n$paragraph\n```").finalTextOrError().getOrThrow()
        }
        val rows = answer.getOrNull()?.let(MarkdownTable::parse).orEmpty()

        val columnCount = rows.maxOfOrNull { it.size } ?: DEFAULT_COLUMNS
        val rowCount = rows.size.takeIf { it > 0 } ?: DEFAULT_ROWS

        // Even without an AI the table is made, empty, so the click does something.
        val current = positionOf(storyStepId)?.let { it + 1 } ?: position
        val spreadsheetId = manager.insertSpreadsheet(current, columnCount, rowCount)
            ?: return Result.failure(Exception("The document can't be edited"))

        rows.forEachIndexed { rowIndex, cells ->
            cells.forEachIndexed { cellIndex, text ->
                if (text.isNotEmpty()) {
                    manager.updateSpreadsheetCell(spreadsheetId, rowIndex, cellIndex, text)
                }
            }
        }

        return answer.map { }
    }

    companion object {
        private const val DEFAULT_COLUMNS = 3
        private const val DEFAULT_ROWS = 3
        private const val MAX_LIST_ITEMS = 10

        const val CODE_PROMPT =
            "The paragraph below introduces or describes a piece of code, a command or a " +
                "configuration snippet. Write that snippet, complete and ready to use, in the " +
                "language or tool the paragraph implies (use the rest of the document to " +
                "decide when the paragraph doesn't say). Answer only with the code, inside one " +
                "fenced code block, with no explanation before or after it. Comments in the " +
                "code should use the language of the text."

        const val LIST_PROMPT =
            "The paragraph below introduces or enumerates several items. Write those items as a " +
                "bullet list, completing the list with the items the paragraph implies. Answer " +
                "only with the items, one per line, each starting with \"- \". At most ten " +
                "items, each a short phrase. Use the language of the text."

        const val CHECKLIST_PROMPT =
            "The paragraph below describes tasks or steps to complete. Write them as a checklist, " +
                "one task per line, each starting with \"- \", in the order they should be " +
                "done. Answer only with the tasks. At most ten, each a short phrase starting " +
                "with a verb. Use the language of the text."

        const val TLDR_PREFIX = "TLDR: "

        const val TLDR_PROMPT =
            "Write a TL;DR for the following document: at most three sentences with its key " +
                "points. Answer only with the sentences: no title, no \"TL;DR\" label, no " +
                "Markdown. Use the language of the text."

        const val CONCLUSION_PROMPT =
            "Write a conclusion for the following document: one or two paragraphs that wrap up " +
                "its ideas. Answer only with the conclusion, without a title. Use the language " +
                "of the text."

        const val HEADING_PROMPT =
            "Write a short heading, of at most eight words, for the following paragraph. Answer " +
                "only with the heading: no quotes, no Markdown, no punctuation at the end. Use " +
                "the language of the text."

        const val DOCUMENT_TITLE_PROMPT =
            "Write a title for the following document, of at most eight words. Answer only with " +
                "the title: no quotes, no Markdown, no punctuation at the end. Use the language " +
                "of the text."

        const val TABLE_PROMPT =
            "Turn the information of the following paragraph into a table. Answer only with a " +
                "Markdown table: a header row, a separator row and the data rows. Nothing else. " +
                "Use the language of the text."
    }
}

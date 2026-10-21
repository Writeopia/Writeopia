package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.ai.AiCommand
import io.writeopia.ai.ResolvedAi
import io.writeopia.app.dto.ai.AiCommandIntent
import io.writeopia.app.dto.ai.AiCommandRequest
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Runs a free command typed by the writer, about the document and the lines selected when it
 * was typed, if any. The cloud decides first (with Jev) whether the command asks for new
 * content or changes the text the writer has. New content streams in as an AI suggestion,
 * after the selection or after the cursor. A change replaces the selected lines, or rewrites
 * the document when nothing is selected; a skeleton stands in for the text meanwhile. A local
 * AI has no judge, so its answers are always added.
 */
object AiCommandService {

    suspend fun run(
        command: String,
        writeopiaManager: WriteopiaStateManager,
        ai: ResolvedAi,
    ): Result<Unit> {
        val stories = writeopiaManager.currentStory.value.stories
        val context = AiCommandContext.build(stories, writeopiaManager.onEditPositions.value)
        val request = AiCommandRequest(
            command = command,
            selectedText = context.selectedText,
            documentTitle = writeopiaManager.getDocument().title,
            documentText = context.documentText,
        )

        val intent = when (ai) {
            is ResolvedAi.Cloud -> when (val decision = ai.commandIntent(request)) {
                is ResultData.Complete -> decision.data.intent ?: AiCommandIntent.ADD
                // Adding never loses text, so it is what happens when the judge is unavailable.
                else -> AiCommandIntent.ADD
            }
            // A local model has no judge: its answer is added, which never loses text.
            is ResolvedAi.Local -> AiCommandIntent.ADD
        }

        return when (intent) {
            // New content: an AI suggestion after the selection, or after the cursor.
            AiCommandIntent.ADD -> {
                val position = PromptService.answerPosition(answerTarget(writeopiaManager), writeopiaManager)
                stream(ai, request.copy(intent = AiCommandIntent.ADD))
                    .let { answer -> PromptService.streamIntoDocument(answer, writeopiaManager, position) }
                Result.success(Unit)
            }

            AiCommandIntent.REPLACE ->
                if (context.selectedPositions.isEmpty()) {
                    rewriteDocument(command, writeopiaManager, ai)
                } else {
                    replace(ai, request, context, writeopiaManager)
                }

            AiCommandIntent.REWRITE -> rewriteDocument(command, writeopiaManager, ai)
        }
    }

    /**
     * An edit with nothing selected is about the document: the document (or the part of it
     * around the cursor that fits) goes to the AI as Markdown and comes back whole, rewritten.
     * A skeleton stands in for it meanwhile, and the lines take their new shape as the answer
     * arrives. Blocks the AI cannot write, like spreadsheets, are neither sent nor changed.
     */
    private suspend fun rewriteDocument(
        command: String,
        writeopiaManager: WriteopiaStateManager,
        ai: ResolvedAi,
    ): Result<Unit> {
        val state = writeopiaManager.currentStory.value
        val rewrite = DocumentRewrite.build(state.stories, cursor = state.focus ?: state.selection.position)
            ?: return Result.failure(IllegalStateException("There is nothing to rewrite in the document"))

        val request = AiCommandRequest(
            command = command,
            documentTitle = writeopiaManager.getDocument().title,
            documentText = rewrite.markdown,
            intent = AiCommandIntent.REWRITE,
        )
        val skeleton = StoryStep(type = StoryTypes.SKELETON.type, text = rewrite.originalText, ephemeral = true)
        val from = rewrite.from
        val toExclusive = rewrite.toExclusive

        val begun = writeopiaManager.beginRegionRewrite(from, toExclusive, listOf(skeleton) + rewrite.keptBlocksInOrder)
        if (!begun) return Result.failure(IllegalStateException("There is nothing to rewrite in the document"))

        var answer = ""
        var error: Exception? = null

        try {
            stream(ai, request).collect { result ->
                when (result) {
                    is ResultData.Complete -> {
                        answer = result.data
                        val parsed = rewrite.parse(answer)
                        writeopiaManager.updateRegion(
                            from,
                            toExclusive,
                            parsed.stories + skeleton + parsed.pendingBlocks,
                            done = false
                        )
                    }

                    is ResultData.Error -> error = result.exception ?: Exception("The AI could not answer")
                    is ResultData.Loading,
                    is ResultData.Idle,
                    is ResultData.InProgress -> {}
                }
            }
        } catch (e: CancellationException) {
            // Stopped by the writer (or the editor closed): the document comes back as it was.
            writeopiaManager.updateRegion(from, toExclusive, rewrite.restoredStories(), done = true)
            throw e
        }

        val parsed = rewrite.parse(answer)
        val failure = error ?: if (parsed.stories.isEmpty()) Exception("The AI answered nothing") else null

        return if (failure != null) {
            // The document comes back as it was, so nothing is lost when the AI fails.
            writeopiaManager.updateRegion(from, toExclusive, rewrite.restoredStories(), done = true)
            Result.failure(failure)
        } else {
            writeopiaManager.updateRegion(from, toExclusive, parsed.stories + parsed.pendingBlocks, done = true)
            Result.success(Unit)
        }
    }

    /** Where the answer goes: after the selected lines, or after the cursor. */
    fun answerTarget(writeopiaManager: WriteopiaStateManager): AiTargetMode =
        if (writeopiaManager.isOnSelection) AiTargetMode.SELECTED_LINES else AiTargetMode.CURSOR

    private suspend fun replace(
        ai: ResolvedAi,
        request: AiCommandRequest,
        context: AiCommandContext,
        writeopiaManager: WriteopiaStateManager,
    ): Result<Unit> {
        val skeletonPosition = writeopiaManager.replaceWithSkeleton(context.selectedPositions.toSet())
            ?: return Result.failure(IllegalStateException("The selected lines are not in the document"))

        var answer = ""
        var error: Exception? = null

        try {
            stream(ai, request.copy(intent = AiCommandIntent.REPLACE)).collect { result ->
                when (result) {
                    is ResultData.Complete -> answer = result.data
                    is ResultData.Error -> error = result.exception ?: Exception("The AI could not answer")
                    is ResultData.Loading,
                    is ResultData.Idle,
                    is ResultData.InProgress -> {}
                }
            }
        } catch (e: CancellationException) {
            // Stopped by the writer (or the editor closed): the lines come back where they were.
            writeopiaManager.replaceSkeletonWithText(skeletonPosition, context.selectedText)
            throw e
        }

        val failure = error ?: if (answer.isBlank()) Exception("The AI answered nothing") else null

        return if (failure != null) {
            // The lines come back where they were, so nothing is lost when the AI fails.
            writeopiaManager.replaceSkeletonWithText(skeletonPosition, context.selectedText)
            Result.failure(failure)
        } else {
            writeopiaManager.replaceSkeletonWithText(skeletonPosition, answer.trim())
            Result.success(Unit)
        }
    }

    private fun stream(ai: ResolvedAi, request: AiCommandRequest): Flow<ResultData<String>> =
        when (ai) {
            is ResolvedAi.Cloud -> ai.streamCommand(request)
            is ResolvedAi.Local -> ai.stream(AiCommand.PROMPT, localPrompt(request))
        }

    /**
     * The prompt of a local model, which has no judge: the answer is always added to the document.
     * A rewrite is still described, for the day a local judge decides one.
     */
    internal fun localPrompt(request: AiCommandRequest): String = buildString {
        if (request.intent == AiCommandIntent.REWRITE) {
            appendLine(
                "You are a writing assistant inside a text editor. The document below is in Markdown, " +
                    "one paragraph per line; a line like [[writeopia-block-3]] stands for a block you " +
                    "cannot see, keep every such line exactly as it is, in its place. Apply the " +
                    "writer's command and answer with the whole document again, in the same Markdown, " +
                    "keeping every line the command does not affect exactly as it is, without " +
                    "explaining what you did and without a code fence around the answer."
            )
        } else {
            appendLine(
                "You are a writing assistant inside a text editor. Answer the writer's command with " +
                    "new content only, in the language of the document, without explaining what you did."
            )
        }
        if (request.documentTitle.isNotBlank()) appendLine("Document title: ${request.documentTitle}")
        if (request.documentText.isNotBlank()) {
            appendLine("Document:")
            appendLine("```")
            appendLine(request.documentText)
            appendLine("```")
        }
        if (request.selectedText.isNotBlank()) {
            appendLine("The writer selected these lines:")
            appendLine("```")
            appendLine(request.selectedText)
            appendLine("```")
        }
        appendLine("Command: ${request.command}")
    }
}

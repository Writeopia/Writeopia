package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.ai.AiCommand
import io.writeopia.ai.ResolvedAi
import io.writeopia.app.dto.ai.AiCommandIntent
import io.writeopia.app.dto.ai.AiCommandRequest
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.flow.Flow

/**
 * Runs a free command typed by the writer, about the document and the lines selected when it
 * was typed, if any. With a selection, the cloud decides first (with Jev) whether the answer
 * replaces those lines or is added after them; then the answer (from Gemini) is put in the
 * document: streamed after the selection, or in its place, which shows a skeleton while the
 * answer is written. Without a selection the answer is added after the cursor. A local AI has
 * no judge, so its answers are always added.
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

        val intent = when {
            context.selectedText.isBlank() -> AiCommandIntent.ADD
            ai is ResolvedAi.Cloud -> when (val decision = ai.commandIntent(request)) {
                is ResultData.Complete -> decision.data.intent ?: AiCommandIntent.ADD
                // Adding never loses text, so it is what happens when the judge is unavailable.
                else -> AiCommandIntent.ADD
            }
            else -> AiCommandIntent.ADD
        }

        return when (intent) {
            AiCommandIntent.ADD -> {
                val position = PromptService.answerPosition(answerTarget(writeopiaManager), writeopiaManager)
                stream(ai, request.copy(intent = AiCommandIntent.ADD))
                    .let { answer -> PromptService.streamIntoDocument(answer, writeopiaManager, position) }
                Result.success(Unit)
            }

            AiCommandIntent.REPLACE -> replace(ai, request, context, writeopiaManager)
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

        stream(ai, request.copy(intent = AiCommandIntent.REPLACE)).collect { result ->
            when (result) {
                is ResultData.Complete -> answer = result.data
                is ResultData.Error -> error = result.exception ?: Exception("The AI could not answer")
                is ResultData.Loading,
                is ResultData.Idle,
                is ResultData.InProgress -> {}
            }
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

    /** The prompt of a local model, which has no judge: the answer is always added to the document. */
    internal fun localPrompt(request: AiCommandRequest): String = buildString {
        appendLine(
            "You are a writing assistant inside a text editor. Answer the writer's command with " +
                "new content only, in the language of the document, without explaining what you did."
        )
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

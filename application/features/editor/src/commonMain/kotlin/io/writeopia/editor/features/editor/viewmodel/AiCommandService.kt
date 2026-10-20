package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.ai.AiCommand
import io.writeopia.ai.ResolvedAi
import io.writeopia.app.dto.ai.AiCommandIntent
import io.writeopia.app.dto.ai.AiCommandRequest
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.flow.Flow

/**
 * Runs a free command typed by the writer. The cloud decides first (with Jev) whether the answer
 * replaces the text the command is about or adds to it; then the answer (from Gemini) is put in
 * the document: streamed after the text, or in the place of the text, which shows a skeleton
 * while the answer is written. A local AI has no judge, so its answers are always added.
 */
object AiCommandService {

    suspend fun run(
        command: String,
        targetMode: AiTargetMode,
        writeopiaManager: WriteopiaStateManager,
        ai: ResolvedAi,
    ): Result<Unit> {
        val state = writeopiaManager.currentStory.value
        val stories = state.stories
        val targets: List<Double> = when (targetMode) {
            AiTargetMode.SELECTED_LINES -> writeopiaManager.onEditPositions.value.sorted()
            AiTargetMode.CURSOR -> listOfNotNull(state.focus ?: state.selection.position)
            AiTargetMode.DOCUMENT -> emptyList()
        }

        val context = AiCommandContext.build(stories, targets)
        val request = AiCommandRequest(
            command = command,
            targetText = context.targetText,
            documentTitle = writeopiaManager.getDocument().title,
            textBefore = context.textBefore,
            textAfter = context.textAfter,
        )

        val intent = when {
            context.targetText.isBlank() -> AiCommandIntent.ADD
            ai is ResolvedAi.Cloud -> when (val decision = ai.commandIntent(request)) {
                is ResultData.Complete -> decision.data.intent ?: AiCommandIntent.ADD
                // Adding never loses text, so it is what happens when the judge is unavailable.
                else -> AiCommandIntent.ADD
            }
            else -> AiCommandIntent.ADD
        }

        return when (intent) {
            AiCommandIntent.ADD -> {
                val position = PromptService.answerPosition(targetMode, writeopiaManager)
                stream(ai, request.copy(intent = AiCommandIntent.ADD))
                    .let { answer -> PromptService.streamIntoDocument(answer, writeopiaManager, position) }
                Result.success(Unit)
            }

            AiCommandIntent.REPLACE -> replace(ai, request, context, writeopiaManager)
        }
    }

    private suspend fun replace(
        ai: ResolvedAi,
        request: AiCommandRequest,
        context: AiCommandContext,
        writeopiaManager: WriteopiaStateManager,
    ): Result<Unit> {
        val skeletonPosition = writeopiaManager.replaceWithSkeleton(context.targetPositions.toSet())
            ?: return Result.failure(IllegalStateException("The text to replace is not in the document"))

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
            // The text comes back where it was, so nothing is lost when the AI fails.
            writeopiaManager.replaceSkeletonWithText(skeletonPosition, context.targetText)
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

    /** The prompt of a local model, which has no judge: the answer is always added after the text. */
    internal fun localPrompt(request: AiCommandRequest): String = buildString {
        appendLine(
            "You are a writing assistant inside a text editor. Answer the writer's command with " +
                "new content only, in the language of the document, without explaining what you did."
        )
        if (request.documentTitle.isNotBlank()) appendLine("Document title: ${request.documentTitle}")
        if (request.textBefore.isNotBlank()) {
            appendLine("Text before:")
            appendLine("```")
            appendLine(request.textBefore)
            appendLine("```")
        }
        if (request.targetText.isNotBlank()) {
            appendLine("The command is about this text:")
            appendLine("```")
            appendLine(request.targetText)
            appendLine("```")
        }
        if (request.textAfter.isNotBlank()) {
            appendLine("Text after:")
            appendLine("```")
            appendLine(request.textAfter)
            appendLine("```")
        }
        appendLine("Command: ${request.command}")
    }
}

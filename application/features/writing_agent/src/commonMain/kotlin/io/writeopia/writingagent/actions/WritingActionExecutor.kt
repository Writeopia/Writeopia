package io.writeopia.writingagent.actions

import io.writeopia.app.dto.writingagent.WritingSuggestionAction

/** Applies a suggestion to the document, around the block with [storyStepId]. */
interface WritingActionExecutor {
    suspend fun execute(action: WritingSuggestionAction, storyStepId: String?, ui: WritingAgentUi)
}

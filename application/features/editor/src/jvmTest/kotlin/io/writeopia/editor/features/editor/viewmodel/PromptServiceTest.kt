package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class PromptServiceTest {

    private fun manager(): WriteopiaStateManager {
        val now = Clock.System.now()
        return WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = UnconfinedTestDispatcher(),
        ).apply {
            loadDocument(
                Document(
                    content = mapOf(
                        0L to StoryStep(type = StoryTypes.TITLE.type, text = "Groceries"),
                        1L to StoryStep(type = StoryTypes.TEXT.type, text = "We need milk."),
                    ),
                    workspaceId = "",
                    createdAt = now,
                    lastUpdatedAt = now,
                    parentId = "root",
                    lastSyncedAt = null,
                )
            )
        }
    }

    @Test
    fun `a streamed answer is one step, rewritten at each chunk`() = runTest {
        val manager = manager()
        val edits = mutableListOf<StoryStep>()
        val watching = launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.currentStory.collect { state ->
                (state.lastEdit as? LastEdit.LineEdition)?.storyStep
                    ?.takeIf { step -> step.type == StoryTypes.AI_ANSWER.type }
                    ?.let(edits::add)
            }
        }

        PromptService.prompt(
            prompt = "Summarize",
            writeopiaManager = manager,
            streamFn = { flowOf(ResultData.Complete("Sho"), ResultData.Complete("Short version.")) },
            promptPosition = 2L,
        )
        watching.cancel()

        // Two chunks went to the same step, so only one is ever saved and synced.
        val answer = manager.currentStory.value.stories[2L]!!
        assertEquals(StoryTypes.AI_ANSWER.type, answer.type)
        assertEquals("Short version.", answer.text)
        assertEquals(setOf(answer.id), edits.map { step -> step.id }.toSet())
        // Nothing is saved while the answer comes: the chunks are ephemeral, the final text is not.
        assertEquals(listOf("Sho", "Short version.", "Short version."), edits.map { step -> step.text })
        assertEquals(listOf(true, true, false), edits.map { step -> step.ephemeral })
        assertFalse(answer.ephemeral)
    }

    @Test
    fun `a free command with nothing selected answers at the end of the document`() = runTest {
        val manager = manager()
        // The cursor is on the title, as it is when the command field took the focus.
        manager.onFocusChange(0L, hasFocus = true)

        val target = AiCommandService.answerTarget(manager)

        assertEquals(AiTargetMode.DOCUMENT, target)
        assertEquals(manager.positionAfter(1L), PromptService.answerPosition(target, manager))
    }

    @Test
    fun `an answer with nothing in it leaves no block behind`() = runTest {
        val manager = manager()

        PromptService.prompt(
            prompt = "Summarize",
            writeopiaManager = manager,
            streamFn = { flowOf(ResultData.Loading(), ResultData.Complete("")) },
            promptPosition = 2L,
        )

        assertEquals(setOf(0L, 1L), manager.currentStory.value.stories.keys)
    }
}

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
                        0.0 to StoryStep(type = StoryTypes.TITLE.type, text = "Groceries"),
                        1.0 to StoryStep(type = StoryTypes.TEXT.type, text = "We need milk."),
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
        val answerIds = mutableSetOf<String>()
        val watching = launch(UnconfinedTestDispatcher(testScheduler)) {
            manager.currentStory.collect { state ->
                (state.lastEdit as? LastEdit.LineEdition)?.storyStep
                    ?.takeIf { step -> step.type == StoryTypes.AI_ANSWER.type }
                    ?.let { step -> answerIds.add(step.id) }
            }
        }

        PromptService.prompt(
            prompt = "Summarize",
            writeopiaManager = manager,
            streamFn = { flowOf(ResultData.Complete("Sho"), ResultData.Complete("Short version.")) },
            promptPosition = 2.0,
        )
        watching.cancel()

        // Two chunks went to the same step, so only one is ever saved and synced.
        val answer = manager.currentStory.value.stories[2.0]!!
        assertEquals(StoryTypes.AI_ANSWER.type, answer.type)
        assertEquals("Short version.", answer.text)
        assertEquals(setOf(answer.id), answerIds)
    }
}

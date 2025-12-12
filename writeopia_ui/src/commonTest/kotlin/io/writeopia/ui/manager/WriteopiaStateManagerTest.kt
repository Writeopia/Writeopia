@file:OptIn(ExperimentalTime::class)

package io.writeopia.ui.manager

import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.workspace.Workspace
import io.writeopia.sdk.repository.StoriesRepository
import io.writeopia.sdk.repository.UserRepository
import io.writeopia.ui.model.TextInput
import io.writeopia.ui.utils.MapStoryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class)
class WriteopiaStateManagerTest {

    private val imagesInLineRepo: StoriesRepository = object : StoriesRepository {
        override suspend fun history(): Map<Int, StoryStep> = MapStoryData.imageStepsList()
    }

    private val imageGroupRepo: StoriesRepository = object : StoriesRepository {
        override suspend fun history(): Map<Int, StoryStep> = MapStoryData.imageGroup()
    }

    private val messagesRepo: StoriesRepository = object : StoriesRepository {
        override suspend fun history(): Map<Int, StoryStep> = MapStoryData.messagesInLine()
    }

    private val singleMessageRepo: StoriesRepository = object : StoriesRepository {
        override suspend fun history(): Map<Int, StoryStep> = MapStoryData.singleMessage()
    }

    private val singleMessageRepoLineBreak: StoriesRepository = object : StoriesRepository {
        override suspend fun history(): Map<Int, StoryStep> = MapStoryData.singleMessageLineBreak()
    }

    private val complexMessagesRepository: StoriesRepository = object : StoriesRepository {
        override suspend fun history(): Map<Int, StoryStep> = MapStoryData.syncHistory()
    }

    private val userRepository: UserRepository = object : UserRepository {
        override suspend fun getUser(): WriteopiaUser = WriteopiaUser.disconnectedUser()
        override suspend fun getWorkspace(): Workspace = Workspace.disconnectedWorkspace()
    }

    @Test
    fun aNewStoryShouldStartCorrectly() {
        val manager = WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = UnconfinedTestDispatcher(),
            userRepository = userRepository,
        )

        manager.newDocument()

        val currentStory = manager.currentStory.value.stories
        val expected = mapOf(
            0 to StoryStep(type = StoryTypes.TITLE.type),
        ).mapValues { (_, storyStep) ->
            storyStep.type
        }

        assertEquals(
            expected,
            currentStory.mapValues { (_, storyStep) -> storyStep.type }
        )
    }

    @Test
    fun whenALineBreakHappensOneNewItemShouldBeCreated() {
        val input = MapStoryData.singleCheckItem()
        val checkItem = input[0]

        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(),
                userRepository = userRepository,
            ).apply {
                loadDocument(
                    Document(
                        content = input,
                        workspaceId = "",
                        createdAt = now,
                        lastUpdatedAt = now,
                        parentId = "root",
                        lastSyncedAt = null,
                    )
                )
            }

        val currentStory = storyManager.currentStory.value.stories

        storyManager.onLineBreak(
            Action.LineBreak(storyStep = checkItem!!, position = 0)
        )

        val newStory = storyManager.currentStory.value.stories

        assertEquals("check_item", newStory[0]!!.type.name, "the first item should be a check_item")
        assertEquals(
            "check_item",
            newStory[1]!!.type.name,
            "the second item should be a check_item"
        )
        assertEquals(
            currentStory.size + 1,
            newStory.size,
            "the size of the story should be 2"
        )
    }

    @Test
    fun whenNewTitleChangesItShouldChangeTheDocumentTitleChangesToo() = runTest {
        val input = MapStoryData.singleCheckItem()

        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = Dispatchers.Main,
                userRepository = userRepository,
            ).apply {
                loadDocument(
                    Document(
                        content = input,
                        workspaceId = "",
                        createdAt = now,
                        lastUpdatedAt = now,
                        parentId = "root",
                        lastSyncedAt = null,
                    )
                )
            }

        val title = "Title"

        storyManager.changeStoryState(
            Action.StoryStateChange(
                StoryStep(
                    text = title,
                    type = StoryTypes.TITLE.type
                ),
                position = 0,
            )
        )

        advanceUntilIdle()

//        assertEquals(title, storyManager.currentDocument?.title)
    }

    @Test
    fun mergeRequestShouldWork() = runTest {
        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        val now = Clock.System.now()
        storyManager.loadDocument(
            Document(
                content = imagesInLineRepo.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )

        val currentStory = storyManager.currentStory.value.stories
        val initialSize = currentStory.size

        val positionFrom = 1
        val positionTo = 0
        val sender = currentStory[positionFrom]!!
        val receiver = currentStory[positionTo]!!

        assertFalse(currentStory[positionTo]!!.isGroup, "The first step is not a Group")

        storyManager.mergeRequest(
            Action.Merge(
                sender = sender,
                receiver = receiver,
                positionFrom = positionFrom,
                positionTo = positionTo,
            )
        )

        val newStory = storyManager.currentStory.value.stories

        assertEquals(
            initialSize - 1,
            newStory.size,
            "One image should be removed"
        )
        assertEquals(
            true,
            newStory[positionTo]?.isGroup,
            "The first step should be now a GroupStep"
        )
    }

    @Test
    fun mergeREquestShouldWork2() = runTest {
        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        val now = Clock.System.now()

        storyManager.loadDocument(
            Document(
                content = imagesInLineRepo.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )

        val currentStory = storyManager.currentStory.value.stories
        val initialSize = currentStory.size

        val positionFrom = 1
        val positionTo = 0

        storyManager.mergeRequest(
            Action.Merge(
                receiver = currentStory[positionTo]!!,
                sender = currentStory[positionFrom]!!,
                positionTo = positionTo,
                positionFrom = positionFrom
            )
        )

        val newStory = storyManager.currentStory.value.stories

        assertEquals(initialSize - 1, newStory.size)
        assertEquals(
            true,
            newStory[positionTo]?.isGroup,
            "The first image should be a GroupImage now"
        )
        assertTrue(newStory[positionFrom] is StoryStep, "Other images should still exist")
    }

    @Test
    fun multipleMergeRequestsShouldWork() = runTest {
        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        val now = Clock.System.now()
        storyManager.loadDocument(
            Document(
                content = imagesInLineRepo.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )

        val currentStory = storyManager.currentStory.value.stories
        val initialSize = currentStory.size

        val positionFrom = 1
        val positionTo = 0

        storyManager.mergeRequest(
            Action.Merge(
                receiver = currentStory[positionTo]!!,
                sender = currentStory[positionFrom]!!,
                positionTo = positionTo,
                positionFrom = positionFrom
            )
        )

        val newHistory = storyManager.currentStory.value.stories

        assertEquals(initialSize - 1, newHistory.size, "One space and one image were removed")
        assertEquals(
            true,
            newHistory[positionTo]?.isGroup,
            "The first message should be a GroupImage instead of a Image now"
        )
        assertEquals(
            2,
            newHistory[positionTo]!!.steps.size,
            "The new created GroupImage should have 2 images"
        )

        repeat(2) {
            val newHistory2 = storyManager.currentStory.value.stories

            val newPositionFrom = 1
            val newPositionTo = 0

            storyManager.mergeRequest(
                Action.Merge(
                    receiver = newHistory2[newPositionTo]!!,
                    sender = newHistory2[newPositionFrom]!!,
                    positionTo = newPositionTo,
                    positionFrom = newPositionFrom
                )
            )
        }

        val newHistory3 = storyManager.currentStory.value.stories

        assertEquals(
            // 3 merges were requested
            initialSize - 3,
            newHistory3.size,
            "The minimum side should be 4 (group, space, large_space)"
        )
        assertEquals(true, newHistory3[positionTo]?.isGroup, "The GroupImage should still exist")
        assertEquals(
            4,
            newHistory3[positionTo]!!.steps.size,
            "Now the group has 4 images"
        )
    }

    @Test
    fun itShouldBePossibleToMergeAnImageInsideAMessageGroup() = runTest {
        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        storyManager.loadDocument(
            Document(
                content = imageGroupRepo.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )

        val currentStory = storyManager.currentStory.value.stories
        val initialSize = currentStory.size
        val initialImageGroupSize = currentStory[0]!!.steps.size

        val positionFrom = 1
        val positionTo = 0

        val mergeInfo = Action.Merge(
            receiver = currentStory[positionTo]!!,
            sender = currentStory[positionFrom]!!,
            positionTo = positionTo,
            positionFrom = positionFrom
        )
        storyManager.mergeRequest(mergeInfo)

        val newStory = storyManager.currentStory.value.stories

        assertEquals(initialSize - 1, newStory.size, "One image and one space were removed")
        assertTrue(newStory[positionTo]?.isGroup == true)
        assertEquals(
            initialImageGroupSize + 1,
            newStory[positionTo]!!.steps.size,
            "One element was added to the GroupStep"
        )
    }

    @Test
    fun itShouldBePossibleToMergeAnImageOutsideAMessageGroup() = runTest {
        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        storyManager.loadDocument(
            Document(
                content = imageGroupRepo.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )
        val positionTo = 1
        val positionFrom = 0

        val currentStory = storyManager.currentStory.value.stories
        val initialGroupSize = currentStory[positionFrom]!!.steps.size

        storyManager.mergeRequest(
            Action.Merge(
                receiver = currentStory[positionTo]!!,
                sender = currentStory[positionFrom]!!.steps[0],
                positionTo = positionTo,
                positionFrom = positionFrom
            )
        )

        val newStory = storyManager.currentStory.value.stories

        assertEquals(
            StoryTypes.GROUP_IMAGE.type,
            newStory[positionTo]!!.type,
            "The image should be now in the position 3, because of spaces."
        )
        assertEquals(
            initialGroupSize - 1,
            newStory[positionFrom]!!.steps.size,
            "The new story now it the GroupImage"
        )
    }

    @Test
    @Ignore
    fun whenMovingOutsideOfAGroupTheParentIdShouldBeNullNow() = runTest {
        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        storyManager.loadDocument(
            Document(
                content = imageGroupRepo.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )

        val currentStory = storyManager.currentStory.value.stories

        val positionTo = currentStory.size - 2
        val positionFrom = 1

        val storyToMove = currentStory[positionFrom]!!.steps[0]

        storyManager.moveRequest(
            Action.Move(
                storyStep = storyToMove,
                positionTo = positionTo,
                positionFrom = positionFrom
            )
        )

        val newStory = storyManager.currentStory.value.stories
        val lastContentStep = newStory[newStory.size - 3]!!

        assertEquals(
            StoryTypes.IMAGE.type,
            lastContentStep.type,
            "The last StoryUnit should be an image."
        )
        assertEquals(
            storyToMove.id,
            lastContentStep.id,
            "The image should be in the correct place now."
        )
        assertNull(
            "The parent of the separated image, should not be there.",
            lastContentStep.parentId
        )
        assertFalse(
            newStory[positionFrom]!!.steps.any { storyUnit ->
                storyUnit.id == storyToMove.id
            },
            "The moved image should not be in the group anymore"
        )
    }

    @Test
    fun itShouldBePossibleToSwitchMessagePlaces() = runTest {
        val now = Clock.System.now()

        val simpleMessagesRepo: StoriesRepository = object : StoriesRepository {
            override suspend fun history(): Map<Int, StoryStep> = MapStoryData.simpleMessages()
        }

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        storyManager.loadDocument(
            Document(
                content = simpleMessagesRepo.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )

        val positionFrom = 0
        val positionTo = 3

        val currentStory = storyManager.currentStory.value.stories
        val storyUnitToMove = currentStory[positionFrom]!!

        storyManager.moveRequest(Action.Move(storyUnitToMove, positionFrom, positionTo))

        val newStory = storyManager.currentStory.value.stories

        assertEquals(
            newStory[positionTo]!!.text,
            storyUnitToMove.text,
            "The first story should have been moved"
        )
    }

    @Test
    fun itShouldBePossibleToRevertMove() = runTest {
        val now = Clock.System.now()

        val simpleMessagesRepo: StoriesRepository = object : StoriesRepository {
            override suspend fun history(): Map<Int, StoryStep> = MapStoryData.simpleMessages()
        }

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )

        storyManager.loadDocument(
            Document(
                content = simpleMessagesRepo.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )

        val positionFrom = 2
        val positionTo = 5

        val currentStory = storyManager.currentStory.value.stories
        val storyUnitToMove = currentStory[positionFrom]!!

        storyManager.moveRequest(Action.Move(storyUnitToMove, positionFrom, positionTo))
        storyManager.undo()

        val oldIds = currentStory.mapValues { (_, storyStep) -> storyStep.text }
        val newIds =
            storyManager.currentStory.value.stories.mapValues { (_, storyStep) -> storyStep.text }

        assertEquals(oldIds, newIds)
    }

    @Test
    fun deletingAndLeavingASingleElementInAGroupDestroysTheGroup() = runTest {
        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        storyManager.loadDocument(
            Document(
                content = imageGroupRepo.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )
        val groupPosition = 0

        val currentStory = storyManager.currentStory.value.stories
        assertEquals(
            StoryTypes.GROUP_IMAGE.type,
            currentStory[groupPosition]!!.type,
            "initial the story unit should be a group"
        )

        val lastImageInsideGroup =
            { storyManager.currentStory.value.stories[groupPosition]!!.steps.last() }

        storyManager.onDelete(
            Action.DeleteStory(
                storyStep = lastImageInsideGroup(),
                position = groupPosition
            )
        )

        storyManager.onDelete(
            Action.DeleteStory(
                storyStep = lastImageInsideGroup(),
                position = groupPosition
            )
        )

        val newStory = storyManager.currentStory.value.stories

        assertEquals(
            StoryTypes.IMAGE.type,
            newStory[groupPosition]!!.type,
            "the group become just an image because there's only a single image"
        )
    }

    @Test
    fun whenDeletingAMessageItShouldNotLeaveConsecutiveSpaces() = runTest {
        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        storyManager.loadDocument(
            Document(
                content = messagesRepo.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )

        storyManager.onDelete(
            Action.DeleteStory(
                storyManager.currentStory.value.stories[3]!!,
                3
            )
        )

        val stack: MutableList<StoryStep> = mutableListOf()

        storyManager.currentStory.value.stories.forEach { (_, storyUnit) ->
            if (stack.isNotEmpty() && stack.lastOrNull()?.type?.name == "space" && storyUnit.type.name == "space") {
                fail("Consecutive spaces happened.")
            }

            stack.add(storyUnit)
        }
    }

    @Test
    fun whenALineBreakHappensANewStoryUnitWithTheSameTypeShouldBeCreated_SimpleTest() =
        runTest {
            val now = Clock.System.now()

            val storyManager = WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = Dispatchers.Unconfined,
                userRepository = userRepository,
            )
            storyManager.loadDocument(
                Document(
                    content = singleMessageRepoLineBreak.history(),
                    workspaceId = "",
                    createdAt = now,
                    lastUpdatedAt = now,
                    parentId = "root",
                    lastSyncedAt = null,
                )
            )

            val stories = storyManager.currentStory.value.stories
            val initialSize = stories.size
            val position = 0

            storyManager.onLineBreak(Action.LineBreak(stories[position]!!, position = position))

            assertEquals(
                initialSize + 1,
                storyManager.currentStory.value.stories.size,
                "1 new story should have been added"
            )
        }

    @Test
    fun whenALineBreakHappensANewStoryUnitWithTheSameTypeShouldBeCreated_ComplexTest() =
        runTest {
            val now = Clock.System.now()

            val storyManager = WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
            storyManager.loadDocument(
                Document(
                    content = singleMessageRepoLineBreak.history(),
                    workspaceId = "",
                    createdAt = now,
                    lastUpdatedAt = now,
                    parentId = "root",
                    lastSyncedAt = null,
                )
            )

            val stories = storyManager.currentStory.value.stories
            val initialSize = stories.size
            val breakPosition = 0

            storyManager.onLineBreak(Action.LineBreak(stories[breakPosition]!!, breakPosition))

            val newStory = storyManager.currentStory.value.stories

            assertEquals(
                initialSize + 1,
                newStory.size,
                "2 new stories should have been added"
            )
        }

    @Test
    @Ignore
    fun complexMoveCase() = runTest {
        /**
         * Steps:
         * 1 - Make 3 single images into a group
         * - Check that the 3 images are in a group
         * 2 - Move one image away.
         * - Check that the correct image was moved correctly
         */
        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        storyManager.loadDocument(
            Document(
                content = complexMessagesRepository.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )

        val stories = storyManager.currentStory.value.stories

        val positionTo = 1
        val positionFrom = 3
        storyManager.mergeRequest(
            Action.Merge(
                receiver = stories[positionTo]!!,
                sender = stories[positionFrom]!!,
                positionFrom = positionFrom,
                positionTo = positionTo,
            )
        )

        val newStory = storyManager.currentStory.value.stories

        assertEquals(2, (newStory[1]!!).steps.size, "The images should have been merged")

        val stories2 = storyManager.currentStory.value.stories

        val positionTo2 = 1
        val positionFrom2 = 3
        storyManager.mergeRequest(
            Action.Merge(
                receiver = (stories2[positionTo2]!!).steps.first(),
                sender = stories2[positionFrom2]!!,
                positionFrom = positionFrom2,
                positionTo = positionTo2,
            )
        )

        val newStory2 = storyManager.currentStory.value.stories

        assertEquals(
            3,
            (newStory2[1]!!).steps.distinctBy { storyUnit -> storyUnit.localId }.size,
            "The images should have been merged"
        )

        val positionFrom3 = 1
        val positionTo3 = 4
        val storyToMove = (newStory[positionFrom3]!!).steps.first()
        storyManager.moveRequest(
            Action.Move(
                storyStep = storyToMove,
                positionFrom = positionFrom3,
                positionTo = positionTo3,
            )
        )

        val newStory3 = storyManager.currentStory.value.stories

        assertEquals(
            2,
            (newStory3[1]!!).steps.size,
            "One image should have been separated"
        )
        assertEquals(
            storyToMove.id,
            newStory3[5]!!.id,
            "The correct StoryUnit should have been moved"
        )
    }

    @Test
    fun itShouldBePossibleToAddContentAndUndoIt_OneUnit() {
        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(),
                userRepository = userRepository,
            )
        val input = MapStoryData.singleCheckItem()

        storyManager.loadDocument(
            Document(
                content = input,
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )
        val currentStory = storyManager.currentStory.value.stories

        storyManager.onLineBreak(Action.LineBreak(input[0]!!, 0))
        storyManager.undo()
        val newStory = storyManager.currentStory.value.stories

        assertEquals(currentStory.size, newStory.size)
    }

    @Test
    fun itShouldBePossibleToAddContentAndUndoIt_ManyUnits() = runTest {
        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        val input = MapStoryData.singleCheckItem()

        storyManager.loadDocument(
            Document(
                content = input,
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )
        val currentStory = storyManager.currentStory.value.stories

        storyManager.onLineBreak(Action.LineBreak(input[0]!!, 0))

        storyManager.onLineBreak(Action.LineBreak(input[0]!!, 1))
        storyManager.onLineBreak(Action.LineBreak(input[0]!!, 2))
        storyManager.undo()
        storyManager.undo()
        storyManager.undo()

        val newStory = storyManager.currentStory.value.stories

        assertEquals(
            currentStory.size,
            newStory.size,
            "The size of the story can't have changed"
        )

        currentStory.values.zip(newStory.values).forEach { (storyUnit1, storyUnit2) ->
            if (storyUnit1.type != storyUnit2.type) fail()

            if (storyUnit1.type != StoryTypes.SPACE.type &&
                storyUnit1.type != StoryTypes.LAST_SPACE.type
            ) {
                assertEquals(storyUnit1.id, storyUnit2.id)
            }
        }
    }

    @Test
    @Ignore // Async nature of selection should be considered in the test
    fun itShouldBePossibleToSelectMessages() = runTest {
        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        storyManager.loadDocument(
            Document(
                content = complexMessagesRepository.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )

        storyManager.onSelected(true, 1)
        storyManager.onSelected(true, 3)
        storyManager.onSelected(true, 5)

        assertEquals(setOf(1, 3, 5), storyManager.onEditPositions.value)
    }

    @Test
    @Ignore // Async nature of selection should be considered in the test
    fun itShouldBePossibleToDeleteSelectedMessages() = runTest {
        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        storyManager.loadDocument(
            Document(
                content = messagesRepo.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )

        val selectionCount = 3
        val selections = buildList {
            repeat(selectionCount) { index ->
                this.add(index * 2)
            }
        }

        selections.forEach { index ->
            storyManager.onSelected(true, index)
        }

        val initialStories = storyManager.currentStory.value.stories
        val initialSize = initialStories.size

        val selectedStories = selections.map { position ->
            initialStories[position]!!
        }

        storyManager.deleteSelection()

        val newStories = storyManager.currentStory.value.stories
        assertEquals(initialSize - selectionCount, newStories.size)

        selectedStories.forEach { storyStep ->
            assertFalse(
                newStories.values.map { it.id }.contains(storyStep.id),
                "The deleted story step should not be in the manager anymore"
            )
        }

        assertTrue(
            storyManager.onEditPositions.value.isEmpty(),
            "The selection should be empty now"
        )
    }

    @Test
    @Ignore // Async nature of selection should be considered in the test
    fun itShouldBePossibleToUndoBulkDeletion() = runTest {
        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        storyManager.loadDocument(
            Document(
                content = messagesRepo.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )

        val selectionCount = 3
        val selections = buildList {
            repeat(selectionCount) { index ->
                this.add(index)
            }
        }

        selections.forEach { index ->
            storyManager.onSelected(true, index)
        }

        val initialStories = storyManager.currentStory.value.stories
        val initialSize = initialStories.size

        val selectedStories = selections.map { position ->
            initialStories[position]!!
        }

        storyManager.deleteSelection()

        val newStories = storyManager.currentStory.value.stories
        assertEquals(initialSize - selectionCount, newStories.size)

        selectedStories.forEach { storyStep ->
            assertFalse(
                newStories.values.map { it.id }.contains(storyStep.id),
                "The deleted story step should not be in the manager anymore"
            )
        }

        assertTrue(
            storyManager.onEditPositions.value.isEmpty(),
            "The selection should be empty now"
        )

        assertEquals(newStories.size, initialStories.size - selectionCount)
        assertEquals(newStories.keys.toList(), initialStories.keys.take(newStories.size))

        storyManager.undo()
    }

    @Test
    fun whenClickingInTheLastPositionAMessageShouldBeAddedAtTheBottom() = runTest {
        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        storyManager.loadDocument(
            Document(
                content = imagesInLineRepo.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )

        storyManager.clickAtTheEnd()

        val currentStory = storyManager.currentStory.value.stories
        val lastContentStory = currentStory[currentStory.size - 1]

        assertEquals(lastContentStory!!.type, StoryTypes.TEXT.type)
        assertEquals(currentStory.size - 2, storyManager.currentStory.value.focus)
    }

    @Test
    @Ignore // Async nature of selection should be considered in the test
    fun itShouldBePossibleToAddBoldToStoriesBySelectingThem() = runTest {
        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        storyManager.loadDocument(
            Document(
                content = messagesRepo.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )

        repeat(3) { index ->
            storyManager.onSelected(true, index)
        }

        storyManager.toggleSpan(Span.BOLD)

        val stories = storyManager.currentStory.value.stories

        repeat(3) { i ->
            assertEquals(stories[i]!!.spans.first().span, Span.BOLD)
        }
    }

    @Test
    fun itShouldBePossibleToAddBoldToText() = runTest {
        val now = Clock.System.now()

        val storyManager =
            WriteopiaStateManager.create(
                writeopiaManager = WriteopiaManager(),
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                userRepository = userRepository,
            )
        storyManager.loadDocument(
            Document(
                content = messagesRepo.history(),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )

        val text = "this will be BOLD"

        storyManager.handleTextInput(
            TextInput(
                "this will be BOLD",
                start = 5,
                end = text.lastIndex,
                spans = emptySet()
            ),
            position = 0,
            lineBreakByContent = false
        )

        storyManager.toggleSpan(Span.BOLD)

        val stories = storyManager.currentStory.value.stories

        assertEquals(stories[0]!!.spans.first().span, Span.BOLD)
    }
}

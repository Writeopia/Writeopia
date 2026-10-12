@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package io.writeopia.writingagent.folders

import io.writeopia.ai.AiCommand
import io.writeopia.ai.AiStreaming
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.core.folders.repository.folder.InMemoryFolderRepository
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import io.writeopia.ai.task.AiTaskManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private class FakeFolderRepository(
    private val rootFolders: List<Folder>,
    delegate: FolderRepository = InMemoryFolderRepository(),
) : FolderRepository by delegate {
    val created = mutableListOf<Folder>()

    override suspend fun getFolderByParentId(parentId: String, workspaceId: String): List<Folder> =
        rootFolders.filter { it.parentId == parentId && it.workspaceId == workspaceId }

    override suspend fun createFolder(folder: Folder) {
        created.add(folder)
    }
}

class WriteopiaDocumentFoldersTest {

    private fun folder(id: String, title: String, deleted: Boolean = false, parentId: String = Folder.ROOT_PATH): Folder {
        val now = Clock.System.now()
        return Folder(
            id = id,
            parentId = parentId,
            title = title,
            createdAt = now,
            lastUpdatedAt = now,
            workspaceId = "ws",
            itemCount = 0,
            deleted = deleted,
        )
    }

    private fun ai(answer: String) = object : AiStreaming {
        override fun stream(command: AiCommand, prompt: String): Flow<ResultData<String>> =
            flowOf(ResultData.Complete(answer))
    }

    private fun subject(
        repository: FakeFolderRepository,
        ai: AiStreaming?,
        moved: MutableList<Pair<String, String>>,
    ) = WriteopiaDocumentFolders(
        documentId = { "doc" },
        documentParentId = { Folder.ROOT_PATH },
        resolveWorkspaceId = { "ws" },
        folderRepository = repository,
        moveDocument = { id, folderId -> moved.add(id to folderId) },
        resolveAi = { ai },
    )

    @Test
    fun `the candidates are the live root folders, at most the limit`() = runTest {
        val repository = FakeFolderRepository(
            listOf(
                folder("a", "Meetings"),
                folder("b", "Gone", deleted = true),
                folder("c", "Nested", parentId = "a"),
                folder("d", "Cooking"),
                folder("e", "Travel"),
            )
        )

        val candidates = subject(repository, ai = null, moved = mutableListOf()).candidates(limit = 2)

        assertEquals(listOf("Meetings", "Cooking"), candidates.map { it.name })
        assertEquals(listOf("a", "d"), candidates.map { it.id })
    }

    @Test
    fun `a new folder named by the AI is created at the root and the document moves there`() = runTest {
        val repository = FakeFolderRepository(emptyList())
        val moved = mutableListOf<Pair<String, String>>()

        val result = subject(repository, ai("\"Recipes\"\n"), moved).createFolderAndMove("Pasta", "Boil water.")

        assertEquals("Recipes", result.getOrNull())
        assertEquals(1, repository.created.size)
        assertEquals("Recipes", repository.created.single().title)
        assertEquals(Folder.ROOT_PATH, repository.created.single().parentId)
        assertEquals(listOf("doc" to repository.created.single().id), moved)
    }

    @Test
    fun `a task cancelled before it runs fails instead of waiting forever`() = runTest {
        // The manager has its own scheduler, which is never advanced: the task stays queued.
        val taskDispatcher = StandardTestDispatcher()
        val taskManager = AiTaskManager(dispatcher = taskDispatcher, scope = CoroutineScope(SupervisorJob() + taskDispatcher))
        val repository = FakeFolderRepository(emptyList())
        val folders = WriteopiaDocumentFolders(
            documentId = { "doc" },
            documentParentId = { Folder.ROOT_PATH },
            resolveWorkspaceId = { "ws" },
            folderRepository = repository,
            moveDocument = { _, _ -> },
            resolveAi = { ai("Recipes") },
            aiTaskManager = taskManager,
        )

        val outcome = async { folders.createFolderAndMove("Pasta", "Boil water.") }
        runCurrent()
        // The task is queued but hasn't run yet: cancel it, like Escape in the editor does.
        taskManager.cancelTask(taskManager.tasks.value.single().id)
        advanceUntilIdle()

        assertTrue(outcome.await().isFailure)
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `a task that runs completes with the folder`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val taskManager = AiTaskManager(dispatcher = dispatcher, scope = CoroutineScope(SupervisorJob() + dispatcher))
        val repository = FakeFolderRepository(emptyList())
        val moved = mutableListOf<Pair<String, String>>()
        val folders = WriteopiaDocumentFolders(
            documentId = { "doc" },
            documentParentId = { Folder.ROOT_PATH },
            resolveWorkspaceId = { "ws" },
            folderRepository = repository,
            moveDocument = { id, folderId -> moved.add(id to folderId) },
            resolveAi = { ai("Recipes") },
            aiTaskManager = taskManager,
        )

        val outcome = async { folders.createFolderAndMove("Pasta", "Boil water.") }
        advanceUntilIdle()

        assertEquals("Recipes", outcome.await().getOrNull())
        assertEquals(1, moved.size)
    }

    @Test
    fun `without an AI no folder is created`() = runTest {
        val repository = FakeFolderRepository(emptyList())
        val moved = mutableListOf<Pair<String, String>>()

        val result = subject(repository, ai = null, moved = moved).createFolderAndMove("Pasta", "Boil water.")

        assertTrue(result.isFailure)
        assertTrue(repository.created.isEmpty())
        assertTrue(moved.isEmpty())
    }
}

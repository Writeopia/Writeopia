package io.writeopia.notemenu.viewmodel

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.commonui.dtos.MenuItemUi
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** A drag and drop into a folder can be undone, putting the items back where they were. */
@OptIn(ExperimentalTime::class)
class FolderMoveUndoTest {

    private val notesUseCase: NotesUseCase = mockk(relaxed = true)
    private val authRepository: AuthRepository = mockk()
    private val workspaceId = Workspace.disconnectedWorkspace().id

    private lateinit var controller: FolderStateController

    @BeforeTest
    fun setUp() {
        FolderStateController.clearInstance()
        coEvery { authRepository.getWorkspace() } returns null
        coEvery { notesUseCase.getFolderById("target") } returns folder("target", "Target")
        controller = FolderStateController.singleton(notesUseCase, authRepository, mockk())
    }

    @AfterTest
    fun tearDown() {
        FolderStateController.clearInstance()
    }

    @Test
    fun `dropping a document tells where it went and undoing moves it back`() = runTest {
        controller.moveToFolder(document("doc", parentId = "previous"), "target")

        val move = awaitMove()

        assertEquals("Doc", move.itemTitle)
        assertEquals("Target", move.folderTitle)
        assertEquals(listOf(FolderMove.MovedItem("doc", isFolder = false, "previous")), move.items)

        controller.undoMove(move)

        assertNull(controller.lastMoveState.value)
        coVerify(timeout = 2_000) {
            notesUseCase.moveItemsById(listOf("doc"), workspaceId, "previous")
        }
    }

    @Test
    fun `undoing a dropped selection moves each item back to its own folder`() = runTest {
        coEvery { notesUseCase.moveItemsById(any(), any(), "target") } returns listOf(
            folder("a", "A", parentId = "first"),
            folder("b", "B", parentId = "second"),
        )
        controller.onDocumentSelected("a", true)
        controller.onDocumentSelected("b", true)

        controller.moveToFolder(MenuItemUi.FolderUi(documentId = "a", title = "A"), "target")

        val move = awaitMove()
        assertEquals(2, move.items.size)
        assertEquals(emptySet(), controller.selectedNotes.value)

        controller.undoMove(move)

        coVerify(timeout = 2_000) { notesUseCase.moveItemsById(listOf("a"), workspaceId, "first") }
        coVerify(timeout = 2_000) { notesUseCase.moveItemsById(listOf("b"), workspaceId, "second") }
    }

    @Test
    fun `a move is undone only once`() = runTest {
        controller.moveToFolder(document("doc", parentId = "previous"), "target")
        val move = awaitMove()

        controller.undoMove(move)
        controller.undoMove(move)

        coVerify(timeout = 2_000, exactly = 1) {
            notesUseCase.moveItemsById(listOf("doc"), workspaceId, "previous")
        }
    }

    @Test
    fun `a selected folder is not dropped inside itself`() = runTest {
        coEvery { notesUseCase.getFolderById("child") } returns folder("child", "Child", parentId = "a")
        controller.onDocumentSelected("a", true)

        controller.moveToFolder(MenuItemUi.FolderUi(documentId = "a", title = "A"), "child")

        coVerify(timeout = 2_000) { notesUseCase.getFolderById("child") }
        withContext(Dispatchers.Default) { delay(200) }
        coVerify(exactly = 0) { notesUseCase.moveItemsById(any(), any(), any()) }
        assertNull(controller.lastMoveState.value)
    }

    @Test
    fun `a dismissed move is forgotten`() = runTest {
        controller.moveToFolder(document("doc", parentId = "previous"), "target")
        val move = awaitMove()

        controller.dismissMove(move)

        assertNull(controller.lastMoveState.value)
    }

    // The controller works on Dispatchers.Default, so this waits in real time.
    private suspend fun awaitMove(): FolderMove = withContext(Dispatchers.Default) {
        withTimeout(2_000) { controller.lastMoveState.filterNotNull().first() }
    }

    private fun document(id: String, parentId: String) = MenuItemUi.DocumentUi(
        documentId = id,
        title = "Doc",
        selected = false,
        isFavorite = false,
        lastEdit = "",
        parentId = parentId,
        preview = emptyList(),
        highlighted = false,
        isSynced = false,
    )

    private fun folder(id: String, title: String, parentId: String = Folder.ROOT_PATH): Folder {
        val now = Clock.System.now()
        return Folder(
            id = id,
            parentId = parentId,
            title = title,
            createdAt = now,
            lastUpdatedAt = now,
            workspaceId = workspaceId,
            itemCount = 0,
        )
    }
}

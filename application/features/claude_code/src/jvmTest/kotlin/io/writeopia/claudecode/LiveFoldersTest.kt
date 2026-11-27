package io.writeopia.claudecode

import io.writeopia.claudecode.mcp.FolderTree
import io.writeopia.claudecode.mcp.LiveFolder
import io.writeopia.claudecode.mcp.McpHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private class FakeFolders(private val current: String = "root") : LiveFolders {
    val made = mutableListOf<Pair<String, String>>()
    private val known = mutableSetOf("root", "work")

    override fun currentFolderId(): String = current

    override suspend fun exists(folderId: String): Boolean = folderId in known

    override suspend fun create(name: String, parentId: String): String {
        val id = "folder${made.size + 1}"
        made.add(name to parentId)
        known.add(id)
        return id
    }

    override suspend fun list(): List<LiveFolder> = listOf(
        LiveFolder("work", "Work", "root"),
        LiveFolder("b", "beta", "work"),
        LiveFolder("a", "Alpha", "work"),
        LiveFolder("orphan", "Lost", "gone"),
    ) + made.mapIndexed { index, (name, parent) -> LiveFolder("folder${index + 1}", name, parent) }
}

class LiveFoldersTest {

    @BeforeTest
    fun setUp() {
        ClaudeCodeBridge.reset()
        ClaudeCodeBridge.idleTimeout = 60.seconds
    }

    @AfterTest
    fun tearDown() {
        ClaudeCodeBridge.reset()
    }

    private fun handler(folders: LiveFolders) = McpHandler(
        LiveDocuments(openTimeout = 50.milliseconds, isLoading = MutableStateFlow(false), folders = folders),
        serverVersion = "test"
    )

    private fun call(tool: String, arguments: String) =
        """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"$tool","arguments":$arguments}}"""

    @Test
    fun `a folder goes in the folder open in the app and is shown`() = runTest {
        val folders = FakeFolders(current = "work")
        val shown = mutableListOf<ClaudeCodeBridge.OpenRequest>()
        val listening = launch(UnconfinedTestDispatcher(testScheduler)) {
            ClaudeCodeBridge.openRequests.collect(shown::add)
        }

        val answer = handler(folders).handle(call("create_folder", """{"name":" Recipes "}"""))!!

        listening.cancel()
        assertEquals(listOf("Recipes" to "work"), folders.made)
        assertEquals(listOf<ClaudeCodeBridge.OpenRequest>(ClaudeCodeBridge.OpenRequest.Folder("folder1")), shown)
        assertTrue(answer.contains("(folder_id: folder1) inside work"))
        assertTrue(answer.contains("\"isError\":false"))
    }

    @Test
    fun `a folder inside one made before`() = runTest {
        val folders = FakeFolders()
        val handler = handler(folders)

        handler.handle(call("create_folder", """{"name":"Trips"}"""))
        handler.handle(call("create_folder", """{"name":"2026","parent_folder_id":"folder1"}"""))

        assertEquals(listOf("Trips" to "root", "2026" to "folder1"), folders.made)
    }

    @Test
    fun `unknown folders are tool errors and nothing is made`() = runTest {
        val folders = FakeFolders()
        val handler = handler(folders)

        val badParent = handler.handle(call("create_folder", """{"name":"X","parent_folder_id":"nope"}"""))!!
        val badFolder = handler.handle(call("create_document", """{"title":"T","folder_id":"nope"}"""))!!

        assertTrue(badParent.contains("\"isError\":true") && badParent.contains("no folder with the id nope"))
        assertTrue(badFolder.contains("\"isError\":true") && badFolder.contains("no folder with the id nope"))
        assertTrue(folders.made.isEmpty())
    }

    @Test
    fun `a document in a folder asks the app for a new document there`() = runTest {
        val shown = mutableListOf<ClaudeCodeBridge.OpenRequest>()
        val listening = launch(UnconfinedTestDispatcher(testScheduler)) {
            ClaudeCodeBridge.openRequests.collect(shown::add)
        }

        // No editor opens in the test, so the call fails after asking; what matters is the request.
        handler(FakeFolders()).handle(call("create_document", """{"title":"T","folder_id":"work"}"""))

        listening.cancel()
        assertEquals(ClaudeCodeBridge.OpenRequest.NewDocument("work"), shown.first())
    }

    @Test
    fun `ai mode is on while the folder is made`() = runTest {
        handler(FakeFolders()).handle(call("create_folder", """{"name":"A"}"""))

        assertEquals(ClaudeCodeBridge.AiMode.WAITING, ClaudeCodeBridge.aiMode.first())
    }

    @Test
    fun `list_folders answers the tree with the open folder, without ai mode`() = runTest {
        val answer = handler(FakeFolders(current = "work")).handle(call("list_folders", "{}"))!!

        assertTrue(answer.contains("Lost (folder_id: orphan)"))
        assertTrue(answer.contains("The app has \\\"Work\\\" (folder_id: work) open"))
        assertEquals(ClaudeCodeBridge.AiMode.OFF, ClaudeCodeBridge.aiMode.value)
    }

    @Test
    fun `the tree sorts siblings by name and indents children`() {
        val tree = FolderTree.describe(
            listOf(
                LiveFolder("work", "Work", "root"),
                LiveFolder("b", "beta", "work"),
                LiveFolder("a", "Alpha", "work"),
                LiveFolder("orphan", "Lost", "gone"),
            ),
            currentFolderId = "root",
        )

        assertEquals(
            """
            Folders in Writeopia (4):
            - Lost (folder_id: orphan)
            - Work (folder_id: work)
              - Alpha (folder_id: a)
              - beta (folder_id: b)

            The app has the top level open: new documents and folders go there unless you pass a folder_id.
            """.trimIndent(),
            tree
        )
    }

    @Test
    fun `no folders yet`() {
        assertEquals(
            "Writeopia has no folders yet: everything is in the top level. Make one with create_folder.",
            FolderTree.describe(emptyList(), "root")
        )
    }
}

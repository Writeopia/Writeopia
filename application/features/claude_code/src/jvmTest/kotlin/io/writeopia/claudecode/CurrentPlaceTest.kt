package io.writeopia.claudecode

import io.writeopia.claudecode.mcp.DocumentSnapshot
import io.writeopia.claudecode.mcp.LiveFolder
import io.writeopia.claudecode.mcp.McpHandler
import io.writeopia.claudecode.mcp.OpenDocument
import io.writeopia.claudecode.mcp.PlaceChange
import io.writeopia.claudecode.mcp.PlaceReport
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.model.action.Action
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

/** The same report cases as `CurrentPlaceTests.swift`: both apps answer word for word. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class CurrentPlaceTest {

    private val attached = mutableListOf<WriteopiaStateManager>()

    @BeforeTest
    fun setUp() {
        Rank.jitter = false
        ClaudeCodeBridge.reset()
    }

    @AfterTest
    fun tearDown() {
        attached.forEach(ClaudeCodeBridge::detach)
        ClaudeCodeBridge.reset()
    }

    private val trips = LiveFolder("f1", "Trips", "root")
    private val year = LiveFolder("f2", "2026", "f1")

    @Test
    fun `a document with its folder path, tags and content`() {
        val text = PlaceReport.describe(
            OpenDocument("d1", "Lisbon", listOf(trips, year), listOf("Food", "Travel"), isLocked = false, hasHeaderPicture = true),
            currentFolder = year,
            change = PlaceChange.Edited(added = 2, changed = 1, removed = 0),
            content = "# Lisbon\n\nDay one\n",
        )

        assertEquals(
            "Writeopia shows the document \"Lisbon\" (document_id: d1) in Trips / 2026 (folder_id: f2).\n" +
                "Tags: Food, Travel. Locked: no. Header picture: yes.\n\n" +
                "Since your last call, the person changed this document: 2 blocks added, 1 changed, 0 removed.\n\n" +
                "Content (Markdown):\n# Lisbon\n\nDay one",
            text
        )
    }

    @Test
    fun `no document open, at the top level or in a folder, after leaving one`() {
        assertEquals(
            "No document is open in Writeopia. The app has the top level open.",
            PlaceReport.describe(null, null, null, null)
        )
        assertEquals(
            "No document is open in Writeopia. The app has \"Trips\" (folder_id: f1) open.\n\n" +
                "Since your last call, the person left \"Lisbon\" (document_id: d1).",
            PlaceReport.describe(null, trips, PlaceChange.Moved("d1", "Lisbon"), null)
        )
    }

    @Test
    fun `a document at the top level, unchanged, empty, and a long one cut`() {
        val doc = OpenDocument("d1", "Notes", emptyList(), emptyList(), isLocked = true, hasHeaderPicture = false)

        assertEquals(
            "Writeopia shows the document \"Notes\" (document_id: d1) in the top level.\n" +
                "Tags: none. Locked: yes. Header picture: no.\n\n" +
                "Since your last call, nothing changed here.\n\nContent: empty.",
            PlaceReport.describe(doc, null, PlaceChange.Unchanged, "  ")
        )
        val long = PlaceReport.describe(doc, null, null, "a".repeat(PlaceReport.CONTENT_LIMIT + 5))
        assertTrue(long.endsWith("\n…(cut: 5 more characters)"))
    }

    @Test
    fun `changes count blocks added, changed and removed, or a move`() {
        val before = DocumentSnapshot("d1", "Lisbon", mapOf("a" to "1", "b" to "2", "c" to "3"))

        assertEquals(
            PlaceChange.Edited(added = 1, changed = 1, removed = 1),
            before.changeTo(DocumentSnapshot("d1", "Lisbon", mapOf("a" to "1", "b" to "2!", "d" to "4")))
        )
        assertEquals(PlaceChange.Unchanged, before.changeTo(before))
        assertEquals(PlaceChange.Moved("d1", "Lisbon"), before.changeTo(DocumentSnapshot("d2", "Other", emptyMap())))
        assertEquals(PlaceChange.Moved("d1", "Lisbon"), before.changeTo(null))
    }

    private fun editor(id: String, title: String): WriteopiaStateManager {
        val now = Clock.System.now()
        val manager = WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = UnconfinedTestDispatcher(),
        ).apply {
            loadDocument(
                Document(
                    id = id,
                    content = mapOf(
                        Rank.at(0) to StoryStep(id = "$id-t", type = StoryTypes.TITLE.type, text = title),
                        Rank.at(1) to StoryStep(id = "$id-p", type = StoryTypes.TEXT.type, text = "Hello"),
                    ),
                    workspaceId = "",
                    createdAt = now,
                    lastUpdatedAt = now,
                    parentId = "root",
                    lastSyncedAt = null,
                )
            )
        }
        ClaudeCodeBridge.attach(manager)
        attached.add(manager)
        return manager
    }

    private suspend fun McpHandler.call(tool: String, arguments: String = "{}") =
        handle("""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"$tool","arguments":$arguments}}""")!!

    @Test
    fun `the open document, then what the person changed, then a switch to another one`() = runTest {
        val handler = McpHandler(
            LiveDocuments(openTimeout = 50.milliseconds, isLoading = MutableStateFlow(false)),
            serverVersion = "test"
        )
        val lisbon = editor("d1", "Lisbon")

        val first = handler.call("get_current_place")
        assertTrue(first.contains("Writeopia shows the document \\\"Lisbon\\\" (document_id: d1)"), first)
        assertTrue(first.contains("Hello"), first)
        assertTrue(!first.contains("Since your last call"), first)

        lisbon.changeStoryState(
            Action.StoryStateChange(StoryStep(id = "d1-p", type = StoryTypes.TEXT.type, text = "Hello there"), Rank.at(1))
        )
        val second = handler.call("get_current_place", """{"include_content":false}""")
        assertTrue(
            second.contains("1 blocks added, 0 changed, 0 removed") || second.contains("0 blocks added, 1 changed, 0 removed"),
            second
        )
        assertTrue(!second.contains("Content"), second)

        // Mid-session (AI mode on), the person opens another document: no writing there unasked.
        ClaudeCodeBridge.beginWriting()?.let(ClaudeCodeBridge::endWriting)
        editor("d2", "Porto")
        val append = handler.call("append_to_document", """{"markdown":"More"}""")
        assertTrue(append.contains("\"isError\":true"), append)
        assertTrue(append.contains("The person switched documents: Writeopia shows \\\"Porto\\\" (document_id: d2)"), append)
    }

    @Test
    fun `a change of formatting only counts as a changed block`() = runTest {
        val handler = McpHandler(
            LiveDocuments(openTimeout = 50.milliseconds, isLoading = MutableStateFlow(false)),
            serverVersion = "test"
        )
        val lisbon = editor("d1", "Lisbon")
        handler.call("get_current_place", """{"include_content":false}""")

        lisbon.changeStoryState(
            Action.StoryStateChange(
                StoryStep(
                    id = "d1-p",
                    type = StoryTypes.TEXT.type,
                    text = "Hello",
                    spans = setOf(SpanInfo.create(0, 5, Span.BOLD)),
                ),
                Rank.at(1)
            )
        )
        val report = handler.call("get_current_place", """{"include_content":false}""")

        assertTrue(report.contains("0 blocks added, 1 changed, 0 removed"), report)
        val again = handler.call("get_current_place", """{"include_content":false}""")
        assertTrue(again.contains("nothing changed here"), again)
    }
}

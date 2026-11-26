package io.writeopia.claudecode

import io.writeopia.claudecode.mcp.McpHandler
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.Decoration
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class HeaderImageTest {

    private var attached: WriteopiaStateManager? = null

    @BeforeTest
    fun setUp() {
        Rank.jitter = false
        ClaudeCodeBridge.reset()
    }

    @AfterTest
    fun tearDown() {
        attached?.let(ClaudeCodeBridge::detach)
        ClaudeCodeBridge.reset()
    }

    private fun editor(
        title: StoryStep,
        painter: ClaudeCodeBridge.HeaderPainter? = null,
    ): WriteopiaStateManager {
        val now = Clock.System.now()
        val manager = WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = UnconfinedTestDispatcher(),
        ).apply {
            loadDocument(
                Document(
                    id = "doc",
                    content = mapOf(Rank.at(0) to title, Rank.at(1) to StoryStep(type = StoryTypes.TEXT.type, text = "Body")),
                    workspaceId = "",
                    createdAt = now,
                    lastUpdatedAt = now,
                    parentId = "root",
                    lastSyncedAt = null,
                )
            )
        }
        ClaudeCodeBridge.attach(manager, painter)
        attached = manager
        return manager
    }

    private suspend fun call(arguments: String) = McpHandler(
        LiveDocuments(openTimeout = 50.milliseconds, isLoading = MutableStateFlow(false)),
        serverVersion = "test"
    ).handle(
        """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"set_header_image","arguments":$arguments}}"""
    )!!

    @Test
    fun `the picture goes in the header and replaces its color`() = runTest {
        var asked: String? = null
        val manager = editor(
            StoryStep(id = "t", type = StoryTypes.TITLE.type, text = "Trip", decoration = Decoration(backgroundColor = 42)),
            painter = { instructions ->
                asked = instructions
                Result.success("https://cdn/p.png")
            },
        )

        val answer = call("""{"description":" a lighthouse at dusk "}""")

        val title = manager.getStory(Rank.at(0))!!
        assertEquals("https://cdn/p.png", title.url)
        assertNull(title.decoration.backgroundColor)
        assertEquals("a lighthouse at dusk", asked)
        assertTrue(answer.contains("Put a header picture in \\\"Trip\\\" (document_id: doc)"))
    }

    @Test
    fun `a header that has a picture is never replaced`() = runTest {
        var drew = false
        val manager = editor(
            StoryStep(id = "t", type = StoryTypes.TITLE.type, text = "Trip", url = "https://cdn/old.png"),
            painter = {
                drew = true
                Result.success("https://cdn/new.png")
            },
        )

        val answer = call("{}")

        assertTrue(answer.contains("\"isError\":true") && answer.contains("already has a header picture"))
        assertEquals("https://cdn/old.png", manager.getStory(Rank.at(0))!!.url)
        assertTrue(!drew)
    }

    @Test
    fun `a picture the cloud can't draw is a tool error with the reason`() = runTest {
        editor(
            StoryStep(id = "t", type = StoryTypes.TITLE.type, text = "Trip"),
            painter = { Result.failure(Exception("Header pictures are drawn by the Writeopia cloud")) },
        )

        val answer = call("{}")

        assertTrue(answer.contains("\"isError\":true") && answer.contains("drawn by the Writeopia cloud"))
    }
}

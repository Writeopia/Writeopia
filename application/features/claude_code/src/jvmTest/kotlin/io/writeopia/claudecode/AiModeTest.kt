package io.writeopia.claudecode

import io.writeopia.claudecode.ClaudeCodeBridge.AiMode
import io.writeopia.claudecode.markdown.LiveMarkdown
import io.writeopia.claudecode.mcp.McpHandler
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.ui.manager.WriteopiaStateManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class AiModeTest {

    @BeforeTest
    fun setUp() {
        Rank.jitter = false
        ClaudeCodeBridge.reset()
        ClaudeCodeBridge.idleTimeout = 100.milliseconds
        ClaudeCodeBridge.stopCooldown = 60.seconds
    }

    @AfterTest
    fun tearDown() {
        ClaudeCodeBridge.reset()
        ClaudeCodeBridge.idleTimeout = 60.seconds
    }

    private fun manager(vararg steps: StoryStep): WriteopiaStateManager {
        val now = Clock.System.now()
        return WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = UnconfinedTestDispatcher(),
        ).apply {
            loadDocument(
                Document(
                    id = "doc",
                    content = steps.withIndex().associate { (index, step) -> Rank.at(index) to step },
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
    fun `ai mode is on while writing, waits between calls, then ends by itself`() = runBlocking {
        val ticket = assertNotNull(ClaudeCodeBridge.beginWriting())
        assertEquals(AiMode.WRITING, ClaudeCodeBridge.aiMode.value)

        ClaudeCodeBridge.endWriting(ticket)
        assertEquals(AiMode.WAITING, ClaudeCodeBridge.aiMode.value)

        withTimeout(2.seconds) { ClaudeCodeBridge.aiMode.first { it == AiMode.OFF } }
        Unit
    }

    @Test
    fun `stopping ends ai mode and refuses the next calls for a while`() {
        val ticket = assertNotNull(ClaudeCodeBridge.beginWriting())

        ClaudeCodeBridge.stop()

        assertEquals(AiMode.OFF, ClaudeCodeBridge.aiMode.value)
        assertTrue(ClaudeCodeBridge.isStopped(ticket))
        ClaudeCodeBridge.endWriting(ticket)
        assertEquals(AiMode.OFF, ClaudeCodeBridge.aiMode.value)
        assertNull(ClaudeCodeBridge.beginWriting())

        ClaudeCodeBridge.stopCooldown = 0.seconds
        assertNotNull(ClaudeCodeBridge.beginWriting())
    }

    @Test
    fun `a stop in the middle of typing keeps and saves what was typed`() = runTest {
        val manager = manager(StoryStep(id = "title", type = StoryTypes.TITLE.type, text = "Notes"))
        var ticks = 0

        val done = LiveWriter(tick = 1.milliseconds, minCharsPerTick = 3).write(
            manager,
            null,
            LiveMarkdown.blocks("Hello world, this is long\nNever written"),
        ) { ++ticks > 3 }

        assertFalse(done)
        val steps = manager.currentStory.value.stories.values.filter { it.type != StoryTypes.SPACE.type }
        // One check before the block, then one per tick of 3 characters: the 4th stops at "Hello ".
        assertEquals(listOf("Notes", "Hello "), steps.map { it.text })
        assertTrue(steps.none { it.ephemeral })
    }

    @Test
    fun `a call after a stop tells Claude to stop`() = runTest {
        ClaudeCodeBridge.beginWriting()
        ClaudeCodeBridge.stop()
        val handler = McpHandler(
            LiveDocuments(openTimeout = 50.milliseconds, isLoading = MutableStateFlow(false)),
            serverVersion = "test"
        )

        val answer = handler.handle(
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"append_to_document","arguments":{"markdown":"Hi"}}}"""
        )!!

        assertTrue(answer.contains("\"isError\":true"))
        assertTrue(answer.contains("pressed Stop"))
    }

    @Test
    fun `finish_writing ends ai mode at once and the next call can write again`() = runTest {
        ClaudeCodeBridge.idleTimeout = 60.seconds
        val ticket = assertNotNull(ClaudeCodeBridge.beginWriting())
        ClaudeCodeBridge.endWriting(ticket)
        assertEquals(AiMode.WAITING, ClaudeCodeBridge.aiMode.value)

        val answer = McpHandler(LiveDocuments(isLoading = MutableStateFlow(false)), serverVersion = "test").handle(
            """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"finish_writing","arguments":{}}}"""
        )!!

        assertTrue(answer.contains("Writeopia left AI mode"))
        assertTrue(answer.contains("\"isError\":false"))
        assertEquals(AiMode.OFF, ClaudeCodeBridge.aiMode.value)
        assertNotNull(ClaudeCodeBridge.beginWriting())
    }
}

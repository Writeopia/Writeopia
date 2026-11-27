package io.writeopia.claudecode

import io.writeopia.claudecode.mcp.McpHandler
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.models.document.Document
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

private class FakeTags : LiveTags {
    val catalogue = mutableListOf(LiveTag("t-travel", "Travel", "Trips and places"), LiveTag("t-food", "Food"))

    override suspend fun list(): List<LiveTag> = catalogue.toList()

    override suspend fun findOrCreate(name: String): Pair<LiveTag, Boolean> {
        catalogue.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it to false }
        val tag = LiveTag("t-${name.lowercase()}", name)
        catalogue.add(tag)
        return tag to true
    }
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class TagsTest {

    private var attached: WriteopiaStateManager? = null
    private val tags = FakeTags()

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

    private fun editor(tagIds: Set<String> = emptySet()): WriteopiaStateManager {
        val now = Clock.System.now()
        val manager = WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = UnconfinedTestDispatcher(),
        ).apply {
            loadDocument(
                Document(
                    id = "doc",
                    content = mapOf(Rank.at(0) to StoryStep(type = StoryTypes.TITLE.type, text = "Lisbon")),
                    workspaceId = "",
                    createdAt = now,
                    lastUpdatedAt = now,
                    parentId = "root",
                    lastSyncedAt = null,
                    tagIds = tagIds,
                )
            )
        }
        ClaudeCodeBridge.attach(manager)
        attached = manager
        return manager
    }

    private suspend fun call(tool: String, arguments: String) = McpHandler(
        LiveDocuments(openTimeout = 50.milliseconds, isLoading = MutableStateFlow(false), tags = tags),
        serverVersion = "test"
    ).handle("""{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"$tool","arguments":$arguments}}""")!!

    @Test
    fun `existing tags are reused by name, new ones made, and the document keeps its tags`() = runTest {
        val manager = editor(tagIds = setOf("t-food"))

        val answer = call("add_tags", """{"tags":["travel"," Portugal ","FOOD","portugal"]}""")

        assertEquals(setOf("t-food", "t-travel", "t-portugal"), manager.documentTagIds.value)
        assertEquals(3, tags.catalogue.size)
        assertTrue(
            answer.contains(
                "Tagged \\\"Lisbon\\\" (document_id: doc) with: Travel, Portugal (new). It already had: Food."
            ),
            answer
        )
    }

    @Test
    fun `list_tags shows the catalogue by name with descriptions`() = runTest {
        val answer = call("list_tags", "{}")

        assertTrue(answer.contains("Tags of the workspace (2):\\n- Food\\n- Travel: Trips and places"), answer)
    }

    @Test
    fun `tags must be a list of names`() = runTest {
        editor()

        val missing = call("add_tags", "{}")
        val blank = call("add_tags", """{"tags":[" "]}""")

        assertTrue(missing.contains("\"isError\":true"))
        assertTrue(blank.contains("Give at least one tag name."))
    }
}

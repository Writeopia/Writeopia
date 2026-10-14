@file:OptIn(ExperimentalTime::class, ExperimentalTestApi::class)

package io.writeopia.ui.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.ui.WriteopiaEditor
import io.writeopia.ui.drawer.factory.DefaultDrawersDesktop
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawState
import kotlin.test.Test
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The desktop editor, as the app mounts it, receiving a pasted address in an empty line.
 */
class LinkCardEditorUiTest {

    private fun managerWithEmptyLine(): WriteopiaStateManager {
        val now = Clock.System.now()
        val manager = WriteopiaStateManager.create(writeopiaManager = WriteopiaManager())
        manager.loadDocument(
            Document(
                content = mapOf(
                    0.0 to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
                    1.0 to StoryStep(type = StoryTypes.TEXT.type, text = "")
                ),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
            )
        )
        return manager
    }

    @Test
    fun `pasting an address in an empty line shows a link card in the editor`() = runComposeUiTest {
        val manager = managerWithEmptyLine()

        setContent {
            MaterialTheme {
                val drawState by manager.toDraw.collectAsState(DrawState())
                WriteopiaEditor(
                    drawers = DefaultDrawersDesktop.create(
                        manager = manager,
                        editable = true,
                        aiExplanation = "AI",
                        isDarkTheme = false,
                        onHeaderClick = {},
                        drawConfig = io.writeopia.ui.model.DrawConfig(),
                        fontFamily = null,
                        generateSection = {},
                        receiveExternalFile = { _, _ -> },
                        onDocumentLinkClick = {},
                        linkLeadingIcon = null,
                        equationToImageUrl = null,
                        customDrawers = emptyMap()
                    ),
                    storyState = drawState
                )
            }
        }

        onNodeWithTag("MessageDrawer_1.0").assertIsDisplayed()

        // A paste delivers the whole address in one input.
        onNodeWithTag("MessageDrawer_1.0").performTextInput("https://writeopia.io")
        waitForIdle()

        onNodeWithTag("LinkCard_1.0").assertIsDisplayed()
        onNodeWithText("https://writeopia.io").assertIsDisplayed()
        onNodeWithTag("LinkCardCopy_1.0").assertIsDisplayed()
    }
}

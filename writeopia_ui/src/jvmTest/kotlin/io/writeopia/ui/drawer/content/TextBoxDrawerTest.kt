@file:OptIn(ExperimentalTestApi::class, ExperimentalTime::class)

package io.writeopia.ui.drawer.content

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.Decoration
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.ui.WriteopiaEditor
import io.writeopia.ui.drawer.factory.DefaultDrawersDesktop
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawConfig
import io.writeopia.ui.model.DrawState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The "message_box" type had no drawer, so a step of that type drew nothing. It now draws its text
 * on the colour of its decoration and stays editable.
 */
class TextBoxDrawerTest {

    private fun managerWithTextBox(
        text: String,
        backgroundColor: Int? = null,
    ): WriteopiaStateManager {
        val now = Clock.System.now()
        val manager = WriteopiaStateManager.create(writeopiaManager = WriteopiaManager())
        manager.loadDocument(
            Document(
                content = mapOf(
                    Rank.at(0) to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
                    Rank.at(1) to StoryStep(
                        type = StoryTypes.TEXT_BOX.type,
                        text = text,
                        decoration = Decoration(backgroundColor = backgroundColor),
                    ),
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
    fun `a message box step is drawn with its text`() = runComposeUiTest {
        val manager = managerWithTextBox("box text")

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
                        drawConfig = DrawConfig(),
                        fontFamily = null,
                        generateSection = {},
                        receiveExternalFile = { _, _ -> },
                        onDocumentLinkClick = {},
                        linkLeadingIcon = null,
                        equationToImageUrl = null,
                        customDrawers = emptyMap(),
                    ),
                    storyState = drawState,
                )
            }
        }

        onNodeWithTag("MessageDrawer_${Rank.at(1)}").assertIsDisplayed()
        onNodeWithText("box text").assertIsDisplayed()
    }

    @Test
    fun `typing in a message box updates its text`() = runComposeUiTest {
        val manager = managerWithTextBox("box")

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
                        drawConfig = DrawConfig(),
                        fontFamily = null,
                        generateSection = {},
                        receiveExternalFile = { _, _ -> },
                        onDocumentLinkClick = {},
                        linkLeadingIcon = null,
                        equationToImageUrl = null,
                        customDrawers = emptyMap(),
                    ),
                    storyState = drawState,
                )
            }
        }

        onNodeWithTag("MessageDrawer_${Rank.at(1)}").performTextInput("new ")
        waitForIdle()

        val text = manager.getDocument().content[Rank.at(1)]?.text
        assertEquals("new box", text)
    }

    @Test
    fun `the colour of a message box comes from the decoration`() {
        assertEquals(
            Color(0xFF102030.toInt()),
            textBoxColor(0xFF102030.toInt(), Color.White),
        )
        assertEquals(Color.White, textBoxColor(null, Color.White))
    }
}

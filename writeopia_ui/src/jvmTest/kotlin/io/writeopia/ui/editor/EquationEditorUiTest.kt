@file:OptIn(ExperimentalTime::class, ExperimentalTestApi::class)

package io.writeopia.ui.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.ui.WriteopiaEditor
import io.writeopia.ui.drawer.factory.DefaultDrawersDesktop
import io.writeopia.ui.manager.WriteopiaStateManager
import io.writeopia.ui.model.DrawState
import io.writeopia.ui.model.EquationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The desktop editor drawing an equation that has no picture yet. */
class EquationEditorUiTest {

    private fun managerWithEquation(): WriteopiaStateManager {
        val now = Clock.System.now()
        val manager = WriteopiaStateManager.create(writeopiaManager = WriteopiaManager())
        manager.loadDocument(
            Document(
                content = mapOf(
                    Rank.at(0) to StoryStep(type = StoryTypes.TITLE.type, text = "Title"),
                    Rank.at(1) to StoryStep(type = StoryTypes.EQUATION.type, text = "\\frac{a}{b}")
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
    fun `an equation without a picture shows its LaTeX and the pencil asks the app to edit it`() = runComposeUiTest {
        val manager = managerWithEquation()
        val events = mutableListOf<EquationEvent>()
        CoroutineScope(Dispatchers.Unconfined).launch { manager.equationEvents.collect { events.add(it) } }

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
                        customDrawers = emptyMap()
                    ),
                    storyState = drawState
                )
            }
        }

        onNodeWithTag("Equation_${Rank.at(1)}").assertIsDisplayed()
        onNodeWithText("\\frac{a}{b}").assertIsDisplayed()

        // On desktop the pencil only shows while the row is hovered.
        onNodeWithTag("Equation_${Rank.at(1)}").performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithTag("EquationEdit_${Rank.at(1)}", useUnmergedTree = true).assertIsDisplayed().performClick()
        waitForIdle()

        val event = assertIs<EquationEvent.Edit>(events.single())
        assertEquals(Rank.at(1), event.position)
        assertEquals("\\frac{a}{b}", event.step.text)
    }
}

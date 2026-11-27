package io.writeopia.notes.desktop.tabs

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.repository.DocumentRepository
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class DesktopTabsUiTest {

    // A root tab's title needs no repository.
    private val titles = TabTitles(
        documentRepository = { error("not used") as DocumentRepository },
        folderRepository = { error("not used") as FolderRepository },
    )

    @Test
    fun theNewTabButtonShowsWithASingleTab() = runComposeUiTest {
        val tabs = DesktopTabsState()
        setContent { DesktopTabStrip(tabsState = tabs, titles = titles) }

        onNodeWithText("Home").assertIsDisplayed()
        onNodeWithContentDescription("New tab").assertIsDisplayed()

        onNodeWithContentDescription("New tab").performClick()
        waitForIdle()

        assertEquals(2, tabs.tabs.value.size)
        assertEquals(2, onAllNodesWithText("Home").fetchSemanticsNodes().size)
        onNodeWithContentDescription("New tab").assertIsDisplayed()
    }
}

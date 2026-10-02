package io.writeopia.notemenu.ui.screen.menu

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.writeopia.sdk.models.document.Folder
import io.writeopia.theme.WriteopiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTestApi::class, ExperimentalTime::class)
class FolderBreadcrumbUiTest {

    private val work = folder("work", Folder.ROOT_PATH, "Work")
    private val projects = folder("projects", "work", "Projects")
    private val notes = folder("notes", "projects", "Notes")

    @Test
    fun `the root, the ancestors and the current folder are shown in order`() = runComposeUiTest {
        setContent {
            WriteopiaTheme {
                FolderBreadcrumb(path = listOf(work, projects, notes), onSelect = {})
            }
        }

        onNodeWithTag(FOLDER_BREADCRUMB_TEST_TAG).assertIsDisplayed()
        onNodeWithText("Home").assertIsDisplayed().assertHasClickAction()
        onNodeWithText("Work").assertIsDisplayed().assertHasClickAction()
        onNodeWithText("Projects").assertIsDisplayed().assertHasClickAction()
        // The folder shown is where the user already is, so it can't be tapped.
        onNodeWithText("Notes").assertIsDisplayed().assertHasNoClickAction()
    }

    @Test
    fun `tapping an ancestor selects it and tapping home selects the root`() = runComposeUiTest {
        val selected = mutableListOf<Folder?>()

        setContent {
            WriteopiaTheme {
                FolderBreadcrumb(path = listOf(work, projects, notes), onSelect = selected::add)
            }
        }

        onNodeWithText("Projects").performClick()
        onNodeWithText("Work").performClick()
        onNodeWithText("Home").performClick()

        assertEquals(listOf("projects", "work"), selected.take(2).map { folder -> folder?.id })
        assertNull(selected[2])
    }

    @Test
    fun `at the root only home is shown and it can't be tapped`() = runComposeUiTest {
        setContent {
            WriteopiaTheme {
                FolderBreadcrumb(path = emptyList(), onSelect = {})
            }
        }

        onNodeWithText("Home").assertIsDisplayed().assertHasNoClickAction()
    }

    private fun folder(id: String, parentId: String, title: String): Folder {
        val now = Clock.System.now()
        return Folder(
            id = id,
            parentId = parentId,
            title = title,
            createdAt = now,
            lastUpdatedAt = now,
            workspaceId = "workspace",
            itemCount = 0,
        )
    }
}

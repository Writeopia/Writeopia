@file:OptIn(ExperimentalTime::class)

package io.writeopia.ui.manager

import io.writeopia.sdk.manager.WriteopiaManager
import io.writeopia.sdk.model.story.LastEdit
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.utils.iterables.Rank
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DocumentIconStateManagerTest {

    private fun manager(icon: MenuItem.Icon? = null): WriteopiaStateManager {
        val now = Clock.System.now()
        val manager = WriteopiaStateManager.create(
            writeopiaManager = WriteopiaManager(),
            dispatcher = UnconfinedTestDispatcher(),
        )
        manager.loadDocument(
            Document(
                content = mapOf(Rank.at(0) to StoryStep(type = StoryTypes.TITLE.type, text = "Title")),
                workspaceId = "",
                createdAt = now,
                lastUpdatedAt = now,
                parentId = "root",
                lastSyncedAt = null,
                icon = icon,
            )
        )
        return manager
    }

    @Test
    fun changingTheIconSavesItWithTheMetadata() {
        val manager = manager()
        val icon = MenuItem.Icon("favorites", 0xFF0000FF.toInt())

        manager.changeDocumentIcon(icon)

        assertEquals(icon, manager.documentInfo.value.icon)
        assertEquals(LastEdit.Metadata, manager.currentStory.value.lastEdit)
    }

    @Test
    fun removingTheIconLeavesTheDocumentWithout() {
        val manager = manager(icon = MenuItem.Icon("favorites", 0))

        manager.changeDocumentIcon(null)

        assertNull(manager.documentInfo.value.icon)
        assertEquals(LastEdit.Metadata, manager.currentStory.value.lastEdit)
    }

    @Test
    fun theSameIconIsNotSavedAgain() {
        val icon = MenuItem.Icon("favorites", 0)
        val manager = manager(icon = icon)

        manager.changeDocumentIcon(icon)

        assertNotEquals(LastEdit.Metadata, manager.currentStory.value.lastEdit)
    }

    @Test
    fun anIconSavedElsewhereShowsWithoutBeingSavedAgain() {
        val manager = manager()
        val icon = MenuItem.Icon("home", 0)

        manager.setDocumentIcon(icon)

        assertEquals(icon, manager.documentInfo.value.icon)
        assertNotEquals(LastEdit.Metadata, manager.currentStory.value.lastEdit)
    }
}

@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.parse

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.MenuItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DocumentParseTest {

    private fun document(icon: MenuItem.Icon?): Document {
        val now = Clock.System.now()
        return Document(
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "w",
            parentId = "root",
            icon = icon,
        )
    }

    @Test
    fun `the icon of the document and its color are kept`() {
        val icon = MenuItem.Icon("favorites", 0xFF0000FF.toInt())

        assertEquals(icon, document(icon).toEntity().toModel().icon)
    }

    @Test
    fun `an icon without a color is kept`() {
        val icon = MenuItem.Icon("home", null)

        assertEquals(icon, document(icon).toEntity().toModel().icon)
    }

    @Test
    fun `a document without an icon has none`() {
        assertNull(document(icon = null).toEntity().toModel().icon)
    }
}

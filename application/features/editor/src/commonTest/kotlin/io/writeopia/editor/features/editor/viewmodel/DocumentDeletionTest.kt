@file:OptIn(ExperimentalTime::class)

package io.writeopia.editor.features.editor.viewmodel

import io.writeopia.sdk.models.document.Document
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DocumentDeletionTest {

    @Test
    fun failedDeleteShouldNotUnregisterSync() = runTest {
        val document = document()
        var unregisterCount = 0

        assertFailsWith<IllegalStateException> {
            deleteDocumentThenUnregister(
                document = document,
                delete = { _, _ -> error("delete failed") },
                unregister = { unregisterCount += 1 },
            )
        }

        assertEquals(0, unregisterCount)
    }

    @Test
    fun successfulDeleteShouldUnregisterAfterRepositoryDelete() = runTest {
        val document = document()
        val events = mutableListOf<String>()

        deleteDocumentThenUnregister(
            document = document,
            delete = { _, _ -> events += "delete" },
            unregister = { events += "unregister" },
        )

        assertEquals(listOf("delete", "unregister"), events)
    }

    private fun document(): Document {
        val now = Clock.System.now()
        return Document(
            id = "document-1",
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = "workspace-1",
            parentId = "root",
        )
    }
}

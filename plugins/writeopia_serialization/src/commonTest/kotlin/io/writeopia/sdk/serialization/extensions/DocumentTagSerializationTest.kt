@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.serialization.extensions

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.sdk.serialization.data.DocumentApi
import io.writeopia.sdk.serialization.data.DocumentTagApi
import io.writeopia.sdk.serialization.json.writeopiaJson
import io.writeopia.sdk.serialization.response.FolderContentResponse
import io.writeopia.sdk.serialization.response.WorkspaceDiffResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DocumentTagSerializationTest {

    @Test
    fun `a tag round trips through the api model, without its local sync time`() {
        val tag = DocumentTag(
            id = "tag-1",
            workspaceId = "ws",
            name = "Work",
            description = "Office things",
            createdAt = Instant.fromEpochMilliseconds(10),
            lastUpdatedAt = Instant.fromEpochMilliseconds(20),
            lastSyncedAt = Instant.fromEpochMilliseconds(30),
            deleted = true,
        )

        val json = writeopiaJson.encodeToString(DocumentTagApi.serializer(), tag.toApi())
        assertTrue("lastSyncedAt" !in json)

        val decoded = writeopiaJson.decodeFromString(DocumentTagApi.serializer(), json).toModel()
        assertEquals(tag.copy(lastSyncedAt = null), decoded)
        assertNull(decoded.lastSyncedAt)
    }

    @Test
    fun `document tag ids travel with the document and default to none`() {
        val document = Document(
            id = "doc",
            createdAt = Instant.fromEpochMilliseconds(1),
            lastUpdatedAt = Instant.fromEpochMilliseconds(2),
            lastSyncedAt = null,
            workspaceId = "ws",
            parentId = "root",
            tagIds = setOf("tag-1", "tag-2"),
        )

        val api = document.toApi()
        assertEquals(listOf("tag-1", "tag-2"), api.tagIds)
        assertEquals(setOf("tag-1", "tag-2"), api.toModel().tagIds)

        val legacy = writeopiaJson.decodeFromString(
            DocumentApi.serializer(),
            """{"id":"doc","workspaceId":"ws","createdAt":1,"lastUpdatedAt":2}"""
        )
        assertTrue(legacy.tagIds.isEmpty())
    }

    @Test
    fun `diff responses carry the catalogue and tolerate its absence`() {
        val withTags = writeopiaJson.decodeFromString(
            WorkspaceDiffResponse.serializer(),
            """{"folders":[],"documents":[],"tags":[{"id":"t","workspaceId":"ws","name":"N","createdAt":1,"lastUpdatedAt":2}]}"""
        )
        assertEquals("N", withTags.tags.single().name)
        assertEquals("", withTags.tags.single().description)

        val legacyWorkspace = writeopiaJson.decodeFromString(WorkspaceDiffResponse.serializer(), """{"folders":[],"documents":[]}""")
        assertTrue(legacyWorkspace.tags.isEmpty())

        val legacyFolder = writeopiaJson.decodeFromString(FolderContentResponse.serializer(), """{"folders":[],"documents":[]}""")
        assertTrue(legacyFolder.tags.isEmpty())
    }
}

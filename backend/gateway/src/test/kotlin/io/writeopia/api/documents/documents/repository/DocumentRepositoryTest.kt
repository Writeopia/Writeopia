package io.writeopia.api.documents.documents.repository

import io.writeopia.api.geteway.configurePersistence
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertTrue

class DocumentRepositoryTest {

    @Test
    fun `when getting a document, the order of steps should be correct`() = runTest {
        val database = configurePersistence()

        val now = Clock.System.now()

        val content: Map<Int, StoryStep> = mapOf(
            0 to StoryStep(type = StoryTypes.TEXT.type, text = "message1"),
            1 to StoryStep(type = StoryTypes.TEXT.type, text = "message2"),
            2 to StoryStep(type = StoryTypes.TEXT.type, text = "message3"),
            3 to StoryStep(type = StoryTypes.TEXT.type, text = "message4"),
        )

        val workspaceId = GenerateId.generate()

        val document = listOf(
            Document(
                id = GenerateId.generate(),
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
                content = content
            ),
            Document(
                id = GenerateId.generate(),
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
                content = content
            ),
            Document(
                id = GenerateId.generate(),
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
                content = content
            ),
        )

        database.saveDocument(*document.toTypedArray())
        val documentFromDb = database.documentsDiffByFolder("root", workspaceId, 0L)

        assertTrue(documentFromDb.isNotEmpty())
    }
}

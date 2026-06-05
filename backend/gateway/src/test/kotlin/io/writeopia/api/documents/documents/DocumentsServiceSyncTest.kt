@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents

import io.writeopia.api.documents.documents.repository.saveDocument
import io.writeopia.api.geteway.configurePersistence
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.serialization.extensions.toApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DocumentsServiceSyncTest {

    @Test
    fun `syncStorySteps returns empty response when no changes`() = runTest {
        val database = configurePersistence()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val now = Clock.System.now()

        // Create a document with some content
        val content = mapOf(
            0.0 to StoryStep(type = StoryTypes.TEXT.type, text = "line1"),
            1.0 to StoryStep(type = StoryTypes.TEXT.type, text = "line2"),
        )

        val document = Document(
            id = documentId,
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = workspaceId,
            parentId = "root",
            content = content
        )

        database.saveDocument(document)

        // Sync with no changes
        val response = DocumentsService.syncStorySteps(
            documentId = documentId,
            lastSyncTimestamp = now.toEpochMilliseconds(),
            modifiedSteps = emptyList(),
            deletedStepIds = emptyList(),
            writeopiaDb = database
        )

        assertTrue(response.updatedSteps.isEmpty())
        assertTrue(response.deletedStepIds.isEmpty())
        assertTrue(response.serverTimestamp > 0)
    }

    @Test
    fun `syncStorySteps saves client changes when no conflicts`() = runTest {
        val database = configurePersistence()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val now = Clock.System.now()

        // Create a document with some content
        val step1 = StoryStep(type = StoryTypes.TEXT.type, text = "line1")
        val content = mapOf(0.0 to step1)

        val document = Document(
            id = documentId,
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = workspaceId,
            parentId = "root",
            content = content
        )

        database.saveDocument(document)

        // Client modifies the step
        val modifiedStep = step1.copy(
            text = "line1 modified",
            lastUpdatedAt = Clock.System.now().toEpochMilliseconds()
        )

        val response = DocumentsService.syncStorySteps(
            documentId = documentId,
            lastSyncTimestamp = now.toEpochMilliseconds() - 1000, // Before any changes
            modifiedSteps = listOf(modifiedStep.toApi(0.0)),
            deletedStepIds = emptyList(),
            writeopiaDb = database
        )

        // No steps returned means client changes were accepted
        assertTrue(response.updatedSteps.isEmpty())
    }

    @Test
    fun `syncStorySteps returns server version when server is newer`() = runTest {
        val database = configurePersistence()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val now = Clock.System.now()
        val serverTimestamp = now.toEpochMilliseconds()

        // Create a document with a step that has a server timestamp
        val step1 = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "server version",
            lastUpdatedAt = serverTimestamp
        )
        val content = mapOf(0.0 to step1)

        val document = Document(
            id = documentId,
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = workspaceId,
            parentId = "root",
            content = content
        )

        database.saveDocument(document)

        // Client sends an older version
        val clientStep = step1.copy(
            text = "client version",
            lastUpdatedAt = serverTimestamp - 5000 // 5 seconds older
        )

        val response = DocumentsService.syncStorySteps(
            documentId = documentId,
            lastSyncTimestamp = serverTimestamp - 10000, // Before server change
            modifiedSteps = listOf(clientStep.toApi(0.0)),
            deletedStepIds = emptyList(),
            writeopiaDb = database
        )

        // Server version should be returned since it's newer
        assertEquals(1, response.updatedSteps.size)
        assertEquals("server version", response.updatedSteps[0].text)
    }

    @Test
    fun `syncStorySteps accepts client version when client is newer`() = runTest {
        val database = configurePersistence()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val now = Clock.System.now()
        val oldTimestamp = now.toEpochMilliseconds() - 10000

        // Create a document with an older server timestamp
        val step1 = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "old server version",
            lastUpdatedAt = oldTimestamp
        )
        val content = mapOf(0.0 to step1)

        val document = Document(
            id = documentId,
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = workspaceId,
            parentId = "root",
            content = content
        )

        database.saveDocument(document)

        // Client sends a newer version
        val clientTimestamp = Clock.System.now().toEpochMilliseconds()
        val clientStep = step1.copy(
            text = "new client version",
            lastUpdatedAt = clientTimestamp
        )

        val response = DocumentsService.syncStorySteps(
            documentId = documentId,
            lastSyncTimestamp = oldTimestamp - 1000,
            modifiedSteps = listOf(clientStep.toApi(0.0)),
            deletedStepIds = emptyList(),
            writeopiaDb = database
        )

        // No steps returned means client version was accepted
        assertTrue(response.updatedSteps.isEmpty())
    }

    @Test
    fun `syncStorySteps handles deletions`() = runTest {
        val database = configurePersistence()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val now = Clock.System.now()

        // Create a document with content
        val step1 = StoryStep(type = StoryTypes.TEXT.type, text = "line1")
        val step2 = StoryStep(type = StoryTypes.TEXT.type, text = "line2")
        val content = mapOf(
            0.0 to step1,
            1.0 to step2
        )

        val document = Document(
            id = documentId,
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = workspaceId,
            parentId = "root",
            content = content
        )

        database.saveDocument(document)

        // Client deletes step2
        val response = DocumentsService.syncStorySteps(
            documentId = documentId,
            lastSyncTimestamp = now.toEpochMilliseconds(),
            modifiedSteps = emptyList(),
            deletedStepIds = listOf(step2.id),
            writeopiaDb = database
        )

        assertTrue(response.updatedSteps.isEmpty())
        assertTrue(response.serverTimestamp > 0)
    }

    @Test
    fun `syncStorySteps returns new server steps not known to client`() = runTest {
        val database = configurePersistence()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val oldTime = Clock.System.now().toEpochMilliseconds() - 10000
        val now = Clock.System.now()

        // Create a document with a step that was added after client's last sync
        val newServerStep = StoryStep(
            type = StoryTypes.TEXT.type,
            text = "new from server",
            lastUpdatedAt = now.toEpochMilliseconds()
        )
        val content = mapOf(0.0 to newServerStep)

        val document = Document(
            id = documentId,
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = workspaceId,
            parentId = "root",
            content = content
        )

        database.saveDocument(document)

        // Client syncs with old timestamp, not sending any changes
        val response = DocumentsService.syncStorySteps(
            documentId = documentId,
            lastSyncTimestamp = oldTime, // Before the server step was created
            modifiedSteps = emptyList(),
            deletedStepIds = emptyList(),
            writeopiaDb = database
        )

        // Server should return the new step
        assertEquals(1, response.updatedSteps.size)
        assertEquals("new from server", response.updatedSteps[0].text)
    }

    @Test
    fun `syncStorySteps handles multiple changes in batch`() = runTest {
        val database = configurePersistence()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val now = Clock.System.now()

        // Create a document with multiple steps
        val step1 = StoryStep(type = StoryTypes.TEXT.type, text = "line1")
        val step2 = StoryStep(type = StoryTypes.TEXT.type, text = "line2")
        val step3 = StoryStep(type = StoryTypes.TEXT.type, text = "line3")
        val content = mapOf(
            0.0 to step1,
            1.0 to step2,
            2.0 to step3
        )

        val document = Document(
            id = documentId,
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = workspaceId,
            parentId = "root",
            content = content
        )

        database.saveDocument(document)

        // Client modifies step1 and step3, deletes step2
        val clientTimestamp = Clock.System.now().toEpochMilliseconds()
        val modifiedStep1 = step1.copy(text = "line1 modified", lastUpdatedAt = clientTimestamp)
        val modifiedStep3 = step3.copy(text = "line3 modified", lastUpdatedAt = clientTimestamp)

        val response = DocumentsService.syncStorySteps(
            documentId = documentId,
            lastSyncTimestamp = now.toEpochMilliseconds(),
            modifiedSteps = listOf(
                modifiedStep1.toApi(0.0),
                modifiedStep3.toApi(2.0)
            ),
            deletedStepIds = listOf(step2.id),
            writeopiaDb = database
        )

        // All changes should be accepted (no conflicts)
        assertTrue(response.updatedSteps.isEmpty())
        assertTrue(response.serverTimestamp > 0)
    }

    @Test
    fun `syncStorySteps server timestamp is consistent across response`() = runTest {
        val database = configurePersistence()
        val workspaceId = GenerateId.generate()
        val documentId = GenerateId.generate()
        val now = Clock.System.now()

        val document = Document(
            id = documentId,
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = now,
            workspaceId = workspaceId,
            parentId = "root",
            content = emptyMap()
        )

        database.saveDocument(document)

        val beforeSync = Clock.System.now().toEpochMilliseconds()

        val response = DocumentsService.syncStorySteps(
            documentId = documentId,
            lastSyncTimestamp = 0L,
            modifiedSteps = emptyList(),
            deletedStepIds = emptyList(),
            writeopiaDb = database
        )

        val afterSync = Clock.System.now().toEpochMilliseconds()

        // Server timestamp should be within the sync window
        assertTrue(response.serverTimestamp >= beforeSync)
        assertTrue(response.serverTimestamp <= afterSync)
    }
}

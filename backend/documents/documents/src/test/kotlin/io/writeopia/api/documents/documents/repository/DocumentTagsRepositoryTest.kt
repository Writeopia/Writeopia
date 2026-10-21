@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.documents.repository

import io.writeopia.api.documents.documents.configureTestPersistence
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class DocumentTagsRepositoryTest {

    private val db = configureTestPersistence()
    private val workspaceId = "workspace-tags"

    private fun tag(id: String, name: String, deleted: Boolean = false) =
        DocumentTag(
            id = id,
            workspaceId = workspaceId,
            name = name,
            description = "About $name",
            createdAt = Instant.fromEpochMilliseconds(10),
            lastUpdatedAt = Instant.fromEpochMilliseconds(20),
            deleted = deleted,
        )

    private fun document(id: String, tagIds: Set<String>) =
        Document(
            id = id,
            title = "Doc $id",
            content = mapOf(0L to StoryStep(type = StoryTypes.TEXT.type, text = "hello")),
            createdAt = Instant.fromEpochMilliseconds(100),
            lastUpdatedAt = Instant.fromEpochMilliseconds(200),
            lastSyncedAt = null,
            workspaceId = workspaceId,
            parentId = "root",
            tagIds = tagIds,
        )

    @AfterTest
    fun tearDown() {
        db.documentTagLinkEntityQueries.deleteByWorkspace(workspaceId)
        db.documentTagEntityQueries.deleteByWorkspace(workspaceId)
        val ids = db.documentEntityQueries.selectAllIdsByWorkspaceId(workspaceId).executeAsList()
        if (ids.isNotEmpty()) db.storyStepEntityQueries.deleteByDocumentIds(ids)
        db.documentEntityQueries.hardDeleteByWorkspaceId(workspaceId)
    }

    @Test
    fun `saves the catalogue and reads it back with its tombstones`() {
        db.saveDocumentTag(tag("t1", "Work"), tag("t2", "Ideas", deleted = true))

        val loaded = db.allDocumentTagsByWorkspaceId(workspaceId)

        assertEquals(listOf("Ideas", "Work"), loaded.map { it.name })
        assertEquals("About Work", loaded.first { it.id == "t1" }.description)
        assertTrue(loaded.first { it.id == "t2" }.deleted)
    }

    @Test
    fun `upserts a tag by id`() {
        db.saveDocumentTag(tag("t1", "Work"))
        db.saveDocumentTag(tag("t1", "Office"))

        assertEquals(listOf("Office"), db.allDocumentTagsByWorkspaceId(workspaceId).map { it.name })
    }

    @Test
    fun `a document keeps its tag ids and a new save replaces them`() = runBlocking {
        db.saveDocument(document("d1", setOf("t1", "t2")))

        assertEquals(setOf("t1", "t2"), db.getDocumentWithContentById("d1", workspaceId)?.tagIds)

        db.saveDocument(document("d1", setOf("t2")))

        assertEquals(setOf("t2"), db.getDocumentWithContentById("d1", workspaceId)?.tagIds)
    }

    @Test
    fun `the diffs attach the tag ids of every document`() = runBlocking {
        db.saveDocument(document("d1", setOf("t1")), document("d2", emptySet()), document("d3", setOf("t1", "t3")))

        val byWorkspace = db.documentsDiffByWorkspace(workspaceId, 0).associate { it.id to it.tagIds }
        assertEquals(mapOf("d1" to setOf("t1"), "d2" to emptySet(), "d3" to setOf("t1", "t3")), byWorkspace)

        val byFolder = db.documentsDiffByFolder("root", workspaceId, 0).associate { it.id to it.tagIds }
        assertEquals(byWorkspace, byFolder)

        val byParent = db.getDocumentsByParentId("root").filter { it.workspaceId == workspaceId }.associate { it.id to it.tagIds }
        assertEquals(byWorkspace, byParent)
    }
}

@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.sqldelight.dao.sql

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.writeopia.libraries.dbtests.DocumentRepositoryTests
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.persistence.sqldelight.dao.DocumentSqlDao
import io.writeopia.sdk.sql.WriteopiaDb
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlin.time.Instant
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime


class SqlDelightDocumentRepositoryTest {

    private val database: WriteopiaDb =
        WriteopiaDb(
            JdbcSqliteDriver(
                "jdbc:sqlite:",
                Properties(),
                WriteopiaDb.Schema.synchronous()
            )
        )
    private val documentSqlDao: DocumentSqlDao = DocumentSqlDao(
        database.documentEntityQueries,
        database.storyStepEntityQueries,
        database.commentEntityQueries,
    )

    private val documentRepository = SqlDelightDocumentRepository(documentSqlDao)

    @Test
    fun `when saving a document when less content, the exceeding content should be erased`() =
        runTest {
            val smallContent = mapOf(
                0.0 to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "text",
                    dbPosition = 0.0,
                ),
                1.0 to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "text1",
                    dbPosition = 1.0
                )
            )

            val bigContent = smallContent + (
                2.0 to StoryStep(type = StoryTypes.TEXT.type, text = "text2", dbPosition = 2.0)
                )

            val instant = Instant.parse("2023-01-01T12:05:30Z")
            val userId = "disconnected_user"
            val workspaceId = "workspaceId"
            val document = Document(
                createdAt = instant,
                lastUpdatedAt = instant,
                lastSyncedAt = null,
                workspaceId = workspaceId,
                parentId = "",
                content = bigContent,
            )

            documentRepository.saveDocument(document)
            val result = documentRepository.loadDocumentById(
                document.id,
                workspaceId = workspaceId
            )

            assertEquals(document, result)

            val newDocument = document.copy(content = smallContent)
            documentRepository.saveDocument(newDocument)
            val result1 = documentRepository.loadDocumentById(
                newDocument.id,
                workspaceId = workspaceId
            )

            assertEquals(smallContent, result1?.content)
        }

    @Test
    fun `it shouold be possible to save icon`() = runTest {
        val now = Clock.System.now()
        val documentId = "asdasdasdgf"
        val icon = "newIcon"
        val tint = 123

        val userId = "disconnected_user"
        val workspaceId = "workspaceId"
        val document = Document(
            id = documentId,
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = workspaceId,
            parentId = "",
            icon = MenuItem.Icon(icon, tint)
        )

        documentRepository.saveDocument(document)

        val newDocument = documentRepository.loadDocumentById(documentId, workspaceId)
        assertEquals(newDocument?.icon?.label, icon)
        assertEquals(newDocument?.icon?.tint, tint)
    }

    @Test
    fun `it should be possible to save the lock state`() = runTest {
        val now = Clock.System.now()
        val documentId = "asdasdasdgf"
        val icon = "newIcon"
        val tint = 123

        val userId = "disconnected_user"
        val workspaceId = "workspaceId"
        val document = Document(
            id = documentId,
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = workspaceId,
            parentId = "",
            icon = MenuItem.Icon(icon, tint),
            isLocked = true
        )

        documentRepository.saveDocument(document)

        val newDocument = documentRepository.loadDocumentById(documentId, workspaceId)
        assertTrue { newDocument!!.isLocked }
    }

    @Test
    fun `it should be possible to get outdated documents`() = runTest {
        val now = Clock.System.now()
        val documentId = "asdasdasdgf"
        val icon = "newIcon"
        val tint = 123

        val userId = "disconnected_user"
        val workspaceId = "workspaceId"
        val document = Document(
            id = documentId,
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = Instant.DISTANT_PAST,
            workspaceId = workspaceId,
            parentId = "root",
            icon = MenuItem.Icon(icon, tint),
            isLocked = true
        )

        documentRepository.saveDocument(document)

        val newDocument = documentRepository.loadOutdatedDocumentsByFolder("root", workspaceId)

        assertEquals(1, newDocument.size)
        assertEquals(documentId, newDocument.first().id)
    }

    @Test
    fun `it should be possible to get outdated documents with null last sync`() = runTest {
        val now = Clock.System.now()
        val documentId = "asdasdasdgf"
        val icon = "newIcon"
        val tint = 123

        val userId = "disconnected_user"
        val workspaceId = "workspaceId"
        val document = Document(
            id = documentId,
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = workspaceId,
            parentId = "root",
            icon = MenuItem.Icon(icon, tint),
            isLocked = true
        )

        documentRepository.saveDocument(document)

        val newDocument = documentRepository.loadOutdatedDocumentsByFolder("root", workspaceId)

        assertEquals(1, newDocument.size)
        assertEquals(documentId, newDocument.first().id)
    }
    @Test
    fun `comments survive save and load in sqldelight`() = runTest {
        val now = Clock.System.now()
        val conversationId = "conversation-1"
        val document = Document(
            id = "document-with-comments",
            title = "Comments",
            content = mapOf(
                0.0 to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "Commented text",
                    spans = setOf(
                        SpanInfo.create(0, 9, Span.COMMENT, conversationId)
                    ),
                    dbPosition = 0.0,
                )
            ),
            commentConversations = mapOf(
                conversationId to listOf(
                    Comment(id = "comment-1", text = "First"),
                    Comment(id = "comment-2", text = "Second"),
                )
            ),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
        )

        documentRepository.saveDocument(document)

        val loadedDocument = documentRepository.loadDocumentById(document.id, document.workspaceId)
        assertEquals(document.id, loadedDocument?.id)
        assertEquals(document.commentConversations, loadedDocument?.commentConversations)
        assertEquals(
            document.content.getValue(0.0).spans,
            loadedDocument?.content?.get(0.0)?.spans,
        )
        assertEquals(
            document.content.getValue(0.0).text,
            loadedDocument?.content?.get(0.0)?.text,
        )

        val loadedFromWorkspace = documentRepository.loadDocumentsWorkspace(document.workspaceId)
            .first { it.id == document.id }
        assertEquals(document.commentConversations, loadedFromWorkspace.commentConversations)
    }

    @Test
    fun `deleted comment tombstone persists`() = runTest {
        DocumentRepositoryTests(documentRepository).deletedCommentTombstonePersists()
    }

    @Test
    fun `document id cannot move between workspaces`() = runTest {
        DocumentRepositoryTests(documentRepository).documentIdCannotMoveBetweenWorkspaces()
    }

    @Test
    fun `comment persistence respects workspace boundaries`() = runTest {
        DocumentRepositoryTests(documentRepository).commentPersistenceRespectsWorkspaceBoundaries()
    }

    @Test
    fun `comment id cannot move between documents`() = runTest {
        DocumentRepositoryTests(documentRepository).commentIdCannotMoveBetweenDocuments()
    }

    @Test
    fun `hard delete removes comment rows in sqldelight`() = runTest {
        val now = Clock.System.now()
        val document = Document(
            id = "document-to-delete",
            commentConversations = mapOf(
                "conversation-delete" to listOf(
                    Comment(id = "comment-delete", text = "Delete me")
                )
            ),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
        )

        val retainedDocument = Document(
            id = "document-to-keep",
            commentConversations = mapOf(
                "conversation-keep" to listOf(
                    Comment(id = "comment-keep", text = "Keep me")
                )
            ),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = document.workspaceId,
            parentId = "root",
        )

        documentRepository.saveDocument(document)
        documentRepository.saveDocument(retainedDocument)
        documentRepository.hardDeleteDocumentByIds(setOf(document.id), document.workspaceId)

        assertTrue(database.commentEntityQueries.selectByDocumentId(document.id).awaitAsList().isEmpty())
        assertTrue(
            database.commentEntityQueries
                .selectByDocumentId(retainedDocument.id)
                .awaitAsList()
                .isNotEmpty()
        )
    }

}

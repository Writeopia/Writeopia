@file:OptIn(ExperimentalTime::class)

package io.writeopia.sdk.persistence.sqldelight.dao.sql

import io.writeopia.sdk.utils.iterables.Rank
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
        database.documentTagLinkEntityQueries,
        database.storyStepTombstoneEntityQueries,
        database.pendingFavoriteEntityQueries,
    )

    private val documentRepository = SqlDelightDocumentRepository(documentSqlDao)

    @Test
    fun `when saving a document when less content, the exceeding content should be erased`() =
        runTest {
            val smallContent = mapOf(
                Rank.at(0) to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "text",
                    dbPosition = Rank.at(0),
                ),
                Rank.at(1) to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "text1",
                    dbPosition = Rank.at(1)
                )
            )

            val bigContent = smallContent + (
                Rank.at(2) to StoryStep(type = StoryTypes.TEXT.type, text = "text2", dbPosition = Rank.at(2))
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
                Rank.at(0) to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "Commented text",
                    spans = setOf(
                        SpanInfo.create(0, 9, Span.COMMENT, conversationId)
                    ),
                    dbPosition = Rank.at(0),
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
            document.content.getValue(Rank.at(0)).spans,
            loadedDocument?.content?.get(Rank.at(0))?.spans,
        )
        assertEquals(
            document.content.getValue(Rank.at(0)).text,
            loadedDocument?.content?.get(Rank.at(0))?.text,
        )

        val loadedFromWorkspace = documentRepository.loadDocumentsWorkspace(document.workspaceId)
            .first { it.id == document.id }
        assertEquals(document.commentConversations, loadedFromWorkspace.commentConversations)
    }

    @Test
    fun `save and load a document with tags`() = runTest {
        DocumentRepositoryTests(documentRepository).saveAndLoadDocumentWithTags()
    }

    @Test
    fun `saving again replaces the tags`() = runTest {
        DocumentRepositoryTests(documentRepository).savingAgainReplacesTags()
    }

    @Test
    fun `a metadata save keeps the tags`() = runTest {
        DocumentRepositoryTests(documentRepository).metadataSaveKeepsTags()
    }

    @Test
    fun `hard delete removes tag links in sqldelight`() = runTest {
        val document = DocumentRepositoryTests(documentRepository).saveDocumentWithTags()

        documentRepository.hardDeleteDocumentByIds(setOf(document.id), document.workspaceId)

        assertTrue(database.documentTagLinkEntityQueries.selectByDocumentId(document.id).awaitAsList().isEmpty())
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

    @Test
    fun deletedStepLeavesATombstoneAndNeverComesBack() = runTest {
        DocumentRepositoryTests(documentRepository).deletedStepLeavesATombstoneAndNeverComesBack()
    }

    @Test
    fun stepsDeletedElsewhereGoWithoutTombstone() = runTest {
        DocumentRepositoryTests(documentRepository).stepsDeletedElsewhereGoWithoutTombstone()
    }

    @Test
    fun aFavoriteChangedHereStaysPendingUntilTheBackendHasIt() = runTest {
        DocumentRepositoryTests(documentRepository).aFavoriteChangedHereStaysPendingUntilTheBackendHasIt()
    }

    @Test
    fun stepTimestampsAreKept() = runTest {
        DocumentRepositoryTests(documentRepository).stepTimestampsAreKept()
    }

    @Test
    fun aDocumentWithMoreStepsThanOneQueryCanBindIsSaved() = runTest {
        DocumentRepositoryTests(documentRepository).aDocumentWithMoreStepsThanOneQueryCanBindIsSaved()
    }

    @Test
    fun `a document saved whole keeps the positions of its steps, not their order in the map`() = runTest {
        val workspaceId = "workspaceId"
        val content = mapOf(
            Rank.at(0) to StoryStep(type = StoryTypes.TEXT.type, text = "title"),
            Rank.at(10) to StoryStep(type = StoryTypes.TEXT.type, text = "a"),
            Rank.at(15) to StoryStep(type = StoryTypes.TEXT.type, text = "between"),
            Rank.at(20) to StoryStep(type = StoryTypes.TEXT.type, text = "c"),
        )
        val document = Document(
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now(),
            lastSyncedAt = null,
            workspaceId = workspaceId,
            parentId = "",
            content = content,
        )

        documentRepository.saveDocument(document)

        val loaded = documentRepository.loadDocumentById(document.id, workspaceId)
        assertEquals(listOf(Rank.at(0), Rank.at(10), Rank.at(15), Rank.at(20)), loaded?.content?.keys?.toList())
        assertEquals(listOf("title", "a", "between", "c"), loaded?.content?.values?.map { it.text })

        // A step saved alone at its position keeps its place among the others
        documentRepository.saveStoryStep(
            content.getValue(Rank.at(15)).copy(text = "edited"),
            position = Rank.at(15),
            documentId = document.id
        )
        val reloaded = documentRepository.loadDocumentById(document.id, workspaceId)
        assertEquals(listOf("title", "a", "edited", "c"), reloaded?.content?.values?.map { it.text })
    }
}

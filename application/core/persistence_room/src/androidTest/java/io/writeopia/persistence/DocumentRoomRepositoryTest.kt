package io.writeopia.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.writeopia.libraries.dbtests.DocumentRepositoryTests
import io.writeopia.persistence.room.WriteopiaApplicationDatabase
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.persistence.dao.CommentEntityDao
import io.writeopia.sdk.persistence.dao.DocumentEntityDao
import io.writeopia.sdk.persistence.dao.StoryUnitEntityDao
import io.writeopia.sdk.persistence.dao.room.RoomDocumentRepository
import io.writeopia.sdk.persistence.parse.toEntity
import io.writeopia.sdk.persistence.parse.toModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentRoomRepositoryTest {

    private lateinit var database: WriteopiaApplicationDatabase
    private lateinit var documentEntityDao: DocumentEntityDao
    private lateinit var storyUnitEntityDao: StoryUnitEntityDao
    private lateinit var commentEntityDao: CommentEntityDao
    private lateinit var documentRepository: DocumentRepository
    private lateinit var documentRepositoryTests: DocumentRepositoryTests

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            WriteopiaApplicationDatabase::class.java
        ).build()

        documentEntityDao = database.documentDao()
        storyUnitEntityDao = database.storyUnitDao()
        commentEntityDao = database.commentDao()

        documentRepository = RoomDocumentRepository(
            documentEntityDao,
            storyUnitEntityDao,
            commentEntityDao,
            database,
        )
        documentRepositoryTests = DocumentRepositoryTests(documentRepository)
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun saveAndLoadASimpleDocument() = runTest {
        val id = GenerateId.generate()
        val document = Document(
            id = id,
            title = "Document1",
            content = emptyMap(),
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now(),
            workspaceId = "userId",
            parentId = "parentId"
        )

        val loadedDocument = documentEntityDao.run {
            insertDocuments(document.toEntity())
            loadDocumentById(id)
        }

        assertEquals(document.id, loadedDocument?.toModel()?.id)
    }

    @Test
    fun saveSimpleDocumentInRepository() = runTest {
        documentRepositoryTests.saveAndLoadADocumentWithoutContent()
    }

    @Test
    fun savingAndLoadingDocumentWithOneImageInRepository() = runTest {
        documentRepositoryTests.savingAndLoadingDocumentWithOneImageInRepository()
    }

    @Test
    fun savingAndLoadingDocumentWithManyImagesInRepository() = runTest {
        documentRepositoryTests.savingAndLoadingDocumentWithManyImagesInRepository()
    }

    @Test
    fun savingAndLoadingDocumentOneImageGroupInRepository() = runTest {
        documentRepositoryTests.savingAndLoadingDocumentOneImageGroupInRepository()
    }

    @Test
    fun favoriteDocumentById() = runTest {
        documentRepositoryTests.favoriteAndUnFavoriteDocumentById()
    }

    @Test
    fun saveSimpleDocumentAndLoadByParentId() = runTest {
        documentRepositoryTests.saveSimpleDocumentAndLoadByParentId()
    }

    @Test
    fun saveAndLoadDocumentWithComments() = runTest {
        documentRepositoryTests.saveAndLoadDocumentWithComments()
    }

    @Test
    fun nestedStoryStepsSurviveRepositoryRoundTrip() = runTest {
        val now = Clock.System.now()
        val workspaceId = "workspace-deep"
        val documentId = GenerateId.generate()
        val conversationId = GenerateId.generate()
        val timestamp = 1_700_000_000_123L
        val grandchild = StoryStep(
            id = GenerateId.generate(),
            type = StoryTypes.TEXT.type,
            text = "grandchild",
            spans = setOf(SpanInfo.create(0, 5, Span.COMMENT, conversationId)),
            lastUpdatedAt = timestamp,
        )
        val child = StoryStep(
            id = GenerateId.generate(),
            type = StoryTypes.TEXT.type,
            text = "child",
            steps = listOf(grandchild),
            lastUpdatedAt = timestamp,
        )
        val parent = StoryStep(
            id = GenerateId.generate(),
            type = StoryTypes.TEXT.type,
            text = "parent",
            steps = listOf(child),
            lastUpdatedAt = timestamp,
        )
        documentRepository.saveDocument(
            Document(
                id = documentId,
                createdAt = now,
                lastUpdatedAt = now,
                lastSyncedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
                content = mapOf(0.0 to parent),
                commentConversations = mapOf(
                    conversationId to listOf(Comment(id = GenerateId.generate(), text = "comment"))
                ),
            )
        )

        val loaded = documentRepository.loadDocumentById(documentId, workspaceId)!!
        val loadedGrandchild = loaded.content.values.single()
            .steps.single()
            .steps.single()

        assertEquals("grandchild", loadedGrandchild.text)
        assertEquals(timestamp, loadedGrandchild.lastUpdatedAt)
        assertEquals(conversationId, loadedGrandchild.spans.single().extra)
        assertTrue(loaded.commentConversations.containsKey(conversationId))
    }

    @Test
    fun incrementalStoryStepSaveReplacesPersistedSubtree() = runTest {
        val now = Clock.System.now()
        val workspaceId = "workspace-subtree-single"
        val documentId = GenerateId.generate()
        val keptChild = StoryStep(
            id = GenerateId.generate(),
            type = StoryTypes.TEXT.type,
            text = "kept",
        )
        val staleGrandchild = StoryStep(
            id = GenerateId.generate(),
            type = StoryTypes.TEXT.type,
            text = "stale-grandchild",
        )
        val removedChild = StoryStep(
            id = GenerateId.generate(),
            type = StoryTypes.TEXT.type,
            text = "removed",
            steps = listOf(staleGrandchild),
        )
        val parent = StoryStep(
            id = GenerateId.generate(),
            type = StoryTypes.TEXT.type,
            text = "parent",
            steps = listOf(keptChild, removedChild),
        )
        documentRepository.saveDocument(
            Document(
                id = documentId,
                createdAt = now,
                lastUpdatedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
                content = mapOf(0.0 to parent),
            )
        )

        documentRepository.saveStoryStep(
            storyStep = parent.copy(steps = listOf(keptChild)),
            position = 0.0,
            documentId = documentId,
        )

        val loaded = documentRepository.loadDocumentById(documentId, workspaceId)!!
        assertEquals(listOf(keptChild.id), loaded.content.getValue(0.0).steps.map { it.id })
        assertEquals(null, storyUnitEntityDao.queryById(removedChild.id))
        assertEquals(null, storyUnitEntityDao.queryById(staleGrandchild.id))
    }

    @Test
    fun incrementalStoryStepsSaveReplacesPersistedSubtrees() = runTest {
        val now = Clock.System.now()
        val workspaceId = "workspace-subtree-batch"
        val documentId = GenerateId.generate()
        val firstKept = StoryStep(
            id = GenerateId.generate(),
            type = StoryTypes.TEXT.type,
            text = "first-kept",
        )
        val firstRemoved = StoryStep(
            id = GenerateId.generate(),
            type = StoryTypes.TEXT.type,
            text = "first-removed",
        )
        val secondKept = StoryStep(
            id = GenerateId.generate(),
            type = StoryTypes.TEXT.type,
            text = "second-kept",
        )
        val secondRemoved = StoryStep(
            id = GenerateId.generate(),
            type = StoryTypes.TEXT.type,
            text = "second-removed",
        )
        val firstParent = StoryStep(
            id = GenerateId.generate(),
            type = StoryTypes.TEXT.type,
            text = "first-parent",
            steps = listOf(firstKept, firstRemoved),
        )
        val secondParent = StoryStep(
            id = GenerateId.generate(),
            type = StoryTypes.TEXT.type,
            text = "second-parent",
            steps = listOf(secondKept, secondRemoved),
        )
        documentRepository.saveDocument(
            Document(
                id = documentId,
                createdAt = now,
                lastUpdatedAt = now,
                workspaceId = workspaceId,
                parentId = "root",
                content = mapOf(0.0 to firstParent, 1.0 to secondParent),
            )
        )

        documentRepository.saveStorySteps(
            steps = listOf(
                0.0 to firstParent.copy(steps = listOf(firstKept)),
                1.0 to secondParent.copy(steps = listOf(secondKept)),
            ),
            documentId = documentId,
        )

        val loaded = documentRepository.loadDocumentById(documentId, workspaceId)!!
        assertEquals(
            listOf(firstKept.id),
            loaded.content.getValue(0.0).steps.map { it.id },
        )
        assertEquals(
            listOf(secondKept.id),
            loaded.content.getValue(1.0).steps.map { it.id },
        )
        assertEquals(null, storyUnitEntityDao.queryById(firstRemoved.id))
        assertEquals(null, storyUnitEntityDao.queryById(secondRemoved.id))
    }

    @Test
    fun collectionLoadPreservesComments() = runTest {
        documentRepositoryTests.collectionLoadPreservesComments()
    }

    @Test
    fun deletedCommentTombstonePersists() = runTest {
        documentRepositoryTests.deletedCommentTombstonePersists()
    }

    @Test
    fun documentIdCannotMoveBetweenWorkspaces() = runTest {
        documentRepositoryTests.documentIdCannotMoveBetweenWorkspaces()
    }

    @Test
    fun commentPersistenceRespectsWorkspaceBoundaries() = runTest {
        documentRepositoryTests.commentPersistenceRespectsWorkspaceBoundaries()
    }

    @Test
    fun commentIdCannotMoveBetweenDocuments() = runTest {
        documentRepositoryTests.commentIdCannotMoveBetweenDocuments()
    }

    @Test
    fun staleSaveDoesNotResurrectSoftDeletedDocument() = runTest {
        documentRepositoryTests.staleSaveDoesNotResurrectSoftDeletedDocument()
    }

    @Test
    fun hardDeleteRemovesComments() = runTest {
        val document = documentRepositoryTests.saveDocumentWithComments()

        documentRepository.hardDeleteDocumentByIds(setOf(document.id), document.workspaceId)

        assertTrue(commentEntityDao.loadByDocumentId(document.id).isEmpty())
    }

    @Test
    fun listenDocumentByParentId() = runTest {
        val document = Document(
            title = "Document1",
            content = emptyMap(),
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now(),
            workspaceId = "userId",
            parentId = "parentId"
        )

        documentRepository.saveDocument(document)
        val flow = documentRepository.listenForDocumentsByParentId(document.parentId)

        assertTrue(flow.first().isNotEmpty())
    }
}

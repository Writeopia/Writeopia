package io.writeopia.persistence

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.writeopia.libraries.dbtests.DocumentRepositoryTests
import io.writeopia.persistence.room.WriteopiaApplicationDatabase
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.persistence.dao.DocumentEntityDao
import io.writeopia.sdk.persistence.dao.StoryUnitEntityDao
import io.writeopia.sdk.persistence.dao.room.RoomDocumentRepository
import io.writeopia.sdk.persistence.parse.toEntity
import io.writeopia.sdk.persistence.parse.toModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
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

        documentRepository = RoomDocumentRepository(documentEntityDao, storyUnitEntityDao)
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

package io.writeopia.notemenu.data.usecase

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.writeopia.common.utils.DISCONNECTED_USER_ID
import io.writeopia.sdk.models.document.Folder
import io.writeopia.core.configuration.repository.ConfigurationRoomRepository
import io.writeopia.persistence.room.WriteopiaApplicationDatabase
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.MenuItem
import io.writeopia.sdk.repository.DocumentRepository
import io.writeopia.sdk.persistence.dao.room.RoomDocumentRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotesUseCaseIntegrationTest {

    private lateinit var database: WriteopiaApplicationDatabase

    private lateinit var documentRepository: DocumentRepository
    private lateinit var folderRepository: RoomFolderRepository
    private lateinit var notesConfig: ConfigurationRepository

    private lateinit var notesUseCase: NotesUseCase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            WriteopiaApplicationDatabase::class.java
        ).build()

        notesConfig = ConfigurationRoomRepository(database.notesConfigurationDao())

        documentRepository = RoomDocumentRepository(database.documentDao(), database.storyUnitDao())
        folderRepository = RoomFolderRepository(database.folderRoomDao())

        notesUseCase = NotesUseCase.singleton(
            documentRepository,
            notesConfig = notesConfig,
            folderRepository = folderRepository
        )
    }

    @Test
    fun itShouldBePossibleToSaveAndLoadDocument() = runTest {
        val now = Clock.System.now()

        notesUseCase.saveDocument(
            Document(
                id = "documentId",
                title = "Document1",
                createdAt = now,
                lastUpdatedAt = now,
                userId = DISCONNECTED_USER_ID,
                parentId = "root"
            )
        )

        assertTrue {
            notesUseCase.loadDocumentsForUser(DISCONNECTED_USER_ID).isNotEmpty()
        }
    }

    @Test
    fun itShouldBePossibleToSaveAndLoadDocumentByParentId() = runTest {
        val now = Clock.System.now()

        notesUseCase.saveDocument(
            Document(
                id = "documentId",
                title = "Document1",
                createdAt = now,
                lastUpdatedAt = now,
                userId = DISCONNECTED_USER_ID,
                parentId = "root"
            )
        )

        val flow = notesUseCase.listenForMenuItemsByParentId(
            "root",
            DISCONNECTED_USER_ID,
        )

        assertTrue { flow.first().isNotEmpty() }
    }

    @Test
    fun itShouldBePossibleToSaveAndLoadFolderByParentId() = runTest {
        notesUseCase.updateFolder(
            Folder.fromName("Folder", DISCONNECTED_USER_ID).copy(parentId = "root")
        )

        val flow = notesUseCase.listenForMenuItemsByParentId(
            "root",
            DISCONNECTED_USER_ID,
        )

        assertTrue { flow.first().isNotEmpty() }
    }

    @Test
    fun itShouldBePossibleToUpdateAnIcon() = runTest {
        val now = Clock.System.now()
        val documentId = "documentId"

        notesUseCase.saveDocument(
            Document(
                id = documentId,
                title = "Document1",
                createdAt = now,
                lastUpdatedAt = now,
                userId = DISCONNECTED_USER_ID,
                parentId = "root"
            )
        )

        notesUseCase.updateDocumentById(documentId) { document ->
            document.copy(icon = MenuItem.Icon("newIcon", null))
        }

        val flow = notesUseCase.listenForMenuItemsByParentId(
            "root",
            DISCONNECTED_USER_ID,
        )

        assertEquals(flow.first().values.first().first().icon?.label, "newIcon")
    }
}

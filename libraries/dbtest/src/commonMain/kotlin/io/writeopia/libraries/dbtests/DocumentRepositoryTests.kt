package io.writeopia.libraries.dbtests

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.repository.DocumentRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentRepositoryTests(private val documentRepository: DocumentRepository) {

    suspend fun saveAndLoadADocumentWithContent() {
        val now = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

        val id = GenerateId.generate()
        val document = Document(
            id = id,
            title = "Document1",
            content = listOf(
                (
                    0 to StoryStep(
                        type = StoryTypes.TEXT.type,
                        text = "text",
                        tags = setOf(TagInfo(Tag.H1))
                    )
                    )
            ).toMap(),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
            isLocked = false,
        )

        documentRepository.saveDocument(document)
        val loadedDocument = documentRepository.loadDocumentById(id, "workspaceId")

        assertEquals(document, loadedDocument)
    }

    suspend fun saveAndLoadADocumentWithoutContent() {
        val now = now()

        val id = GenerateId.generate()
        val document = Document(
            id = id,
            title = "Document1",
            content = emptyMap(),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
            isLocked = false
        )

        documentRepository.saveDocument(document)
        val loadedDocument = documentRepository.loadDocumentById(id, "workspaceId")

        assertEquals(document, loadedDocument)
    }

    suspend fun savingAndLoadingDocumentWithOneImageInRepository() {
        val now = now()

        val id = GenerateId.generate()
        val document = Document(
            id = id,
            title = "Document1",
            content = simpleImage(),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
            isLocked = false
        )

        documentRepository.saveDocument(document)
        val loadedDocument = documentRepository.loadDocumentById(id, "workspaceId")

        assertEquals(document, loadedDocument)
    }

    suspend fun savingAndLoadingDocumentWithManyImagesInRepository() {
        val now = now()

        val id = GenerateId.generate()
        val document = Document(
            id = id,
            title = "Document1",
            content = imageStepsList(),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
            isLocked = false
        )

        documentRepository.saveDocument(document)
        val loadedDocument = documentRepository.loadDocumentById(id, "workspaceId")

        assertTrue(loadedDocument?.content?.isNotEmpty() == true)
        assertEquals(document, loadedDocument)
    }

    suspend fun savingAndLoadingDocumentOneImageGroupInRepository() {
        val now = now()

        val id = GenerateId.generate()
        val document = Document(
            id = id,
            title = "Document1",
            content = imageGroup(),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
            isLocked = false
        )

        documentRepository.saveDocument(document)
        val loadedDocument = documentRepository.loadDocumentById(id, "workspaceId")

        assertEquals(document, loadedDocument)
    }

    suspend fun favoriteAndUnFavoriteDocumentById() {
        val now = now()

        val id = GenerateId.generate()
        val workspaceId = Random(24).nextInt().toString()
        val document = Document(
            id = id,
            title = "Document1",
            content = imageGroup(),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = workspaceId,
            favorite = false,
            parentId = "parentId",
            isLocked = false
        )

        documentRepository.saveDocument(document)

        val loadedDocument0 = documentRepository.loadDocumentById(id, workspaceId)
        assertTrue(loadedDocument0?.favorite == false)

        documentRepository.favoriteDocumentByIds(setOf(id))
        val loadedDocument1 = documentRepository.loadDocumentById(id, workspaceId)
        assertTrue(loadedDocument1?.favorite == true)

        documentRepository.unFavoriteDocumentByIds(setOf(id))
        val loadedDocument2 = documentRepository.loadDocumentById(id, workspaceId)
        assertTrue(loadedDocument2?.favorite == false)
    }

    suspend fun saveSimpleDocumentAndLoadByParentId() {
        val document = Document(
            id = GenerateId.generate(),
            title = "Document1",
            content = emptyMap(),
            createdAt = Clock.System.now(),
            lastUpdatedAt = Clock.System.now(),
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "parentId",
            isLocked = false
        )

        val loadedDocument = documentRepository.run {
            saveDocument(document)
            loadDocumentsByParentId("parentId", "workspaceId")
        }.first()

        assertEquals(document.id, loadedDocument.id)
    }
}

private fun now() = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

fun simpleText(): Map<Int, StoryStep> = mapOf(
    0 to StoryStep(
        type = StoryTypes.TEXT.type,
        text = "text"
    )
)

fun simpleImage(): Map<Int, StoryStep> = mapOf(
    0 to StoryStep(
        localId = "0",
        type = StoryTypes.IMAGE.type,
    )
)

fun imageStepsList(): Map<Int, StoryStep> = mapOf(
    0 to StoryStep(
        localId = "0",
        type = StoryTypes.IMAGE.type,
    ),
    1 to StoryStep(
        localId = "1",
        type = StoryTypes.IMAGE.type,
    ),
    2 to StoryStep(
        localId = "2",
        type = StoryTypes.IMAGE.type,
    ),
)

fun imageGroup(): Map<Int, StoryStep> {
    val groupId = GenerateId.generate()

    return mapOf(
        0 to StoryStep(
            id = groupId,
            localId = "1",
            type = StoryTypes.GROUP_IMAGE.type,
            steps = listOf(
                StoryStep(
                    localId = "2",
                    type = StoryTypes.IMAGE.type,
                    parentId = groupId,
                ),
                StoryStep(
                    localId = "3",
                    type = StoryTypes.IMAGE.type,
                    parentId = groupId,
                ),
                StoryStep(
                    localId = "4",
                    type = StoryTypes.IMAGE.type,
                    parentId = groupId,
                )
            )
        ),
    )
}

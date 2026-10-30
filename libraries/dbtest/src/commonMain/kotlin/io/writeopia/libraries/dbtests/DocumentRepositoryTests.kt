@file:OptIn(ExperimentalTime::class)

package io.writeopia.libraries.dbtests

import io.writeopia.sdk.utils.iterables.Rank
import io.writeopia.sdk.models.comment.Comment
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.PendingFavorite
import io.writeopia.sdk.models.id.GenerateId
import io.writeopia.sdk.models.sorting.OrderBy
import io.writeopia.sdk.models.span.Span
import io.writeopia.sdk.models.span.SpanInfo
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.story.Tag
import io.writeopia.sdk.models.story.TagInfo
import io.writeopia.sdk.repository.DocumentRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class DocumentRepositoryTests(private val documentRepository: DocumentRepository) {

    suspend fun saveAndLoadADocumentWithContent() {
        val now = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

        val id = GenerateId.generate()
        val document = Document(
            id = id,
            title = "Document1",
            content = listOf(
                (
                    Rank.at(0) to StoryStep(
                        type = StoryTypes.TEXT.type,
                        text = "text",
                        tags = setOf(TagInfo(Tag.H1)),
                        dbPosition = Rank.at(0)
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

        assertEquals(loadedDocument?.content?.isNotEmpty(), true)
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
        assertEquals(loadedDocument0?.favorite, false)

        documentRepository.favoriteDocumentByIds(setOf(id))
        val loadedDocument1 = documentRepository.loadDocumentById(id, workspaceId)
        assertEquals(loadedDocument1?.favorite, true)

        documentRepository.unFavoriteDocumentByIds(setOf(id))
        val loadedDocument2 = documentRepository.loadDocumentById(id, workspaceId)
        assertEquals(loadedDocument2?.favorite, false)
    }

    suspend fun saveDocumentWithComments(): Document {
        val now = now()
        val conversationId = "conversation-1"
        val document = Document(
            id = GenerateId.generate(),
            title = "Document with comments",
            content = mapOf(
                Rank.at(0) to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "Commented text",
                    spans = setOf(
                        SpanInfo.create(0, 9, Span.COMMENT, conversationId),
                        SpanInfo.create(10, 13, Span.COMMENT, "conversation-2"),
                    ),
                    dbPosition = Rank.at(0),
                )
            ),
            commentConversations = mapOf(
                conversationId to listOf(
                    Comment(id = "comment-1", text = "First"),
                    Comment(id = "comment-2", text = "Second"),
                ),
                "conversation-2" to listOf(
                    Comment(id = "comment-3", text = "Third"),
                ),
            ),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
            isLocked = false,
        )

        documentRepository.saveDocument(document)
        return document
    }

    suspend fun saveAndLoadDocumentWithComments() {
        val document = saveDocumentWithComments()
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
    }

    suspend fun collectionLoadPreservesComments() {
        val document = saveDocumentWithComments()
        val loadedDocument = documentRepository.loadDocumentsWorkspace(document.workspaceId)
            .first { it.id == document.id }

        assertEquals(document.commentConversations, loadedDocument.commentConversations)
    }

    suspend fun deletedCommentTombstonePersists() {
        val now = now()
        val conversationId = "conversation-tombstone"
        val document = Document(
            id = GenerateId.generate(),
            title = "Tombstone",
            commentConversations = mapOf(
                conversationId to listOf(
                    Comment(id = "comment-active", text = "Keep"),
                    Comment(id = "comment-deleted", text = "Deleted", deleted = true),
                )
            ),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
        )

        documentRepository.saveDocument(document)

        assertEquals(
            document.commentConversations,
            documentRepository.loadDocumentById(document.id, document.workspaceId)
                ?.commentConversations,
        )
    }

    suspend fun commentIdCannotMoveBetweenDocuments() {
        val now = now()

        fun document(id: String, workspaceId: String, text: String) = Document(
            id = id,
            title = id,
            commentConversations = mapOf(
                "conversation-$id" to listOf(
                    Comment(id = "shared-comment-id", text = text)
                )
            ),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = workspaceId,
            parentId = "root",
        )

        val first = document("first-document", "workspace-a", "First")
        val second = document("second-document", "workspace-b", "Second")

        documentRepository.saveDocument(first)

        var failed = false
        try {
            documentRepository.saveDocument(second)
        } catch (_: Exception) {
            failed = true
        }

        assertTrue(failed)
        assertEquals(
            first.commentConversations,
            documentRepository.loadDocumentById(first.id, first.workspaceId)?.commentConversations,
        )
        assertEquals(null, documentRepository.loadDocumentById(second.id, second.workspaceId))
    }

    suspend fun documentIdCannotMoveBetweenWorkspaces() {
        val now = now()
        val first = Document(
            id = "shared-document-id",
            title = "First",
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspace-a",
            parentId = "root",
        )
        val second = first.copy(
            title = "Second",
            workspaceId = "workspace-b",
        )

        documentRepository.saveDocument(first)

        var failed = false
        try {
            documentRepository.saveDocument(second)
        } catch (_: Exception) {
            failed = true
        }

        assertTrue(failed)
        assertEquals(first, documentRepository.loadDocumentById(first.id, first.workspaceId))
        assertEquals(null, documentRepository.loadDocumentById(second.id, second.workspaceId))
    }

    suspend fun commentPersistenceRespectsWorkspaceBoundaries() {
        val now = now()

        fun document(
            id: String,
            workspaceId: String,
            conversationId: String,
            commentId: String,
        ) = Document(
            id = id,
            title = id,
            content = mapOf(
                Rank.at(0) to StoryStep(
                    type = StoryTypes.TEXT.type,
                    text = "Commented",
                    spans = setOf(
                        SpanInfo.create(0, 9, Span.COMMENT, conversationId)
                    ),
                    dbPosition = Rank.at(0),
                )
            ),
            commentConversations = mapOf(
                conversationId to listOf(Comment(id = commentId, text = workspaceId))
            ),
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = workspaceId,
            parentId = "root",
        )

        val first = document(
            id = "workspace-a-document",
            workspaceId = "workspace-a",
            conversationId = "conversation-a",
            commentId = "comment-a",
        )
        val second = document(
            id = "workspace-b-document",
            workspaceId = "workspace-b",
            conversationId = "conversation-b",
            commentId = "comment-b",
        )

        documentRepository.saveDocument(first)
        documentRepository.saveDocument(second)

        assertEquals(null, documentRepository.loadDocumentById(second.id, first.workspaceId))
        assertEquals(
            listOf(first.id),
            documentRepository.loadDocumentByIds(
                listOf(first.id, second.id),
                first.workspaceId,
            ).map { it.id },
        )
        assertEquals(
            listOf(first.id),
            documentRepository.loadDocumentsWithContentByIds(
                listOf(first.id, second.id),
                OrderBy.NAME.type,
                first.workspaceId,
            ).map { it.id },
        )

        documentRepository.hardDeleteDocumentByIds(
            setOf(first.id, second.id),
            first.workspaceId,
        )

        assertEquals(null, documentRepository.loadDocumentById(first.id, first.workspaceId))
        assertEquals(
            second.commentConversations,
            documentRepository.loadDocumentById(second.id, second.workspaceId)
                ?.commentConversations,
        )
    }

    suspend fun saveDocumentWithTags(tagIds: Set<String> = setOf("tag-work", "tag-ideas")): Document {
        val now = now()
        val document = Document(
            id = GenerateId.generate(),
            title = "Document with tags",
            content = mapOf(
                Rank.at(0) to StoryStep(type = StoryTypes.TEXT.type, text = "Tagged text", dbPosition = Rank.at(0))
            ),
            tagIds = tagIds,
            createdAt = now,
            lastUpdatedAt = now,
            lastSyncedAt = null,
            workspaceId = "workspaceId",
            parentId = "root",
            isLocked = false,
        )

        documentRepository.saveDocument(document)
        return document
    }

    suspend fun saveAndLoadDocumentWithTags() {
        val document = saveDocumentWithTags()

        assertEquals(document.tagIds, documentRepository.loadDocumentById(document.id, document.workspaceId)?.tagIds)
        assertEquals(
            document.tagIds,
            documentRepository.loadDocumentsWorkspace(document.workspaceId).first { it.id == document.id }.tagIds,
        )
        assertEquals(
            document.tagIds,
            documentRepository.loadDocumentsByParentId("root", document.workspaceId).first { it.id == document.id }.tagIds,
        )
    }

    suspend fun savingAgainReplacesTags() {
        val document = saveDocumentWithTags()

        documentRepository.saveDocument(document.copy(tagIds = setOf("tag-ideas")))
        assertEquals(setOf("tag-ideas"), documentRepository.loadDocumentById(document.id, document.workspaceId)?.tagIds)

        documentRepository.saveDocument(document.copy(tagIds = emptySet()))
        assertEquals(emptySet(), documentRepository.loadDocumentById(document.id, document.workspaceId)?.tagIds)
    }

    /** A metadata save (every keystroke) must not touch the tag links. */
    suspend fun metadataSaveKeepsTags() {
        val document = saveDocumentWithTags()

        documentRepository.saveDocumentMetadata(document.copy(title = "Renamed", tagIds = emptySet()))

        val loaded = documentRepository.loadDocumentById(document.id, document.workspaceId)
        assertEquals("Renamed", loaded?.title)
        assertEquals(document.tagIds, loaded?.tagIds)
    }

    /** A step deleted here leaves a tombstone for the sync, and nothing recreates it meanwhile. */
    suspend fun deletedStepLeavesATombstoneAndNeverComesBack() {
        val document = Document(
            createdAt = now(),
            lastUpdatedAt = now(),
            lastSyncedAt = null,
            workspaceId = "workspace-tombstone",
            parentId = "",
            content = twoLines(),
        )
        documentRepository.saveDocument(document)
        val (deleted, kept) = document.content.entries.sortedBy { entry -> entry.key }.map { entry -> entry.value }

        documentRepository.deleteStoryStep(deleted.id, document.id)

        val tombstones = documentRepository.loadStoryStepTombstones(document.workspaceId)
        assertEquals(listOf(deleted.id), tombstones.map { tombstone -> tombstone.stepId })
        assertEquals(document.id, tombstones.single().documentId)
        assertEquals(listOf(kept.id), documentRepository.loadDocumentById(document.id, document.workspaceId)?.stepIds())

        // Whatever saves it again, step by step or whole: it stays deleted.
        documentRepository.saveStoryStep(deleted, Rank.at(0), document.id)
        assertEquals(listOf(kept.id), documentRepository.loadDocumentById(document.id, document.workspaceId)?.stepIds())
        documentRepository.saveDocument(document)
        assertEquals(listOf(kept.id), documentRepository.loadDocumentById(document.id, document.workspaceId)?.stepIds())

        // Once the backend has the deletion, the tombstone goes.
        documentRepository.deleteStoryStepTombstones(listOf(deleted.id))
        assertTrue(documentRepository.loadStoryStepTombstones(document.workspaceId).isEmpty())
    }

    /**
     * A favorite changed here is recorded for the sync until the backend has it: one record per
     * document, the latest change, and a send that began before a newer change doesn't drop it.
     */
    suspend fun aFavoriteChangedHereStaysPendingUntilTheBackendHasIt() {
        val workspaceId = "workspace-pending-favorite"
        val first = PendingFavorite(documentId = "doc", workspaceId = workspaceId, favorite = true, changedAt = 10)
        val elsewhere = PendingFavorite(documentId = "other", workspaceId = "another-workspace", favorite = true, changedAt = 10)
        documentRepository.savePendingFavorites(listOf(first, elsewhere))

        assertEquals(listOf(first), documentRepository.loadPendingFavorites(workspaceId))

        // Changed again: the latest change is the record.
        val second = first.copy(favorite = false, changedAt = 20)
        documentRepository.savePendingFavorites(listOf(second))
        assertEquals(listOf(second), documentRepository.loadPendingFavorites(workspaceId))

        // The send of the first change ends now: the newer record stays.
        documentRepository.deletePendingFavorite(first)
        assertEquals(listOf(second), documentRepository.loadPendingFavorites(workspaceId))

        documentRepository.deletePendingFavorite(second)
        assertTrue(documentRepository.loadPendingFavorites(workspaceId).isEmpty())
        assertEquals(listOf(elsewhere), documentRepository.loadPendingFavorites(elsewhere.workspaceId))
    }

    /** A step deleted elsewhere goes without a tombstone, and only inside its workspace. */
    suspend fun stepsDeletedElsewhereGoWithoutTombstone() {
        val document = Document(
            createdAt = now(),
            lastUpdatedAt = now(),
            lastSyncedAt = null,
            workspaceId = "workspace-deleted-elsewhere",
            parentId = "",
            content = twoLines(),
        )
        documentRepository.saveDocument(document)
        val (deleted, kept) = document.content.entries.sortedBy { entry -> entry.key }.map { entry -> entry.value }

        documentRepository.deleteStoryStepsByIds(listOf(deleted.id), "another-workspace")
        assertEquals(2, documentRepository.loadDocumentById(document.id, document.workspaceId)?.content?.size)

        documentRepository.deleteStoryStepsByIds(listOf(deleted.id), document.workspaceId)
        assertEquals(listOf(kept.id), documentRepository.loadDocumentById(document.id, document.workspaceId)?.stepIds())
        assertTrue(documentRepository.loadStoryStepTombstones(document.workspaceId).isEmpty())
    }

    /** The time of a step's last edit survives a round trip, so the sync can compare it. */
    suspend fun stepTimestampsAreKept() {
        val content = mapOf(
            Rank.at(0) to StoryStep(type = StoryTypes.TEXT.type, text = "stamped", lastUpdatedAt = 1_791_998_156_814L),
            Rank.at(1) to StoryStep(type = StoryTypes.TEXT.type, text = "not stamped"),
        )
        val document = Document(
            createdAt = now(),
            lastUpdatedAt = now(),
            lastSyncedAt = null,
            workspaceId = "workspace-timestamps",
            parentId = "",
            content = content,
        )

        documentRepository.saveDocument(document)

        val loaded = documentRepository.loadDocumentById(document.id, document.workspaceId)
        assertEquals(listOf(1_791_998_156_814L, null), loaded?.content?.values?.map { it.lastUpdatedAt })
    }

    /** More steps than one SQLite query can bind (older builds allow 999 variables). */
    suspend fun aDocumentWithMoreStepsThanOneQueryCanBindIsSaved() {
        val content = (0 until 1_200).associate { index ->
            Rank.at(index) to StoryStep(type = StoryTypes.TEXT.type, text = "line $index")
        }
        val document = Document(
            createdAt = now(),
            lastUpdatedAt = now(),
            lastSyncedAt = null,
            workspaceId = "workspace-big",
            parentId = "",
            content = content,
        )

        documentRepository.saveDocument(document)
        val deleted = content.values.take(1_100).map { step -> step.id }
        documentRepository.deleteStoryStepsByIds(deleted, document.workspaceId)

        assertEquals(100, documentRepository.loadDocumentById(document.id, document.workspaceId)?.content?.size)
    }

    private fun Document.stepIds(): List<String> = content.values.map { it.id }

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

fun twoLines(): Map<String, StoryStep> = mapOf(
    Rank.at(0) to StoryStep(type = StoryTypes.TEXT.type, text = "first", dbPosition = Rank.at(0)),
    Rank.at(1) to StoryStep(type = StoryTypes.TEXT.type, text = "second", dbPosition = Rank.at(1)),
)

fun simpleText(): Map<String, StoryStep> = mapOf(
    Rank.at(0) to StoryStep(
        type = StoryTypes.TEXT.type,
        text = "text",
        dbPosition = Rank.at(0)
    )
)

fun simpleImage(): Map<String, StoryStep> = mapOf(
    Rank.at(0) to StoryStep(
        localId = "0",
        type = StoryTypes.IMAGE.type,
        dbPosition = Rank.at(0)
    )
)

fun imageStepsList(): Map<String, StoryStep> = mapOf(
    Rank.at(0) to StoryStep(
        localId = "0",
        type = StoryTypes.IMAGE.type,
        dbPosition = Rank.at(0)
    ),
    Rank.at(1) to StoryStep(
        localId = "1",
        type = StoryTypes.IMAGE.type,
        dbPosition = Rank.at(1)
    ),
    Rank.at(2) to StoryStep(
        localId = "2",
        type = StoryTypes.IMAGE.type,
        dbPosition = Rank.at(2)
    ),
)

fun imageGroup(): Map<String, StoryStep> {
    val groupId = GenerateId.generate()

    return mapOf(
        Rank.at(0) to StoryStep(
            id = groupId,
            localId = "1",
            type = StoryTypes.GROUP_IMAGE.type,
            dbPosition = Rank.at(0),
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

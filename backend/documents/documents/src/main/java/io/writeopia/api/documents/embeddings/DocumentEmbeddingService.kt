@file:OptIn(ExperimentalTime::class)

package io.writeopia.api.documents.embeddings

import io.writeopia.api.documents.documents.repository.getDocumentWithContentById
import io.writeopia.api.documents.documents.repository.getFolderById
import io.writeopia.api.documents.documents.repository.getFoldersByParentId
import io.writeopia.api.documents.documents.repository.getIdsByParentId
import io.writeopia.api.embeddings.DocumentPlainText
import io.writeopia.api.embeddings.Embedder
import io.writeopia.api.embeddings.EmbeddingPurpose
import io.writeopia.api.embeddings.EmbeddingRefreshPolicy
import io.writeopia.api.embeddings.EmbeddingStore
import io.writeopia.api.embeddings.EmbeddingVectors
import io.writeopia.api.embeddings.FolderEmbeddingKind
import io.writeopia.api.embeddings.StoredDocumentEmbedding
import io.writeopia.api.embeddings.StoredFolderEmbedding
import io.writeopia.connection.logger
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sql.WriteopiaDbBackend
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Keeps the embeddings of documents and folders up to date as the documents service saves,
 * syncs, moves and deletes them.
 *
 * A document is embedded again only after a big change, by [EmbeddingRefreshPolicy], since the
 * apps sync every few hundred milliseconds while the user types. A folder's vector is the mean
 * of the documents in its tree, recomputed when a document in it changes, moves or goes, and
 * then up its parent chain; a folder without documents gets the vector of its title instead.
 *
 * Everything here is best effort: it runs inside the request (Cloud Run gives no CPU after the
 * answer), a failure is logged and never fails the request.
 */
class DocumentEmbeddingService(
    private val embedder: Embedder,
    val store: EmbeddingStore,
    private val policy: EmbeddingRefreshPolicy = EmbeddingRefreshPolicy(),
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    /**
     * Embeds the document if its text changed enough, then refreshes the folders above it.
     *
     * The syncs arrive every few hundred milliseconds while the user types, so the document is
     * compared with its embedded text at most once per cooldown: a comparison that finds too
     * small a change is recorded as a check, and until the cooldown passes again nothing is even
     * loaded. The changes made meanwhile are still measured against the embedded text, so they
     * are embedded as soon as a check finds them big enough.
     */
    suspend fun embedDocumentIfNeeded(documentId: String, workspaceId: String, db: WriteopiaDbBackend) {
        guarded("embedding document $documentId") {
            val meta = store.documentMeta(documentId)
            // The cheap gate: within the cooldown of the last check nothing is even loaded.
            if (meta != null && meta.model == embedder.model && !policy.cooldownElapsed(meta.checkedAt, now())) {
                return@guarded
            }

            val document = db.getDocumentWithContentById(documentId, workspaceId) ?: return@guarded
            if (!embedDocument(document, db) && meta != null) store.markDocumentChecked(documentId, now())
        }
    }

    /** After a whole document was saved. [previousParentId] is where it was before, if it existed. */
    suspend fun onDocumentSaved(document: Document, previousParentId: String?, db: WriteopiaDbBackend) {
        guarded("embedding saved document ${document.id}") {
            val embedded = embedDocument(document, db)
            // embedDocument refreshes the new parent only when it embedded; a move must refresh anyway.
            if (!embedded && previousParentId != document.parentId) refreshFolderChain(document.parentId, document.workspaceId, db)
            if (previousParentId != null && previousParentId != document.parentId) {
                refreshFolderChain(previousParentId, document.workspaceId, db)
            }
        }
    }

    suspend fun onDocumentMoved(oldParentId: String?, newParentId: String, workspaceId: String, db: WriteopiaDbBackend) {
        guarded("refreshing folders after a document move") {
            if (oldParentId != null) refreshFolderChain(oldParentId, workspaceId, db)
            refreshFolderChain(newParentId, workspaceId, db)
        }
    }

    suspend fun onFolderMoved(oldParentId: String?, newParentId: String, workspaceId: String, db: WriteopiaDbBackend) {
        guarded("refreshing folders after a folder move") {
            if (oldParentId != null) refreshFolderChain(oldParentId, workspaceId, db)
            refreshFolderChain(newParentId, workspaceId, db)
        }
    }

    /** After documents were deleted: their vectors go, and the folders they were in are refreshed. */
    suspend fun onDocumentsDeleted(documentIds: Collection<String>, parentIds: Collection<String>, workspaceId: String, db: WriteopiaDbBackend) {
        guarded("refreshing folders after deleting documents") {
            store.deleteDocuments(documentIds)
            parentIds.distinct().forEach { parentId -> refreshFolderChain(parentId, workspaceId, db) }
        }
    }

    /** After a folder tree was deleted: every vector of the tree goes, and the folder above it is refreshed. */
    suspend fun onFolderDeleted(folderIds: Collection<String>, documentIds: Collection<String>, parentId: String?, workspaceId: String, db: WriteopiaDbBackend) {
        guarded("refreshing folders after deleting a folder") {
            store.deleteDocuments(documentIds)
            store.deleteFolders(folderIds)
            if (parentId != null) refreshFolderChain(parentId, workspaceId, db)
        }
    }

    /** After a folder was created or renamed: a folder without documents is known by its title. */
    suspend fun onFolderSaved(folder: Folder, db: WriteopiaDbBackend) {
        guarded("embedding folder ${folder.id}") {
            val existing = store.folder(folder.id)
            if (existing != null && existing.kind == FolderEmbeddingKind.CENTROID && existing.model == embedder.model) {
                return@guarded
            }
            ensureTitleEmbedding(folder)
        }
    }

    /** Recomputes [folderId] and every folder above it, up to the root. */
    suspend fun refreshFolderChain(folderId: String, workspaceId: String, db: WriteopiaDbBackend) {
        val visited = mutableSetOf<String>()
        var currentId = folderId

        while (currentId.isNotBlank() && currentId != Folder.ROOT_PATH && visited.add(currentId)) {
            val folder = db.getFolderById(currentId, workspaceId) ?: return
            recomputeFolder(folder, db)
            currentId = folder.parentId
        }
    }

    /** Embeds [document] when the policy says so. True when a new vector was stored. */
    private suspend fun embedDocument(document: Document, db: WriteopiaDbBackend): Boolean {
        val text = DocumentPlainText.of(document)
        val previous = store.document(document.id)
        val nowMillis = now()

        if (!policy.shouldReembed(previous, text.title, text.body, embedder.model, nowMillis)) return false

        val vector = embedder.embed(DocumentPlainText.embeddingInput(text), EmbeddingPurpose.DOCUMENT)
        if (vector == null) {
            logger.warn("No embedding answered for document {}", document.id)
            return false
        }

        store.upsertDocument(
            StoredDocumentEmbedding(
                documentId = document.id,
                workspaceId = document.workspaceId,
                model = embedder.model,
                vector = vector,
                sourceTitle = text.title,
                sourceText = text.body,
                embeddedAt = nowMillis,
            )
        )
        logger.info("Embedded document {} ({} chars)", document.id, text.body.length)

        refreshFolderChain(document.parentId, document.workspaceId, db)
        return true
    }

    /**
     * The folder's vector from its tree: its documents weigh one each, each child folder's mean
     * weighs the documents it stands for. Without any document, the title stands in.
     */
    private suspend fun recomputeFolder(folder: Folder, db: WriteopiaDbBackend) {
        val documentVectors = store.documents(db.getIdsByParentId(folder.id))
            .filter { row -> row.model == embedder.model && row.vector.size == embedder.dimension }
            .map { row -> row.vector to 1 }

        val childVectors = store.folders(db.getFoldersByParentId(folder.id).map { child -> child.id })
            .filter { row ->
                row.kind == FolderEmbeddingKind.CENTROID && row.documentCount > 0 &&
                    row.model == embedder.model && row.vector.size == embedder.dimension
            }
            .map { row -> row.vector to row.documentCount }

        val entries = documentVectors + childVectors
        val total = entries.sumOf { (_, count) -> count }
        val mean = EmbeddingVectors.weightedMean(entries)

        if (mean == null || total == 0) {
            ensureTitleEmbedding(folder)
            return
        }

        store.upsertFolder(
            StoredFolderEmbedding(
                folderId = folder.id,
                workspaceId = folder.workspaceId,
                model = embedder.model,
                vector = mean,
                kind = FolderEmbeddingKind.CENTROID,
                documentCount = total,
                sourceTitle = folder.title,
                updatedAt = now(),
            )
        )
    }

    /** A folder without documents is represented by its title, embedded once per title. */
    private suspend fun ensureTitleEmbedding(folder: Folder) {
        val title = folder.title.trim()
        val existing = store.folder(folder.id)

        if (title.isBlank()) {
            if (existing != null) store.deleteFolders(listOf(folder.id))
            return
        }
        if (existing != null && existing.kind == FolderEmbeddingKind.TITLE &&
            existing.model == embedder.model && existing.sourceTitle == title
        ) {
            return
        }

        val vector = embedder.embed(title, EmbeddingPurpose.DOCUMENT)
        if (vector == null) {
            logger.warn("No embedding answered for the title of folder {}", folder.id)
            return
        }

        store.upsertFolder(
            StoredFolderEmbedding(
                folderId = folder.id,
                workspaceId = folder.workspaceId,
                model = embedder.model,
                vector = vector,
                kind = FolderEmbeddingKind.TITLE,
                documentCount = 0,
                sourceTitle = title,
                updatedAt = now(),
            )
        )
    }

    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            logger.error("Embeddings: $what failed: ${e.message}", e)
        }
    }
}

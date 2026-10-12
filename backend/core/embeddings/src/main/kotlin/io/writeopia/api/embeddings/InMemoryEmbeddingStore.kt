package io.writeopia.api.embeddings

import java.util.concurrent.ConcurrentHashMap

/** An [EmbeddingStore] in maps, for tests and for running without pgvector. */
class InMemoryEmbeddingStore : EmbeddingStore {
    private val documentRows = ConcurrentHashMap<String, StoredDocumentEmbedding>()
    private val folderRows = ConcurrentHashMap<String, StoredFolderEmbedding>()

    val allDocuments: List<StoredDocumentEmbedding> get() = documentRows.values.toList()
    val allFolders: List<StoredFolderEmbedding> get() = folderRows.values.toList()

    override fun documentMeta(documentId: String): DocumentEmbeddingMeta? =
        documentRows[documentId]?.let { row -> DocumentEmbeddingMeta(row.documentId, row.model, row.embeddedAt, row.checkedAt) }

    override fun document(documentId: String): StoredDocumentEmbedding? = documentRows[documentId]

    override fun documents(documentIds: Collection<String>): List<StoredDocumentEmbedding> =
        documentIds.mapNotNull { id -> documentRows[id] }

    override fun upsertDocument(embedding: StoredDocumentEmbedding) {
        documentRows[embedding.documentId] = embedding
    }

    override fun markDocumentChecked(documentId: String, checkedAt: Long) {
        documentRows.computeIfPresent(documentId) { _, row -> row.copy(checkedAt = checkedAt) }
    }

    override fun deleteDocuments(documentIds: Collection<String>) {
        documentIds.forEach { id -> documentRows.remove(id) }
    }

    override fun deleteDocumentsByWorkspace(workspaceId: String) {
        documentRows.values.removeIf { row -> row.workspaceId == workspaceId }
    }

    override fun folder(folderId: String): StoredFolderEmbedding? = folderRows[folderId]

    override fun folders(folderIds: Collection<String>): List<StoredFolderEmbedding> =
        folderIds.mapNotNull { id -> folderRows[id] }

    override fun upsertFolder(embedding: StoredFolderEmbedding) {
        folderRows[embedding.folderId] = embedding
    }

    override fun deleteFolders(folderIds: Collection<String>) {
        folderIds.forEach { id -> folderRows.remove(id) }
    }

    override fun deleteFoldersByWorkspace(workspaceId: String) {
        folderRows.values.removeIf { row -> row.workspaceId == workspaceId }
    }

    override fun rankFolders(workspaceId: String, query: FloatArray, model: String, limit: Int): List<RankedFolder> =
        folderRows.values
            .filter { row -> row.workspaceId == workspaceId && row.model == model && row.vector.size == query.size }
            .map { row -> RankedFolder(row.folderId, row.kind, EmbeddingVectors.cosine(row.vector, query)) }
            .sortedByDescending { ranked -> ranked.score }
            .take(limit)

    fun clear() {
        documentRows.clear()
        folderRows.clear()
    }
}

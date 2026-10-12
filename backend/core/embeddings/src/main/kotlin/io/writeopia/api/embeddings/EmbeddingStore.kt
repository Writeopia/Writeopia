package io.writeopia.api.embeddings

/** The embedding of a document and the text it was made from, to tell later how much changed. */
data class StoredDocumentEmbedding(
    val documentId: String,
    val workspaceId: String,
    val model: String,
    val vector: FloatArray,
    val sourceTitle: String,
    val sourceText: String,
    val embeddedAt: Long,
    /** When the document was last compared with this text, embedded or not; never before [embeddedAt]. */
    val checkedAt: Long = embeddedAt,
)

/** The part of a document embedding that gates a refresh, read without the text or the vector. */
data class DocumentEmbeddingMeta(
    val documentId: String,
    val model: String,
    val embeddedAt: Long,
    val checkedAt: Long = embeddedAt,
)

/** How a folder's vector was made. */
enum class FolderEmbeddingKind(val dbValue: String) {
    /** The mean of the documents in the folder tree. */
    CENTROID("centroid"),

    /** The embedding of the folder's title, for a folder without documents. */
    TITLE("title");

    companion object {
        fun fromDb(value: String): FolderEmbeddingKind =
            entries.firstOrNull { it.dbValue == value } ?: CENTROID
    }
}

data class StoredFolderEmbedding(
    val folderId: String,
    val workspaceId: String,
    val model: String,
    /** For [FolderEmbeddingKind.CENTROID], the raw mean. Cosine similarity ignores its length. */
    val vector: FloatArray,
    val kind: FolderEmbeddingKind,
    /** The documents in the folder tree the vector stands for; 0 for [FolderEmbeddingKind.TITLE]. */
    val documentCount: Int,
    val sourceTitle: String,
    val updatedAt: Long,
)

/** A folder ranked against a query: [score] is the cosine similarity, higher is closer. */
data class RankedFolder(
    val folderId: String,
    val kind: FolderEmbeddingKind,
    val score: Double,
)

/**
 * Where document and folder embeddings live. The Postgres implementation uses pgvector; the
 * in-memory one serves tests, since the embedded Postgres of the test suites has no pgvector.
 */
interface EmbeddingStore {
    fun documentMeta(documentId: String): DocumentEmbeddingMeta?
    fun document(documentId: String): StoredDocumentEmbedding?
    fun documents(documentIds: Collection<String>): List<StoredDocumentEmbedding>
    fun upsertDocument(embedding: StoredDocumentEmbedding)

    /** Records that the document was compared with its embedded text at [checkedAt] and found not changed enough. */
    fun markDocumentChecked(documentId: String, checkedAt: Long)
    fun deleteDocuments(documentIds: Collection<String>)
    fun deleteDocumentsByWorkspace(workspaceId: String)

    fun folder(folderId: String): StoredFolderEmbedding?
    fun folders(folderIds: Collection<String>): List<StoredFolderEmbedding>
    fun upsertFolder(embedding: StoredFolderEmbedding)
    fun deleteFolders(folderIds: Collection<String>)
    fun deleteFoldersByWorkspace(workspaceId: String)

    /** The folders of the workspace closest to [query], best first, at most [limit]. */
    fun rankFolders(workspaceId: String, query: FloatArray, model: String, limit: Int): List<RankedFolder>
}

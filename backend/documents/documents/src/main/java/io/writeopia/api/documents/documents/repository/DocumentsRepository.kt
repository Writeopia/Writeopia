package io.writeopia.api.documents.documents.repository

import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.DocumentTag
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.extensions.sortWithOrderBy
import io.writeopia.sdk.models.sorting.OrderBy
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sql.WriteopiaDbBackend
import io.writeopia.databse.HikariCp
import io.writeopia.databse.tombstone.StoryStepTombstoneStore

private var documentSqlDao: DocumentSqlBeDao? = null

private fun WriteopiaDbBackend.getDocumentDaoFn(): DocumentSqlBeDao =
    documentSqlDao ?: kotlin.run {
        DocumentSqlBeDao(
            documentEntityQueries,
            storyStepEntityQueries,
            folderEntityQueries,
            userFavoriteEntityQueries,
            documentTagEntityQueries,
            documentTagLinkEntityQueries,
            StoryStepTombstoneStore(HikariCp.driver()),
        ).also {
            documentSqlDao = it
        }
    }

/** The deleted steps of the database, kept for the devices that still have them. */
fun WriteopiaDbBackend.storyStepTombstones(): StoryStepTombstoneStore =
    tombstoneStore ?: StoryStepTombstoneStore(HikariCp.driver()).also { tombstoneStore = it }

private var tombstoneStore: StoryStepTombstoneStore? = null

fun WriteopiaDbBackend.saveDocument(vararg documents: Document) {
    val dao = getDocumentDaoFn()
    documents.forEach(dao::insertDocumentWithContent)
}

/**
 * Upserts [folders], skipping any whose id is tombstoned (deleted earlier).
 *
 * @return the ids that were dropped because they are tombstoned.
 */
suspend fun WriteopiaDbBackend.saveFolder(vararg folders: Folder): List<String> {
    val dao = getDocumentDaoFn()
    return folders.filterNot { folder -> dao.insertFolder(folder) }.map { folder -> folder.id }
}

fun WriteopiaDbBackend.isFolderTombstoned(folderId: String): Boolean =
    getDocumentDaoFn().isFolderTombstoned(folderId)

fun WriteopiaDbBackend.saveDocumentTag(vararg tags: DocumentTag) {
    val dao = getDocumentDaoFn()
    tags.forEach(dao::insertDocumentTag)
}

/** The whole tag catalogue of the workspace, tombstones included. */
fun WriteopiaDbBackend.allDocumentTagsByWorkspaceId(workspaceId: String): List<DocumentTag> =
    getDocumentDaoFn().loadDocumentTagsByWorkspaceId(workspaceId)

suspend fun WriteopiaDbBackend.documentsDiffByFolder(
    folderId: String,
    workspaceId: String,
    lastSync: Long,
    orderBy: String = "last_updated_at"
): List<Document> =
    getDocumentDaoFn()
        .loadDocumentsWithContentFolderIdAfterTime(folderId, workspaceId, lastSync)
        .sortWithOrderBy(OrderBy.fromString(orderBy))

suspend fun WriteopiaDbBackend.documentsDiffByWorkspace(
    workspaceId: String,
    lastSync: Long,
    orderBy: String = "last_updated_at"
): List<Document> =
    getDocumentDaoFn()
        .loadDocumentsWithContentByWorkspaceIdAfterTime(workspaceId, lastSync)
        .sortWithOrderBy(OrderBy.fromString(orderBy))

suspend fun WriteopiaDbBackend.allFoldersByWorkspaceId(workspaceId: String): List<Folder> {
    return getDocumentDaoFn().loadAllFoldersByWorkspaceId(workspaceId)
}

fun WriteopiaDbBackend.getDocumentsByParentId(parentId: String = "root"): List<Document> =
    getDocumentDaoFn().loadDocumentByParentId(parentId)

suspend fun WriteopiaDbBackend.getDocumentById(
    id: String = "test",
    workspaceId: String
): Document? = getDocumentDaoFn().loadDocumentById(id, workspaceId)

suspend fun WriteopiaDbBackend.getDocumentWithContentById(
    id: String, workspaceId: String
): Document? = getDocumentDaoFn().loadDocumentWithContentById(id, workspaceId)

suspend fun WriteopiaDbBackend.getDocumentByTitle(
    title: String, workspaceId: String
): Document? = getDocumentDaoFn().loadDocumentWithContentByTitle(title, workspaceId)

suspend fun WriteopiaDbBackend.getFolderById(id: String = "test", workspaceId: String): Folder? =
    getDocumentDaoFn().loadFolderById(id, workspaceId)

suspend fun WriteopiaDbBackend.getIdsByParentId(parentId: String = "root"): List<String> =
    getDocumentDaoFn().loadDocumentIdsByParentId(parentId)

/** The documents of a folder with only their first [stepsPerDocument] top-level steps. */
fun WriteopiaDbBackend.getDocumentsPreviewByParentId(
    parentId: String,
    workspaceId: String,
    stepsPerDocument: Int
): List<Document> = getDocumentDaoFn().loadDocumentsPreviewByParentId(parentId, workspaceId, stepsPerDocument)

fun WriteopiaDbBackend.getFoldersByParentId(parentId: String, workspaceId: String): List<Folder> =
    getDocumentDaoFn().loadFoldersByParentId(parentId, workspaceId)

fun WriteopiaDbBackend.getFoldersByParentId(parentId: String = "root"): List<Folder> =
    getDocumentDaoFn().loadFoldersByParentId(parentId)

suspend fun WriteopiaDbBackend.deleteDocumentById(vararg documentIds: String) {
    val dao = getDocumentDaoFn()
    documentIds.forEach(dao::deleteDocumentById)
}

fun WriteopiaDbBackend.deleteFolder(folderId: String, workspaceId: String) {
    getDocumentDaoFn().deleteFolder(folderId, workspaceId)
}

fun WriteopiaDbBackend.deleteDocumentsByFolderId(folderId: String) {
    getDocumentDaoFn().deleteDocumentsByFolderId(folderId)
}

fun WriteopiaDbBackend.moveFolderToFolder(folderId: String, parentId: String) {
    getDocumentDaoFn().moveFolderToFolder(folderId, parentId)
}

fun WriteopiaDbBackend.moveDocumentToFolder(documentId: String, parentId: String) {
    getDocumentDaoFn().moveToFolder(documentId, parentId)
}

suspend fun WriteopiaDbBackend.deleteDocumentsByIds(documentIds: List<String>) {
    val dao = getDocumentDaoFn()
    dao.deleteDocumentByIds(documentIds.toSet())
}

fun WriteopiaDbBackend.addUserFavorite(userId: String, documentId: String, workspaceId: String) {
    getDocumentDaoFn().addUserFavorite(userId, documentId, workspaceId)
}

fun WriteopiaDbBackend.removeUserFavorite(userId: String, documentId: String) {
    getDocumentDaoFn().removeUserFavorite(userId, documentId)
}

fun WriteopiaDbBackend.isUserFavorite(userId: String, documentId: String): Boolean {
    return getDocumentDaoFn().isUserFavorite(userId, documentId)
}

fun WriteopiaDbBackend.getUserFavoriteDocumentIds(userId: String, workspaceId: String): List<String> {
    return getDocumentDaoFn().getUserFavoriteDocumentIds(userId, workspaceId)
}

// StoryStep Sync Operations

/**
 * Upserts a StoryStep with a specific timestamp for conflict resolution.
 */
fun WriteopiaDbBackend.upsertStoryStep(
    storyStep: StoryStep,
    position: Double,
    documentId: String,
    lastUpdatedAt: Long
) {
    getDocumentDaoFn().upsertStoryStep(storyStep, position, documentId, lastUpdatedAt)
}

/**
 * Gets StorySteps for a document that were updated after the given timestamp.
 */
fun WriteopiaDbBackend.getStoryStepsAfterTime(
    documentId: String,
    afterTime: Long
): List<Pair<Double, StoryStep>> {
    return getDocumentDaoFn().getStoryStepsAfterTime(documentId, afterTime)
}

/**
 * Gets a single StoryStep by its ID.
 */
fun WriteopiaDbBackend.getStoryStepById(storyStepId: String): Pair<Double, StoryStep>? {
    return getDocumentDaoFn().getStoryStepById(storyStepId)
}

/**
 * The id of the document that holds the StoryStep with [storyStepId], or null when there is none.
 */
fun WriteopiaDbBackend.getStoryStepDocumentId(storyStepId: String): String? {
    return getDocumentDaoFn().getStoryStepDocumentId(storyStepId)
}

/**
 * Deletes a StoryStep by its ID.
 */
fun WriteopiaDbBackend.deleteStoryStepById(storyStepId: String) {
    getDocumentDaoFn().deleteStoryStepById(storyStepId)
}

/**
 * Deletes multiple StorySteps by their IDs.
 */
fun WriteopiaDbBackend.deleteStoryStepsByIds(storyStepIds: List<String>) {
    getDocumentDaoFn().deleteStoryStepsByIds(storyStepIds)
}

/** Deletes [stepIds] where they belong to a document of [workspaceId]. */
fun WriteopiaDbBackend.deleteStoryStepsByIdsForWorkspace(stepIds: Collection<String>, workspaceId: String) {
    getDocumentDaoFn().deleteStoryStepsByIdsForWorkspace(stepIds, workspaceId)
}

/** The document of each of [stepIds], for the ones that exist. */
fun WriteopiaDbBackend.storyStepDocumentIds(stepIds: Collection<String>): Map<String, String> =
    getDocumentDaoFn().storyStepDocumentIds(stepIds)

// Document Publishing Operations

/**
 * Gets a published document by ID with its content.
 * Returns null if the document doesn't exist or is not published.
 */
suspend fun WriteopiaDbBackend.getPublishedDocumentById(documentId: String): Document? =
    getDocumentDaoFn().loadPublishedDocumentWithContentById(documentId)

/**
 * Sets the published status of a document.
 */
fun WriteopiaDbBackend.setDocumentPublished(documentId: String, published: Boolean) {
    getDocumentDaoFn().setDocumentPublished(documentId, published)
}

/**
 * Checks if a document is published.
 */
fun WriteopiaDbBackend.isDocumentPublished(documentId: String): Boolean {
    return getDocumentDaoFn().isDocumentPublished(documentId)
}

/**
 * Gets all document IDs in a workspace (lightweight query for memory-efficient processing).
 */
fun WriteopiaDbBackend.getDocumentIdsByWorkspaceId(workspaceId: String): List<String> =
    getDocumentDaoFn().loadDocumentIdsByWorkspaceId(workspaceId)

/**
 * Updates only the document title without affecting story steps.
 */
fun WriteopiaDbBackend.updateDocumentTitle(documentId: String, title: String) {
    getDocumentDaoFn().updateDocumentTitle(documentId, title)
}

/**
 * Records a change of the document's steps on the document row, so the next folder diff of
 * the other devices includes it.
 */
fun WriteopiaDbBackend.touchDocument(documentId: String, lastUpdatedAt: Long, lastSynced: Long) {
    getDocumentDaoFn().touchDocument(documentId, lastUpdatedAt, lastSynced)
}

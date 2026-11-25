@file:OptIn(ExperimentalTime::class)

package io.writeopia.core.folders.sync.cloud

import io.writeopia.core.folders.repository.folder.FolderRepository
import io.writeopia.sdk.models.document.Document
import io.writeopia.sdk.models.document.Folder
import io.writeopia.sdk.models.story.StoryStep
import io.writeopia.sdk.models.story.StoryTypes
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.persistence.core.repository.InMemoryDocumentRepository
import io.writeopia.sdk.utils.iterables.Rank
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class CloudWorkspaceSyncTest {

    private val workspaceId = "disconnected_user"
    private val cloud = FakeCloudFileStore()

    private val deviceA = Device("deviceA")
    private val deviceB = Device("deviceB")

    @Test
    fun `a document written on one device arrives on the other`() = runBlocking {
        deviceA.documents.saveDocument(document("doc1", step("a", 1, "hello")))

        deviceA.syncOk()
        deviceB.syncOk()

        val received = assertNotNull(deviceB.documents.loadDocumentById("doc1", workspaceId))
        assertEquals("hello", received.step("a").text)
        assertTrue(cloud.names().contains("doc.deviceA.doc1.json"))
    }

    @Test
    fun `a received document is not written back as the receiver's own copy`() = runBlocking {
        deviceA.documents.saveDocument(document("doc1", step("a", 1)))
        deviceA.syncOk()
        deviceB.syncOk()

        assertTrue(cloud.names().none { name -> name.startsWith("doc.deviceB.") })
    }

    @Test
    fun `edits of different steps on two devices are merged`() = runBlocking {
        deviceA.documents.saveDocument(document("doc1", step("a", 1, "a1"), step("b", 1, "b1")))
        deviceA.syncOk()
        deviceB.syncOk()

        deviceA.documents.saveStorySteps(listOf(Rank.at(0) to step("a", 10, "a from A")), "doc1")
        deviceA.touch("doc1")
        deviceB.documents.saveStorySteps(listOf(Rank.at(1) to step("b", 20, "b from B")), "doc1")
        deviceB.touch("doc1")

        deviceA.syncOk()
        deviceB.syncOk()
        deviceA.syncOk()

        for (device in listOf(deviceA, deviceB)) {
            val document = assertNotNull(device.documents.loadDocumentById("doc1", workspaceId))
            assertEquals("a from A", document.step("a").text, device.id)
            assertEquals("b from B", document.step("b").text, device.id)
        }
    }

    @Test
    fun `the newer edit of the same step wins`() = runBlocking {
        deviceA.documents.saveDocument(document("doc1", step("a", 1, "first")))
        deviceA.syncOk()
        deviceB.syncOk()

        deviceA.documents.saveStorySteps(listOf(Rank.at(0) to step("a", 10, "older")), "doc1")
        deviceA.touch("doc1")
        deviceB.documents.saveStorySteps(listOf(Rank.at(0) to step("a", 20, "newer")), "doc1")
        deviceB.touch("doc1")

        deviceA.syncOk()
        deviceB.syncOk()
        deviceA.syncOk()

        assertEquals("newer", deviceA.documents.loadDocumentById("doc1", workspaceId)!!.step("a").text)
        assertEquals("newer", deviceB.documents.loadDocumentById("doc1", workspaceId)!!.step("a").text)
    }

    @Test
    fun `a step deleted on one device is removed on the other and never comes back`() = runBlocking {
        deviceA.documents.saveDocument(document("doc1", step("a", 1), step("b", 1)))
        deviceA.syncOk()
        deviceB.syncOk()

        deviceB.documents.deleteStoryStep("b", "doc1")
        deviceB.touch("doc1")
        deviceB.syncOk()
        deviceA.syncOk()

        assertNull(deviceA.documents.loadDocumentById("doc1", workspaceId)!!.stepOrNull("b"))
        // The tombstone is in the cloud now, not on the device.
        assertTrue(deviceB.documents.loadStoryStepTombstones(workspaceId).isEmpty())

        // A's file still had "b" when B synced; a new device must not bring it back.
        val deviceC = Device("deviceC")
        deviceC.syncOk()
        assertNull(deviceC.documents.loadDocumentById("doc1", workspaceId)!!.stepOrNull("b"))
    }

    @Test
    fun `a document deleted on one device is deleted everywhere, its files too`() = runBlocking {
        deviceA.documents.saveDocument(document("doc1", step("a", 1)))
        deviceA.syncOk()
        deviceB.syncOk()
        // B edits it, so B has its own copy in the cloud.
        deviceB.documents.saveStorySteps(listOf(Rank.at(0) to step("a", 5, "edited")), "doc1")
        deviceB.touch("doc1")
        deviceB.syncOk()

        // Soft deleted on A, the way the app deletes.
        val doc = deviceA.documents.loadDocumentById("doc1", workspaceId)!!
        deviceA.documents.saveDocument(doc.copy(deleted = true))
        deviceA.syncOk()
        deviceB.syncOk()

        assertNull(deviceA.documents.loadDocumentById("doc1", workspaceId))
        assertNull(deviceB.documents.loadDocumentById("doc1", workspaceId))
        assertTrue(cloud.names().none { name -> name.startsWith("doc.") }, cloud.names().toString())

        val deviceC = Device("deviceC")
        deviceC.syncOk()
        assertNull(deviceC.documents.loadDocumentById("doc1", workspaceId))
    }

    @Test
    fun `folders arrive, the newer copy wins, and a deleted folder goes everywhere`() = runBlocking {
        deviceA.folders.createFolder(folder("f1", "Work", updatedAt = 1))
        deviceA.syncOk()
        deviceB.syncOk()
        assertEquals("Work", deviceB.folders.getFolderById("f1")?.title)

        deviceB.folders.updateFolder(deviceB.folders.getFolderById("f1")!!.copy(title = "Job", lastUpdatedAt = Clock.System.now()))
        deviceB.syncOk()
        deviceA.syncOk()
        assertEquals("Job", deviceA.folders.getFolderById("f1")?.title)

        deviceA.folders.deleteFolderById("f1", workspaceId)
        deviceA.syncOk()
        deviceB.syncOk()
        assertNull(deviceA.folders.getFolderById("f1"))
        assertNull(deviceB.folders.getFolderById("f1"))
    }

    @Test
    fun `an unchanged file of another device is not downloaded again`() = runBlocking {
        deviceA.documents.saveDocument(document("doc1", step("a", 1)))
        deviceA.syncOk()
        deviceB.syncOk()

        val docFileId = cloud.idOf("doc.deviceA.doc1.json")
        val before = cloud.downloads.count { id -> id == docFileId }
        deviceB.syncOk()

        assertEquals(before, cloud.downloads.count { id -> id == docFileId })
    }

    @Test
    fun `a failure of the store is reported and nothing local is lost`() = runBlocking {
        deviceA.documents.saveDocument(document("doc1", step("a", 1)))
        deviceA.documents.deleteStoryStep("a", "doc1")
        cloud.failing = true

        val result = deviceA.sync.sync(workspaceId)

        assertTrue(result is ResultData.Error)
        assertEquals(1, deviceA.documents.loadStoryStepTombstones(workspaceId).size)
        assertNull(deviceA.documents.loadDocumentById("doc1", workspaceId)!!.lastSyncedAt)
    }

    @Test
    fun `file names are parsed back`() {
        assertEquals(
            CloudSyncFile.DocumentFile("dev", "doc.with.dots"),
            CloudSyncFile.parse(CloudSyncFile.documentName("dev", "doc.with.dots"))
        )
        assertEquals(CloudSyncFile.FoldersFile("dev"), CloudSyncFile.parse(CloudSyncFile.foldersName("dev")))
        assertEquals(CloudSyncFile.DeletionsFile("dev"), CloudSyncFile.parse(CloudSyncFile.deletionsName("dev")))
        assertNull(CloudSyncFile.parse("something-else.json"))
        assertNull(CloudSyncFile.parse("folders.a.b.json"))
    }

    private inner class Device(val id: String) {
        val documents = InMemoryDocumentRepository()
        val folders = FakeFolderRepository()
        val sync = CloudWorkspaceSync(
            documentRepository = documents,
            folderRepository = folders,
            store = cloud,
            state = InMemoryCloudSyncStateStore(id),
        )

        suspend fun syncOk() {
            val result = sync.sync(workspaceId)
            assertTrue(result is ResultData.Complete, "sync of $id: $result")
        }

        /** An edit made here: the document is newer than its last sync. */
        suspend fun touch(documentId: String) {
            val document = documents.loadDocumentById(documentId, workspaceId)!!
            documents.saveDocumentMetadata(document.copy(lastUpdatedAt = Clock.System.now()))
        }
    }

    private fun step(id: String, lastUpdatedAt: Long, text: String = id) =
        StoryStep(id = id, type = StoryTypes.TEXT.type, text = text, lastUpdatedAt = lastUpdatedAt)

    private fun document(id: String, vararg steps: StoryStep) =
        Document(
            id = id,
            title = "Doc",
            content = steps.mapIndexed { index, step -> Rank.at(index) to step }.toMap(),
            createdAt = at(0),
            lastUpdatedAt = Clock.System.now(),
            lastSyncedAt = null,
            workspaceId = workspaceId,
            parentId = "root"
        )

    private fun folder(id: String, title: String, updatedAt: Long) = Folder(
        id = id,
        parentId = Folder.ROOT_PATH,
        title = title,
        createdAt = at(0),
        lastUpdatedAt = at(updatedAt),
        workspaceId = workspaceId,
        itemCount = 0,
    )

    private fun at(millis: Long) = Instant.fromEpochMilliseconds(millis)

    private fun Document.stepOrNull(id: String): StoryStep? = content.values.firstOrNull { step -> step.id == id }

    private fun Document.step(id: String): StoryStep = assertNotNull(stepOrNull(id), "step $id")
}

/** A cloud folder in memory. Each write bumps the file's version, like Drive. */
class FakeCloudFileStore : CloudFileStore {
    private data class Stored(val name: String, val content: String, val version: Int)

    private val files = linkedMapOf<String, Stored>()
    private var nextId = 0
    val downloads = mutableListOf<String>()
    var failing = false

    fun names(): List<String> = files.values.map { it.name }

    fun idOf(name: String): String = files.entries.first { it.value.name == name }.key

    override suspend fun list(): List<CloudFile> {
        check()
        return files.map { (id, file) -> CloudFile(id, file.name, file.version.toString()) }
    }

    override suspend fun download(fileId: String): String {
        check()
        downloads += fileId
        return files.getValue(fileId).content
    }

    override suspend fun create(name: String, content: String): CloudFile {
        check()
        val id = "file${nextId++}"
        files[id] = Stored(name, content, 1)
        return CloudFile(id, name, "1")
    }

    override suspend fun update(fileId: String, content: String): CloudFile {
        check()
        val file = files.getValue(fileId)
        val updated = file.copy(content = content, version = file.version + 1)
        files[fileId] = updated
        return CloudFile(fileId, updated.name, updated.version.toString())
    }

    override suspend fun delete(fileId: String) {
        check()
        files.remove(fileId)
    }

    private fun check() {
        if (failing) throw CloudStoreException("offline")
    }
}

/** Folders in memory, with soft delete and the outdated check of the real repositories. */
@OptIn(ExperimentalTime::class)
class FakeFolderRepository : FolderRepository {
    private val folders = linkedMapOf<String, Folder>()

    override suspend fun getFolderById(id: String): Folder? = folders[id]

    override suspend fun getFolderByParentId(parentId: String, workspaceId: String) =
        folders.values.filter { it.parentId == parentId && !it.deleted }

    override suspend fun getFoldersForWorkspaceAfterTime(workspaceId: String, instant: Instant) =
        folders.values.filter { it.lastUpdatedAt > instant }

    override suspend fun getFoldersForWorkspace(workspaceId: String) = folders.values.toList()

    override suspend fun createFolder(folder: Folder) {
        folders[folder.id] = folder
    }

    override suspend fun updateFolder(folder: Folder) {
        folders[folder.id] = folder
    }

    override suspend fun setLastUpdated(folderId: String, long: Long) {}

    override suspend fun deleteFolderById(folderId: String, workspaceId: String) {
        folders[folderId]?.let { folders[folderId] = it.copy(deleted = true) }
    }

    override suspend fun deleteFolderByParent(folderId: String, workspaceId: String) {}

    override suspend fun hardDeleteFolderById(folderId: String, workspaceId: String) {
        folders.remove(folderId)
    }

    override suspend fun getSoftDeletedFolders(workspaceId: String) = folders.values.filter { it.deleted }

    override suspend fun favoriteDocumentByIds(ids: Set<String>) {}

    override suspend fun unFavoriteDocumentByIds(ids: Set<String>) {}

    override suspend fun moveToFolder(documentId: String, parentId: String) {}

    override suspend fun refreshFolders() {}

    override suspend fun listenForFoldersByParentId(parentId: String, workspaceId: String): Flow<Map<String, List<Folder>>> =
        flowOf(emptyMap())

    override suspend fun listenForFavoriteFolders(workspaceId: String): Flow<List<Folder>> = flowOf(emptyList())

    override suspend fun stopListeningForFoldersByParentId(parentId: String, workspaceId: String) {}

    override suspend fun localOutDatedFolders(workspaceId: String) = folders.values.filter { folder ->
        val syncedAt = folder.lastSyncedAt
        !folder.deleted && (syncedAt == null || folder.lastUpdatedAt > syncedAt)
    }

    override suspend fun getContentSyncedAt(folderId: String, workspaceId: String): Instant? = null

    override suspend fun setContentSyncedAt(folderId: String, workspaceId: String, contentSyncedAt: Instant) {}
}

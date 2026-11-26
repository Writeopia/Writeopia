package io.writeopia.core.folders.sync.cloud

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.persistence.core.repository.InMemoryDocumentRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CloudSyncManagerTest {

    private class FakeConnector(var connected: Boolean = false, var token: String? = "t") : CloudAccountConnector {
        override val providerName = "Fake Drive"
        var refuse = false

        override suspend fun isConnected() = connected

        override suspend fun connect(): Result<Unit> =
            if (refuse) Result.failure(RuntimeException("refused")) else Result.success(Unit).also { connected = true }

        override suspend fun disconnect() {
            connected = false
        }

        override suspend fun accessToken(forceRefresh: Boolean) = token
    }

    private val cloud = FakeCloudFileStore()
    private val stateStore = InMemoryCloudSyncStateStore("device")
    private val connector = FakeConnector()
    private var workspaceId = "disconnected_user"

    private val manager = CloudSyncManager(
        connector = connector,
        cloudSync = {
            CloudWorkspaceSync(InMemoryDocumentRepository(), FakeFolderRepository(), cloud, stateStore)
        },
        stateStore = stateStore,
        currentWorkspaceId = { workspaceId },
    )

    @Test
    fun `a connection whose reset fails is undone, so nothing syncs incomplete`() = runBlocking<Unit> {
        val brokenState = object : CloudSyncStateStore by stateStore {
            override suspend fun saveSeenVersions(versions: Map<String, String>) =
                throw CloudStoreException("state can't be written")

            override suspend fun clearSeenVersions() = saveSeenVersions(emptyMap())
        }
        val manager = CloudSyncManager(
            connector = connector,
            cloudSync = { CloudWorkspaceSync(InMemoryDocumentRepository(), FakeFolderRepository(), cloud, brokenState) },
            stateStore = brokenState,
            currentWorkspaceId = { workspaceId },
        )

        assertTrue(manager.connect().isFailure)

        assertFalse(connector.connected)
        assertFalse(manager.status.value.connected)
        assertEquals("state can't be written", manager.status.value.error)
        assertIs<ResultData.Idle<Unit>>(manager.syncNow())
    }

    @Test
    fun `only the offline workspace of a connected account syncs`() = runBlocking<Unit> {
        assertIs<ResultData.Idle<Unit>>(manager.syncNow())

        connector.connected = true
        workspaceId = "a_backend_workspace"
        assertFalse(manager.isActive())
        assertIs<ResultData.Idle<Unit>>(manager.syncNow())

        workspaceId = "disconnected_user"
        assertTrue(manager.isActive())
        assertIs<ResultData.Complete<Unit>>(manager.syncNow())
        assertNotNull(manager.status.value.lastSync)
    }

    @Test
    fun `connecting shows the account as connected, a refusal shows why`() = runBlocking<Unit> {
        connector.refuse = true
        assertTrue(manager.connect().isFailure)
        assertEquals("refused", manager.status.value.error)
        assertFalse(manager.status.value.connected)

        connector.refuse = false
        assertTrue(manager.connect().isSuccess)
        assertTrue(manager.status.value.connected)
        assertEquals(null, manager.status.value.error)
    }

    @Test
    fun `a failed sync is shown, and a lost access asks to connect again`() = runBlocking<Unit> {
        connector.connected = true
        cloud.failing = true
        manager.syncNow()
        assertNotNull(manager.status.value.error)
        assertFalse(manager.status.value.needsReconnect)

        cloud.failing = false
        connector.token = null
        val result = CloudSyncManager(
            connector = connector,
            cloudSync = {
                CloudWorkspaceSync(
                    InMemoryDocumentRepository(),
                    FakeFolderRepository(),
                    GoogleDriveFileStore(HttpClient(MockEngine { error("no call") })) { null },
                    stateStore,
                )
            },
            stateStore = stateStore,
            currentWorkspaceId = { workspaceId },
        ).also { it.syncNow() }
        assertTrue(result.status.value.needsReconnect)
    }

    @Test
    fun `disconnecting forgets what was taken in, so a new account takes everything in`() = runBlocking<Unit> {
        connector.connected = true
        stateStore.saveSeenVersions(mapOf("file" to "1"))

        manager.disconnect()

        assertFalse(manager.status.value.connected)
        assertTrue(stateStore.seenVersions().isEmpty())
        assertEquals("device", stateStore.deviceId())
    }
}

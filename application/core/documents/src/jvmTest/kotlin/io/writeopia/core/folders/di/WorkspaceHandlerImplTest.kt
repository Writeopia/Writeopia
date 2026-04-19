package io.writeopia.core.folders.di

import io.mockk.coEvery
import io.mockk.mockk
import io.writeopia.auth.core.data.WorkspaceApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.sync.ConfigFileWatcher
import io.writeopia.core.folders.sync.WorkspaceSync
import io.writeopia.models.interfaces.configuration.WorkspaceConfigRepository
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceHandlerImplTest {

    private var testScope: CoroutineScope? = null

    @AfterTest
    fun tearDown() {
        testScope?.cancel()
    }

    @Test
    fun `startAutoSync should start watching when path is not blank`() = runBlocking {
        val fakeWatcher = FakeConfigFileWatcher()
        val handler = createHandler(configFileWatcher = fakeWatcher, savedPath = "/some/path")
        val scope = CoroutineScope(Dispatchers.Default + Job())
        testScope = scope
        handler.initScope(scope)

        // Set a path first
        handler.changeWorkspaceLocalPath("/some/path")
        delay(100) // Wait for coroutine

        handler.startAutoSync()

        assertTrue(fakeWatcher.isWatching)
        assertTrue(handler.isAutoSyncEnabled.value)
    }

    @Test
    fun `startAutoSync should not start watching when path is blank`() = runBlocking {
        val fakeWatcher = FakeConfigFileWatcher()
        val handler = createHandler(configFileWatcher = fakeWatcher, savedPath = null)
        val scope = CoroutineScope(Dispatchers.Default + Job())
        testScope = scope
        handler.initScope(scope)

        handler.startAutoSync()

        assertFalse(fakeWatcher.isWatching)
        assertFalse(handler.isAutoSyncEnabled.value)
    }

    @Test
    fun `stopAutoSync should stop watching`() = runBlocking {
        val fakeWatcher = FakeConfigFileWatcher()
        val handler = createHandler(configFileWatcher = fakeWatcher, savedPath = "/some/path")
        val scope = CoroutineScope(Dispatchers.Default + Job())
        testScope = scope
        handler.initScope(scope)

        handler.changeWorkspaceLocalPath("/some/path")
        delay(100)

        handler.startAutoSync()
        assertTrue(fakeWatcher.isWatching)

        handler.stopAutoSync()

        assertFalse(fakeWatcher.isWatching)
        assertFalse(handler.isAutoSyncEnabled.value)
    }

    @Test
    fun `localSyncRequired should emit when configChanges emits non-null`() = runBlocking {
        val fakeWatcher = FakeConfigFileWatcher()
        val handler = createHandler(configFileWatcher = fakeWatcher)
        val scope = CoroutineScope(Dispatchers.Default + Job())
        testScope = scope
        handler.initScope(scope)

        var emissionCount = 0
        val job = scope.launch {
            handler.localSyncRequired.collect {
                emissionCount++
            }
        }

        // Give time for collector to start
        delay(50)

        // Emit a config change
        fakeWatcher.emitChange(1000L)
        delay(50)

        assertEquals(1, emissionCount)

        // Emit another change
        fakeWatcher.emitChange(2000L)
        delay(50)

        assertEquals(2, emissionCount)

        job.cancel()
    }

    @Test
    fun `localSyncRequired should not emit when configChanges emits null`() = runBlocking {
        val fakeWatcher = FakeConfigFileWatcher()
        val handler = createHandler(configFileWatcher = fakeWatcher)
        val scope = CoroutineScope(Dispatchers.Default + Job())
        testScope = scope
        handler.initScope(scope)

        var emissionCount = 0
        val job = scope.launch {
            handler.localSyncRequired.collect {
                emissionCount++
            }
        }

        // Give time for collector to start
        delay(50)

        // Emit null (should not trigger localSyncRequired)
        fakeWatcher.emitChange(null)
        delay(50)

        assertEquals(0, emissionCount)

        job.cancel()
    }

    @Test
    fun `changeWorkspaceLocalPath should restart watcher if auto-sync was enabled`() = runBlocking {
        val fakeWatcher = FakeConfigFileWatcher()
        var savedPath = "/path/one"
        val mockConfigRepo = mockk<WorkspaceConfigRepository> {
            coEvery { saveWorkspacePath(any(), any()) } answers {
                savedPath = firstArg()
            }
            coEvery { loadWorkspacePath(any()) } answers { savedPath }
            coEvery { isOnboarded() } returns true
            coEvery { setOnboarded() } returns Unit
        }
        val handler = createHandler(
            configFileWatcher = fakeWatcher,
            workspaceConfigRepository = mockConfigRepo
        )
        val scope = CoroutineScope(Dispatchers.Default + Job())
        testScope = scope
        handler.initScope(scope)

        // Start auto-sync with initial path
        handler.changeWorkspaceLocalPath("/path/one")
        delay(100)
        handler.startAutoSync()
        assertTrue(fakeWatcher.isWatching)
        assertEquals("/path/one", fakeWatcher.lastWatchedPath)

        // Change path - should restart watcher
        handler.changeWorkspaceLocalPath("/path/two")
        delay(100)

        assertTrue(fakeWatcher.isWatching)
        assertEquals("/path/two", fakeWatcher.lastWatchedPath)
    }

    @Test
    fun `changeWorkspaceLocalPath should not start watcher if auto-sync was not enabled`() = runBlocking {
        val fakeWatcher = FakeConfigFileWatcher()
        val handler = createHandler(configFileWatcher = fakeWatcher, savedPath = "/path/one")
        val scope = CoroutineScope(Dispatchers.Default + Job())
        testScope = scope
        handler.initScope(scope)

        // Change path without enabling auto-sync
        handler.changeWorkspaceLocalPath("/path/one")
        delay(100)

        assertFalse(fakeWatcher.isWatching)
    }

    private fun createHandler(
        configFileWatcher: ConfigFileWatcher = FakeConfigFileWatcher(),
        workspaceConfigRepository: WorkspaceConfigRepository? = null,
        savedPath: String? = null
    ): WorkspaceHandlerImpl {
        val mockAuthRepo = mockk<AuthRepository> {
            coEvery { getUser() } returns WriteopiaUser.disconnectedUser()
            coEvery { getWorkspace() } returns Workspace.disconnectedWorkspace()
            coEvery { listenForUser() } returns MutableStateFlow(WriteopiaUser.disconnectedUser())
            coEvery { listenForWorkspace() } returns MutableStateFlow(Workspace.disconnectedWorkspace())
        }

        val mockWorkspaceApi = mockk<WorkspaceApi>()
        val mockWorkspaceSync = mockk<WorkspaceSync>()

        val mockConfigRepo = workspaceConfigRepository ?: mockk<WorkspaceConfigRepository> {
            coEvery { saveWorkspacePath(any(), any()) } returns Unit
            coEvery { loadWorkspacePath(any()) } returns savedPath
            coEvery { isOnboarded() } returns true
            coEvery { setOnboarded() } returns Unit
        }

        return WorkspaceHandlerImpl(
            authRepository = mockAuthRepo,
            workspaceApi = mockWorkspaceApi,
            workspaceSync = mockWorkspaceSync,
            workspaceConfigRepository = mockConfigRepo,
            configFileWatcher = configFileWatcher
        )
    }
}

private class FakeConfigFileWatcher : ConfigFileWatcher {
    private val _configChanges = MutableSharedFlow<Long?>()
    override val configChanges: Flow<Long?> = _configChanges

    private var _isWatching = false
    override val isWatching: Boolean get() = _isWatching

    var lastWatchedPath: String? = null
        private set

    override fun startWatching(workspacePath: String) {
        if (workspacePath.isNotBlank()) {
            _isWatching = true
            lastWatchedPath = workspacePath
        }
    }

    override fun stopWatching() {
        _isWatching = false
    }

    suspend fun emitChange(timestamp: Long?) {
        _configChanges.emit(timestamp)
    }
}

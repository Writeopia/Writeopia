@file:OptIn(ExperimentalTime::class)

package io.writeopia.auth.workspace

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.folders.sync.WorkspaceSync
import io.writeopia.sdk.models.user.Tier
import io.writeopia.sdk.models.user.WriteopiaUser
import io.writeopia.sdk.models.utils.ResultData
import io.writeopia.sdk.models.workspace.Workspace
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceSyncViewModelTest {

    private val workspace = Workspace(
        id = "ws1",
        userId = "user1",
        name = "Team",
        lastSync = Instant.DISTANT_PAST,
        selected = true,
        role = "admin",
    )

    @Test
    fun `the workspace is synced whole, forced, and the screen is done when it ends`() = runTest {
        val authRepository = premiumUser()
        val workspaceSync = mockk<WorkspaceSync>()
        coEvery { workspaceSync.syncWorkspace(workspace.id, force = true) } returns ResultData.Complete(Unit)
        val viewModel = WorkspaceSyncViewModel(authRepository, workspaceSync, syncScope = this)

        viewModel.startSync()
        assertEquals(WorkspaceSyncState.SYNCING, viewModel.state.value)
        advanceUntilIdle()

        assertEquals(WorkspaceSyncState.DONE, viewModel.state.value)
        coVerify(exactly = 1) { workspaceSync.syncWorkspace(workspace.id, force = true) }
    }

    @Test
    fun `a sync that fails says so instead of moving on by itself`() = runTest {
        val workspaceSync = mockk<WorkspaceSync>()
        coEvery { workspaceSync.syncWorkspace(workspace.id, force = true) } returns ResultData.Error()
        val viewModel = WorkspaceSyncViewModel(premiumUser(), workspaceSync, syncScope = this)

        viewModel.startSync()
        advanceUntilIdle()

        assertEquals(WorkspaceSyncState.FAILED, viewModel.state.value)
    }

    @Test
    fun `the offline workspace has nothing to sync`() = runTest {
        val authRepository = premiumUser(workspace = Workspace.disconnectedWorkspace())
        val workspaceSync = mockk<WorkspaceSync>()
        val viewModel = WorkspaceSyncViewModel(authRepository, workspaceSync, syncScope = this)

        viewModel.startSync()
        advanceUntilIdle()

        assertEquals(WorkspaceSyncState.DONE, viewModel.state.value)
        coVerify(exactly = 0) { workspaceSync.syncWorkspace(any(), any()) }
    }

    @Test
    fun `a free account has nothing to sync`() = runTest {
        val authRepository = premiumUser(tier = Tier.FREE)
        val workspaceSync = mockk<WorkspaceSync>()
        val viewModel = WorkspaceSyncViewModel(authRepository, workspaceSync, syncScope = this)

        viewModel.startSync()
        advanceUntilIdle()

        assertEquals(WorkspaceSyncState.DONE, viewModel.state.value)
        coVerify(exactly = 0) { workspaceSync.syncWorkspace(any(), any()) }
    }

    @Test
    fun `asking again while the sync runs doesn't start another`() = runTest {
        val workspaceSync = mockk<WorkspaceSync>()
        val running = CompletableDeferred<ResultData<Unit>>()
        coEvery { workspaceSync.syncWorkspace(workspace.id, force = true) } coAnswers { running.await() }
        val viewModel = WorkspaceSyncViewModel(premiumUser(), workspaceSync, syncScope = this)

        viewModel.startSync()
        advanceUntilIdle()
        viewModel.startSync()
        advanceUntilIdle()
        running.complete(ResultData.Complete(Unit))
        advanceUntilIdle()

        assertEquals(WorkspaceSyncState.DONE, viewModel.state.value)
        coVerify(exactly = 1) { workspaceSync.syncWorkspace(any(), any()) }
    }

    private fun premiumUser(
        workspace: Workspace = this.workspace,
        tier: Tier = Tier.PREMIUM,
    ): AuthRepository = mockk<AuthRepository>().also { authRepository ->
        coEvery { authRepository.getWorkspace() } returns workspace
        coEvery { authRepository.isLoggedIn() } returns true
        coEvery { authRepository.getUser() } returns WriteopiaUser(
            id = "user1",
            email = "user@writeopia.io",
            name = "User",
            tier = tier,
        )
    }
}

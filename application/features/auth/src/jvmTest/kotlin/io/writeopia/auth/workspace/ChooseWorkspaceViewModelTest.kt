package io.writeopia.auth.workspace

import io.mockk.coEvery
import io.mockk.mockk
import io.writeopia.LocalAiRepository
import io.writeopia.analytics.testing.FakeAnalyticsTracker
import io.writeopia.auth.core.data.WorkspaceApi
import io.writeopia.auth.core.manager.AuthRepository
import io.writeopia.core.configuration.repository.ConfigurationRepository
import io.writeopia.core.folders.repository.folder.NotesUseCase
import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ChooseWorkspaceViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var authRepository: AuthRepository
    private lateinit var workspaceApi: WorkspaceApi
    private lateinit var configRepository: ConfigurationRepository
    private lateinit var notesUseCase: NotesUseCase
    private lateinit var localAiRepository: LocalAiRepository
    private lateinit var analyticsTracker: FakeAnalyticsTracker

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        workspaceApi = mockk(relaxed = true)
        configRepository = mockk(relaxed = true)
        notesUseCase = mockk(relaxed = true)
        localAiRepository = mockk(relaxed = true)
        analyticsTracker = FakeAnalyticsTracker()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ChooseWorkspaceViewModel(
        authRepository = authRepository,
        workspaceApi = workspaceApi,
        configRepository = configRepository,
        notesUseCase = notesUseCase,
        localAiRepository = localAiRepository,
        analyticsTracker = analyticsTracker
    )

    @Test
    fun `createWorkspace should track workspace_created on success`() = runTest {
        coEvery { workspaceApi.createWorkspace(any()) } returns ResultData.Complete(Unit)
        coEvery { workspaceApi.getAvailableWorkspaces() } returns ResultData.Complete(emptyList())

        viewModel().createWorkspace("Acme")
        advanceUntilIdle()

        assertEquals(1, analyticsTracker.capturedEvents.size)
        assertEquals("workspace_created", analyticsTracker.capturedEvents.single().event)
    }

    @Test
    fun `createWorkspace should not track workspace_created when the API call fails`() = runTest {
        coEvery { workspaceApi.createWorkspace(any()) } returns ResultData.Error(Exception("offline"))

        viewModel().createWorkspace("Acme")
        advanceUntilIdle()

        assertEquals(emptyList(), analyticsTracker.capturedEvents)
    }
}

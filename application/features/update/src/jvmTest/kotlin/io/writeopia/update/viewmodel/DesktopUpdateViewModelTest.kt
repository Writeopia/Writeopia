package io.writeopia.update.viewmodel

import io.writeopia.sdk.serialization.response.DesktopAppVersionResponse
import io.writeopia.update.DesktopUpdateChecker
import io.writeopia.update.api.DesktopUpdateVersionSource
import io.writeopia.update.model.DesktopPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopUpdateViewModelTest {

    @Test
    fun `exposes available update and download failure state`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val viewModel = DesktopUpdateViewModel(checker("0.48.0"))

            assertSame(DesktopUpdateUiState.Idle, viewModel.uiState.value)
            viewModel.checkForUpdate()

            val available = assertIs<DesktopUpdateUiState.UpdateAvailable>(viewModel.uiState.value)
            assertEquals("0.48.0", available.update.latestVersion)

            viewModel.onDownloadOpenFailed()
            assertEquals(
                true,
                assertIs<DesktopUpdateUiState.UpdateAvailable>(viewModel.uiState.value).openFailed
            )

            viewModel.dismissUpdate()
            assertSame(DesktopUpdateUiState.Idle, viewModel.uiState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `exposes idle when no update exists`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val viewModel = DesktopUpdateViewModel(checker("0.47.0"))

            viewModel.checkForUpdate()

            assertSame(DesktopUpdateUiState.Idle, viewModel.uiState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `exposes failure when update check fails`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val checker = DesktopUpdateChecker(
                versionSource = DesktopUpdateVersionSource { error("network failure") },
                currentVersion = "0.47.0",
                downloadBaseUrl = "https://writeopia.io",
                platformProvider = { DesktopPlatform.LINUX }
            )
            val viewModel = DesktopUpdateViewModel(checker)

            viewModel.checkForUpdate()

            assertSame(DesktopUpdateUiState.CheckFailed, viewModel.uiState.value)

            viewModel.dismissCheckFailure()
            assertSame(DesktopUpdateUiState.Idle, viewModel.uiState.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun checker(latestVersion: String) = DesktopUpdateChecker(
        versionSource = DesktopUpdateVersionSource {
            DesktopAppVersionResponse(latestVersion)
        },
        currentVersion = "0.47.0",
        downloadBaseUrl = "https://writeopia.io",
        platformProvider = { DesktopPlatform.LINUX }
    )
}

package io.writeopia.update

import io.writeopia.update.api.DesktopUpdateVersionSource
import io.writeopia.update.model.DesktopAppVersionResponse
import io.writeopia.update.model.DesktopPlatform
import io.writeopia.update.model.DesktopUpdateCheckResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class DesktopUpdateCheckerTest {

    @Test
    fun `returns update with writeopia download URL when newer version exists`() = runTest {
        val checker = checker(
            latestVersion = "0.48.0",
            currentVersion = "0.47.0",
            platform = DesktopPlatform.WINDOWS
        )

        val result = assertIs<DesktopUpdateCheckResult.UpdateAvailable>(checker.checkForUpdate())

        assertEquals("0.48.0", result.update.latestVersion)
        assertEquals(
            "https://writeopia.io/apps-download/latest/Writeopia.msi",
            result.update.downloadUrl
        )
    }

    @Test
    fun `returns no update when latest version matches current version`() = runTest {
        val checker = checker(latestVersion = "0.47.0", currentVersion = "0.47.0")

        assertSame(DesktopUpdateCheckResult.NoUpdate, checker.checkForUpdate())
    }

    @Test
    fun `returns no update when server version is older`() = runTest {
        val checker = checker(latestVersion = "0.46.9", currentVersion = "0.47.0")

        assertSame(DesktopUpdateCheckResult.NoUpdate, checker.checkForUpdate())
    }

    @Test
    fun `selects macOS download URL`() = runTest {
        val checker = checker(
            latestVersion = "0.48.0",
            currentVersion = "0.47.0",
            platform = DesktopPlatform.MAC
        )

        val result = assertIs<DesktopUpdateCheckResult.UpdateAvailable>(checker.checkForUpdate())

        assertEquals(
            "https://writeopia.io/apps-download/latest/Writeopia.dmg",
            result.update.downloadUrl
        )
    }

    @Test
    fun `returns failure when version response is invalid`() = runTest {
        val cause = IllegalStateException("network failure")
        val checker = DesktopUpdateChecker(
            versionSource = DesktopUpdateVersionSource { throw cause },
            currentVersion = "0.47.0",
            downloadBaseUrl = "https://writeopia.io",
            platformProvider = { DesktopPlatform.LINUX }
        )

        val result = assertIs<DesktopUpdateCheckResult.Failure>(checker.checkForUpdate())

        assertSame(cause, result.cause)
    }

    @Test
    fun `compares multi digit version parts numerically`() {
        assertEquals(true, isNewerVersion("0.10.0", "0.9.9"))
        assertEquals(false, isNewerVersion("0.9.9", "0.10.0"))
    }

    @Test
    fun `detects supported desktop platforms`() {
        assertEquals(DesktopPlatform.WINDOWS, currentDesktopPlatform("Windows 11"))
        assertEquals(DesktopPlatform.LINUX, currentDesktopPlatform("Linux"))
        assertEquals(DesktopPlatform.MAC, currentDesktopPlatform("Mac OS X"))
        assertEquals(null, currentDesktopPlatform("Unknown"))
    }

    private fun checker(
        latestVersion: String,
        currentVersion: String,
        platform: DesktopPlatform = DesktopPlatform.LINUX
    ) = DesktopUpdateChecker(
        versionSource = DesktopUpdateVersionSource {
            DesktopAppVersionResponse(latestVersion)
        },
        currentVersion = currentVersion,
        downloadBaseUrl = "https://writeopia.io",
        platformProvider = { platform }
    )
}

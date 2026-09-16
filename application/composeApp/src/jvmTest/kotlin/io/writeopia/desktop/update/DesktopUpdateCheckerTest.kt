package io.writeopia.desktop.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class DesktopUpdateCheckerTest {

    @Test
    fun `it should find the newest desktop release for the current platform`() {
        val update = findDesktopUpdate(
            releasesJson = releasesJson(),
            platform = DesktopPlatform.WINDOWS,
            currentReleaseNumber = 47,
        )

        assertEquals(49, update?.releaseNumber)
        assertEquals("apps-alpha49", update?.tagName)
        assertEquals("https://example.com/49/Writeopia.msi", update?.downloadUrl)
    }

    @Test
    fun `it should ignore SDK releases`() {
        val update = findDesktopUpdate(
            releasesJson = releasesJson(),
            platform = DesktopPlatform.LINUX,
            currentReleaseNumber = 49,
        )

        assertNull(update)
    }

    @Test
    fun `it should choose the Apple Silicon asset on ARM macOS`() {
        val update = findDesktopUpdate(
            releasesJson = releasesJson(),
            platform = DesktopPlatform.MAC_ARM,
            currentReleaseNumber = 47,
        )

        assertEquals("https://example.com/49/Writeopia.dmg", update?.downloadUrl)
    }

    @Test
    fun `it should choose the Intel mac asset on Intel macOS`() {
        val update = findDesktopUpdate(
            releasesJson = releasesJson(),
            platform = DesktopPlatform.MAC_INTEL,
            currentReleaseNumber = 47,
        )

        assertEquals("https://example.com/49/Writeopia-intel.dmg", update?.downloadUrl)
    }

    @Test
    fun `it should return a failure when the update request fails`() = runTest {
        val checker = DesktopUpdateChecker(
            currentReleaseNumber = 47,
            platformProvider = { DesktopPlatform.WINDOWS },
            releaseFetcher = { error("network failure") },
        )

        val result = checker.checkForUpdate()

        val failure = assertIs<DesktopUpdateCheckResult.Failure>(result)
        assertEquals("network failure", failure.cause.message)
    }

    @Test
    fun `it should detect desktop platforms`() {
        assertEquals(
            DesktopPlatform.WINDOWS,
            currentDesktopPlatform("Windows 11", "amd64"),
        )
        assertEquals(
            DesktopPlatform.LINUX,
            currentDesktopPlatform("Linux", "amd64"),
        )
        assertEquals(
            DesktopPlatform.MAC_ARM,
            currentDesktopPlatform("Mac OS X", "aarch64"),
        )
        assertEquals(
            DesktopPlatform.MAC_INTEL,
            currentDesktopPlatform("Mac OS X", "x86_64"),
        )
        assertNull(currentDesktopPlatform("FreeBSD", "amd64"))
    }

    private fun releasesJson(): String = """
            [
              {
                "tag_name": "sdk-0.14.0",
                "draft": false,
                "assets": []
              },
              {
                "tag_name": "apps-alpha49",
                "draft": false,
                "assets": [
                  {
                    "name": "Writeopia.msi",
                    "browser_download_url": "https://example.com/49/Writeopia.msi"
                  },
                  {
                    "name": "Writeopia.deb",
                    "browser_download_url": "https://example.com/49/Writeopia.deb"
                  },
                  {
                    "name": "Writeopia.dmg",
                    "browser_download_url": "https://example.com/49/Writeopia.dmg"
                  },
                  {
                    "name": "Writeopia-intel.dmg",
                    "browser_download_url": "https://example.com/49/Writeopia-intel.dmg"
                  }
                ]
              },
              {
                "tag_name": "apps-alpha48",
                "draft": false,
                "assets": [
                  {
                    "name": "Writeopia.msi",
                    "browser_download_url": "https://example.com/48/Writeopia.msi"
                  }
                ]
              }
            ]
        """.trimIndent()
}

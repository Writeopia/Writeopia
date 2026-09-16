package io.writeopia.desktop.update

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal const val CURRENT_DESKTOP_RELEASE_NUMBER = 47

private const val RELEASES_URL =
    "https://api.github.com/repos/Writeopia/Writeopia/releases?per_page=100"

internal data class DesktopUpdate(
    val releaseNumber: Int,
    val tagName: String,
    val downloadUrl: String,
)

internal sealed interface DesktopUpdateCheckResult {
    data class UpdateAvailable(val update: DesktopUpdate) : DesktopUpdateCheckResult

    data object NoUpdate : DesktopUpdateCheckResult

    data class Failure(val cause: Throwable) : DesktopUpdateCheckResult
}

internal enum class DesktopPlatform {
    WINDOWS,
    LINUX,
    MAC_ARM,
    MAC_INTEL,
}

internal class DesktopUpdateChecker(
    private val currentReleaseNumber: Int = CURRENT_DESKTOP_RELEASE_NUMBER,
    private val releasesUrl: String = RELEASES_URL,
    private val platformProvider: () -> DesktopPlatform? = { currentDesktopPlatform() },
    private val releaseFetcher: (String) -> String = ::fetchReleaseJson,
) {
    suspend fun checkForUpdate(): DesktopUpdateCheckResult = withContext(Dispatchers.IO) {
        val platform = platformProvider() ?: return@withContext DesktopUpdateCheckResult.NoUpdate

        try {
            val response = releaseFetcher(releasesUrl)
            val update = findDesktopUpdate(
                releasesJson = response,
                platform = platform,
                currentReleaseNumber = currentReleaseNumber,
            )

            if (update == null) {
                DesktopUpdateCheckResult.NoUpdate
            } else {
                DesktopUpdateCheckResult.UpdateAvailable(update)
            }
        } catch (error: Exception) {
            println(
                "Desktop update check failed for $releasesUrl: " +
                    "${error.message ?: error::class.simpleName}"
            )
            error.printStackTrace()
            DesktopUpdateCheckResult.Failure(error)
        }
    }
}

private fun fetchReleaseJson(releasesUrl: String): String {
    val connection = URL(releasesUrl).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 5_000
    connection.readTimeout = 5_000
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    connection.setRequestProperty("User-Agent", "Writeopia-Desktop")

    return try {
        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            error("GitHub releases request failed with HTTP $responseCode")
        }

        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

internal fun currentDesktopPlatform(
    osName: String = System.getProperty("os.name").orEmpty(),
    osArch: String = System.getProperty("os.arch").orEmpty(),
): DesktopPlatform? {
    val normalizedOs = osName.lowercase()
    val normalizedArch = osArch.lowercase()

    return when {
        normalizedOs.contains("win") -> DesktopPlatform.WINDOWS
        normalizedOs.contains("linux") -> DesktopPlatform.LINUX
        normalizedOs.contains("mac") &&
            (
                normalizedArch.contains("aarch64") ||
                    normalizedArch.contains("arm64")
            ) -> DesktopPlatform.MAC_ARM

        normalizedOs.contains("mac") -> DesktopPlatform.MAC_INTEL
        else -> null
    }
}

internal fun findDesktopUpdate(
    releasesJson: String,
    platform: DesktopPlatform,
    currentReleaseNumber: Int,
): DesktopUpdate? {
    val releases = Json.parseToJsonElement(releasesJson).jsonArray

    return releases
        .mapNotNull { releaseElement ->
            val release = releaseElement.jsonObject
            val isDraft = release["draft"]?.jsonPrimitive?.booleanOrNull == true
            if (isDraft) {
                return@mapNotNull null
            }

            val tagName = release["tag_name"]?.jsonPrimitive?.contentOrNull
                ?: return@mapNotNull null
            val releaseNumber = desktopReleaseNumber(tagName)
                ?: return@mapNotNull null

            val downloadUrl = release["assets"]
                ?.jsonArray
                ?.mapNotNull { assetElement ->
                    val asset = assetElement.jsonObject
                    val name = asset["name"]?.jsonPrimitive?.contentOrNull
                        ?: return@mapNotNull null
                    val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull
                        ?: return@mapNotNull null
                    name to url
                }
                ?.let { assets -> selectAsset(assets, platform) }
                ?: return@mapNotNull null

            DesktopUpdate(
                releaseNumber = releaseNumber,
                tagName = tagName,
                downloadUrl = downloadUrl,
            )
        }
        .filter { it.releaseNumber > currentReleaseNumber }
        .maxByOrNull { it.releaseNumber }
}

private fun desktopReleaseNumber(tagName: String): Int? =
    Regex("^apps-alpha(\\d+)$")
        .matchEntire(tagName)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()

private fun selectAsset(
    assets: List<Pair<String, String>>,
    platform: DesktopPlatform,
): String? {
    val match = when (platform) {
        DesktopPlatform.WINDOWS -> assets.firstOrNull {
            it.first.endsWith(".msi", ignoreCase = true)
        }

        DesktopPlatform.LINUX -> assets.firstOrNull {
            it.first.endsWith(".deb", ignoreCase = true)
        }

        DesktopPlatform.MAC_ARM -> assets.firstOrNull {
            it.first.endsWith(".dmg", ignoreCase = true) &&
                !it.first.contains("intel", ignoreCase = true)
        }

        DesktopPlatform.MAC_INTEL -> assets.firstOrNull {
            it.first.endsWith(".dmg", ignoreCase = true) &&
                it.first.contains("intel", ignoreCase = true)
        }
    }

    return match?.second
}

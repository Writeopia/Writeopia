package io.writeopia.update

import io.writeopia.update.api.DesktopUpdateVersionSource
import io.writeopia.update.model.DesktopPlatform
import io.writeopia.update.model.DesktopUpdate
import io.writeopia.update.model.DesktopUpdateCheckResult
import kotlinx.coroutines.CancellationException

class DesktopUpdateChecker(
    private val versionSource: DesktopUpdateVersionSource,
    private val currentVersion: String,
    private val downloadBaseUrl: String,
    private val platformProvider: () -> DesktopPlatform? = ::currentDesktopPlatform
) {

    suspend fun checkForUpdate(): DesktopUpdateCheckResult {
        val platform = platformProvider() ?: return DesktopUpdateCheckResult.NoUpdate

        return try {
            val latestVersion = versionSource.latestVersion().version

            if (isNewerVersion(latestVersion = latestVersion, currentVersion = currentVersion)) {
                DesktopUpdateCheckResult.UpdateAvailable(
                    DesktopUpdate(
                        latestVersion = latestVersion,
                        downloadUrl = downloadUrl(platform)
                    )
                )
            } else {
                DesktopUpdateCheckResult.NoUpdate
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            println(
                "Desktop update check failed: " +
                    (error.message ?: error::class.simpleName)
            )
            error.printStackTrace()
            DesktopUpdateCheckResult.Failure(error)
        }
    }

    private fun downloadUrl(platform: DesktopPlatform): String =
        "${downloadBaseUrl.trimEnd('/')}/apps-download/latest/${platform.downloadFileName}"
}

internal fun currentDesktopPlatform(
    osName: String = System.getProperty("os.name").orEmpty()
): DesktopPlatform? {
    val normalizedOs = osName.lowercase()
    val normalizedArch = System.getProperty("os.arch").orEmpty().lowercase()
    val isAppleSilicon = normalizedArch == "aarch64" || normalizedArch == "arm64"

    return when {
        (normalizedOs.contains("mac") || normalizedOs.contains("darwin")) && isAppleSilicon -> DesktopPlatform.MAC
        normalizedOs.contains("mac") || normalizedOs.contains("darwin") -> null
        normalizedOs.contains("win") -> DesktopPlatform.WINDOWS
        normalizedOs.contains("linux") -> DesktopPlatform.LINUX
        else -> null
    }
}

internal fun isNewerVersion(
    latestVersion: String,
    currentVersion: String
): Boolean {
    val latest = parseVersion(latestVersion)
    val current = parseVersion(currentVersion)
    val partCount = maxOf(latest.size, current.size)

    for (index in 0 until partCount) {
        val latestPart = latest.getOrElse(index) { 0 }
        val currentPart = current.getOrElse(index) { 0 }

        if (latestPart != currentPart) {
            return latestPart > currentPart
        }
    }

    return false
}

private fun parseVersion(version: String): List<Int> {
    val normalized = version
        .trim()
        .removePrefix("v")
        .substringBefore('-')

    require(normalized.isNotEmpty()) { "Version must not be empty" }

    return normalized.split('.').map { part ->
        require(part.isNotEmpty() && part.all(Char::isDigit)) {
            "Invalid version: $version"
        }
        part.toInt()
    }
}

package io.writeopia.update.model

sealed interface DesktopUpdateCheckResult {
    data class UpdateAvailable(
        val update: DesktopUpdate
    ) : DesktopUpdateCheckResult

    data object NoUpdate : DesktopUpdateCheckResult

    data class Failure(
        val cause: Throwable
    ) : DesktopUpdateCheckResult
}

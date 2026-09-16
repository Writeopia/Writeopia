package io.writeopia.update.api

import io.writeopia.update.model.DesktopAppVersionResponse

fun interface DesktopUpdateVersionSource {
    suspend fun latestVersion(): DesktopAppVersionResponse
}

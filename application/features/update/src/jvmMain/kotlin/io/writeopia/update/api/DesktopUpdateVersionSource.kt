package io.writeopia.update.api

import io.writeopia.sdk.serialization.response.DesktopAppVersionResponse

fun interface DesktopUpdateVersionSource {
    suspend fun latestVersion(): DesktopAppVersionResponse
}

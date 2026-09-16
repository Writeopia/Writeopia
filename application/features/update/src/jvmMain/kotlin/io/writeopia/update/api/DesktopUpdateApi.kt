package io.writeopia.update.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.writeopia.update.model.DesktopAppVersionResponse

private const val DESKTOP_VERSION_ENDPOINT = "api/app/desktop-version"

class DesktopUpdateApi(
    private val client: HttpClient,
    private val baseUrl: String
) : DesktopUpdateVersionSource {

    override suspend fun latestVersion(): DesktopAppVersionResponse =
        client.get("${baseUrl.trimEnd('/')}/$DESKTOP_VERSION_ENDPOINT").body()
}

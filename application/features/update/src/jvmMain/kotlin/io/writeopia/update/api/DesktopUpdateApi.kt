package io.writeopia.update.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.serialization.response.DesktopAppVersionResponse

class DesktopUpdateApi(
    private val client: HttpClient,
    private val baseUrl: String
) : DesktopUpdateVersionSource {

    override suspend fun latestVersion(): DesktopAppVersionResponse =
        client.get("$baseUrl/api/${EndPoints.desktopAppVersion()}") {
            expectSuccess = true
        }.body()
}

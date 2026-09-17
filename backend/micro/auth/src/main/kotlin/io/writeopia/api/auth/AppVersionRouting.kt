package io.writeopia.api.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.writeopia.api.core.auth.utils.DesktopAppVersionConfig
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.serialization.response.DesktopAppVersionResponse

private const val CACHE_MAX_AGE_SECONDS = 60 * 60

/**
 * Registers the public desktop application version endpoint.
 */
fun Routing.appVersionRoute() {
    get("/api/${EndPoints.desktopAppVersion()}") {
        call.response.header(HttpHeaders.CacheControl, "public, max-age=$CACHE_MAX_AGE_SECONDS")
        call.respond(
            HttpStatusCode.OK,
            DesktopAppVersionResponse(version = DesktopAppVersionConfig.CURRENT)
        )
    }
}

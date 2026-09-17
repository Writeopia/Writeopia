package io.writeopia.api.geteway

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.writeopia.app.endpoints.EndPoints
import io.writeopia.sdk.serialization.response.DesktopAppVersionResponse

private const val CACHE_MAX_AGE_SECONDS = 60 * 60

/**
 * Public endpoint exposing the latest desktop application version.
 *
 * The value is identical for every user, so the response can be cached by the CDN.
 */
fun Routing.desktopAppVersionRoute() {
    get("/${EndPoints.desktopAppVersion()}") {
        call.response.header(HttpHeaders.CacheControl, "public, max-age=$CACHE_MAX_AGE_SECONDS")
        call.respond(
            HttpStatusCode.OK,
            DesktopAppVersionResponse(version = DesktopAppVersionConfig.CURRENT)
        )
    }
}

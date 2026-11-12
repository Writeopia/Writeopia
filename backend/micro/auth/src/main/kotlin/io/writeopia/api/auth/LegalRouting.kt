package io.writeopia.api.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import io.writeopia.api.core.auth.legal.LegalDocuments
import io.writeopia.app.endpoints.EndPoints

// Browsers revalidate after an hour; the CDN keeps a copy for a day. The text only changes with a
// release, and a new LegalDocuments.VERSION changes the ETag.
private const val CACHE_CONTROL = "public, max-age=3600, s-maxage=86400"

/**
 * Registers the public legal text endpoint (`terms` and `privacy`).
 *
 * The response never depends on the caller, so it can be cached by the CDN.
 */
fun Routing.legalRoute() {
    get("/api/${EndPoints.legalDocument()}") {
        val document = call.parameters["type"]?.let(LegalDocuments::get)

        if (document == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }

        val etag = "\"${document.type}-${document.version}\""

        call.response.header(HttpHeaders.CacheControl, CACHE_CONTROL)
        call.response.header(HttpHeaders.ETag, etag)

        if (call.request.headers[HttpHeaders.IfNoneMatch] == etag) {
            call.respond(HttpStatusCode.NotModified)
        } else {
            call.respond(HttpStatusCode.OK, document)
        }
    }
}

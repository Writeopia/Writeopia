package io.writeopia.api.documents

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.writeopia.api.documents.routing.documentsRoute
import io.writeopia.sql.WriteopiaDbBackend

fun Application.configureRouting(
    writeopiaDb: WriteopiaDbBackend?,
    useAi: Boolean,
    debugMode: Boolean = false
) {
    routing {
        // Health check for Cloud Run
        get("/health") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }

        if (writeopiaDb != null) {
            documentsRoute(writeopiaDb, useAi, debugMode)
        }

        get {
            call.respondText("Writeopia Documents Service")
        }
    }
}

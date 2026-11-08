package io.writeopia.api.media

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.writeopia.api.media.routing.accountDeletionEventsRoute
import io.writeopia.api.media.routing.mediaRoute
import io.writeopia.sql.WriteopiaDbBackend
import io.writeopia.storage.StorageUsageService

fun Application.configureRouting(debugMode: Boolean = false, writeopiaDb: WriteopiaDbBackend) {
    routing {
        // Health check for Cloud Run
        get("/health") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }

        // Health check accessible via load balancer
        get("/api/media/health") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }

        mediaRoute(debugMode, storageUsage = StorageUsageService(writeopiaDb))

        // Account-deletion saga: internal Pub/Sub-push endpoint.
        accountDeletionEventsRoute(debugMode)

        get {
            call.respondText("Writeopia Media Service")
        }
    }
}

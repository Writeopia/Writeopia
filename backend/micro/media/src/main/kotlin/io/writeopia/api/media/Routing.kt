package io.writeopia.api.media

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.writeopia.api.media.routing.mediaRoute

fun Application.configureRouting(debugMode: Boolean = false) {
    routing {
        // Health check for Cloud Run
        get("/health") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }

        // Health check accessible via load balancer
        get("/api/media/health") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }

        mediaRoute(debugMode)

        get {
            call.respondText("Writeopia Media Service")
        }
    }
}

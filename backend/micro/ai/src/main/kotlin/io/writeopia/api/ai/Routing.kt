package io.writeopia.api.ai

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.writeopia.api.ai.routing.aiRoute
import io.writeopia.api.ai.routing.localAiConfigRoute
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend

fun Application.configureRouting(
    debugMode: Boolean = false,
    writeopiaDb: WriteopiaDbBackend? = null
) {
    routing {
        // Health check endpoint for Cloud Run
        get("/health") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }

        // Health check accessible via load balancer
        get("/api/ai/health") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }

        // Public, CDN-cacheable Local AI auto-configuration
        localAiConfigRoute()

        // AI routes
        logger.info("Configuring AI routes...")
        aiRoute(debugMode, writeopiaDb)

        // Root endpoint
        get {
            call.respondText("Writeopia AI Service")
        }
    }
}

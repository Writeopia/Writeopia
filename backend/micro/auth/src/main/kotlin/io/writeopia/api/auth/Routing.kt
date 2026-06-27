package io.writeopia.api.auth

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.writeopia.api.core.auth.routing.adminProtectedRoute
import io.writeopia.api.core.auth.routing.authRoute
import io.writeopia.api.core.auth.routing.emailRoute
import io.writeopia.api.core.auth.routing.workspaceRoute
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend

fun Application.configureRouting(
    writeopiaDb: WriteopiaDbBackend?,
    debugMode: Boolean = false,
    adminKey: String?
) {
    routing {
        // Health check endpoint for Cloud Run
        get("/health") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }

        // Health check accessible via load balancer
        get("/api/auth/health") {
            call.respondText("OK", status = HttpStatusCode.OK)
        }

        if (writeopiaDb != null) {
            // Auth routes: login, register, password reset, account deletion, current user
            authRoute(writeopiaDb, debugMode)

            // Workspace routes: workspace CRUD operations
            workspaceRoute(adminKey, writeopiaDb, debugMode)

            // Admin routes: user management (enable/disable)
            if (adminKey != null || debugMode) {
                logger.info("Admin routes are enabled.")
                adminProtectedRoute(adminKey, writeopiaDb, debugMode)
            } else {
                logger.info("Admin key is null. Admin routes are disabled.")
            }

            emailRoute(writeopiaDb)
        }

        // Root endpoint
        get {
            call.respondText("Writeopia Auth Service")
        }
    }
}

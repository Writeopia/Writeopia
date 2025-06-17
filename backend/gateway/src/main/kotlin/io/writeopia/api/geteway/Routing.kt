package io.writeopia.api.geteway

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.writeopia.api.core.auth.routing.adminProtectedRoute
import io.writeopia.api.core.auth.routing.authRoute
import io.writeopia.api.documents.routing.documentsRoute
import io.writeopia.connection.logger
import io.writeopia.sql.WriteopiaDbBackend

fun Application.configureRouting(
    writeopiaDb: WriteopiaDbBackend?,
    useAi: Boolean,
    debugMode: Boolean = false,
    adminKey: String?
) {
    routing {
        if (writeopiaDb != null) {
            documentsRoute(writeopiaDb, useAi, debugMode)
            authRoute(writeopiaDb, debugMode)

            if (adminKey != null) {
                logger.info("Admin routes are enabled.")
                adminProtectedRoute(adminKey, writeopiaDb)
            } else {
                logger.info("Admin key is null. Admin routes are disabled.")
            }
        }

        get {
            call.respondText("Hi")
        }
    }
}
